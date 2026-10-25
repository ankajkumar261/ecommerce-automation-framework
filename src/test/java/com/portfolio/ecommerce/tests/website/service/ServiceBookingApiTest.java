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
import java.util.Map;

/**
 * Section 9 — SERVICES + BOOKINGS (read-only slices: onsite radius gate + availability).
 *
 * Covers the parts of the booking flow that can be asserted without creating a booking:
 *   - GET /api/customer/services/:id/onsite-check?lat&lng — the radius gate that decides whether
 *     an onsite booking is allowed. Out-of-radius must be blocked, and the allowed flag must be
 *     consistent with distanceKm vs radiusKm.
 *   - GET /api/customer/services/:id/availability?date — the instore slot buckets.
 * Both endpoints require an authenticated customer.
 */
@Epic("Services & bookings")
@Feature("Onsite radius gate & availability")
public class ServiceBookingApiTest extends BaseTest {

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

    /** A service that supports onsite mode (so onsite-check is meaningful). */
    private String onsiteServiceId() {
        APIResponse resp = getPage().request().get(apiBase() + "/services?limit=50");
        Assert.assertEquals(resp.status(), 200, "Services list should return 200");
        for (JsonNode s : tree(resp.text()).path("data").path("services")) {
            if (s.path("modes").path("onsite").asBoolean(false)) return s.path("_id").asText();
        }
        throw new SkipException("No service with onsite mode enabled found");
    }

    private JsonNode onsiteCheck(String token, String serviceId, double lat, double lng) {
        APIResponse resp = getPage().request().get(apiBase() + "/customer/services/" + serviceId
                        + "/onsite-check?lat=" + lat + "&lng=" + lng,
                RequestOptions.create().setHeader("Authorization", "Bearer " + token));
        Assert.assertEquals(resp.status(), 200, "onsite-check should return 200 for an authed customer");
        return tree(resp.text()).path("data");
    }

    // ─────────────────────────────────────────────────────────
    // ONSITE RADIUS GATE
    // ─────────────────────────────────────────────────────────

    @Test(description = "TC-SVC001: An out-of-radius onsite address is blocked")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Radius gate")
    @Description("Runs onsite-check from several globally-separated points and verifies at least one is "
            + "blocked (allowed=false) with a reason and distanceKm greater than radiusKm — a small radius "
            + "cannot cover multiple continents, so wherever the storefront sits, some point is out of range.")
    public void onsiteCheckBlocksOutsideRadius() {
        String token = customerToken();
        String serviceId = onsiteServiceId();
        // Widely-separated points; the storefront's radius can include at most one of them.
        double[][] points = {
                {12.9716, 77.5946},   // Bengaluru
                {51.5074, -0.1278},   // London
                {-33.8688, 151.2093}, // Sydney
                {40.7128, -74.0060},  // New York
        };

        boolean sawBlocked = false;
        for (double[] p : points) {
            JsonNode data = onsiteCheck(token, serviceId, p[0], p[1]);
            if (!data.path("allowed").asBoolean(true)) {
                sawBlocked = true;
                Assert.assertTrue(data.path("distanceKm").asDouble() > data.path("radiusKm").asDouble(),
                        "A blocked point must have distanceKm > radiusKm");
                Assert.assertFalse(data.path("reason").asText("").isBlank(),
                        "A blocked onsite check should explain why");
            }
        }
        Assert.assertTrue(sawBlocked,
                "At least one far-away point must be blocked by the onsite radius gate");
    }

    @Test(description = "TC-SVC002: The onsite allowed flag is consistent with distance vs radius")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Radius gate")
    @Description("Runs onsite-check and verifies the allowed flag exactly matches whether distanceKm is "
            + "within radiusKm — the gate's decision is internally consistent.")
    public void onsiteRadiusDecisionIsConsistent() {
        String token = customerToken();
        String serviceId = onsiteServiceId();
        JsonNode data = onsiteCheck(token, serviceId, 12.9716, 77.5946); // Bengaluru

        boolean allowed = data.path("allowed").asBoolean();
        double distance = data.path("distanceKm").asDouble();
        double radius = data.path("radiusKm").asDouble();
        Assert.assertTrue(radius > 0, "radiusKm should be a positive configured value");
        Assert.assertEquals(allowed, distance <= radius,
                "allowed (" + allowed + ") must match distanceKm<=radiusKm (" + distance + " vs " + radius + ")");
    }

    @Test(description = "TC-SVC003: onsite-check requires authentication")
    @Severity(SeverityLevel.NORMAL)
    @Story("Endpoint is protected")
    @Description("Calls onsite-check with no token and verifies it returns 401.")
    public void onsiteCheckRequiresAuth() {
        String serviceId = onsiteServiceId();
        APIResponse resp = getPage().request().get(apiBase() + "/customer/services/" + serviceId
                + "/onsite-check?lat=12.97&lng=77.59");
        Assert.assertEquals(resp.status(), 401, "Anonymous onsite-check must be 401");
    }

    // ─────────────────────────────────────────────────────────
    // AVAILABILITY
    // ─────────────────────────────────────────────────────────

    @Test(description = "TC-SVC004: Availability returns structured slot data for a requested date")
    @Severity(SeverityLevel.NORMAL)
    @Story("Instore availability")
    @Description("Requests availability for a near-future date and verifies the response echoes that date "
            + "and returns a slots array plus a positive slotMinutes granularity.")
    public void availabilityReturnsStructuredSlots() {
        String token = customerToken();
        String serviceId = onsiteServiceId();
        String date = LocalDate.now().plusDays(7).toString();

        APIResponse resp = getPage().request().get(apiBase() + "/customer/services/" + serviceId
                        + "/availability?date=" + date,
                RequestOptions.create().setHeader("Authorization", "Bearer " + token));
        Assert.assertEquals(resp.status(), 200, "availability should return 200");
        JsonNode data = tree(resp.text()).path("data");

        Assert.assertTrue(data.path("slots").isArray(), "availability must return a slots array");
        Assert.assertTrue(data.path("slotMinutes").asInt(0) > 0, "slotMinutes should be a positive granularity");
        Assert.assertEquals(data.path("date").asText(), date, "the response should echo the requested date");
    }

    @Test(description = "TC-SVC005: availability requires authentication")
    @Severity(SeverityLevel.NORMAL)
    @Story("Endpoint is protected")
    @Description("Calls availability with no token and verifies it returns 401.")
    public void availabilityRequiresAuth() {
        String serviceId = onsiteServiceId();
        APIResponse resp = getPage().request().get(apiBase() + "/customer/services/" + serviceId
                + "/availability?date=" + LocalDate.now().plusDays(3));
        Assert.assertEquals(resp.status(), 401, "Anonymous availability must be 401");
    }
}
