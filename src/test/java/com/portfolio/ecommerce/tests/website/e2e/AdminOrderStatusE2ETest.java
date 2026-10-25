package com.portfolio.ecommerce.tests.website.e2e;

import com.portfolio.ecommerce.website.base.BaseTest;
import com.portfolio.ecommerce.website.config.ConfigReader;
import com.portfolio.ecommerce.website.drivers.BrowserManager;
import com.portfolio.ecommerce.website.pages.admin.AdminLoginPage;
import com.portfolio.ecommerce.website.pages.admin.AdminOrderDetailPage;
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
 * Order status update (E2E):
 *   shopper places a fresh COD order (starts as Pending) -> admin opens it ->
 *   changes the status to Processing -> the order's status reflects the change.
 *
 * Picks whatever product is available at runtime (no hardcoded product/category).
 * Online payment is not automated (no gateway test mode), so if the chosen product
 * doesn't offer COD the test SKIPS. The fresh order guarantees a known starting state
 * (Pending). Admin runs in a separate context (panel.theecommerce.com).
 */
@Epic("End to End")
@Feature("Admin order status update")
public class AdminOrderStatusE2ETest extends BaseTest {

    private static final String NEW_STATUS = "Processing";

    @Override
    protected String getUrl() {
        String base = ConfigReader.getInstance().getStorefrontUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/login";
    }

    @Test(description = "TC-E2E003: Admin updates the status of a freshly placed order")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Place order -> admin changes status -> status updated")
    @Description("A shopper places a COD order (Pending). Admin opens the order and changes its "
            + "status to Processing, then we verify the order's status badge reflects the change. "
            + "Skips if the chosen product doesn't offer COD.")
    public void adminUpdatesOrderStatus() {
        ConfigReader config = ConfigReader.getInstance();

        // ── Shopper places a fresh order ──
        logStep("Customer logs in and places a COD order");
        StorefrontLoginPage login = new StorefrontLoginPage(getPage());
        login.loginAs(config.getCustomerEmail(), config.getCustomerPassword());
        Assert.assertTrue(login.isLoggedIn(), "Customer should be logged in");

        StorefrontPage storefront = new StorefrontPage(getPage());
        // NOTE: the configured COD product is not reachable via the storefront UI in automation
        // (see StorefrontCheckoutTest). Open the first product; skips below if it is not COD.
        storefront.open(config.getStorefrontUrl());
        String product = storefront.openFirstProduct();

        ProductDetailPage pdp = new ProductDetailPage(getPage());
        pdp.selectFirstSize();
        pdp.addToCart();

        CartDrawer cart = new CartDrawer(getPage());
        cart.waitForOpen();
        Assert.assertTrue(cart.containsProduct(product), "Cart should contain: " + product);
        cart.clickCheckout();

        CheckoutPage checkout = new CheckoutPage(getPage());
        if (!checkout.hasSavedAddress()) {
            throw new SkipException("Precondition not met: the customer has no saved address to check out with");
        }
        checkout.selectFirstAddress();

        // Online payment can't be automated safely (no gateway test mode). Skip if no COD.
        if (!checkout.isCodAvailable()) {
            throw new SkipException("COD not available for '" + product
                    + "' — skipping (online payment can't be automated without a test-mode gateway)");
        }

        checkout.selectCOD();
        checkout.placeOrder();
        Assert.assertTrue(checkout.isOrderConfirmed(), "Order should be confirmed");
        String orderId = checkout.getOrderId();

        // ── Admin updates the status ──
        logStep("Open admin in a fresh context and log in");
        Page adminPage = BrowserManager.getSecondaryPage();
        adminPage.navigate(config.getAdminUrl(), new Page.NavigateOptions()
                .setTimeout(45000)
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        new AdminLoginPage(adminPage).loginAs(config.getAdminEmail(), config.getAdminPassword());

        AdminOrdersPage orders = new AdminOrdersPage(adminPage);
        orders.openOrders();

        logStep("New order should start as Pending");
        Assert.assertTrue(orders.isOrderPresent(orderId),
                "Freshly placed order should appear in admin: " + orderId);
        Assert.assertEquals(orders.getStatusBadge(orderId), "Pending",
                "A freshly placed order should start in Pending");

        logStep("Open the order and change its status to " + NEW_STATUS);
        orders.openOrder(orderId);
        AdminOrderDetailPage detail = new AdminOrderDetailPage(adminPage);
        detail.changeStatus(NEW_STATUS);

        logStep("Back on the list, the order's status should now be " + NEW_STATUS);
        orders.openOrders();
        Assert.assertEquals(orders.getStatusBadge(orderId), NEW_STATUS,
                "Order status should be updated to " + NEW_STATUS + " after the admin change");
    }
}