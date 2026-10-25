package com.portfolio.ecommerce.mobile.screens;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen Object for a single Order Detail screen (reached from My Orders).
 *
 * Layout (from the live app):
 *   order ID         StaticText  "#XXXXX" or "Order #XXXXX"
 *   status           StaticText  "Pending" / "Processing" / "Completed" / "Cancelled"
 *   items            StaticText  product names, quantities, prices
 *   subtotal         StaticText  "Subtotal" + ₹ amount
 *   total            StaticText  "Total" + ₹ amount
 *   delivery address StaticText  the delivery address
 *   payment method   StaticText  "Cash on Delivery" / "Online"
 *   timeline         various     status steps (ordered, processing, shipped, delivered)
 *   back             Button      "BackButton"
 */
public class OrderDetailScreen extends BaseScreen {

    private static final By BACK = AppiumBy.accessibilityId("BackButton");

    /** Order ID — typically carries a '#' prefix. */
    private static final By ORDER_ID = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeStaticText' AND (value CONTAINS '#' OR name CONTAINS '#')");

    /** Status badge — matched by common status words. */
    private static final By STATUS = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeStaticText' AND ("
                    + "value == 'Pending' OR value == 'Processing' OR value == 'Completed' "
                    + "OR value == 'Cancelled' OR value == 'Delivered' OR value == 'Shipped' "
                    + "OR value == 'Confirmed')");

    /** Total row. */
    private static final By TOTAL_LABEL = AppiumBy.accessibilityId("Total");

    /** Delivery address row. */
    private static final By DELIVER_TO = AppiumBy.accessibilityId("Deliver to");

    /** Payment method row. */
    private static final By PAYMENT = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeStaticText' AND (value CONTAINS 'Cash on Delivery' "
                    + "OR value CONTAINS 'Online' OR value CONTAINS 'Razorpay')");

    /** Timeline steps — a vertical list of status indicators. */
    private static final By TIMELINE_STEP = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeStaticText' AND ("
                    + "value CONTAINS 'Ordered' OR value CONTAINS 'Processing' "
                    + "OR value CONTAINS 'Shipped' OR value CONTAINS 'Delivered' "
                    + "OR value CONTAINS 'Confirmed')");

    public OrderDetailScreen(AppiumDriver driver) {
        super(driver);
    }

    @Step("Wait for the Order Detail screen")
    public boolean isLoaded() {
        return isPresent(ORDER_ID) || isPresent(STATUS) || isPresent(TOTAL_LABEL);
    }

    @Step("Read the order ID")
    public String orderId() {
        WebElement el = firstDisplayed(ORDER_ID);
        return el == null ? "" : String.valueOf(el.getAttribute("value"));
    }

    @Step("Read the order status")
    public String status() {
        WebElement el = firstDisplayed(STATUS);
        return el == null ? "" : String.valueOf(el.getAttribute("value"));
    }

    @Step("Check the order status is: {expected}")
    public boolean hasStatus(String expected) {
        return status().equalsIgnoreCase(expected);
    }

    /** The order total as a number. -1 when unreadable. */
    @Step("Read the total")
    public int total() {
        return parseAmount(amountBesideLabel("Total"));
    }

    /** The total as displayed, e.g. "₹7,022". Empty when absent. */
    @Step("Read the total amount")
    public String totalDisplay() {
        return amountBesideLabel("Total");
    }

    @Step("Check a delivery address is shown")
    public boolean hasDeliveryAddress() {
        return scrollIntoView(DELIVER_TO) != null;
    }

    @Step("Check a payment method is shown")
    public boolean hasPaymentMethod() {
        return scrollIntoView(PAYMENT) != null;
    }

    /** The payment method text. */
    @Step("Read the payment method")
    public String paymentMethod() {
        WebElement el = scrollIntoView(PAYMENT);
        return el == null ? "" : String.valueOf(el.getAttribute("value"));
    }

    @Step("Check the order timeline is shown")
    public boolean hasTimeline() {
        return !findAll(TIMELINE_STEP).isEmpty();
    }

    /** The text of each visible timeline step, in order. */
    @Step("Read the timeline steps")
    public List<String> timelineSteps() {
        List<String> steps = new ArrayList<>();
        for (WebElement el : findAll(TIMELINE_STEP)) {
            try {
                String text = String.valueOf(el.getAttribute("value"));
                if (!text.isBlank()) steps.add(text);
            } catch (Exception ignored) { }
        }
        return steps;
    }

    @Step("Go back to Orders")
    public void goBack() {
        try { driver.findElement(BACK).click(); } catch (Exception ignored) { }
    }
}
