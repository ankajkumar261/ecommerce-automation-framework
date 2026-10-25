package com.portfolio.ecommerce.mobile.android.screens;

import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;

/**
 * Checkout on Android.
 *
 * Tags: checkout_address, checkout_notes, checkout_payment_online, checkout_place_order.
 *
 * COD IS PER PRODUCT, exactly as on iOS: checkout_payment_cod only renders when everything in
 * the cart allows Cash on Delivery. A cart holding any online-only item shows just
 * checkout_payment_online, so isCodOffered() returning false is a statement about the CART, not
 * a broken locator.
 *
 * ORDERS ARE NEVER PLACED HERE. place_order is exposed so a test can assert it is reachable and
 * correctly labelled; nothing in this class taps it. The iOS suite takes the same line — orders
 * are append-only on a shared account with no way to undo one.
 */
public class AndroidCheckoutScreen extends AndroidBaseScreen {

    public static final By ADDRESS        = tag("checkout_address");
    public static final By NOTES          = tag("checkout_notes");
    public static final By PAYMENT_ONLINE = tag("checkout_payment_online");
    public static final By PAYMENT_COD    = tag("checkout_payment_cod");
    public static final By PLACE_ORDER    = tag("checkout_place_order");

    public AndroidCheckoutScreen(AppiumDriver driver) {
        super(driver);
    }

    @Step("Wait for Checkout")
    public boolean isLoaded() {
        return waitFor(PLACE_ORDER, 20000);
    }

    @Step("Check a delivery address is set")
    public boolean hasDeliveryAddress() {
        return isPresent(ADDRESS);
    }

    @Step("Check Cash on Delivery is offered")
    public boolean isCodOffered() {
        return isPresent(PAYMENT_COD);
    }

    @Step("Check online payment is offered")
    public boolean isOnlinePaymentOffered() {
        return isPresent(PAYMENT_ONLINE);
    }

    @Step("Check the order can be placed")
    public boolean canPlaceOrder() {
        var el = first(PLACE_ORDER);
        return el != null && "true".equals(el.getAttribute("clickable"));
    }

    @Step("Type an order note")
    public boolean typeNote(String note) {
        var el = first(NOTES);
        if (el == null) {
            return false;
        }
        try {
            el.click();
            pause(500);
            el.sendKeys(note);
            pause(500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Items / Shipping / Platform fee / Total, read from the summary. -1 when unreadable. */
    public int items()       { return amount("Items"); }
    public int platformFee() { return amount("Platform fee"); }
    public int total()       { return amount("Total"); }

    private int amount(String label) {
        return new AndroidCartScreen(driver).amountAfter(label);
    }
}
