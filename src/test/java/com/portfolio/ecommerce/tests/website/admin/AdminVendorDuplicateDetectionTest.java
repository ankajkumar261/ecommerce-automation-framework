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
 * Section 2 — ADMIN-DRIVEN VENDOR CREATION: duplicate detection.
 *
 * The doc's assertion for the admin-driven path is that duplicate detection surfaces
 * conflicts BEFORE a vendor is created:
 *   POST /api/vendor/admin/check-duplicates   -> { findings: [ {field, value, owner} ] }
 *   GET  /api/vendor/admin/conflicts          -> { conflicts: {...} }
 *
 * These are read-only checks, so this suite asserts the detection contract without
 * mutating the shared dev backend (no vendor is actually created).
 *
 * Robustness: an existing PAN / account number is discovered at runtime from
 * GET /api/vendor/admin/all, so the tests do not hardcode seed data.
 */
@Epic("Vendor approval")
@Feature("Admin-driven vendor creation — duplicate detection")
public class AdminVendorDuplicateDetectionTest extends BaseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Clearly-synthetic identifiers that should never match a real vendor.
    private static final String FAKE_PAN = "ZZZZZ9999Z";
    private static final String FAKE_GST = "ZZ11ZZ0000Z1Z9";
    private static final String FAKE_PHONE = "6000000009";

    @Override
    protected String getUrl() {
        return ConfigReader.getInstance().getAdminUrl();
    }

    private String apiBase() {
        String base = ConfigReader.getInstance().getApiUrl();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    // ── API helpers ───────────────────────────────────────────
    private String adminToken() {
        ConfigReader config = ConfigReader.getInstance();
        APIResponse resp = getPage().request().post(apiBase() + "/auth/login",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setData(Map.of("email", config.getAdminEmail(),
                                "password", config.getAdminPassword())));
        Assert.assertTrue(resp.ok(), "Admin login should succeed (HTTP " + resp.status() + ")");
        String token = readTree(resp.text()).path("data").path("token").asText(null);
        Assert.assertNotNull(token, "Admin login must return a token");
        return token;
    }

    private JsonNode checkDuplicates(String token, Map<String, Object> payload) {
        APIResponse resp = getPage().request().post(apiBase() + "/vendor/admin/check-duplicates",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + token)
                        .setHeader("Content-Type", "application/json")
                        .setData(payload));
        Assert.assertEquals(resp.status(), 200,
                "check-duplicates should return 200 for an authorised admin, got " + resp.status());
        return readTree(resp.text());
    }

    private JsonNode readTree(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            throw new AssertionError("Response was not valid JSON: " + body, e);
        }
    }

    /** Finds a real existing value for a given identifier field from the vendor list. */
    private String findExistingValue(String token, String... jsonPath) {
        APIResponse resp = getPage().request().get(apiBase() + "/vendor/admin/all?limit=200",
                RequestOptions.create().setHeader("Authorization", "Bearer " + token));
        Assert.assertEquals(resp.status(), 200, "Should be able to list vendors");
        JsonNode vendors = readTree(resp.text()).path("vendors");
        for (JsonNode v : vendors) {
            JsonNode node = v;
            for (String key : jsonPath) node = node.path(key);
            String value = node.asText("");
            if (!value.isBlank()) return value;
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────
    // TESTS
    // ─────────────────────────────────────────────────────────

    @Test(description = "TC-DUP001: An existing PAN is surfaced as a duplicate finding")
    @Description("Discovers a real PAN already on record, posts it to check-duplicates, and verifies the "
            + "response flags a panNumber finding that names the conflicting owner.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Existing identifier flagged")
    public void existingPanIsFlaggedAsDuplicate() {
        String token = adminToken();
        String pan = findExistingValue(token, "businessInfo", "panNumber");
        Assert.assertNotNull(pan, "Precondition: at least one vendor should have a PAN on record");

        JsonNode findings = checkDuplicates(token, Map.of("panNumber", pan)).path("findings");
        Assert.assertTrue(findings.isArray() && findings.size() >= 1,
                "An existing PAN (" + pan + ") should produce at least one finding");

        boolean matched = false;
        for (JsonNode f : findings) {
            if ("panNumber".equals(f.path("field").asText())
                    && pan.equalsIgnoreCase(f.path("value").asText())) {
                Assert.assertFalse(f.path("owner").isMissingNode(),
                        "A duplicate finding must name the conflicting owner");
                matched = true;
            }
        }
        Assert.assertTrue(matched, "Findings should include the panNumber conflict for " + pan);
    }

    @Test(description = "TC-DUP002: An existing bank account number is surfaced as a duplicate finding")
    @Description("Discovers a real bank account number on record and verifies check-duplicates reports it "
            + "as an accountNumber conflict.")
    @Severity(SeverityLevel.NORMAL)
    @Story("Existing identifier flagged")
    public void existingAccountNumberIsFlagged() {
        String token = adminToken();
        String account = findExistingValue(token, "bankDetails", "accountNumber");
        Assert.assertNotNull(account, "Precondition: at least one vendor should have a bank account on record");

        JsonNode findings = checkDuplicates(token, Map.of("accountNumber", account)).path("findings");
        boolean matched = false;
        for (JsonNode f : findings) {
            if ("accountNumber".equals(f.path("field").asText())
                    && account.equals(f.path("value").asText())) {
                matched = true;
            }
        }
        Assert.assertTrue(matched,
                "An existing account number (" + account + ") should be flagged as a duplicate");
    }

    @Test(description = "TC-DUP003: Unknown identifiers produce no findings")
    @Description("Posts clearly-synthetic PAN, GST and phone values to check-duplicates and verifies an "
            + "empty findings array — a genuinely new vendor raises no false conflicts.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Clean identifiers pass")
    public void unknownIdentifiersReturnNoFindings() {
        String token = adminToken();
        JsonNode findings = checkDuplicates(token, Map.of(
                "panNumber", FAKE_PAN,
                "gstNumber", FAKE_GST,
                "phone", FAKE_PHONE)).path("findings");
        Assert.assertTrue(findings.isArray() && findings.isEmpty(),
                "Unknown identifiers must return an empty findings array, got: " + findings);
    }

    @Test(description = "TC-DUP004: Multi-field check flags only the conflicting field")
    @Description("Posts a real PAN alongside fake GST and phone values and verifies only the panNumber is "
            + "reported — the check pinpoints the exact conflicting field, not the whole payload.")
    @Severity(SeverityLevel.NORMAL)
    @Story("Precise conflict reporting")
    public void multiFieldFlagsOnlyTheConflictingField() {
        String token = adminToken();
        String pan = findExistingValue(token, "businessInfo", "panNumber");
        Assert.assertNotNull(pan, "Precondition: a vendor with a PAN must exist");

        JsonNode findings = checkDuplicates(token, Map.of(
                "panNumber", pan,          // real -> should conflict
                "gstNumber", FAKE_GST,     // fake -> should NOT conflict
                "phone", FAKE_PHONE        // fake -> should NOT conflict
        )).path("findings");

        Assert.assertTrue(findings.size() >= 1, "The real PAN should still be flagged");
        for (JsonNode f : findings) {
            Assert.assertEquals(f.path("field").asText(), "panNumber",
                    "Only the conflicting field (panNumber) should be reported, saw: " + f.path("field").asText());
        }
    }

    @Test(description = "TC-DUP005: check-duplicates requires authentication")
    @Description("Calls check-duplicates with no token and verifies it is rejected with 401 — the "
            + "detection endpoint is not exposed anonymously.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Endpoint is protected")
    public void checkDuplicatesRequiresAuth() {
        APIResponse resp = getPage().request().post(apiBase() + "/vendor/admin/check-duplicates",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setData(Map.of("panNumber", FAKE_PAN)));
        Assert.assertEquals(resp.status(), 401,
                "Unauthenticated duplicate-check must be rejected (401), got " + resp.status());
    }

    @Test(description = "TC-DUP006: The conflicts endpoint returns a conflicts object for an admin and rejects anon")
    @Description("Verifies GET /vendor/admin/conflicts is 401 anonymously and, with an admin token, returns "
            + "200 with a conflicts object listing existing cross-vendor duplicates.")
    @Severity(SeverityLevel.NORMAL)
    @Story("Conflicts overview")
    public void conflictsEndpointRequiresAuthAndReturnsObject() {
        // Anonymous -> 401
        APIResponse anon = getPage().request().get(apiBase() + "/vendor/admin/conflicts");
        Assert.assertEquals(anon.status(), 401,
                "Unauthenticated conflicts read must be rejected (401), got " + anon.status());

        // Admin -> 200 with a "conflicts" object
        String token = adminToken();
        APIResponse resp = getPage().request().get(apiBase() + "/vendor/admin/conflicts",
                RequestOptions.create().setHeader("Authorization", "Bearer " + token));
        Assert.assertEquals(resp.status(), 200, "Admin should read conflicts (200)");
        Assert.assertFalse(readTree(resp.text()).path("conflicts").isMissingNode(),
                "conflicts response must contain a 'conflicts' object");
    }
}
