package com.portfolio.ecommerce.tests.website.communication;

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
 * Section 12 — Communication (Chat, Support, Notifications), read-only slices.
 *
 * Asserts the read contracts without sending messages or mutating notifications:
 *   - The three notification counters agree (list unreadCount == /unread-count == /badges total).
 *   - Notifications and vendor chat require authentication.
 *   - Vendor support is reachable in every approval state (approved AND pending) — support sits
 *     outside the vendorApproved gate.
 */
@Epic("Communication")
@Feature("Notifications, chat & support")
public class NotificationsChatSupportApiTest extends BaseTest {

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

    private String approvedVendorToken() {
        ConfigReader c = ConfigReader.getInstance();
        return login("/vendor/login", c.getVendorEmail(), c.getVendorPassword());
    }

    private String pendingVendorToken() {
        ConfigReader c = ConfigReader.getInstance();
        return login("/vendor/login", c.getPendingVendorEmail(), c.getPendingVendorPassword());
    }

    private JsonNode getData(String endpoint, String token) {
        APIResponse resp = getPage().request().get(apiBase() + "/" + endpoint,
                RequestOptions.create().setHeader("Authorization", "Bearer " + token));
        Assert.assertEquals(resp.status(), 200, "GET /" + endpoint + " should return 200");
        return tree(resp.text()).path("data");
    }

    // ─────────────────────────────────────────────────────────

    @Test(description = "TC-COMM001: The three notification counters agree")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Notification counts stay in sync")
    @Description("Reads the notifications list, /notifications/unread-count and /notifications/badges and "
            + "verifies the unread total is the same across all three (and non-negative).")
    public void notificationCountsAreConsistent() {
        String token = customerToken();

        JsonNode list = getData("notifications", token);
        JsonNode unread = getData("notifications/unread-count", token);
        JsonNode badges = getData("notifications/badges", token);

        int listUnread = list.path("unreadCount").asInt();
        int dedicated = unread.path("count").asInt();
        int badgeTotal = badges.path("total").asInt();

        Assert.assertTrue(dedicated >= 0, "unread count must be non-negative");
        Assert.assertEquals(dedicated, badgeTotal,
                "/unread-count (" + dedicated + ") must match /badges total (" + badgeTotal + ")");
        Assert.assertEquals(listUnread, dedicated,
                "the list's unreadCount (" + listUnread + ") must match /unread-count (" + dedicated + ")");
        Assert.assertTrue(list.path("notifications").isArray(), "notifications must be a list");
    }

    @Test(description = "TC-COMM002: Notifications require authentication")
    @Severity(SeverityLevel.NORMAL)
    @Story("Endpoint is protected")
    @Description("Calls /notifications with no token and verifies it returns 401.")
    public void notificationsRequireAuth() {
        APIResponse resp = getPage().request().get(apiBase() + "/notifications");
        Assert.assertEquals(resp.status(), 401, "Anonymous notifications read must be 401");
    }

    @Test(description = "TC-COMM003: Vendor chat threads require an authenticated vendor")
    @Severity(SeverityLevel.NORMAL)
    @Story("Chat is protected")
    @Description("Verifies GET /vendor/my-chat returns 200 for an authenticated vendor and 401 with no token.")
    public void vendorChatRequiresAuth() {
        APIResponse anon = getPage().request().get(apiBase() + "/vendor/my-chat");
        Assert.assertEquals(anon.status(), 401, "Anonymous vendor chat must be 401");

        APIResponse resp = getPage().request().get(apiBase() + "/vendor/my-chat",
                RequestOptions.create().setHeader("Authorization", "Bearer " + approvedVendorToken()));
        Assert.assertEquals(resp.status(), 200, "An authenticated vendor should read their chat threads");
    }

    @Test(description = "TC-COMM004: Vendor support is reachable in every approval state")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Support outside the approval gate")
    @Description("Verifies GET /vendor/support returns 200 for both an approved vendor and a pending "
            + "(non-approved) vendor — support is available regardless of approval status.")
    public void vendorSupportReachableWhilePending() {
        APIResponse approved = getPage().request().get(apiBase() + "/vendor/support",
                RequestOptions.create().setHeader("Authorization", "Bearer " + approvedVendorToken()));
        Assert.assertEquals(approved.status(), 200, "An approved vendor should reach support");

        APIResponse pending = getPage().request().get(apiBase() + "/vendor/support",
                RequestOptions.create().setHeader("Authorization", "Bearer " + pendingVendorToken()));
        Assert.assertEquals(pending.status(), 200,
                "A pending vendor must still reach support (it sits outside the approval gate)");
    }
}
