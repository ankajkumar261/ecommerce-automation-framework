package com.portfolio.ecommerce.mobile.screens;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Screen Object for the Storefront app Profile / Account screen.
 *
 * Reached by tapping the Profile tab ("person" icon). Layout (from the live app):
 *   guest state:      "You're browsing as a guest" / "Sign in / Create account"
 *   signed-in state:  account menu rows:
 *       "My Orders"
 *       "Addresses"
 *       "Wishlist"
 *       "Chat"
 *       "Support"
 *       "Privacy Policy"
 *       "Terms and Conditions"
 *       "Sign Out"
 */
public class ProfileScreen extends BaseScreen {

    private static final By PROFILE_TAB = AppiumBy.accessibilityId("person");

    // Guest markers
    private static final By GUEST_MARKER    = AppiumBy.accessibilityId("You're browsing as a guest");
    private static final By SIGN_IN_CREATE  = AppiumBy.accessibilityId("Sign in / Create account");

    // Signed-in menu rows
    private static final By MY_ORDERS        = AppiumBy.accessibilityId("My Orders");
    private static final By ADDRESSES        = AppiumBy.accessibilityId("Addresses");
    private static final By WISHLIST_ENTRY   = AppiumBy.accessibilityId("Wishlist");
    private static final By CHAT_ENTRY       = AppiumBy.accessibilityId("Chat");
    private static final By SUPPORT_ENTRY    = AppiumBy.accessibilityId("Support");
    private static final By PRIVACY_POLICY   = AppiumBy.accessibilityId("Privacy Policy");
    private static final By TERMS_CONDITIONS = AppiumBy.accessibilityId("Terms and Conditions");
    private static final By SIGN_OUT         = AppiumBy.accessibilityId("Sign Out");

    public ProfileScreen(AppiumDriver driver) {
        super(driver);
    }

    @Step("Open the Profile tab")
    public void open() {
        returnToTabBar();
        WebElement tab = firstDisplayed(PROFILE_TAB);
        if (tab != null) {
            tab.click();
            pause(1500);
        }
    }

    @Step("Wait for the Profile screen to load")
    public boolean isLoaded() {
        return isPresent(GUEST_MARKER) || isPresent(MY_ORDERS) || isPresent(SIGN_OUT);
    }

    @Step("Check if the app is in guest state")
    public boolean isGuest() {
        return isPresent(GUEST_MARKER) || isPresent(SIGN_IN_CREATE);
    }

    @Step("Check if the shopper is signed in")
    public boolean isSignedIn() {
        return isPresent(MY_ORDERS) && isPresent(SIGN_OUT);
    }

    // ── navigation to account sections ────────────────────────

    @Step("Open My Orders")
    public void openMyOrders() {
        WebElement row = scrollIntoView(MY_ORDERS);
        if (row != null) row.click();
        pause(1400);
    }

    @Step("Open Addresses")
    public void openAddresses() {
        WebElement row = scrollIntoView(ADDRESSES);
        if (row != null) row.click();
        pause(1400);
    }

    @Step("Open Wishlist")
    public void openWishlist() {
        WebElement row = scrollIntoView(WISHLIST_ENTRY);
        if (row != null) row.click();
        pause(1400);
    }

    @Step("Open Chat")
    public void openChat() {
        WebElement row = scrollIntoView(CHAT_ENTRY);
        if (row != null) row.click();
        pause(1400);
    }

    @Step("Open Support")
    public void openSupport() {
        WebElement row = scrollIntoView(SUPPORT_ENTRY);
        if (row != null) row.click();
        pause(1400);
    }

    @Step("Open Privacy Policy")
    public void openPrivacyPolicy() {
        WebElement row = scrollIntoView(PRIVACY_POLICY);
        if (row != null) row.click();
        pause(1400);
    }

    @Step("Open Terms and Conditions")
    public void openTermsAndConditions() {
        WebElement row = scrollIntoView(TERMS_CONDITIONS);
        if (row != null) row.click();
        pause(1400);
    }

    // ── presence checks ──────────────────────────────────────

    @Step("Check 'My Orders' is visible")
    public boolean hasMyOrders() {
        return scrollIntoView(MY_ORDERS) != null;
    }

    @Step("Check 'Addresses' is visible")
    public boolean hasAddresses() {
        return scrollIntoView(ADDRESSES) != null;
    }

    @Step("Check 'Chat' is visible")
    public boolean hasChat() {
        return scrollIntoView(CHAT_ENTRY) != null;
    }

    @Step("Check 'Support' is visible")
    public boolean hasSupport() {
        return scrollIntoView(SUPPORT_ENTRY) != null;
    }

    @Step("Check 'Privacy Policy' is visible")
    public boolean hasPrivacyPolicy() {
        return scrollIntoView(PRIVACY_POLICY) != null;
    }

    @Step("Check 'Terms and Conditions' is visible")
    public boolean hasTermsAndConditions() {
        return scrollIntoView(TERMS_CONDITIONS) != null;
    }

    @Step("Check 'Sign Out' is visible")
    public boolean hasSignOut() {
        return scrollIntoView(SIGN_OUT) != null;
    }
}
