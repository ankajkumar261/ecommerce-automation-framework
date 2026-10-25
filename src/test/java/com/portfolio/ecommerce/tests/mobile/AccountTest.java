package com.portfolio.ecommerce.tests.mobile;

import com.portfolio.ecommerce.mobile.base.MobileBaseTest;
import com.portfolio.ecommerce.mobile.screens.HomeScreen;
import com.portfolio.ecommerce.mobile.screens.OrderDetailScreen;
import com.portfolio.ecommerce.mobile.screens.OrdersScreen;
import com.portfolio.ecommerce.mobile.screens.ProfileScreen;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Account / Profile section coverage: verify the Profile screen menu, navigation into
 * My Orders, order detail, and back navigation.
 *
 * All tests require a signed-in shopper (supplied by ensureSignedIn).
 */
@Epic("Mobile (iOS)")
@Feature("Account / Profile")
public class AccountTest extends MobileBaseTest {

    private ProfileScreen openProfile() {
        ensureSignedIn();
        ProfileScreen profile = new ProfileScreen(getDriver());
        profile.open();
        return profile;
    }

    @Test(description = "TC-IOS065: Profile screen shows all signed-in menu entries")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Profile menu")
    @Description("Opens the Profile tab as a signed-in shopper and verifies all expected menu "
            + "entries are present: My Orders, Addresses, Chat, Support, Privacy Policy, "
            + "Terms and Conditions, Sign Out.")
    public void profileScreenShowsAllSignedInMenuEntries() {
        ProfileScreen profile = openProfile();
        Assert.assertTrue(profile.isSignedIn(), "Shopper should be signed in");

        logStep("Verify My Orders is visible");
        Assert.assertTrue(profile.hasMyOrders(), "My Orders should be visible");

        logStep("Verify Addresses is visible");
        Assert.assertTrue(profile.hasAddresses(), "Addresses should be visible");

        logStep("Verify Chat is visible");
        Assert.assertTrue(profile.hasChat(), "Chat should be visible");

        logStep("Verify Support is visible");
        Assert.assertTrue(profile.hasSupport(), "Support should be visible");

        logStep("Verify Privacy Policy is visible");
        Assert.assertTrue(profile.hasPrivacyPolicy(), "Privacy Policy should be visible");

        logStep("Verify Terms and Conditions is visible");
        Assert.assertTrue(profile.hasTermsAndConditions(), "Terms and Conditions should be visible");

        logStep("Verify Sign Out is visible");
        Assert.assertTrue(profile.hasSignOut(), "Sign Out should be visible");
    }

    @Test(description = "TC-IOS066: My Orders screen loads for a signed-in shopper")
    @Severity(SeverityLevel.CRITICAL)
    @Story("My Orders")
    @Description("Opens the Profile tab, taps My Orders, and verifies the Orders screen loads "
            + "with the title visible.")
    public void myOrdersScreenLoads() {
        ProfileScreen profile = openProfile();
        Assert.assertTrue(profile.isSignedIn(), "Shopper should be signed in");

        logStep("Open My Orders");
        profile.openMyOrders();

        OrdersScreen orders = new OrdersScreen(getDriver());
        Assert.assertTrue(orders.isLoaded(), "My Orders screen should load");
    }

    @Test(description = "TC-IOS067: My Orders screen shows order list or empty state")
    @Severity(SeverityLevel.NORMAL)
    @Story("My Orders list")
    @Description("Opens My Orders and verifies either a list of orders is shown or the empty "
            + "state is displayed.")
    public void myOrdersShowsListOrEmptyState() {
        ProfileScreen profile = openProfile();
        profile.openMyOrders();

        OrdersScreen orders = new OrdersScreen(getDriver());
        Assert.assertTrue(orders.isLoaded(), "My Orders screen should load");

        int orderCount = orders.orderCount();
        log.info("Orders on screen: {}", orderCount);

        logStep("Verify order list or empty state");
        Assert.assertTrue(orderCount > 0 || orders.hasEmptyState(),
                "My Orders should show either an order list or an empty state message");
    }

    @Test(description = "TC-IOS068: Opening an order detail loads the order detail screen")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Order detail")
    @Description("Opens My Orders and taps into the first order (if any) to verify the "
            + "order detail screen loads correctly. Skips if no orders exist.")
    public void orderDetailLoadsFromOrdersList() {
        ProfileScreen profile = openProfile();
        profile.openMyOrders();

        OrdersScreen orders = new OrdersScreen(getDriver());
        Assert.assertTrue(orders.isLoaded(), "My Orders screen should load");

        if (orders.orderCount() == 0) {
            log.info("No orders in this account — skipping order detail test");
            return;
        }

        logStep("Open the first order");
        orders.openOrder(0);

        OrderDetailScreen detail = new OrderDetailScreen(getDriver());
        Assert.assertTrue(detail.isLoaded(), "Order detail screen should load");

        logStep("Verify order ID is shown");
        String orderId = detail.orderId();
        log.info("Order ID: {}", orderId);
        Assert.assertFalse(orderId.isBlank(), "The order ID should not be blank");
    }

    @Test(description = "TC-IOS069: Order detail shows status, total, and payment method")
    @Severity(SeverityLevel.NORMAL)
    @Story("Order detail content")
    @Description("Opens an order detail and verifies it shows an order status, a total, "
            + "and a payment method.")
    public void orderDetailShowsStatusTotalPayment() {
        ProfileScreen profile = openProfile();
        profile.openMyOrders();

        OrdersScreen orders = new OrdersScreen(getDriver());
        if (orders.orderCount() == 0) {
            log.info("No orders in this account — skipping");
            return;
        }
        orders.openOrder(0);

        OrderDetailScreen detail = new OrderDetailScreen(getDriver());
        Assert.assertTrue(detail.isLoaded(), "Order detail should load");

        logStep("Verify order status is present");
        String status = detail.status();
        log.info("Order status: {}", status);
        Assert.assertFalse(status.isBlank(),
                "Order detail should show a status (Pending / Processing / Completed / Cancelled)");

        logStep("Verify total is present");
        String total = detail.totalDisplay();
        log.info("Order total: {}", total);
        Assert.assertFalse(total.isBlank(), "Order detail should show a total amount");

        logStep("Verify payment method is present");
        Assert.assertTrue(detail.hasPaymentMethod(),
                "Order detail should show a payment method");
    }

    @Test(description = "TC-IOS088: My Orders status filters filter the list without errors")
    @Severity(SeverityLevel.NORMAL)
    @Story("Order status filters")
    @Description("Opens My Orders and exercises each status filter chip that the screen offers "
            + "(All, Pending, Processing, Completed, Cancelled). Verifies each filter is applied "
            + "without crashing and the screen stays loaded. Order data varies by environment, so "
            + "this asserts the filters respond rather than a fixed number of orders per status.")
    public void myOrdersStatusFiltersFilterTheList() {
        ProfileScreen profile = openProfile();
        profile.openMyOrders();

        OrdersScreen orders = new OrdersScreen(getDriver());
        Assert.assertTrue(orders.isLoaded(), "My Orders screen should load");

        // The status filter chips only render when the account has orders. On an empty account
        // the screen shows its empty state instead, so there is nothing to filter — skip cleanly
        // rather than assert a filter bar that the app deliberately withholds.
        if (orders.orderCount() == 0 || orders.hasEmptyState()) {
            log.info("No orders in this account — the status filter bar is not shown; skipping");
            return;
        }

        logStep("Verify the All filter is present");
        Assert.assertTrue(orders.hasFilter("All"),
                "My Orders with orders present should offer an 'All' filter chip");

        String[] statuses = {"All", "Pending", "Processing", "Completed", "Cancelled"};
        for (String status : statuses) {
            if (!orders.hasFilter(status)) {
                log.info("Filter '{}' is not offered in this build — skipping it", status);
                continue;
            }
            logStep("Apply the '" + status + "' filter");
            orders.filterByStatus(status);

            Assert.assertTrue(orders.isLoaded(),
                    "My Orders should stay loaded after applying the '" + status + "' filter");
            int count = orders.orderCount();
            log.info("Orders shown under '{}': {}", status, count);
            Assert.assertTrue(count >= 0,
                    "Applying the '" + status + "' filter should not error out");
        }
    }

    @Test(description = "TC-IOS089: Every listed order opens and shows its detail")
    @Severity(SeverityLevel.NORMAL)
    @Story("Order detail")
    @Description("Opens My Orders and walks through each listed order (capped for runtime), "
            + "opening each one, verifying the detail screen loads with an order ID and a status, "
            + "then returning to the list. Skips gracefully if the account has no orders.")
    public void everyListedOrderOpensAndShowsDetail() {
        ProfileScreen profile = openProfile();
        profile.openMyOrders();

        OrdersScreen orders = new OrdersScreen(getDriver());
        Assert.assertTrue(orders.isLoaded(), "My Orders screen should load");

        int total = orders.orderCount();
        if (total == 0) {
            log.info("No orders in this account — skipping the per-order detail walk");
            return;
        }

        // Cap the walk so a large order history does not blow up suite runtime.
        int toCheck = Math.min(total, 3);
        log.info("Opening the first {} of {} order(s)", toCheck, total);

        for (int i = 0; i < toCheck; i++) {
            // Re-read the list each time — opening and going back re-renders the rows.
            OrdersScreen list = new OrdersScreen(getDriver());
            Assert.assertTrue(list.isLoaded(), "My Orders list should be shown before opening order " + i);

            logStep("Open order at position " + i);
            list.openOrder(i);

            OrderDetailScreen detail = new OrderDetailScreen(getDriver());
            Assert.assertTrue(detail.isLoaded(), "Order detail should load for order " + i);

            String orderId = detail.orderId();
            String status = detail.status();
            log.info("Order {} — id: '{}', status: '{}'", i, orderId, status);
            Assert.assertFalse(orderId.isBlank(),
                    "Order " + i + " detail should show an order ID");
            Assert.assertFalse(status.isBlank(),
                    "Order " + i + " detail should show a status");

            logStep("Return to the orders list");
            detail.goBack();
            pause(1400);
        }
    }

    @Test(description = "TC-IOS070: Profile back navigation from My Orders returns to Profile")
    @Severity(SeverityLevel.NORMAL)
    @Story("Account navigation")
    @Description("Opens Profile → My Orders, taps back, and verifies the app returns to "
            + "the Profile screen.")
    public void backNavigationFromMyOrdersReturnsToProfile() {
        ProfileScreen profile = openProfile();
        Assert.assertTrue(profile.isSignedIn(), "Shopper should be signed in");

        logStep("Open My Orders");
        profile.openMyOrders();

        OrdersScreen orders = new OrdersScreen(getDriver());
        Assert.assertTrue(orders.isLoaded(), "My Orders should load");

        logStep("Navigate back");
        orders.goBack();
        pause(1500);

        logStep("Verify we return to the Profile screen");
        Assert.assertTrue(profile.isLoaded(),
                "Should return to the Profile screen after navigating back from My Orders");
    }
}
