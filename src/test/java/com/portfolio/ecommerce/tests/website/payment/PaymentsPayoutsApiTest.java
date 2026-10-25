package com.portfolio.ecommerce.tests.website.payment;

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
 * Section 11 — Payments, Payouts, Receipts (read-only slices).
 *
 * Asserts the reporting/pipeline contracts without moving money:
 *   - Vendor earnings totals are internally consistent (payout aliases, non-negative).
 *   - handed_directly ("direct") payment rows are born "received" and SKIP the payout pipeline —
 *     they must never sit in the pending/released release queue.
 *   - The payments list is admin-scoped; receipts require authentication.
 */
@Epic("Payments & payouts")
@Feature("Earnings, payout queue & receipts")
public class PaymentsPayoutsApiTest extends BaseTest {

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

    private String vendorToken() {
        ConfigReader c = ConfigReader.getInstance();
        return login("/vendor/login", c.getVendorEmail(), c.getVendorPassword());
    }

    private String customerToken() {
        ConfigReader c = ConfigReader.getInstance();
        return login("/auth/login", c.getCustomerEmail(), c.getCustomerPassword());
    }

    // ─────────────────────────────────────────────────────────

    @Test(description = "TC-PAY001: Vendor earnings totals are internally consistent")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Earnings totals")
    @Description("Reads the vendor earnings summary and verifies the payout aliases agree "
            + "(pendingPayout==pendingEarnings, releasedPayout==releasedEarnings, totalRevenue==totalEarned), "
            + "all totals are non-negative, and transactions is a list.")
    public void vendorEarningsTotalsAreConsistent() {
        JsonNode d = tree(getPage().request().get(apiBase() + "/vendor/my-earnings",
                RequestOptions.create().setHeader("Authorization", "Bearer " + vendorToken())).text())
                .path("data");

        double pendingPayout = d.path("pendingPayout").asDouble();
        double pendingEarnings = d.path("pendingEarnings").asDouble();
        double releasedPayout = d.path("releasedPayout").asDouble();
        double releasedEarnings = d.path("releasedEarnings").asDouble();

        Assert.assertEquals(pendingPayout, pendingEarnings, 0.01, "pendingPayout must equal pendingEarnings");
        Assert.assertEquals(releasedPayout, releasedEarnings, 0.01, "releasedPayout must equal releasedEarnings");
        Assert.assertEquals(d.path("totalRevenue").asDouble(), d.path("totalEarned").asDouble(), 0.01,
                "totalRevenue must equal totalEarned");
        for (String k : new String[]{"pendingPayout", "releasedPayout", "totalRevenue", "totalDue"}) {
            Assert.assertTrue(d.path(k).asDouble() >= 0, k + " must be non-negative");
        }
        // transactions may be paginated/omitted depending on the query — if present, it must be a list.
        JsonNode tx = d.path("transactions");
        if (!tx.isMissingNode() && !tx.isNull()) {
            Assert.assertTrue(tx.isArray(), "when present, earnings transactions must be a list");
        }
    }

    @Test(description = "TC-PAY002: Direct (handed_directly) payments skip the payout queue")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Direct payments bypass payout")
    @Description("Scans the payments ledger and verifies every direct row (manualMethod set) has status "
            + "'received' — direct payments are settled at creation and never enter the pending/released payout queue.")
    public void directPaymentsSkipPayoutQueue() {
        JsonNode payments = tree(getPage().request().get(apiBase() + "/payments?limit=300",
                RequestOptions.create().setHeader("Authorization", "Bearer " + adminToken())).text())
                .path("data").path("payments");
        Assert.assertTrue(payments.isArray() && payments.size() > 0, "There should be payment rows to inspect");

        int directCount = 0;
        for (JsonNode p : payments) {
            String manualMethod = p.path("manualMethod").asText("");
            if (!manualMethod.isBlank()) {
                directCount++;
                Assert.assertEquals(p.path("status").asText(), "received",
                        "A direct payment (manualMethod=" + manualMethod + ") must be 'received', not in the payout queue");
            }
        }
        if (directCount == 0) {
            throw new org.testng.SkipException(
                    "Precondition not met: no direct (handed_directly) payments exist in this environment");
        }
    }

    @Test(description = "TC-PAY003: The payments ledger is admin-scoped")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Payments ledger access")
    @Description("Verifies GET /payments returns 200 with a payments array for an admin and 401 with no token.")
    public void paymentsListRequiresAdmin() {
        APIResponse anon = getPage().request().get(apiBase() + "/payments");
        Assert.assertEquals(anon.status(), 401, "Anonymous payments read must be 401");

        APIResponse admin = getPage().request().get(apiBase() + "/payments?limit=5",
                RequestOptions.create().setHeader("Authorization", "Bearer " + adminToken()));
        Assert.assertEquals(admin.status(), 200, "Admin should read the payments ledger");
        Assert.assertTrue(tree(admin.text()).path("data").path("payments").isArray(),
                "payments response must expose a payments array");
    }

    @Test(description = "TC-PAY004: Receipts require authentication")
    @Severity(SeverityLevel.NORMAL)
    @Story("Receipts")
    @Description("Verifies GET /receipts is 401 anonymously and returns 200 for an authenticated customer.")
    public void receiptsRequireAuth() {
        APIResponse anon = getPage().request().get(apiBase() + "/receipts");
        Assert.assertEquals(anon.status(), 401, "Anonymous receipts read must be 401");

        APIResponse resp = getPage().request().get(apiBase() + "/receipts",
                RequestOptions.create().setHeader("Authorization", "Bearer " + customerToken()));
        Assert.assertEquals(resp.status(), 200, "An authenticated customer should read their receipts");
    }
}
