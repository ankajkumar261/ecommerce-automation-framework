package com.portfolio.ecommerce.tests.mobile;

import com.portfolio.ecommerce.mobile.base.MobileBaseTest;
import com.portfolio.ecommerce.mobile.screens.CategoryScreen;
import com.portfolio.ecommerce.mobile.screens.HomeScreen;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.util.List;

/**
 * iOS category browse: open a category from Home and check the listing it produces.
 *
 * Browsing is public, so these tests do not sign in and work in either auth state.
 *
 * No category name is hardcoded — the tests read whatever categories Home is advertising and
 * work with the first one. The catalogue differs per environment (and changes as products are
 * added), so a hardcoded "Anarkali Suits" would be a per-environment landmine of exactly the
 * kind that broke the web suite when it was pointed at live.
 */
@Epic("Mobile (iOS)")
@Feature("Category browse")
public class CategoryBrowseTest extends MobileBaseTest {

    /** Read the categories Home offers, skipping the "All" aggregate. */
    private String firstRealCategory(HomeScreen home) {
        List<String> categories = home.categoryNames();
        log.info("Categories on Home: {}", categories);
        for (String name : categories) {
            if (!"All".equalsIgnoreCase(name)) return name;
        }
        throw new SkipException("Home is not advertising any category to browse");
    }

    @Test(description = "TC-IOS011: Home advertises browsable categories")
    @Severity(SeverityLevel.NORMAL)
    @Story("Category rail")
    @Description("Verifies the Home screen shows its 'Shop the categories' rail with at least "
            + "one real category besides the 'All' aggregate.")
    public void homeShowsCategoryRail() {
        HomeScreen home = new HomeScreen(getDriver());
        home.open();
        Assert.assertTrue(home.isLoaded(), "Home should load");

        List<String> categories = home.categoryNames();
        log.info("Categories on Home: {}", categories);

        Assert.assertTrue(categories.size() > 0, "Home should advertise at least one category");
        Assert.assertTrue(categories.stream().anyMatch(c -> !"All".equalsIgnoreCase(c)),
                "Home should offer a real category, not just the 'All' aggregate. Got: " + categories);
    }

    @Test(groups = {"mobile-smoke"}, description = "TC-IOS012: Tapping a category opens its listing")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Open a category")
    @Description("Taps the first real category on Home and verifies its listing opens, showing "
            + "that category as the heading and offering the category filter rail.")
    public void tappingCategoryOpensItsListing() {
        HomeScreen home = new HomeScreen(getDriver());
        home.open();
        Assert.assertTrue(home.isLoaded(), "Home should load");

        String category = firstRealCategory(home);

        logStep("Open category: " + category);
        home.openCategory(category);

        CategoryScreen listing = new CategoryScreen(getDriver());
        Assert.assertTrue(listing.isLoaded(category),
                "The listing for '" + category + "' should open");
        Assert.assertTrue(listing.railHasCategory("All"),
                "The category listing should offer the 'All' filter in its rail");

        log.info("'{}' listing shows: {}", category, listing.productNames());
    }

    @Test(description = "TC-IOS013: A category shows the number of products it advertises")
    @Severity(SeverityLevel.NORMAL)
    @Story("Category product count")
    @Description("Reads the item count a category chip advertises on Home, opens that category "
            + "and verifies the listing shows exactly that many products.")
    public void categoryShowsTheProductCountItAdvertises() {
        HomeScreen home = new HomeScreen(getDriver());
        home.open();
        Assert.assertTrue(home.isLoaded(), "Home should load");

        String category = firstRealCategory(home);
        int advertised = home.categoryItemCount(category);
        log.info("Home advertises {} item(s) for '{}'", advertised, category);

        if (advertised < 0) {
            throw new SkipException("Home did not advertise an item count for: " + category);
        }

        logStep("Open category: " + category);
        home.openCategory(category);

        CategoryScreen listing = new CategoryScreen(getDriver());
        Assert.assertTrue(listing.isLoaded(category), "The category listing should open");

        List<String> shown = listing.productNames();
        log.info("'{}' listing contains {}: {}", category, shown.size(), shown);

        Assert.assertEquals(shown.size(), advertised,
                "The '" + category + "' listing should show the " + advertised
                        + " product(s) Home advertises, but showed " + shown.size() + ": " + shown);
    }

    @Test(description = "TC-IOS014: Switching category from the rail changes the results")
    @Severity(SeverityLevel.NORMAL)
    @Story("Switch category")
    @Description("From inside one category, uses the filter rail to switch to 'All' and verifies "
            + "the listing widens to include at least as many products as the single category.")
    public void switchingToAllWidensTheResults() {
        HomeScreen home = new HomeScreen(getDriver());
        home.open();
        Assert.assertTrue(home.isLoaded(), "Home should load");

        String category = firstRealCategory(home);
        home.openCategory(category);

        CategoryScreen listing = new CategoryScreen(getDriver());
        Assert.assertTrue(listing.isLoaded(category), "The category listing should open");
        int inCategory = listing.productCount();
        log.info("'{}' shows {} product(s)", category, inCategory);

        logStep("Switch the rail to 'All'");
        listing.selectCategory("All");

        int inAll = listing.productCount();
        log.info("'All' shows {} product(s)", inAll);

        Assert.assertTrue(inAll >= inCategory,
                "'All' should show at least as many products (" + inAll + ") as the single "
                        + "category '" + category + "' (" + inCategory + ")");
    }
}
