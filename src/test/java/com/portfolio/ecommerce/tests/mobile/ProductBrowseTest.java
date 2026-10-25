package com.portfolio.ecommerce.tests.mobile;

import com.portfolio.ecommerce.mobile.base.MobileBaseTest;
import com.portfolio.ecommerce.mobile.screens.HomeScreen;
import com.portfolio.ecommerce.mobile.screens.ProductDetailScreen;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * iOS browse + product-detail flow:
 *   Home (Discover) -> tap a product card -> Product Detail screen shows the product name,
 *   a price, a size selector and Add-to-cart.
 *
 * Browsing is public, so no login is required and this works in either auth state. The base
 * class puts the app back on Home before each test, so this starts where it expects to.
 */
@Epic("Mobile (iOS)")
@Feature("Browse & Product Detail")
public class    ProductBrowseTest extends MobileBaseTest {

    @Test(groups = {"mobile-smoke"}, description = "TC-IOS003: Shopper opens a product from Home and sees the product detail")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Open product detail")
    @Description("From the Home/Discover screen, taps the first product card and verifies the Product "
            + "Detail screen loads with the same product name, a price, a size selector and Add-to-cart.")
    public void opensProductDetailFromHome() {
        HomeScreen home = new HomeScreen(getDriver());

        logStep("Wait for Home to load");
        Assert.assertTrue(home.isLoaded(), "Home (Discover) screen should load");

        logStep("Open the first product card");
        String productName = home.openFirstProduct();
        log.info("Opened product: {}", productName);

        ProductDetailScreen pdp = new ProductDetailScreen(getDriver());

        logStep("Verify the Product Detail screen loaded");
        Assert.assertTrue(pdp.isLoaded(), "Product Detail screen should load after tapping a product");

        logStep("Verify the product name matches the card that was tapped");
        Assert.assertTrue(pdp.showsProductNamed(productName),
                "Product Detail should show the tapped product's name: " + productName);

        logStep("Verify price, size selector and Add-to-cart are present");
        Assert.assertTrue(pdp.hasPrice(), "A price should be shown on the product detail");
        Assert.assertTrue(pdp.hasSizeSelector(), "A size selector should be shown");
        Assert.assertTrue(pdp.hasAddToCart(), "Add to cart should be available");
    }
}
