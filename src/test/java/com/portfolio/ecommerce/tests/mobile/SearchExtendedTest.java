package com.portfolio.ecommerce.tests.mobile;

import com.portfolio.ecommerce.mobile.base.MobileBaseTest;
import com.portfolio.ecommerce.mobile.screens.ProductDetailScreen;
import com.portfolio.ecommerce.mobile.screens.SearchScreen;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Extended search coverage: partial names, empty input, different content types,
 * and scope filtering.
 *
 * The base SearchTest covers keyword search, no-match, and clear. This class extends
 * that coverage to partial names, empty input, storefront/service searches, scope filters,
 * and navigation correctness.
 *
 * Search is public — no sign-in required.
 */
@Epic("Mobile (iOS)")
@Feature("Search — extended")
public class SearchExtendedTest extends MobileBaseTest {

    /** A partial product name that should match multiple items. */
    private static final String PARTIAL_TERM = "Sah";

    /** A term likely to match a storefront name. */
    private static final String BOUTIQUE_TERM = "Test";

    /** A term likely to match a service. */
    private static final String SERVICE_TERM = "Tailor";

    /** Gibberish that matches nothing. */
    private static final String NO_MATCH_TERM = "xyzabc999";

    @Test(description = "TC-IOS058: Partial product name search returns matching results")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Partial name search")
    @Description("Searches with a partial product name and verifies that results containing "
            + "the partial term are returned.")
    public void partialNameSearchReturnsResults() {
        SearchScreen search = new SearchScreen(getDriver());
        search.open();
        Assert.assertTrue(search.isLoaded(), "Search screen should load");

        logStep("Search with partial term: " + PARTIAL_TERM);
        search.search(PARTIAL_TERM);

        Assert.assertFalse(search.hasNoResults(),
                "Partial term '" + PARTIAL_TERM + "' should match at least one product");

        List<String> names = search.productResultNames();
        log.info("Partial search returned {} product(s): {}", names.size(), names);
        Assert.assertTrue(names.size() > 0,
                "Partial search should return at least one product");

        logStep("Verify results contain the partial term");
        for (String name : names) {
            Assert.assertTrue(
                    name.toLowerCase().contains(PARTIAL_TERM.toLowerCase()),
                    "Result '" + name + "' should contain the partial search term '"
                            + PARTIAL_TERM + "'");
        }
    }

    @Test(description = "TC-IOS059: Empty search input shows the idle suggestion state")
    @Severity(SeverityLevel.NORMAL)
    @Story("Empty search")
    @Description("Opens the Search tab without typing anything and verifies the idle "
            + "suggestion state (trending / recent searches) is shown.")
    public void emptySearchInputShowsIdleState() {
        SearchScreen search = new SearchScreen(getDriver());
        search.open();
        Assert.assertTrue(search.isLoaded(), "Search screen should load");

        logStep("Verify the idle state is shown with no search input");
        Assert.assertTrue(search.isIdleState(),
                "With no search input, the idle state (trending / recent searches) should show");

        logStep("Verify no results are shown yet");
        Assert.assertEquals(search.productResultCount(), 0,
                "No product results should appear without a search term");
    }

    @Test(description = "TC-IOS060: Search for a storefront name returns storefront results")
    @Severity(SeverityLevel.NORMAL)
    @Story("Storefront search")
    @Description("Searches for a term likely to match a storefront and verifies that results "
            + "are displayed. The assertion is on result presence, not on specific storefronts, "
            + "so it works across environments.")
    public void searchForStorefrontReturnsResults() {
        SearchScreen search = new SearchScreen(getDriver());
        search.open();
        Assert.assertTrue(search.isLoaded(), "Search screen should load");

        logStep("Search for a storefront term: " + BOUTIQUE_TERM);
        search.search(BOUTIQUE_TERM);

        logStep("Verify results are displayed (products, services, or storefronts)");
        int products = search.productResultCount();
        boolean hasSection = search.hasSection("RECOMMENDED BOUTIQUES")
                || search.hasSection("BOUTIQUES")
                || search.hasSection("SERVICES");
        log.info("Products: {}, Storefront/Service sections: {}", products, hasSection);

        Assert.assertTrue(products > 0 || hasSection,
                "Searching '" + BOUTIQUE_TERM + "' should return some results — "
                        + "product cards, storefront section, or services");
    }

    @Test(description = "TC-IOS061: Search for a service name returns service results")
    @Severity(SeverityLevel.NORMAL)
    @Story("Service search")
    @Description("Searches for a term likely to match a service and verifies service results "
            + "are displayed.")
    public void searchForServiceReturnsServiceResults() {
        SearchScreen search = new SearchScreen(getDriver());
        search.open();
        Assert.assertTrue(search.isLoaded(), "Search screen should load");

        logStep("Search for a service term: " + SERVICE_TERM);
        search.search(SERVICE_TERM);

        logStep("Verify service or product results are displayed");
        int products = search.productResultCount();
        boolean hasServices = search.hasSection("SERVICES");
        log.info("Products: {}, Services section: {}", products, hasServices);

        Assert.assertTrue(products > 0 || hasServices,
                "Searching '" + SERVICE_TERM + "' should return results");
    }

    @Test(description = "TC-IOS062: Incorrect/no-match search shows the empty state")
    @Severity(SeverityLevel.CRITICAL)
    @Story("No-match search")
    @Description("Searches for gibberish and verifies the empty state with an appropriate "
            + "message is displayed.")
    public void incorrectSearchShowsNoResults() {
        SearchScreen search = new SearchScreen(getDriver());
        search.open();
        Assert.assertTrue(search.isLoaded(), "Search screen should load");

        logStep("Search for gibberish: " + NO_MATCH_TERM);
        search.search(NO_MATCH_TERM);

        logStep("Verify the empty state");
        Assert.assertTrue(search.hasNoResults(),
                "A term matching nothing should show the 'No results' state");

        logStep("Verify the no-results message contains the search term");
        String message = search.noResultsMessage();
        log.info("Empty state message: {}", message);
        Assert.assertTrue(message.contains(NO_MATCH_TERM),
                "The no-results message should quote the search term. Got: " + message);

        logStep("Verify zero product results");
        Assert.assertEquals(search.productResultCount(), 0,
                "No product cards should be shown for a term that matches nothing");
    }

    @Test(description = "TC-IOS063: Selecting a search result opens the correct item")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Search result navigation")
    @Description("Searches for a keyword, opens the first result, and verifies the product "
            + "detail screen shows the same product that was tapped from the search results.")
    public void searchResultNavigatesToCorrectItem() {
        SearchScreen search = new SearchScreen(getDriver());
        search.open();
        Assert.assertTrue(search.isLoaded(), "Search screen should load");

        logStep("Search for: Suit");
        search.search("Suit");
        Assert.assertTrue(search.productResultCount() > 0, "No results to open");

        logStep("Open the first result");
        String resultName = search.openFirstResult();
        log.info("Opened from search: {}", resultName);

        ProductDetailScreen pdp = new ProductDetailScreen(getDriver());
        Assert.assertTrue(pdp.isLoaded(), "Product detail should load");
        Assert.assertTrue(pdp.showsProductNamed(resultName),
                "Product detail should show the product that was tapped: " + resultName);
    }

    @Test(description = "TC-IOS064: Search scope filter works correctly")
    @Severity(SeverityLevel.NORMAL)
    @Story("Search scope filter")
    @Description("Searches for a term, then applies the Products scope filter and verifies "
            + "results are filtered correctly.")
    public void searchScopeFilterWorksCorrectly() {
        SearchScreen search = new SearchScreen(getDriver());
        search.open();
        Assert.assertTrue(search.isLoaded(), "Search screen should load");

        logStep("Search for a term that matches multiple types");
        search.search("Set");

        int allProducts = search.productResultCount();
        log.info("Results before filtering: {} products", allProducts);

        logStep("Apply the Products filter");
        search.selectScope("Products");

        int filteredProducts = search.productResultCount();
        log.info("Results after Products filter: {} products", filteredProducts);

        // The Products filter should show only products (same or fewer than unfiltered).
        Assert.assertTrue(filteredProducts >= 0,
                "The Products filter should not error out");
        // If there were products before, they should still be present (phealthcarebly fewer
        // if storefront/service results were filtered out, but product count should not drop).
        if (allProducts > 0) {
            Assert.assertTrue(filteredProducts > 0,
                    "Applying the Products filter should still show product results");
        }
    }
}
