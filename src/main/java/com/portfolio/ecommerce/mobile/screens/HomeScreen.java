package com.portfolio.ecommerce.mobile.screens;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Screen Object for the Storefront app Home / Discover screen.
 *
 * iOS locators use the element's accessibility id (the "name" attribute in the page source):
 *   "Discover"  -> the page title (StaticText)
 *   "house.fill"/"bag.fill"/"cart.fill" -> bottom tab bar buttons (SF Symbol names)
 *
 * NOTE: the app runs against the LIVE dev backend with noReset(true), so it can launch on
 * whatever screen/tab it was last left on. isLoaded() therefore tolerates that: if the
 * Discover title isn't immediately up but the tab bar is, it taps the Home tab first.
 */
public class HomeScreen extends BaseScreen {

    private static final By DISCOVER_TITLE = AppiumBy.accessibilityId("Discover");
    private static final By HOME_TAB       = AppiumBy.accessibilityId("house.fill");
    private static final By SHOP_TAB       = AppiumBy.accessibilityId("bag.fill");
    private static final By CART_TAB       = AppiumBy.accessibilityId("cart.fill");

    /**
     * Product cards are buttons whose accessibility label is a composite,
     * e.g. "TEST CREATIONS, Sahara Suit Lavander, ₹3,999, View →".
     * Match on the trailing "View →" plus a ₹ price so we don't catch "View all".
     */
    private static final By PRODUCT_CARD = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND label CONTAINS 'View →' AND label CONTAINS '₹'");

    /** "Shop the categories" heading — Home-specific content, used to confirm the tab. */
    private static final By CATEGORY_RAIL = AppiumBy.accessibilityId("Shop the categories");


    public HomeScreen(AppiumDriver driver) {
        super(driver);
    }

    /**
     * Navigate to the Home tab's root from wherever we are.
     *
     * Both stacks have to be unwound — the one we are on to reach the tab bar, and Home's own,
     * since a test that opened a product from Home leaves it sitting on product detail. See
     * BaseScreen#openTab.
     */
    @Step("Go to the Home tab")
    public void open() {
        openTab(HOME_TAB);
        pause(500);
    }

    /**
     * True once the Home screen has loaded (Discover title + tab bar visible).
     * Tolerant of the app launching on another tab: if the tab bar is present but
     * Discover isn't showing, tap the Home tab and re-check.
     */
    /** How long to give Home to finish loading its content from the network. */
    private static final long LOAD_TIMEOUT_MS = 30_000;

    @Step("Wait for the Home screen to load")
    public boolean isLoaded() {
        // Poll the whole condition rather than checking each branch once. Home fetches its
        // content over the network on a cold launch, so the tab bar can be up seconds before
        // any product or category is. Checking for content the instant the tab bar appears
        // reports "not loaded" for what is really just a slow start.
        long end = System.currentTimeMillis() + LOAD_TIMEOUT_MS;
        boolean tappedHomeTab = false;

        while (System.currentTimeMillis() < end) {
            // Two cheap accessibility-id/predicate lookups, NOT one combined predicate: a
            // single "A OR B" predicate here is a full-tree scan and measured far slower.
            // See the performance note in BaseScreen.
            if (isPresent(CATEGORY_RAIL) || isPresent(PRODUCT_CARD)) {
                return true;
            }

            // We may be inside the app shell but on another tab — tap Home once.
            if (!tappedHomeTab) {
                WebElement homeTab = firstDisplayed(HOME_TAB);
                if (homeTab != null) {
                    try { homeTab.click(); } catch (Exception ignored) { }
                    tappedHomeTab = true;
                }
            }

            pause(500);
        }
        return false;
    }

    /** True if all three main tabs (Home, Shop, Cart) are present. */
    @Step("Check the bottom tab bar is visible")
    public boolean isTabBarVisible() {
        return !driver.findElements(HOME_TAB).isEmpty()
                && !driver.findElements(SHOP_TAB).isEmpty()
                && !driver.findElements(CART_TAB).isEmpty();
    }


    /**
     * Open the first product card and return its product name.
     *
     * The Home screen stacks a greeting, search bar, category rail and "Featured storefronts"
     * above the product grid, so on a phone-sized screen EVERY product card starts below the
     * fold (present in the tree, but visible="false"). Waiting for one to be clickable can
     * therefore never succeed — we have to scroll it into view first.
     *
     * The product name is the 2nd comma-separated component of the card's label.
     */
    @Step("Open the first product on Home")
    public String openFirstProduct() {
        WebElement card = scrollIntoView(PRODUCT_CARD);
        if (card == null) {
            throw new NoSuchElementException(
                    "No product card came into view on Home after " + MAX_SWIPES + " swipes. "
                            + (isPresent(PRODUCT_CARD)
                            ? "Cards exist in the tree but never scrolled on screen."
                            : "No product cards are present at all — the catalogue may be empty."));
        }

        String productName = productNameFrom(card.getAttribute("label"));
        card.click();
        return productName;
    }

    /**
     * Tap the wishlist heart on the first product card, without opening the product.
     * As a guest this opens the sign-in sheet rather than saving anything.
     */
    @Step("Tap the heart on the first product card")
    public void tapFirstProductHeart() {
        WebElement heart = scrollIntoView(AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeButton' AND (name == 'heart' OR name == 'heart.fill')"));
        if (heart == null) {
            throw new NoSuchElementException("No wishlist heart found on any product card");
        }
        heart.click();
        pause(1400);
    }

    /** True if at least one product card exists on Home (on screen or below the fold). */
    @Step("Check the product grid has products")
    public boolean hasProducts() {
        return isPresent(PRODUCT_CARD);
    }

    /** Pull the product name out of a card label like "BOUTIQUE, Name, ₹1,234, View →". */
    private String productNameFrom(String label) {
        if (label == null) return "";
        String[] parts = label.split(",");
        return parts.length >= 2 ? parts[1].trim() : "";
    }

    /**
     * The whole composite label of the first product card, e.g.
     * "TEST CREATIONS, Sahara Suit Lavander, ₹3,999, View →".
     *
     * Exposed so a test can compare what the card ADVERTISES against what the product detail
     * screen then shows — the card and the PDP are separately rendered from the same record,
     * so a mismatch is a real defect rather than a duplicate assertion.
     */
    @Step("Read the first product card's label")
    public String firstProductCardLabel() {
        WebElement card = scrollIntoView(PRODUCT_CARD);
        if (card == null) {
            throw new NoSuchElementException("No product card came into view on Home");
        }
        String label = card.getAttribute("label");
        return label == null ? "" : label;
    }

    /**
     * The price advertised on the first product card, e.g. "₹3,999". Empty when the label
     * carries no ₹ component. Note the card price is the CURRENT price, so a discounted
     * product advertises the discounted figure — which is what the PDP shows too.
     */
    @Step("Read the first product card's price")
    public String firstProductPrice() {
        return priceFrom(firstProductCardLabel());
    }

    /**
     * Pull the ₹ component out of a card label.
     *
     * Matched with a regex rather than by splitting the label on commas: prices are
     * thousands-separated ("₹3,999"), so a comma split cuts the price in half and yields "₹3".
     *
     * The pattern has to END on a digit. The price is followed by the label's own separator
     * (", View →"), so a trailing `[\d,]+` swallows that comma too and yields "₹3,999,".
     */
    private static final Pattern CARD_PRICE = Pattern.compile("₹\\d[\\d,]*\\d|₹\\d");

    private String priceFrom(String label) {
        if (label == null) return "";
        Matcher m = CARD_PRICE.matcher(label);
        return m.find() ? m.group() : "";
    }

    /** The product name advertised on the first card, without opening it. */
    @Step("Read the first product card's name")
    public String firstProductName() {
        return productNameFrom(firstProductCardLabel());
    }

    // ── category rail ─────────────────────────────────────────

    /**
     * Category chips carry their own item count in the label,
     * e.g. "Anarkali Suits, 1 items" (and "All, 11 total").
     */
    private static final By CATEGORY_CHIP = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND (label CONTAINS 'items' OR label CONTAINS 'total')");

    private static final Pattern CHIP_COUNT = Pattern.compile("(\\d+)\\s+(?:items?|total)");

    /** The category names offered on Home, in order. */
    @Step("Read the category names on Home")
    public List<String> categoryNames() {
        List<String> names = new ArrayList<>();
        for (WebElement chip : findAll(CATEGORY_CHIP)) {
            try {
                String label = chip.getAttribute("label");
                if (label == null) continue;
                int comma = label.lastIndexOf(',');
                names.add(comma > 0 ? label.substring(0, comma).trim() : label.trim());
            } catch (Exception ignored) { }
        }
        return names;
    }

    /**
     * The item count a category chip advertises, e.g. 1 for "Anarkali Suits, 1 items".
     * Returns -1 when that category isn't on screen.
     */
    @Step("Read the advertised item count for: {categoryName}")
    public int categoryItemCount(String categoryName) {
        for (WebElement chip : findAll(CATEGORY_CHIP)) {
            try {
                String label = chip.getAttribute("label");
                if (label == null || !label.startsWith(categoryName + ",")) continue;
                Matcher m = CHIP_COUNT.matcher(label);
                if (m.find()) return Integer.parseInt(m.group(1));
            } catch (Exception ignored) { }
        }
        return -1;
    }

    /** Tap a category chip to open its listing. */
    @Step("Open category: {categoryName}")
    public void openCategory(String categoryName) {
        By chip = AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeButton' AND label BEGINSWITH '"
                        + categoryName.replace("'", "\\'") + ",'");
        WebElement el = scrollIntoView(chip);
        if (el == null) {
            throw new NoSuchElementException("Category chip not found on Home: " + categoryName);
        }
        el.click();
        pause(1400);
    }
}