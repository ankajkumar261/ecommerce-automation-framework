package com.portfolio.ecommerce.tests.mobile.android;

import com.portfolio.ecommerce.mobile.android.base.AndroidBaseTest;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

@Epic("Mobile — Android")
@Feature("Home and navigation")
public class AndroidHomeTest extends AndroidBaseTest {

    @Test(description = "TC-AND002: Home shows a complete bottom tab bar")
    @Severity(SeverityLevel.BLOCKER)
    @Story("App shell")
    @Description("Every Android test navigates by the bottom tabs, so this is the check that "
            + "fails first and most clearly if the tab tags are dropped from a build.")
    public void homeShowsAllTabs() {
        Assert.assertTrue(home.isLoaded(), "Home did not load");
        Assert.assertTrue(home.hasAllTabs(),
                "The bottom tab bar is incomplete — expected bottom_tab_home, _shop, _cart and "
                        + "_search to all be present.");
    }

    @Test(description = "TC-AND003: Home lists products the shopper can open")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Browse")
    @Description("Home must render product cards. This is the storefront's core promise, and "
            + "the entry point for every product-detail test that follows.")
    public void homeListsProducts() {
        Assert.assertTrue(home.isLoaded(), "Home did not load");
        int cards = home.productCardCount();
        logStep("Product cards on Home: " + cards);
        Assert.assertTrue(cards > 0,
                "Home rendered no product cards. Either the catalogue is empty on this "
                        + "environment, or the products did not load before the check.");
    }
}
