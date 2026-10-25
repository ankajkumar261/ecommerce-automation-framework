package com.portfolio.ecommerce.tests.website.storefront;

import com.portfolio.ecommerce.website.base.BaseTest;
import com.portfolio.ecommerce.website.config.ConfigReader;
import com.portfolio.ecommerce.website.pages.storefront.StorefrontLoginPage;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Storefront (customer) login coverage — mirrors the Admin login suite.
 *
 * BaseTest navigates the primary page to the storefront /login URL before each test
 * (see getUrl() override), so every test starts on the login page.
 *
 * Positive: valid credentials log the customer in (redirect off /login).
 * Negatives: bad credentials / empty fields do NOT log in (stays on /login).
 */
@Epic("Authentication")
@Feature("Storefront customer login")
public class StorefrontLoginTest extends BaseTest {

    // Start every test on the storefront /login page.
    @Override
    protected String getUrl() {
        String base = ConfigReader.getInstance().getStorefrontUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/login";
    }

    @Test(description = "TC-SL001: Valid customer credentials log in successfully")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Valid login")
    public void validCustomerLoginSucceeds() {
        ConfigReader config = ConfigReader.getInstance();
        StorefrontLoginPage login = new StorefrontLoginPage(getPage());

        logStep("Log in with valid customer credentials");
        login.loginAs(config.getCustomerEmail(), config.getCustomerPassword());

        Assert.assertTrue(login.isLoggedIn(),
                "Customer should be logged in with valid credentials");
    }

    @Test(description = "TC-SL002: Wrong password is rejected")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Invalid login")
    public void wrongPasswordRejected() {
        ConfigReader config = ConfigReader.getInstance();
        StorefrontLoginPage login = new StorefrontLoginPage(getPage());

        logStep("Attempt login with a wrong password");
        login.loginAs(config.getCustomerEmail(), "WrongPass@999");

        Assert.assertFalse(login.isLoggedIn(),
                "Login must NOT succeed with a wrong password");
        Assert.assertTrue(login.isOnLoginPage(),
                "Should remain on the login page after a wrong password");
    }

    @Test(description = "TC-SL003: Unknown email is rejected")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Invalid login")
    public void unknownEmailRejected() {
        StorefrontLoginPage login = new StorefrontLoginPage(getPage());

        logStep("Attempt login with an unregistered email");
        login.loginAs("missing@example.test", "Test@123");

        Assert.assertFalse(login.isLoggedIn(),
                "Login must NOT succeed with an unregistered email");
        Assert.assertTrue(login.isOnLoginPage(),
                "Should remain on the login page after an unknown email");
    }

    @Test(description = "TC-SL004: Empty email is blocked")
    @Severity(SeverityLevel.NORMAL)
    @Story("Invalid login")
    public void emptyEmailBlocked() {
        ConfigReader config = ConfigReader.getInstance();
        StorefrontLoginPage login = new StorefrontLoginPage(getPage());

        logStep("Reveal form, leave email empty, try to submit");
        login.clickEmailPasswordOption();
        login.enterPassword(config.getCustomerPassword());
        login.clickLogin();

        Assert.assertFalse(login.isLoggedIn(),
                "Login must NOT succeed with an empty email");
        Assert.assertTrue(login.isOnLoginPage(),
                "Should remain on the login page with an empty email");
    }

    @Test(description = "TC-SL005: Empty password is blocked")
    @Severity(SeverityLevel.NORMAL)
    @Story("Invalid login")
    public void emptyPasswordBlocked() {
        ConfigReader config = ConfigReader.getInstance();
        StorefrontLoginPage login = new StorefrontLoginPage(getPage());

        logStep("Reveal form, leave password empty, try to submit");
        login.clickEmailPasswordOption();
        login.enterEmail(config.getCustomerEmail());
        login.clickLogin();

        Assert.assertFalse(login.isLoggedIn(),
                "Login must NOT succeed with an empty password");
        Assert.assertTrue(login.isOnLoginPage(),
                "Should remain on the login page with an empty password");
    }
}