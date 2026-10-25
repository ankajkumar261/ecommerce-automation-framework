package com.portfolio.ecommerce.website.pages.admin;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AdminLoginPage {

    private static final Logger log = LogManager.getLogger(AdminLoginPage.class);

    private final Page page;

    // Locators
    private final Locator emailField;
    private final Locator passwordField;
    private final Locator loginButton;
    private final Locator errorMessage;

    public AdminLoginPage(Page page) {
        this.page = page;

        this.emailField = page.locator("#email");
        this.passwordField = page.locator("#password");
        this.loginButton = page.locator("button[type='submit']");

        this.errorMessage = page.locator(
                "text=/invalid|incorrect|failed|error|password/i"
        ).first();
    }

    @Step("Enter admin email: {email}")
    public AdminLoginPage enterEmail(String email) {
        log.info("Entering email: {}", email);
        emailField.fill(email);
        return this;
    }

    @Step("Enter admin password")
    public AdminLoginPage enterPassword(String password) {
        log.info("Entering password: [hidden]");
        passwordField.fill(password);
        return this;
    }

    @Step("Click Sign In button")
    public AdminDashboardPage clickLogin() {

        log.info("Clicking Sign In button");

        loginButton.click();

        try {

            // Wait for login processing
            page.waitForTimeout(5000);

            log.info("Current URL after login: {}", page.url());
            log.info("Page title after login: {}", page.title());

        } catch (Exception e) {

            log.error(
                    "Error while waiting after login click: {}",
                    e.getMessage()
            );
        }

        return new AdminDashboardPage(page);
    }

    @Step("Login as admin: {email}")
    public AdminDashboardPage loginAs(String email, String password) {

        log.info("Starting admin login flow for: {}", email);

        return enterEmail(email)
                .enterPassword(password)
                .clickLogin();
    }

    @Step("Click Sign In with empty fields")
    public AdminLoginPage clickLoginWithEmptyFields() {

        log.info("Clicking Sign In with empty fields");

        loginButton.click();

        return this;
    }

    /**
     * Reads whether the Sign In button is currently enabled — WITHOUT clicking it.
     * The admin login form disables Sign In until the input is valid, so negative
     * tests use this to assert the button stays disabled instead of clicking a
     * disabled button (which would just time out).
     */
    @Step("Check if the Sign In button is enabled")
    public boolean isSignInButtonEnabled() {
        try {
            return loginButton.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isErrorMessageVisible() {

        try {

            errorMessage.waitFor(
                    new Locator.WaitForOptions().setTimeout(5000)
            );

            return errorMessage.isVisible();

        } catch (Exception e) {

            return false;
        }
    }

    public String getErrorMessage() {

        try {

            return errorMessage.textContent().trim();

        } catch (Exception e) {

            return "";
        }
    }

    public boolean isOnLoginPage() {
        return page.url().contains("/login");
    }

    public String getCurrentUrl() {
        return page.url();
    }

    public boolean isEmailFieldVisible() {

        try {

            return emailField.isVisible();

        } catch (Exception e) {

            return false;
        }
    }

    public boolean isLoginPageLoaded() {

        try {

            return emailField.isVisible()
                    && passwordField.isVisible()
                    && loginButton.isVisible();

        } catch (Exception e) {

            return false;
        }
    }
}