package com.portfolio.ecommerce.website.pages.storefront;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Page Object for a Storefront product detail page (storefront.url/products/... — the base URL
 * comes from config-${env}.properties via ConfigReader, never hardcoded).
 *
 * Confirmed selectors:
 *   size buttons : button[class*='min-w-11']  (e.g. "Free Size", "S", "M"...).
 *                  Selected state adds border-primary/bg-primary classes.
 *   add to cart  : button text "Add to cart" (shopping-bag icon)
 *
 * After "Add to cart", a cart drawer (role=dialog "Your Cart") slides in — see CartDrawer.
 */
public class ProductDetailPage {

    private static final Logger log = LogManager.getLogger(ProductDetailPage.class);
    private final Page page;

    public ProductDetailPage(Page page) {
        this.page = page;
        registerPromoAutoDismiss();
    }

    /** Auto-dismiss the site-wide newsletter popup before any action. */
    private void registerPromoAutoDismiss() {
        try {
            Locator close = page.locator("button[class*='glass']:has(svg.lucide-x)");
            page.addLocatorHandler(close, h -> {
                try { h.click(); } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }

    /**
     * Select the first available size (required before adding to cart).
     * Out-of-stock sizes render as disabled buttons (title "Size X is out of stock"),
     * so pick the first ENABLED size — clicking a disabled one just times out.
     * If every size is out of stock the wait fails, which is the correct outcome:
     * a shopper can't add that product either.
     */
    @Step("Select the first available size")
    public void selectFirstSize() {
        page.locator("button[class*='min-w-11']").first()
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
        Locator size = page.locator("button[class*='min-w-11']:not([disabled])").first();
        size.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
        size.click();
        log.info("Selected first available size: {}", safeText(size));
    }

    /**
     * Click "Add to cart". The cart drawer opens afterwards.
     */
    @Step("Add to cart")
    public void addToCart() {
        Locator btn = page.locator("button:has-text('Add to cart')").first();
        btn.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
        btn.click();
        log.info("Clicked Add to cart");
    }

    private String safeText(Locator l) {
        try { String t = l.textContent(); return t == null ? "" : t.trim(); }
        catch (Exception e) { return ""; }
    }
}
