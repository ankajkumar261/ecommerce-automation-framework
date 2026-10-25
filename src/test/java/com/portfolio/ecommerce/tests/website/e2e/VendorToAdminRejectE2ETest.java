package com.portfolio.ecommerce.tests.website.e2e;

import com.portfolio.ecommerce.website.base.BaseTest;
import com.portfolio.ecommerce.website.config.ConfigReader;
import com.portfolio.ecommerce.website.drivers.BrowserManager;
import com.portfolio.ecommerce.website.pages.admin.AdminLoginPage;
import com.portfolio.ecommerce.website.pages.admin.AdminProductApprovalPage;
import com.portfolio.ecommerce.website.pages.storefront.StorefrontPage;
import com.portfolio.ecommerce.website.pages.vendor.VendorAddProductPage;
import com.portfolio.ecommerce.website.pages.vendor.VendorLoginPage;
import com.github.javafaker.Faker;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Reject-path E2E (mirror of the approval spine):
 *   vendor publishes a product -> admin REJECTS it -> the product must NOT appear
 *   to shoppers on the storefront.
 *
 * Session isolation matches the approval spine:
 *   - Vendor on the PRIMARY page.
 *   - Admin on a SECONDARY (separate) context (shares host panel.theecommerce.com).
 *   - Storefront reuses the secondary context (different origin, no session bleed).
 */
@Epic("End to End")
@Feature("Vendor to Admin rejection")
public class VendorToAdminRejectE2ETest extends BaseTest {

    // Per-environment test data — the wizard picks these by visible name.
    private static final String BOUTIQUE     = ConfigReader.getInstance().getVendorStorefront();
    private static final String CATEGORY     = ConfigReader.getInstance().getVendorCategory();
    private static final String SAMPLE_IMAGE = "src/test/resources/testdata/sample-product.jpg";

    private final Faker faker = new Faker();

    @Override
    protected String getUrl() {
        return ConfigReader.getInstance().getVendorUrl();
    }

    @Test(description = "TC-E2E002: Vendor publishes a product, admin rejects it, shopper cannot see it")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Vendor create -> Admin reject -> NOT visible on storefront")
    @Description("Creates a uniquely-named product as a vendor, rejects it as admin (separate "
            + "context), then verifies it does NOT appear on the storefront Men's Casual category.")
    public void vendorCreatesAndAdminRejects() {
        ConfigReader config = ConfigReader.getInstance();

        String productName = "QA " + faker.commerce().productName() + " " + faker.number().digits(4);

        // 1. VENDOR (primary): create + publish
        Page vendorPage = BrowserManager.getPage();
        logStep("Vendor login");
        new VendorLoginPage(vendorPage).loginAs(config.getVendorEmail(), config.getVendorPassword());

        logStep("Vendor publishes: " + productName);
        VendorAddProductPage addProduct = new VendorAddProductPage(vendorPage);
        addProduct.clickAddProduct();
        addProduct.selectStorefront(BOUTIQUE);
        addProduct.fillProductDetails(CATEGORY, productName,
                faker.lorem().sentence(), faker.lorem().sentence(),
                "unisex", 500, "medium");
        addProduct.uploadPhoto(SAMPLE_IMAGE);
        addProduct.clickNext();
        addProduct.fillPricingAndAvailability(2500, 2, "M", 10);
        addProduct.publish();
        Assert.assertTrue(addProduct.isProductCreated(),
                "Vendor Add Product wizard did not close — creation likely failed for: " + productName);

        // 2. ADMIN (secondary = fresh context): find + reject
        Page adminPage = BrowserManager.getSecondaryPage();
        logStep("Open admin in a fresh context and log in");
        adminPage.navigate(config.getAdminUrl(), new Page.NavigateOptions()
                .setTimeout(45000)
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        new AdminLoginPage(adminPage).loginAs(config.getAdminEmail(), config.getAdminPassword());

        logStep("Admin finds the product and rejects it");
        AdminProductApprovalPage approval = new AdminProductApprovalPage(adminPage);
        approval.openProducts();
        approval.search(productName);
        approval.viewByName(productName);
        approval.reject();

        logStep("Verify the product shows as Rejected in admin");
        Assert.assertTrue(approval.isRejected(productName),
                "Product '" + productName + "' should show Rejected after admin rejection");

        // 3. STOREFRONT (secondary context, different origin): must NOT be visible
        logStep("Verify the rejected product is NOT visible on the storefront");
        StorefrontPage storefront = new StorefrontPage(adminPage);
        storefront.openTShirtCategory(config.getStorefrontUrl());
        Assert.assertFalse(storefront.isProductVisible(productName),
                "Rejected product '" + productName + "' must NOT appear on the storefront");
    }
}