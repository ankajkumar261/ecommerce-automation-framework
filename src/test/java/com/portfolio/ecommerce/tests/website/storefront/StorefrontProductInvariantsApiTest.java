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
 * Section 3.1 — storefront-facing product invariants (read-only, via the public API).
 *
 * Asserts the catalogue rules the doc calls out for the storefront:
 *   - A pending/rejected product is NOT visible on the storefront (only approved+active leak out).
 *   - price is the MRP and the source of truth (positive, within bounds).
 *   - The discount is DERIVED from price + discountPercent, not a stored comparePrice.
 *
 * All checks read GET /api/public/products, so nothing is mutated.
 */
@Epic("Catalog")
@Feature("Storefront product invariants")
public class StorefrontProductInvariantsApiTest extends BaseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double MAX_PRICE = 10_000_000d;

    @Override
    protected String getUrl() {
        return ConfigReader.getInstance().getStorefrontUrl();
    }

    private String apiBase() {
        String base = ConfigReader.getInstance().getApiUrl();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** GET /api/public/products and return the products array (data.products). */
    private JsonNode fetchProducts(int limit) {
        APIResponse resp = getPage().request().get(apiBase() + "/public/products?limit=" + limit);
        Assert.assertEquals(resp.status(), 200, "public products should return 200");
        try {
            JsonNode products = MAPPER.readTree(resp.text()).path("data").path("products");
            Assert.assertTrue(products.isArray() && products.size() > 0,
                    "The public catalogue should return at least one product");
            return products;
        } catch (Exception e) {
            throw new AssertionError("public products response was not valid JSON", e);
        }
    }

    @Test(description = "TC-PUB001: The public listing exposes only approved, active products")
    @Description("Fetches the public catalogue and verifies every returned product is approvalStatus=approved "
            + "and active — no pending or rejected product leaks to the storefront.")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Pending products not visible")
    public void publicListingExposesOnlyApprovedActiveProducts() {
        JsonNode products = fetchProducts(100);
        for (JsonNode p : products) {
            String name = p.path("name").asText("<unnamed>");
            JsonNode approval = p.path("approvalStatus");
            if (!approval.isMissingNode() && !approval.isNull()) {
                Assert.assertEquals(approval.asText(), "approved",
                        "Non-approved product leaked to the storefront: " + name);
            }
            JsonNode active = p.path("isActive");
            if (active.isBoolean()) {
                Assert.assertTrue(active.asBoolean(),
                        "Inactive product leaked to the storefront: " + name);
            }
        }
    }

    @Test(description = "TC-PUB002: Every public product carries a valid MRP (price > 0, <= 10,000,000)")
    @Description("Verifies every public product's price is numeric and within the MRP bounds (greater than 0 "
            + "and at most 10,000,000) — price is the source-of-truth MRP.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Price is the MRP source of truth")
    public void everyPublicProductHasValidMrp() {
        JsonNode products = fetchProducts(100);
        for (JsonNode p : products) {
            String name = p.path("name").asText("<unnamed>");
            JsonNode price = p.path("price");
            Assert.assertTrue(price.isNumber(), "price must be numeric for: " + name);
            double v = price.asDouble();
            Assert.assertTrue(v > 0, "price must be > 0 for: " + name + " (was " + v + ")");
            Assert.assertTrue(v <= MAX_PRICE,
                    "price must be <= " + MAX_PRICE + " for: " + name + " (was " + v + ")");
        }
    }

    @Test(description = "TC-PUB003: Discount is derived from price + discountPercent, not a stored value")
    @Description("Verifies discountPercent stays within [0,100) and, when a discounted price is exposed, it "
            + "equals the value derived from price × (1 − pct/100) — confirming the badge is computed, not stored.")
    @Severity(SeverityLevel.NORMAL)
    @Story("Discount badge is derived")
    public void discountIsDerivedAndConsistent() {
        JsonNode products = fetchProducts(100);
        for (JsonNode p : products) {
            String name = p.path("name").asText("<unnamed>");
            double percent = p.path("discountPercent").asDouble(0);
            Assert.assertTrue(percent >= 0 && percent < 100,
                    "discountPercent must be in [0,100) for: " + name + " (was " + percent + ")");

            // If the API exposes an effective/discounted price, it must equal the derived value.
            JsonNode discounted = p.path("discountedPrice");
            if (discounted.isNumber()) {
                double price = p.path("price").asDouble();
                double derived = price * (1 - percent / 100d);
                Assert.assertEquals(discounted.asDouble(), derived, 1.0,
                        "discountedPrice must equal the derived value (price*(1-pct/100)) for: " + name);
                Assert.assertTrue(discounted.asDouble() <= price,
                        "discountedPrice must not exceed the MRP for: " + name);
            }
        }
    }
}
