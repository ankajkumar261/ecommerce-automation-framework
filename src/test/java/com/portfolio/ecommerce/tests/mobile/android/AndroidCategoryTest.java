package com.portfolio.ecommerce.tests.mobile.android;

import com.portfolio.ecommerce.mobile.android.base.AndroidBaseTest;
import com.portfolio.ecommerce.mobile.android.screens.AndroidHomeScreen;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Category browsing on Android — counterpart to the iOS CategoryBrowseTest.
 *
 * Guest-visible, so no sign-in needed.
 */
@Epic("Mobile — Android")
@Feature("Browse")
public class AndroidCategoryTest extends AndroidBaseTest {

    @Test(description = "TC-AND020: Home advertises browsable categories")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Categories")
    @Description("Verifies Home renders category tiles. Counterpart of TC-IOS011.")
    public void homeAdvertisesCategories() {
        Assert.assertTrue(home.isLoaded(), "Home did not load");
        int categories = home.categoryCount();
        logStep("Category tiles on Home: " + categories);
        Assert.assertTrue(categories > 0, "Home shows no category tiles");
    }

    @Test(description = "TC-AND021: Tapping a category opens its listing")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Categories")
    @Description("Opens the first category and verifies a product listing renders. Counterpart "
            + "of TC-IOS012.")
    public void tappingACategoryOpensItsListing() {
        Assert.assertTrue(home.isLoaded(), "Home did not load");
        Assert.assertTrue(home.openFirstCategory(), "Could not open a category from Home");

        Assert.assertTrue(home.productCardCount() > 0,
                "The category listing shows no products. Either the category is empty on this "
                        + "environment, or the listing did not render.");
    }
}
