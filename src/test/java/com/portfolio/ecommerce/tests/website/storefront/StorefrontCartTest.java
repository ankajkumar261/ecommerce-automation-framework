package com.portfolio.ecommerce.tests.website.storefront;

import com.portfolio.ecommerce.website.base.BaseTest;
import com.portfolio.ecommerce.website.config.ConfigReader;
import com.portfolio.ecommerce.website.pages.storefront.CartDrawer;
import com.portfolio.ecommerce.website.pages.storefront.ProductDetailPage;
import com.portfolio.ecommerce.website.pages.storefront.StorefrontLoginPage;
import com.portfolio.ecommerce.website.pages.storefront.StorefrontPage;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Storefront cart management: change quantity and remove an item.
 *
 * login -> open storefront home -> open first product -> select size -> add to cart -> in the cart drawer:
 *   - quantity starts at 1
 *   - increase -> quantity 2 and subtotal goes up
 *   - decrease -> back to 1
 *   - remove   -> item leaves the cart
 *
 * Operations are scoped to the product's own line item, so other items already in the
 * customer's cart don't affect the assertions.
 */
@Epic("Storefront")
@Feature("Cart management")
public class StorefrontCartTest extends BaseTest {

    @Override
    protected String getUrl() {
        String base = ConfigReader.getInstance().getStorefrontUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/login";
    }

    @Test(description = "TC-CART001: Change quantity and remove an item in the cart")
    @Severity(SeverityLevel.NORMAL)
    @Story("Update quantity and remove")
    @Description("Adds a product, then in the cart drawer increases and decreases its quantity "
            + "(checking the number and subtotal react) and finally removes it from the cart.")
    public void updateQuantityAndRemove() {
        ConfigReader config = ConfigReader.getInstance();

        logStep("Log in as the customer");
        StorefrontLoginPage login = new StorefrontLoginPage(getPage());
        login.loginAs(config.getCustomerEmail(), config.getCustomerPassword());
        Assert.assertTrue(login.isLoggedIn(), "Customer should be logged in before shopping");

        logStep("Open the storefront and pick the first product");
        StorefrontPage storefront = new StorefrontPage(getPage());
        // The old T-Shirt category was removed — open the storefront home and pick the first product.
        storefront.open(config.getStorefrontUrl());
        String product = storefront.openFirstProduct();

        logStep("Select a size and add to cart: " + product);
        ProductDetailPage pdp = new ProductDetailPage(getPage());
        pdp.selectFirstSize();
        pdp.addToCart();

        CartDrawer cart = new CartDrawer(getPage());
        cart.waitForOpen();
        Assert.assertTrue(cart.containsProduct(product),
                "Cart should contain the added product: " + product);

        // The cart persists across runs and accumulates. Trash the line to clear it, then
        // re-add a fresh one so we start from a known quantity of 1.
        logStep("Clear the item (trash), then re-add it fresh");
        cart.removeItem(product);
        Assert.assertTrue(cart.isProductGone(product),
                "Item should be cleared from the cart before re-adding: " + product);
        cart.close();

        pdp.addToCart();
        cart.waitForOpen();
        Assert.assertEquals(cart.getQuantity(product), 1, "Quantity should be 1 after re-adding");
        int subtotalAt1 = cart.getSubtotalAmount();

        logStep("Increase quantity to 2");
        cart.increaseQuantity(product);
        Assert.assertEquals(cart.getQuantity(product), 2, "Quantity should be 2 after increase");
        int subtotalAt2 = cart.getSubtotalAmount();
        Assert.assertTrue(subtotalAt2 > subtotalAt1,
                "Subtotal should increase when quantity goes up (was " + subtotalAt1
                        + ", now " + subtotalAt2 + ")");

        logStep("Decrease quantity back to 1");
        cart.decreaseQuantity(product);
        Assert.assertEquals(cart.getQuantity(product), 1, "Quantity should be 1 after decrease");

        logStep("Remove the item from the cart");
        cart.removeItem(product);
        Assert.assertTrue(cart.isProductGone(product),
                "Product should be removed from the cart: " + product);
    }
}