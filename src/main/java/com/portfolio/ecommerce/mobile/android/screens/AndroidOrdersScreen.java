package com.portfolio.ecommerce.mobile.android.screens;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;

/**
 * The My Orders screen on Android (Account → My Orders).
 *
 * Untagged; keyed on its header "My Orders" and the "No orders yet" empty state. Order rows
 * carry an order number ("#…") or "Order" in their text when present.
 */
public class AndroidOrdersScreen extends AndroidBaseScreen {

    private static final By TITLE   = textContains("My Orders");
    private static final By EMPTY   = textContains("No orders");
    // Order numbers render as "ORD-…" on this build (older builds used "#…"). Match both.
    private static final By ORDER_ROW_ORD = AppiumBy.androidUIAutomator(
            "new UiSelector().textContains(\"ORD-\")");
    private static final By ORDER_ROW_HASH = AppiumBy.androidUIAutomator(
            "new UiSelector().textContains(\"#\")");

    // Status filter chips, when orders exist.
    private static final By FILTER_ALL = AppiumBy.androidUIAutomator("new UiSelector().text(\"All\")");

    public AndroidOrdersScreen(AppiumDriver driver) {
        super(driver);
    }

    @Step("Wait for the My Orders screen")
    public boolean isLoaded() {
        return waitFor(TITLE, 12000);
    }

    @Step("Check the empty-orders state")
    public boolean hasEmptyState() {
        return isPresent(EMPTY);
    }

    @Step("Count the orders")
    public int orderCount() {
        return all(ORDER_ROW_ORD).size() + all(ORDER_ROW_HASH).size();
    }

    @Step("Check a status filter is offered")
    public boolean hasStatusFilters() {
        return isPresent(FILTER_ALL);
    }
}
