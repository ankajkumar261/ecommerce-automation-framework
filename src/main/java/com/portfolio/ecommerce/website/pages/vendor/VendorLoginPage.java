package com.portfolio.ecommerce.website.pages.vendor;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * VendorLoginPage — Page Object for vendor login
 * URL: panel.theecommerce.com/vendor/login
 *
 * Page flow:
 *   1. "Use email and password" button visible on load
 *   2. Click it → #email and #password fields appear
 *   3. Fill fields → click "Sign In"
 */
public class VendorLoginPage {

    private static final Logger log = LogManager.getLogger(VendorLoginPage.class);

    private final Page page;

    // ── Confirmed locators ────────────────────────────────────
    private final Locator useEmailPasswordButton;
    private final Locator emailField;
    private final Locator passwordField;
    private final Locator loginButton;
    private final Locator errorMessage;
    private final Locator forgotPasswordLink;

    public VendorLoginPage(Page page) {
        this.page = page;

        this.useEmailPasswordButton = page.locator("button:has-text('Use email and password')");
        this.emailField             = page.locator("#email");
        this.passwordField          = page.locator("#password");
        this.loginButton            = page.locator("button[type='submit']:has-text('Sign In')");
        this.errorMessage           = page.locator(".error, .alert-danger, [class*='error'], [class*='Error']").first();
        this.forgotPasswordLink     = page.locator("a:has-text('Forgot'), a:has-text('forgot')").first();
    }

    // ── Private: reveal form only if not already open ─────────

    private void revealFormIfNeeded() {
        if (emailField.isVisible()) {
            log.info("Email form already visible — skipping toggle click");
            return;
        }
        log.info("Waiting for 'Use email and password' button...");
        // The vendor login SPA occasionally renders slowly under load; the
        // default 10s timeout is too tight and produces flaky failures on the
        // toggle button / email field. 25s tolerates a slow render. This does
        // NOT mask a down server — that fails on navigation in BaseTest first.
        useEmailPasswordButton.waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                .setTimeout(25000));
        useEmailPasswordButton.click();
        log.info("Clicked 'Use email and password'");
        emailField.waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                .setTimeout(25000));
        log.info("Email/password form is now visible");
    }

    // ── Public actions ────────────────────────────────────────

    @Step("Click 'Use email and password' to reveal login form")
    public VendorLoginPage clickUseEmailAndPassword() {
        revealFormIfNeeded();
        return this;
    }

    @Step("Enter vendor email: {email}")
    public VendorLoginPage enterEmail(String email) {
        log.info("Entering vendor email: {}", email);
        revealFormIfNeeded();
        emailField.clear();
        emailField.fill(email);
        return this;
    }

    @Step("Enter vendor password")
    public VendorLoginPage enterPassword(String password) {
        log.info("Entering vendor password: [hidden]");
        revealFormIfNeeded();
        passwordField.waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE));
        passwordField.clear();
        passwordField.fill(password);
        return this;
    }

    @Step("Click Sign In button")
    public VendorDashboardPage clickLogin() {
        log.info("Waiting for Sign In button to be visible and enabled...");
        loginButton.waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE));

        // Poll until enabled — handles slow form animations (max 5s)
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (loginButton.isEnabled()) break;
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }

        // Click, wait for the URL to leave /vendor/login. If it doesn't (slow
        // render / dropped click under load), click once more before giving up —
        // this removes the flaky "URL did not change after login" failures.
        if (!clickAndWaitForLeave()) {
            log.warn("Login URL did not change — retrying the Sign In click once");
            try {
                if (loginButton.isVisible()) {
                    clickAndWaitForLeave();
                }
            } catch (Exception e) {
                log.warn("Retry click failed: {}", e.getMessage());
            }
        }

        if (isOnLoginPage()) {
            log.warn("Still on vendor login page after login attempt — current URL: {}", page.url());
        } else {
            log.info("Login successful. Current URL: {}", page.url());
        }
        return new VendorDashboardPage(page);
    }

    /** Click Sign In and wait for the URL to leave /vendor/login. Returns true if it left. */
    private boolean clickAndWaitForLeave() {
        try {
            log.info("Clicking Sign In");
            loginButton.click();
            page.waitForURL(url -> !url.contains("/vendor/login"),
                    new Page.WaitForURLOptions().setTimeout(15000));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Step("Login as vendor with email: {email}")
    public VendorDashboardPage loginAs(String email, String password) {
        log.info("Logging in as vendor: {}", email);
        revealFormIfNeeded();
        emailField.clear();
        emailField.fill(email);
        log.info("Email entered");
        passwordField.waitFor();
        passwordField.clear();
        passwordField.fill(password);
        log.info("Password entered");
        return clickLogin();
    }

    @Step("Reveal form and click Sign In with current field state")
    public VendorLoginPage clickLoginWithEmptyFields() {
        log.info("Clicking Sign In with empty/partial fields");
        revealFormIfNeeded();
        try {
            loginButton.click(new Locator.ClickOptions().setForce(true));
            log.info("Sign In clicked (forced)");
        } catch (Exception e) {
            log.info("Sign In click blocked — expected for empty fields: {}", e.getMessage());
        }
        return this;
    }

    @Step("Click Forgot Password link")
    public VendorLoginPage clickForgotPassword() {
        revealFormIfNeeded();
        forgotPasswordLink.waitFor();
        forgotPasswordLink.click();
        return this;
    }

    // ── Getters ───────────────────────────────────────────────

    public boolean isErrorMessageVisible() {
        try { return errorMessage.isVisible(); } catch (Exception e) { return false; }
    }

    public String getErrorMessage() {
        errorMessage.waitFor();
        return errorMessage.textContent().trim();
    }

    public boolean isOnLoginPage() {
        return page.url().contains("/vendor/login");
    }

    public String getCurrentUrl() { return page.url(); }

    public String getPageTitle() { return page.title(); }
}