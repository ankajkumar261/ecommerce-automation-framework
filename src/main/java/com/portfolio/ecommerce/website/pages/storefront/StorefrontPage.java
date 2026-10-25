package com.portfolio.ecommerce.website.pages.storefront;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Page Object for the public Storefront (storefront.url from config-${env}.properties).
 *
 * Confirmed structure (from DOM):
 *   login entry  : button text "Email & Password" (lucide-lock icon)
 *   email field  : input#email   (placeholder "you@example.test")
 *   password     : input#password
 *   login submit : button[type='submit'] text "Login"
 *   product list : storefront home / /search?tab=products
 *   product card : a.block[href^="/products/"]
 *   product name : h3.line-clamp-1
 *   storefront     : p.uppercase      (e.g. "Jagmeet Tailors")
 *
 * NOTE: browsing the storefront is PUBLIC, so product visibility can be verified
 * without logging in. login() is provided for a logged-in shopper flow and is
 * best-effort: if the login UI isn't found, it logs a warning and continues,
 * since the visibility assertion does not require an authenticated session.
 */
public class StorefrontPage {

    private static final Logger log = LogManager.getLogger(StorefrontPage.class);
    private final Page page;

    /**
     * Script injected into the context so it runs on EVERY navigation/refresh, before page
     * scripts. It watches the DOM and clicks the promo's close button (glass button with the
     * lucide-x icon) the instant the modal appears — so the popup never blocks an action,
     * even though it re-shows on every page load.
     */
    static final String PROMO_KILLER_SCRIPT =
            "() => {"
                    + "  function kill(){try{var bs=document.querySelectorAll('button');"
                    + "    for(var i=0;i<bs.length;i++){var b=bs[i];"
                    + "      if((b.className||'').indexOf('glass')>-1 && b.querySelector('svg.lucide-x')){b.click();}}}catch(e){}}"
                    + "  try{var mo=new MutationObserver(kill);"
                    + "    function start(){if(document.body){mo.observe(document.body,{childList:true,subtree:true});kill();}}"
                    + "    if(document.readyState==='loading'){document.addEventListener('DOMContentLoaded',start);}else{start();}"
                    + "    setInterval(kill,800);}catch(e){}"
                    + "}";

    public StorefrontPage(Page page) {
        this.page = page;
        // 1) Proactive: kill the popup on every future navigation/refresh via an init script.
        try { page.context().addInitScript(PROMO_KILLER_SCRIPT); } catch (Exception ignored) {}
        // 2) Reactive: also dismiss it before any action on the already-loaded page.
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
     * Navigate to the storefront home.
     */
    @Step("Open storefront: {storefrontUrl}")
    public void open(String storefrontUrl) {
        page.navigate(storefrontUrl, new Page.NavigateOptions()
                .setTimeout(45000)
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        log.info("Opened storefront: {}", storefrontUrl);
    }

    /**
     * Log in as a customer using the confirmed flow:
     *   go to /login -> click "Email & Password" -> fill #email/#password -> click "Login".
     *
     * Best-effort: if any step fails, log a warning and continue. Storefront browsing is
     * public, so product visibility can still be verified without an authenticated session.
     */
    @Step("Storefront login as: {email}")
    public void login(String storefrontBaseUrl, String email, String password) {
        try {
            // Header "Login" is just <a href="/login"> — navigate straight there.
            String loginUrl = trimTrailingSlash(storefrontBaseUrl) + "/login";
            page.navigate(loginUrl, new Page.NavigateOptions()
                    .setTimeout(45000)
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

            Locator emailPwBtn = page.locator("button:has-text('Email & Password')").first();
            emailPwBtn.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
            emailPwBtn.click();
            log.info("Selected 'Email & Password' login");

            page.locator("#email").fill(email);
            page.locator("#password").fill(password);
            page.locator("button[type='submit']:has-text('Login')").first().click();

            page.waitForLoadState();
            log.info("Submitted storefront login for: {}", email);
        } catch (Exception e) {
            log.warn("Storefront login skipped/failed ({}). Continuing as guest — "
                    + "browsing is public so visibility can still be verified.", e.getMessage());
        }
    }

    /**
     * Open the storefront home / all-products listing. Use this instead of the old
     * T-Shirt category (that category was removed). Combine with openProductByName(...)
     * to reach a specific product, or isProductVisible(...) to verify one appears.
     */
    @Step("Open storefront home")
    public void openStorefront(String storefrontBaseUrl) {
        page.navigate(trimTrailingSlash(storefrontBaseUrl), new Page.NavigateOptions()
                .setTimeout(45000)
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        log.info("Opened storefront home");
        dismissPromoIfPresent();
    }

    /**
     * Open the product search results for a query (finds a product by name regardless of
     * category). Path: /search?tab=products&q=<query>
     */
    @Step("Search products for: {query}")
    public void searchProducts(String storefrontBaseUrl, String query) {
        // The storefront search route filters on the `search` param (NOT `q`); with `q` it ignored
        // the term and returned the full, paginated catalogue. Spaces must be %20 — the SPA does
        // not treat '+' as a space, so URLEncoder's '+' would return no results.
        String url = trimTrailingSlash(storefrontBaseUrl)
                + "/search?tab=products&search=" + query.replace(" ", "%20");
        page.navigate(url, new Page.NavigateOptions()
                .setTimeout(45000)
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        log.info("Searched products for: {}", query);
        dismissPromoIfPresent();
    }

    /**
     * Backward-compatible alias so existing callers keep working: the old T-Shirt
     * category was removed, so this now just opens the storefront home listing.
     * @deprecated use openStorefront(...) or searchProducts(...) instead.
     */
    @Deprecated
    @Step("Open storefront (was: T-Shirt category)")
    public void openTShirtCategory(String storefrontBaseUrl) {
        openStorefront(storefrontBaseUrl);
    }

    /** Open a product by its displayed name from the current listing (scrolls to lazy-load it). */
    @Step("Open product by name: {name}")
    public String openProductByName(String name) {
        Locator cards = page.locator("a.block[href^='/products/']");
        cards.first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(15000));

        // The listing lazy-loads as you scroll, so the named card may not be in the DOM yet.
        // Scroll down until it appears (or we run out of tries).
        Locator match = cards.filter(new Locator.FilterOptions().setHasText(name)).first();
        for (int i = 0; i < 12 && match.count() == 0; i++) {
            page.mouse().wheel(0, 2200);
            page.waitForTimeout(500);
            match = page.locator("a.block[href^='/products/']")
                    .filter(new Locator.FilterOptions().setHasText(name)).first();
        }
        match.scrollIntoViewIfNeeded();
        match.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(10000));

        String actual = match.locator("h3").first().textContent();
        actual = (actual == null) ? name : actual.trim();
        match.click();
        page.waitForLoadState();
        log.info("Opened product by name: {}", actual);
        return actual;
    }


    /**
     * Click the first SHOPPABLE product in the listing and return its displayed name.
     * Product cards are <a class="block" href="/products/..."> with the name in an <h3>.
     *
     * Skips products our own automation created (names starting with "QA "), because those
     * throwaway products can have Cash-on-Delivery disabled / no stock. Falls back to the
     * very first card if every visible product is a QA one.
     */
    @Step("Open the first shoppable product in the listing")
    public String openFirstProduct() {
        Locator cards = page.locator("a.block[href^='/products/']");
        cards.first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(15000));

        int count = cards.count();
        Locator chosen = null;
        String name = "";
        for (int i = 0; i < count; i++) {
            Locator card = cards.nth(i);
            String n = card.locator("h3").first().textContent();
            n = (n == null) ? "" : n.trim();
            if (!n.startsWith("QA ")) {        // skip automation's throwaway products
                chosen = card;
                name = n;
                break;
            }
        }
        if (chosen == null) {                  // everything visible was a QA product
            chosen = cards.first();
            String n = chosen.locator("h3").first().textContent();
            name = (n == null) ? "" : n.trim();
            log.warn("Only QA products found in listing; falling back to first: {}", name);
        }

        chosen.click();
        page.waitForLoadState();
        log.info("Opened product: {}", name);
        return name;
    }

    /**
     * Verify a product with the given name is visible in the listing.
     * Matches the product-title <h3> by case-insensitive text (the storefront may
     * title-case the displayed name). Reloads once if not found on first pass, to
     * absorb any brief indexing delay after approval.
     *
     * @return true if the product's title is visible.
     */
    @Step("Verify product is visible on storefront: {productName}")
    public boolean isProductVisible(String productName) {
        if (waitForTitle(productName, 12000)) {
            log.info("Product visible on storefront: {}", productName);
            return true;
        }

        // Not found yet — reload once and retry (newly approved products can lag briefly).
        log.info("Product not found on first pass; reloading and retrying: {}", productName);
        page.reload(new Page.ReloadOptions()
                .setTimeout(45000)
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

        if (waitForTitle(productName, 12000)) {
            log.info("Product visible on storefront after reload: {}", productName);
            return true;
        }

        log.warn("Product NOT visible on storefront: {}", productName);
        return false;
    }

    // ── helpers ───────────────────────────────────────────────

    /**
     * Best-effort dismiss of the promo/newsletter pop-up that sometimes appears
     * (a glass modal with an "X" close button, lucide-x icon). Clicks the X if it's
     * showing, otherwise presses Escape. Never throws — if there's no modal, does nothing.
     * Call this after a page load before interacting with the page.
     */
    @Step("Dismiss promo modal if present")
    public void dismissPromoIfPresent() {
        try {
            Locator close = page.locator("button[class*='glass']:has(svg.lucide-x)").first();
            if (close.isVisible()) {
                close.click();
                log.info("Dismissed promo modal");
                return;
            }
        } catch (Exception ignored) {
            // no modal / not clickable — fall through
        }
        try {
            page.keyboard().press("Escape");
        } catch (Exception ignored) {
            // nothing to dismiss
        }
    }

    private boolean waitForTitle(String productName, int timeoutMs) {
        try {
            Locator title = page.locator("h3", new Page.LocatorOptions().setHasText(productName))
                    .first();
            title.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String trimTrailingSlash(String s) {
        return (s != null && s.endsWith("/")) ? s.substring(0, s.length() - 1) : s;
    }
}