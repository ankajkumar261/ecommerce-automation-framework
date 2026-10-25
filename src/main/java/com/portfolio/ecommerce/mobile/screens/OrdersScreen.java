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
 * Screen Object for Profile -> My Orders.
 *
 * Layout (from the live app):
 *   title            StaticText  "My Orders"
 *   filter chips     Button      "All" / "Pending" / "Processing" / "Completed" / "Cancelled"
 *   order rows       Button      each carries order ID, status badge, date, total
 *   empty state      StaticText  "No orders yet" or similar
 *   back             Button      "BackButton"
 */
public class OrdersScreen extends BaseScreen {

    private static final By ORDERS_TITLE = AppiumBy.accessibilityId("My Orders");
    private static final By BACK         = AppiumBy.accessibilityId("BackButton");

    // Filter chips — exact names depend on the app; these are common patterns.
    private static final By FILTER_ALL       = AppiumBy.accessibilityId("All");
    private static final By FILTER_PENDING   = AppiumBy.accessibilityId("Pending");
    private static final By FILTER_PROCESSING = AppiumBy.accessibilityId("Processing");
    private static final By FILTER_COMPLETED = AppiumBy.accessibilityId("Completed");
    private static final By FILTER_CANCELLED = AppiumBy.accessibilityId("Cancelled");

    /**
     * Order rows. Each row carries the order ID and status in its label.
     * Matched as buttons with an order-like label.
     */
    private static final By ORDER_ROW = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND (label CONTAINS '#' OR label CONTAINS 'Order')");

    /** Empty state shown when there are no orders. */
    private static final By EMPTY_STATE = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeStaticText' AND (value CONTAINS 'No orders' "
                    + "OR value CONTAINS 'no orders' OR value CONTAINS 'empty')");

    public OrdersScreen(AppiumDriver driver) {
        super(driver);
    }

    @Step("Wait for the My Orders screen")
    public boolean isLoaded() {
        return isVisible(ORDERS_TITLE, wait);
    }

    @Step("Count the order rows")
    public int orderCount() {
        return findAll(ORDER_ROW).size();
    }

    @Step("Check the empty state is shown")
    public boolean hasEmptyState() {
        return scrollIntoView(EMPTY_STATE) != null;
    }

    /** True if there are any orders displayed. */
    @Step("Check orders are displayed")
    public boolean hasOrders() {
        return orderCount() > 0;
    }

    /** Open an order by its position in the list (0-indexed). */
    @Step("Open order at position: {index}")
    public void openOrder(int index) {
        List<WebElement> orders = findAll(ORDER_ROW);
        if (index >= orders.size()) {
            throw new NoSuchElementException(
                    "Order index " + index + " out of range (" + orders.size() + " orders)");
        }
        WebElement order = orders.get(index);
        scrollIntoView(AppiumBy.accessibilityId(order.getAttribute("label")));
        order.click();
        pause(1400);
    }

    /** The status text of the order at the given position. */
    @Step("Read the status of order at position: {index}")
    public String statusOf(int index) {
        List<WebElement> orders = findAll(ORDER_ROW);
        if (index >= orders.size()) return "";
        String label = orders.get(index).getAttribute("label");
        if (label == null) return "";
        // Status is typically the second or last component of the label.
        String[] parts = label.split(",");
        for (String part : parts) {
            String trimmed = part.trim().toLowerCase();
            if (trimmed.contains("pending") || trimmed.contains("processing")
                    || trimmed.contains("completed") || trimmed.contains("cancelled")
                    || trimmed.contains("delivered") || trimmed.contains("shipped")) {
                return part.trim();
            }
        }
        return parts.length >= 2 ? parts[1].trim() : "";
    }

    /** Read all order labels for inspection. */
    @Step("Read all order labels")
    public List<String> orderLabels() {
        List<String> labels = new ArrayList<>();
        for (WebElement row : findAll(ORDER_ROW)) {
            try {
                String label = row.getAttribute("label");
                if (label != null) labels.add(label);
            } catch (Exception ignored) { }
        }
        return labels;
    }

    /** Select a status filter chip. */
    @Step("Filter orders by: {status}")
    public void filterByStatus(String status) {
        By chip = switch (status.toLowerCase()) {
            case "pending"   -> FILTER_PENDING;
            case "processing" -> FILTER_PROCESSING;
            case "completed" -> FILTER_COMPLETED;
            case "cancelled" -> FILTER_CANCELLED;
            default          -> FILTER_ALL;
        };
        WebElement el = firstDisplayed(chip);
        if (el == null) {
            throw new NoSuchElementException("Filter chip not found: " + status);
        }
        el.click();
        pause(1200);
    }

    /** True if a filter chip with the given label is present. */
    @Step("Check if filter '{status}' is available")
    public boolean hasFilter(String status) {
        By chip = switch (status.toLowerCase()) {
            case "pending"   -> FILTER_PENDING;
            case "processing" -> FILTER_PROCESSING;
            case "completed" -> FILTER_COMPLETED;
            case "cancelled" -> FILTER_CANCELLED;
            default          -> FILTER_ALL;
        };
        return isPresent(chip);
    }

    @Step("Go back")
    public void goBack() {
        try { driver.findElement(BACK).click(); } catch (Exception ignored) { }
    }
}
