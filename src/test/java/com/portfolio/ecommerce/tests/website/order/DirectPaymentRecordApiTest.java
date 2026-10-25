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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Section 8 (Direct Payment) — record-payment validation on handed_directly orders.
 *
 * PUT /api/orders/:id/record-payment (super_admin) records an installment on a
 * handed_directly order. Rules (doc §8): amount >= Rs 1 (except when it exactly equals the
 * remaining balance), amount may never push amountPaid past the order total, method must be
 * cash|upi|bank. This suite drives the NEGATIVES so every request is rejected and nothing is
 * recorded — and, for the amount cases, re-reads amountPaid to confirm it did not change.
 */
@Epic("Payments")
@Feature("Direct payment — record-payment validation")
public class DirectPaymentRecordApiTest extends BaseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected String getUrl() {
        return ConfigReader.getInstance().getAdminUrl();
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

    private String login(String path, String email, String password) {
        APIResponse resp = getPage().request().post(apiBase() + path,
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setData(Map.of("email", email, "password", password)));
        Assert.assertTrue(resp.ok(), "Login should succeed for " + email);
        String token = tree(resp.text()).path("data").path("token").asText(null);
        if (token == null) token = tree(resp.text()).path("token").asText(null);
        Assert.assertNotNull(token, "Login must return a token for " + email);
        return token;
    }

    private String adminToken() {
        ConfigReader c = ConfigReader.getInstance();
        return login("/auth/login", c.getAdminEmail(), c.getAdminPassword());
    }

    private String customerToken() {
        ConfigReader c = ConfigReader.getInstance();
        return login("/auth/login", c.getCustomerEmail(), c.getCustomerPassword());
    }

    /** A handed_directly order with headroom (total − amountPaid > 1) so tiny/over amounts are clearly invalid. */
    private record DirectOrder(String id, double total, double amountPaid) {}

    private DirectOrder findHandedDirectlyOrder(String token) {
        APIResponse resp = getPage().request().get(apiBase() + "/orders?limit=200",
                RequestOptions.create().setHeader("Authorization", "Bearer " + token));
        Assert.assertEquals(resp.status(), 200, "Admin should list orders");
        for (JsonNode o : tree(resp.text()).path("data").path("orders")) {
            if ("handed_directly".equals(o.path("paymentMethod").asText())) {
                double total = orderTotal(o);
                double paid = o.path("amountPaid").asDouble(0);
                if (total - paid > 1.0) return new DirectOrder(o.path("_id").asText(), total, paid);
            }
        }
        throw new SkipException("No handed_directly order with spare balance found to exercise record-payment");
    }

    /** The order total lives under totalAmount on this backend (total/grandTotal are fallbacks). */
    private double orderTotal(JsonNode o) {
        for (String key : new String[]{"totalAmount", "total", "grandTotal"}) {
            JsonNode n = o.path(key);
            if (n.isNumber()) return n.asDouble();
        }
        return 0;
    }

    private double amountPaidOf(String token, String orderId) {
        APIResponse resp = getPage().request().get(apiBase() + "/orders?limit=200",
                RequestOptions.create().setHeader("Authorization", "Bearer " + token));
        for (JsonNode o : tree(resp.text()).path("data").path("orders")) {
            if (orderId.equals(o.path("_id").asText())) return o.path("amountPaid").asDouble(0);
        }
        return Double.NaN;
    }

    private APIResponse recordPayment(String token, String orderId, Object amount, String method) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amount", amount);
        payload.put("method", method);
        return getPage().request().put(apiBase() + "/orders/" + orderId + "/record-payment",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + token)
                        .setHeader("Content-Type", "application/json")
                        .setData(payload));
    }

    // ─────────────────────────────────────────────────────────
    // AUTH / ROLE (guaranteed non-mutating)
    // ─────────────────────────────────────────────────────────

    @Test(description = "TC-DPAY001: record-payment requires authentication")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Endpoint is protected")
    @Description("Calls the record-payment endpoint with no token and verifies it returns 401 — "
            + "installments cannot be recorded anonymously.")
    public void recordPaymentRequiresAuth() {
        DirectOrder order = findHandedDirectlyOrder(adminToken());
        APIResponse resp = getPage().request().put(apiBase() + "/orders/" + order.id() + "/record-payment",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setData(Map.of("amount", 100, "method", "cash")));
        Assert.assertEquals(resp.status(), 401, "Anonymous record-payment must be 401, got " + resp.status());
    }

    @Test(description = "TC-DPAY002: A customer cannot record a payment")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Role gate")
    @Description("Uses a customer token to call the admin record-payment endpoint and verifies it is "
            + "rejected with 403 — recording direct payments is not a customer capability.")
    public void customerCannotRecordPayment() {
        DirectOrder order = findHandedDirectlyOrder(adminToken());
        APIResponse resp = recordPayment(customerToken(), order.id(), 100, "cash");
        Assert.assertEquals(resp.status(), 403, "Customer record-payment must be 403, got " + resp.status());
    }

    // ─────────────────────────────────────────────────────────
    // AMOUNT VALIDATION (invalid → rejected; amountPaid re-checked)
    // ─────────────────────────────────────────────────────────

    @Test(description = "TC-DPAY003: An amount below Rs 1 (not the exact remaining balance) is rejected")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Minimum amount")
    @Description("Records 0.01 (below the Rs 1 floor and not equal to the remaining balance) on a "
            + "handed_directly order, verifies it is rejected (4xx), and confirms amountPaid did not change.")
    public void belowMinimumAmountRejected() {
        String token = adminToken();
        DirectOrder order = findHandedDirectlyOrder(token);
        double before = amountPaidOf(token, order.id());

        APIResponse resp = recordPayment(token, order.id(), 0.01, "cash");
        Assert.assertTrue(resp.status() >= 400 && resp.status() < 500,
                "An amount below Rs 1 must be rejected (4xx), got " + resp.status());
        Assert.assertEquals(amountPaidOf(token, order.id()), before, 0.0001,
                "A rejected sub-rupee payment must not change amountPaid");
    }

    @Test(description = "TC-DPAY004: An amount that would push amountPaid past the order total is rejected")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Over-total")
    @Description("Records an amount far larger than the order total on a handed_directly order, verifies "
            + "it is rejected (4xx) so amountPaid can never exceed total, and confirms amountPaid did not change.")
    public void overTotalAmountRejected() {
        String token = adminToken();
        DirectOrder order = findHandedDirectlyOrder(token);
        double before = amountPaidOf(token, order.id());

        APIResponse resp = recordPayment(token, order.id(), order.total() + 1000, "cash");
        Assert.assertTrue(resp.status() >= 400 && resp.status() < 500,
                "An over-total amount must be rejected (4xx), got " + resp.status());
        Assert.assertEquals(amountPaidOf(token, order.id()), before, 0.0001,
                "A rejected over-total payment must not change amountPaid");
    }
}
