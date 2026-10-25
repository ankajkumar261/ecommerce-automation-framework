package com.portfolio.ecommerce.tests.mobile;

import com.portfolio.ecommerce.mobile.base.MobileBaseTest;
import com.portfolio.ecommerce.mobile.screens.AddressScreen;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Delivery addresses.
 *
 * This exists because checkout depends on the shopper having a saved address, and until now
 * nothing created one — it was account state maintained by hand. When it was deleted from the
 * dev account, TC-IOS019 failed with "the shopper should have a saved delivery address" and read
 * like a product defect for the second time. Creating the address in a test both covers the
 * feature and removes that false alarm.
 *
 * ORDERING MATTERS. Run this before the checkout block (see mobile-regression.xml) so the
 * address exists by the time checkout looks for it.
 */
@Epic("Mobile — iOS")
@Feature("Account")
public class AddressTest extends MobileBaseTest {

    private static final String NAME     = "QA Automation";
    private static final String PHONE    = "9876543210";
    private static final String HOUSE    = "302";
    private static final String BUILDING = "QA Residency";
    private static final String LOCALITY = "Fatehpur";
    private static final String CITY     = "Kangra";
    private static final String STATE    = "Himachal Pradesh";
    private static final String PINCODE  = "176063";

    @Test(description = "TC-IOS046: A shopper can save a delivery address")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Delivery addresses")
    @Description("Adds a delivery address from Profile -> Addresses and verifies it is listed "
            + "afterwards. Doubles as the precondition for every checkout test: those assert an "
            + "address exists but none of them creates one.")
    public void shopperCanSaveADeliveryAddress() {
        ensureSignedIn();

        AddressScreen addresses = new AddressScreen(driver);

        logStep("Open Profile -> Addresses");
        Assert.assertTrue(addresses.open(),
                "Could not reach Profile -> Addresses");
        Assert.assertTrue(addresses.isLoaded(), "The Addresses screen did not load");

        int before = addresses.savedAddressCount();
        logStep("Saved addresses before: " + before);

        logStep("Open the Add address sheet");
        Assert.assertTrue(addresses.openAddSheet(),
                "The Add address sheet did not open — no 'Recipient name' field appeared");

        logStep("Fill in the address");
        addresses.fillForm(NAME, PHONE, HOUSE, BUILDING, LOCALITY, CITY, STATE, PINCODE);

        // The form marks the GPS pin "Required for delivery", so set it before saving. Needs the
        // simulator to have a location:  xcrun simctl location <udid> set 32.0998,76.2691
        logStep("Set the GPS pin");
        addresses.useCurrentLocation();

        logStep("Save");
        Assert.assertTrue(addresses.save(),
                "The address was not saved — the Add address sheet stayed open, which is how it "
                        + "reports a validation error. Check the GPS pin: the form requires one.");

        logStep("Verify it is listed");
        Assert.assertTrue(addresses.hasSavedAddress(),
                "No saved address on the Addresses screen after saving one");
        Assert.assertTrue(addresses.isAddressListed(PINCODE),
                "The saved address is not listed. Expected a row containing '" + PINCODE + "'.");

        int after = addresses.savedAddressCount();
        logStep("Saved addresses after: " + after);
        Assert.assertTrue(after > before,
                "The address count did not increase: " + before + " -> " + after);
    }

    @Test(description = "TC-IOS047: A saved address is offered at checkout",
          dependsOnMethods = "shopperCanSaveADeliveryAddress")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Delivery addresses")
    @Description("Verifies the address just saved is the one checkout picks up — the join between "
            + "the account screen and the buying flow, and the precondition TC-IOS019 asserts.")
    public void aSavedAddressIsOfferedAtCheckout() {
        ensureSignedIn();

        logStep("Put the configured COD product in the cart and open checkout");
        var cart = flows.emptyCart();
        flows.addProductByName(config.getCodProduct());
        var checkout = flows.goToCheckout(cart);

        logStep("Verify checkout sees a delivery address");
        Assert.assertTrue(checkout.hasDeliveryAddress(),
                "Checkout does not see a delivery address even though one was just saved. If "
                        + "TC-IOS046 passed, the address is saved but checkout is not picking it "
                        + "up — that would be a product issue, not a test one.");

        String shown = checkout.deliveryAddress();
        logStep("Checkout shows: " + shown);
        Assert.assertTrue(shown.contains(PINCODE),
                "Checkout is using a different address than the one saved. Expected it to contain "
                        + "'" + PINCODE + "', got: '" + shown + "'");
    }
}
