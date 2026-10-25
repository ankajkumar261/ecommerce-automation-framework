package com.portfolio.ecommerce.tests.mobile.android;

import com.portfolio.ecommerce.mobile.android.base.AndroidBaseTest;
import com.portfolio.ecommerce.mobile.android.screens.AndroidProductDetailScreen;
import com.portfolio.ecommerce.mobile.android.screens.AndroidSearchScreen;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

@Epic("Mobile — Android")
@Feature("Search")
public class AndroidSearchTest extends AndroidBaseTest {

    /** Broad enough to match this catalogue whatever the dev data holds on the day. */
    private static final String QUERY = "set";

    private AndroidSearchScreen openSearch() {
        Assert.assertTrue(home.isLoaded(), "Home did not load");
        Assert.assertTrue(home.openSearchTab(), "Could not open the Search tab");
        AndroidSearchScreen search = new AndroidSearchScreen(driver);
        Assert.assertTrue(search.isLoaded(), "Search did not load — no search_field on screen");
        return search;
    }

    @Test(description = "TC-AND008: Searching a keyword returns matching products")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Search")
    @Description("Types a keyword into the tagged search field and verifies results render as "
            + "product cards.")
    public void searchingReturnsMatchingProducts() {
        AndroidSearchScreen search = openSearch();
        Assert.assertTrue(search.search(QUERY), "Could not type into the search field");
        int results = search.resultCount();
        logStep("Results for '" + QUERY + "': " + results);
        Assert.assertTrue(results > 0,
                "Searching '" + QUERY + "' returned no products. If the catalogue changed, this "
                        + "query may simply no longer match anything.");
    }

    @Test(description = "TC-AND009: A search result opens its product detail")
    @Severity(SeverityLevel.NORMAL)
    @Story("Search")
    @Description("Opens the first search result and verifies the product detail screen renders — "
            + "proving results are navigable, not just displayed.")
    public void aSearchResultOpensItsDetail() {
        AndroidSearchScreen search = openSearch();
        Assert.assertTrue(search.search(QUERY), "Could not type into the search field");
        Assert.assertTrue(search.resultCount() > 0, "No results to open");

        Assert.assertTrue(search.openFirstResult(), "Could not open the first search result");
        AndroidProductDetailScreen detail = new AndroidProductDetailScreen(driver);
        Assert.assertTrue(detail.isLoaded(),
                "Product detail did not load after opening a search result");
    }
}
