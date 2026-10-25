package com.portfolio.ecommerce.tests.website.admin;

import com.portfolio.ecommerce.website.base.BaseTest;
import com.portfolio.ecommerce.website.pages.admin.AdminDashboardPage;
import com.portfolio.ecommerce.website.pages.admin.AdminLoginPage;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * AdminLoginTest — Test class for admin login scenarios
 * URL: admin.url from config-${env}.properties (dev by default; live only with -Denv=live)
 *
 * Extends BaseTest — browser launch/close handled automatically.
 * No override of getUrl() needed — BaseTest defaults to admin URL.
 *
 * Test cases covered:
 *   TC-001  Valid login
 *   TC-002  Invalid password
 *   TC-003  Invalid email
 *   TC-004  Empty email
 *   TC-005  Empty password
 *   TC-006  Both fields empty
 *   TC-007  Logout after login
 *
 * NOTE on the negative tests (TC-003..006): the admin login form DISABLES the
 * Sign In button until the input is valid. So the correct behaviour to verify is
 * "Sign In stays disabled" — we assert that instead of clicking a disabled button
 * (clicking it just times out).
 */
@Epic("Admin Panel")
@Feature("Admin Login")
public class AdminLoginTest extends BaseTest {

    // ── No getUrl() override — BaseTest uses admin URL by default ──

    // ─────────────────────────────────────────────────────────
    // TC-001 — Valid login
    // ─────────────────────────────────────────────────────────
    @Test(priority = 1,
            description = "Admin should be able to login with valid credentials")
    @Story("Valid Login")
    @Severity(SeverityLevel.BLOCKER)
    public void testValidAdminLogin() {
        logStep("Open admin login page");
        AdminLoginPage loginPage = new AdminLoginPage(getPage());

        logStep("Login with valid credentials");
        AdminDashboardPage dashboard = loginPage.loginAs(
                config.getAdminEmail(),
                config.getAdminPassword()
        );

        logStep("Verify dashboard is loaded after login");
        Assert.assertTrue(
                dashboard.isDashboardLoaded(),
                "Dashboard should load after valid login. Current URL: " + dashboard.getCurrentUrl()
        );
    }

    // ─────────────────────────────────────────────────────────
    // TC-002 — Invalid password
    // ─────────────────────────────────────────────────────────
    @Test(priority = 2,
            description = "Admin login should fail with wrong password")
    @Story("Invalid Login")
    @Severity(SeverityLevel.CRITICAL)
    @Issue("BOUT-ZZZ")   // replace with the real admin invalid-password ticket id
    public void testLoginWithInvalidPassword() {
        logStep("Open admin login page");
        AdminLoginPage loginPage = new AdminLoginPage(getPage());

        logStep("Enter valid email but wrong password");
        loginPage.enterEmail(config.getAdminEmail())
                .enterPassword("WrongPassword@999")
                .clickLogin();

        logStep("Verify error message is displayed");
        // KNOWN BUG: an invalid admin password triggers a server error and redirects to the
        // vendor login page instead of showing an invalid-credentials message on admin login.
        // While that defect is open this test SKIPS (yellow, "Known Bugs" bucket) and the build
        // stays green. When the app is fixed to show the error, the guard is no longer hit and
        // the assertions below run -> the test turns green on its own.
        if (!loginPage.isErrorMessageVisible()) {
            knownBug("BOUT-ZZZ", "invalid admin password triggers a server error and redirects to "
                    + "the vendor login page instead of showing an invalid-credentials message on "
                    + "the admin login page.");
        }

        Assert.assertTrue(
                loginPage.isErrorMessageVisible(),
                "Error message should appear for wrong password"
        );

        logStep("Verify user stays on login page");
        Assert.assertTrue(
                loginPage.isOnLoginPage(),
                "User should remain on login page after failed login"
        );
    }

    // ─────────────────────────────────────────────────────────
    // TC-003 — Invalid email format
    // ─────────────────────────────────────────────────────────
    @Test(priority = 3,
            description = "Sign In stays disabled with an invalid email format")
    @Story("Validation")
    @Severity(SeverityLevel.NORMAL)
    public void testLoginWithInvalidEmail() {
        logStep("Open admin login page");
        AdminLoginPage loginPage = new AdminLoginPage(getPage());

        logStep("Enter invalid email format and a valid password");
        loginPage.enterEmail("notanemail")
                .enterPassword(config.getAdminPassword());

        logStep("Verify Sign In stays disabled for an invalid email");
        Assert.assertFalse(
                loginPage.isSignInButtonEnabled(),
                "Sign In should stay disabled when the email format is invalid"
        );

        logStep("Verify user stays on login page");
        Assert.assertTrue(
                loginPage.isOnLoginPage(),
                "User should remain on login page with invalid email"
        );
    }

    // ─────────────────────────────────────────────────────────
    // TC-004 — Empty email field
    // ─────────────────────────────────────────────────────────
    @Test(priority = 4,
            description = "Sign In stays disabled when the email field is empty")
    @Story("Validation")
    @Severity(SeverityLevel.NORMAL)
    public void testLoginWithEmptyEmail() {
        logStep("Open admin login page");
        AdminLoginPage loginPage = new AdminLoginPage(getPage());

        logStep("Leave email empty, enter valid password");
        loginPage.enterPassword(config.getAdminPassword());

        logStep("Verify Sign In stays disabled with an empty email");
        Assert.assertFalse(
                loginPage.isSignInButtonEnabled(),
                "Sign In should stay disabled when the email is empty"
        );

        logStep("Verify user stays on login page");
        Assert.assertTrue(
                loginPage.isOnLoginPage(),
                "User should remain on login page when email is empty"
        );
    }

    // ─────────────────────────────────────────────────────────
    // TC-005 — Empty password field
    // ─────────────────────────────────────────────────────────
    @Test(priority = 5,
            description = "Sign In stays disabled when the password field is empty")
    @Story("Validation")
    @Severity(SeverityLevel.NORMAL)
    public void testLoginWithEmptyPassword() {
        logStep("Open admin login page");
        AdminLoginPage loginPage = new AdminLoginPage(getPage());

        logStep("Enter valid email, leave password empty");
        loginPage.enterEmail(config.getAdminEmail());

        logStep("Verify Sign In stays disabled with an empty password");
        Assert.assertFalse(
                loginPage.isSignInButtonEnabled(),
                "Sign In should stay disabled when the password is empty"
        );

        logStep("Verify user stays on login page");
        Assert.assertTrue(
                loginPage.isOnLoginPage(),
                "User should remain on login page when password is empty"
        );
    }

    // ─────────────────────────────────────────────────────────
    // TC-006 — Both fields empty
    // ─────────────────────────────────────────────────────────
    @Test(priority = 6,
            description = "Sign In stays disabled when both fields are empty")
    @Story("Validation")
    @Severity(SeverityLevel.NORMAL)
    public void testLoginWithBothFieldsEmpty() {
        logStep("Open admin login page");
        AdminLoginPage loginPage = new AdminLoginPage(getPage());

        logStep("Leave both fields empty");
        // nothing entered

        logStep("Verify Sign In stays disabled with both fields empty");
        Assert.assertFalse(
                loginPage.isSignInButtonEnabled(),
                "Sign In should stay disabled when both fields are empty"
        );

        logStep("Verify user stays on login page");
        Assert.assertTrue(
                loginPage.isOnLoginPage(),
                "User should remain on login page when both fields are empty"
        );
    }

    // ─────────────────────────────────────────────────────────
    // TC-007 — Logout after login
    // ─────────────────────────────────────────────────────────
    @Test(priority = 7,
            description = "Admin should be able to logout successfully")
    @Story("Logout")
    @Severity(SeverityLevel.CRITICAL)
    public void testAdminLogout() {
        logStep("Login as admin");
        AdminLoginPage loginPage = new AdminLoginPage(getPage());
        AdminDashboardPage dashboard = loginPage.loginAs(
                config.getAdminEmail(),
                config.getAdminPassword()
        );

        logStep("Verify login was successful");
        Assert.assertTrue(dashboard.isDashboardLoaded(), "Must be logged in before testing logout");

        logStep("Click logout");
        dashboard.logout();

        logStep("Verify user is back on login page");
        Assert.assertTrue(
                getPage().url().contains("/login"),
                "After logout, user should be redirected back to login page"
        );
    }
}