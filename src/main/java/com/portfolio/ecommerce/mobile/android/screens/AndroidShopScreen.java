package com.portfolio.ecommerce.mobile.android.screens;

import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;

/**
 * The Shop tab on Android.
 *
 * Reached via the bottom tab bar (bottom_tab_shop). The Shop screen is only partly tagged: the
 * product cards carry product_card, but the All / Products / Services filter chips and the
 * service cards are text-only in this build, so those are located by their visible copy.
 *
 * Confirmed against the live app: Products lists product_card tiles; Services lists cards that
 * each carry a "Book →" call to action and a "From ₹…" price.
 */
public class AndroidShopScreen extends AndroidBaseScreen {

    public static final By TAB_SHOP = AndroidHomeScreen.TAB_SHOP;
    public static final By PRODUCT_CARD = AndroidHomeScreen.PRODUCT_CARD;

    private static final By CHIP_ALL      = textExact("All");
    private static final By CHIP_PRODUCTS = textExact("Products");
    private static final By CHIP_SERVICES = textExact("Services");

    /** Service cards are untagged; each shows a "Book" call to action and a "From ₹…" price. */
    private static final By SERVICE_CTA = textContains("Book");
    private static final By SERVICE_PRICE = io.appium.java_client.AppiumBy.androidUIAutomator(
            "new UiSelector().textContains(\"From ₹\")");

    public AndroidShopScreen(AppiumDriver driver) {
        super(driver);
    }

    private static By textExact(String v) {
        return io.appium.java_client.AppiumBy.androidUIAutomator(
                "new UiSelector().text(\"" + v + "\")");
    }

    @Step("Open the Shop tab")
    public boolean open() {
        click(TAB_SHOP);
        // The Shop content loads over the network — the "Discover" header shows first and the
        // chips/cards arrive a beat later. Wait for the chips; if the first tap landed before the
        // tab was ready, tap once more.
        if (waitFor(CHIP_PRODUCTS, 20000) || isPresent(PRODUCT_CARD)) return true;
        click(TAB_SHOP);
        return waitFor(CHIP_PRODUCTS, 20000) || waitFor(PRODUCT_CARD, 20000);
    }

    @Step("Wait for the Shop screen")
    public boolean isLoaded() {
        return waitFor(CHIP_PRODUCTS, 15000) || isPresent(PRODUCT_CARD) || isPresent(CHIP_ALL);
    }

    @Step("Check the All / Products / Services filter chips are present")
    public boolean hasFilterChips() {
        waitFor(CHIP_PRODUCTS, 15000);
        return isPresent(CHIP_ALL) && isPresent(CHIP_PRODUCTS) && isPresent(CHIP_SERVICES);
    }

    @Step("Select the {name} filter")
    public boolean selectFilter(String name) {
        By chip = textExact(name);
        waitFor(chip, 10000);
        boolean tapped = click(chip);
        pause(2500);
        return tapped;
    }

    @Step("Count the product cards")
    public int productCardCount() {
        waitFor(PRODUCT_CARD, 12000);
        return all(PRODUCT_CARD).size();
    }

    @Step("Check services are listed")
    public boolean hasServices() {
        return waitFor(SERVICE_CTA, 15000) || isPresent(SERVICE_PRICE);
    }

    @Step("Count the service cards")
    public int serviceCardCount() {
        return all(SERVICE_CTA).size();
    }

    @Step("Open the first product")
    public boolean openFirstProduct() {
        return waitFor(PRODUCT_CARD, 12000) && click(PRODUCT_CARD);
    }

    @Step("Open the first service")
    public boolean openFirstService() {
        return waitFor(SERVICE_CTA, 12000) && click(SERVICE_CTA);
    }
}
