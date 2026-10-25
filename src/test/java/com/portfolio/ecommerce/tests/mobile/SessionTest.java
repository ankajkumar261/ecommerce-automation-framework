package com.portfolio.ecommerce.tests.mobile;

import com.portfolio.ecommerce.mobile.base.MobileBaseTest;
import com.portfolio.ecommerce.mobile.screens.LoginScreen;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * iOS session lifecycle: signing out, and what does or does not survive a relaunch.
 *
 * SUITE PLACEMENT MATTERS MORE HERE THAN ANYWHERE ELSE. Every test in this class destroys the
 * run's shared login — signing out on purpose, or relaunching the app, which drops the session
 * as a side effect. They therefore belong in the LAST suite block, and the sign-out test last
 * within it. Run early, they make every following test pay for a fresh sign-in.
 *
 * They still work run out of order or on their own from the IDE: each establishes the auth
 * state it needs rather than assuming one.
 */
@Epic("Mobile (iOS)")
@Feature("Session")
public class SessionTest extends MobileBaseTest {

    @Test(description = "TC-IOS044: Signing out returns the app to the guest state")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Sign out")
    @Description("Signs out from Profile, confirming the alert, and verifies the app is genuinely "
            + "a guest afterwards — the guest banner back and an offer to sign in. The web suite "
            + "covers logout for admin and vendor; this is the mobile shopper equivalent.")
    public void signingOutReturnsTheAppToTheGuestState() {
        logStep("Make sure we are signed in, so there is something to sign out of");
        ensureSignedIn();

        LoginScreen login = new LoginScreen(getDriver());
        Assert.assertFalse(login.isGuest(),
                "Precondition: the shopper should be signed in before signing out");

        logStep("Sign out");
        ensureSignedOut();

        Assert.assertTrue(login.isGuest(),
                "After signing out the app should be back in the guest state — the guest banner "
                        + "showing, or 'Sign in / Create account' offered again");
        Assert.assertFalse(isSessionSignedIn(),
                "The run's auth flag should record that the session is gone, so a later test "
                        + "does not assume a shopper that is no longer signed in");
    }

    @Test(description = "TC-IOS045: The wishlist survives an app relaunch")
    @Severity(SeverityLevel.NORMAL)
    @Story("Persistence")
    @Description("Saves a product, kills and reopens the app, signs back in and verifies the "
            + "product is still saved — proving the wishlist is held on the server rather than "
            + "in the app's memory.")
    public void wishlistSurvivesAnAppRelaunch() {
        ensureSignedIn();
        flows.emptyWishlist();

        var opened = flows.openFirstProductFromHome();
        String productName = opened.name();
        logStep("Save '" + productName + "' to the wishlist");
        opened.detail().addToWishlist();
        Assert.assertTrue(opened.detail().isInWishlist(),
                "Precondition: the product should be saved before the relaunch");

        logStep("Relaunch the app (this also signs the shopper out)");
        relaunchApp();

        logStep("Sign back in and re-open the wishlist");
        ensureSignedIn();
        var wishlist = new com.portfolio.ecommerce.mobile.screens.WishlistScreen(getDriver());
        wishlist.open();
        Assert.assertTrue(wishlist.isLoaded(), "The Wishlist screen should load after the relaunch");

        Assert.assertTrue(wishlist.containsProduct(productName),
                "'" + productName + "' should still be saved after an app relaunch, but the "
                        + "wishlist held: " + wishlist.productNames());

        flows.clearWishlistQuietly();
    }
}
