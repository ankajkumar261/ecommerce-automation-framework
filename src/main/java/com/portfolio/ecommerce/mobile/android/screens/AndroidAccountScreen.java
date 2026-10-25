package com.portfolio.ecommerce.mobile.android.screens;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;

/**
 * The Account screen on Android (top-bar profile icon → Account, once signed in).
 *
 * This screen carries NO test tags in this build, so every row is located by its visible label.
 * Confirmed labels and their sections:
 *   SHOPPING : My Orders, My Bookings, Addresses, Measurements, Wishlist
 *   SUPPORT  : Messages (chat), Help & Support, Notifications
 *   LEGAL    : Privacy Policy, Terms & Conditions
 *   plus Delete Account and Sign Out.
 */
public class AndroidAccountScreen extends AndroidBaseScreen {

    private static final By PROFILE_BUTTON = AndroidLoginScreen.PROFILE_BUTTON;

    private static final By MY_ORDERS   = textExact("My Orders");
    private static final By MY_BOOKINGS = textExact("My Bookings");
    private static final By ADDRESSES   = textExact("Addresses");
    private static final By MESSAGES    = textExact("Messages");
    private static final By SUPPORT     = textExact("Help & Support");
    private static final By PRIVACY     = textExact("Privacy Policy");
    private static final By TERMS       = textExact("Terms & Conditions");
    private static final By SIGN_OUT    = textExact("Sign Out");

    public AndroidAccountScreen(AppiumDriver driver) {
        super(driver);
    }

    private static By textExact(String v) {
        return AppiumBy.androidUIAutomator("new UiSelector().text(\"" + v + "\")");
    }

    /** Open the Account screen via the top-bar profile icon (must already be signed in). */
    @Step("Open the Account screen")
    public boolean open() {
        if (isLoaded()) return true;
        // profile_button only exists on the tabbed screens. Coming from a pushed screen (checkout,
        // cart, a product detail) there is none, so back out one screen at a time until it
        // reappears — stopping as soon as it does, which also avoids pressing Back from Home.
        for (int i = 0; i < 3 && !isPresent(PROFILE_BUTTON); i++) {
            driver.navigate().back();
            pause(1500);
        }
        click(PROFILE_BUTTON);
        return waitFor(MY_ORDERS, 12000);
    }

    @Step("Wait for the Account screen")
    public boolean isLoaded() {
        return isPresent(MY_ORDERS) || isPresent(SIGN_OUT);
    }

    @Step("Check My Orders is present")
    public boolean hasMyOrders() { return scrollUntilPresent(MY_ORDERS, 4); }

    @Step("Check Addresses is present")
    public boolean hasAddresses() { return scrollUntilPresent(ADDRESSES, 4); }

    @Step("Check Messages (chat) is present")
    public boolean hasMessages() { return scrollUntilPresent(MESSAGES, 6); }

    @Step("Check Help & Support is present")
    public boolean hasSupport() { return scrollUntilPresent(SUPPORT, 6); }

    @Step("Check Privacy Policy is present")
    public boolean hasPrivacyPolicy() { return scrollUntilPresent(PRIVACY, 8); }

    @Step("Check Terms & Conditions is present")
    public boolean hasTermsAndConditions() { return scrollUntilPresent(TERMS, 8); }

    @Step("Check My Bookings is present")
    public boolean hasMyBookings() { return scrollUntilPresent(MY_BOOKINGS, 6); }

    @Step("Check Sign Out is present")
    public boolean hasSignOut() { return scrollUntilPresent(SIGN_OUT, 8); }

    @Step("Open My Orders")
    public boolean openMyOrders() { return scrollUntilPresent(MY_ORDERS, 4) && click(MY_ORDERS); }

    @Step("Open Messages (chat)")
    public boolean openMessages() { return scrollUntilPresent(MESSAGES, 6) && click(MESSAGES); }

    @Step("Open Help & Support")
    public boolean openSupport() { return scrollUntilPresent(SUPPORT, 6) && click(SUPPORT); }

    @Step("Open Privacy Policy")
    public boolean openPrivacyPolicy() { return scrollUntilPresent(PRIVACY, 8) && click(PRIVACY); }

    @Step("Open Terms & Conditions")
    public boolean openTerms() { return scrollUntilPresent(TERMS, 8) && click(TERMS); }
}
