package com.portfolio.ecommerce.tests.website.admin;

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
 * Section 16 — Admin configuration modules (read-only slices).
 *
 * Asserts config-module read contracts and two documented invariants:
 *   - COUPONS were fully removed: there is no coupon endpoint, and Order.discount is dormant at 0.
 *   - The homeSections layout Setting exists (it drives the storefront home order/visibility).
 * Plus basic readability/auth of the config modules (business-types, banners, legal, settings).
 */
@Epic("Admin configuration")
@Feature("Config modules, coupons removed, homeSections")
public class ConfigModulesApiTest extends BaseTest {

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

    private String adminToken() {
        ConfigReader c = ConfigReader.getInstance();
        APIResponse resp = getPage().request().post(apiBase() + "/auth/login",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setData(Map.of("email", c.getAdminEmail(), "password", c.getAdminPassword())));
        Assert.assertTrue(resp.ok(), "Admin login should succeed");
        String token = tree(resp.text()).path("data").path("token").asText(null);
        if (token == null) token = tree(resp.text()).path("token").asText(null);
        Assert.assertNotNull(token, "Admin login must return a token");
        return token;
    }

    private APIResponse get(String endpoint, String token) {
        RequestOptions opts = RequestOptions.create();
        if (token != null) opts.setHeader("Authorization", "Bearer " + token);
        return getPage().request().get(apiBase() + "/" + endpoint, opts);
    }

    // ─────────────────────────────────────────────────────────

    @Test(description = "TC-CFG001: Coupons were fully removed — no coupon endpoint exists")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Coupons removed")
    @Description("With an admin token (unknown routes 404 rather than 401), GETs /coupons and verifies it "
            + "returns 404 just like a nonsense path — the coupon feature has no surviving endpoint.")
    public void couponEndpointIsRemoved() {
        String token = adminToken();
        // Sanity: an admin token surfaces 404 (not 401) for unknown routes.
        Assert.assertEquals(get("definitely-not-a-real-endpoint-xyz", token).status(), 404,
                "Precondition: unknown routes must 404 for an admin token");
        Assert.assertEquals(get("coupons", token).status(), 404,
                "There must be no /coupons endpoint — the coupon feature was removed");
    }

    @Test(description = "TC-CFG002: Order.discount is dormant at 0")
    @Severity(SeverityLevel.NORMAL)
    @Story("Coupons removed")
    @Description("Reads recent orders and verifies every order's discount is 0 — the dormant coupon "
            + "discount field is never populated.")
    public void orderDiscountIsDormantZero() {
        String token = adminToken();
        JsonNode orders = tree(get("orders?limit=50", token).text()).path("data").path("orders");
        Assert.assertTrue(orders.size() > 0, "There should be orders to inspect");
        for (JsonNode o : orders) {
            Assert.assertEquals(o.path("discount").asDouble(0), 0.0, 0.0001,
                    "Order.discount must be dormant at 0 (order " + o.path("orderNumber").asText() + ")");
        }
    }

    @Test(description = "TC-CFG003: The homeSections layout Setting exists")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Home layout setting")
    @Description("Verifies /settings is 401 anonymously and, for an admin, returns a settings array that "
            + "includes the homeSections key — the Setting that drives storefront home order/visibility.")
    public void homeSectionsSettingPresent() {
        Assert.assertEquals(get("settings", null).status(), 401, "Anonymous settings read must be 401");

        JsonNode settings = tree(get("settings", adminToken()).text()).path("data").path("settings");
        Assert.assertTrue(settings.isArray() && settings.size() > 0, "settings should be a non-empty array");

        boolean hasHomeSections = false;
        for (JsonNode s : settings) {
            if ("homeSections".equals(s.path("key").asText())) { hasHomeSections = true; break; }
        }
        Assert.assertTrue(hasHomeSections, "The homeSections layout Setting must exist");
    }

    @Test(description = "TC-CFG004: Config modules are readable, and admin-only ones require auth")
    @Severity(SeverityLevel.NORMAL)
    @Story("Config module access")
    @Description("Verifies public config modules (business-types, banners) return 200, while an admin-only "
            + "module (legal) is 401 anonymously and 200 for an admin.")
    public void configModulesReadable() {
        Assert.assertEquals(get("business-types", null).status(), 200, "business-types should be readable");
        Assert.assertEquals(get("banners", null).status(), 200, "banners should be readable");

        Assert.assertEquals(get("legal", null).status(), 401, "legal admin list must require auth");
        Assert.assertEquals(get("legal", adminToken()).status(), 200, "an admin should read the legal module");
    }
}
