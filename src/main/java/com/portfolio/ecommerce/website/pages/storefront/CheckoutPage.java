package com.portfolio.ecommerce.website.pages.storefront;

import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Page Object for the Storefront checkout page (after clicking Checkout in the cart drawer).
 *
 * Confirmed selectors:
 *   address option : button[class*='text-left'] (saved address card; selected = border-primary)
 *   COD option     : label containing "Cash on Delivery" (radio, checked by default)
 *   place order    : button text "Place order"
 *
 * Order confirmation: best-effort detection (URL change to an order/success page, a success
 * toast, or confirmation text). Refine isOrderConfirmed() once the exact element is known.
 *
 * NOTE: assumes the customer already has a saved address (this account has "Work").
 * Placing the order creates a REAL order. COD is always safe to automate. Online payment
 * (Razorpay) is automated ONLY in test mode (dev env, no real money) via
 * {@link #selectOnlinePayment()} + {@link #completeRazorpayTestPayment(String)}, and tests
 * must gate it behind ConfigReader#isPaymentTestMode so it never runs against a live gateway.
 */
public class CheckoutPage {

    private static final Logger log = LogManager.getLogger(CheckoutPage.class);
    private final Page page;

    public CheckoutPage(Page page) {
        this.page = page;
        try {
            Locator close = page.locator("button[class*='glass']:has(svg.lucide-x)");
            page.addLocatorHandler(close, h -> { try { h.click(); } catch (Exception ignored) {} });
        } catch (Exception ignored) {}
    }

    /**
     * Select the first saved address. (One is usually pre-selected; clicking ensures it.)
     */
    @Step("Select the first saved address")
    public void selectFirstAddress() {
        Locator addr = page.locator("button[class*='text-left'][class*='rounded-lg']").first();
        addr.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
        addr.click();
        log.info("Selected saved address");
    }

    /**
     * Whether the customer has a saved address to check out with — checks (without clicking,
     * short timeout) that an address card is present. Checkout can't proceed without one, so
     * tests use this to skip gracefully when the account has no saved address.
     */
    @Step("Check a saved address is available")
    public boolean hasSavedAddress() {
        try {
            Locator addr = page.locator("button[class*='text-left'][class*='rounded-lg']").first();
            addr.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(6000));
            return addr.isVisible();
        } catch (Exception e) {
            log.info("No saved address found on checkout — treating as unavailable");
            return false;
        }
    }

    /**
     * Whether Cash on Delivery is offered on this checkout — checks (without clicking)
     * that the COD option is present and visible. Online payment can't be automated
     * safely (no gateway test mode), so tests use this to skip if COD isn't offered.
     */
    @Step("Check if Cash on Delivery is available")
    public boolean isCodAvailable() {
        try {
            Locator cod = page.locator("label:has-text('Cash on Delivery')").first();
            cod.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
            boolean available = cod.isVisible();
            log.info("Cash on Delivery available: {}", available);
            return available;
        } catch (Exception e) {
            log.info("Cash on Delivery option not found — treating as unavailable");
            return false;
        }
    }

    /**
     * Choose Cash on Delivery (pre-checked by default; clicking ensures it).
     */
    @Step("Select Cash on Delivery")
    public void selectCOD() {
        Locator cod = page.locator("label:has-text('Cash on Delivery')").first();
        cod.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
        cod.click();
        log.info("Selected Cash on Delivery");
    }

    // ── Online payment (Razorpay TEST mode) ─────────────────────────────────────
    // Selectors mapped live against the dev env (Razorpay test mode, red "Test Mode"
    // ribbon). The widget renders in a cross-origin iframe (api.razorpay.com/v1/checkout);
    // we reach its elements by scanning page.frames() for a razorpay.com URL. Only ever
    // runs when payment.testmode=true (see ConfigReader#isPaymentTestMode) — never live.

    /**
     * Whether the "Online payment" option (UPI / cards / netbanking) is offered — checks
     * presence without clicking. Some products are online-only (no COD).
     */
    @Step("Check if Online payment is available")
    public boolean isOnlinePaymentAvailable() {
        try {
            Locator online = page.locator("label:has-text('Online payment')").first();
            online.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
            boolean available = online.isVisible();
            log.info("Online payment available: {}", available);
            return available;
        } catch (Exception e) {
            log.info("Online payment option not found — treating as unavailable");
            return false;
        }
    }

    /**
     * Choose the "Online payment" option (UPI, cards, netbanking → Razorpay).
     */
    @Step("Select Online payment")
    public void selectOnlinePayment() {
        Locator online = page.locator("label:has-text('Online payment')").first();
        online.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
        online.click();
        log.info("Selected Online payment");
    }

    /**
     * Drive the Razorpay TEST-mode checkout to completion with a test card
     * (4111 1111 1111 1111). Assumes {@link #selectOnlinePayment()} then
     * {@link #placeOrder()} have been called and the Razorpay widget has opened.
     *
     * Flow: contact (phone) → Continue → Cards tab → card fields → Continue →
     * test authorization (Success). Best-effort at the final authorization step:
     * clicks a "Success" simulator button if present, otherwise assumes auto-approval.
     *
     * @param cardNumber test card, e.g. "4111 1111 1111 1111"
     */
    @Step("Complete Razorpay test-mode card payment")
    public void completeRazorpayTestPayment(String cardNumber) {
        Frame rzp = waitForRazorpayFrame(45000);
        if (rzp == null) {
            throw new RuntimeException("Razorpay checkout iframe did not appear");
        }
        page.waitForTimeout(3000);
        log.info("Razorpay screen: {}", razorpayScreen());

        // Screen 1 — contact details (phone required; email is usually prefilled).
        // Must be a VALID Indian mobile: Razorpay rejects all-same-digit numbers. Also, the
        // field is a React controlled input whose validator runs on real keystrokes — a plain
        // fill() sets the value but leaves it "invalid", so TYPE it character-by-character.
        Locator phone = inRazorpay("input[name='contact']");
        if (phone != null) {
            try {
                phone.click();
                phone.fill("");
                // Use a realistic, non-sequential mobile: Razorpay rejects patterned numbers
                // like 9876543210 ("Please enter a valid mobile number").
                phone.pressSequentially("8628813869",
                        new Locator.PressSequentiallyOptions().setDelay(60));
                // Razorpay re-validates the number on BLUR — Tab out so the "invalid"
                // error clears and the Continue button enables before we click it.
                phone.press("Tab");
                page.waitForTimeout(800);
                log.info("Razorpay contact entered; screen: {}", razorpayScreen());
            } catch (Exception ignored) {}
        }
        // Continue is disabled until the number validates, so wait for it to enable.
        Locator contactContinue = inRazorpay("button:has-text('Continue')");
        if (contactContinue != null) {
            try {
                contactContinue.waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE).setTimeout(5000));
                long deadline = System.currentTimeMillis() + 5000;
                while (System.currentTimeMillis() < deadline && !contactContinue.isEnabled()) {
                    page.waitForTimeout(200);
                }
                contactContinue.click();
            } catch (Exception e) {
                try { contactContinue.click(new Locator.ClickOptions().setForce(true)); } catch (Exception ignored) {}
            }
            page.waitForTimeout(2500);
        }

        // Screen 2 — switch to the Cards method. React only reacts to a dispatched
        // click event here (plain/force clicks do not switch the panel).
        Locator cardTab = inRazorpay("[data-testid='card']");
        if (cardTab != null) {
            try { cardTab.dispatchEvent("click"); } catch (Exception ignored) {}
            page.waitForTimeout(2500);
        }

        // Card form (in the top razorpay frame). Name field appears after the number.
        Locator number = inRazorpay("input[name='card.number']");
        if (number == null) {
            throw new RuntimeException("Razorpay card-number field not found (screen: "
                    + razorpayScreen() + ")");
        }
        // Card fields are React controlled inputs (same as contact): TYPE them so the
        // formatters/validators fire, otherwise the CTA stays disabled.
        typeInto(number, cardNumber);
        page.waitForTimeout(400);
        Locator expiry = inRazorpay("input[name='card.expiry']");
        if (expiry != null) typeInto(expiry, "1230");
        Locator cvv = inRazorpay("input[name='card.cvv']");
        if (cvv != null) typeInto(cvv, "123");
        Locator name = inRazorpay("input[name='card.name']");
        if (name != null) typeInto(name, "Test Customer");
        page.waitForTimeout(600);
        log.info("Filled Razorpay test card");

        // The card CTA is a "Continue" (or "Pay ₹…") button — NOT necessarily type=submit.
        Locator cardCta = inRazorpay("button:has-text('Continue'), button:has-text('Pay ')");
        if (cardCta != null) {
            try {
                long deadline = System.currentTimeMillis() + 4000;
                while (System.currentTimeMillis() < deadline && !cardCta.isEnabled()) {
                    page.waitForTimeout(200);
                }
            } catch (Exception ignored) {}
            cardCta.click(new Locator.ClickOptions().setForce(true));
            log.info("Submitted card details");
        } else {
            log.warn("Card CTA (Continue/Pay) not found on the card screen");
        }

        // "Save your card for future payments?" prompt — decline to continue.
        page.waitForTimeout(2500);
        Locator saveCard = inRazorpay(
                "button:has-text('Maybe later'), button:has-text('Skip'), button:has-text('No, thanks')");
        if (saveCard != null) {
            try { saveCard.click(new Locator.ClickOptions().setForce(true)); log.info("Dismissed save-card prompt"); }
            catch (Exception ignored) {}
            page.waitForTimeout(2000);
        }

        // Razorpay TEST bank simulator ("Success" / "Failure") — opens as a SEPARATE popup
        // window (…/v1/gateway/mocksharp/payment), not an in-page iframe. Click Success there.
        clickTestBankSuccess();
        page.waitForTimeout(3000);
        page.waitForLoadState();
    }

    /**
     * Click "Success" on the Razorpay test-mode bank simulator. It opens as a popup page
     * (…/v1/gateway/mocksharp/payment); falls back to an in-frame Success button if the
     * simulator renders inside the checkout instead.
     */
    private void clickTestBankSuccess() {
        String sel = "button:has-text('Success'), input[value='Success'], a:has-text('Success')";
        for (int i = 0; i < 25; i++) {
            // Popup / separate tab (the demo "Razorpay Bank" page).
            for (Page p : page.context().pages()) {
                String u = p.url();
                if (u != null && (u.contains("mocksharp") || u.contains("/v1/gateway/"))) {
                    try {
                        Locator s = p.locator(sel).first();
                        s.waitFor(new Locator.WaitForOptions()
                                .setState(WaitForSelectorState.VISIBLE).setTimeout(4000));
                        s.click();
                        log.info("Clicked test-bank Success (popup)");
                        page.waitForTimeout(3000);
                        return;
                    } catch (Exception ignored) {}
                }
            }
            // In-frame fallback.
            Locator inframe = inRazorpay("button:has-text('Success'), a:has-text('Success')");
            if (inframe != null) {
                try {
                    inframe.click(new Locator.ClickOptions().setForce(true));
                    log.info("Clicked test-bank Success (in-frame)");
                    return;
                } catch (Exception ignored) {}
            }
            // Payment already returned to the storefront?
            if (waitForRazorpayFrame(0) == null && !page.url().contains("checkout")) {
                log.info("Returned to storefront before the simulator — assuming auto-approval");
                return;
            }
            page.waitForTimeout(1500);
        }
        log.warn("Test-bank Success button not found — payment may not have completed");
    }

    /**
     * Types a value into a React-controlled input character-by-character so its
     * onChange formatters/validators fire (a plain fill() leaves the field "invalid").
     */
    private void typeInto(Locator field, String value) {
        try {
            field.click();
            field.fill("");
            field.pressSequentially(value, new Locator.PressSequentiallyOptions().setDelay(55));
        } catch (Exception ignored) {}
    }

    /** The active Razorpay screen-container text (single line) — for logging/waits. */
    private String razorpayScreen() {
        Locator sc = inRazorpay("[data-testid='screen-container']");
        if (sc == null) return "<none>";
        try {
            String t = sc.innerText().replaceAll("\\s+", " ").trim();
            return t.substring(0, Math.min(160, t.length()));
        } catch (Exception e) { return "<err>"; }
    }

    /** Poll for the Razorpay checkout iframe; returns null if it never appears. */
    private Frame waitForRazorpayFrame(int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        do {
            for (Frame f : page.frames()) {
                String u = f.url();
                if (u != null && u.contains("api.razorpay.com")) return f;
            }
            if (timeoutMs > 0) page.waitForTimeout(1000);
        } while (System.currentTimeMillis() < deadline);
        return null;
    }

    /**
     * First locator matching {@code selector} inside any razorpay.com frame (the widget
     * spawns js.stripe.com / m.stripe.network helper frames we skip), or null if none.
     */
    private Locator inRazorpay(String selector) {
        Locator firstAny = null;
        for (Frame f : page.frames()) {
            String u = f.url();
            if (u == null || !u.contains("razorpay.com")) continue;
            try {
                Locator l = f.locator(selector);
                int n = l.count();
                for (int i = 0; i < n; i++) {
                    Locator cand = l.nth(i);
                    if (firstAny == null) firstAny = cand;
                    // Prefer the VISIBLE element — Razorpay's nested frames often carry a
                    // hidden duplicate of the same field, and typing into the hidden one
                    // leaves the visible input's React validation stuck on "invalid".
                    try { if (cand.isVisible()) return cand; } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }
        return firstAny;
    }

    /**
     * Place the order (creates a real COD order).
     */
    @Step("Place order")
    public void placeOrder() {
        Locator btn = page.locator("button:has-text('Place order')").first();
        btn.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
        btn.click();
        log.info("Clicked Place order");
        page.waitForLoadState();
    }

    /**
     * Read the order ID from the confirmation/detail page.
     * The heading looks like:  <h1>Order ORD-1782975927548—649</h1>
     * (note: no '#', and the ID can contain an em-dash '—' as well as '-').
     *
     * Dismisses the promo popup first (it can re-appear over the heading), then matches the
     * order heading flexibly and extracts the full "ORD-..." token.
     */
    @Step("Capture order ID from confirmation")
    public String getOrderId() {

        // 1) Prefer an explicit ORD- token anywhere in a heading.
        try {
            Locator ordHeading = page.locator(
                            "h1, h2, h3, [class*='title'], [class*='heading']")
                    .filter(new Locator.FilterOptions()
                            .setHasText(java.util.regex.Pattern.compile("ORD-", java.util.regex.Pattern.CASE_INSENSITIVE)))
                    .first();
            ordHeading.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
            String id = extractOrderId(ordHeading.textContent());
            if (!id.isEmpty()) {
                log.info("Captured order ID: {}", id);
                return id;
            }
        } catch (Exception ignored) { }

        // 2) Any heading containing "Order".
        try {
            Locator anyOrder = page.locator("h1, h2, h3")
                    .filter(new Locator.FilterOptions()
                            .setHasText(java.util.regex.Pattern.compile("order", java.util.regex.Pattern.CASE_INSENSITIVE)))
                    .first();
            anyOrder.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
            String id = extractOrderId(anyOrder.textContent());
            if (!id.isEmpty()) {
                log.info("Captured order ID: {}", id);
                return id;
            }
        } catch (Exception ignored) { }

        // 3) Last resort: scan the whole page text for an ORD- token.
        try {
            String body = page.locator("body").textContent();
            String id = extractOrderId(body);
            if (!id.isEmpty()) {
                log.info("Captured order ID (from page text): {}", id);
                return id;
            }
        } catch (Exception ignored) { }

        log.warn("Could not capture an order ID from the confirmation page");
        return "";
    }

    /**
     * Pull an ORD-xxxx token if present (the ID may contain digits, normal hyphens, and
     * em-dashes '—', e.g. "ORD-1782975927548—649"); else the text after a '#'; else "".
     */
    private String extractOrderId(String raw) {
        if (raw == null) return "";
        String text = raw.trim();
        // ORD, then any run of digits / hyphen / en-dash / em-dash.
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("ORD[-\\u2012\\u2013\\u2014\\u2015][0-9\\u2012\\u2013\\u2014\\u2015-]+")
                .matcher(text);
        if (m.find()) return m.group();
        int hash = text.indexOf('#');
        if (hash >= 0) {
            String after = text.substring(hash + 1).trim();
            int sp = after.indexOf(' ');
            return (sp > 0) ? after.substring(0, sp) : after;
        }
        return "";
    }

    /**
     * Confirm the order was placed. Primary signal: the "Order placed" status badge
     * that appears after placing (a yellow pill). Falls back to a URL change to an
     * order/success route, then a success toast.
     */
    @Step("Verify order confirmed")
    public boolean isOrderConfirmed() {
        // 1) Primary: the "Order placed" badge.
        try {
            page.locator(":text-is('Order placed')").first()
                    .waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
            log.info("Order confirmed ('Order placed' badge shown)");
            return true;
        } catch (Exception ignored) {
            // fall through
        }

        // 2) URL signal
        try {
            page.waitForURL(u -> {
                String s = u.toLowerCase();
                return s.contains("order") || s.contains("success")
                        || s.contains("confirm") || s.contains("thank");
            }, new Page.WaitForURLOptions().setTimeout(10000));
            log.info("Order confirmed (URL: {})", page.url());
            return true;
        } catch (Exception ignored) {
            // fall through
        }

        // 3) Toast / confirmation text signal
        try {
            page.locator(
                            "[data-sonner-toast][data-type='success'], "
                                    + ":text-matches('order placed|thank you|order confirmed|successfully placed', 'i')")
                    .first()
                    .waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
            log.info("Order confirmed (success message shown)");
            return true;
        } catch (Exception e) {
            log.warn("Could not confirm order placement");
            return false;
        }
    }
}