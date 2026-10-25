package com.portfolio.ecommerce.tests.mobile.android;

import com.portfolio.ecommerce.mobile.android.base.AndroidBaseTest;
import com.portfolio.ecommerce.mobile.android.screens.*;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Cart on Android — the counterpart to the iOS CartTest.
 *
 * Needs an authenticated session: the cart lives on the server, so a guest has nowhere to add to.
 */
@Epic("Mobile — Android")
@Feature("Cart")
public class AndroidCartTest extends AndroidBaseTest {

    private AndroidCartScreen openCartWithAProduct() {
        AndroidLoginScreen login = new AndroidLoginScreen(driver);
        Assert.assertTrue(login.ensureSignedIn(config.getCustomerEmail(), config.getCustomerPassword()),
                "Could not sign in — the cart is server-side, so nothing can be added as a guest");
        resetToHome();

        Assert.assertTrue(home.openFirstProduct(), "Could not open a product from Home");
        AndroidProductDetailScreen detail = new AndroidProductDetailScreen(driver);
        Assert.assertTrue(detail.isLoaded(), "Product detail did not load");
        detail.selectFirstAvailableSize();
        Assert.assertTrue(detail.addToCart(), "Could not add the product to the cart");

        // Reach the cart whether the build auto-navigates there or (as on the device build) stays
        // on the product page behind a snackbar. If the bottom tab bar is not showing we are on a
        // pushed screen — back out to the tabs first, then open the Cart tab explicitly.
        AndroidCartScreen cart = new AndroidCartScreen(driver);
        if (driver.findElements(AndroidHomeScreen.TAB_CART).isEmpty()) {
            for (int i = 0; i < 3 && driver.findElements(AndroidHomeScreen.TAB_CART).isEmpty(); i++) {
                driver.navigate().back();
                try { Thread.sleep(1200); } catch (InterruptedException ignored) { }
            }
        }
        Assert.assertTrue(cart.open(), "The cart tab did not open after adding a product");
        Assert.assertTrue(cart.isLoaded(), "The cart did not load after adding");
        return cart;
    }

    @Test(description = "TC-AND012: Adding a product puts it in the cart")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Cart")
    @Description("Adds a product from its detail screen and verifies a cart line appears. The "
            + "counterpart of TC-IOS004.")
    public void addingAProductPutsItInTheCart() {
        AndroidCartScreen cart = openCartWithAProduct();
        int lines = cart.itemCount();
        logStep("Cart line items: " + lines);
        Assert.assertTrue(lines > 0, "The cart holds no line items after adding a product");
    }

    @Test(description = "TC-AND087: Cart data stays correct after navigating away and back")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Cart")
    @Description("Adds a product, navigates away to Home, returns to the cart via its tab, and "
            + "verifies the line items and subtotal are unchanged. Counterpart of TC-IOS087.")
    public void cartStaysCorrectAfterNavigatingAwayAndBack() {
        AndroidCartScreen cart = openCartWithAProduct();
        int itemsBefore = cart.itemCount();
        int subtotalBefore = cart.subtotal();
        logStep("Before navigating away — items: " + itemsBefore + ", subtotal: ₹" + subtotalBefore);
        Assert.assertTrue(itemsBefore > 0, "Precondition: the cart should hold a product");

        logStep("Navigate away from the cart");
        // Leave the cart entirely. resetToHome backs out / relaunches as needed; the exact landing
        // screen varies by build (some restore the product page), which is fine — the point is we
        // are no longer looking at the cart.
        resetToHome();

        logStep("Return to the cart and verify it is unchanged");
        // open() is robust: it backs out to the tabbed world if needed before tapping the Cart tab.
        AndroidCartScreen reopened = new AndroidCartScreen(driver);
        Assert.assertTrue(reopened.open(), "The cart should reopen from its tab");

        logStep("Verify the cart is unchanged");
        Assert.assertEquals(reopened.itemCount(), itemsBefore,
                "The cart line count should be unchanged after navigating away and back");
        Assert.assertEquals(reopened.subtotal(), subtotalBefore,
                "The subtotal should be unchanged after navigating away and back");
    }

    @Test(description = "TC-AND013: The cart subtotal is a real amount")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Cart")
    @Description("Reads the subtotal from the cart summary and verifies it is a positive amount, "
            + "so a cart with items can never quietly show nothing owed.")
    public void cartSubtotalIsAPositiveAmount() {
        AndroidCartScreen cart = openCartWithAProduct();
        int subtotal = cart.subtotal();
        logStep("Subtotal: ₹" + subtotal);
        Assert.assertTrue(subtotal > 0,
                "Could not read a positive subtotal (got " + subtotal + "). -1 means the "
                        + "'Subtotal' label was not found — the summary layout may have changed.");
    }

    @Test(description = "TC-AND014: Increasing the quantity raises the subtotal")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Cart")
    @Description("Drives cart_quantity_increase and verifies the subtotal goes up — the "
            + "arithmetic a shopper is charged on. Counterpart of TC-IOS031.")
    public void increasingQuantityRaisesTheSubtotal() {
        AndroidCartScreen cart = openCartWithAProduct();

        int before = cart.subtotal();
        logStep("Subtotal before: ₹" + before);
        Assert.assertTrue(before > 0, "Could not read the starting subtotal");

        Assert.assertTrue(cart.increaseFirstQuantity(), "Could not tap cart_quantity_increase");
        int after = cart.subtotal();
        logStep("Subtotal after: ₹" + after);

        Assert.assertTrue(after > before,
                "Increasing the quantity did not raise the subtotal: ₹" + before + " -> ₹" + after);

        cart.decreaseFirstQuantity();          // leave the cart as we found it
    }

    @Test(description = "TC-AND015: An unknown coupon leaves the subtotal unchanged")
    @Severity(SeverityLevel.NORMAL)
    @Story("Cart")
    @Description("Applies a nonsense coupon and verifies nothing is discounted. Counterpart of "
            + "TC-IOS034 — the check that a bad code cannot reduce what is owed.")
    public void anUnknownCouponChangesNothing() {
        AndroidCartScreen cart = openCartWithAProduct();

        int before = cart.subtotal();
        Assert.assertTrue(before > 0, "Could not read the starting subtotal");

        logStep("Apply a coupon that does not exist");
        cart.applyCoupon("QA-NOT-A-REAL-COUPON");

        int after = cart.subtotal();
        logStep("Subtotal: ₹" + before + " -> ₹" + after);
        Assert.assertEquals(after, before,
                "An unknown coupon changed the subtotal — it must not discount anything");
    }
}
