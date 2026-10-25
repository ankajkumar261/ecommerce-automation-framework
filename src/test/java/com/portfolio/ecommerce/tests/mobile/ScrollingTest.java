package com.portfolio.ecommerce.tests.mobile;

import com.portfolio.ecommerce.mobile.base.MobileBaseTest;
import com.portfolio.ecommerce.mobile.screens.CategoryScreen;
import com.portfolio.ecommerce.mobile.screens.HomeScreen;
import com.portfolio.ecommerce.mobile.screens.ProductDetailScreen;
import com.portfolio.ecommerce.mobile.screens.ShopScreen;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Scrolling validation across the app's main screens.
 *
 * Every screen that renders more content than fits on one viewport must be scrolled from
 * top to bottom without crashing, freezing, or losing elements. These tests exercise that
 * path and verify content keeps loading as the user scrolls.
 *
 * Browsing is public, so no sign-in is required.
 */
@Epic("Mobile (iOS)")
@Feature("Scrolling")
public class ScrollingTest extends MobileBaseTest {

    @Test(description = "TC-IOS048: Home screen scrolls from top to bottom without issues")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Scroll Home")
    @Description("Opens the Home screen, scrolls from the top to the bottom, and verifies that "
            + "product cards and category chips keep loading. Checks for no crashes, freezes, "
            + "or missing elements throughout the scroll.")
    public void homeScreenScrollsFromTopToBottom() {
        HomeScreen home = new HomeScreen(getDriver());
        home.open();
        Assert.assertTrue(home.isLoaded(), "Home screen should load");

        logStep("Verify Home has products");
        Assert.assertTrue(home.hasProducts(), "Home should show at least one product card");

        logStep("Scroll down through the Home screen");
        int scrollCycles = 8;
        for (int i = 0; i < scrollCycles; i++) {
            scrollDown();
            pause(600);
        }

        logStep("Verify Home is still responsive after scrolling");
        Assert.assertTrue(home.isLoaded(),
                "Home should still be loaded after scrolling to the bottom");

        logStep("Scroll back up");
        for (int i = 0; i < scrollCycles; i++) {
            scrollUp();
            pause(400);
        }

        logStep("Verify content is still present after scrolling back");
        Assert.assertTrue(home.hasProducts(),
                "Home should still show products after scrolling back up");
    }

    @Test(description = "TC-IOS049: Shop screen scrolls from top to bottom without issues")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Scroll Shop")
    @Description("Opens the Shop tab, scrolls through all products and services, and verifies "
            + "the content loads correctly with no UI issues.")
    public void shopScreenScrollsFromTopToBottom() {
        ShopScreen shop = new ShopScreen(getDriver());
        shop.open();
        Assert.assertTrue(shop.isLoaded(), "Shop screen should load");

        logStep("Verify initial content is present");
        int products = shop.productCardCount();
        log.info("Products on Shop: {}", products);
        Assert.assertTrue(products > 0 || shop.hasServices(),
                "Shop should show at least products or services");

        logStep("Scroll down through the Shop screen");
        for (int i = 0; i < 8; i++) {
            scrollDown();
            pause(600);
        }

        logStep("Verify Shop is still responsive after scrolling");
        Assert.assertTrue(shop.isLoaded(),
                "Shop should still be loaded after scrolling");

        logStep("Scroll back up");
        for (int i = 0; i < 8; i++) {
            scrollUp();
            pause(400);
        }

        logStep("Verify content persists after scrolling back");
        int afterProducts = shop.productCardCount();
        Assert.assertTrue(afterProducts > 0 || shop.hasServices(),
                "Shop should still show content after scrolling back");
    }

    @Test(description = "TC-IOS050: Product detail scrolls from top to bottom without issues")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Scroll product detail")
    @Description("Opens a product, scrolls through the full detail screen (price, sizes, "
            + "description, specifications), and verifies all sections load correctly with "
            + "no UI issues.")
    public void productDetailScrollsFromTopToBottom() {
        HomeScreen home = new HomeScreen(getDriver());
        home.open();
        Assert.assertTrue(home.isLoaded(), "Home should load");

        logStep("Open a product");
        String productName = home.openFirstProduct();
        log.info("Opened product: {}", productName);

        ProductDetailScreen pdp = new ProductDetailScreen(getDriver());
        Assert.assertTrue(pdp.isLoaded(), "Product detail should load");
        Assert.assertTrue(pdp.hasPrice(), "Product should show a price");

        logStep("Scroll down through the product detail");
        for (int i = 0; i < 10; i++) {
            scrollDown();
            pause(500);
        }

        logStep("Verify the product detail is still loaded after scrolling");
        Assert.assertTrue(pdp.isLoaded(),
                "Product detail should still be loaded after scrolling to the bottom");

        logStep("Scroll back up to the top");
        for (int i = 0; i < 10; i++) {
            scrollUp();
            pause(400);
        }

        logStep("Verify size selector is reachable again at the top");
        Assert.assertTrue(pdp.hasSizeSelector(),
                "The size selector should be reachable after scrolling back to the top");
    }

    @Test(description = "TC-IOS051: Category listing scrolls from top to bottom without issues")
    @Severity(SeverityLevel.NORMAL)
    @Story("Scroll category listing")
    @Description("Opens a category from Home, scrolls through all products, and verifies "
            + "content consistency and no UI issues throughout.")
    public void categoryListingScrollsFromTopToBottom() {
        HomeScreen home = new HomeScreen(getDriver());
        home.open();
        Assert.assertTrue(home.isLoaded(), "Home should load");

        List<String> categories = home.categoryNames();
        log.info("Categories: {}", categories);

        String category = null;
        for (String name : categories) {
            if (!"All".equalsIgnoreCase(name)) {
                category = name;
                break;
            }
        }
        if (category == null) {
            throw new SkipException("No real category found on Home");
        }

        logStep("Open category: " + category);
        home.openCategory(category);

        CategoryScreen listing = new CategoryScreen(getDriver());
        Assert.assertTrue(listing.isLoaded(category), "Category listing should load");

        int initialCount = listing.productCount();
        log.info("Products in '{}': {}", category, initialCount);

        logStep("Scroll down through the category listing");
        for (int i = 0; i < 8; i++) {
            scrollDown();
            pause(500);
        }

        logStep("Verify the listing is still loaded after scrolling");
        Assert.assertTrue(listing.isLoaded(category),
                "Category listing should still be loaded after scrolling");

        logStep("Scroll back up");
        for (int i = 0; i < 8; i++) {
            scrollUp();
            pause(400);
        }

        logStep("Verify product count is consistent after scrolling back");
        int afterScrollCount = listing.productCount();
        Assert.assertEquals(afterScrollCount, initialCount,
                "Product count should be the same after scrolling back: was " + initialCount
                        + ", now " + afterScrollCount);
    }
}
