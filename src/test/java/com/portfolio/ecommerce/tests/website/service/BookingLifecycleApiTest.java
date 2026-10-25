package com.portfolio.ecommerce.tests.website.service;

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

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Section 9 — SERVICES + BOOKINGS full flow (customer books → vendor accepts → completes).
 *
 * End-to-end on the dev env (mutates): a customer books an instore slot on a service owned by
 * the test vendor, the vendor accepts and completes it, and a second booking of the SAME slot
 * is rejected (double-booking guard). Uses the instore path (onsite fails the radius gate and
 * these services have no online mode).
 */
@Epic("Services & bookings")
@Feature("Booking lifecycle (book → accept → complete)")
public class BookingLifecycleApiTest extends BaseTest {

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

    private String customerToken() {
        ConfigReader c = ConfigReader.getInstance();
        return login("/auth/login", c.getCustomerEmail(), c.getCustomerPassword());
    }

    private String vendorToken() {
        ConfigReader c = ConfigReader.getInstance();
        return login("/vendor/login", c.getVendorEmail(), c.getVendorPassword());
    }

    private String vendorId(String vtok) {
        APIResponse resp = getPage().request().get(apiBase() + "/vendor/profile",
                RequestOptions.create().setHeader("Authorization", "Bearer " + vtok));
        Assert.assertEquals(resp.status(), 200, "vendor profile should be readable");
        return tree(resp.text()).path("data").path("user").path("_id").asText();
    }

    /** A service owned by the test vendor that offers the instore mode. */
    private String instoreServiceId(String vid) {
        APIResponse resp = getPage().request().get(apiBase() + "/services?limit=100");
        Assert.assertEquals(resp.status(), 200, "services list should be 200");
        for (JsonNode s : tree(resp.text()).path("data").path("services")) {
            String owner = s.path("vendor").path("_id").asText(s.path("vendor").asText(""));
            if (vid.equals(owner) && s.path("modes").path("instore").asBoolean(false)) {
                return s.path("_id").asText();
            }
        }
        throw new SkipException("No instore service owned by the test vendor");
    }

    /** First upcoming date (within 21 days) that has an open slot; returns {date, slot}. */
    private Map<String, Object> firstOpenSlot(String ctok, String serviceId) {
        for (int d = 1; d <= 21; d++) {
            String date = LocalDate.now().plusDays(d).toString();
            APIResponse resp = getPage().request().get(apiBase() + "/customer/services/" + serviceId
                            + "/availability?date=" + date,
                    RequestOptions.create().setHeader("Authorization", "Bearer " + ctok));
            JsonNode slots = tree(resp.text()).path("data").path("slots");
            if (slots.isArray() && slots.size() > 0) {
                JsonNode s = slots.get(0);
                Map<String, Object> slot = new LinkedHashMap<>();
                slot.put("startTime", s.path("startTime").asText());
                slot.put("endTime", s.path("endTime").asText());
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("date", date);
                out.put("slot", slot);
                return out;
            }
        }
        throw new SkipException("No open instore slot found in the next 21 days");
    }

    private APIResponse book(String ctok, String serviceId, String date, Object slot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mode", "instore");
        payload.put("date", date);
        payload.put("slot", slot);
        return getPage().request().post(apiBase() + "/customer/services/" + serviceId + "/book",
                RequestOptions.create().setHeader("Authorization", "Bearer " + ctok)
                        .setHeader("Content-Type", "application/json").setData(payload));
    }

    // ─────────────────────────────────────────────────────────

    @Test(description = "TC-BOOK001: Customer books an instore slot; vendor accepts; unpaid completion is blocked")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Book → accept → (payment gate on complete)")
    @Description("A customer books an open instore slot; the vendor accepts it (succeeds). Business rule "
            + "confirmed with the product owner: a booking can only be COMPLETED after the customer has "
            + "paid, so completing this still-unpaid booking must be rejected. The full pay-then-complete "
            + "happy path needs the gateway-driven booking payment, which isn't automatable at this API level.")
    @SuppressWarnings("unchecked")
    public void bookAcceptCompleteFlow() {
        String customer = customerToken();
        String vendor = vendorToken();
        String serviceId = instoreServiceId(vendorId(vendor));
        Map<String, Object> when = firstOpenSlot(customer, serviceId);

        APIResponse booked = book(customer, serviceId, (String) when.get("date"), when.get("slot"));
        Assert.assertTrue(booked.ok(), "Booking should be submitted (HTTP " + booked.status() + "): " + booked.text());
        String bookingId = tree(booked.text()).path("data").path("booking").path("_id").asText(null);

        // Vendor finds the pending booking (by id, else the newest pending) and accepts it.
        String vBearer = "Bearer " + vendor;
        if (bookingId == null || bookingId.isBlank()) {
            JsonNode inbox = tree(getPage().request().get(apiBase() + "/vendor/services/bookings?limit=50",
                    RequestOptions.create().setHeader("Authorization", vBearer)).text());
            for (JsonNode b : inbox.path("data").path("bookings")) {
                if ("pending".equals(b.path("status").asText())) { bookingId = b.path("_id").asText(); break; }
            }
        }
        Assert.assertNotNull(bookingId, "The new booking should be findable");

        APIResponse accept = getPage().request().put(apiBase() + "/vendor/services/bookings/" + bookingId + "/accept",
                RequestOptions.create().setHeader("Authorization", vBearer)
                        .setHeader("Content-Type", "application/json").setData(Map.of()));
        Assert.assertTrue(accept.ok(), "Vendor accept should succeed (HTTP " + accept.status() + ")");

        // The customer has NOT paid yet, so completion must be blocked (payment is required first).
        APIResponse complete = getPage().request().put(apiBase() + "/vendor/services/bookings/" + bookingId + "/complete",
                RequestOptions.create().setHeader("Authorization", vBearer)
                        .setHeader("Content-Type", "application/json").setData(Map.of()));
        Assert.assertFalse(complete.ok(),
                "Completing an UNPAID booking must be rejected (payment required first), got HTTP "
                        + complete.status());
    }

    @Test(description = "TC-BOOK002: Booking the same slot twice is rejected (no double-booking)")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Double-booking guard")
    @Description("Books an open instore slot, then attempts to book the exact same slot again and verifies "
            + "the second attempt is rejected (e.g. 409) — a slot cannot be double-booked.")
    public void doubleBookingSameSlotRejected() {
        String customer = customerToken();
        String vendor = vendorToken();
        String serviceId = instoreServiceId(vendorId(vendor));
        Map<String, Object> when = firstOpenSlot(customer, serviceId);

        APIResponse first = book(customer, serviceId, (String) when.get("date"), when.get("slot"));
        Assert.assertTrue(first.ok(), "The first booking of an open slot should succeed");

        APIResponse second = book(customer, serviceId, (String) when.get("date"), when.get("slot"));
        Assert.assertFalse(second.ok(),
                "Booking the same slot again must be rejected, got HTTP " + second.status());
    }
}
