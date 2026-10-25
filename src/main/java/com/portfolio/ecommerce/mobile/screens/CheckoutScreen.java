package com.portfolio.ecommerce.mobile.screens;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Screen Object for the Storefront app Checkout screen (reached from Cart -> Checkout).
 *
 * Layout (from the live dev app):
 *   header           StaticText  "Checkout"
 *   address section  StaticText  "Deliver to"
 *                    Button      "<name>, <full address>"   <- the saved address row,
 *                                carrying the Image "mappin.and.ellipse"
 *   items            StaticText  "Items (1)" + a ₹ amount
 *   totals           StaticText  "Shipping (storefront-managed)" / "Platform fee" / "Total"
 *   notes            TextField   placeholder "Anything we should know?"  <- the ONLY field here
 *   payment          StaticText  "Payment"
 *                    Button      "Cash on delivery, Pay when your order arrives."
 *                    Button      "Pay online, UPI, cards, netbanking via Razorpay."
 *   place order      Button      "Place order · ₹7,022"   (COD selected)
 *                    Button      "Pay ₹4,022"             (online payment selected)
 *
 * The selected payment radio renders as the image "largecircle.fill.circle"; an unselected one
 * renders as plain "circle".
 *
 * The coupon field lives on CART, not here — this screen has exactly one TextField, the notes
 * box. (The layout above used to list a coupon field on Checkout; the page-source dumps in
 * test-output/recon show otherwise.)
 *
 * IMPORTANT — COD is per product, not global. Only products with codEnabled=true offer the
 * Cash-on-delivery option; for anything else the app shows only "Pay online" plus the notice
 * "An item in your order is online-only, so the full order must be paid online." At the time
 * of writing exactly ONE product in the dev catalogue is COD-eligible, so a checkout test must
 * put THAT product in the cart rather than "the first product on Home".
 *
 * Online payment is NOT automated here on purpose: the Razorpay path is real money unless the
 * gateway key is rzp_test_*, which is what config's payment.testmode guards. This screen
 * object deliberately exposes no way to drive the online-payment flow.
 */
public class CheckoutScreen extends BaseScreen {

    private static final By CHECKOUT_TITLE = AppiumBy.accessibilityId("Checkout");
    private static final By DELIVER_TO     = AppiumBy.accessibilityId("Deliver to");
    private static final By PAYMENT_HEADER = AppiumBy.accessibilityId("Payment");

    /** The Cash-on-delivery option. Absent entirely when nothing in the cart allows COD. */
    private static final By COD_OPTION = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND name BEGINSWITH 'Cash on delivery'");

    /** The online-payment option — matched only so tests can assert it, never to drive it. */
    private static final By ONLINE_OPTION = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND name BEGINSWITH 'Pay online'");

    /** The notice shown when the cart contains an online-only item. */
    private static final By ONLINE_ONLY_NOTICE = AppiumBy.iOSNsPredicateString(
            "name CONTAINS 'online-only'");

    /** Commit button in COD mode. In online mode it reads "Pay ₹..." instead. */
    private static final By PLACE_ORDER = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND name BEGINSWITH 'Place order'");

    /** Commit button in ONLINE mode — detected so we can refuse to tap it. */
    private static final By PAY_ONLINE_BUTTON = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND name MATCHES '^Pay ₹.*'");

    /**
     * The saved-address row: the button under the "Deliver to" header that carries the map-pin
     * icon. Matched on where it sits and what it contains, never on what the address SAYS.
     *
     * This used to be the predicate "name CONTAINS ', ' AND name CONTAINS 'India'", which
     * quietly encoded a data assumption: that every saved address ends in a country. The moment
     * an address was saved without one ("Ankaj Kumar, 302, new building, fatehpur, kangra,
     * 176063") nothing matched, and the suite reported the shopper had no delivery address while
     * the row sat plainly on screen.
     *
     * The pin is what makes this unambiguous, and dropping it is not safe: "Cash on delivery,
     * Pay when your order arrives." and "Pay online, UPI, cards, netbanking via Razorpay." are
     * BOTH following-siblings of the same "Deliver to" header and both contain ", ", so matching
     * on position or punctuation alone would report a payment option as the address whenever no
     * address is set — turning a real failure green. The pin renders exactly once on the screen.
     */
    private static final By ADDRESS_ROW = AppiumBy.xpath(
            "//XCUIElementTypeStaticText[@name='Deliver to']"
                    + "/following-sibling::XCUIElementTypeButton"
                    + "[.//XCUIElementTypeImage[@name='mappin.and.ellipse']]");

    public CheckoutScreen(AppiumDriver driver) {
        super(driver);
    }

    // ── loading ───────────────────────────────────────────────

    @Step("Wait for the Checkout screen")
    public boolean isLoaded() {
        return isVisible(CHECKOUT_TITLE, wait) || isVisible(PAYMENT_HEADER, wait);
    }

    // ── address ───────────────────────────────────────────────

    // bringIntoView, not scrollIntoView: the address is the FIRST section on Checkout, and
    // scrollIntoView only ever scrolls DOWN — it cannot reach anything the screen is already
    // scrolled past. See the note on BaseScreen#bringIntoView.
    @Step("Check a delivery address is set")
    public boolean hasDeliveryAddress() {
        return isPresent(DELIVER_TO) && bringIntoView(ADDRESS_ROW) != null;
    }

    /** The saved address as displayed, or "" when none is set. */
    @Step("Read the delivery address")
    public String deliveryAddress() {
        WebElement row = bringIntoView(ADDRESS_ROW);
        return row == null ? "" : row.getAttribute("name");
    }

    // ── payment options ───────────────────────────────────────

    @Step("Check Cash on delivery is offered")
    public boolean isCodAvailable() {
        return scrollIntoView(COD_OPTION) != null;
    }

    @Step("Check online payment is offered")
    public boolean isOnlinePaymentAvailable() {
        return scrollIntoView(ONLINE_OPTION) != null;
    }

    /** True when the cart holds an online-only item, so COD is suppressed for the whole order. */
    @Step("Check for the online-only notice")
    public boolean hasOnlineOnlyNotice() {
        return isPresent(ONLINE_ONLY_NOTICE);
    }

    @Step("Select Cash on delivery")
    public void selectCod() {
        WebElement cod = scrollIntoView(COD_OPTION);
        if (cod == null) {
            throw new IllegalStateException(
                    "Cash on delivery is not offered for this cart. Only products with "
                            + "codEnabled=true allow it — check the cart contents before "
                            + "calling this, or skip the test rather than paying online.");
        }
        cod.click();
        pause(1500);
    }

    // ── placing the order ─────────────────────────────────────

    @Step("Check the order can be placed with Cash on delivery")
    public boolean canPlaceCodOrder() {
        return scrollIntoView(PLACE_ORDER) != null;
    }

    /** The commit button's caption, e.g. "Place order · ₹7,022" — useful for assertions. */
    @Step("Read the place-order button caption")
    public String placeOrderCaption() {
        WebElement btn = scrollIntoView(PLACE_ORDER);
        return btn == null ? "" : btn.getAttribute("name");
    }

    /**
     * Place the order. Creates a REAL order on the backend.
     *
     * Refuses to act unless the button is the COD "Place order" one: if the screen is showing
     * "Pay ₹..." then online payment is selected and tapping would open Razorpay — which is
     * real money on any environment whose key is not rzp_test_*.
     */
    @Step("Place the order (Cash on delivery)")
    public void placeCodOrder() {
        if (isPresent(PAY_ONLINE_BUTTON) && !isPresent(PLACE_ORDER)) {
            throw new IllegalStateException(
                    "Refusing to tap the commit button: it reads 'Pay ₹...', meaning online "
                            + "payment is selected. That opens Razorpay — real money unless the "
                            + "gateway key is rzp_test_*. Select COD first.");
        }
        WebElement btn = scrollIntoView(PLACE_ORDER);
        if (btn == null) {
            throw new IllegalStateException("The 'Place order' button was not found on Checkout");
        }
        btn.click();
        pause(2500);
    }

    // ── order summary arithmetic ──────────────────────────────

    /** The "Items (N)" row's ₹ amount — the goods total before fees. -1 when absent. */
    @Step("Read the items total")
    public int itemsTotal() {
        WebElement label = scrollIntoView(AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeStaticText' AND value BEGINSWITH 'Items ('"));
        if (label == null) return -1;
        int y = label.getRect().getY();
        for (WebElement amt : findAll(RUPEE_AMOUNT)) {
            try {
                if (Math.abs(amt.getRect().getY() - y) < 20) {
                    return parseAmount(amt.getAttribute("value"));
                }
            } catch (Exception ignored) { }
        }
        return -1;
    }

    /** The N in "Items (N)" — checkout's own count of what is being bought. -1 when absent. */
    @Step("Read the checkout item count")
    public int itemCount() {
        WebElement label = scrollIntoView(AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeStaticText' AND value BEGINSWITH 'Items ('"));
        if (label == null) return -1;
        Matcher m = ITEMS_COUNT.matcher(String.valueOf(label.getAttribute("value")));
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static final Pattern ITEMS_COUNT = Pattern.compile("Items \\((\\d+)\\)");

    /**
     * The shipping row as displayed. Storefront-managed shipping renders the word "Free" rather
     * than ₹0, so this returns the TEXT and shippingAmount() interprets it.
     */
    @Step("Read the shipping row")
    public String shipping() {
        WebElement label = scrollIntoView(AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeStaticText' AND value BEGINSWITH 'Shipping'"));
        if (label == null) return "";
        int y = label.getRect().getY();
        int labelX = label.getRect().getX();
        for (WebElement el : findAll(AppiumBy.className("XCUIElementTypeStaticText"))) {
            try {
                if (Math.abs(el.getRect().getY() - y) < 20 && el.getRect().getX() > labelX) {
                    return String.valueOf(el.getAttribute("value"));
                }
            } catch (Exception ignored) { }
        }
        return "";
    }

    /** Shipping as a number: "Free" is 0, "₹99" is 99, anything unreadable is -1. */
    @Step("Read the shipping amount")
    public int shippingAmount() {
        String shown = shipping();
        if (shown.equalsIgnoreCase("Free")) return 0;
        return parseAmount(shown);
    }

    @Step("Read the platform fee")
    public int platformFee() {
        return parseAmount(amountBesideLabel("Platform fee"));
    }

    @Step("Read the order total")
    public int total() {
        return parseAmount(amountBesideLabel("Total"));
    }

    /**
     * The ₹ amount in the commit button's own caption ("Place order · ₹7,022" -> 7022).
     *
     * Worth asserting separately from total(): the button is what the shopper actually commits
     * to, so a summary that disagrees with it is exactly the defect this catches. Works for the
     * "Pay ₹..." caption too. -1 when neither button is present.
     */
    @Step("Read the amount on the commit button")
    public int commitButtonAmount() {
        WebElement btn = scrollIntoView(PLACE_ORDER);
        if (btn == null) btn = scrollIntoView(PAY_ONLINE_BUTTON);
        return btn == null ? -1 : parseAmount(btn.getAttribute("name"));
    }

    // ── payment selection state ───────────────────────────────

    /** The radio image on the SELECTED payment row. Unselected rows render plain "circle". */
    private static final By SELECTED_RADIO = AppiumBy.accessibilityId("largecircle.fill.circle");

    /**
     * Which payment method is currently selected: "cod", "online", or "" when neither is
     * marked. Read geometrically — the selected radio is the one sharing a row with its option
     * button, and the rows are the only thing distinguishing two otherwise identical images.
     */
    @Step("Read the selected payment method")
    public String selectedPaymentMethod() {
        WebElement radio = firstDisplayed(SELECTED_RADIO);
        if (radio == null) return "";
        int radioY = radio.getRect().getY();

        WebElement cod = firstDisplayed(COD_OPTION);
        if (cod != null && rowsOverlap(cod, radioY)) return "cod";

        WebElement online = firstDisplayed(ONLINE_OPTION);
        if (online != null && rowsOverlap(online, radioY)) return "online";

        return "";
    }

    /** True when the given y sits within the element's vertical extent (same visual row). */
    private boolean rowsOverlap(WebElement option, int y) {
        var r = option.getRect();
        return y >= r.getY() - 10 && y <= r.getY() + r.getHeight() + 10;
    }

    // ── order notes ───────────────────────────────────────────

    /** The notes box — the ONLY text field on this screen (the coupon box is on Cart). */
    private static final By NOTES_FIELD = AppiumBy.className("XCUIElementTypeTextField");

    @Step("Enter order notes: {note}")
    public void enterNotes(String note) {
        WebElement field = scrollIntoView(NOTES_FIELD);
        if (field == null) throw new IllegalStateException("Notes field not found on Checkout");
        field.click();
        field.clear();
        field.sendKeys(note);
        dismissKeyboardWithoutTyping();
    }

    /**
     * Put the keyboard away without altering what was typed.
     *
     * BaseScreen#hideKeyboard sends Return/Done/Go, which is right for single-line fields — but
     * the notes box is MULTI-LINE, so Return puts a newline in it rather than dismissing
     * anything, and the field then reads back as "…reception\n". Tapping an inert label
     * dismisses the keyboard and leaves the text alone.
     *
     * The label has to be an inert one. Tapping "somewhere near the top" is not safe here: the
     * top of Checkout is the nav bar, and its leading button is Back.
     */
    private void dismissKeyboardWithoutTyping() {
        try {
            if (!isKeyboardShown()) return;
        } catch (Exception ignored) { }

        for (By label : new By[]{PAYMENT_HEADER, DELIVER_TO}) {
            WebElement el = firstDisplayed(label);
            if (el != null) {
                try {
                    el.click();
                    pause(600);
                    return;
                } catch (Exception ignored) { }
            }
        }
        // Nothing inert on screen — fall back, accepting that this may append a newline.
        hideKeyboard();
    }

    /** What the notes box currently holds. Still the placeholder when nothing was typed. */
    @Step("Read the order notes")
    public String notes() {
        WebElement field = scrollIntoView(NOTES_FIELD);
        return field == null ? "" : String.valueOf(field.getAttribute("value"));
    }
}
