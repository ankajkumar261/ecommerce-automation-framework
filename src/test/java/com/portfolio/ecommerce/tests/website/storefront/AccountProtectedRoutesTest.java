package com.portfolio.ecommerce.tests.website.storefront;

import com.portfolio.ecommerce.website.base.BaseTest;
import com.portfolio.ecommerce.website.config.ConfigReader;
import com.microsoft.playwright.Page;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Section 13 — Customer Account Area: ProtectedRoute guard.
 *
 * Every /account/* route is behind a client-side ProtectedRoute (middleware.ts is a no-op),
 * so a logged-out visitor must be bounced to /login rather than seeing the protected page.
 * This test deep-links each account route with no session and asserts the redirect.
 */
@Epic("Customer account")
@Feature("ProtectedRoute guard")
public class AccountProtectedRoutesTest extends BaseTest {

    /** Every customer account route that must be gated. */
    private static final String[] ACCOUNT_ROUTES = {
            "/account",
            "/account/orders",
            "/account/returns",
            "/account/addresses",
            "/account/wishlist",
            "/account/bookings",
            "/account/measurements",
            "/account/messages",
            "/account/receipts",
            "/account/support",
    };

    // BaseTest opens a fresh (logged-out) context on the storefront /login before the test.
    @Override
    protected String getUrl() {
        return storefrontBase() + "/login";
    }

    private String storefrontBase() {
        String base = ConfigReader.getInstance().getStorefrontUrl();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    @Test(description = "TC-ACCT001: Every /account/* route bounces a logged-out visitor to /login")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Logged-out account access is redirected")
    @Description("With no session, deep-links each customer account route (orders, addresses, wishlist, "
            + "bookings, measurements, messages, receipts, support, returns) and verifies every one "
            + "redirects to /login instead of rendering the protected page.")
    public void loggedOutAccountRoutesRedirectToLogin() {
        Page page = getPage();
        List<String> leaks = new ArrayList<>();

        for (String route : ACCOUNT_ROUTES) {
            page.navigate(storefrontBase() + route, new Page.NavigateOptions().setTimeout(45000));
            try {
                page.waitForURL(u -> u.contains("/login"),
                        new Page.WaitForURLOptions().setTimeout(12000));
            } catch (Exception ignored) {
                // fall through to the check below
            }
            String finalUrl = page.url();
            logStep(route + " -> " + finalUrl);
            if (!finalUrl.contains("/login")) {
                leaks.add(route + " (landed on " + finalUrl + ")");
            }
        }

        Assert.assertTrue(leaks.isEmpty(),
                "These account routes did NOT bounce a logged-out visitor to /login: " + leaks);
    }
}
