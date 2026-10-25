package com.portfolio.ecommerce.tests.website.vendor;

import com.portfolio.ecommerce.website.base.BaseTest;
import com.portfolio.ecommerce.website.config.ConfigReader;
import com.portfolio.ecommerce.website.pages.vendor.VendorLoginPage;
import com.microsoft.playwright.Page;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Section 2 (UI side) — vendor approval-state redirect + guard behaviour.
 *
 * Complements {@link VendorApprovalGateApiTest} (which asserts the API 403 gate) by
 * checking the browser-level guards from the redirect matrix (doc §1.5 / §2 UI):
 *   - An approved vendor reaches guarded pages (e.g. /vendor/my-products).
 *   - A verified, NOT-onboarded vendor is parked on /vendor/onboarding and bounced
 *     away from a guarded page.
 *   - /vendor/contact-support is reachable in every state (here: while logged out).
 *
 * DATA GAP (not automated): the pure "/vendor/pending" and "/vendor/rejected" redirects
 * need a verified+onboarded+pending vendor and a verified+rejected vendor respectively.
 * The dev env currently has no such accounts (all pending/rejected vendors are
 * isVerified=false, so the matrix sends them to /vendor/verify-account first).
 */
@Epic("Vendor approval")
@Feature("Approval-state redirects + UI guard")
public class VendorStateRedirectTest extends BaseTest {

    @Override
    protected String getUrl() {
        return ConfigReader.getInstance().getVendorUrl();
    }

    /** Vendor-app origin, derived by stripping the "/vendor/login" suffix from vendor.url. */
    private String vendorOrigin() {
        String url = ConfigReader.getInstance().getVendorUrl();
        int idx = url.indexOf("/vendor/login");
        return idx > 0 ? url.substring(0, idx) : url;
    }

    private String waitForUrlSettled(String needle, int timeoutMs) {
        try {
            getPage().waitForURL(u -> u.contains(needle),
                    new Page.WaitForURLOptions().setTimeout(timeoutMs));
        } catch (Exception ignored) {
            // caller asserts on final URL
        }
        return getPage().url();
    }

    @Test(description = "TC-VSTATE001: An approved vendor can open a guarded page (/vendor/my-products)")
    @Description("Logs in as the approved vendor and deep-links to /vendor/my-products, verifying the page "
            + "loads and the vendor is not bounced to login or the pending screen.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Approved vendor reaches guarded pages")
    public void approvedVendorReachesGuardedPage() {
        ConfigReader config = ConfigReader.getInstance();

        logStep("Log in as the approved vendor");
        VendorLoginPage login = new VendorLoginPage(getPage());
        login.loginAs(config.getVendorEmail(), config.getVendorPassword());
        Assert.assertFalse(login.isOnLoginPage(),
                "Approved vendor should be logged in, not stuck on /vendor/login");

        logStep("Navigate to a guarded page: /vendor/my-products");
        getPage().navigate(vendorOrigin() + "/vendor/my-products",
                new Page.NavigateOptions().setTimeout(45000));
        String finalUrl = waitForUrlSettled("/vendor/my-products", 10000);

        Assert.assertTrue(finalUrl.contains("/vendor/my-products"),
                "Approved vendor should stay on /vendor/my-products, was: " + finalUrl);
        Assert.assertFalse(finalUrl.contains("/vendor/login"),
                "Approved vendor must not be bounced to login. Was: " + finalUrl);
    }

    @Test(description = "TC-VSTATE002: A not-onboarded vendor is parked on /vendor/onboarding after login")
    @Description("Logs in as a verified but not-onboarded vendor and verifies the redirect matrix parks "
            + "them on /vendor/onboarding.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Not-onboarded vendor redirect")
    public void notOnboardedVendorLandsOnOnboarding() {
        ConfigReader config = ConfigReader.getInstance();

        logStep("Log in as the verified, not-onboarded (pending) vendor");
        VendorLoginPage login = new VendorLoginPage(getPage());
        login.loginAs(config.getPendingVendorEmail(), config.getPendingVendorPassword());

        String finalUrl = waitForUrlSettled("/vendor/onboarding", 15000);
        logStep("Landed on: " + finalUrl);
        // Precondition: the configured vendor must actually be not-onboarded in this environment.
        // If it lands on the dashboard, it's approved+onboarded here (e.g. on live) — skip.
        if (finalUrl.contains("/vendor/dashboard")) {
            throw new org.testng.SkipException(
                    "Precondition not met: '" + config.getPendingVendorEmail()
                            + "' is approved/onboarded in this environment (lands on dashboard)");
        }
        Assert.assertTrue(finalUrl.contains("/vendor/onboarding"),
                "A verified, not-onboarded vendor should land on /vendor/onboarding, was: " + finalUrl);
    }

    @Test(description = "TC-VSTATE003: A not-onboarded vendor is bounced away from a guarded page")
    @Description("Logs in as the not-onboarded vendor, then deep-links to the guarded /vendor/my-products "
            + "and verifies the UI guard bounces them away (they cannot reach it before completing onboarding).")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Not-onboarded vendor UI guard")
    public void notOnboardedVendorBouncedFromGuardedPage() {
        ConfigReader config = ConfigReader.getInstance();

        logStep("Log in as the not-onboarded vendor");
        VendorLoginPage login = new VendorLoginPage(getPage());
        login.loginAs(config.getPendingVendorEmail(), config.getPendingVendorPassword());
        String afterLogin = waitForUrlSettled("/vendor/onboarding", 15000);
        // Precondition: skip if this account is actually approved/onboarded here (lands on dashboard).
        if (afterLogin.contains("/vendor/dashboard")) {
            throw new org.testng.SkipException(
                    "Precondition not met: '" + config.getPendingVendorEmail()
                            + "' is approved/onboarded in this environment (lands on dashboard)");
        }

        logStep("Try to deep-link into a guarded page: /vendor/my-products");
        getPage().navigate(vendorOrigin() + "/vendor/my-products",
                new Page.NavigateOptions().setTimeout(45000));
        // Give the client guard a moment to redirect.
        getPage().waitForTimeout(2500);
        String finalUrl = getPage().url();
        logStep("Ended on: " + finalUrl);

        Assert.assertFalse(finalUrl.contains("/vendor/my-products"),
                "A not-onboarded vendor must be bounced away from /vendor/my-products, but stayed. Was: " + finalUrl);
    }

    @Test(description = "TC-VSTATE004: /vendor/contact-support sits outside the approval gate (reachable by a non-approved vendor)")
    @Description("Logs in as the non-approved (not-onboarded) vendor and opens /vendor/contact-support, "
            + "verifying support is reachable in every approval state and is not bounced to login or onboarding.")
    @Severity(SeverityLevel.NORMAL)
    @Story("Contact support reachable in every approval state")
    public void contactSupportReachableForNonApprovedVendor() {
        ConfigReader config = ConfigReader.getInstance();

        logStep("Log in as the non-approved (not-onboarded) vendor");
        VendorLoginPage login = new VendorLoginPage(getPage());
        login.loginAs(config.getPendingVendorEmail(), config.getPendingVendorPassword());
        waitForUrlSettled("/vendor/onboarding", 15000);

        logStep("Navigate to /vendor/contact-support — must be reachable despite not being approved");
        getPage().navigate(vendorOrigin() + "/vendor/contact-support",
                new Page.NavigateOptions().setTimeout(45000));
        String finalUrl = waitForUrlSettled("/vendor/contact-support", 10000);
        logStep("Landed on: " + finalUrl);

        Assert.assertTrue(finalUrl.contains("/vendor/contact-support"),
                "Contact support must be reachable in every approval state, was: " + finalUrl);
        Assert.assertFalse(finalUrl.contains("/vendor/login"),
                "Contact support must NOT bounce to login for an authenticated vendor. Was: " + finalUrl);
    }
}
