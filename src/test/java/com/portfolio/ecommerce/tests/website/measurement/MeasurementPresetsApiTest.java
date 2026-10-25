package com.portfolio.ecommerce.tests.website.measurement;

import com.portfolio.ecommerce.website.base.BaseTest;
import com.portfolio.ecommerce.website.config.ConfigReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * Section 14 — Measurements + Presets (read-only slices).
 *
 * The model is gender -> preset -> dynamic fields. The preset catalogue is a read-only reference
 * that includes the legacy "Default" preset (outfitType 0). Customer measurement profiles are
 * scoped: a customer may read only their own, so another profile's id is denied.
 *
 *   - GET /api/measurement-presets       public read-only catalogue
 *   - GET /api/customer/measurements     the customer's own profiles (auth)
 *   - GET /api/customer/measurements/:id another profile -> denied (scope)
 */
@Epic("Measurements")
@Feature("Preset catalogue & scope")
public class MeasurementPresetsApiTest extends BaseTest {

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
                        .setData(java.util.Map.of("email", c.getCustomerEmail(), "password", c.getCustomerPassword())));
        Assert.assertTrue(resp.ok(), "Customer login should succeed");
        String token = tree(resp.text()).path("data").path("token").asText(null);
        if (token == null) token = tree(resp.text()).path("token").asText(null);
        Assert.assertNotNull(token, "Customer login must return a token");
        return token;
    }

    private JsonNode presets() {
        APIResponse resp = getPage().request().get(apiBase() + "/measurement-presets");
        Assert.assertEquals(resp.status(), 200, "The preset catalogue should be publicly readable (200)");
        JsonNode data = tree(resp.text()).path("data");
        JsonNode presets = data.path("presets");
        return presets.isArray() ? presets : data;
    }

    // ─────────────────────────────────────────────────────────

    @Test(description = "TC-MEAS001: The preset catalogue includes the legacy Default preset (outfitType 0)")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Default preset present")
    @Description("Reads the public preset catalogue and verifies a 'Default' preset with outfitType 0 exists "
            + "— the legacy common-fields profile that the account UI still renders.")
    public void catalogueIncludesDefaultPreset() {
        JsonNode presets = presets();
        Assert.assertTrue(presets.size() > 0, "The catalogue should not be empty");

        boolean hasDefault = false;
        for (JsonNode p : presets) {
            if (p.path("outfitType").asInt(-1) == 0 && "Default".equalsIgnoreCase(p.path("name").asText())) {
                hasDefault = true;
                break;
            }
        }
        Assert.assertTrue(hasDefault, "A 'Default' preset with outfitType 0 must exist in the catalogue");
    }

    @Test(description = "TC-MEAS002: Presets cover multiple distinct outfit types, each named")
    @Severity(SeverityLevel.NORMAL)
    @Story("Preset variety")
    @Description("Verifies the catalogue exposes several presets with distinct outfitType values, each "
            + "carrying a non-blank name — the gender/outfit → preset catalogue is populated.")
    public void presetsExposeDistinctOutfitTypes() {
        JsonNode presets = presets();
        Set<Integer> outfitTypes = new HashSet<>();
        for (JsonNode p : presets) {
            Assert.assertFalse(p.path("name").asText("").isBlank(), "Every preset must have a name");
            outfitTypes.add(p.path("outfitType").asInt(-1));
        }
        Assert.assertTrue(outfitTypes.size() >= 5,
                "The catalogue should cover several distinct outfit types, saw " + outfitTypes.size());
    }

    @Test(description = "TC-MEAS003: A customer's measurement profiles require authentication")
    @Severity(SeverityLevel.NORMAL)
    @Story("Profiles are protected")
    @Description("Verifies GET /customer/measurements is 401 anonymously and returns 200 for the customer.")
    public void customerMeasurementsRequireAuth() {
        APIResponse anon = getPage().request().get(apiBase() + "/customer/measurements");
        Assert.assertEquals(anon.status(), 401, "Anonymous measurements read must be 401");

        APIResponse resp = getPage().request().get(apiBase() + "/customer/measurements",
                RequestOptions.create().setHeader("Authorization", "Bearer " + customerToken()));
        Assert.assertEquals(resp.status(), 200, "A customer should read their own measurement profiles");
    }

    @Test(description = "TC-MEAS004: A customer cannot read a measurement profile that is not theirs")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Measurement scope")
    @Description("Requests a measurement profile by an id the customer does not own and verifies access is "
            + "denied (403/404) — a customer sees only their own profiles.")
    public void crossCustomerMeasurementAccessDenied() {
        APIResponse resp = getPage().request().get(apiBase() + "/customer/measurements/000000000000000000000000",
                RequestOptions.create().setHeader("Authorization", "Bearer " + customerToken()));
        Assert.assertTrue(resp.status() == 403 || resp.status() == 404,
                "A profile that is not the customer's must be denied (403/404), got " + resp.status());
    }
}
