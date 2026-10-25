package com.portfolio.ecommerce.website.pages.storefront;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Page Object for the cart drawer that slides in after "Add to cart".
 *
 * Confirmed structure (role=dialog):
 *   title        : h2 "Your Cart (N)"
 *   line item    : a[href^='/products/'] (product name) + "Size: ..." + qty + price
 *   subtotal/total
 *   checkout     : button text "Checkout"  (bg-foreground)
 *   view cart    : a[href='/cart'] "View cart"
 *   close        : button with lucide-x ("Close")
 */
public class CartDrawer {

    private static final Logger log = LogManager.getLogger(CartDrawer.class);
    private final Page page;

    // Scope everything to the cart dialog.
    private static final String DIALOG = "[role='dialog']:has-text('Your Cart')";

    public CartDrawer(Page page) {
        this.page = page;
    }

    /** Wait for the cart drawer to be open. */
    @Step("Wait for cart drawer")
    public void waitForOpen() {
        page.locator(DIALOG).first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
        log.info("Cart drawer is open");
    }

    /** Name of the first item in the cart. */
    @Step("Read first cart item name")
    public String firstItemName() {
        Locator item = page.locator(DIALOG + " a[href^='/products/']").first();
        item.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
        String name = item.textContent();
        return name == null ? "" : name.trim();
    }

    /** True if an item whose name contains the given text is in the cart. */
    @Step("Verify cart contains: {productName}")
    public boolean containsProduct(String productName) {
        try {
            page.locator(DIALOG + " a[href^='/products/']", new Page.LocatorOptions()
                            .setHasText(productName)).first()
                    .waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Proceed to checkout from the drawer. */
    @Step("Click Checkout")
    public void clickCheckout() {
        page.locator(DIALOG + " button:has-text('Checkout')").first().click();
        page.waitForLoadState();
        log.info("Clicked Checkout");
    }

    // ── cart management ───────────────────────────────────────

    /** The line-item row for a given product (scoped so other cart items don't interfere). */
    private Locator itemRow(String productName) {
        return page.locator(DIALOG + " div.flex.gap-3")
                .filter(new Locator.FilterOptions().setHasText(productName)).first();
    }

    /** Current quantity shown for the product. */
    @Step("Read quantity for: {productName}")
    public int getQuantity(String productName) {
        String q = itemRow(productName).locator("span[class*='w-8']").first().textContent();
        try {
            return Integer.parseInt(q == null ? "" : q.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Increase the quantity of the product by one (waits for the number to update). */
    @Step("Increase quantity for: {productName}")
    public void increaseQuantity(String productName) {
        changeQuantity(productName, "Increase", +1);
    }

    /** Decrease the quantity of the product by one (waits for the number to update). */
    @Step("Decrease quantity for: {productName}")
    public void decreaseQuantity(String productName) {
        changeQuantity(productName, "Decrease", -1);
    }

    private void changeQuantity(String productName, String ariaLabel, int delta) {
        int before = getQuantity(productName);
        int expected = before + delta;
        Locator btn = itemRow(productName).locator("button[aria-label='" + ariaLabel + "']").first();

        Boolean disabled = btn.isDisabled();
        if (Boolean.TRUE.equals(disabled)) {
            log.warn("{} button is DISABLED for {} (qty {} — stock/min limit?)",
                    ariaLabel, productName, before);
        }

        btn.scrollIntoViewIfNeeded();
        btn.click();
        if (!quantityReached(productName, expected, 4000)) {
            // Click may have been lost during the cart's async re-render — retry once.
            log.info("Quantity not yet {}, retrying {} click", expected, ariaLabel);
            try { btn.click(); } catch (Exception ignored) {}
            quantityReached(productName, expected, 6000);
        }
        log.info("{} quantity for {} -> {} (was {})", ariaLabel, productName,
                getQuantity(productName), before);
    }

    /** Wait until the product's quantity reads the expected value; true if reached. */
    private boolean quantityReached(String productName, int expected, int timeoutMs) {
        try {
            itemRow(productName)
                    .locator("span[class*='w-8']:text-is('" + expected + "')")
                    .first()
                    .waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Remove the product from the cart. */
    @Step("Remove from cart: {productName}")
    public void removeItem(String productName) {
        itemRow(productName).locator("button[aria-label='Remove']").first().click();
        page.waitForTimeout(700);
        log.info("Removed from cart: {}", productName);
    }

    /** Close the cart drawer (the X in the top-right of the dialog). */
    @Step("Close cart drawer")
    public void close() {
        try {
            page.locator(DIALOG + " button:has(svg.lucide-x)").first().click();
            page.locator(DIALOG).first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN).setTimeout(6000));
            log.info("Closed cart drawer");
        } catch (Exception e) {
            log.warn("Could not close cart drawer: {}", e.getMessage());
        }
    }

    /** Number of line items currently in the cart. */
    @Step("Count cart items")
    public int itemCount() {
        return page.locator(DIALOG + " a[href^='/products/']").count();
    }

    /**
     * Reduce the product's quantity down to 1 (the cart persists across runs and accumulates,
     * so the starting quantity is unknown). Clicks Decrease until it reads 1.
     */
    @Step("Reduce {productName} quantity to 1")
    public void reduceToOne(String productName) {
        int q = getQuantity(productName);
        int guard = 0;
        while (q > 1 && guard++ < 25) {
            decreaseQuantity(productName);
            int nq = getQuantity(productName);
            if (nq == q) break;   // no change — stop to avoid looping forever
            q = nq;
        }
        log.info("Normalized {} quantity to {}", productName, q);
    }

    /** True once the product is no longer in the cart. */
    @Step("Verify removed from cart: {productName}")
    public boolean isProductGone(String productName) {
        try {
            page.locator(DIALOG + " a[href^='/products/']", new Page.LocatorOptions()
                            .setHasText(productName)).first()
                    .waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.HIDDEN).setTimeout(8000));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Cart subtotal as a number (e.g. "₹598" -> 598). Returns -1 if not readable. */
    @Step("Read cart subtotal")
    public int getSubtotalAmount() {
        try {
            String raw = page.locator(
                            DIALOG + " div.justify-between:has(span:text-is('Subtotal')) span").last()
                    .textContent();
            String digits = (raw == null ? "" : raw).replaceAll("[^0-9]", "");
            return digits.isEmpty() ? -1 : Integer.parseInt(digits);
        } catch (Exception e) {
            return -1;
        }
    }
}