package com.portfolio.ecommerce.tests.mobile;

import com.portfolio.ecommerce.mobile.base.MobileBaseTest;
import com.portfolio.ecommerce.mobile.flows.ShopperFlows;
import com.portfolio.ecommerce.mobile.screens.HomeScreen;
import com.portfolio.ecommerce.mobile.screens.LoginScreen;
import com.portfolio.ecommerce.mobile.screens.ProductDetailScreen;
import com.portfolio.ecommerce.mobile.screens.WishlistScreen;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

/**
 * iOS wishlist: save a product with the heart, see it in Profile -> Wishlist, unsave it.
 *
 * As with the cart, the wishlist needs a signed-in shopper — supplied by the run-scoped
 * ensureSignedIn() fixture rather than a login per test. The wishlist itself is server-side
 * and persists between runs, so each test still clears it first.
 *
 * The guest-gate test is the odd one out: it needs the app signed OUT, so it is listed first
 * in mobile.xml (see the "Guest gates" block). Running it before anything signs in keeps the
 * suite down to a single login; run out of order it still passes, it just costs one extra
 * logout/login round trip.
 *
 * The heart is a single toggle control that reports its own state through its label:
 *   "Add to wishlist"    -> not saved
 *   "Remove from wishlist" -> saved
 * which is what these tests assert against, rather than the icon name.
 */
@Epic("Mobile (iOS)")
@Feature("Wishlist")
public class WishlistTest extends MobileBaseTest {

    /** Sign in (once per run) and empty the wishlist so the test starts from a known state. */
    private WishlistScreen startWithEmptyWishlist() {
        ensureSignedIn();
        logStep("Clear the wishlist");
        return flows.emptyWishlist();
    }

    /**
     * Leave the shared account as we found it — the wishlist is server-side and outlives the
     * run. Best-effort and silent, so it can never mask the failure that brought us here.
     *
     * Skipped in the guest state: there is no wishlist to clear when nobody is signed in, and
     * opening Profile as a guest just walks into the sign-in sheet the next test has to undo.
     */
    @AfterClass(alwaysRun = true)
    public void emptyTheWishlistAfterwards() {
        if (isSessionSignedIn()) {
            flows.clearWishlistQuietly();
        }
    }

    @Test(groups = {"mobile-smoke"}, description = "TC-IOS015: A guest tapping the heart is asked to sign in")
    @Severity(SeverityLevel.NORMAL)
    @Story("Wishlist requires sign-in")
    @Description("As a guest, taps the heart on a product card and verifies the app opens the "
            + "sign-in sheet instead of silently doing nothing.")
    public void guestTappingHeartIsAskedToSignIn() {
        ensureSignedOut();

        LoginScreen login = new LoginScreen(getDriver());
        HomeScreen home = new HomeScreen(getDriver());

        home.open();
        Assert.assertTrue(home.isLoaded(), "Home should load");

        logStep("Tap the heart on a product card while signed out");
        home.tapFirstProductHeart();

        boolean sheetShown = login.isSignInSheetShown();

        // Leave the sheet closed so the next test does not inherit a modal it knows nothing
        // about. resetToHome() also does this, but being explicit keeps the intent local.
        login.dismissSignInSheet();

        Assert.assertTrue(sheetShown,
                "Tapping the wishlist heart as a guest should open the sign-in sheet");
    }

    @Test(groups = {"mobile-smoke"}, description = "TC-IOS016: Saving a product adds it to the wishlist")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Add to wishlist")
    @Description("Signs in, empties the wishlist, saves the first product from its detail screen "
            + "and verifies the heart flips state and the product appears in Profile > Wishlist.")
    public void savingProductAddsItToTheWishlist() {
        WishlistScreen wishlist = startWithEmptyWishlist();

        ShopperFlows.OpenedProduct opened = flows.openFirstProductFromHome();
        String productName = opened.name();
        ProductDetailScreen pdp = opened.detail();
        log.info("Saving product: {}", productName);

        Assert.assertFalse(pdp.isInWishlist(),
                "Precondition: the product should not be saved yet after clearing the wishlist");

        logStep("Tap the heart to save the product");
        pdp.addToWishlist();

        Assert.assertTrue(pdp.isInWishlist(),
                "After saving, the heart should offer 'Remove from wishlist' for: " + productName);

        logStep("Open the wishlist and verify the product is there");
        wishlist.open();
        Assert.assertTrue(wishlist.isLoaded(), "The Wishlist screen should load");
        Assert.assertEquals(wishlist.productCount(), 1, "The wishlist should hold exactly one product");
        Assert.assertTrue(wishlist.containsProduct(productName),
                "The wishlist should contain the saved product: " + productName
                        + ", but held: " + wishlist.productNames());
    }

    @Test(description = "TC-IOS017: Removing the only saved product empties the wishlist")
    @Severity(SeverityLevel.NORMAL)
    @Story("Remove from wishlist")
    @Description("Saves a product, removes it from the wishlist grid and verifies the wishlist "
            + "shows its empty state afterwards.")
    public void removingSavedProductEmptiesTheWishlist() {
        WishlistScreen wishlist = startWithEmptyWishlist();

        ShopperFlows.OpenedProduct opened = flows.openFirstProductFromHome();
        String productName = opened.name();
        opened.detail().addToWishlist();

        wishlist.open();
        Assert.assertEquals(wishlist.productCount(), 1, "Precondition: one saved product");

        logStep("Remove the product from the wishlist grid");
        wishlist.removeProduct(productName);

        Assert.assertTrue(wishlist.isEmpty(),
                "The wishlist should be empty after removing its only product (" + productName
                        + "), but held: " + wishlist.productNames());

        String message = wishlist.emptyMessage();
        log.info("Empty wishlist message: {}", message);
        Assert.assertFalse(message.isBlank(),
                "An empty wishlist should explain itself rather than showing a blank screen");
    }

    @Test(description = "TC-IOS018: The heart on product detail toggles both ways")
    @Severity(SeverityLevel.NORMAL)
    @Story("Toggle the wishlist heart")
    @Description("Saves and then unsaves a product from its detail screen, verifying the heart "
            + "returns to its unsaved state and the wishlist ends up empty.")
    public void productDetailHeartTogglesBothWays() {
        WishlistScreen wishlist = startWithEmptyWishlist();

        ShopperFlows.OpenedProduct opened = flows.openFirstProductFromHome();
        String productName = opened.name();
        ProductDetailScreen pdp = opened.detail();

        logStep("Save the product");
        pdp.addToWishlist();
        Assert.assertTrue(pdp.isInWishlist(), "The product should be saved after the first tap");

        logStep("Tap again to unsave it");
        pdp.removeFromWishlist();
        Assert.assertFalse(pdp.isInWishlist(),
                "The heart should return to 'Add to wishlist' after unsaving: " + productName);

        logStep("Confirm the wishlist is empty again");
        wishlist.open();
        Assert.assertEquals(wishlist.productCount(), 0,
                "Unsaving from product detail should leave the wishlist empty, but it held: "
                        + wishlist.productNames());
    }
}
