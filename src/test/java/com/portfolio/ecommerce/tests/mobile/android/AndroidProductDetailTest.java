package com.portfolio.ecommerce.tests.mobile.android;

import com.portfolio.ecommerce.mobile.android.base.AndroidBaseTest;
import com.portfolio.ecommerce.mobile.android.screens.AndroidProductDetailScreen;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

@Epic("Mobile — Android")
@Feature("Product detail")
public class AndroidProductDetailTest extends AndroidBaseTest {

    private AndroidProductDetailScreen openFirstProduct() {
        Assert.assertTrue(home.isLoaded(), "Home did not load");
        Assert.assertTrue(home.openFirstProduct(), "Could not open a product from Home");
        AndroidProductDetailScreen detail = new AndroidProductDetailScreen(driver);
        Assert.assertTrue(detail.isLoaded(),
                "Product detail did not load — no product_add_to_cart on screen");
        return detail;
    }

    @Test(description = "TC-AND004: Opening a product from Home shows its detail")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Product detail")
    @Description("Taps the first product card on Home and verifies the detail screen renders "
            + "with its Add to cart control — the spine every other detail test builds on.")
    public void openingAProductShowsItsDetail() {
        AndroidProductDetailScreen detail = openFirstProduct();
        Assert.assertTrue(detail.canAddToCart(),
                "Add to cart is present but not clickable on the product detail screen");
    }

    @Test(description = "TC-AND005: Product detail offers size options")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Product detail")
    @Description("Verifies the size selector renders with size chips. Note the app does not "
            + "expose which size is SELECTED — every size_option_* reports selected=false — so "
            + "this asserts the options are offered, not which one is chosen.")
    public void productDetailOffersSizeOptions() {
        AndroidProductDetailScreen detail = openFirstProduct();
        int sizes = detail.sizeOptionCount();
        logStep("Size options offered: " + sizes);
        Assert.assertTrue(detail.hasSizeOptions(),
                "No size selector on the product detail screen");
        Assert.assertTrue(sizes > 0, "The size selector rendered no size options");
    }

    @Test(description = "TC-AND006: An available size can be selected")
    @Severity(SeverityLevel.NORMAL)
    @Story("Product detail")
    @Description("Taps the first size the product actually stocks. Sizes that are out of stock "
            + "render but are not clickable, so this picks a selectable one rather than assuming "
            + "a fixed size exists.")
    public void anAvailableSizeCanBeSelected() {
        AndroidProductDetailScreen detail = openFirstProduct();
        String size = detail.selectFirstAvailableSize();
        logStep("Selected size: " + size);
        Assert.assertNotNull(size,
                "No selectable size on this product — every size_option_* was non-clickable, "
                        + "which means the product is out of stock in all sizes.");
    }

    @Test(description = "TC-AND085: Every available size can be selected one by one")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Product detail")
    @Description("Walks through every selectable size in turn and taps each, verifying each "
            + "selection is accepted and the product stays purchasable. Counterpart of TC-IOS085. "
            + "The app does not expose which size is selected, so this asserts each tap is "
            + "accepted and Add to cart remains available rather than reading a selected marker.")
    public void everyAvailableSizeCanBeSelectedOneByOne() {
        AndroidProductDetailScreen detail = openFirstProduct();
        java.util.List<String> sizes = detail.availableSizes();
        logStep("Selectable sizes: " + sizes);
        Assert.assertFalse(sizes.isEmpty(),
                "The product should offer at least one selectable size");

        for (String size : sizes) {
            logStep("Select size " + size);
            Assert.assertTrue(detail.selectSize(size),
                    "Size " + size + " is listed as selectable but could not be selected");
            Assert.assertTrue(detail.canAddToCart(),
                    "Add to cart should remain available after selecting size " + size);
        }
    }

    @Test(description = "TC-AND007: The quantity stepper increases and decreases the quantity")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Product detail")
    @Description("Drives quantity_increase and quantity_decrease and reads the value between "
            + "them, verifying the arithmetic in both directions.")
    public void quantityStepperWorksBothWays() {
        AndroidProductDetailScreen detail = openFirstProduct();

        int start = detail.quantity();
        logStep("Quantity on open: " + start);
        Assert.assertTrue(start >= 1,
                "Could not read the quantity — expected at least 1, got " + start);

        Assert.assertTrue(detail.increaseQuantity(), "Could not tap quantity_increase");
        int increased = detail.quantity();
        logStep("After increase: " + increased);
        Assert.assertEquals(increased, start + 1,
                "Increasing the quantity should add exactly one");

        Assert.assertTrue(detail.decreaseQuantity(), "Could not tap quantity_decrease");
        int decreased = detail.quantity();
        logStep("After decrease: " + decreased);
        Assert.assertEquals(decreased, start,
                "Decreasing should return the quantity to where it started");
    }
}
