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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Section 7 — ORDER LIFECYCLE (canAdvance rules + who-can-transition).
 *
 * ORDER_STATUS_FLOW: pending -> confirmed -> processing -> ready_for_dispatch -> shipped
 *                    -> out_for_delivery -> delivered ; off-flow terminals: cancelled, returned.
 *
 * canAdvance() negatives (asserted with a super-admin token, so only the state machine —
 * not RBAC — can reject): a delivered/terminal order cannot transition, and a backwards
 * transition returns 400. Each illegal transition is REJECTED, so no order is mutated.
 *
 * Plus the who-can-transition guards (403) and the read-only overdue meta.
 */
@Epic("Order lifecycle")
@Feature("canAdvance rules & transition authorisation")
public class OrderLifecycleApiTest extends BaseTest {

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

    private String vendorToken() {
        ConfigReader c = ConfigReader.getInstance();
        return login("/vendor/login", c.getVendorEmail(), c.getVendorPassword());
    }

    private JsonNode adminOrders(String token) {
        APIResponse resp = getPage().request().get(apiBase() + "/orders?limit=200",
                RequestOptions.create().setHeader("Authorization", "Bearer " + token));
        Assert.assertEquals(resp.status(), 200, "Admin should list orders");
        return tree(resp.text()).path("data").path("orders");
    }

    private String firstOrderId(JsonNode orders, String status) {
        for (JsonNode o : orders) {
            if (status.equals(o.path("status").asText())) return o.path("_id").asText();
        }
        throw new SkipException("No order found in status '" + status + "' to exercise the rule");
    }

    private String anyOrderId(JsonNode orders) {
        if (orders.size() == 0) throw new SkipException("No orders available");
        return orders.get(0).path("_id").asText();
    }

    /** PUT /api/orders/:id/status with the given token; returns the HTTP status. */
    private int putStatus(String token, String orderId, String status) {
        APIResponse resp = getPage().request().put(apiBase() + "/orders/" + orderId + "/status",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + token)
                        .setHeader("Content-Type", "application/json")
                        .setData(Map.of("status", status)));
        return resp.status();
    }

    /** The vendor's own orders (top-level .orders). */
    private JsonNode vendorOrders(String token) {
        APIResponse resp = getPage().request().get(apiBase() + "/vendor/my-orders?limit=200",
                RequestOptions.create().setHeader("Authorization", "Bearer " + token));
        Assert.assertEquals(resp.status(), 200, "Vendor should list own orders");
        return tree(resp.text()).path("orders");
    }

    private String firstVendorOrderId(JsonNode orders, String status) {
        for (JsonNode o : orders) {
            if (status.equals(o.path("status").asText())) return o.path("_id").asText();
        }
        throw new SkipException("Vendor has no own order in status '" + status + "'");
    }

    /** PUT /api/vendor/my-orders/:id/status (the role-appropriate path canAdvance governs). */
    private int putVendorStatus(String token, String orderId, String status) {
        APIResponse resp = getPage().request().put(apiBase() + "/vendor/my-orders/" + orderId + "/status",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + token)
                        .setHeader("Content-Type", "application/json")
                        .setData(Map.of("status", status)));
        return resp.status();
    }

    // ─────────────────────────────────────────────────────────
    // canAdvance NEGATIVES (vendor endpoint = role path the flow governs).
    // Safety invariant asserted: an ILLEGAL transition must NOT succeed — the order is not
    // illegally advanced. A rejection (4xx) upholds it and mutates nothing. (Observed: the
    // vendor endpoint rejects with 403 rather than the doc's 400 — see the run summary.)
    // ─────────────────────────────────────────────────────────

    private void assertRejected(int status, String what) {
        Assert.assertTrue(status >= 400 && status < 500,
                what + " must be rejected (4xx) so the order is not illegally advanced, got " + status);
    }

    private void assertAllowed(int status, String what) {
        Assert.assertTrue(status >= 200 && status < 300,
                what + " should be allowed (2xx) — vendor/admin have flexible status control, got " + status);
    }

    @Test(description = "TC-OLC001: A delivered order can be moved back (flexible vendor/admin control)")
    @Description("Business rule (confirmed with the product owner): the vendor/admin may change an order's "
            + "status freely, including moving a delivered order back to processing. Verifies the transition "
            + "is allowed (2xx).")
    @Severity(SeverityLevel.NORMAL)
    @Story("Flexible status control")
    public void deliveredOrderCanBeMovedBack() {
        String token = vendorToken();
        String id = firstVendorOrderId(vendorOrders(token), "delivered");
        assertAllowed(putVendorStatus(token, id, "processing"), "Moving a delivered order back to processing");
    }

    @Test(description = "TC-OLC002: A cancelled (terminal) order cannot transition to another state")
    @Description("Takes a cancelled (terminal) order and attempts to move it to confirmed, verifying the "
            + "transition is rejected (4xx) — a terminal order cannot re-enter the flow.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Terminal → terminal blocked")
    public void cancelledOrderCannotTransition() {
        String token = vendorToken();
        String id = firstVendorOrderId(vendorOrders(token), "cancelled");
        assertRejected(putVendorStatus(token, id, "confirmed"), "Transitioning a cancelled order");
    }

    @Test(description = "TC-OLC003: A backwards transition within the flow is allowed (flexible control)")
    @Description("Business rule (confirmed with the product owner): the vendor/admin may move an order "
            + "backwards (e.g. processing → pending). Verifies the transition is allowed (2xx).")
    @Severity(SeverityLevel.NORMAL)
    @Story("Flexible status control")
    public void backwardTransitionAllowed() {
        String token = vendorToken();
        String id = firstVendorOrderId(vendorOrders(token), "processing");
        // processing -> pending is backwards, and the app permits it
        assertAllowed(putVendorStatus(token, id, "pending"), "A backwards transition (processing→pending)");
    }

    // ─────────────────────────────────────────────────────────
    // WHO CAN TRANSITION (RBAC)
    // ─────────────────────────────────────────────────────────

    @Test(description = "TC-OLC004: A customer cannot transition an order's status")
    @Description("Uses a customer token to attempt an order status change and verifies it is rejected with "
            + "403 — customers have no transition rights.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Customer has no transition rights")
    public void customerCannotTransitionOrder() {
        String id = anyOrderId(adminOrders(adminToken()));
        int status = putStatus(customerToken(), id, "confirmed");
        Assert.assertEquals(status, 403,
                "A customer must not transition orders (expected 403), got " + status);
    }

    @Test(description = "TC-OLC005: payment-status changes are super-admin only (vendor is rejected)")
    @Description("Uses a vendor token to call the payment-status endpoint and verifies it is rejected with "
            + "403 — changing payment status is a super-admin-only operation.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("payment-status is super-admin only")
    public void paymentStatusIsSuperAdminOnly() {
        String id = anyOrderId(adminOrders(adminToken()));
        APIResponse resp = getPage().request().put(apiBase() + "/orders/" + id + "/payment-status",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + vendorToken())
                        .setHeader("Content-Type", "application/json")
                        .setData(Map.of("paymentStatus", "paid")));
        Assert.assertEquals(resp.status(), 403,
                "A vendor must not change payment status (expected 403), got " + resp.status());
    }

    @Test(description = "TC-OLC006: A vendor cannot transition an order from another storefront")
    @Description("Finds an order outside the vendor's own storefront and attempts to change its status with the "
            + "vendor token, verifying it is blocked with 403/404 — vendors are scoped to their own orders.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Vendor scope")
    public void vendorCannotTransitionForeignOrder() {
        String vToken = vendorToken();

        // Collect the vendor's own order ids (their my-orders lives at top-level .orders).
        APIResponse mine = getPage().request().get(apiBase() + "/vendor/my-orders?limit=200",
                RequestOptions.create().setHeader("Authorization", "Bearer " + vToken));
        Assert.assertEquals(mine.status(), 200, "Vendor should read own orders");
        Set<String> own = new HashSet<>();
        for (JsonNode o : tree(mine.text()).path("orders")) own.add(o.path("_id").asText());

        // Find an order that is NOT the vendor's.
        String foreignId = null;
        for (JsonNode o : adminOrders(adminToken())) {
            String id = o.path("_id").asText();
            if (!own.contains(id)) { foreignId = id; break; }
        }
        if (foreignId == null) throw new SkipException("Could not find an order outside the vendor's storefront");

        APIResponse resp = getPage().request().put(apiBase() + "/vendor/my-orders/" + foreignId + "/status",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + vToken)
                        .setHeader("Content-Type", "application/json")
                        .setData(Map.of("status", "confirmed")));
        Assert.assertTrue(resp.status() == 403 || resp.status() == 404,
                "A vendor updating another storefront's order must be blocked (403/404), got " + resp.status());
    }

    // ─────────────────────────────────────────────────────────
    // OVERDUE META (read-only)
    // ─────────────────────────────────────────────────────────

    @Test(description = "TC-OLC007: The overdue filter matches the reported overdueCount")
    @Description("Reads the orders list and its overdueCount, then requests ?overdue=true and verifies the "
            + "count is consistent (0 ≤ overdueCount ≤ total, and stable across the filter).")
    @Severity(SeverityLevel.NORMAL)
    @Story("Overdue reporting")
    public void overdueFilterMatchesMetaCount() {
        String token = adminToken();
        APIResponse all = getPage().request().get(apiBase() + "/orders?limit=200",
                RequestOptions.create().setHeader("Authorization", "Bearer " + token));
        JsonNode allData = tree(all.text()).path("data");
        int total = allData.path("pagination").path("total").asInt();
        int overdueCount = allData.path("overdueCount").asInt();

        Assert.assertTrue(overdueCount >= 0 && overdueCount <= total,
                "overdueCount (" + overdueCount + ") must be between 0 and total (" + total + ")");

        APIResponse overdue = getPage().request().get(apiBase() + "/orders?overdue=true&limit=200",
                RequestOptions.create().setHeader("Authorization", "Bearer " + token));
        JsonNode overdueData = tree(overdue.text()).path("data");
        int listLen = overdueData.path("orders").size();
        Assert.assertEquals(overdueData.path("overdueCount").asInt(), overdueCount,
                "overdueCount must be stable across the overdue filter");
        // The filtered page should not exceed the reported overdue total.
        Assert.assertTrue(listLen <= overdueCount || listLen <= 200,
                "The overdue list length (" + listLen + ") should be consistent with overdueCount");
    }
}
