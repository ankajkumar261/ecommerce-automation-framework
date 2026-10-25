package com.portfolio.ecommerce.tests.website.session;

import com.portfolio.ecommerce.website.base.BaseTest;
import com.portfolio.ecommerce.website.config.ConfigReader;
import com.portfolio.ecommerce.website.pages.admin.AdminDashboardPage;
import com.portfolio.ecommerce.website.pages.admin.AdminLoginPage;
import com.portfolio.ecommerce.website.pages.storefront.StorefrontLoginPage;
import com.microsoft.playwright.Page;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Section 1.6 — SESSION / LOGOUT / INTERCEPTOR.
 *
 * Covers the security-critical session behaviours from E2E_QA_FLOWS.txt §1.6 that
 * the login suites do not:
 *   - A 401 from a protected page (corrupted token) auto-redirects to that app's login.
 *   - Cross-app token leakage: a storefront customer token in admin_token must NOT
 *     grant admin access.
 *   - Deep-link while logged out bounces to /login and returns to the target after login.
 *
 * Token keys (per §0): admin console -> admin_token, storefront -> storefront_token.
 * localStorage is per-origin; admin + vendor share one origin, storefront is separate,
 * so each test navigates explicitly to the origin it needs.
 */
@Epic("Authentication")
@Feature("Session / logout / interceptor")
public class SessionSecurityTest extends BaseTest {

    // Land on the storefront /login before each test (most tests start here);
    // admin-origin tests navigate themselves.
    @Override
    protected String getUrl() {
        return storefrontBase() + "/login";
    }

    // ── URL helpers ───────────────────────────────────────────
    private String storefrontBase() {
        String base = ConfigReader.getInstance().getStorefrontUrl();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** Admin origin root, derived by stripping the "/login" suffix from admin.url. */
    private String adminBase() {
        String url = ConfigReader.getInstance().getAdminUrl();
        return url.endsWith("/login") ? url.substring(0, url.length() - "/login".length()) : url;
    }

    // ── localStorage helpers (run on whatever origin the page is on) ──
    private String readLocalStorage(String key) {
        Object v = getPage().evaluate("(k) => window.localStorage.getItem(k)", key);
        return v == null ? null : v.toString();
    }

    private void writeLocalStorage(String key, String value) {
        getPage().evaluate(
                "([k, v]) => window.localStorage.setItem(k, v)",
                java.util.Arrays.asList(key, value));
    }

    /**
     * Poisons the storefront session so the app still believes it is logged in but
     * carries an invalid Bearer token — the precise condition the 401 interceptor guards.
     * Corrupting only storefront_token is not enough: the Zustand "storefront-auth" persist
     * blob re-syncs its stored token back into storefront_token on rehydrate
     * (onRehydrateStorage -> tokenStore.set). So we poison the token in BOTH places.
     */
    private void corruptStorefrontSession(String badToken) {
        getPage().evaluate(
                "(bad) => {" +
                "  try { localStorage.setItem('storefront_token', bad); } catch (e) {}" +
                "  try {" +
                "    const raw = localStorage.getItem('storefront-auth');" +
                "    if (raw) {" +
                "      const o = JSON.parse(raw);" +
                "      if (o && o.state) { o.state.token = bad; }" +
                "      localStorage.setItem('storefront-auth', JSON.stringify(o));" +
                "    }" +
                "  } catch (e) {}" +
                "}",
                badToken);
    }

    /** Waits up to timeoutMs for the URL to contain a substring; returns the final URL. */
    private String waitForUrlContains(String needle, int timeoutMs) {
        try {
            getPage().waitForURL(u -> u.contains(needle),
                    new Page.WaitForURLOptions().setTimeout(timeoutMs));
        } catch (Exception ignored) {
            // fall through — caller asserts on the final URL
        }
        return getPage().url();
    }

    // ─────────────────────────────────────────────────────────
    // DEEP-LINK WHILE LOGGED OUT
    // ─────────────────────────────────────────────────────────

    @Test(description = "TC-SESS001: Deep-link to a protected page while logged out bounces to /login")
    @Description("Navigates straight to /account/orders with no session and verifies the client guard "
            + "redirects the visitor to /login instead of rendering the protected page.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Logged-out deep link")
    public void loggedOutDeepLinkBouncesToLogin() {
        logStep("Directly open a protected page (/account/orders) while logged out");
        getPage().navigate(storefrontBase() + "/account/orders",
                new Page.NavigateOptions().setTimeout(45000));

        String finalUrl = waitForUrlContains("/login", 15000);
        logStep("Landed on: " + finalUrl);

        Assert.assertTrue(finalUrl.contains("/login"),
                "A logged-out visitor to /account/orders must be bounced to /login. Was: " + finalUrl);
    }

    @Test(description = "TC-SESS002: After logging in from a deep-link bounce, the user returns to the original page")
    @Description("Hits /account/orders while logged out, logs in on the resulting /login page, and verifies "
            + "the app honours the redirect target by returning the user to /account/orders.")
    @Severity(SeverityLevel.NORMAL)
    @Story("Logged-out deep link")
    public void deepLinkReturnsToTargetAfterLogin() {
        ConfigReader config = ConfigReader.getInstance();

        logStep("Open /account/orders while logged out -> expect bounce to /login");
        getPage().navigate(storefrontBase() + "/account/orders",
                new Page.NavigateOptions().setTimeout(45000));
        String bounced = waitForUrlContains("/login", 15000);
        Assert.assertTrue(bounced.contains("/login"),
                "Precondition: should be bounced to /login. Was: " + bounced);

        logStep("Log in with valid customer credentials from the bounce page");
        StorefrontLoginPage login = new StorefrontLoginPage(getPage());
        login.loginAs(config.getCustomerEmail(), config.getCustomerPassword());

        String finalUrl = waitForUrlContains("/account/orders", 15000);
        logStep("After login landed on: " + finalUrl);

        Assert.assertTrue(finalUrl.contains("/account/orders"),
                "After login the user should return to the original deep-link (/account/orders). Was: " + finalUrl);
    }

    // ─────────────────────────────────────────────────────────
    // 401 INTERCEPTOR (corrupted token)
    // ─────────────────────────────────────────────────────────

    @Test(description = "TC-SESS003: A corrupted storefront token forces a redirect to /login on the next protected page")
    @Description("Logs in as a customer, poisons the stored session token (both storefront_token and the "
            + "storefront-auth blob), then opens a protected page and verifies the 401 interceptor redirects to /login.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("401 interceptor")
    public void corruptedStorefrontTokenRedirectsToLogin() {
        ConfigReader config = ConfigReader.getInstance();

        logStep("Log in as a customer");
        StorefrontLoginPage login = new StorefrontLoginPage(getPage());
        login.loginAs(config.getCustomerEmail(), config.getCustomerPassword());
        Assert.assertTrue(login.isLoggedIn(), "Precondition: customer must be logged in");

        logStep("Poison the stored session token (storefront_token + storefront-auth blob)");
        corruptStorefrontSession("corrupted.invalid.token");

        logStep("Navigate to a protected page (/account/orders) with the bad token");
        getPage().navigate(storefrontBase() + "/account/orders",
                new Page.NavigateOptions().setTimeout(45000));

        String finalUrl = waitForUrlContains("/login", 15000);
        logStep("Landed on: " + finalUrl);

        Assert.assertTrue(finalUrl.contains("/login"),
                "A 401 from a corrupted token must redirect to /login. Was: " + finalUrl);
    }

    @Test(description = "TC-SESS004: A corrupted admin token forces a redirect to the admin /login")
    @Description("Logs into the admin console, corrupts the stored admin_token, reloads a protected admin "
            + "page, and verifies the 401 interceptor sends the user back to the admin /login.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("401 interceptor")
    public void corruptedAdminTokenRedirectsToLogin() {
        ConfigReader config = ConfigReader.getInstance();

        logStep("Open the admin login page and sign in as super admin");
        getPage().navigate(adminBase() + "/login",
                new Page.NavigateOptions().setTimeout(45000));
        AdminLoginPage adminLogin = new AdminLoginPage(getPage());
        AdminDashboardPage dashboard = adminLogin.loginAs(
                config.getAdminEmail(), config.getAdminPassword());
        Assert.assertTrue(dashboard.isDashboardLoaded(),
                "Precondition: admin must reach the dashboard. Was: " + dashboard.getCurrentUrl());

        logStep("Corrupt the stored admin_token");
        writeLocalStorage("admin_token", "corrupted.invalid.token");

        logStep("Reload the protected admin page with the bad token");
        getPage().navigate(adminBase() + "/dashboard",
                new Page.NavigateOptions().setTimeout(45000));

        String finalUrl = waitForUrlContains("/login", 15000);
        logStep("Landed on: " + finalUrl);

        Assert.assertTrue(finalUrl.contains("/login"),
                "A 401 from a corrupted admin token must redirect to the admin /login. Was: " + finalUrl);
    }

    // ─────────────────────────────────────────────────────────
    // CROSS-APP TOKEN LEAKAGE
    // ─────────────────────────────────────────────────────────

    @Test(description = "TC-SESS005: A storefront customer token placed in admin_token must NOT grant admin access")
    @Description("Captures a real customer storefront_token, injects it into the admin origin's admin_token, "
            + "opens the admin dashboard, and verifies the customer token is rejected (redirect to /login) — no cross-app leakage.")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Cross-app token leakage")
    public void customerTokenDoesNotGrantAdminAccess() {
        ConfigReader config = ConfigReader.getInstance();

        logStep("Log in on the storefront and capture the customer storefront_token");
        StorefrontLoginPage login = new StorefrontLoginPage(getPage());
        login.loginAs(config.getCustomerEmail(), config.getCustomerPassword());
        Assert.assertTrue(login.isLoggedIn(), "Precondition: customer must be logged in");

        String customerToken = readLocalStorage("storefront_token");
        Assert.assertTrue(customerToken != null && !customerToken.isBlank(),
                "Precondition: a storefront_token should be present after customer login");

        logStep("Move to the admin origin and inject the customer token as admin_token");
        getPage().navigate(adminBase() + "/login",
                new Page.NavigateOptions().setTimeout(45000));
        writeLocalStorage("admin_token", customerToken);

        logStep("Attempt to open the admin dashboard with the leaked customer token");
        getPage().navigate(adminBase() + "/dashboard",
                new Page.NavigateOptions().setTimeout(45000));

        String finalUrl = waitForUrlContains("/login", 15000);
        logStep("Landed on: " + finalUrl);

        Assert.assertTrue(finalUrl.contains("/login"),
                "A customer token must NOT grant admin access — expected a redirect to /login. Was: " + finalUrl);
    }
}
