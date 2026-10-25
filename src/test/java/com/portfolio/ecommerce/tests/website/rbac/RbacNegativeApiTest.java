package com.portfolio.ecommerce.tests.website.rbac;

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
 * Section 17 — AUTHORISATION (RBAC) NEGATIVE SUITE.
 *
 * For every guarded endpoint, a token of a role that must not have access is rejected with
 * 401/403 and no data is leaked. All requests are GETs, so this suite is read-only.
 *
 *   VENDOR_SCOPED  — approved-vendor data; a customer must get 403, no token 401.
 *   ADMIN_ONLY     — super-admin surface; both customer and vendor must get 403, no token 401.
 *
 * (Complements the role checks embedded in the approval-gate, order-lifecycle and session suites.)
 */
@Epic("Authorisation")
@Feature("RBAC negative suite")
public class RbacNegativeApiTest extends BaseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Vendor-scoped endpoints: an approved vendor may read them; a customer may not. */
    private static final String[] VENDOR_SCOPED = {
            "vendor/my-products",
            "vendor/my-orders",
            "vendor/my-earnings",
            "vendor/dashboard",
    };

    /** Super-admin-only endpoints: neither customer nor vendor may read them. */
    private static final String[] ADMIN_ONLY = {
            "vendor/admin/pending",
            "vendor/admin/all",
            "vendor/admin/conflicts",
            "users",
            "payments/release",
            "payments/refund",
    };

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

    private String customerToken() {
        ConfigReader c = ConfigReader.getInstance();
        return login("/auth/login", c.getCustomerEmail(), c.getCustomerPassword());
    }

    private String vendorToken() {
        ConfigReader c = ConfigReader.getInstance();
        return login("/vendor/login", c.getVendorEmail(), c.getVendorPassword());
    }

    /** GET an endpoint (optionally with a bearer token) and return the HTTP status + body. */
    private APIResponse get(String endpoint, String token) {
        RequestOptions opts = RequestOptions.create();
        if (token != null) opts.setHeader("Authorization", "Bearer " + token);
        return getPage().request().get(apiBase() + "/" + endpoint, opts);
    }

    /** Asserts the response is a denial (given status) and does not leak a success/data payload. */
    private void assertDenied(APIResponse resp, int expected, String endpoint, String who) {
        Assert.assertEquals(resp.status(), expected,
                who + " must be denied on /" + endpoint + " with " + expected + ", got " + resp.status());
        JsonNode body = tree(resp.text());
        Assert.assertFalse(body.path("success").asBoolean(false),
                who + " denial on /" + endpoint + " must not return a success payload");
        Assert.assertTrue(body.path("data").isMissingNode() || body.path("data").isNull(),
                who + " denial on /" + endpoint + " must not leak a data payload");
    }

    // ─────────────────────────────────────────────────────────

    @Test(description = "TC-RBAC001: A customer is denied on vendor-scoped endpoints")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Customer vs vendor endpoints")
    @Description("Uses a customer token to GET each vendor-scoped endpoint and verifies every one is "
            + "rejected with 403 and leaks no data.")
    public void customerCannotAccessVendorEndpoints() {
        String token = customerToken();
        for (String ep : VENDOR_SCOPED) {
            assertDenied(get(ep, token), 403, ep, "A customer");
        }
    }

    @Test(description = "TC-RBAC002: A customer is denied on super-admin endpoints")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Customer vs admin endpoints")
    @Description("Uses a customer token to GET each super-admin-only endpoint and verifies every one is "
            + "rejected with 403 and leaks no data.")
    public void customerCannotAccessAdminEndpoints() {
        String token = customerToken();
        for (String ep : ADMIN_ONLY) {
            assertDenied(get(ep, token), 403, ep, "A customer");
        }
    }

    @Test(description = "TC-RBAC003: A vendor is denied on super-admin endpoints")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Vendor vs admin endpoints")
    @Description("Uses an approved-vendor token to GET each super-admin-only endpoint (vendor/admin/*, "
            + "users, payment release/refund) and verifies every one is rejected with 403 and leaks no data.")
    public void vendorCannotAccessAdminEndpoints() {
        String token = vendorToken();
        for (String ep : ADMIN_ONLY) {
            assertDenied(get(ep, token), 403, ep, "A vendor");
        }
    }

    @Test(description = "TC-RBAC004: Every protected endpoint rejects an anonymous request with 401")
    @Severity(SeverityLevel.CRITICAL)
    @Story("No token")
    @Description("GETs every vendor-scoped and super-admin endpoint with no token and verifies each "
            + "returns 401 and leaks no data.")
    public void noTokenIsRejectedEverywhere() {
        for (String ep : VENDOR_SCOPED) {
            assertDenied(get(ep, null), 401, ep, "An anonymous caller");
        }
        for (String ep : ADMIN_ONLY) {
            assertDenied(get(ep, null), 401, ep, "An anonymous caller");
        }
    }
}
