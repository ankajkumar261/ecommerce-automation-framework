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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Section 10 — RETURNS full lifecycle (customer raises → admin approves).
 *
 * End-to-end across three roles on the dev env (this mutates data):
 *   1. Find an order that is both the customer's AND in the test vendor's storefront, paid and
 *      not yet delivered.
 *   2. Vendor drives it to 'delivered' (so it enters the return window).
 *   3. Customer confirms return-eligibility and raises a return.
 *   4. Admin approves it; the customer then sees the approved return in their list.
 */
@Epic("Returns & refunds")
@Feature("Return lifecycle (raise → approve)")
public class ReturnsLifecycleApiTest extends BaseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> FLOW = List.of(
            "pending", "confirmed", "processing", "ready_for_dispatch",
            "shipped", "out_for_delivery", "delivered");

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

    private String login(String path, String email, String password) {
        APIResponse resp = getPage().request().post(apiBase() + path,
                RequestOptions.create().setHeader("Content-Type", "application/json")
                        .setData(Map.of("email", email, "password", password)));
        Assert.assertTrue(resp.ok(), "Login should succeed for " + email);
        String token = tree(resp.text()).path("data").path("token").asText(null);
        if (token == null) token = tree(resp.text()).path("token").asText(null);
        Assert.assertNotNull(token, "Login must return a token for " + email);
        return token;
    }

    private JsonNode getJson(String endpoint, String token) {
        APIResponse resp = getPage().request().get(apiBase() + endpoint,
                RequestOptions.create().setHeader("Authorization", "Bearer " + token));
        Assert.assertEquals(resp.status(), 200, "GET " + endpoint + " should be 200");
        return tree(resp.text());
    }

    private void putStatus(String vtok, String orderId, String status) {
        getPage().request().put(apiBase() + "/vendor/my-orders/" + orderId + "/status",
                RequestOptions.create().setHeader("Authorization", "Bearer " + vtok)
                        .setHeader("Content-Type", "application/json")
                        .setData(Map.of("status", status)));
    }

    private String vendorOrderStatus(String vtok, String orderId) {
        for (JsonNode o : getJson("/vendor/my-orders?limit=200", vtok).path("orders")) {
            if (orderId.equals(o.path("_id").asText())) return o.path("status").asText();
        }
        return "<not found>";
    }

    @Test(description = "TC-RETL001: A customer raises a return on a delivered order and admin approves it")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Raise → approve")
    @Description("Drives an order that belongs to the customer and the test vendor's storefront to 'delivered', "
            + "then the customer raises a return which the admin approves — verifying the return is created, "
            + "moves to 'approved', and appears in the customer's returns list.")
    public void customerRaisesReturnAdminApproves() {
        ConfigReader c = ConfigReader.getInstance();
        String customer = login("/auth/login", c.getCustomerEmail(), c.getCustomerPassword());
        String vendor = login("/vendor/login", c.getVendorEmail(), c.getVendorPassword());
        String admin = login("/auth/login", c.getAdminEmail(), c.getAdminPassword());

        // Vendor's order ids (the storefront the test vendor can fulfil).
        Set<String> vendorIds = new HashSet<>();
        for (JsonNode o : getJson("/vendor/my-orders?limit=200", vendor).path("orders")) {
            vendorIds.add(o.path("_id").asText());
        }

        // Candidate orders: paid, in-flow, that are BOTH the customer's and in the vendor's storefront.
        List<String> candidates = new java.util.ArrayList<>();
        for (JsonNode o : getJson("/orders?limit=200", customer).path("data").path("orders")) {
            String id = o.path("_id").asText();
            int idx = FLOW.indexOf(o.path("status").asText());
            if (vendorIds.contains(id) && "paid".equals(o.path("paymentStatus").asText())
                    && idx >= 1 && idx < FLOW.size()) {
                candidates.add(id);
            }
        }
        if (candidates.isEmpty()) throw new SkipException("No suitable customer+vendor order");

        // Returnability depends on the product's policy (known only after delivery), so try
        // candidates until one is cleanly returnable. Cap the attempts to limit mutation.
        String orderId = null;
        String orderItemId = null;
        int attempts = 0;
        for (String cand : candidates) {
            if (attempts++ >= 8) break;
            int from = FLOW.indexOf(vendorOrderStatus(vendor, cand));
            for (int i = from + 1; i < FLOW.size(); i++) putStatus(vendor, cand, FLOW.get(i));
            putStatus(vendor, cand, "delivered");
            if (!"delivered".equals(vendorOrderStatus(vendor, cand))) continue;

            JsonNode elig = getJson("/customer/orders/" + cand + "/return-eligibility", customer).path("data");
            if (elig.path("eligible").asBoolean() && elig.path("anyReturnable").asBoolean()
                    && elig.path("existingRequest").isNull()
                    && elig.path("itemEligibility").fieldNames().hasNext()) {
                orderId = cand;
                orderItemId = elig.path("itemEligibility").fieldNames().next();
                break;
            }
        }
        if (orderId == null) {
            throw new SkipException("No cleanly-returnable order found among candidates (product policy)");
        }

        // Customer raises the return.
        APIResponse raise = getPage().request().post(apiBase() + "/customer/orders/" + orderId + "/return",
                RequestOptions.create().setHeader("Authorization", "Bearer " + customer)
                        .setHeader("Content-Type", "application/json")
                        .setData(Map.of(
                                "type", "return",
                                "reasonCategory", "size_issue",
                                "items", List.of(Map.of("orderItemId", orderItemId, "quantity", 1)))));
        Assert.assertTrue(raise.ok(), "Raising a return should succeed (HTTP " + raise.status() + "): " + raise.text());
        String returnId = tree(raise.text()).path("data").path("request").path("_id").asText(null);

        // Admin finds the return for this order and approves it.
        if (returnId == null || returnId.isBlank()) {
            for (JsonNode r : getJson("/admin/returns?limit=100", admin).path("data").path("requests")) {
                if (orderId.equals(r.path("order").asText())) { returnId = r.path("_id").asText(); break; }
            }
        }
        Assert.assertNotNull(returnId, "The new return should be findable");

        APIResponse approve = getPage().request().patch(apiBase() + "/admin/returns/" + returnId + "/approve",
                RequestOptions.create().setHeader("Authorization", "Bearer " + admin)
                        .setHeader("Content-Type", "application/json").setData(Map.of()));
        Assert.assertTrue(approve.ok(), "Admin approve should succeed (HTTP " + approve.status() + ")");
        Assert.assertEquals(tree(approve.text()).path("data").path("request").path("status").asText(),
                "approved", "The return should be 'approved' after admin approval");

        // Customer sees the approved return in their list.
        boolean seen = false;
        for (JsonNode r : getJson("/customer/returns", customer).path("data").path("requests")) {
            if (returnId.equals(r.path("_id").asText())) { seen = true; break; }
        }
        Assert.assertTrue(seen, "The customer should see the raised return in /customer/returns");
    }
}
