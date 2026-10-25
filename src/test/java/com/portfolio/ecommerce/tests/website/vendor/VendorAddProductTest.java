package com.portfolio.ecommerce.tests.website.vendor;

import com.portfolio.ecommerce.website.base.BaseTest;
import com.portfolio.ecommerce.website.config.ConfigReader;
import com.portfolio.ecommerce.website.drivers.BrowserManager;
import com.portfolio.ecommerce.website.pages.vendor.VendorAddProductPage;
import com.portfolio.ecommerce.website.pages.vendor.VendorLoginPage;
import com.github.javafaker.Faker;
import com.microsoft.playwright.Page;
import io.qameta.allure.Description;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * End-to-end test: a vendor creates a product through the full 4-step Add Product wizard.
 *
 * Flow:
 *   login -> Add Product -> Select Storefront
 *   Step 1: product details   -> Next
 *   Step 2: photo upload+crop  -> Next
 *   Step 3: pricing & sizes    -> Next
 *   Step 4: publish ("Add Product")  -> verify
 */
public class VendorAddProductTest extends BaseTest {

    private static final String SAMPLE_IMAGE = "src/test/resources/testdata/sample-product.jpg";

    /** Route the browser to the vendor login URL (BaseTest navigates here before the test). */
    @Override
    protected String getUrl() {
        return ConfigReader.getInstance().getVendorUrl();
    }

    @Test(description = "TC-VP001: Vendor creates a product end-to-end through the 4-step wizard")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Logs in as vendor, completes all 4 steps of Add Product, publishes, and verifies success")
    public void vendorCanAddProductEndToEnd() {
        Page page = BrowserManager.getPage();
        ConfigReader config = ConfigReader.getInstance();
        Faker faker = new Faker();

        // unique name each run so we never collide with an existing product
        String productName = "QA " + faker.commerce().productName() + " " + faker.number().digits(4);

        // ---- 1. Login as vendor ----
        VendorLoginPage loginPage = new VendorLoginPage(page);
        loginPage.loginAs(config.getVendorEmail(), config.getVendorPassword());

        // ---- 2. Walk the Add Product wizard ----
        VendorAddProductPage addProduct = new VendorAddProductPage(page);
        addProduct.clickAddProduct();
        // Step 0 — pick the storefront (fresh session shows the picker). Storefront and
        // category come from config: they are picked by visible name, so they differ
        // per environment.
        addProduct.selectStorefront(ConfigReader.getInstance().getVendorStorefront());

        // Step 1 — product details (ends with Next)
        addProduct.fillProductDetails(
                ConfigReader.getInstance().getVendorCategory(),   // category
                productName,
                faker.lorem().sentence(),       // description
                faker.lorem().sentence(),       // details
                "unisex",                       // gender
                500,                            // weight in grams
                "medium");                      // weight category

        // Step 2 — photo upload + crop, then Next
        addProduct.uploadPhoto(SAMPLE_IMAGE);
        addProduct.clickNext();

        // Step 3 — pricing & availability (ends with Next)
        addProduct.fillPricingAndAvailability(
                2500,                           // price
                2,                              // days to dispatch
                "M",                            // size
                10);                            // stock quantity

        // Step 4 — publish
        addProduct.publish();

        // ---- 3. Verify ----
        Assert.assertTrue(addProduct.isProductCreated(),
                "Add Product wizard did not close - creation likely failed for: " + productName);

        System.out.println("Created product: " + productName);
    }
}