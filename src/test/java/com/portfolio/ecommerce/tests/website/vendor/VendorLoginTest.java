package com.portfolio.ecommerce.tests.website.vendor;

import com.portfolio.ecommerce.website.base.BaseTest;
import com.portfolio.ecommerce.website.pages.vendor.VendorDashboardPage;
import com.portfolio.ecommerce.website.pages.vendor.VendorLoginPage;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

@Epic("Vendor Panel")
@Feature("Vendor Login")
public class VendorLoginTest extends BaseTest {

    @Override
    protected String getUrl() {
        return config.getVendorUrl();
    }

    // TC-V001 — Valid login
    @Test(priority = 1, description = "Vendor should be able to login with valid credentials")
    @Story("Valid Login")
    @Severity(SeverityLevel.BLOCKER)
    public void testValidVendorLogin() {
        logStep("Open vendor login page");
        VendorLoginPage loginPage = new VendorLoginPage(getPage());

        logStep("Login with valid vendor credentials");
        VendorDashboardPage dashboard = loginPage.loginAs(
                config.getVendorEmail(),
                config.getVendorPassword()
        );

        logStep("Verify vendor dashboard loaded after login");
        Assert.assertTrue(
                dashboard.isDashboardLoaded(),
                "Vendor dashboard should load after valid login. Current URL: " + dashboard.getCurrentUrl()
        );
    }

    // TC-V002 — Invalid password
    @Test(priority = 2, description = "Vendor login should fail with wrong password")
    @Story("Invalid Login")
    @Severity(SeverityLevel.CRITICAL)
    public void testLoginWithInvalidPassword() {
        logStep("Open vendor login page");
        VendorLoginPage loginPage = new VendorLoginPage(getPage());

        logStep("Reveal form, enter valid email but wrong password");
        // enterEmail() calls clickUseEmailAndPassword() internally
        loginPage.enterEmail(config.getVendorEmail())
                .enterPassword("WrongPassword@999");

        logStep("Click login");
        loginPage.clickLoginWithEmptyFields(); // reuse force-click; form is filled but wrong creds

        // Re-click properly via the actual login button
        logStep("Submit the form with wrong password");
        try {
            getPage().locator("button[type='submit']").filter(
                    new com.microsoft.playwright.Locator.FilterOptions().setHasNotText("Send OTP")
            ).first().click();
            getPage().waitForLoadState();
        } catch (Exception e) {
            // may stay on page — that's expected
        }

        logStep("Verify error message is displayed or user stays on login page");
        Assert.assertTrue(
                loginPage.isOnLoginPage(),
                "User should remain on vendor login page after failed login"
        );
    }

    // TC-V003 — Invalid email format
    @Test(priority = 3, description = "Vendor login should fail with invalid email format")
    @Story("Invalid Login")
    @Severity(SeverityLevel.NORMAL)
    public void testLoginWithInvalidEmail() {
        logStep("Open vendor login page");
        VendorLoginPage loginPage = new VendorLoginPage(getPage());

        logStep("Reveal form and enter invalid email format");
        loginPage.enterEmail("notanemail")
                .enterPassword(config.getVendorPassword());

        logStep("Submit and verify user stays on login page");
        try {
            getPage().locator("button[type='submit']").filter(
                    new com.microsoft.playwright.Locator.FilterOptions().setHasNotText("Send OTP")
            ).first().click();
            getPage().waitForLoadState();
        } catch (Exception e) {
            // expected — invalid email may prevent navigation
        }

        Assert.assertTrue(
                loginPage.isOnLoginPage(),
                "User should remain on vendor login page with invalid email"
        );
    }

    // TC-V004 — Empty email field
    @Test(priority = 4, description = "Vendor login should fail when email field is empty")
    @Story("Validation")
    @Severity(SeverityLevel.NORMAL)
    public void testLoginWithEmptyEmail() {
        logStep("Open vendor login page");
        VendorLoginPage loginPage = new VendorLoginPage(getPage());

        logStep("Reveal form, leave email empty, enter only password, click login");
        loginPage.clickUseEmailAndPassword()
                .enterPassword(config.getVendorPassword())
                .clickLoginWithEmptyFields();

        logStep("Verify user stays on vendor login page");
        Assert.assertTrue(
                loginPage.isOnLoginPage(),
                "User should remain on vendor login page when email is empty"
        );
    }

    // TC-V005 — Empty password field
    @Test(priority = 5, description = "Vendor login should fail when password field is empty")
    @Story("Validation")
    @Severity(SeverityLevel.NORMAL)
    public void testLoginWithEmptyPassword() {
        logStep("Open vendor login page");
        VendorLoginPage loginPage = new VendorLoginPage(getPage());

        logStep("Reveal form, enter email, leave password empty, click login");
        loginPage.enterEmail(config.getVendorEmail())
                .clickLoginWithEmptyFields();

        logStep("Verify user stays on vendor login page");
        Assert.assertTrue(
                loginPage.isOnLoginPage(),
                "User should remain on vendor login page when password is empty"
        );
    }

    // TC-V006 — Both fields empty
    @Test(priority = 6, description = "Vendor login should fail when both fields are empty")
    @Story("Validation")
    @Severity(SeverityLevel.NORMAL)
    public void testLoginWithBothFieldsEmpty() {
        logStep("Open vendor login page");
        VendorLoginPage loginPage = new VendorLoginPage(getPage());

        logStep("Reveal form and click login without entering any credentials");
        loginPage.clickLoginWithEmptyFields();

        logStep("Verify user stays on vendor login page");
        Assert.assertTrue(
                loginPage.isOnLoginPage(),
                "User should remain on vendor login page when both fields are empty"
        );
    }

    // TC-V007 — Logout after login
    @Test(priority = 7, description = "Vendor should be able to logout successfully")
    @Story("Logout")
    @Severity(SeverityLevel.CRITICAL)
    public void testVendorLogout() {
        logStep("Login as vendor");
        VendorLoginPage loginPage = new VendorLoginPage(getPage());
        VendorDashboardPage dashboard = loginPage.loginAs(
                config.getVendorEmail(),
                config.getVendorPassword()
        );

        logStep("Verify login was successful");
        Assert.assertTrue(dashboard.isDashboardLoaded(), "Must be logged in before testing logout");

        logStep("Click logout");
        dashboard.logout();

        logStep("Verify user is redirected back to vendor login page");
        Assert.assertTrue(
                getPage().url().contains("/vendor/login"),
                "After logout, user should be redirected back to vendor login page"
        );
    }
}