package com.portfolio.ecommerce.mobile.screens;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen Object for the Storefront app Product Detail screen.
 *
 * Accessibility ids (from the live app):
 *   title           StaticText  "Product details"
 *   product name    StaticText  e.g. "Sahara Suit Lavander"
 *   price           StaticText  begins with "₹"
 *   size options    Button      "Size S" / "Size M" / "Size L" / "Size XL" / "Custom size"
 *   add to cart     Button      name "cart.fill" (label "Add to cart")
 *   buy now         Button      "Buy Now"
 *   wishlist        Button      name "heart" (label "Add to wishlist")
 *   back            Button      "BackButton"
 */
public class ProductDetailScreen extends BaseScreen {

    private static final By TITLE       = AppiumBy.accessibilityId("Product details");
    private static final By BUY_NOW      = AppiumBy.accessibilityId("Buy Now");
    private static final By BACK         = AppiumBy.accessibilityId("BackButton");

    /**
     * Add-to-cart. Matched on the LABEL ONLY. The button's own name is the SF Symbol
     * "cart.fill" — which the bottom tab bar's Cart tab also uses, and the tab comes FIRST in
     * the accessibility tree. Allowing `name == 'cart.fill'` as an alternative therefore taps
     * the Cart tab instead of adding, whenever both are on screen.
     *
     * Both are on screen more often than it looks: reaching product detail from Home pushes
     * over the tab shell and hides the tab bar (only the real button matches, so a name-based
     * match appears to work), but reaching it from Search leaves the tab bar visible.
     *
     * This button only appears once a size has been selected — before that the product cannot
     * be added at all, so selectSize() has to come first.
     */
    private static final By ADD_TO_CART = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND label == 'Add to cart'");

    /** After a successful add, the button turns into a checkmark labelled "View cart". */
    private static final By VIEW_CART = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND (label == 'View cart' OR name == 'checkmark')");

    /** The cart icon in the product-detail nav bar (distinct from the tab bar's cart). */
    private static final By CART_NAV = AppiumBy.accessibilityId("bag");

    /**
     * The wishlist toggle. It is a single control that switches between two states:
     *   not saved -> Button "heart"      label "Add to wishlist"
     *   saved     -> Button "heart.fill" label "Remove from wishlist"
     */
    private static final By WISHLIST_ADD = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND label == 'Add to wishlist'");

    private static final By WISHLIST_REMOVE = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND label == 'Remove from wishlist'");

    public ProductDetailScreen(AppiumDriver driver) {
        super(driver);
    }

    /** True once the product detail screen has loaded (title + a size option present). */
    @Step("Wait for the Product Detail screen")
    public boolean isLoaded() {
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(TITLE));
        } catch (Exception e) {
            // Some builds may not label the header — fall back to the Add-to-cart button.
            return !driver.findElements(ADD_TO_CART).isEmpty() || hasAnySize();
        }
        return true;
    }

    /** The product name shown on the detail screen (matches the card that was tapped). */
    @Step("Read the product name")
    public boolean showsProductNamed(String expectedName) {
        if (expectedName == null || expectedName.isBlank()) return false;
        By byName = AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeStaticText' AND value == '" + expectedName.replace("'", "\\'") + "'");
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(byName));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** True if a rupee price is displayed. */
    @Step("Check a price is displayed")
    public boolean hasPrice() {
        return !driver.findElements(AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeStaticText' AND value BEGINSWITH '₹'")).isEmpty();
    }

    /** True if the standard size options (S/M/L/XL) are offered. */
    @Step("Check the size selector is present")
    public boolean hasSizeSelector() {
        return !driver.findElements(AppiumBy.accessibilityId("Size M")).isEmpty()
                || !driver.findElements(AppiumBy.accessibilityId("Size S")).isEmpty();
    }

    private boolean hasAnySize() {
        return !driver.findElements(AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeButton' AND name BEGINSWITH 'Size '")).isEmpty();
    }

    /**
     * Select a size, e.g. "M".
     *
     * Scrolls in BOTH directions to find it. The size selector sits near the top of the
     * screen, and product detail keeps its scroll offset between visits — so a test that
     * scrolled down to SPECIFICATIONS leaves the next one opening with the sizes ABOVE the
     * viewport. Waiting for "clickable" without scrolling therefore times out on an element
     * that is present and perfectly tappable, just off screen the other way, which is exactly
     * how this failed in the full suite while passing on its own.
     */
    @Step("Select size {size}")
    public void selectSize(String size) {
        By locator = AppiumBy.accessibilityId("Size " + size);
        WebElement btn = bringIntoView(locator);
        if (btn == null) {
            throw new NoSuchElementException(
                    "Size " + size + " never came into view on product detail. Sizes offered: "
                            + sizeOptions());
        }
        btn.click();
        pause(300);
    }

    /** True if the add-to-cart control is present. */
    @Step("Check Add to cart is available")
    public boolean hasAddToCart() {
        return !driver.findElements(ADD_TO_CART).isEmpty();
    }

    @Step("Tap Add to cart")
    public void tapAddToCart() {
        WebElement btn = scrollIntoView(ADD_TO_CART);
        if (btn == null) {
            throw new NoSuchElementException(
                    "Add-to-cart is not available. It only appears once a size has been "
                            + "selected — call selectSize(...) first.");
        }
        btn.click();
        pause(1200);
    }

    /** True once the add succeeded — the button becomes a "View cart" checkmark. */
    @Step("Verify the product was added to the cart")
    public boolean isAddedToCart() {
        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end) {
            if (isPresent(VIEW_CART)) return true;
            pause(500);
        }
        return false;
    }

    /** Open the cart from the product-detail nav bar. */
    @Step("Open the cart from product detail")
    public void openCart() {
        WebElement bag = firstDisplayed(CART_NAV);
        if (bag == null) throw new NoSuchElementException("Cart icon not found on product detail");
        bag.click();
        pause(1400);
    }

    // ── wishlist ──────────────────────────────────────────────

    /** True if this product is currently saved to the wishlist. */
    @Step("Check whether the product is in the wishlist")
    public boolean isInWishlist() {
        return isPresent(WISHLIST_REMOVE);
    }

    /** Save the product to the wishlist. No-op if it is already saved. */
    @Step("Add the product to the wishlist")
    public void addToWishlist() {
        if (isInWishlist()) return;
        WebElement heart = firstDisplayed(WISHLIST_ADD);
        if (heart == null) {
            throw new NoSuchElementException("The 'Add to wishlist' heart was not found");
        }
        heart.click();
        pause(1400);
    }

    /** Unsave the product. No-op if it is not currently saved. */
    @Step("Remove the product from the wishlist")
    public void removeFromWishlist() {
        WebElement heart = firstDisplayed(WISHLIST_REMOVE);
        if (heart == null) return;
        heart.click();
        pause(1400);
    }

    /** The product's displayed price, e.g. "₹3,999" — empty when not shown. */
    @Step("Read the displayed price")
    public String price() {
        WebElement el = firstDisplayed(AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeStaticText' AND value BEGINSWITH '₹'"));
        return el == null ? "" : el.getAttribute("value");
    }

    @Step("Go back")
    public void goBack() {
        try { driver.findElement(BACK).click(); } catch (Exception ignored) { }
    }

    // ── size selection ────────────────────────────────────────

    /** Every size button offered, e.g. [Size S, Size M, Size L, Size XL]. */
    private static final By SIZE_BUTTON = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND name BEGINSWITH 'Size '");

    /** The size labels offered, without the "Size " prefix: [S, M, L, XL]. */
    @Step("Read the size options")
    public List<String> sizeOptions() {
        List<String> sizes = new ArrayList<>();
        for (WebElement btn : findAll(SIZE_BUTTON)) {
            try {
                String name = btn.getAttribute("name");
                if (name != null && name.startsWith("Size ")) {
                    sizes.add(name.substring("Size ".length()).trim());
                }
            } catch (Exception ignored) { }
        }
        return sizes;
    }

    /**
     * True if this size is the currently chosen one.
     *
     * The app marks the selection with value="1" on the chosen size button and leaves the
     * others with no value at all — so selection is directly readable rather than having to
     * be inferred from colour or from the fact that Add-to-cart appeared.
     */
    @Step("Check size {size} is selected")
    public boolean isSizeSelected(String size) {
        WebElement btn = firstDisplayed(AppiumBy.accessibilityId("Size " + size));
        if (btn == null) return false;
        return "1".equals(btn.getAttribute("value"));
    }

    /** The chosen size ("M"), or empty when nothing is chosen yet. */
    @Step("Read the selected size")
    public String selectedSize() {
        for (String size : sizeOptions()) {
            if (isSizeSelected(size)) return size;
        }
        return "";
    }

    /** How many size buttons currently carry the selected marker — should always be 0 or 1. */
    @Step("Count the selected sizes")
    public int selectedSizeCount() {
        int count = 0;
        for (String size : sizeOptions()) {
            if (isSizeSelected(size)) count++;
        }
        return count;
    }

    // ── quantity stepper ──────────────────────────────────────

    /**
     * The stepper is one accessible Button named "Quantity N" wrapping a minus Image, the
     * number, and a plus Button. The children report visible="false" because the parent
     * absorbs them for accessibility, so they have to be tapped by coordinate — see
     * BaseScreen#tapElementCentre.
     */
    private static final By QUANTITY_STEPPER = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND name BEGINSWITH 'Quantity '");

    private static final By QUANTITY_PLUS  = AppiumBy.accessibilityId("plus");
    private static final By QUANTITY_MINUS = AppiumBy.accessibilityId("minus");

    /** The quantity the stepper currently shows. 0 when the stepper is not on screen. */
    @Step("Read the quantity")
    public int quantity() {
        WebElement stepper = scrollIntoView(QUANTITY_STEPPER);
        if (stepper == null) return 0;
        String name = stepper.getAttribute("name");
        if (name == null) return 0;
        String digits = name.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? 0 : Integer.parseInt(digits);
    }

    @Step("Increase the quantity")
    public void increaseQuantity() {
        tapStepper(QUANTITY_PLUS, "+");
    }

    @Step("Decrease the quantity")
    public void decreaseQuantity() {
        tapStepper(QUANTITY_MINUS, "−");
    }

    private void tapStepper(By control, String label) {
        // Make sure the stepper itself is on screen first — tapping coordinates for something
        // below the fold would land on whatever is actually rendered there.
        if (scrollIntoView(QUANTITY_STEPPER) == null) {
            throw new NoSuchElementException("The quantity stepper is not on the product detail screen");
        }
        List<WebElement> matches = findAll(control);
        if (matches.isEmpty()) {
            throw new NoSuchElementException("No '" + label + "' control inside the quantity stepper");
        }
        tapElementCentre(matches.get(0));
        pause(800);
    }

    // ── badges and detail sections ────────────────────────────

    /** The stock line, e.g. "In stock — ready to ship". Empty when absent. */
    @Step("Read the stock label")
    public String stockLabel() {
        WebElement el = firstDisplayed(AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeStaticText' AND value CONTAINS 'stock'"));
        return el == null ? "" : el.getAttribute("value");
    }

    /**
     * True if this product advertises Cash on Delivery on its own detail screen.
     *
     * The PDP-side mirror of what CheckoutScreen#isCodAvailable asserts: COD is a per-product
     * flag, so the badge here and the option at checkout should agree.
     */
    @Step("Check the COD badge is shown")
    public boolean advertisesCod() {
        return scrollIntoView(AppiumBy.accessibilityId("Cash on Delivery available")) != null;
    }

    /**
     * The three collapsible detail sections, as their header buttons.
     *
     * NOTE: they ship EXPANDED. Tapping a header therefore COLLAPSES it — which is why
     * ensureSectionExpanded exists and why nothing should call toggleSection blindly.
     */
    private static final By SECTION_HEADER = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND (name == 'DESCRIPTION' "
                    + "OR name == 'MATERIAL & CARE' OR name == 'SPECIFICATIONS')");

    private By sectionHeader(String section) {
        return AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeButton' AND name == '" + section.replace("'", "\\'") + "'");
    }

    /** Tap a section header, flipping it between expanded and collapsed. */
    @Step("Toggle the {section} section")
    public void toggleSection(String section) {
        WebElement header = scrollIntoView(sectionHeader(section));
        if (header == null) {
            throw new NoSuchElementException("No collapsible section named: " + section);
        }
        header.click();
        pause(900);
    }

    /** Make sure a section's body is showing, opening it only if it is currently closed. */
    @Step("Make sure the {section} section is expanded")
    public void ensureSectionExpanded(String section) {
        if (!isSectionExpanded(section)) {
            toggleSection(section);
        }
    }

    /**
     * The value of a specification row, e.g. specification("Material") -> "Velvet".
     * Rows render as two StaticTexts sharing a baseline, so the value is the text to the
     * RIGHT of the label on the same row. Empty when the row is not shown.
     */
    @Step("Read the {label} specification")
    public String specification(String label) {
        WebElement lbl = scrollIntoView(AppiumBy.accessibilityId(label));
        if (lbl == null) return "";
        int y = lbl.getRect().getY();
        int labelX = lbl.getRect().getX();
        for (WebElement el : findAll(AppiumBy.className("XCUIElementTypeStaticText"))) {
            try {
                if (Math.abs(el.getRect().getY() - y) < 20 && el.getRect().getX() > labelX) {
                    return el.getAttribute("value");
                }
            } catch (Exception ignored) { }
        }
        return "";
    }

    /**
     * True if the section's body is currently rendered.
     *
     * Decided by geometry: a section is expanded when there is body text between ITS header and
     * the NEXT header down. Bounding it at the next header is what makes this work — every
     * section sits above other content, so "is there anything below this header" would report
     * every section as expanded regardless of state.
     *
     * The header's own StaticText twin sits at exactly the header's y, so a strict `>` excludes
     * it; the value check is belt-and-braces for that same twin.
     */
    @Step("Check the {section} body is showing")
    public boolean isSectionExpanded(String section) {
        WebElement header = scrollIntoView(sectionHeader(section));
        if (header == null) return false;
        int headerY = header.getRect().getY();

        int nextHeaderY = Integer.MAX_VALUE;
        for (WebElement other : findAll(SECTION_HEADER)) {
            try {
                int y = other.getRect().getY();
                if (y > headerY && y < nextHeaderY) nextHeaderY = y;
            } catch (Exception ignored) { }
        }

        for (WebElement el : findAll(AppiumBy.className("XCUIElementTypeStaticText"))) {
            try {
                String value = el.getAttribute("value");
                if (value == null || value.isBlank() || value.equals(section)) continue;
                int y = el.getRect().getY();
                if (y > headerY && y < nextHeaderY) return true;
            } catch (Exception ignored) { }
        }
        return false;
    }

    // ── buy now ───────────────────────────────────────────────

    @Step("Check Buy Now is offered")
    public boolean hasBuyNow() {
        return isPresent(BUY_NOW);
    }

    /**
     * Tap Buy Now (the express-purchase path).
     *
     * As a GUEST this raises the sign-in sheet, which is the only thing the suite uses it for.
     * It is deliberately not driven any further while signed in: Buy Now leads straight to
     * checkout and a real order.
     */
    @Step("Tap Buy Now")
    public void tapBuyNow() {
        WebElement btn = scrollIntoView(BUY_NOW);
        if (btn == null) throw new NoSuchElementException("Buy Now not found on product detail");
        btn.click();
        pause(1600);
    }
}
