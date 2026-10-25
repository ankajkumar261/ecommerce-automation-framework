package com.portfolio.ecommerce.website.pages.admin;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Page Object for the Admin Orders page (panel.theecommerce.com).
 *
 * Confirmed selectors (same admin list layout as Products, hash _7usmu_):
 *   Orders nav  : button containing span "Orders"
 *   search box  : input[placeholder='Search orders...']
 *   order card  : div._listCard_...  with title p._listCardTitle_... = "ORD-..."
 *
 * The list is sorted newest-first, so a freshly placed order is at the top of page 1.
 */
public class AdminOrdersPage {

    private static final Logger log = LogManager.getLogger(AdminOrdersPage.class);
    private final Page page;

    public AdminOrdersPage(Page page) {
        this.page = page;
    }

    /** Open the Orders section from the side nav. */
    @Step("Open Orders")
    public void openOrders() {
        Locator nav = page.locator("button:has(span:text-is('Orders'))").first();
        nav.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
        nav.click();
        // Wait for the orders list to render (search box is a stable anchor).
        page.locator("input[placeholder='Search orders...']").first()
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
        log.info("Opened Orders");
    }

    /** Type into the orders search box to filter. */
    @Step("Search orders: {query}")
    public void search(String query) {
        Locator box = page.locator("input[placeholder='Search orders...']").first();
        box.fill(query);
        page.waitForTimeout(800); // let the list filter
        log.info("Searched orders for: {}", query);
    }

    /**
     * True if an order card with the given order ID is visible.
     * Matches the order-card title (p._listCardTitle_...) by text. Because the list is
     * newest-first, a just-placed order is on page 1 even without searching.
     */
    @Step("Verify order present: {orderId}")
    public boolean isOrderPresent(String orderId) {
        try {
            page.locator("p[class*='_listCardTitle_']", new Page.LocatorOptions()
                            .setHasText(orderId)).first()
                    .waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE).setTimeout(12000));
            log.info("Order found in admin: {}", orderId);
            return true;
        } catch (Exception e) {
            log.warn("Order NOT found in admin: {}", orderId);
            return false;
        }
    }

    /** The order-list card for a given order ID. */
    private Locator orderCard(String orderId) {
        return page.locator("div[class*='_listCard_']")
                .filter(new Locator.FilterOptions().setHasText(orderId)).first();
    }

    /** Open an order's detail by clicking its View button. */
    @Step("Open order (View): {orderId}")
    public void openOrder(String orderId) {
        Locator card = orderCard(orderId);
        card.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
        card.locator("button:has-text('View')").first().click();
        page.waitForLoadState();
        log.info("Opened order detail: {}", orderId);
    }

    /** The status badge text shown on the order's list card (e.g. "Pending", "Processing"). */
    @Step("Read status badge for order: {orderId}")
    public String getStatusBadge(String orderId) {
        Locator badge = orderCard(orderId).locator("span[class*='_badge']").first();
        badge.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
        String txt = badge.textContent();
        return txt == null ? "" : txt.trim();
    }
}