package com.portfolio.ecommerce.mobile.screens;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen Object for the Storefront app Search tab.
 *
 * Layout (from the live app):
 *   tab              Button      "magnifyingglass" (label "Search")
 *   title            StaticText  "Search"
 *   input            TextField   placeholder "Products, storefronts, services, brands…"
 *   scope filters    Button      "All" / "Products" / "Storefronts" / "Services"
 *   clear            Button      "xmark.circle.fill" (label "Close")
 *   result sections  StaticText  "PRODUCTS" / "SERVICES" / "CATEGORIES" / "RECOMMENDED BOUTIQUES"
 *   idle state       StaticText  "RECENT SEARCHES" / "TRENDING PRODUCTS"
 *   empty state      StaticText  "No results for \"<term>\"" + "Try a different word or remove filters."
 *
 * Search is public — no sign-in required.
 *
 * Result rows come in three shapes and only PRODUCTS carry the "View →" + ₹ composite label,
 * so the product-card locator cleanly excludes storefront and service rows (services read
 * "From ₹350" and have no "View →").
 */
public class SearchScreen extends BaseScreen {

    private static final By SEARCH_TAB   = AppiumBy.accessibilityId("magnifyingglass");
    private static final By TITLE        = AppiumBy.accessibilityId("Search");
    private static final By INPUT        = AppiumBy.className("XCUIElementTypeTextField");
    private static final By CLEAR_BUTTON = AppiumBy.accessibilityId("xmark.circle.fill");

    /** Product result cards — same composite label as the Home grid. */
    private static final By PRODUCT_CARD = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND label CONTAINS 'View →' AND label CONTAINS '₹'");

    /** The "No results for ..." headline shown when nothing matches. */
    private static final By NO_RESULTS = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeStaticText' AND value BEGINSWITH 'No results for'");

    public SearchScreen(AppiumDriver driver) {
        super(driver);
    }

    // ── navigation ────────────────────────────────────────────

    /**
     * Open the Search tab at its ROOT.
     *
     * openTab() does the real work: the Search tab keeps its own navigation stack, so if an
     * earlier test opened a product from a search result, tapping the tab lands back on that
     * product — with the tab bar visible, so it looks like a perfectly normal screen while
     * having no search box on it at all.
     */
    @Step("Open the Search tab")
    public void open() {
        if (openTab(SEARCH_TAB) == null) {
            throw new NoSuchElementException("Search tab not found in the tab bar");
        }
        pause(500);
    }

    @Step("Wait for the Search screen")
    public boolean isLoaded() {
        return isVisible(TITLE, wait) && isPresent(INPUT);
    }

    // ── searching ─────────────────────────────────────────────

    /**
     * Type a term into the search box and wait for results.
     * The keyboard is dismissed afterwards — it covers the results otherwise.
     */
    @Step("Search for: {term}")
    public void search(String term) {
        WebElement input = firstDisplayed(INPUT);
        if (input == null) throw new NoSuchElementException("Search input not found");
        input.click();
        pause(500);
        input.clear();
        input.sendKeys(term);
        pause(800);
        hideKeyboard();
        pause(1200);
    }

    /** The text currently in the search box. */
    @Step("Read the search term")
    public String currentTerm() {
        WebElement input = firstDisplayed(INPUT);
        return input == null ? "" : String.valueOf(input.getAttribute("value"));
    }

    /** Tap the X to clear the current search. */
    @Step("Clear the search")
    public void clearSearch() {
        WebElement clear = firstDisplayed(CLEAR_BUTTON);
        if (clear == null) {
            clear = firstDisplayed(AppiumBy.accessibilityId("Clear search"));
        }
        if (clear != null) {
            clear.click();
            pause(1000);
        }
    }

    /** Narrow results to one kind: "All", "Products", "Storefronts" or "Services". */
    @Step("Filter results by: {scope}")
    public void selectScope(String scope) {
        WebElement chip = firstDisplayed(AppiumBy.accessibilityId(scope));
        if (chip == null) throw new NoSuchElementException("Filter chip not found: " + scope);
        chip.click();
        pause(1200);
    }

    // ── reading results ───────────────────────────────────────

    /** Number of PRODUCT result cards (storefront and service rows are not counted). */
    @Step("Count the product results")
    public int productResultCount() {
        return findAll(PRODUCT_CARD).size();
    }

    /** The product names from the result cards, in order. */
    @Step("Read the product result names")
    public List<String> productResultNames() {
        List<String> names = new ArrayList<>();
        for (WebElement card : findAll(PRODUCT_CARD)) {
            try {
                String label = card.getAttribute("label");
                if (label == null) continue;
                String[] parts = label.split(",");
                if (parts.length >= 2) names.add(parts[1].trim());
            } catch (Exception ignored) { }
        }
        return names;
    }

    /** True when the search returned nothing and the empty state is shown. */
    @Step("Check the no-results state is shown")
    public boolean hasNoResults() {
        return isPresent(NO_RESULTS);
    }

    /** The "No results for ..." message, or empty string when results were found. */
    @Step("Read the no-results message")
    public String noResultsMessage() {
        WebElement el = firstDisplayed(NO_RESULTS);
        return el == null ? "" : String.valueOf(el.getAttribute("value"));
    }

    /** True if a named result section ("PRODUCTS", "SERVICES", "CATEGORIES") is shown. */
    @Step("Check the {section} section is shown")
    public boolean hasSection(String section) {
        return isPresent(AppiumBy.accessibilityId(section));
    }

    /** True if the idle (pre-search) state is showing its suggestion sections. */
    @Step("Check the idle search state is shown")
    public boolean isIdleState() {
        return hasSection("TRENDING PRODUCTS") || hasSection("RECENT SEARCHES");
    }

    /** Open the first product result and return its name. */
    @Step("Open the first product result")
    public String openFirstResult() {
        WebElement card = scrollIntoView(PRODUCT_CARD);
        if (card == null) throw new NoSuchElementException("No product result to open");
        String name = "";
        String label = card.getAttribute("label");
        if (label != null) {
            String[] parts = label.split(",");
            if (parts.length >= 2) name = parts[1].trim();
        }
        card.click();
        pause(1400);
        return name;
    }
}
