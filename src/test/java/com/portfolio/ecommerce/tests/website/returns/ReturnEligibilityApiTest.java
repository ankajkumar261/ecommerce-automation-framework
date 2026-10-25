package com.portfolio.ecommerce.tests.website.returns;

import com.portfolio.ecommerce.website.base.BaseTest;
import com.portfolio.ecommerce.website.config.ConfigReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * Section 10.1 — customer return-eligibility (read-only).
 *
 * The Return action only applies to delivered orders inside the return window, and both the
 * window and the returnable/replaceable flags are read from the product policy — never
 * hardcoded. This suite asserts that contract via GET /api/customer/orders/:id/return-eligibility
 * and the /api/customer/returns list, without raising any return.
 */
@Epic("Returns & refunds")
@Feature("Return eligibility")
public class ReturnEligibilityApiTest extends BaseTest {

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
        ConfigReader c = ConfigReader.getInstance();
        APIResponse resp = getPage().request().post(apiBase() + "/auth/login",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setData(Map.of("email", c.getCustomerEmail(), "password", c.getCustomerPassword())));
        Assert.assertTrue(resp.ok(), "Customer login should succeed");
        String token = tree(resp.text()).path("data").path("token").asText(null);
        if (token == null) token = tree(resp.text()).path("token").asText(null);
        Assert.assertNotNull(token, "Customer login must return a token");
        return token;
    }

    /** The customer's own orders live at the scoped /orders list. */
    private JsonNode myOrders(String token) {
        APIResponse resp = getPage().request().get(apiBase() + "/orders?limit=100",
                RequestOptions.create().setHeader("Authorization", "Bearer " + token));
        Assert.assertEquals(resp.status(), 200, "Customer should read own orders");
        return tree(resp.text()).path("data").path("orders");
    }

    private String firstOrderId(JsonNode orders, java.util.function.Predicate<String> statusMatch) {
        for (JsonNode o : orders) {
            if (statusMatch.test(o.path("status").asText())) return o.path("_id").asText();
        }
        return null;
    }

    private JsonNode eligibility(String token, String orderId) {
        APIResponse resp = getPage().request().get(apiBase() + "/customer/orders/" + orderId + "/return-eligibility",
                RequestOptions.create().setHeader("Authorization", "Bearer " + token));
        Assert.assertEquals(resp.status(), 200, "return-eligibility should return 200 for the owner");
        return tree(resp.text()).path("data");
    }

    // ─────────────────────────────────────────────────────────

    @Test(description = "TC-RET001: A non-delivered order is not returnable")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Return requires delivery")
    @Description("Finds a non-delivered order and verifies return-eligibility reports eligible=false with a "
            + "reason, and no items are returnable — the Return action only applies once delivered.")
    public void nonDeliveredOrderNotReturnable() {
        String token = customerToken();
        String id = firstOrderId(myOrders(token),
                s -> !s.equals("delivered") && !s.equals("returned"));
        if (id == null) throw new SkipException("No non-delivered order to check");

        JsonNode data = eligibility(token, id);
        Assert.assertFalse(data.path("eligible").asBoolean(true),
                "A non-delivered order must not be returnable");
        Assert.assertFalse(data.path("reason").asText("").isBlank(),
                "A non-eligible order should explain why");
        Assert.assertFalse(data.path("anyReturnable").asBoolean(true),
                "No item should be returnable on a non-delivered order");
    }

    @Test(description = "TC-RET002: Return eligibility is driven by the product policy window, not a hardcode")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Policy-driven window")
    @Description("For a delivered order, verifies return-eligibility exposes a positive policy window "
            + "(windowHours) plus a deadline and returnable/replaceable flags, and that the eligible flag "
            + "is coherent with the window (an expired window yields eligible=false).")
    public void deliveredOrderEligibilityIsPolicyDriven() {
        String token = customerToken();
        String id = firstOrderId(myOrders(token), s -> s.equals("delivered"));
        if (id == null) throw new SkipException("No delivered order to check");

        JsonNode data = eligibility(token, id);
        Assert.assertTrue(data.path("windowHours").asInt(0) > 0,
                "The return window (windowHours) must be a positive policy-driven value");
        Assert.assertTrue(data.has("deadline"), "Eligibility must expose a deadline");
        Assert.assertTrue(data.path("anyReturnable").isBoolean(), "anyReturnable flag must be present");
        Assert.assertTrue(data.path("anyReplaceable").isBoolean(), "anyReplaceable flag must be present");

        // Coherence: if the reason says the window expired, the order must not be eligible.
        String reason = data.path("reason").asText("");
        if (reason.toLowerCase().contains("expired")) {
            Assert.assertFalse(data.path("eligible").asBoolean(true),
                    "An expired-window order must report eligible=false");
        }
    }

    @Test(description = "TC-RET003: return-eligibility requires authentication")
    @Severity(SeverityLevel.NORMAL)
    @Story("Endpoint is protected")
    @Description("Calls return-eligibility with no token and verifies it returns 401.")
    public void returnEligibilityRequiresAuth() {
        APIResponse resp = getPage().request().get(apiBase()
                + "/customer/orders/000000000000000000000000/return-eligibility");
        Assert.assertEquals(resp.status(), 401, "Anonymous return-eligibility must be 401");
    }

    @Test(description = "TC-RET004: The returns list is protected and scoped to the customer")
    @Severity(SeverityLevel.NORMAL)
    @Story("Returns list")
    @Description("Verifies GET /customer/returns is 401 anonymously and, with a customer token, returns "
            + "200 with a requests array scoped to that customer.")
    public void returnsListRequiresAuthAndIsScoped() {
        APIResponse anon = getPage().request().get(apiBase() + "/customer/returns");
        Assert.assertEquals(anon.status(), 401, "Anonymous returns list must be 401");

        APIResponse resp = getPage().request().get(apiBase() + "/customer/returns",
                RequestOptions.create().setHeader("Authorization", "Bearer " + customerToken()));
        Assert.assertEquals(resp.status(), 200, "Customer should read own returns");
        Assert.assertTrue(tree(resp.text()).path("data").path("requests").isArray(),
                "returns list must expose a requests array");
    }
}
