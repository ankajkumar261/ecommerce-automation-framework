package com.portfolio.ecommerce.website.pages.vendor;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.SelectOption;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Paths;

/**
 * Page Object for the Vendor "Add Product" multi-step wizard.
 *
 * STEP 0 — Select Storefront : shown in fresh sessions (pick the storefront to add to)
 * STEP 1 — Product Details : category, name, description, details, gender, weight, weight category
 * STEP 2 — Photos          : upload image, crop
 * STEP 3 — Pricing & Availability : price, dispatch, discount, offer, sizes, stock, status flags
 * STEP 4 — Review / Publish : final "Add Product" button + success check
 */
public class VendorAddProductPage {

    private static final Logger log = LogManager.getLogger(VendorAddProductPage.class);
    private final Page page;

    // ---- Entry ----
    private final Locator addProductCard;
    private final Locator storefrontSearchInput;   // Select Storefront step (always shown in fresh sessions)

    // ---- Step 1 ----
    private final Locator categoryInput;
    private final Locator productNameInput;
    private final Locator descriptionTextarea;
    private final Locator detailsTextarea;
    private final Locator genderSelect;
    private final Locator weightInput;
    private final Locator weightCategorySelect;

    // ---- Step 2 ----
    private final Locator photoUploadInput;
    private final Locator useCropButton;

    // ---- Step 3 ----
    private final Locator priceInput;
    private final Locator daysToReadyInput;
    private final Locator discountToggle;
    private final Locator discountPercentTypeBtn;
    private final Locator discountFlatTypeBtn;
    private final Locator discountPercentInput;
    private final Locator discountFlatInput;
    private final Locator offerStartDateInput;
    private final Locator offerEndDateInput;
    private final Locator offerUsageLimitInput;
    private final Locator stockInput;

    // ---- Step 4 ----
    private final Locator completeButton;
    private final Locator successToast;

    // ---- Shared ----
    private final Locator nextButton;

    public VendorAddProductPage(Page page) {
        this.page = page;

        this.addProductCard       = page.locator("h3:has-text('Add Product')");
        this.storefrontSearchInput  = page.locator("input[placeholder='Search storefronts...']");

        // Step 1
        this.categoryInput        = page.locator("input[placeholder='Type to search categories...']");
        this.productNameInput     = page.locator("input[name='productName']");
        this.descriptionTextarea  = page.locator("textarea[name='productDescription']");
        this.detailsTextarea      = page.locator("textarea[name='productDetails']");
        this.genderSelect         = page.locator("select[name='gender']");
        this.weightInput          = page.locator("input[placeholder*='9000']");
        this.weightCategorySelect = page.locator("select[name='weightCategory']");

        // Step 2
        this.photoUploadInput = page.locator("input[type='file']").first();
        this.useCropButton        = page.locator("button:has-text('Use crop')");

        // Step 3
        this.priceInput           = page.locator("input[name='price']");
        this.daysToReadyInput     = page.locator("input[name='daysToReady']");
        // The discount toggle shares its CSS-module class with the newer "Price includes GST"
        // switch, and that class carries a build-specific hash (…_mvcxc_… → …_1iuh0_…) that
        // changes on every rebuild. Anchor on the adjacent label instead ("No Discount" / with
        // the discount on it reads "Discount") — hash-free and unambiguous vs the GST switch.
        this.discountToggle         = page.locator(
                "xpath=//span[contains(normalize-space(.),'Discount')]/preceding-sibling::button[1]");
        this.discountPercentTypeBtn = page.locator("button:text-is('% Off')");
        this.discountFlatTypeBtn    = page.locator("button:text-is('₹ Flat Off')");
        this.discountPercentInput   = page.locator("input[name='discountPercent']");
        this.discountFlatInput      = page.locator("input[name='discountFlat']");
        this.offerStartDateInput  = page.locator("input[name='offerStartDate']");
        this.offerEndDateInput    = page.locator("input[name='offerEndDate']");
        this.offerUsageLimitInput = page.locator("input[name='offerUsageLimit']");
        this.stockInput           = page.locator("input[placeholder='Qty']");

        // Step 4
        this.completeButton       = page.locator("button._completeButton_mvcxc_1232, button:has-text('Add Product')").last();
        this.successToast         = page.locator("[class*='toast'], [class*='success'], [role='alert']");

        // Shared
        this.nextButton           = page.locator("button:has-text('Next')");
    }

    // ============================================================
    // ENTRY  +  STEP 0 (Select Storefront)
    // ============================================================

    @Step("Open the Add Product form")
    public void clickAddProduct() {
        addProductCard.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        addProductCard.click();
        // Don't hard-wait on the storefront search here — the modal sometimes opens
        // on the storefront step and sometimes skips straight to Category.
        // Give the modal a moment to render either way.
        page.waitForTimeout(1000);
        log.info("Add Product modal opened");
    }

    @Step("Select storefront (if the picker is shown): {name}")
    public void selectStorefront(String name) {
        // The storefront step is not always present. Probe for it with a SHORT timeout;
        // if it's not there, the wizard already advanced — just return.
        Locator search = page.locator("input[placeholder='Search storefronts...']");
        try {
            search.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(4000));
        } catch (TimeoutError e) {
            log.info("No storefront picker shown — wizard already on Category step, skipping");
            return;
        }

        search.fill(name);
        // Scope to the wizard modal: an unscoped div:has-text(...) also matches the
        // dashboard's "Welcome back, <storefront>!" banner BEHIND the modal overlay,
        // which intercepts the click and times out. The storefront row has no stable
        // class, but its name <p> is unique inside the modal and the click bubbles
        // up to the row's handler.
        Locator card = page.locator("[class*='_modal_'] p:text-is('" + name + "')").first();
        card.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(5000));
        card.click();
        log.info("Storefront selected: {}", name);

        Locator next = page.locator("button:has-text('Next'):not([disabled])");
        try {
            next.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(3000));
            next.click();
            log.info("Storefront step Next clicked");
        } catch (TimeoutError ignored) {
            log.info("Storefront step auto-advanced");
        }
    }

    // ============================================================
    // STEP 1 — PRODUCT DETAILS
    // ============================================================

    @Step("Select category: {category}")
    public void selectCategory(String category) {
        categoryInput.click();
        categoryInput.fill(category);

        // Use getByText (quote-safe) instead of :has-text('...'), which breaks when
        // the category contains an apostrophe, e.g. "Men's Casual".
        // Prefer a real button role; fall back to any element with that text.
        Locator option = page.getByRole(
                com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(category)).first();

        try {
            option.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(5000));
        } catch (Exception e) {
            // Fallback: match the dropdown item by visible text (also quote-safe).
            option = page.getByText(category, new Page.GetByTextOptions().setExact(true)).first();
            option.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        }

        option.click();
        log.info("Category selected: {}", category);
    }
    @Step("Enter product name: {name}")
    public void enterProductName(String name) { productNameInput.fill(name); }

    @Step("Enter product description")
    public void enterDescription(String description) { descriptionTextarea.fill(description); }

    @Step("Enter product details")
    public void enterDetails(String details) { detailsTextarea.fill(details); }

    /** value = unisex | female | male | kids | other */
    @Step("Select gender: {value}")
    public void selectGender(String value) { genderSelect.selectOption(new SelectOption().setValue(value)); }

    @Step("Enter weight (grams): {grams}")
    public void enterWeight(int grams) { weightInput.fill(String.valueOf(grams)); }

    /** value = light | medium | heavy | extra_heavy | bulk_5kg | bulk_10kg | bulk_15kg | bulk_20kg */
    @Step("Select weight category: {value}")
    public void selectWeightCategory(String value) {
        weightCategorySelect.selectOption(new SelectOption().setValue(value));
    }

    @Step("Fill product details (step 1)")
    public void fillProductDetails(String category, String name, String description,
                                   String details, String gender, int weightGrams,
                                   String weightCategory) {
        selectCategory(category);
        enterProductName(name);
        enterDescription(description);
        enterDetails(details);
        selectGender(gender);
        enterWeight(weightGrams);
        selectWeightCategory(weightCategory);
        clickNext();
    }

    // ============================================================
    // STEP 2 — PHOTOS
    // ============================================================

    @Step("Upload product photo: {filePath}")
    public void uploadPhoto(String filePath) {
        photoUploadInput.setInputFiles(Paths.get(filePath));
        useCropButton.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        useCropButton.click();
        // After the click the button reads "Saving…" while the image uploads and Next
        // stays disabled; wait for the save to finish so the caller's clickNext()
        // doesn't time out mid-upload.
        page.locator("button:has-text('Next'):not([disabled])").first()
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE).setTimeout(30000));
        log.info("Photo uploaded and cropped: {}", filePath);
    }

    // ============================================================
    // STEP 3 — PRICING & AVAILABILITY
    // ============================================================

    @Step("Enter price: {price}")
    public void enterPrice(int price) { priceInput.fill(String.valueOf(price)); }

    @Step("Enter days to dispatch: {days}")
    public void enterDaysToReady(int days) { daysToReadyInput.fill(String.valueOf(days)); }

    @Step("Toggle discount on")
    public void enableDiscount() { discountToggle.click(); }

    /** Toggle discount on, choose % Off, and enter the percent. */
    @Step("Apply percent discount: {percent}%")
    public void applyPercentDiscount(int percent) {
        enableDiscount();
        discountPercentTypeBtn.click();
        discountPercentInput.fill(String.valueOf(percent));
    }

    /** Toggle discount on, choose Flat Off, and enter the amount. */
    @Step("Apply flat discount: {amount}")
    public void applyFlatDiscount(int amount) {
        enableDiscount();
        discountFlatTypeBtn.click();
        discountFlatInput.fill(String.valueOf(amount));
    }

    /** date format: yyyy-MM-dd e.g. "2026-06-20" */
    @Step("Set offer start date: {date}")
    public void setOfferStartDate(String date) { offerStartDateInput.fill(date); }

    @Step("Set offer end date: {date}")
    public void setOfferEndDate(String date) { offerEndDateInput.fill(date); }

    @Step("Enter offer usage limit: {limit}")
    public void enterOfferUsageLimit(int limit) { offerUsageLimitInput.fill(String.valueOf(limit)); }

    /** size = Free Size | XS | S | M | L | XL | XXL | 2XL | 3XL (exact match) */
    @Step("Select size: {size}")
    public void selectSize(String size) {
        page.locator("button._sizeChip_mvcxc_533:text-is('" + size + "'), " +
                "button:text-is('" + size + "')").first().click();
        log.info("Size selected: {}", size);
    }

    @Step("Enter stock quantity: {qty}")
    public void enterStock(int qty) {
        stockInput.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        stockInput.first().fill(String.valueOf(qty));
    }

    /**
     * Status-style toggles render as two buttons; clicking the exact label selects it.
     * Defaults are usually already Active / COD Available / Allow returns / Allow replacements,
     * so call these only when you want to flip from the default.
     */
    @Step("Set product status: {exactLabel}")          // "Active" | "Inactive"
    public void setProductStatus(String exactLabel) { clickStatusOption(exactLabel); }

    @Step("Set COD: {exactLabel}")                     // "COD Available" | "COD Not Available"
    public void setCod(String exactLabel) { clickStatusOption(exactLabel); }

    @Step("Set returns: {exactLabel}")                 // "Allow returns" | "Non-returnable"
    public void setReturns(String exactLabel) { clickStatusOption(exactLabel); }

    @Step("Set replacement: {exactLabel}")             // "Allow replacements" | "Non-replaceable"
    public void setReplacement(String exactLabel) { clickStatusOption(exactLabel); }

    private void clickStatusOption(String exactLabel) {
        page.locator("button:text-is('" + exactLabel + "')").first().click();
        log.info("Status option clicked: {}", exactLabel);
    }

    /** Minimal happy-path: price, dispatch, one size + stock, accept all default flags, then Next. */
    @Step("Fill pricing & availability (step 3)")
    public void fillPricingAndAvailability(int price, int daysToReady, String size, int stock) {
        enterPrice(price);
        enterDaysToReady(daysToReady);
        selectSize(size);
        enterStock(stock);
        clickNext();
    }

    // ============================================================
    // STEP 4 — REVIEW / PUBLISH
    // ============================================================

    @Step("Publish the product (click 'Add Product')")
    public void publish() {
        completeButton.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        completeButton.click();
        log.info("Clicked 'Add Product' to publish");
    }

    /**
     * Success proxy: after publishing, the wizard closes (the complete button detaches from the DOM).
     * TODO: once you observe the real success toast/redirect on the first run, tighten this
     *       using the successToast locator for a stronger, more explicit assertion.
     */
    @Step("Verify the product was created")
    public boolean isProductCreated() {
        try {
            completeButton.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN).setTimeout(15000));
            log.info("Add Product wizard closed - creation succeeded");
            return true;
        } catch (Exception e) {
            log.error("Add Product wizard still open - creation may have failed");
            return false;
        }
    }

    // ============================================================
    // SHARED
    // ============================================================

    @Step("Click Next")
    public void clickNext() {
        nextButton.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        nextButton.click();
        log.info("Clicked Next");
    }
}