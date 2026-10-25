package com.portfolio.ecommerce.tests.mobile.android;

import com.portfolio.ecommerce.mobile.android.base.AndroidBaseTest;
import com.portfolio.ecommerce.mobile.android.screens.AndroidSearchScreen;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Extended search on Android — the counterpart of the iOS SearchExtendedTest.
 *
 * The base AndroidSearchTest covers a keyword search and opening a result. This adds partial
 * names, no-match, and empty input. Android search results are product cards only (storefront /
 * service scopes are not separately exposed in this build), so this covers the feasible subset.
 */
@Epic("Mobile (Android)")
@Feature("Search — extended")
public class AndroidSearchExtendedTest extends AndroidBaseTest {

    private static final String PARTIAL_TERM = "Sah";
    private static final String NO_MATCH_TERM = "xyzabc999";

    private AndroidSearchScreen openSearch() {
        // The Search tab lives on the bottom bar; if a prior screen left the app off the tabbed
        // world the first tap can miss, so reset to Home and retry once.
        if (!home.openSearchTab()) {
            resetToHome();
            Assert.assertTrue(home.openSearchTab(), "Could not open the Search tab");
        }
        AndroidSearchScreen search = new AndroidSearchScreen(driver);
        Assert.assertTrue(search.isLoaded(), "Search screen should load");
        return search;
    }

    @Test(description = "TC-AND058: A partial product name returns matching results")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Partial name search")
    public void partialNameSearchReturnsResults() {
        AndroidSearchScreen search = openSearch();
        Assert.assertTrue(search.search(PARTIAL_TERM), "Could not run the search");
        int count = search.resultCount();
        log.info("Partial '{}' returned {} result(s)", PARTIAL_TERM, count);
        Assert.assertTrue(count > 0,
                "A partial product name should return at least one product result");
    }

    @Test(description = "TC-AND062: A no-match search returns no results")
    @Severity(SeverityLevel.CRITICAL)
    @Story("No-match search")
    public void noMatchSearchReturnsNothing() {
        AndroidSearchScreen search = openSearch();
        Assert.assertTrue(search.search(NO_MATCH_TERM), "Could not run the search");
        int count = search.resultCount();
        log.info("No-match '{}' returned {} result(s)", NO_MATCH_TERM, count);
        Assert.assertEquals(count, 0,
                "A term that matches nothing should return zero product results");
    }

    @Test(description = "TC-AND059: Empty search input is handled gracefully (idle state)")
    @Severity(SeverityLevel.NORMAL)
    @Story("Empty search")
    public void emptySearchShowsIdleState() {
        AndroidSearchScreen search = openSearch();
        // Type a term, then clear back to empty and verify the field really is empty and the
        // Search screen stays healthy. Android surfaces trending products on an empty query
        // rather than an empty list, so this asserts the empty-input handling, not a zero count.
        search.search(PARTIAL_TERM);
        search.search("");
        String q = search.currentQuery();
        log.info("Query after clearing: '{}'", q);
        Assert.assertTrue(q.isEmpty(),
                "The search field should be empty after clearing the query, was: '" + q + "'");
        Assert.assertTrue(search.isLoaded(),
                "The Search screen should remain loaded with an empty query (idle state)");
    }
}
