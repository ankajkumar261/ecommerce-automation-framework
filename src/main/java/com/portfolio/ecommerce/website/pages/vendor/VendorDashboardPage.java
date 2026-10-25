package com.portfolio.ecommerce.website.pages.vendor;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * VendorDashboardPage — Page Object for vendor dashboard
 * URL: panel.theecommerce.com/vendor/dashboard
 *
 * Logout is two steps:
 *   1. Click logout icon  → button[title='Logout']
 *   2. Click red confirm  → button:has-text('Log out')
 *   3. Wait for redirect  → URL contains /login
 */
public class VendorDashboardPage {

    private static final Logger log = LogManager.getLogger(VendorDashboardPage.class);

    private final Page page;

    // ── Logout locators ───────────────────────────────────────
    // Logout moved behind the profile card's kebab (⋮) menu. The trigger is a stable, semantic
    // button (title="Account options", aria-haspopup="menu"); the menu item reads "Sign out".
    private final Locator accountMenuTrigger;
    private final Locator signOutItem;

    // Navigation — update after inspecting real dashboard
    private final Locator productsMenu;
    private final Locator ordersMenu;

    public VendorDashboardPage(Page page) {
        this.page = page;

        this.accountMenuTrigger = page.locator("button[title='Account options'], button[aria-haspopup='menu']").first();
        this.signOutItem        = page.locator(
                "button[role='menuitem']:has-text('Sign out'), button:has-text('Sign out'), button:has-text('Log out')").first();
        this.productsMenu     = page.locator("a:has-text('Product'), a[href*='product']").first();
        this.ordersMenu       = page.locator("a:has-text('Order'), a[href*='order']").first();
    }

    // ── Verification ──────────────────────────────────────────

    public boolean isDashboardLoaded() {
        try {
            page.waitForLoadState();
            boolean result = !page.url().contains("/login");
            log.info("Vendor dashboard loaded check — URL: {} | result: {}", page.url(), result);
            return result;
        } catch (Exception e) {
            log.error("Dashboard load check failed: {}", e.getMessage());
            return false;
        }
    }

    public String getCurrentUrl() {
        return page.url();
    }

    // ── Navigation ────────────────────────────────────────────

    @Step("Navigate to Vendor Products section")
    public VendorDashboardPage goToProducts() {
        productsMenu.click();
        page.waitForLoadState();
        return this;
    }

    @Step("Navigate to Vendor Orders section")
    public VendorDashboardPage goToOrders() {
        ordersMenu.click();
        page.waitForLoadState();
        return this;
    }

    // ── Logout ────────────────────────────────────────────────

    @Step("Logout from vendor panel")
    public void logout() {
        log.info("Opening the account (kebab) menu");
        accountMenuTrigger.waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE));
        accountMenuTrigger.click();

        log.info("Clicking Sign out");
        signOutItem.waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE));
        signOutItem.click();

        // Sign out raises a confirm modal ("Log out? — You will be signed out…") with a red
        // "Log out" button (and a "Stay" cancel). Confirm it. The modal is a custom div, not a
        // [role=dialog], so match the button by its text — at this point the "Sign out" menu item
        // is gone, so "Log out" is unambiguous.
        Locator confirm = page.locator(
                "button:has-text('Log out'), button:has-text('Yes, log out'), button:has-text('Confirm')").first();
        confirm.waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                .setTimeout(6000));
        confirm.click();

        // Wait for redirect back to login page
        try {
            page.waitForURL(url -> url.contains("/login"),
                    new Page.WaitForURLOptions().setTimeout(10000));
        } catch (Exception e) {
            log.warn("URL did not change to /login after logout — current: {}", page.url());
        }
        log.info("Logged out. Current URL: {}", page.url());
    }
}