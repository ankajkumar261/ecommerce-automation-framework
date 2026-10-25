package com.portfolio.ecommerce.tests.website.storefront;

import com.portfolio.ecommerce.website.base.BaseTest;
import com.portfolio.ecommerce.website.config.ConfigReader;
import com.portfolio.ecommerce.website.drivers.BrowserManager;
import com.portfolio.ecommerce.website.pages.admin.AdminLoginPage;
import com.portfolio.ecommerce.website.pages.admin.AdminOrdersPage;
import com.portfolio.ecommerce.website.pages.storefront.CartDrawer;
import com.portfolio.ecommerce.website.pages.storefront.CheckoutPage;
import com.portfolio.ecommerce.website.pages.storefront.ProductDetailPage;
import com.portfolio.ecommerce.website.pages.storefront.StorefrontLoginPage;
import com.portfolio.ecommerce.website.pages.storefront.StorefrontPage;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * Storefront shopper purchase flow (Cash on Delivery), end to end, with admin verification:
 *   login -> storefront home -> open first available product -> select size -> add to cart
 *   -> verify cart -> checkout -> address -> COD -> place order -> capture order ID
 *   -> admin logs in (fresh context) -> Orders -> the order is present.
 *
 * Picks whatever product is available at runtime (no hardcoded product/category).
 * Online payment is NOT automated (the gateway has no test mode = real money), so if the
 * chosen product does not offer COD, the test SKIPS (yellow) instead of paying for real.
 *
 * Admin runs in a separate browser context because it lives on panel.theecommerce.com.
 * BaseTest navigates the page to the storefront /login URL before the test (getUrl override).
 */
@Epic("Storefront")
@Feature("Shopper checkout (Cash on Delivery)")
public class StorefrontCheckoutTest extends BaseTest {

    @Override
    protected String getUrl() {
        String base = ConfigReader.getInstance().getStorefrontUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/login";
    }

    @Test(description = "TC-CHK001: Logged-in shopper buys a product via Cash on Delivery")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Add to cart -> COD -> place order")
    @Description("A customer logs in, opens the first available product, selects a size, adds it to "
            + "the cart, proceeds to checkout, picks the saved address and Cash on Delivery, and "
            + "places the order — verifying the order is confirmed. Skips if COD isn't offered.")
    public void shopperPlacesCodOrder() {
        ConfigReader config = ConfigReader.getInstance();

        logStep("Log in as the customer");
        StorefrontLoginPage login = new StorefrontLoginPage(getPage());
        login.loginAs(config.getCustomerEmail(), config.getCustomerPassword());
        Assert.assertTrue(login.isLoggedIn(), "Customer should be logged in before shopping");

        logStep("Open the storefront and pick the first available product");
        StorefrontPage storefront = new StorefrontPage(getPage());
        storefront.open(config.getStorefrontUrl());
        // NOTE: the configured COD product is not reachable via the storefront UI in automation
        // (the two COD-eligible products are not on the home listing, the /search page renders no
        // product cards to the driver, and the product slug 404s on direct navigation). So this
        // opens the first product and skips below if it is not COD-eligible — the pre-existing
        // behaviour. To make this run: mark the COD product as Featured on dev, or place the COD
        // order via the API instead of the UI.
        String product = storefront.openFirstProduct();

        logStep("Select a size and add to cart: " + product);
        ProductDetailPage pdp = new ProductDetailPage(getPage());
        pdp.selectFirstSize();
        pdp.addToCart();

        logStep("Verify the cart drawer shows the product");
        CartDrawer cart = new CartDrawer(getPage());
        cart.waitForOpen();
        Assert.assertTrue(cart.containsProduct(product),
                "Cart should contain the product that was just added: " + product);

        logStep("Proceed to checkout");
        cart.clickCheckout();

        logStep("Select address");
        CheckoutPage checkout = new CheckoutPage(getPage());
        if (!checkout.hasSavedAddress()) {
            throw new SkipException("Precondition not met: the customer has no saved address to check out with");
        }
        checkout.selectFirstAddress();

        // Online payment can't be automated safely (no gateway test mode). If this product
        // doesn't offer COD, skip rather than attempt a real online payment.
        if (!checkout.isCodAvailable()) {
            throw new SkipException("COD not available for '" + product
                    + "' — skipping (online payment can't be automated without a test-mode gateway)");
        }

        logStep("Choose COD and place the order");
        checkout.selectCOD();
        checkout.placeOrder();

        logStep("Verify the order was confirmed");
        Assert.assertTrue(checkout.isOrderConfirmed(),
                "Order should be confirmed after placing the COD order");

        String orderId = checkout.getOrderId();

        // ── Admin leg (fresh context: admin is on panel.theecommerce.com) ──
        logStep("Open admin in a fresh context and log in");
        Page adminPage = BrowserManager.getSecondaryPage();
        adminPage.navigate(config.getAdminUrl(), new Page.NavigateOptions()
                .setTimeout(45000)
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        new AdminLoginPage(adminPage).loginAs(config.getAdminEmail(), config.getAdminPassword());

        logStep("Open Orders and verify the order is there: " + orderId);
        AdminOrdersPage orders = new AdminOrdersPage(adminPage);
        orders.openOrders();
        orders.search(orderId);
        Assert.assertTrue(orders.isOrderPresent(orderId),
                "Order '" + orderId + "' placed on the storefront should appear in admin Orders");
    }
}