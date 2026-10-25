package com.portfolio.ecommerce.tests.mobile;

import com.portfolio.ecommerce.mobile.base.MobileBaseTest;
import com.portfolio.ecommerce.mobile.data.MobileTestData;
import com.portfolio.ecommerce.mobile.screens.CartScreen;
import com.portfolio.ecommerce.mobile.screens.HomeScreen;
import com.portfolio.ecommerce.mobile.screens.SearchScreen;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.util.List;

/**
 * iOS cart flow: add a product to the cart, change its quantity, remove it.
 *
 * The cart requires authentication — tapping Add to cart as a guest opens the sign-in sheet
 * instead of adding anything. That sign-in is a RUN-scoped fixture (ensureSignedIn), not a
 * per-test one: the whole suite shares one app session, so the first test that needs a
 * shopper signs in and everything after it inherits that.
 *
 * What each test does still own is its data: the cart is server-side and survives between
 * runs, so every test clears it first and works from a known-empty state rather than trusting
 * whatever the last run left behind.
 */
@Epic("Mobile (iOS)")
@Feature("Cart")
public class CartTest extends MobileBaseTest {

    /** Make sure we have a signed-in shopper with an empty cart, and hand back the screen. */
    private CartScreen startWithEmptyCart() {
        ensureSignedIn();
        logStep("Clear the cart so the test starts from a known state");
        return flows.emptyCart();
    }

    /**
     * Leave the shared account as we found it — ONCE, after the whole class.
     *
     * The cart is server-side, so a run that ends with items in it leaves real data behind for
     * whoever uses this account next. But doing it per test was pure waste: every test here
     * already clears the cart on the way IN, so the teardown was clearing a cart that was
     * about to be cleared again, at 13-22s a time. Per class it costs that once.
     *
     * Best-effort and silent on error: cleanup must never replace a real failure message.
     */
    @AfterClass(alwaysRun = true)
    public void emptyTheCartAfterwards() {
        if (flows != null && isSessionSignedIn()) {
            flows.clearCartQuietly();
        }
    }

    @Test(groups = {"mobile-smoke"}, description = "TC-IOS004: Adding a product puts it in the cart")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Add to cart")
    @Description("Signs in, empties the cart, adds the first product from Home and verifies the "
            + "cart holds exactly that one item with quantity 1 and offers Checkout.")
    public void addingProductPutsItInCart() {
        CartScreen cart = startWithEmptyCart();

        String productName = flows.addFirstProductToCart().name();

        logStep("Open the cart and verify the product is there");
        cart.open();
        Assert.assertTrue(cart.isLoaded(), "Cart screen should load");
        Assert.assertTrue(cart.containsProduct(productName),
                "The cart should contain the product that was added: " + productName);
        Assert.assertEquals(cart.lineItemCount(), 1, "The cart should hold exactly one line item");
        Assert.assertEquals(cart.itemCount(), 1, "The cart should report 1 item");
        Assert.assertTrue(cart.hasCheckout(), "A non-empty cart should offer Checkout");
    }

    @Test(description = "TC-IOS005: Increasing the quantity updates the cart count")
    @Severity(SeverityLevel.NORMAL)
    @Story("Change quantity")
    @Description("With one product in the cart, taps + and verifies the line quantity and the "
            + "cart's own item count both move from 1 to 2.")
    public void increasingQuantityUpdatesTheCart() {
        CartScreen cart = startWithEmptyCart();
        flows.addFirstProductToCart();

        cart.open();
        Assert.assertTrue(cart.isLoaded(), "Cart screen should load");
        Assert.assertEquals(cart.firstItemQuantity(), 1, "Quantity should start at 1");
        Assert.assertEquals(cart.itemCount(), 1, "The cart should report 1 item to begin with");

        logStep("Tap + to increase the quantity");
        cart.increaseQuantity();

        Assert.assertEquals(cart.firstItemQuantity(), 2, "The line quantity should now be 2");
        Assert.assertEquals(cart.itemCount(), 2, "The cart should now report 2 items");
        Assert.assertEquals(cart.lineItemCount(), 1,
                "Increasing the quantity should not add a second line item");
    }

    @Test(description = "TC-IOS006: Removing the only item empties the cart")
    @Severity(SeverityLevel.NORMAL)
    @Story("Remove from cart")
    @Description("With one product in the cart, removes it (confirming the dialog) and verifies "
            + "the cart is empty afterwards.")
    public void removingTheOnlyItemEmptiesTheCart() {
        CartScreen cart = startWithEmptyCart();
        String productName = flows.addFirstProductToCart().name();

        cart.open();
        Assert.assertTrue(cart.isLoaded(), "Cart screen should load");
        Assert.assertEquals(cart.lineItemCount(), 1, "Precondition: one line item in the cart");

        logStep("Remove the item");
        cart.removeFirstItem();

        Assert.assertTrue(cart.isEmpty(),
                "The cart should be empty after removing its only item (" + productName + ")");
        Assert.assertEquals(cart.itemCount(), 0, "The cart should report 0 items");
    }

    @Test(description = "TC-IOS029: The cart's item count equals the sum of its line quantities")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Cart totals")
    @Description("Cross-checks the headline 'N items' against the per-row quantities it "
            + "summarises, at one unit and again at two. The two are rendered independently, so "
            + "a headline that drifts from the rows is telling the shopper the wrong basket size.")
    public void cartItemCountEqualsTheSumOfLineQuantities() {
        CartScreen cart = startWithEmptyCart();
        flows.addFirstProductToCart();

        cart.open();
        Assert.assertTrue(cart.isLoaded(), "Cart screen should load");

        List<Integer> quantities = cart.lineQuantities();
        log.info("Line quantities {} vs headline item count {}", quantities, cart.itemCount());
        Assert.assertEquals(cart.itemCount(), sumOf(quantities),
                "The headline item count should equal the sum of the line quantities " + quantities);

        logStep("Increase the quantity and re-check the headline");
        cart.increaseQuantity();

        List<Integer> after = cart.lineQuantities();
        log.info("Line quantities {} vs headline item count {}", after, cart.itemCount());
        Assert.assertEquals(sumOf(after), 2, "Precondition: the single line should now hold 2 units");
        Assert.assertEquals(cart.itemCount(), sumOf(after),
                "The headline should still equal the sum of the line quantities " + after
                        + " after a quantity change");
    }

    private static int sumOf(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).sum();
    }

    @Test(description = "TC-IOS030: Adding a second product creates a second line item")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Add to cart")
    @Description("Adds two different products and verifies they are held as two separate lines, "
            + "and that the subtotal equals the sum of the two line prices.")
    public void addingASecondProductCreatesASecondLineItem() {
        CartScreen cart = startWithEmptyCart();

        String first = flows.addFirstProductToCart().name();
        log.info("First product: {}", first);

        logStep("Add a different product via search");
        String second = addADifferentProduct(first);
        log.info("Second product: {}", second);

        cart.open();
        Assert.assertTrue(cart.isLoaded(), "Cart screen should load");
        Assert.assertEquals(cart.lineItemCount(), 2,
                "Two different products should be held as two separate line items");
        Assert.assertEquals(cart.itemCount(), 2, "The cart should report 2 items");

        List<Integer> prices = cart.lineItemPrices();
        log.info("Line item prices: {} | subtotal: {}", prices, cart.subtotal());
        int summed = prices.stream().mapToInt(Integer::intValue).sum();
        Assert.assertEquals(cart.subtotalAmount(), summed,
                "The subtotal should equal the sum of the line prices " + prices);
    }

    @Test(description = "TC-IOS031: Increasing the quantity updates the subtotal")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Change quantity")
    @Description("Verifies the money follows the quantity: doubling one line item doubles the "
            + "subtotal. A count that moves without the total moving is the expensive bug here.")
    public void increasingQuantityUpdatesTheSubtotal() {
        CartScreen cart = startWithEmptyCart();
        flows.addFirstProductToCart();

        cart.open();
        Assert.assertTrue(cart.isLoaded(), "Cart screen should load");
        int before = cart.subtotalAmount();
        log.info("Subtotal at quantity 1: {}", before);
        Assert.assertTrue(before > 0, "Precondition: the cart should show a subtotal above zero");

        logStep("Increase the quantity to 2");
        cart.increaseQuantity();

        int after = cart.subtotalAmount();
        log.info("Subtotal at quantity 2: {}", after);
        Assert.assertEquals(after, before * 2,
                "Doubling the quantity should double the subtotal (was " + before + ")");
    }

    @Test(description = "TC-IOS032: Decreasing the quantity from two returns it to one")
    @Severity(SeverityLevel.NORMAL)
    @Story("Change quantity")
    @Description("Steps a line item up to 2 and back down, verifying the − control appears above "
            + "quantity 1 and that stepping down restores both the quantity and the subtotal.")
    public void decreasingQuantityFromTwoReturnsItToOne() {
        CartScreen cart = startWithEmptyCart();
        flows.addFirstProductToCart();

        cart.open();
        Assert.assertTrue(cart.isLoaded(), "Cart screen should load");
        int subtotalAtOne = cart.subtotalAmount();

        cart.increaseQuantity();
        Assert.assertEquals(cart.firstItemQuantity(), 2, "Precondition: quantity should be 2");

        logStep("Step the quantity back down");
        cart.decreaseQuantity();

        Assert.assertEquals(cart.firstItemQuantity(), 1, "The quantity should be back to 1");
        Assert.assertEquals(cart.subtotalAmount(), subtotalAtOne,
                "The subtotal should return to what it was at quantity 1");
        Assert.assertEquals(cart.lineItemCount(), 1,
                "Stepping down to 1 should keep the item, not remove it");
    }

    @Test(description = "TC-IOS033: An empty cart offers no checkout")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Empty cart")
    @Description("Verifies an empty cart withholds Checkout rather than letting a shopper start "
            + "an order with nothing in it.")
    public void anEmptyCartOffersNoCheckout() {
        CartScreen cart = startWithEmptyCart();

        cart.open();
        Assert.assertTrue(cart.isEmpty(), "Precondition: the cart should be empty");
        Assert.assertEquals(cart.lineItemCount(), 0, "An empty cart should hold no line items");
        Assert.assertEquals(cart.itemCount(), 0, "An empty cart should report 0 items");
        Assert.assertFalse(cart.hasCheckout(),
                "An empty cart should NOT offer Checkout — there is nothing to order");
    }

    @Test(description = "TC-IOS034: Applying an unknown coupon leaves the total unchanged")
    @Severity(SeverityLevel.NORMAL)
    @Story("Coupons")
    @Description("Types a code the backend cannot know and verifies the total is untouched. "
            + "Asserts on the money rather than on error copy, which is not verified anywhere.")
    public void applyingAnUnknownCouponLeavesTheTotalUnchanged() {
        CartScreen cart = startWithEmptyCart();
        flows.addFirstProductToCart();

        cart.open();
        Assert.assertTrue(cart.isLoaded(), "Cart screen should load");
        Assert.assertTrue(cart.hasCouponField(), "Precondition: the cart should offer a coupon box");

        int before = cart.totalAmount();
        log.info("Total before the coupon: {}", before);

        logStep("Enter an unknown coupon code: " + MobileTestData.INVALID_COUPON);
        cart.enterCoupon(MobileTestData.INVALID_COUPON);
        Assert.assertTrue(cart.isApplyEnabled(),
                "Apply should become pressable once a code has been typed (it is disabled while "
                        + "the box is empty)");

        cart.applyCoupon();

        Assert.assertEquals(cart.totalAmount(), before,
                "An unrecognised coupon must not change the total. A discount applied for a code "
                        + "the backend does not know is money given away.");
    }

    @Test(description = "TC-IOS087: Cart data stays correct after navigating away and back")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Persistence")
    @Description("Adds a product, sets its quantity to 2, then navigates away to Home and back "
            + "to the cart WITHOUT relaunching, and verifies the product, its quantity and the "
            + "subtotal are all unchanged. This is the lighter in-session counterpart to the "
            + "relaunch test — it proves tab navigation does not lose or corrupt cart state.")
    public void cartDataStaysCorrectAfterNavigatingAwayAndBack() {
        CartScreen cart = startWithEmptyCart();
        String productName = flows.addFirstProductToCart().name();

        cart.open();
        Assert.assertTrue(cart.isLoaded(), "Cart screen should load");
        cart.increaseQuantity();
        Assert.assertEquals(cart.firstItemQuantity(), 2, "Precondition: quantity should be 2");
        int subtotalBefore = cart.subtotalAmount();
        log.info("Before navigating away — subtotal: {}", subtotalBefore);

        logStep("Navigate away to Home, then to another tab, then back to the cart");
        HomeScreen home = new HomeScreen(getDriver());
        home.open();
        Assert.assertTrue(home.isLoaded(), "Home should load after leaving the cart");

        logStep("Return to the cart");
        CartScreen reopened = new CartScreen(getDriver());
        reopened.open();
        Assert.assertTrue(reopened.isLoaded(), "Cart screen should load again after returning");

        logStep("Verify the cart is unchanged");
        Assert.assertTrue(reopened.containsProduct(productName),
                "The cart should still hold '" + productName + "' after navigating away and back");
        Assert.assertEquals(reopened.lineItemCount(), 1,
                "There should still be exactly one line item after returning to the cart");
        Assert.assertEquals(reopened.firstItemQuantity(), 2,
                "The quantity should still be 2 after navigating away and back");
        Assert.assertEquals(reopened.subtotalAmount(), subtotalBefore,
                "The subtotal should be unchanged after navigating away and back");
    }

    @Test(description = "TC-IOS035: The cart survives an app relaunch")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Persistence")
    @Description("Adds a product, kills and reopens the app, signs back in and verifies the cart "
            + "still holds it — proving the cart is server-side rather than in memory.")
    public void cartSurvivesAnAppRelaunch() {
        CartScreen cart = startWithEmptyCart();
        String productName = flows.addFirstProductToCart().name();

        cart.open();
        Assert.assertEquals(cart.lineItemCount(), 1, "Precondition: one item in the cart");

        logStep("Relaunch the app (this also signs the shopper out)");
        relaunchApp();

        logStep("Sign back in and re-open the cart");
        ensureSignedIn();
        CartScreen afterRelaunch = new CartScreen(getDriver());
        afterRelaunch.open();
        Assert.assertTrue(afterRelaunch.isLoaded(), "Cart screen should load after the relaunch");

        Assert.assertTrue(afterRelaunch.containsProduct(productName),
                "The cart should still hold '" + productName + "' after an app relaunch — it "
                        + "lives on the server, not in the app's memory");
        Assert.assertEquals(afterRelaunch.lineItemCount(), 1,
                "The relaunched app should show exactly the one line item that was added");
    }

    /** Add a product that is NOT the one already in the cart, so a second line is created. */
    private String addADifferentProduct(String alreadyAdded) {
        HomeScreen home = new HomeScreen(getDriver());
        home.open();
        List<String> categories = home.categoryNames();

        // Search is the reliable way to reach a SPECIFIC second product; the search term is
        // chosen to match several so there is almost always one that is not the first.
        SearchScreen search = new SearchScreen(getDriver());
        search.open();
        search.search(MobileTestData.SEARCH_TERM);
        List<String> results = search.productResultNames();
        String different = results.stream()
                .filter(n -> !n.equalsIgnoreCase(alreadyAdded))
                .findFirst()
                .orElseThrow(() -> new SkipException(
                        "No second product available: searching '" + MobileTestData.SEARCH_TERM
                                + "' returned " + results + " and the cart already holds '"
                                + alreadyAdded + "'. Categories on Home: " + categories));

        return flows.addProductByName(different).name();
    }
}
