package com.portfolio.ecommerce.mobile.screens;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Screen Object for the Storefront app Cart screen.
 *
 * Layout (from the live app):
 *   title            StaticText  "Cart"
 *   item count       StaticText  "2 items"        <- the reliable size signal
 *   line item        StaticText  product name, colour, "₹3,999"
 *   quantity         StaticText  a bare number, level with the +/- controls
 *   decrement        Button      "trash" (label "Bin")  when quantity == 1
 *                    Button      "minus" (label "Remove") when quantity > 1
 *   increment        Button      "plus"  (label "Add")
 *   coupon           Button      "Apply"
 *   totals           StaticText  "Subtotal" / "Shipping" / "Total"
 *   checkout         Button      "Checkout"
 *
 * Removing a line item raises a confirmation with a "Remove" button.
 *
 * NOTE: the cart is server-side and PERSISTS between runs, so a test that asserts absolute
 * counts must clear() first — otherwise it inherits whatever the last run left behind.
 */
public class    CartScreen extends BaseScreen {

    private static final By CART_TITLE   = AppiumBy.accessibilityId("Cart");
    private static final By CART_TAB     = AppiumBy.accessibilityId("cart.fill");
    private static final By TRASH        = AppiumBy.accessibilityId("trash");
    private static final By MINUS        = AppiumBy.accessibilityId("minus");
    private static final By PLUS         = AppiumBy.accessibilityId("plus");
    private static final By CHECKOUT     = AppiumBy.accessibilityId("Checkout");
    private static final By APPLY_COUPON = AppiumBy.accessibilityId("Apply");

    /** "2 items" / "1 item" — the cart's own count of what it holds. */
    private static final By ITEM_COUNT = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeStaticText' AND (value ENDSWITH 'items' OR value ENDSWITH 'item')");

    /** The confirm button on the remove-item dialog. */
    private static final By REMOVE_CONFIRM = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND (name == 'Remove' OR label == 'Remove' "
                    + "OR name == 'Delete' OR label == 'Delete')");

    private static final Pattern COUNT_PATTERN = Pattern.compile("(\\d+)\\s+items?");

    public CartScreen(AppiumDriver driver) {
        super(driver);
    }

    // ── navigation ────────────────────────────────────────────

    /**
     * Tap the Cart tab and land on its root (see BaseScreen#openTab).
     *
     * Retries once via Home. Unwinding a deep stack can leave the tab tap landing somewhere
     * unexpected — a product detail pushed over the tab bar is the usual case — and bouncing
     * off Home resets both stacks so the second attempt starts from a known place.
     */
    @Step("Open the Cart tab")
    public void open() {
        openTab(CART_TAB);
        if (isVisible(CART_TITLE, shortWait)) return;

        new HomeScreen(driver).open();
        openTab(CART_TAB);
    }

    @Step("Wait for the Cart screen")
    public boolean isLoaded() {
        return isVisible(CART_TITLE, wait);
    }

    // ── reading the cart ──────────────────────────────────────

    /**
     * Total quantity across the cart, read from the app's own "N items" label.
     * Returns 0 when the cart is empty (the label disappears).
     */
    @Step("Read the cart item count")
    public int itemCount() {
        WebElement el = firstDisplayed(ITEM_COUNT);
        if (el == null) return 0;
        String text = el.getAttribute("value");
        if (text == null) return 0;
        Matcher m = COUNT_PATTERN.matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** Number of distinct line items (each row has exactly one decrement control). */
    @Step("Count the cart line items")
    public int lineItemCount() {
        return findAll(TRASH).size() + findAll(MINUS).size();
    }

    @Step("Check the cart is empty")
    public boolean isEmpty() {
        return lineItemCount() == 0 && itemCount() == 0;
    }

    @Step("Check the cart contains: {productName}")
    public boolean containsProduct(String productName) {
        if (productName == null || productName.isBlank()) return false;
        By byName = AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeStaticText' AND value == '"
                        + productName.replace("'", "\\'") + "'");
        return scrollIntoView(byName) != null;
    }

    /** The quantity shown on the first line item (the bare number beside the +/- controls). */
    @Step("Read the first line item's quantity")
    public int firstItemQuantity() {
        WebElement plus = firstDisplayed(PLUS);
        if (plus == null) return 0;
        int plusY = plus.getRect().getY();

        for (WebElement t : findAll(AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeStaticText' AND value MATCHES '^\\\\d+$'"))) {
            try {
                Rectangle r = t.getRect();
                // The quantity sits on the same row as the +/- buttons.
                if (Math.abs(r.getY() - plusY) < 40) {
                    return Integer.parseInt(t.getAttribute("value").trim());
                }
            } catch (Exception ignored) { }
        }
        return 0;
    }

    /** The subtotal as displayed, e.g. "₹7,998" — empty string when not shown. */
    @Step("Read the subtotal")
    public String subtotal() {
        return amountBesideLabel("Subtotal");
    }

    /** The subtotal as a number, e.g. 7998. -1 when not shown. */
    @Step("Read the subtotal amount")
    public int subtotalAmount() {
        return parseAmount(subtotal());
    }

    /** The cart's Total row as displayed. On Cart this equals the subtotal — shipping is
     *  "Calculated at checkout" here, and only Checkout adds the platform fee. */
    @Step("Read the cart total")
    public String total() {
        return amountBesideLabel("Total");
    }

    @Step("Read the cart total amount")
    public int totalAmount() {
        return parseAmount(total());
    }

    /**
     * The quantity on every line item, top to bottom.
     *
     * Read geometrically, like firstItemQuantity(): each row's bare number sits level with that
     * row's + control, and nothing in the tree ties them together otherwise.
     *
     * NOTE FOR ANYONE LOOKING FOR A CART BADGE: there isn't one. The Cart TAB button carries
     * value="1", but that marks the SELECTED TAB, not a count — the page-source dumps show
     * `magnifyingglass` carrying it while on Search and `cart.fill` carrying it while on Cart,
     * and it stays "1" with three units in the cart. The cart's own "N items" label
     * (itemCount()) is the only count the app publishes.
     */
    @Step("Read every line item's quantity")
    public List<Integer> lineQuantities() {
        List<Integer> quantities = new ArrayList<>();
        List<WebElement> numbers = findAll(AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeStaticText' AND value MATCHES '^\\\\d+$'"));

        for (WebElement plus : findAll(PLUS)) {
            try {
                int plusY = plus.getRect().getY();
                for (WebElement n : numbers) {
                    if (Math.abs(n.getRect().getY() - plusY) < 40) {
                        quantities.add(Integer.parseInt(n.getAttribute("value").trim()));
                        break;
                    }
                }
            } catch (Exception ignored) { }
        }
        return quantities;
    }

    /**
     * Every line item's ₹ price, top to bottom, as numbers.
     *
     * The summary rows (Subtotal / Shipping / Total) are ₹ amounts too, so they have to be
     * excluded. The boundary is the Subtotal label: everything above it is a line item,
     * everything from there down is the summary.
     *
     * Note the deliberate scrollIntoView FIRST. Excluding the summary by matching each label's
     * row only works if those labels are on screen — with the summary below the fold the
     * exclusion silently does nothing and the subtotal gets counted as a line price, which is
     * exactly the bug this replaced. Scrolling to the summary settles the layout before any
     * coordinate is read, so every y below comes from the same scroll position.
     */
    @Step("Read the line item prices")
    public List<Integer> lineItemPrices() {
        List<Integer> prices = new ArrayList<>();
        WebElement subtotalLabel = scrollIntoView(AppiumBy.accessibilityId("Subtotal"));
        int boundaryY = subtotalLabel == null ? Integer.MAX_VALUE : subtotalLabel.getRect().getY();

        for (WebElement amt : findAll(RUPEE_AMOUNT)) {
            try {
                if (amt.getRect().getY() < boundaryY) {
                    prices.add(parseAmount(amt.getAttribute("value")));
                }
            } catch (Exception ignored) { }
        }
        return prices;
    }

    // ── mutating the cart ─────────────────────────────────────

    @Step("Increase the first item's quantity")
    public void increaseQuantity() {
        WebElement plus = scrollIntoView(PLUS);
        if (plus == null) throw new IllegalStateException("No + control found — is the cart empty?");
        plus.click();
        pause(1200);
    }

    @Step("Decrease the first item's quantity")
    public void decreaseQuantity() {
        WebElement minus = scrollIntoView(MINUS);
        if (minus == null) {
            throw new IllegalStateException(
                    "No - control found. At quantity 1 the control becomes 'trash' (removal) "
                            + "rather than 'minus' — use removeFirstItem() instead.");
        }
        minus.click();
        pause(1200);
    }

    /**
     * Remove the first line item outright, confirming the dialog that follows.
     *
     * Waits on the line count actually dropping rather than on fixed sleeps. The removal is a
     * server round-trip whose latency varies, so a fixed pause is either too short (flaky) or
     * too long (slow) — and clearing runs at the start of most tests, so the cost is paid
     * dozens of times a run.
     */
    @Step("Remove the first line item")
    public void removeFirstItem() {
        int before = lineItemCount();

        WebElement trash = firstDisplayed(TRASH);
        if (trash == null) {
            // Quantity > 1: step it down until the control turns into the bin.
            for (int i = 0; i < 10 && trash == null; i++) {
                WebElement minus = firstDisplayed(MINUS);
                if (minus == null) break;
                minus.click();
                waitForQuantityToSettle();
                trash = firstDisplayed(TRASH);
            }
        }
        if (trash == null) return;
        trash.click();
        confirmRemoval();
        waitForLineCountBelow(before);
    }

    /** Poll until the cart holds fewer lines than it did, or we run out of patience. */
    private void waitForLineCountBelow(int previous) {
        long end = System.currentTimeMillis() + 8_000;
        while (System.currentTimeMillis() < end) {
            if (lineItemCount() < previous) return;
            pause(250);
        }
    }

    /** Brief settle after a +/- tap; the row re-renders before the server confirms. */
    private void waitForQuantityToSettle() {
        pause(600);
    }

    /** Empty the cart completely so a test starts from a known state. */
    @Step("Clear the cart")
    public void clear() {
        for (int i = 0; i < 15 && lineItemCount() > 0; i++) {
            removeFirstItem();
        }
    }

    private void confirmRemoval() {
        WebElement confirm = firstDisplayed(REMOVE_CONFIRM);
        if (confirm != null) {
            try { confirm.click(); } catch (Exception ignored) { }
        }
    }

    // ── checkout ──────────────────────────────────────────────

    @Step("Check Checkout is available")
    public boolean hasCheckout() {
        return isPresent(CHECKOUT);
    }

    @Step("Check the coupon field is available")
    public boolean hasCouponField() {
        return isPresent(APPLY_COUPON);
    }

    // ── coupon ────────────────────────────────────────────────

    /** The coupon box. Matched by TYPE — its `value` is the placeholder until we type. */
    private static final By COUPON_FIELD = AppiumBy.className("XCUIElementTypeTextField");

    /**
     * Type a coupon code. Apply is disabled (enabled="false") while the box is empty, so this
     * is what makes the button pressable at all.
     */
    @Step("Enter coupon code: {code}")
    public void enterCoupon(String code) {
        WebElement field = scrollIntoView(COUPON_FIELD);
        if (field == null) throw new IllegalStateException("Coupon field not found on the cart");
        field.click();
        field.clear();
        field.sendKeys(code);
        hideKeyboard();
    }

    @Step("Check the Apply button is enabled")
    public boolean isApplyEnabled() {
        WebElement apply = firstDisplayed(APPLY_COUPON);
        return apply != null && apply.isEnabled();
    }

    @Step("Tap Apply")
    public void applyCoupon() {
        WebElement apply = scrollIntoView(APPLY_COUPON);
        if (apply == null) throw new IllegalStateException("Apply button not found on the cart");
        apply.click();
        pause(2000);   // the code goes to the server for validation
    }

    @Step("Tap Checkout")
    public void tapCheckout() {
        WebElement btn = scrollIntoView(CHECKOUT);
        if (btn == null) throw new IllegalStateException("Checkout button not found");
        btn.click();
        pause(1400);
    }
}
