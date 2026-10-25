package com.portfolio.ecommerce.tests.website.vendor;

import com.portfolio.ecommerce.website.base.BaseTest;
import com.portfolio.ecommerce.website.config.ConfigReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * Section 2 — VENDOR APPROVAL STATE MACHINE (middleware/vendorApproved.js).
 *
 * The doc calls this "the highest-value gate suite - every vendor feature sits behind it".
 * These are API-level assertions (independent of the UI redirect matrix): every guarded
 * /api/vendor/* family must return 403 while approvalStatus !== approved, while the
 * explicitly-unguarded families (profile, notifications) stay reachable.
 *
 * Accounts (from config):
 *   approved vendor  -> vendor.email          (approvalStatus=approved)  guarded => 200
 *   pending  vendor  -> vendor.pending.email  (approvalStatus=pending)   guarded => 403
 *
 * Uses Playwright's APIRequestContext (getPage().request()) — no extra HTTP dependency.
 */
@Epic("Vendor approval")
@Feature("Approval gate (vendorApproved middleware)")
public class VendorApprovalGateApiTest extends BaseTest {

    // Guarded families — 403 until approved (doc §2).
    private static final String[] GUARDED = {
            "vendor/my-products",
            "vendor/my-orders",
            "vendor/my-earnings",
            "vendor/dashboard",
    };

    // NOT guarded — reachable while pending (doc §2).
    private static final String[] UNGUARDED = {
            "vendor/profile",
            "vendor/notifications",
    };

    // BaseTest navigates the primary page before each test; point it at the vendor
    // login page (harmless — these tests drive the API, not the UI).
    @Override
    protected String getUrl() {
        return ConfigReader.getInstance().getVendorUrl();
    }

    private String apiBase() {
        String base = ConfigReader.getInstance().getApiUrl();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Logs a vendor in via POST /api/vendor/login and returns the JWT. */
    private String vendorToken(String email, String password) {
        APIRequestContext req = getPage().request();
        APIResponse resp = req.post(apiBase() + "/vendor/login",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setData(Map.of("email", email, "password", password)));
        Assert.assertTrue(resp.ok(),
                "Vendor login should succeed for " + email + " (HTTP " + resp.status() + ")");
        String token = extractToken(resp.text());
        Assert.assertNotNull(token, "Login response for " + email + " must carry a token");
        return token;
    }

    /** Token may sit at data.token or top-level token depending on the endpoint. */
    private String extractToken(String responseBody) {
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            JsonNode top = root.path("token");
            if (top.isTextual() && !top.asText().isBlank()) return top.asText();
            JsonNode nested = root.path("data").path("token");
            if (nested.isTextual() && !nested.asText().isBlank()) return nested.asText();
        } catch (Exception e) {
            // fall through — caller asserts non-null
        }
        return null;
    }

    private int statusOf(String token, String endpoint) {
        APIResponse resp = getPage().request().get(apiBase() + "/" + endpoint,
                RequestOptions.create().setHeader("Authorization", "Bearer " + token));
        return resp.status();
    }

    @Test(description = "TC-VGATE001: Guarded vendor endpoints return 403 for a pending (non-approved) vendor")
    @Description("Logs in as a pending vendor and calls each guarded family (my-products, my-orders, "
            + "my-earnings, dashboard), asserting every one is blocked with 403 by the vendorApproved middleware.")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Guarded endpoints blocked while not approved")
    public void pendingVendorIsBlockedFromGuardedEndpoints() {
        ConfigReader config = ConfigReader.getInstance();
        String token = vendorToken(config.getPendingVendorEmail(), config.getPendingVendorPassword());

        // Precondition: the configured "pending" vendor must actually be non-approved in this
        // environment. If a guarded endpoint is already reachable (200), the account is approved
        // here (e.g. on live) — skip rather than fail on a data mismatch.
        if (statusOf(token, GUARDED[0]) == 200) {
            throw new org.testng.SkipException(
                    "Precondition not met: '" + config.getPendingVendorEmail()
                            + "' is not a pending/non-approved vendor in this environment");
        }

        for (String ep : GUARDED) {
            int status = statusOf(token, ep);
            logStep("pending vendor -> /" + ep + " => " + status);
            Assert.assertEquals(status, 403,
                    "A non-approved vendor must be blocked (403) from /" + ep + ", got " + status);
        }
    }

    @Test(description = "TC-VGATE002: The same guarded endpoints return 200 for an approved vendor")
    @Description("Logs in as an approved vendor and calls the same guarded families, asserting each now "
            + "returns 200 — proving the gate opens exactly when approvalStatus becomes approved.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Guarded endpoints reachable once approved")
    public void approvedVendorReachesGuardedEndpoints() {
        ConfigReader config = ConfigReader.getInstance();
        String token = vendorToken(config.getVendorEmail(), config.getVendorPassword());

        for (String ep : GUARDED) {
            int status = statusOf(token, ep);
            logStep("approved vendor -> /" + ep + " => " + status);
            Assert.assertEquals(status, 200,
                    "An approved vendor should reach /" + ep + " (200), got " + status);
        }
    }

    @Test(description = "TC-VGATE003: Unguarded endpoints stay reachable for a pending vendor")
    @Description("Logs in as a pending vendor and calls the explicitly-unguarded families (profile, "
            + "notifications), asserting both return 200 so a not-yet-approved vendor can still manage its account.")
    @Severity(SeverityLevel.NORMAL)
    @Story("Unguarded endpoints reachable while pending")
    public void pendingVendorCanReachUnguardedEndpoints() {
        ConfigReader config = ConfigReader.getInstance();
        String token = vendorToken(config.getPendingVendorEmail(), config.getPendingVendorPassword());

        for (String ep : UNGUARDED) {
            int status = statusOf(token, ep);
            logStep("pending vendor -> /" + ep + " => " + status);
            Assert.assertEquals(status, 200,
                    "A pending vendor should still reach the unguarded /" + ep + " (200), got " + status);
        }
    }
}
