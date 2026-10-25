package com.portfolio.ecommerce.tests.mobile;

import com.portfolio.ecommerce.mobile.base.MobileBaseTest;
import com.portfolio.ecommerce.mobile.screens.HomeScreen;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * First iOS test — proves the mobile plumbing works end to end:
 * launch the Storefront app and confirm the Home (Discover) screen loaded.
 */
@Epic("Mobile (iOS)")
@Feature("Home screen")
public class HomeScreenTest extends MobileBaseTest {

    @Test(groups = {"mobile-smoke"}, description = "TC-IOS001: App launches and the Home screen loads")
    @Severity(SeverityLevel.BLOCKER)
    @Story("App launch")
    @Description("Launches the Storefront customer app on the iOS simulator and verifies the "
            + "Home/Discover screen and its bottom tab bar are visible.")
    public void homeScreenLoads() {
        HomeScreen home = new HomeScreen(getDriver());

        logStep("Verify the Home (Discover) screen loaded");
        Assert.assertTrue(home.isLoaded(),
                "Home screen (Discover title + tab bar) should be visible on launch");

        logStep("Verify the bottom tab bar (Home, Shop, Cart) is visible");
        Assert.assertTrue(home.isTabBarVisible(),
                "Bottom tab bar should show Home, Shop and Cart");
    }
}