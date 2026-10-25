package com.portfolio.ecommerce.tests.mobile;

import com.portfolio.ecommerce.mobile.base.MobileBaseTest;
import com.portfolio.ecommerce.mobile.screens.ProductDetailScreen;
import com.portfolio.ecommerce.mobile.screens.SearchScreen;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Locale;

/**
 * iOS search: find products by keyword, handle the no-match case, clear the search.
 *
 * Search is public, so unlike the cart these tests neither sign in nor care whether the run's
 * shared session happens to be signed in — they work either way.
 *
 * The search term is deliberately a generic one that the catalogue is structured around
 * rather than a single product name, so the test does not break the moment one product is
 * renamed. The assertion is a property of the results ("every hit contains the term"), not a
 * hardcoded list of expected products.
 */
@Epic("Mobile (iOS)")
@Feature("Search")
public class SearchTest extends MobileBaseTest {

    /** A word that appears in several product names across storefronts. */
    private static final String TERM = "Suit";

    /** A term chosen to match nothing, to exercise the empty state. */
    private static final String NONSENSE_TERM = "zzzqqqxyz";

    @Test(groups = {"mobile-smoke"}, description = "TC-IOS007: Searching a keyword returns matching products")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Keyword search")
    @Description("Opens the Search tab, searches for a keyword, and verifies every product "
            + "returned actually contains that keyword in its name.")
    public void searchReturnsMatchingProducts() {
        SearchScreen search = new SearchScreen(getDriver());

        logStep("Open the Search tab");
        search.open();
        Assert.assertTrue(search.isLoaded(), "The Search screen should load");

        logStep("Search for: " + TERM);
        search.search(TERM);

        Assert.assertFalse(search.hasNoResults(),
                "'" + TERM + "' should match products in the catalogue");

        List<String> names = search.productResultNames();
        log.info("Search returned {} product(s): {}", names.size(), names);
        Assert.assertTrue(names.size() > 0,
                "Searching '" + TERM + "' should return at least one product");

        logStep("Verify every result actually matches the search term");
        for (String name : names) {
            Assert.assertTrue(
                    name.toLowerCase(Locale.ROOT).contains(TERM.toLowerCase(Locale.ROOT)),
                    "Result '" + name + "' does not contain the search term '" + TERM + "'");
        }
    }

    @Test(description = "TC-IOS008: A search with no matches shows the empty state")
    @Severity(SeverityLevel.NORMAL)
    @Story("Empty search results")
    @Description("Searches for a term that cannot match anything and verifies the app shows its "
            + "'No results for ...' state rather than stale or unrelated results.")
    public void searchWithNoMatchesShowsEmptyState() {
        SearchScreen search = new SearchScreen(getDriver());

        search.open();
        Assert.assertTrue(search.isLoaded(), "The Search screen should load");

        logStep("Search for a term that matches nothing: " + NONSENSE_TERM);
        search.search(NONSENSE_TERM);

        Assert.assertTrue(search.hasNoResults(),
                "A term matching nothing should show the 'No results' state");
        Assert.assertEquals(search.productResultCount(), 0,
                "No product cards should be shown for a term that matches nothing");

        String message = search.noResultsMessage();
        log.info("Empty state message: {}", message);
        Assert.assertTrue(message.contains(NONSENSE_TERM),
                "The no-results message should quote the term searched for. Got: " + message);
    }

    @Test(description = "TC-IOS009: Clearing the search returns to the browse state")
    @Severity(SeverityLevel.NORMAL)
    @Story("Clear search")
    @Description("After a search, taps the clear control and verifies the box is empty and the "
            + "idle suggestion state (trending / recent searches) is shown again.")
    public void clearingSearchReturnsToBrowseState() {
        SearchScreen search = new SearchScreen(getDriver());

        search.open();
        Assert.assertTrue(search.isLoaded(), "The Search screen should load");

        search.search(TERM);
        Assert.assertTrue(search.currentTerm().contains(TERM),
                "The search box should hold the term that was typed");

        logStep("Clear the search");
        search.clearSearch();

        Assert.assertFalse(search.currentTerm().contains(TERM),
                "The search box should no longer hold the search term");
        Assert.assertTrue(search.isIdleState(),
                "Clearing the search should restore the trending / recent-searches state");
    }

    @Test(description = "TC-IOS010: A search result opens its product detail")
    @Severity(SeverityLevel.NORMAL)
    @Story("Open a search result")
    @Description("Searches for a keyword, opens the first product result and verifies the "
            + "product detail screen loads showing that same product.")
    public void searchResultOpensProductDetail() {
        SearchScreen search = new SearchScreen(getDriver());

        search.open();
        search.search(TERM);
        Assert.assertTrue(search.productResultCount() > 0,
                "Precondition: the search should return at least one product");

        logStep("Open the first result");
        String productName = search.openFirstResult();
        log.info("Opened from search: {}", productName);

        ProductDetailScreen pdp = new ProductDetailScreen(getDriver());
        Assert.assertTrue(pdp.isLoaded(), "Product detail should load from a search result");
        Assert.assertTrue(pdp.showsProductNamed(productName),
                "Product detail should show the product that was tapped: " + productName);
    }
}
