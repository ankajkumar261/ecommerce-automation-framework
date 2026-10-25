package com.portfolio.ecommerce.website.pages.storefront;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Page Object for the Storefront customer login (storefront.url/login from config-${env}.properties).
 *
 * Confirmed flow:
 *   1. Land on /login (header "Login" is just <a href="/login">, so navigate directly).
 *   2. Click the "Email & Password" button to reveal the form (social options show first).
 *   3. Fill #email + #password.
 *   4. Click the "Login" submit button.
 *
 * Selectors (confirmed from DOM):
 *   email & pw option : button text "Email & Password" (lucide-lock icon)
 *   email field       : input#email   (placeholder "you@example.test")
 *   password field    : input#password
 *   submit            : button[type='submit'] text "Login"
 *   header login link : a[href='/login'] text "Login"
 */
public class StorefrontLoginPage {

    private static final Logger log = LogManager.getLogger(StorefrontLoginPage.class);
    private final Page page;

    public StorefrontLoginPage(Page page) {
        this.page = page;
        registerPromoAutoDismiss();
    }

    /**
     * Register a Playwright locator handler that auto-dismisses the newsletter / 10%-off
     * popup whenever it appears. Playwright runs this before every action, so the modal
     * can pop at any time and it'll be closed before it can block a click. Registered once
     * per page in the constructor.
     */
    private void registerPromoAutoDismiss() {
        // Proactive: kill the popup on every navigation/refresh via a context init script.
        try { page.context().addInitScript(StorefrontPage.PROMO_KILLER_SCRIPT); } catch (Exception ignored) {}
        // Reactive: also dismiss before any action on the current page.
        try {
            Locator closeBtn = page.locator("button[class*='glass']:has(svg.lucide-x)");
            page.addLocatorHandler(closeBtn, handler -> {
                try {
                    handler.click();
                    log.info("Auto-dismissed promo modal");
                } catch (Exception ignored) {
                    // already gone / not clickable
                }
            });
        } catch (Exception e) {
            log.warn("Could not register promo auto-dismiss: {}", e.getMessage());
        }
    }

    /**
     * Reveal the email/password form by clicking the "Email & Password" option.
     * (On /login the social-login choices appear first.)
     */
    @Step("Choose 'Email & Password' login")
    public void clickEmailPasswordOption() {
        Locator btn = page.locator("button:has-text('Email & Password')").first();
        btn.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
        // The promo modal is auto-killed by the init script + locator handler, so just click.
        btn.click();
        log.info("Selected 'Email & Password' login");
    }

    /**
     * Instant best-effort dismiss of the promo pop-up: clicks the X only if it's already
     * visible (no waiting). The init script + locator handler normally handle it; this is
     * just a cheap extra nudge with no delay.
     */
    @Step("Dismiss promo modal if present")
    public void dismissPromoIfPresent() {
        try {
            Locator close = page.locator("button[class*='glass']:has(svg.lucide-x)").first();
            if (close.isVisible()) {
                close.click();
                log.info("Dismissed promo modal");
            }
        } catch (Exception ignored) {
            // nothing to dismiss
        }
    }

    @Step("Enter email: {email}")
    public void enterEmail(String email) {
        Locator field = page.locator("#email");
        field.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
        field.fill(email);
    }

    @Step("Enter password")
    public void enterPassword(String password) {
        page.locator("#password").fill(password);
    }

    @Step("Click Login")
    public void clickLogin() {
        // Promo modal is auto-killed by the init script + locator handler — just click.
        page.locator("button[type='submit']:has-text('Login')").first().click();
        page.waitForLoadState();
    }

    /**
     * Full login: reveal form -> enter creds -> submit.
     */
    @Step("Login as: {email}")
    public void loginAs(String email, String password) {
        clickEmailPasswordOption();
        enterEmail(email);
        enterPassword(password);
        clickLogin();
        log.info("Submitted login for: {}", email);
    }

    /**
     * True once the customer is logged in. Primary signal: the "Welcome back!" success
     * toast (Sonner) that appears right after a successful login. Falls back to a redirect
     * away from /login, then to the header "Login" link disappearing.
     */
    @Step("Check logged in")
    public boolean isLoggedIn() {
        // 1) Primary: the success toast "Welcome back!".
        try {
            Locator toast = page.locator(
                    "[data-sonner-toast][data-type='success']:has-text('Welcome back')").first();
            toast.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
            log.info("Login succeeded ('Welcome back!' toast shown)");
            return true;
        } catch (Exception ignored) {
            // fall through to secondary signals
        }

        // 2) Fallback: redirected away from /login.
        try {
            page.waitForURL(u -> !u.contains("/login"),
                    new Page.WaitForURLOptions().setTimeout(5000));
            log.info("Login succeeded (redirected away from /login)");
            return true;
        } catch (Exception ignored) {
            // fall through
        }

        // 3) Last resort: header "Login" link is gone.
        boolean loginLinkGone = !page.locator("a[href='/login']").first().isVisible();
        log.info("No welcome toast / no redirect; header Login link gone = {}", loginLinkGone);
        return loginLinkGone;
    }

    /**
     * True if still on the /login page (i.e. not logged in).
     */
    @Step("Check still on login page")
    public boolean isOnLoginPage() {
        return page.url().contains("/login");
    }
}