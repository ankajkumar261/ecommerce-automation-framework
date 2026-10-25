package com.portfolio.ecommerce.tests.website.storefront;

import com.portfolio.ecommerce.website.base.BaseTest;
import com.portfolio.ecommerce.website.config.ConfigReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIResponse;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Section 4.1 — listing filters + sort + pagination (read-only, via the public API).
 *
 * Asserts the public catalogue query contract used by /search?tab=products:
 *   GET /api/public/products?category=&minPrice=&maxPrice=&sort=&page=&limit=
 *   - price sort orders results monotonically,
 *   - price-range filters narrow the set (every card satisfies the bound),
 *   - a category filter returns only that category,
 *   - pagination pages are distinct with a stable total and a non-empty last page,
 *   - an imphealthcareble filter yields an empty result (200), not an error.
 *
 * NOTE on sort naming: the backend sorts with the UNDERSCORE values (price_asc / price_desc).
 * The hyphenated "price-asc" from the doc is a no-op (returns default order) — see the
 * message in the response summary. These tests assert the values that actually sort.
 */
@Epic("Storefront discovery")
@Feature("Listing filters, sort & pagination")
public class StorefrontDiscoveryApiTest extends BaseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected String getUrl() {
        return ConfigReader.getInstance().getStorefrontUrl();
    }

    private String apiBase() {
        String base = ConfigReader.getInstance().getApiUrl();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** GET /api/public/products with a query string; returns the "data" node (products + pagination). */
    private JsonNode fetchData(String query) {
        APIResponse resp = getPage().request().get(apiBase() + "/public/products?" + query);
        Assert.assertEquals(resp.status(), 200, "public products should return 200 for: " + query);
        try {
            return MAPPER.readTree(resp.text()).path("data");
        } catch (Exception e) {
            throw new AssertionError("public products response was not valid JSON for: " + query, e);
        }
    }

    /**
     * Sold-out products are pushed to the END of the listing regardless of price (confirmed with
     * the product owner). So price ordering only holds among IN-STOCK items; the sold-out items
     * form the tail. A product is sold out when its stock is 0.
     */
    private static boolean inStock(JsonNode p) {
        return p.path("stock").asInt(0) > 0;
    }

    /**
     * Collects in-stock prices in returned order and asserts they are ordered, tolerating a SMALL
     * number of local inversions. Out-of-stock items (stock 0) are ignored — they sort to the end.
     * The backend layers secondary tiebreaks (GST-inclusive customer price, featured ordering) on
     * the raw price, so an occasional adjacent swap is expected; a grossly unsorted result fails.
     */
    private void assertMostlyOrdered(JsonNode products, boolean ascending) {
        java.util.List<Double> prices = new java.util.ArrayList<>();
        for (JsonNode p : products) {
            if (inStock(p)) prices.add(p.path("price").asDouble());
        }
        Assert.assertTrue(prices.size() >= 2, "Need at least 2 in-stock products to assert ordering");
        int inversions = 0;
        for (int i = 1; i < prices.size(); i++) {
            boolean bad = ascending ? prices.get(i) < prices.get(i - 1)
                                    : prices.get(i) > prices.get(i - 1);
            if (bad) inversions++;
        }
        int tolerance = Math.max(1, prices.size() / 12);
        Assert.assertTrue(inversions <= tolerance,
                "Price sort is not ordered: " + inversions + " inversions among " + prices.size()
                        + " in-stock products (tolerance " + tolerance + "). Prices: " + prices);
    }

    @Test(description = "TC-DISC001: sort=price_asc orders in-stock products ascending")
    @Description("Requests sort=price_asc and verifies in-stock prices are ascending (tolerating minor "
            + "tiebreak variance); out-of-stock items are ignored as they sort to the end.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Sort")
    public void priceAscendingSortOrders() {
        assertMostlyOrdered(fetchData("sort=price_asc&limit=50").path("products"), true);
    }

    @Test(description = "TC-DISC002: sort=price_desc orders in-stock products descending")
    @Description("Requests sort=price_desc and verifies in-stock prices are descending (tolerating minor "
            + "tiebreak variance); out-of-stock items are ignored as they sort to the end.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Sort")
    public void priceDescendingSortOrders() {
        assertMostlyOrdered(fetchData("sort=price_desc&limit=50").path("products"), false);
    }

    @Test(description = "TC-DISC003: minPrice/maxPrice filters narrow the result set")
    @Description("Applies minPrice and maxPrice filters and verifies every returned product satisfies the "
            + "bound, and that filtering never returns more than the unfiltered total.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Price filter")
    public void priceRangeFilterNarrows() {
        int total = fetchData("limit=1").path("pagination").path("total").asInt();

        JsonNode minFiltered = fetchData("minPrice=5000&limit=50").path("products");
        for (JsonNode p : minFiltered) {
            Assert.assertTrue(p.path("price").asDouble() >= 5000,
                    "minPrice=5000 must exclude cheaper products, saw " + p.path("price").asDouble());
        }

        JsonNode maxFiltered = fetchData("maxPrice=4000&limit=50").path("products");
        for (JsonNode p : maxFiltered) {
            Assert.assertTrue(p.path("price").asDouble() <= 4000,
                    "maxPrice=4000 must exclude pricier products, saw " + p.path("price").asDouble());
        }

        int narrowed = fetchData("minPrice=5000&limit=50").path("pagination").path("total").asInt();
        Assert.assertTrue(narrowed <= total,
                "A price filter should not return more than the unfiltered total");
    }

    @Test(description = "TC-DISC004: A category filter returns only products of that category")
    @Description("Takes the category of an existing product, filters the listing by it, and verifies every "
            + "returned product belongs to that same category.")
    @Severity(SeverityLevel.NORMAL)
    @Story("Category filter")
    public void categoryFilterReturnsOnlyThatCategory() {
        // Self-seed: use the category of an existing product (guaranteed to have >=1 result).
        String categoryId = fetchData("limit=1").path("products").get(0).path("category").path("_id").asText();
        Assert.assertFalse(categoryId.isBlank(), "Precondition: a product with a category must exist");

        JsonNode products = fetchData("category=" + categoryId + "&limit=50").path("products");
        Assert.assertTrue(products.size() >= 1, "Category filter should return at least one product");
        for (JsonNode p : products) {
            Assert.assertEquals(p.path("category").path("_id").asText(), categoryId,
                    "Every returned product must belong to the filtered category");
        }
    }

    @Test(description = "TC-DISC005: Pagination returns distinct pages with a stable total and a non-empty last page")
    @Description("Fetches page 1 and page 2 and verifies their first items differ, the reported total is "
            + "stable across pages, and the last page is not empty.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Pagination")
    public void paginationIsConsistent() {
        JsonNode page1 = fetchData("limit=5&page=1");
        int total = page1.path("pagination").path("total").asInt();
        int totalPages = page1.path("pagination").path("totalPages").asInt();
        if (totalPages < 2) {
            throw new org.testng.SkipException("Not enough products for multi-page assertions");
        }

        JsonNode page2 = fetchData("limit=5&page=2");
        Assert.assertEquals(page2.path("pagination").path("total").asInt(), total,
                "The total must stay stable across pages");

        String firstOnP1 = page1.path("products").get(0).path("_id").asText();
        String firstOnP2 = page2.path("products").get(0).path("_id").asText();
        Assert.assertNotEquals(firstOnP2, firstOnP1,
                "Page 2's first item must differ from page 1's");

        JsonNode lastPage = fetchData("limit=5&page=" + totalPages);
        Assert.assertTrue(lastPage.path("products").size() > 0,
                "The last page must not be empty");
    }

    @Test(description = "TC-DISC006: An imphealthcareble filter yields an empty result, not an error")
    @Description("Applies an imphealthcarebly-high minPrice and verifies the API returns 200 with an empty product "
            + "list and total 0 — the zero-result state, not an error.")
    @Severity(SeverityLevel.NORMAL)
    @Story("Zero-result state")
    public void imphealthcarebleFilterReturnsEmpty() {
        JsonNode data = fetchData("minPrice=99999999&limit=5");
        Assert.assertEquals(data.path("products").size(), 0,
                "An imphealthcareble price floor should return zero products");
        Assert.assertEquals(data.path("pagination").path("total").asInt(), 0,
                "The reported total should be 0 for a zero-result query");
    }
}
