package com.portfolio.ecommerce.tests.mobile.android;

import com.portfolio.ecommerce.mobile.android.base.AndroidBaseTest;
import com.portfolio.ecommerce.mobile.android.screens.*;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Checkout on Android — counterpart to the iOS CheckoutTest.
 *
 * NO ORDER IS EVER PLACED. checkout_place_order is asserted reachable, never tapped: orders are
 * append-only on the shared dev account and the app offers no way to undo one. The iOS suite
 * gates the same behaviour behind mobile.place.orders, off by default.
 */
@Epic("Mobile — Android")
@Feature("Checkout")
public class AndroidCheckoutTest extends AndroidBaseTest {

    private AndroidCheckoutScreen openCheckout() {
        AndroidLoginScreen login = new AndroidLoginScreen(driver);
        Assert.assertTrue(login.ensureSignedIn(config.getCustomerEmail(), config.getCustomerPassword()),
                "Could not sign in — checkout needs an authenticated session");
        resetToHome();

        Assert.assertTrue(home.openFirstProduct(), "Could not open a product");
        AndroidProductDetailScreen detail = new AndroidProductDetailScreen(driver);
        Assert.assertTrue(detail.isLoaded(), "Product detail did not load");
        detail.selectFirstAvailableSize();
        Assert.assertTrue(detail.addToCart(), "Could not add to cart");

        AndroidCartScreen cart = new AndroidCartScreen(driver);
        Assert.assertTrue(cart.isLoaded(), "The cart did not load");
        Assert.assertTrue(cart.goToCheckout(), "Could not tap cart_checkout_button");

        AndroidCheckoutScreen checkout = new AndroidCheckoutScreen(driver);
        Assert.assertTrue(checkout.isLoaded(), "Checkout did not load");
        return checkout;
    }

    @Test(description = "TC-AND016: Checkout shows a delivery address")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Checkout")
    @Description("Verifies checkout has an address to deliver to. Counterpart of the iOS "
            + "precondition in TC-IOS019 — the check that failed there for a week because the "
            + "account's address had been deleted and no test created one.")
    public void checkoutShowsADeliveryAddress() {
        AndroidCheckoutScreen checkout = openCheckout();
        Assert.assertTrue(checkout.hasDeliveryAddress(),
                "Checkout shows no delivery address. The account may have none saved — add one in "
                        + "the app under Account -> Addresses.");
    }

    @Test(description = "TC-AND017: Checkout offers a payment method")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Checkout")
    @Description("Verifies at least one payment method is offered and the commit button is "
            + "reachable. COD is per product, so which methods appear depends on the cart — this "
            + "asserts the shopper can pay somehow, not that COD specifically is present.")
    public void checkoutOffersAPaymentMethod() {
        AndroidCheckoutScreen checkout = openCheckout();

        boolean cod = checkout.isCodOffered();
        boolean online = checkout.isOnlinePaymentOffered();
        logStep("COD offered: " + cod + " | online offered: " + online);

        Assert.assertTrue(cod || online,
                "Checkout offers no payment method at all");
        Assert.assertTrue(checkout.canPlaceOrder(),
                "The place-order button is not reachable, so the order could never be committed");
    }

    @Test(description = "TC-AND018: The checkout total is the items plus fees")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Checkout")
    @Description("Reads the order summary and verifies the total is at least the item cost — the "
            + "arithmetic a shopper is charged on. Counterpart of TC-IOS036.")
    public void checkoutTotalCoversTheItems() {
        AndroidCheckoutScreen checkout = openCheckout();

        int items = checkout.items();
        int fee   = checkout.platformFee();
        int total = checkout.total();
        logStep("Items ₹" + items + " | platform fee ₹" + fee + " | total ₹" + total);

        Assert.assertTrue(items > 0, "Could not read the item cost (got " + items + ")");
        Assert.assertTrue(total >= items,
                "The total (₹" + total + ") is less than the items cost (₹" + items + ")");
        if (fee > 0) {
            Assert.assertTrue(total >= items + fee,
                    "The total (₹" + total + ") does not cover items (₹" + items + ") plus the "
                            + "platform fee (₹" + fee + ")");
        }
    }

    @Test(description = "TC-AND019: Order notes accept text before placing the order")
    @Severity(SeverityLevel.NORMAL)
    @Story("Checkout")
    @Description("Types into the notes field and verifies it accepts input. Counterpart of "
            + "TC-IOS039.")
    public void orderNotesAcceptText() {
        AndroidCheckoutScreen checkout = openCheckout();
        Assert.assertTrue(checkout.typeNote("QA automation — please ignore"),
                "The order-notes field did not accept text");
    }
}
