package com.portfolio.ecommerce.tests.website.storefront;

import com.portfolio.ecommerce.website.base.BaseTest;
import com.portfolio.ecommerce.website.config.ConfigReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * Section 6 — CHECKOUT, STEP 1 (address) + endpoint guards.
 *
 * The checkout stepper is address -> payment -> review, and STEP 1 auto-selects the
 * isDefault address (else the first). This suite asserts the API contract behind STEP 1
 * without creating any orders:
 *   - GET /api/customer/addresses is protected and exposes exactly one default,
 *   - POST /api/customer/checkout is protected (no anonymous order creation).
 *
 * NOTE: the COD-availability matrix (custom-size / codEnabled=false / courier-no-COD) and
 * the three place-order outcomes (COD / custom-approval / online-Razorpay) are UI + order-
 * creating flows; the COD happy path is covered by {@link StorefrontCheckoutTest}. They are
 * intentionally out of scope here to avoid mutating the shared backend.
 */
@Epic("Checkout")
@Feature("Address step & checkout guards")
public class CheckoutAddressApiTest extends BaseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected String getUrl() {
        return ConfigReader.getInstance().getStorefrontUrl();
    }

    private String apiBase() {
        String base = ConfigReader.getInstance().getApiUrl();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private JsonNode tree(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            throw new AssertionError("Response was not valid JSON: " + body, e);
        }
    }

    private String customerToken() {
        ConfigReader config = ConfigReader.getInstance();
        APIResponse resp = getPage().request().post(apiBase() + "/auth/login",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setData(Map.of("email", config.getCustomerEmail(),
                                "password", config.getCustomerPassword())));
        Assert.assertTrue(resp.ok(), "Customer login should succeed");
        String token = tree(resp.text()).path("data").path("token").asText(null);
        if (token == null) token = tree(resp.text()).path("token").asText(null);
        Assert.assertNotNull(token, "Customer login must return a token");
        return token;
    }

    private JsonNode addresses(String token) {
        APIResponse resp = getPage().request().get(apiBase() + "/customer/addresses",
                RequestOptions.create().setHeader("Authorization", "Bearer " + token));
        Assert.assertEquals(resp.status(), 200, "GET addresses should return 200 for an authed customer");
        JsonNode data = tree(resp.text()).path("data");
        JsonNode list = data.path("addresses");
        return list.isArray() ? list : data;
    }

    @Test(description = "TC-CHKA001: The addresses endpoint requires authentication")
    @Description("Calls GET /customer/addresses with no token and verifies it returns 401.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Address book is protected")
    public void addressesRequireAuth() {
        APIResponse resp = getPage().request().get(apiBase() + "/customer/addresses");
        Assert.assertEquals(resp.status(), 401, "Anonymous GET /customer/addresses must be 401");
    }

    @Test(description = "TC-CHKA002: Checkout cannot be placed anonymously")
    @Description("Posts to /customer/checkout with no token and verifies it returns 401 — orders cannot be "
            + "created without an authenticated session.")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Checkout is protected")
    public void checkoutRequiresAuth() {
        APIResponse resp = getPage().request().post(apiBase() + "/customer/checkout",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setData(Map.of()));
        Assert.assertEquals(resp.status(), 401,
                "Anonymous POST /customer/checkout must be rejected (401), got " + resp.status());
    }

    @Test(description = "TC-CHKA003: The customer has exactly one default address for STEP 1 auto-selection")
    @Description("Reads the customer's saved addresses and verifies exactly one is marked default — the "
            + "checkout STEP 1 auto-selects that address.")
    @Severity(SeverityLevel.NORMAL)
    @Story("Default address selection")
    public void exactlyOneDefaultAddress() {
        JsonNode list = addresses(customerToken());
        if (list.size() == 0) {
            throw new org.testng.SkipException(
                    "Precondition not met: the checkout customer has no saved addresses in this environment");
        }

        int defaults = 0;
        for (JsonNode a : list) {
            if (a.path("isDefault").asBoolean(false)) defaults++;
        }
        Assert.assertEquals(defaults, 1,
                "Exactly one address must be the default (STEP 1 auto-selects it); found " + defaults);
    }
}
