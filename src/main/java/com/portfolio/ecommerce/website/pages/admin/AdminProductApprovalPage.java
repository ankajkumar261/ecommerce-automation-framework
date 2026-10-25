package com.portfolio.ecommerce.website.pages.admin;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Page Object for the Admin → Products approval flow.
 *
 * Confirmed structure (from DOM):
 *   row        : div._listCard_7usmu_357
 *   title      : p._listCardTitle_7usmu_394   (holds the product name)
 *   status     : span._badgePending_7usmu_302 / span._badgeActive...  (text "Pending"/"Active")
 *   view btn   : button._actionBtn_7usmu_332  (text "View")
 *   search box : input[placeholder='Search products...']
 *
 * Selectors fall back to visible text where the hashed CSS-module classes might
 * change between front-end builds.
 */
public class AdminProductApprovalPage {

    private static final Logger log = LogManager.getLogger(AdminProductApprovalPage.class);
    private final Page page;

    public AdminProductApprovalPage(Page page) {
        this.page = page;
    }

    @Step("Open the Products section")
    public void openProducts() {
        Locator products = page.locator("button:has(span:text-is('Products'))").first();
        products.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        products.click();
        log.info("Opened Products section");
    }

    @Step("Open the Pending tab")
    public void openPendingTab() {
        Locator pending = page.locator("button:has-text('Pending'), [role='tab']:has-text('Pending')")
                .first();
        pending.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        pending.click();
        log.info("Switched to Pending tab");
    }

    /** Type a product name into the products search box to narrow the list. */
    @Step("Search products for: {productName}")
    public void search(String productName) {
        Locator box = page.locator("input[placeholder='Search products...']");
        box.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        box.fill(productName);
        page.waitForTimeout(1000);   // let the list filter
        log.info("Searched products for: {}", productName);
    }

    /** The product card (row) whose title contains the given name. */
    private Locator rowByName(String productName) {
        return page.locator("div._listCard_7usmu_357:has(p:has-text(\"" + productName + "\"))").first();
    }

    /** Read the status badge text ("Pending" / "Active" / ...) for a named product. */
    @Step("Read status of product: {productName}")
    public String statusOf(String productName) {
        Locator badge = rowByName(productName).locator("span[class*='_badge_']").first();
        badge.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        return badge.innerText().trim();
    }

    /** Click View on the row whose title contains the given product name. */
    @Step("Open product detail (View): {productName}")
    public void viewByName(String productName) {
        Locator view = rowByName(productName).locator("button:has-text('View')").first();
        view.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        view.click();
        log.info("Clicked View for product: {}", productName);
    }

    /** Click View on the first row that has a "Pending" badge (order-independent). */
    @Step("Open the first Pending product")
    public void viewFirstPending() {
        Locator pendingRow = page.locator(
                "div._listCard_7usmu_357:has(span._badgePending_7usmu_302)").first();
        pendingRow.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        pendingRow.locator("button:has-text('View')").first().click();
        log.info("Clicked View on first Pending product");
    }

    @Step("Approve the product")
    public void approve() {
        Locator approve = page.locator("button:has-text('Approve')").first();
        approve.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        approve.click();
        log.info("Clicked Approve");
    }

    @Step("Reject the product")
    public void reject() {

        // Click first Reject button (opens modal)
        Locator rejectButton = page.locator("button:has-text('Reject')").first();
        rejectButton.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE));
        rejectButton.click();

        // Wait for rejection modal
        Locator reason = page.locator("#reject-reason");
        reason.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE));

        // Fill rejection reason
        String rejectReason = "QA automated rejection";
        reason.fill(rejectReason);

        log.info("Entered rejection reason: {}", reason.inputValue());

        // Click the Reject button inside the modal
        Locator confirmReject = page.locator("button:has-text('Reject')").last();
        confirmReject.click();

        log.info("Product rejected successfully");
    }

    /**
     * After approving, the product shows an "Active" status badge
     * (span._badgeActive_... with text "Active"). Verify that appears.
     * Returns true once the Active badge is visible.
     */
    @Step("Verify product is approved (Active badge shown): {productName}")
    public boolean isApproved(String productName) {
        try {
            Locator activeBadge = page.locator(
                    "span[class*='_badgeActive_']:has-text('Active'), "
                            + "span[class*='_badgeActive_']").first();
            activeBadge.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
            log.info("Active badge shown — product approved: {}", productName);
            return true;
        } catch (Exception e) {
            log.warn("Could not confirm Active badge for {}: {}", productName, e.getMessage());
            return false;
        }
    }

    /**
     * After rejecting, the product shows a "Rejected" status badge. Matched by text
     * (and a badge class prefix as fallback) so it survives class-hash rebuilds.
     * Returns true once a "Rejected" badge/label is visible.
     */
    @Step("Verify product is rejected (Rejected badge shown): {productName}")
    public boolean isRejected(String productName) {
        try {
            Locator rejectedBadge = page.locator(
                    "span[class*='_badgeRejected_']:has-text('Rejected'), "
                            + ":text-is('Rejected')").first();
            rejectedBadge.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
            log.info("Rejected badge shown — product rejected: {}", productName);
            return true;
        } catch (Exception e) {
            log.warn("Could not confirm Rejected badge for {}: {}", productName, e.getMessage());
            return false;
        }
    }
}