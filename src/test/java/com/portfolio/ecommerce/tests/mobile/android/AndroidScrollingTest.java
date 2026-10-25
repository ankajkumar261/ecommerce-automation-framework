package com.portfolio.ecommerce.tests.mobile.android;

import com.portfolio.ecommerce.mobile.android.base.AndroidBaseTest;
import com.portfolio.ecommerce.mobile.android.screens.AndroidHomeScreen;
import com.portfolio.ecommerce.mobile.android.screens.AndroidProductDetailScreen;
import com.portfolio.ecommerce.mobile.android.screens.AndroidShopScreen;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Scrolling validation on Android — the counterpart of the iOS ScrollingTest.
 *
 * Long content screens (Home, Shop, Product detail) must scroll from top to bottom without
 * crashing, freezing, or losing their content. Browsing is public, so no sign-in is needed.
 */
@Epic("Mobile (Android)")
@Feature("Scrolling")
public class AndroidScrollingTest extends AndroidBaseTest {

    @Test(description = "TC-AND048: Home scrolls top to bottom without issues")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Scroll Home")
    public void homeScrollsTopToBottom() {
        Assert.assertTrue(home.isLoaded(), "Home should load");
        Assert.assertTrue(home.productCardCount() > 0, "Home should show product cards");

        logStep("Scroll down through Home");
        for (int i = 0; i < 6; i++) home.swipeUp();
        Assert.assertTrue(home.isLoaded(), "Home should still be loaded after scrolling down");

        logStep("Scroll back up");
        for (int i = 0; i < 6; i++) home.swipeDown();
        Assert.assertTrue(home.productCardCount() > 0,
                "Home should still show products after scrolling back up");
    }

    @Test(description = "TC-AND049: Shop scrolls top to bottom without issues")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Scroll Shop")
    public void shopScrollsTopToBottom() {
        AndroidShopScreen shop = new AndroidShopScreen(driver);
        Assert.assertTrue(shop.open(), "Shop should open");
        Assert.assertTrue(shop.isLoaded(), "Shop should load");

        int before = shop.productCardCount();
        log.info("Products on Shop: {}", before);

        logStep("Scroll down through Shop");
        for (int i = 0; i < 6; i++) shop.swipeUp();
        Assert.assertTrue(shop.isLoaded(), "Shop should still be loaded after scrolling");

        logStep("Scroll back up");
        for (int i = 0; i < 6; i++) shop.swipeDown();
        Assert.assertTrue(shop.isLoaded(), "Shop should still be loaded after scrolling back");
    }

    @Test(description = "TC-AND050: Product detail scrolls top to bottom without issues")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Scroll product detail")
    public void productDetailScrollsTopToBottom() {
        Assert.assertTrue(home.isLoaded(), "Home should load");
        Assert.assertTrue(home.openFirstProduct(), "A product should open from Home");

        AndroidProductDetailScreen pdp = new AndroidProductDetailScreen(driver);
        Assert.assertTrue(pdp.isLoaded(), "Product detail should load");

        logStep("Scroll down through product detail");
        for (int i = 0; i < 8; i++) pdp.swipeUp();
        Assert.assertTrue(pdp.isLoaded(),
                "Product detail should still be loaded after scrolling to the bottom");

        logStep("Scroll back up");
        for (int i = 0; i < 8; i++) pdp.swipeDown();
        Assert.assertTrue(pdp.isLoaded(),
                "Product detail should still be loaded after scrolling back to the top");
    }
}
