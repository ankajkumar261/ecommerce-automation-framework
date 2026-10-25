package com.portfolio.ecommerce.tests.website.vendor;

import com.portfolio.ecommerce.website.base.BaseTest;
import com.portfolio.ecommerce.website.config.ConfigReader;
import com.portfolio.ecommerce.website.drivers.BrowserManager;
import com.portfolio.ecommerce.website.pages.vendor.VendorAddProductPage;
import com.portfolio.ecommerce.website.pages.vendor.VendorLoginPage;
import com.github.javafaker.Faker;
import com.microsoft.playwright.Page;
import io.qameta.allure.Description;
import io.qameta.allure.Issue;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import java.time.LocalDate;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Negative / validation tests for the vendor Add Product wizard.
 *
 * How these work (standard automation):
 *   - Each test feeds INVALID input and asserts the app does the CORRECT thing
 *     (blocks it / stays on the step / clamps the value).
 *   - App handles it correctly -> assertion passes -> GREEN  -> validation works.
 *   - App handles it wrong     -> assertion fails  -> RED    -> a bug, shown as a failure.
 *
 * IMPORTANT — two ways the wizard can block invalid input on a step:
 *   (a) it DISABLES the Next button, or
 *   (b) it keeps Next enabled but refuses to advance when clicked.
 * The app uses (a) for the pricing-step validations (Next greys out). So these tests must
 * NOT blindly click a disabled Next (that just times out) — instead they assert the block
 * via a shared helper that: if Next is disabled -> pass; if enabled -> click and assert the
 * wizard stayed on the Pricing step. This handles both (a) and (b) safely.
 */
public class VendorAddProductNegativeTest extends BaseTest {

    // Storefront/category come from config: the wizard picks them by visible name, so
    // they differ per environment (dev has "Test Creations", live does not).
    private static final String BOUTIQUE     = ConfigReader.getInstance().getVendorStorefront();
    private static final String CATEGORY     = ConfigReader.getInstance().getVendorCategory();
    private static final String SAMPLE_IMAGE = "src/test/resources/testdata/sample-product.jpg";

    private final Faker faker = new Faker();

    @Override
    protected String getUrl() {
        return ConfigReader.getInstance().getVendorUrl();
    }

    // ---- assertion helpers -------------------------------------------------

    /** The wizard's Next button must be disabled (step-level validation not satisfied). */
    private void assertNextDisabled(Page page) {
        assertThat(page.locator("button:has-text('Next')").first()).isDisabled();
    }

    /** The price field only exists on the Pricing step, so its visibility = still on that step. */
    private void assertStillOnPricingStep(Page page) {
        assertThat(page.locator("input[name='price']")).isVisible();
    }

    /**
     * Assert the wizard BLOCKS advancing from the Pricing step with the current (invalid) input.
     * Works whether the app disables Next, or keeps it enabled but refuses to advance:
     *   - If Next is disabled            -> validation is working; done.
     *   - If Next is (still) enabled     -> click it, then assert we stayed on Pricing.
     * Never clicks a disabled button (which would just time out).
     */
    private void assertPricingBlocked(Page page, VendorAddProductPage addProduct) {
        boolean nextEnabled = page.locator("button:has-text('Next')").first().isEnabled();
        if (!nextEnabled) {
            // (a) Next is disabled — the wizard is correctly blocking. Nothing more to do.
            return;
        }
        // (b) Next is enabled — clicking must NOT advance past Pricing.
        addProduct.clickNext();
        assertStillOnPricingStep(page);
    }

    // ---- navigation helpers (get the wizard to a given step) ---------------

    private VendorAddProductPage openWizardAtCategory(Page page) {
        ConfigReader config = ConfigReader.getInstance();
        new VendorLoginPage(page).loginAs(config.getVendorEmail(), config.getVendorPassword());

        VendorAddProductPage addProduct = new VendorAddProductPage(page);
        addProduct.clickAddProduct();
        addProduct.selectStorefront(BOUTIQUE);
        return addProduct;
    }

    private VendorAddProductPage reachPhotoStep(Page page) {
        VendorAddProductPage addProduct = openWizardAtCategory(page);
        addProduct.fillProductDetails(CATEGORY, "QA " + faker.commerce().productName(),
                "Some description", "Some details", "unisex", 500, "medium");
        return addProduct;
    }

    private VendorAddProductPage reachPricingStep(Page page) {
        VendorAddProductPage addProduct = reachPhotoStep(page);
        addProduct.uploadPhoto(SAMPLE_IMAGE);
        addProduct.clickNext();
        return addProduct;
    }

    // ========================================================================
    // STEP 1 — Product details validation
    // ========================================================================

    @Test(description = "TC-VPN001: Empty product name keeps Step 1 Next disabled")
    @Severity(SeverityLevel.NORMAL)
    @Description("Fill every Step 1 field except the product name. Next must stay disabled.")
    public void emptyProductNameKeepsNextDisabled() {
        Page page = BrowserManager.getPage();
        VendorAddProductPage addProduct = openWizardAtCategory(page);

        addProduct.selectCategory(CATEGORY);
        addProduct.enterDescription("Some description");
        addProduct.enterDetails("Some details");
        addProduct.selectGender("unisex");
        addProduct.enterWeight(500);
        addProduct.selectWeightCategory("medium");

        assertNextDisabled(page);
    }

    @Test(description = "TC-VPN002: No category selected keeps Step 1 Next disabled")
    @Severity(SeverityLevel.NORMAL)
    @Description("Reach Step 1 without choosing a category. Next must stay disabled.")
    public void noCategoryKeepsNextDisabled() {
        Page page = BrowserManager.getPage();
        openWizardAtCategory(page);
        assertNextDisabled(page);
    }

    // ========================================================================
    // STEP 2 — Photo validation
    // ========================================================================

    @Test(description = "TC-VPN003: No photo uploaded keeps Step 2 Next disabled")
    @Severity(SeverityLevel.NORMAL)
    @Description("Reach the Photo step without uploading an image. Next must stay disabled.")
    public void noPhotoKeepsNextDisabled() {
        Page page = BrowserManager.getPage();
        reachPhotoStep(page);
        assertNextDisabled(page);
    }

    // ========================================================================
    // STEP 3 — Pricing & availability validation
    // ========================================================================

    @Test(description = "TC-VPN004: Missing price keeps Step 3 Next disabled")
    @Severity(SeverityLevel.CRITICAL)
    @Description("On the Pricing step, select a size but leave price empty. Next must stay disabled.")
    public void missingPriceKeepsNextDisabled() {
        Page page = BrowserManager.getPage();
        VendorAddProductPage addProduct = reachPricingStep(page);
        addProduct.selectSize("M");
        assertNextDisabled(page);
    }

    @Test(description = "TC-VPN005: No size selected keeps the wizard on the Pricing step")
    @Severity(SeverityLevel.NORMAL)
    @Description("Enter a valid price but select no size. The wizard must not advance past Pricing "
            + "(whether Next is disabled or refuses to advance when clicked).")
    public void noSizeBlocksStep3() {
        Page page = BrowserManager.getPage();
        VendorAddProductPage addProduct = reachPricingStep(page);
        addProduct.enterPrice(2500);
        addProduct.enterDaysToReady(2);
        assertPricingBlocked(page, addProduct);
    }

    @Test(description = "TC-VPN006: Price 0 keeps the wizard on the Pricing step")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Enter price 0 with a valid size + stock. The wizard must not advance past Pricing "
            + "(whether Next is disabled or refuses to advance when clicked).")
    public void zeroPriceBlocksStep3() {
        Page page = BrowserManager.getPage();
        VendorAddProductPage addProduct = reachPricingStep(page);
        addProduct.enterPrice(0);
        addProduct.enterDaysToReady(2);
        addProduct.selectSize("M");
        addProduct.enterStock(10);
        assertPricingBlocked(page, addProduct);
    }

    @Test(description = "TC-VPN007: Discount over 99% is rejected and blocks the Pricing step")
    @Severity(SeverityLevel.CRITICAL)
    @Description("The discount-percent field accepts 1–99. Entering 150 must be rejected: the app "
            + "shows an 'Enter a valid discount (1–99%)' message and keeps Next disabled so the "
            + "invalid discount can't be saved. (The app validates-and-blocks rather than clamping "
            + "the typed value, which is acceptable — a >99% discount is never accepted.)")
    public void discountOver99IsRejected() {
        Page page = BrowserManager.getPage();
        VendorAddProductPage addProduct = reachPricingStep(page);
        addProduct.enterPrice(2500);
        addProduct.enterDaysToReady(2);
        addProduct.selectSize("M");
        addProduct.enterStock(10);
        addProduct.applyPercentDiscount(150);   // above the allowed max of 99

        // Correct behaviour: the invalid discount is rejected and cannot be submitted.
        // The app blocks the step (error message + disabled Next) rather than clamping.
        assertPricingBlocked(page, addProduct);
    }

    @Test(description = "TC-VPN008: Offer end date before start date must keep the wizard on Pricing")
    @Severity(SeverityLevel.CRITICAL)
    @Issue("BOUT-YYY")   // offer-date-validation defect ticket
    @Description("Set an offer end date earlier than the start date. The wizard must not advance "
            + "past Pricing (whether Next is disabled or refuses to advance when clicked). "
            + "If it advances, date-order validation is missing.")
    public void offerEndBeforeStartBlocksStep3() {
        Page page = BrowserManager.getPage();
        VendorAddProductPage addProduct = reachPricingStep(page);
        addProduct.enterPrice(2500);
        addProduct.enterDaysToReady(2);
        addProduct.selectSize("M");
        addProduct.enterStock(10);
        addProduct.applyPercentDiscount(10);

        String start = LocalDate.now().plusDays(5).toString();
        String end   = LocalDate.now().plusDays(1).toString();   // earlier than start
        addProduct.setOfferStartDate(start);
        addProduct.setOfferEndDate(end);

        // Correct behaviour: a reversed date range is rejected, wizard stays on Pricing.
        assertPricingBlocked(page, addProduct);
    }
}