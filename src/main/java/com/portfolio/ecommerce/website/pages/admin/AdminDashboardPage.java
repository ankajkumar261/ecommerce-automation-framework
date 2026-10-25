package com.portfolio.ecommerce.website.pages.admin;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AdminDashboardPage {

    private static final Logger log = LogManager.getLogger(AdminDashboardPage.class);

    private final Page page;

    private final Locator dashboardHeading;
    private final Locator logoutIcon;
    private final Locator confirmLogoutButton;
    private final Locator productsMenu;
    private final Locator ordersMenu;
    private final Locator vendorsMenu;
    private final Locator welcomeMessage;

    public AdminDashboardPage(Page page) {
        this.page = page;

        this.dashboardHeading = page.locator(
                "h1, h2, .dashboard-title, [class*='dashboard']"
        ).first();

        // Logout moved behind the profile card's kebab (⋮) menu — a stable, semantic trigger
        // (title="Account options", aria-haspopup="menu"); the menu item reads "Sign out".
        this.logoutIcon = page.locator(
                "button[title='Account options'], button[aria-haspopup='menu']").first();

        // The "Sign out" menu item (this build has no separate confirm dialog).
        this.confirmLogoutButton = page.locator(
                "button[role='menuitem']:has-text('Sign out'), button:has-text('Sign out'), button:has-text('Log out')"
        ).first();

        this.productsMenu = page.locator(
                "a:has-text('Product'), a[href*='product']"
        ).first();

        this.ordersMenu = page.locator(
                "a:has-text('Order'), a[href*='order']"
        ).first();

        this.vendorsMenu = page.locator(
                "a:has-text('Vendor'), a[href*='vendor']"
        ).first();

        this.welcomeMessage = page.locator(
                "[class*='welcome'], [class*='user-name'], .navbar-text"
        ).first();
    }

    /**
     * Verifies successful login.
     */
    public boolean isDashboardLoaded() {

        try {

            page.waitForURL(
                    url -> !url.contains("/login"),
                    new Page.WaitForURLOptions().setTimeout(20000)
            );

            log.info("Dashboard loaded successfully");
            log.info("Current URL: {}", page.url());

            return true;

        } catch (Exception e) {

            log.error("Still on login page");
            log.error("Current URL: {}", page.url());

            return false;
        }
    }

    public String getCurrentUrl() {
        return page.url();
    }

    public boolean isLogoutVisible() {
        try {
            return logoutIcon.isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    @Step("Navigate to Products section")
    public AdminDashboardPage goToProducts() {
        log.info("Navigating to Products");
        productsMenu.click();
        page.waitForLoadState();
        return this;
    }

    @Step("Navigate to Orders section")
    public AdminDashboardPage goToOrders() {
        log.info("Navigating to Orders");
        ordersMenu.click();
        page.waitForLoadState();
        return this;
    }

    @Step("Navigate to Vendors section")
    public AdminDashboardPage goToVendors() {
        log.info("Navigating to Vendors");
        vendorsMenu.click();
        page.waitForLoadState();
        return this;
    }

    @Step("Logout from admin panel")
    public void logout() {

        log.info("Opening the account (kebab) menu");

        logoutIcon.waitFor();
        logoutIcon.click();

        log.info("Clicking Sign out");

        confirmLogoutButton.waitFor();
        confirmLogoutButton.click();

        // Sign out raises a confirm modal ("Log out? — You will be signed out…") with a red
        // "Log out" button. Confirm it (the "Sign out" menu item is gone, so "Log out" is unique).
        log.info("Confirming logout");
        Locator confirmModalBtn = page.locator(
                "button:has-text('Log out'), button:has-text('Yes, log out'), button:has-text('Confirm')").first();
        confirmModalBtn.waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                .setTimeout(6000));
        confirmModalBtn.click();

        page.waitForURL(
                "**/login",
                new Page.WaitForURLOptions().setTimeout(15000)
        );

        log.info("Successfully logged out");
    }
}