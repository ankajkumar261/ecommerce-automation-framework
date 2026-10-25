package com.portfolio.ecommerce.tests.website.order;

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

import java.util.List;
import java.util.Map;

/**
 * Section 7 — ORDER LIFECYCLE, positive path (vendor drives own orders).
 *
 * Complements the negative/guard suite ({@link OrderLifecycleApiTest}) by exercising the rules
 * that require a real transition:
 *   - a valid FORWARD step on a PAID order succeeds and persists,
 *   - a BACKWARD step is rejected and the order is unchanged,
 *   - an unpaid ONLINE order cannot be advanced ("customer must pay first").
 *
 * These mutate order state on the dev env (advancing an order forward is one-way); each test
 * discovers its own order at runtime so runs stay independent.
 */
@Epic("Order lifecycle")
@Feature("Forward transitions & payment gate")
public class OrderLifecyclePositiveApiTest extends BaseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** utils/orderStatus.js ORDER_STATUS_FLOW. */
    private static final List<String> FLOW = List.of(
            "pending", "confirmed", "processing", "ready_for_dispatch",
            "shipped", "out_for_delivery", "delivered");

    @Override
    protected String getUrl() {
        return ConfigReader.getInstance().getVendorUrl();
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

    private String vendorToken() {
        ConfigReader c = ConfigReader.getInstance();
        APIResponse resp = getPage().request().post(apiBase() + "/vendor/login",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setData(Map.of("email", c.getVendorEmail(), "password", c.getVendorPassword())));
        Assert.assertTrue(resp.ok(), "Vendor login should succeed");
        String token = tree(resp.text()).path("data").path("token").asText(null);
        if (token == null) token = tree(resp.text()).path("token").asText(null);
        Assert.assertNotNull(token, "Vendor login must return a token");
        return token;
    }

    private JsonNode vendorOrders(String token) {
        APIResponse resp = getPage().request().get(apiBase() + "/vendor/my-orders?limit=200",
                RequestOptions.create().setHeader("Authorization", "Bearer " + token));
        Assert.assertEquals(resp.status(), 200, "Vendor should read own orders");
        return tree(resp.text()).path("orders");
    }

    private String statusOf(String token, String orderId) {
        for (JsonNode o : vendorOrders(token)) {
            if (orderId.equals(o.path("_id").asText())) return o.path("status").asText();
        }
        return "<not found>";
    }

    private APIResponse putStatus(String token, String orderId, String status) {
        return getPage().request().put(apiBase() + "/vendor/my-orders/" + orderId + "/status",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + token)
                        .setHeader("Content-Type", "application/json")
                        .setData(Map.of("status", status)));
    }

    // ─────────────────────────────────────────────────────────

    @Test(description = "TC-OLP001: A valid forward transition on a paid order succeeds and persists")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Forward transition succeeds")
    @Description("Finds a PAID vendor order in an in-flow, non-terminal state and advances it one step "
            + "forward, verifying the API returns success and the order's status actually moves to the next stage.")
    public void validForwardTransitionSucceeds() {
        String token = vendorToken();
        JsonNode chosen = null;
        int idx = -1;
        for (JsonNode o : vendorOrders(token)) {
            if (!"paid".equals(o.path("paymentStatus").asText())) continue;
            int i = FLOW.indexOf(o.path("status").asText());
            if (i >= 0 && i < FLOW.size() - 1) { chosen = o; idx = i; break; }
        }
        if (chosen == null) throw new SkipException("No paid, advanceable vendor order found");

        String id = chosen.path("_id").asText();
        String next = FLOW.get(idx + 1);
        APIResponse resp = putStatus(token, id, next);
        Assert.assertTrue(resp.ok(),
                "A valid forward transition to '" + next + "' should succeed (HTTP " + resp.status() + ")");
        Assert.assertEquals(statusOf(token, id), next,
                "The order status should now be '" + next + "'");
    }

    @Test(description = "TC-OLP002: A backward transition on a paid order is allowed (flexible control)")
    @Severity(SeverityLevel.NORMAL)
    @Story("Flexible status control")
    @Description("Business rule (confirmed with the product owner): the vendor/admin may move an order "
            + "backwards even when it is PAID. Finds a paid vendor order past 'pending', moves it one step "
            + "BACK, and verifies the transition is allowed (2xx) and the status actually changed.")
    public void backwardTransitionAllowedOnPaidOrder() {
        String token = vendorToken();
        JsonNode chosen = null;
        int idx = -1;
        for (JsonNode o : vendorOrders(token)) {
            if (!"paid".equals(o.path("paymentStatus").asText())) continue;
            int i = FLOW.indexOf(o.path("status").asText());
            if (i >= 1 && i < FLOW.size() - 1) { chosen = o; idx = i; break; }
        }
        if (chosen == null) throw new SkipException("No paid order past 'pending' found");

        String id = chosen.path("_id").asText();
        String current = FLOW.get(idx);
        String previous = FLOW.get(idx - 1);
        APIResponse resp = putStatus(token, id, previous);
        Assert.assertTrue(resp.status() >= 200 && resp.status() < 300,
                "A backward transition (" + current + "→" + previous + ") should be allowed (2xx), got " + resp.status());
        Assert.assertEquals(statusOf(token, id), previous,
                "The order should have moved back to '" + previous + "'");
    }

    @Test(description = "TC-OLP003: A vendor can advance an unpaid online order (no payment gate on status)")
    @Severity(SeverityLevel.NORMAL)
    @Story("Order status flow")
    @Description("Business rule (confirmed with the product owner): the vendor/admin may move an order "
            + "forward even when the online payment is still pending — status changes are not payment-gated. "
            + "Finds an unpaid ONLINE 'pending' order, advances it to 'confirmed', and verifies it succeeds "
            + "and the status actually changed.")
    public void unpaidOnlineOrderCanBeAdvanced() {
        String token = vendorToken();
        JsonNode chosen = null;
        for (JsonNode o : vendorOrders(token)) {
            if ("pending".equals(o.path("status").asText())
                    && "online".equals(o.path("paymentMethod").asText())
                    && !"paid".equals(o.path("paymentStatus").asText())) {
                chosen = o; break;
            }
        }
        if (chosen == null) throw new SkipException("No unpaid online pending order found");

        String id = chosen.path("_id").asText();
        APIResponse resp = putStatus(token, id, "confirmed");
        Assert.assertTrue(resp.status() >= 200 && resp.status() < 300,
                "Advancing an unpaid online order should be allowed (2xx), got " + resp.status());
        Assert.assertEquals(statusOf(token, id), "confirmed",
                "The order should have advanced to 'confirmed'");
    }
}
