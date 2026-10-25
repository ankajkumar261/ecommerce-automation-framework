package com.portfolio.ecommerce.website.pages.admin;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Page Object for the Admin Order detail view (after clicking View on an order).
 *
 * Confirmed selectors:
 *   change-status trigger : button containing "Change Status"
 *   status option         : button whose exact text is the status
 *                           (Pending, Confirmed, Processing, Ready for Dispatch,
 *                            Shipped, Out for Delivery, Delivered, Cancelled)
 */
public class AdminOrderDetailPage {

    private static final Logger log = LogManager.getLogger(AdminOrderDetailPage.class);
    private final Page page;

    public AdminOrderDetailPage(Page page) {
        this.page = page;
    }

    /**
     * Change the order status: open the "Change Status" dropdown and pick the target.
     */
    @Step("Change order status to: {target}")
    public void changeStatus(String target) {
        Locator trigger = page.locator("button:has-text('Change Status')").first();
        trigger.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
        trigger.click();

        Locator option = page.locator("button:text-is('" + target + "')").first();
        option.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
        option.click();

        page.waitForLoadState();
        log.info("Changed order status to: {}", target);
    }
}