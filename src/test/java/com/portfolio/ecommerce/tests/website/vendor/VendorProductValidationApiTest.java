package com.portfolio.ecommerce.tests.website.vendor;

import com.portfolio.ecommerce.website.base.BaseTest;
import com.portfolio.ecommerce.website.config.ConfigReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Section 3.3 — product field validation at the CONTROLLER / schema layer.
 *
 * The doc requires the validation rules to be enforced at three layers (admin form,
 * controller 400 + friendly message, Mongoose schema). The admin-form layer is covered
 * by {@link VendorAddProductNegativeTest}; this suite drives the API layer:
 * POST /api/vendor/my-products with invalid payloads and asserts each is rejected with 400.
 *
 * SAFETY: every payload here is deliberately invalid, so the create is rejected and NO
 * product is persisted — these tests do not mutate the catalogue.
 */
@Epic("Catalog")
@Feature("Product validation (controller/schema layer)")
public class VendorProductValidationApiTest extends BaseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SAMPLE_IMAGE = "src/test/resources/testdata/sample-product.jpg";

    @Override
    protected String getUrl() {
        return ConfigReader.getInstance().getVendorUrl();
    }

    private String apiBase() {
        String base = ConfigReader.getInstance().getApiUrl();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private String createUrl() {
        return apiBase() + "/vendor/my-products";
    }

    // ── Auth ──────────────────────────────────────────────────
    private String approvedVendorToken() {
        ConfigReader config = ConfigReader.getInstance();
        APIResponse resp = getPage().request().post(apiBase() + "/vendor/login",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setData(Map.of("email", config.getVendorEmail(),
                                "password", config.getVendorPassword())));
        Assert.assertTrue(resp.ok(), "Approved vendor login should succeed");
        String token = tree(resp.text()).path("data").path("token").asText(null);
        if (token == null) token = tree(resp.text()).path("token").asText(null);
        Assert.assertNotNull(token, "Vendor login must return a token");
        return token;
    }

    /** Discover a real category ObjectId so schema validation targets only the field under test. */
    private String firstCategoryId(String token) {
        APIResponse resp = getPage().request().get(apiBase() + "/vendor/my-categories",
                RequestOptions.create().setHeader("Authorization", "Bearer " + token));
        Assert.assertEquals(resp.status(), 200, "Should be able to read vendor categories");
        JsonNode root = tree(resp.text());
        JsonNode cats = root.path("categories");
        if (!cats.isArray()) cats = root.path("data").path("categories");
        if (!cats.isArray()) cats = root.path("data");
        Assert.assertTrue(cats.isArray() && cats.size() > 0, "At least one category must exist");
        return cats.get(0).path("_id").asText();
    }

    private JsonNode tree(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            throw new AssertionError("Response was not valid JSON: " + body, e);
        }
    }

    private byte[] sampleImage() {
        try {
            return Files.readAllBytes(Paths.get(SAMPLE_IMAGE));
        } catch (Exception e) {
            throw new AssertionError("Could not read sample image at " + SAMPLE_IMAGE, e);
        }
    }

    /** Build a multipart product payload with the sample image; caller supplies the (bad) price. */
    private FormData productForm(String categoryId, String price) {
        return FormData.create()
                .set("name", "QA Validation Probe")
                .set("description", "controller validation probe")
                .set("category", categoryId)
                .set("price", price)
                .set("stock", "{\"M\":10}")
                .set("images", new FilePayload("sample-product.jpg", "image/jpeg", sampleImage()));
    }

    // ─────────────────────────────────────────────────────────
    // CONTROLLER FRIENDLY-MESSAGE LAYER (no image needed)
    // ─────────────────────────────────────────────────────────

    @Test(description = "TC-PVAL001: Missing name/price is rejected with a friendly 400")
    @Description("Posts an empty product payload and verifies the controller rejects it with 400 and a "
            + "friendly 'required' message rather than a raw server error.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Required fields")
    public void missingNameAndPriceRejected() {
        String token = approvedVendorToken();
        APIResponse resp = getPage().request().post(createUrl(),
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + token)
                        .setHeader("Content-Type", "application/json")
                        .setData(Map.of()));
        Assert.assertEquals(resp.status(), 400, "Empty payload must be rejected (400)");
        Assert.assertTrue(resp.text().toLowerCase().contains("required"),
                "Response should carry a friendly 'required' message, was: " + resp.text());
    }

    @Test(description = "TC-PVAL002: A product with no image is rejected with a friendly 400")
    @Description("Posts a product with valid name/price/category but no image and verifies the controller "
            + "rejects it with 400 and an 'image required' message.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Image required")
    public void missingImageRejected() {
        String token = approvedVendorToken();
        String category = firstCategoryId(token);
        APIResponse resp = getPage().request().post(createUrl(),
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + token)
                        .setHeader("Content-Type", "application/json")
                        .setData(Map.of(
                                "name", "QA Validation Probe",
                                "description", "no image",
                                "category", category,
                                "price", 2500,
                                "stock", Map.of("M", 10))));
        Assert.assertEquals(resp.status(), 400, "A product with no image must be rejected (400)");
        Assert.assertTrue(resp.text().toLowerCase().contains("image"),
                "Response should mention the missing image, was: " + resp.text());
    }

    // ─────────────────────────────────────────────────────────
    // SCHEMA VALUE VALIDATORS (reached via multipart image)
    // ─────────────────────────────────────────────────────────

    @Test(description = "TC-PVAL003: A price above the 10,000,000 cap is rejected")
    @Description("Submits a multipart product (with a real image and category) whose price exceeds the "
            + "10,000,000 cap and verifies schema validation rejects it with 400 — no product is created.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Price range")
    public void priceAboveMaxRejected() {
        String token = approvedVendorToken();
        String category = firstCategoryId(token);
        APIResponse resp = getPage().request().post(createUrl(),
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + token)
                        .setMultipart(productForm(category, "20000000")));
        Assert.assertEquals(resp.status(), 400, "A price over the cap must be rejected (400)");
        String body = resp.text().toLowerCase();
        Assert.assertTrue(body.contains("maximum") || body.contains("10000000") || body.contains("price"),
                "Response should explain the price-range violation, was: " + resp.text());
    }

    @Test(description = "TC-PVAL004: A scientific-notation price (1e10) is rejected")
    @Description("Submits a multipart product with price '1e10' and verifies it is rejected with 400 "
            + "(the value parses to over-cap) — scientific-notation prices are not accepted.")
    @Severity(SeverityLevel.NORMAL)
    @Story("Price format")
    public void sciNotationPriceRejected() {
        String token = approvedVendorToken();
        String category = firstCategoryId(token);
        APIResponse resp = getPage().request().post(createUrl(),
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + token)
                        .setMultipart(productForm(category, "1e10")));
        Assert.assertEquals(resp.status(), 400,
                "A scientific-notation / over-cap price must be rejected (400)");
    }

    // ─────────────────────────────────────────────────────────
    // AUTH
    // ─────────────────────────────────────────────────────────

    @Test(description = "TC-PVAL005: Product creation requires authentication")
    @Description("Posts a product with no token and verifies creation is rejected with 401/403 — the "
            + "create endpoint is protected.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Endpoint is protected")
    public void createRequiresAuth() {
        APIResponse resp = getPage().request().post(createUrl(),
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setData(Map.of("name", "QA", "price", 100)));
        Assert.assertTrue(resp.status() == 401 || resp.status() == 403,
                "Unauthenticated create must be rejected (401/403), got " + resp.status());
    }
}
