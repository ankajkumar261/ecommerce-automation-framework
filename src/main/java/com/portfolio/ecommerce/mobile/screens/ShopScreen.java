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
 * Screen Object for the Storefront app Shop tab.
 *
 * The Shop tab is distinct from Home/Discover and offers filterable browsing of
 * Products and Services. Layout (from the live app):
 *   tab              Button      "bag.fill" (label "Shop")
 *   title            StaticText  "Shop"
 *   filter chips     Button      "All" / "Products" / "Services"
 *   product cards    Button      same composite label as Home (name, price, "View →")
 *   service cards    Button      service name, price range, "Book"
 */
public class ShopScreen extends BaseScreen {

    private static final By SHOP_TAB     = AppiumBy.accessibilityId("bag.fill");
    private static final By TITLE        = AppiumBy.accessibilityId("Shop");

    private static final By FILTER_ALL       = AppiumBy.accessibilityId("All");
    private static final By FILTER_PRODUCTS = AppiumBy.accessibilityId("Products");
    private static final By FILTER_SERVICES = AppiumBy.accessibilityId("Services");

    /** Product cards — same composite label as the Home grid. */
    private static final By PRODUCT_CARD = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND label CONTAINS 'View →' AND label CONTAINS '₹'");

    /** Service cards — contain a price range but NOT "View →". */
    private static final By SERVICE_CARD = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND label CONTAINS 'Book' AND label CONTAINS '₹'");

    public ShopScreen(AppiumDriver driver) {
        super(driver);
    }

    @Step("Open the Shop tab")
    public void open() {
        openTab(SHOP_TAB);
        pause(500);
    }

    @Step("Wait for the Shop screen to load")
    public boolean isLoaded() {
        return isVisible(TITLE, wait);
    }

    @Step("Check filter chips are visible")
    public boolean hasFilterChips() {
        return isPresent(FILTER_ALL) && isPresent(FILTER_PRODUCTS) && isPresent(FILTER_SERVICES);
    }

    @Step("Check if filter '{filter}' is selected")
    public boolean isFilterSelected(String filter) {
        By chip = filterLocator(filter);
        WebElement el = firstDisplayed(chip);
        if (el == null) return false;
        String value = el.getAttribute("value");
        return "1".equals(value);
    }

    @Step("Select filter: {filter}")
    public void selectFilter(String filter) {
        WebElement chip = firstDisplayed(filterLocator(filter));
        if (chip == null) {
            throw new NoSuchElementException("Filter chip not found: " + filter);
        }
        chip.click();
        pause(1200);
    }

    @Step("Count product cards")
    public int productCardCount() {
        return findAll(PRODUCT_CARD).size();
    }

    @Step("Count service cards")
    public int serviceCardCount() {
        return findAll(SERVICE_CARD).size();
    }

    @Step("Open the first product")
    public String openFirstProduct() {
        WebElement card = scrollIntoView(PRODUCT_CARD);
        if (card == null) {
            throw new NoSuchElementException("No product card on the Shop screen");
        }
        String name = productNameFrom(card.getAttribute("label"));
        card.click();
        pause(1400);
        return name;
    }

    @Step("Open the first service")
    public String openFirstService() {
        WebElement card = scrollIntoView(SERVICE_CARD);
        if (card == null) {
            throw new NoSuchElementException("No service card on the Shop screen");
        }
        String name = serviceNameFrom(card.getAttribute("label"));
        card.click();
        pause(1400);
        return name;
    }

    @Step("Read all product names")
    public List<String> productNames() {
        List<String> names = new ArrayList<>();
        for (WebElement card : findAll(PRODUCT_CARD)) {
            try {
                String label = card.getAttribute("label");
                if (label != null) names.add(productNameFrom(label));
            } catch (Exception ignored) { }
        }
        return names;
    }

    @Step("Read all service names")
    public List<String> serviceNames() {
        List<String> names = new ArrayList<>();
        for (WebElement card : findAll(SERVICE_CARD)) {
            try {
                String label = card.getAttribute("label");
                if (label != null) names.add(serviceNameFrom(label));
            } catch (Exception ignored) { }
        }
        return names;
    }

    /** True if at least one service card exists (on screen or below the fold). */
    @Step("Check if services are available")
    public boolean hasServices() {
        return isPresent(SERVICE_CARD);
    }

    /** True if at least one product card exists. */
    @Step("Check if products are available")
    public boolean hasProducts() {
        return isPresent(PRODUCT_CARD);
    }

    private By filterLocator(String filter) {
        return switch (filter.toLowerCase()) {
            case "products" -> FILTER_PRODUCTS;
            case "services" -> FILTER_SERVICES;
            default -> FILTER_ALL;
        };
    }

    private String productNameFrom(String label) {
        if (label == null) return "";
        String[] parts = label.split(",");
        return parts.length >= 2 ? parts[1].trim() : "";
    }

    private String serviceNameFrom(String label) {
        if (label == null) return "";
        String[] parts = label.split(",");
        return parts.length >= 1 ? parts[0].trim() : "";
    }
}
