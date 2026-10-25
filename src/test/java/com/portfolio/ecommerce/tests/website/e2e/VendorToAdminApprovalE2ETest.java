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
 * End-to-end "spine" test: a vendor publishes a product, an admin approves that
 * SAME product, and finally a shopper sees it live on the storefront.
 *
 * Session isolation:
 *   - Vendor steps run on the PRIMARY page (BrowserManager.getPage()).
 *   - Admin + storefront steps run on the SECONDARY page (BrowserManager.getSecondaryPage()),
 *     a separate browser context with its own cookies/storage.
 *
 *     A separate context is REQUIRED for admin because vendor and admin share the host
 *     panel.theecommerce.com — reusing the vendor context kept the vendor session alive and
 *     redirected the admin URL to the vendor dashboard.
 *
 *     The storefront (theecommerce.com) is a DIFFERENT origin, so its cookies/storage are
 *     separate from panel.theecommerce.com automatically — no session bleed. The shopper leg
 *     therefore safely reuses the secondary context; no third context is needed.
 *
 * Flow:
 *   1. Vendor (primary)   : login -> Add Product wizard -> publish   (unique name captured)
 *   2. Admin  (secondary) : login -> Products -> search that name -> View -> Approve
 *   3. Shopper(secondary) : open storefront -> (login) -> product visible in listing
 */
@Epic("End to End")
@Feature("Vendor to Admin to Storefront product spine")
public class VendorToAdminApprovalE2ETest extends BaseTest {

    // Per-environment test data — the wizard picks these by visible name.
    private static final String BOUTIQUE     = ConfigReader.getInstance().getVendorStorefront();
    private static final String CATEGORY     = ConfigReader.getInstance().getVendorCategory();
    private static final String SAMPLE_IMAGE = "src/test/resources/testdata/sample-product.jpg";

    private final Faker faker = new Faker();

    // BaseTest navigates the PRIMARY page here before the test (vendor login URL).
    @Override
    protected String getUrl() {
        return ConfigReader.getInstance().getVendorUrl();
    }

    @Test(description = "TC-E2E001: Vendor publishes a product, admin approves it, shopper sees it on the storefront")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Vendor create -> Admin approve -> Storefront visible")
    @Description("Creates a uniquely-named product as a vendor, approves it as admin (in a "
            + "separate browser context), then verifies it appears to a shopper on the public "
            + "storefront listing — the full publish spine.")
    public void vendorCreatesAndAdminApproves() {
        ConfigReader config = ConfigReader.getInstance();

        // Unique name shared across all legs so each surface can find the exact product.
        String productName = "QA " + faker.commerce().productName() + " " + faker.number().digits(4);

        // ───────────────────────────── 1. VENDOR (primary page): create + publish ─────────────────────────────
        Page vendorPage = BrowserManager.getPage();

        logStep("Vendor login");
        new VendorLoginPage(vendorPage).loginAs(config.getVendorEmail(), config.getVendorPassword());

        logStep("Vendor walks the Add Product wizard and publishes: " + productName);
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

        // ───────────────────────────── 2. ADMIN (secondary page = fresh context): find + approve ─────────────────────────────
        Page adminPage = BrowserManager.getSecondaryPage();   // isolated context: no vendor session

        logStep("Open admin panel in a fresh context and log in");
        adminPage.navigate(config.getAdminUrl(), new Page.NavigateOptions()
                .setTimeout(45000)
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        new AdminLoginPage(adminPage).loginAs(config.getAdminEmail(), config.getAdminPassword());

        logStep("Admin opens Products, finds the vendor's product, approves it");
        AdminProductApprovalPage approval = new AdminProductApprovalPage(adminPage);
        approval.openProducts();
        approval.search(productName);
        approval.viewByName(productName);
        approval.approve();

        logStep("Verify the product shows as Active (approved) in admin");
        Assert.assertTrue(approval.isApproved(productName),
                "Product '" + productName + "' should show Active after admin approval");

        // ───────────────────────────── 3. SHOPPER (secondary page, different origin): storefront visibility ─────────────────────────────
        // theecommerce.com is a different origin from panel.theecommerce.com, so reusing this
        // context is safe — the admin session does not apply to the storefront.
        logStep("Open storefront and log in as a customer");
        StorefrontPage storefront = new StorefrontPage(adminPage);
        storefront.login(config.getStorefrontUrl(), config.getCustomerEmail(), config.getCustomerPassword());

        // VERIFY VIA SEARCH, NOT THE HOME LISTING — this is a correctness fix, not a preference.
        //
        // The storefront home is a Next.js ISR page:
        //     cache-control: s-maxage=60, stale-while-revalidate=31535940
        //     x-nextjs-cache: STALE
        // so a product approved seconds ago is simply not in the cached HTML yet, and
        // stale-while-revalidate means a reload STILL returns the old page and only triggers
        // regeneration afterwards. isProductVisible() budgets ~24s against a 60s cache, so
        // asserting on home was a race: it passed or failed on where the cache window fell,
        // which read as "the publish spine is broken" when the product was live all along.
        //
        // /search?tab=products is served  cache-control: no-store, must-revalidate  — always
        // live data. That makes this leg deterministic, and it is still a real shopper journey:
        // it proves a customer can find the product on the storefront.
        //
        // Note this no longer asserts the product reaches the BROWSE listing. If that becomes a
        // requirement it needs its own check with a budget longer than the cache window, kept
        // separate from this one rather than folded back in here.
        logStep("Search the storefront and verify the product is findable by shoppers");
        storefront.searchProducts(config.getStorefrontUrl(), productName);
        Assert.assertTrue(storefront.isProductVisible(productName),
                "Approved product '" + productName + "' should be findable on the storefront");
    }
}