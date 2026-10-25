package com.portfolio.ecommerce.tests.mobile;

import com.portfolio.ecommerce.mobile.base.MobileBaseTest;
import com.portfolio.ecommerce.mobile.data.MobileTestData;
import com.portfolio.ecommerce.mobile.flows.ShopperFlows;
import com.portfolio.ecommerce.mobile.screens.CartScreen;
import com.portfolio.ecommerce.mobile.screens.CheckoutScreen;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

/**
 * iOS checkout: what the Checkout screen offers for a given cart.
 *
 * The shape of these tests is driven by one fact about the product model: COD is a
 * PER-PRODUCT flag (codEnabled). A cart holding any non-COD item offers only "Pay online"
 * (Razorpay) and says so explicitly. Since the online path cannot be automated without a
 * test-mode gateway key, the COD tests add the one configured COD-eligible product by name
 * (checkout.cod.product) rather than "the first product on Home".
 *
 * NOTE: none of these tests place an order — they assert what checkout OFFERS. Actually
 * committing a COD order is a separate test, because it writes a real order to the backend.
 *
 * The shopper is signed in once for the whole run (ensureSignedIn), so what these tests set up
 * for themselves is only the cart contents.
 */
@Epic("Mobile (iOS)")
@Feature("Checkout")
public class CheckoutTest extends MobileBaseTest {

    /** Make sure we have a signed-in shopper with an empty cart. */
    private CartScreen signInWithEmptyCart() {
        ensureSignedIn();
        logStep("Clear the cart");
        return flows.emptyCart();
    }

    /**
     * These tests deliberately END with a populated cart — they stop at what checkout offers
     * rather than committing. Without this the cart would still hold that product after the
     * run, on an account shared with manual QA and the web suite.
     */
    @AfterClass(alwaysRun = true)
    public void emptyTheCartAfterwards() {
        if (flows != null && isSessionSignedIn()) {
            flows.clearCartQuietly();
        }
    }

    @Test(groups = {"mobile-smoke"}, description = "TC-IOS019: A COD-eligible product offers Cash on Delivery at checkout")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Checkout payment options")
    @Description("Adds the configured COD-eligible product, opens checkout and verifies it has a "
            + "delivery address, offers Cash on Delivery, and that selecting COD turns the commit "
            + "button into 'Place order'. Stops short of placing the order.")
    public void codEligibleProductOffersCashOnDelivery() {
        CartScreen cart = signInWithEmptyCart();

        String product = flows.addProductByName(config.getCodProduct()).name();
        CheckoutScreen checkout = flows.goToCheckout(cart);

        logStep("Verify the checkout preconditions");
        Assert.assertTrue(checkout.hasDeliveryAddress(),
                "The shopper should have a saved delivery address to check out with");

        Assert.assertTrue(checkout.isCodAvailable(),
                "'" + product + "' is configured as COD-eligible, so checkout should offer "
                        + "Cash on delivery. If this fails, codEnabled may have been turned off "
                        + "for it — check checkout.cod.product for this environment.");
        Assert.assertFalse(checkout.hasOnlineOnlyNotice(),
                "A COD-eligible cart should NOT show the online-only notice");

        logStep("Select Cash on delivery");
        checkout.selectCod();

        Assert.assertTrue(checkout.canPlaceCodOrder(),
                "With COD selected the commit button should read 'Place order'");
        Assert.assertTrue(checkout.placeOrderCaption().startsWith("Place order"),
                "The commit button should be the COD one, not the online-payment 'Pay ₹...' "
                        + "button. Actual caption: '" + checkout.placeOrderCaption() + "'");
    }

    @Test(description = "TC-IOS020: An online-only product suppresses Cash on Delivery")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Checkout payment options")
    @Description("Adds a product that is not COD-eligible and verifies checkout withholds Cash on "
            + "Delivery, shows the online-only notice, and offers online payment instead — the "
            + "safety invariant that keeps automation off the real-money Razorpay path.")
    public void onlineOnlyProductSuppressesCashOnDelivery() {
        CartScreen cart = signInWithEmptyCart();

        logStep("Add the first product on Home (the catalogue is overwhelmingly non-COD)");
        ShopperFlows.OpenedProduct opened = flows.openFirstProductFromHome();
        String product = opened.name();

        if (product != null && product.equalsIgnoreCase(config.getCodProduct())) {
            throw new SkipException("The first product on Home is the COD-eligible one ('"
                    + product + "'), so it cannot exercise the online-only path.");
        }

        flows.addOpenProductToCart(opened, MobileTestData.DEFAULT_SIZE);

        CheckoutScreen checkout = flows.goToCheckout(cart);

        logStep("Verify COD is withheld for a non-COD product");
        Assert.assertFalse(checkout.isCodAvailable(),
                "'" + product + "' is not COD-eligible, so checkout must NOT offer Cash on "
                        + "delivery");
        Assert.assertTrue(checkout.hasOnlineOnlyNotice(),
                "Checkout should explain why COD is unavailable via the online-only notice");
        Assert.assertTrue(checkout.isOnlinePaymentAvailable(),
                "Online payment should be offered as the only way to pay");
    }

    @Test(description = "TC-IOS036: The checkout total is items plus shipping plus platform fee")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Order summary")
    @Description("Checks checkout's arithmetic end to end: the line items, shipping and platform "
            + "fee must add up to the stated Total, and the Total must equal the amount printed "
            + "on the button the shopper actually commits with.")
    public void checkoutTotalIsTheSumOfItemsShippingAndPlatformFee() {
        CartScreen cart = signInWithEmptyCart();
        flows.addProductByName(config.getCodProduct());
        CheckoutScreen checkout = flows.goToCheckout(cart);

        int items    = checkout.itemsTotal();
        int shipping = checkout.shippingAmount();
        int fee      = checkout.platformFee();
        int total    = checkout.total();
        log.info("Items {} + shipping {} + platform fee {} =? total {}", items, shipping, fee, total);

        Assert.assertTrue(items > 0, "Checkout should show an items total above zero");
        Assert.assertTrue(shipping >= 0,
                "Shipping should be readable — 'Free' counts as 0, but a blank row does not");
        Assert.assertTrue(fee >= 0, "The platform fee should be readable");

        Assert.assertEquals(total, items + shipping + fee,
                "The stated Total should equal items + shipping + platform fee. A summary that "
                        + "does not add up is the shopper being charged something they were not shown.");

        logStep("Select Cash on delivery and compare the commit button against the summary");
        checkout.selectCod();
        Assert.assertEquals(checkout.commitButtonAmount(), total,
                "The amount on the commit button should equal the Total in the summary. These are "
                        + "rendered separately, so a mismatch means the shopper agrees to one "
                        + "figure while the summary shows another. Button read: '"
                        + checkout.placeOrderCaption() + "'");
    }

    @Test(description = "TC-IOS037: Checkout shows the item count from the cart")
    @Severity(SeverityLevel.NORMAL)
    @Story("Order summary")
    @Description("Verifies checkout's 'Items (N)' tracks what the cart actually holds, at one "
            + "unit and again at two.")
    public void checkoutShowsTheItemCountFromTheCart() {
        CartScreen cart = signInWithEmptyCart();
        flows.addProductByName(config.getCodProduct());

        CheckoutScreen checkout = flows.goToCheckout(cart);
        Assert.assertEquals(checkout.itemCount(), 1,
                "Checkout should report 1 item for a cart holding one unit");

        logStep("Go back to the cart and add a second unit");
        cart.open();
        Assert.assertTrue(cart.isLoaded(), "Cart screen should load");
        cart.increaseQuantity();
        Assert.assertEquals(cart.itemCount(), 2, "Precondition: the cart should now hold 2 units");

        CheckoutScreen recheck = flows.goToCheckout(cart);
        Assert.assertEquals(recheck.itemCount(), 2,
                "Checkout should follow the cart to 2 items — a stale count here means the "
                        + "shopper reviews an order that is not the one they built");
    }

    @Test(description = "TC-IOS038: Selecting Cash on delivery marks it as the chosen payment method")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Checkout payment options")
    @Description("Verifies the selected-payment radio actually moves onto the COD row. TC-IOS019 "
            + "checks the commit button changes; this checks the screen SHOWS which method is "
            + "selected, so a shopper can tell what they are about to agree to.")
    public void selectingCashOnDeliveryMarksItAsTheChosenPaymentMethod() {
        CartScreen cart = signInWithEmptyCart();
        flows.addProductByName(config.getCodProduct());
        CheckoutScreen checkout = flows.goToCheckout(cart);

        Assert.assertTrue(checkout.isCodAvailable(),
                "Precondition: the configured COD product should offer Cash on delivery");

        logStep("Select Cash on delivery");
        checkout.selectCod();

        String selected = checkout.selectedPaymentMethod();
        log.info("Selected payment method reads as: '{}'", selected);
        Assert.assertEquals(selected, "cod",
                "The filled radio should sit on the Cash-on-delivery row after selecting it. "
                        + "Reading '" + selected + "' means the screen does not show the shopper "
                        + "which method is active.");
    }

    @Test(description = "TC-IOS039: Order notes accept text before placing the order")
    @Severity(SeverityLevel.NORMAL)
    @Story("Order notes")
    @Description("Types into the optional notes box and verifies it takes the text and does not "
            + "block the order — an optional field that disables the commit button is a defect.")
    public void orderNotesAcceptTextBeforePlacingTheOrder() {
        CartScreen cart = signInWithEmptyCart();
        flows.addProductByName(config.getCodProduct());
        CheckoutScreen checkout = flows.goToCheckout(cart);
        checkout.selectCod();

        String note = MobileTestData.uniqueTag() + " please leave at reception";
        logStep("Enter an order note");
        checkout.enterNotes(note);

        Assert.assertEquals(checkout.notes(), note,
                "The notes box should hold exactly what was typed");
        Assert.assertTrue(checkout.canPlaceCodOrder(),
                "Notes are optional, so filling them in must not withdraw the Place-order button");
    }

    @Test(description = "TC-IOS040: Checkout cannot be reached with an empty cart")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Empty cart")
    @Description("Verifies an empty cart offers no route to checkout, so an order can never be "
            + "started with nothing in it.")
    public void checkoutCannotBeReachedWithAnEmptyCart() {
        CartScreen cart = signInWithEmptyCart();

        cart.open();
        Assert.assertTrue(cart.isEmpty(), "Precondition: the cart should be empty");
        Assert.assertFalse(cart.hasCheckout(),
                "An empty cart should not offer Checkout at all");

        CheckoutScreen checkout = new CheckoutScreen(getDriver());
        Assert.assertFalse(checkout.isLoaded(),
                "The Checkout screen should not be reachable from an empty cart");
    }
}
