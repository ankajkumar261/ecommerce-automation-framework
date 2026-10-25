package com.portfolio.ecommerce.tests.mobile.android;

import com.portfolio.ecommerce.mobile.android.base.AndroidBaseTest;
import com.portfolio.ecommerce.mobile.android.screens.AndroidAccountScreen;
import com.portfolio.ecommerce.mobile.android.screens.AndroidOrdersScreen;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Account / Profile on Android — the counterpart of the iOS AccountTest.
 * Requires a signed-in shopper (ensureSignedIn). The Account screen is text-based in this build.
 */
@Epic("Mobile (Android)")
@Feature("Account / Profile")
public class AndroidAccountTest extends AndroidBaseTest {

    private AndroidAccountScreen openAccount() {
        ensureSignedIn();
        AndroidAccountScreen account = new AndroidAccountScreen(driver);
        Assert.assertTrue(account.open(), "The Account screen should open");
        return account;
    }

    @Test(description = "TC-AND065: Account screen shows the signed-in menu entries")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Account menu")
    public void accountShowsSignedInMenuEntries() {
        AndroidAccountScreen account = openAccount();
        Assert.assertTrue(account.hasMyOrders(), "My Orders should be visible");
        Assert.assertTrue(account.hasAddresses(), "Addresses should be visible");
        Assert.assertTrue(account.hasMessages(), "Messages (chat) should be visible");
        Assert.assertTrue(account.hasSupport(), "Help & Support should be visible");
        Assert.assertTrue(account.hasPrivacyPolicy(), "Privacy Policy should be visible");
        Assert.assertTrue(account.hasTermsAndConditions(), "Terms & Conditions should be visible");
        Assert.assertTrue(account.hasSignOut(), "Sign Out should be visible");
    }

    @Test(description = "TC-AND066: My Orders opens and shows a list or empty state")
    @Severity(SeverityLevel.CRITICAL)
    @Story("My Orders")
    public void myOrdersOpensAndShowsListOrEmpty() {
        AndroidAccountScreen account = openAccount();
        Assert.assertTrue(account.openMyOrders(), "My Orders should open");

        AndroidOrdersScreen orders = new AndroidOrdersScreen(driver);
        Assert.assertTrue(orders.isLoaded(), "My Orders screen should load");
        Assert.assertTrue(orders.orderCount() > 0 || orders.hasEmptyState(),
                "My Orders should show either orders or the empty state");
    }

    @Test(description = "TC-AND067: My Orders status filters do not error out")
    @Severity(SeverityLevel.NORMAL)
    @Story("Order status filters")
    public void myOrdersStatusFilters() {
        AndroidAccountScreen account = openAccount();
        Assert.assertTrue(account.openMyOrders(), "My Orders should open");

        AndroidOrdersScreen orders = new AndroidOrdersScreen(driver);
        Assert.assertTrue(orders.isLoaded(), "My Orders should load");

        // Status filters only render when orders exist. On an empty account there is nothing to
        // filter, so skip cleanly rather than assert a filter bar the app withholds.
        if (orders.orderCount() == 0 || orders.hasEmptyState()) {
            log.info("No orders in this account — status filter bar not shown; skipping");
            return;
        }
        Assert.assertTrue(orders.hasStatusFilters(),
                "My Orders with orders present should offer status filters");
    }
}
