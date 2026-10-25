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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * Storefront shopper purchase flow via ONLINE PAYMENT (Razorpay), end to end, with admin
 * verification:
 *   login -> home -> open first product -> size -> add to cart -> verify cart -> checkout
 *   -> address -> Online payment -> place order -> drive Razorpay test-mode card payment
 *   -> capture order ID -> admin (fresh context) -> Orders -> the order is present.
 *
 * SAFETY: this drives a REAL payment gateway. It runs ONLY when payment.testmode=true (the
 * dev env is in Razorpay TEST mode — a red "Test Mode" ribbon, test cards, no real money).
 * Without the flag it SKIPS, so it can never move real money against a live gateway.
 * Enable with:  mvn test -DsuiteFile=... -Dpayment.testmode=true
 */
@Epic("Storefront")
@Feature("Shopper checkout (Online payment / Razorpay test mode)")
public class StorefrontOnlinePaymentTest extends BaseTest {

    @Override
    protected String getUrl() {
        String base = ConfigReader.getInstance().getStorefrontUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/login";
    }

    @Test(description = "TC-CHK002: Logged-in shopper buys a product via online payment (Razorpay test mode)")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Add to cart -> Online payment -> Razorpay test card -> place order")
    @Description("A customer logs in, opens the first available product, adds it to the cart, "
            + "proceeds to checkout, chooses Online payment, and completes the Razorpay test-mode "
            + "card payment (4111...), verifying the order is confirmed and appears in admin. "
            + "Runs ONLY with -Dpayment.testmode=true; otherwise skips (never touches a live gateway).")
    public void shopperPlacesOnlinePaymentOrder() {
        ConfigReader config = ConfigReader.getInstance();

        // Hard gate: never drive the gateway unless test mode is explicitly enabled.
        if (!config.isPaymentTestMode()) {
            throw new SkipException("payment.testmode is not enabled — skipping online-payment "
                    + "automation (set -Dpayment.testmode=true only when the gateway is in test mode)");
        }

        logStep("Log in as the customer");
        StorefrontLoginPage login = new StorefrontLoginPage(getPage());
        login.loginAs(config.getCustomerEmail(), config.getCustomerPassword());
        Assert.assertTrue(login.isLoggedIn(), "Customer should be logged in before shopping");

        logStep("Open the storefront, then a product that has an in-stock size");
        StorefrontPage storefront = new StorefrontPage(getPage());
        storefront.open(config.getStorefrontUrl());
        // openFirstProduct() can land on a sold-out product; instead pick one that the
        // public catalogue says has an in-stock size, and open its PDP directly.
        Buyable buyable = pickBuyableProduct();
        String product = buyable.name();
        getPage().navigate(storefrontBase() + "/products/" + buyable.id(),
                new Page.NavigateOptions().setTimeout(45000).setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

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
        checkout.selectFirstAddress();

        if (!checkout.isOnlinePaymentAvailable()) {
            throw new SkipException("Online payment not offered for '" + product + "' — skipping");
        }

        logStep("Choose Online payment and place the order");
        checkout.selectOnlinePayment();
        checkout.placeOrder();

        logStep("Complete the Razorpay test-mode card payment");
        checkout.completeRazorpayTestPayment(config.getTestCardNumber());

        logStep("Verify the order was confirmed");
        Assert.assertTrue(checkout.isOrderConfirmed(),
                "Order should be confirmed after completing the online payment");

        String orderId = checkout.getOrderId();

        // ── Admin leg (fresh context) ──
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
                "Order '" + orderId + "' placed via online payment should appear in admin Orders");
    }

    // ── Buyable-product discovery (avoid sold-out products) ──────────────────
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** A purchasable product: id + display name, with at least one in-stock size. */
    private record Buyable(String id, String name) {}

    private String apiBase() {
        String base = ConfigReader.getInstance().getApiUrl();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private String storefrontBase() {
        String base = ConfigReader.getInstance().getStorefrontUrl();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /**
     * Finds a product the public catalogue reports as having an in-stock size, so the PDP
     * has a selectable (non-disabled) size. Skips automation's throwaway "QA " products.
     */
    private Buyable pickBuyableProduct() {
        APIResponse resp = getPage().request().get(apiBase() + "/public/products?limit=50");
        Assert.assertEquals(resp.status(), 200, "public products should return 200");
        try {
            JsonNode products = MAPPER.readTree(resp.text()).path("data").path("products");
            for (JsonNode p : products) {
                String name = p.path("name").asText("");
                if (name.startsWith("QA ")) continue;
                JsonNode sizes = p.path("sizes");
                JsonNode sizeStock = p.path("sizeStock");
                if (sizes.isArray()) {
                    for (JsonNode s : sizes) {
                        if (sizeStock.path(s.asText()).asInt(0) >= 1) {
                            return new Buyable(p.path("_id").asText(), name);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new AssertionError("Could not parse public products", e);
        }
        throw new SkipException("No product with an in-stock size is available to purchase");
    }
}
