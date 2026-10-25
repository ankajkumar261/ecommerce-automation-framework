package com.portfolio.ecommerce.mobile.android.screens;

import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * The Search tab on Android.
 *
 * Tags: search_field, plus the product_card / product_wishlist of the results. The "Filters"
 * button and the filter chips are untagged, so this screen object does not drive them.
 */
public class AndroidSearchScreen extends AndroidBaseScreen {

    public static final By SEARCH_FIELD = tag("search_field");
    public static final By RESULT_CARD  = tag("product_card");

    public AndroidSearchScreen(AppiumDriver driver) {
        super(driver);
    }

    @Step("Wait for the Search screen")
    public boolean isLoaded() {
        return waitFor(SEARCH_FIELD, 20000);
    }

    /**
     * Type a query, CLEARING whatever is already there first.
     *
     * The clear is not optional. The Search tab keeps its query when you navigate away and come
     * back, and sendKeys appends — so a second test searching "set" against a field still holding
     * "set" searched for "setset", got the empty state, and failed as though the catalogue had
     * no matches. The evidence was the field reading "setset" in the failure dump.
     */
    @Step("Search for: {query}")
    public boolean search(String query) {
        WebElement field = first(SEARCH_FIELD);
        if (field == null) return false;
        try {
            field.click();
            pause(800);
            try { field.clear(); } catch (Exception ignored) { }
            pause(500);
            field.sendKeys(query);
            pause(3000);            // results come from the network
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** What the search field currently holds — used to prove the clear actually worked. */
    public String currentQuery() {
        WebElement field = first(SEARCH_FIELD);
        if (field == null) return "";
        try {
            String t = field.getText();
            return t == null ? "" : t.trim();
        } catch (Exception e) {
            return "";
        }
    }

    @Step("Count the results")
    public int resultCount() {
        waitFor(RESULT_CARD, 12000);
        return all(RESULT_CARD).size();
    }

    @Step("Open the first result")
    public boolean openFirstResult() {
        return waitFor(RESULT_CARD, 15000) && click(RESULT_CARD);
    }
}
