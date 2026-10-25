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
 * Screen Object for the category listing reached by tapping a category on Home.
 *
 * Layout (from the live app):
 *   title        StaticText  the category name, e.g. "Anarkali Suits"
 *   filter rail  Button      every category — "All", "Anarkali Suits", "Sarees", …
 *   results      Button      product cards for that category
 *   back         Button      "BackButton"
 *
 * Browsing is public — no sign-in required.
 */
public class CategoryScreen extends BaseScreen {

    private static final By BACK = AppiumBy.accessibilityId("BackButton");

    private static final By PRODUCT_CARD = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND label CONTAINS 'View →' AND label CONTAINS '₹'");

    public CategoryScreen(AppiumDriver driver) {
        super(driver);
    }

    /** True once the listing for {@code categoryName} is showing. */
    @Step("Wait for the {categoryName} category screen")
    public boolean isLoaded(String categoryName) {
        return isVisible(AppiumBy.accessibilityId(categoryName), wait);
    }

    /** Number of product cards in this category. */
    @Step("Count the products in this category")
    public int productCount() {
        return findAll(PRODUCT_CARD).size();
    }

    /** The product names shown in this category, in order. */
    @Step("Read the product names in this category")
    public List<String> productNames() {
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

    /** Switch to another category using the filter rail at the top. */
    @Step("Switch to category: {categoryName}")
    public void selectCategory(String categoryName) {
        WebElement chip = scrollIntoView(AppiumBy.accessibilityId(categoryName));
        if (chip == null) {
            throw new NoSuchElementException("Category not found in the rail: " + categoryName);
        }
        chip.click();
        pause(1400);
    }

    /** True if the category filter rail offers the given category. */
    @Step("Check the rail offers: {categoryName}")
    public boolean railHasCategory(String categoryName) {
        return isPresent(AppiumBy.accessibilityId(categoryName));
    }

    @Step("Go back")
    public void goBack() {
        WebElement back = firstDisplayed(BACK);
        if (back != null) {
            back.click();
            pause(1000);
        }
    }
}
