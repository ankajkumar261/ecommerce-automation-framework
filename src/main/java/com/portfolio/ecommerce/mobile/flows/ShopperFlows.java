package com.portfolio.ecommerce.mobile.flows;

import com.portfolio.ecommerce.mobile.data.MobileTestData;
import com.portfolio.ecommerce.mobile.screens.CartScreen;
import com.portfolio.ecommerce.mobile.screens.CheckoutScreen;
import com.portfolio.ecommerce.mobile.screens.HomeScreen;
import com.portfolio.ecommerce.mobile.screens.ProductDetailScreen;
import com.portfolio.ecommerce.mobile.screens.SearchScreen;
import com.portfolio.ecommerce.mobile.screens.WishlistScreen;
import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.Assert;
import org.testng.SkipException;

/**
 * The setup journeys the mobile tests share — "get me a signed-in shopper with one product
 * in an empty cart" and friends.
 *
 * These used to be private helpers duplicated across CartTest, CheckoutTest and WishlistTest
 * (WishlistTest even passed a ProductDetailScreen[] out-param to work around not being able
 * to return two things). Collecting them here means a change to how the app is navigated is
 * one edit rather than three, and new test classes get the tricky parts — the cart-list lag,
 * the size-before-add-to-cart rule — for free.
 *
 * Deliberately NOT covered here: signing in. That is a run-scoped fixture living on
 * MobileBaseTest (ensureSignedIn), because the whole suite shares one app session and one
 * login. Call it from the test, then use these.
 *
 * These are SETUP helpers, so their assertions are preconditions: a failure means the test
 * never got to the thing it was written to check, and says so in its message.
 */
public class ShopperFlows {

    private static final Logger log = LogManager.getLogger(ShopperFlows.class);

    private final AppiumDriver driver;

    public ShopperFlows(AppiumDriver driver) {
        this.driver = driver;
    }

    /** A product that has been opened, with its detail screen — so callers get both at once. */
    public record OpenedProduct(String name, ProductDetailScreen detail) { }

    // ── starting state ────────────────────────────────────────

    /**
     * Empty the cart and hand back the screen.
     *
     * The cart is server-side and survives between runs, so any test asserting an absolute
     * count has to start here rather than trusting whatever the last run left behind.
     */
    @Step("Start with an empty cart")
    public CartScreen emptyCart() {
        CartScreen cart = new CartScreen(driver);
        cart.open();
        cart.clear();
        Assert.assertTrue(cart.isEmpty(), "Precondition: the cart should be empty at the start");
        return cart;
    }

    /** Empty the wishlist and hand back the screen. Server-side, same reasoning as the cart. */
    @Step("Start with an empty wishlist")
    public WishlistScreen emptyWishlist() {
        WishlistScreen wishlist = new WishlistScreen(driver);
        wishlist.open();
        Assert.assertTrue(wishlist.isLoaded(), "The Wishlist screen should load");
        wishlist.clear();
        Assert.assertEquals(wishlist.productCount(), 0,
                "Precondition: the wishlist should be empty at the start");
        return wishlist;
    }

    // ── opening products ──────────────────────────────────────

    /** Open the first product card on Home and return it with its detail screen. */
    @Step("Open the first product on Home")
    public OpenedProduct openFirstProductFromHome() {
        HomeScreen home = new HomeScreen(driver);
        home.open();
        Assert.assertTrue(home.isLoaded(), "Home should load");
        Assert.assertTrue(home.hasProducts(), "Precondition: the catalogue should have products");

        String name = home.openFirstProduct();
        ProductDetailScreen pdp = new ProductDetailScreen(driver);
        Assert.assertTrue(pdp.isLoaded(), "Product detail should load for: " + name);
        return new OpenedProduct(name, pdp);
    }

    /**
     * Find a product by name via Search and open it.
     *
     * Skips rather than fails when the catalogue has no match: a stale
     * checkout.cod.product is a test-data problem, and reporting it as a failure sends
     * whoever triages it looking for a script bug that isn't there.
     */
    @Step("Open the product named: {productName}")
    public OpenedProduct openProductByName(String productName) {
        SearchScreen search = new SearchScreen(driver);
        search.open();
        search.search(productName);
        if (search.productResultCount() == 0) {
            throw new SkipException("Test data missing: no search result for '" + productName
                    + "'. It may be stale for this environment.");
        }
        String opened = search.openFirstResult();

        ProductDetailScreen pdp = new ProductDetailScreen(driver);
        Assert.assertTrue(pdp.isLoaded(), "Product detail should load for: " + opened);
        return new OpenedProduct(opened, pdp);
    }

    // ── filling the cart ──────────────────────────────────────

    /** Open the first product on Home and add one unit of the default size to the cart. */
    @Step("Add the first product on Home to the cart")
    public OpenedProduct addFirstProductToCart() {
        return addFirstProductToCart(MobileTestData.DEFAULT_SIZE);
    }

    @Step("Add the first product on Home to the cart (size {size})")
    public OpenedProduct addFirstProductToCart(String size) {
        OpenedProduct product = openFirstProductFromHome();
        log.info("Adding product: {}", product.name());
        addOpenProductToCart(product, size);
        return product;
    }

    /** Find a product by name, then add one unit of the default size to the cart. */
    @Step("Add {productName} to the cart")
    public OpenedProduct addProductByName(String productName) {
        return addProductByName(productName, MobileTestData.DEFAULT_SIZE);
    }

    @Step("Add {productName} to the cart (size {size})")
    public OpenedProduct addProductByName(String productName, String size) {
        OpenedProduct product = openProductByName(productName);
        addOpenProductToCart(product, size);
        return product;
    }

    /**
     * Add a product whose detail screen is already open.
     *
     * The size has to be chosen first — Add to cart does not appear until it is, which is the
     * single commonest reason a naive add-to-cart step does nothing at all.
     */
    @Step("Add the open product to the cart (size {size})")
    public void addOpenProductToCart(OpenedProduct product, String size) {
        ProductDetailScreen pdp = product.detail();
        pdp.selectSize(size);
        pdp.tapAddToCart();
        Assert.assertTrue(pdp.isAddedToCart(),
                "After adding, the button should turn into 'View cart' for: " + product.name());
    }

    // ── checkout ──────────────────────────────────────────────

    /**
     * Open the cart and continue to Checkout.
     *
     * Always opens the cart FIRST. Counting line items before navigating is wrong: the
     * product-detail screen has its own quantity stepper, so lineItemCount() is non-zero there
     * too and a "skip if already populated" check would never leave the PDP.
     *
     * The retry loop absorbs the cart list lagging the server right after an add — bouncing
     * off Home forces it to refetch.
     */
    @Step("Go to Checkout")
    public CheckoutScreen goToCheckout(CartScreen cart) {
        cart.open();
        Assert.assertTrue(cart.isLoaded(), "The Cart screen should load");

        for (int i = 0; i < 3 && cart.lineItemCount() == 0; i++) {
            log.info("  → Cart list still empty — bouncing off Home and re-opening");
            new HomeScreen(driver).open();
            cart.open();
        }
        Assert.assertTrue(cart.lineItemCount() > 0, "Precondition: the cart should hold an item");
        Assert.assertTrue(cart.hasCheckout(), "A non-empty cart should offer Checkout");

        cart.tapCheckout();

        CheckoutScreen checkout = new CheckoutScreen(driver);
        Assert.assertTrue(checkout.isLoaded(), "The Checkout screen should load");
        return checkout;
    }

    // ── teardown ──────────────────────────────────────────────

    /**
     * Best-effort cart clear for @AfterMethod.
     *
     * Swallows everything on purpose: cleanup runs after a test has already failed, and an
     * exception thrown here would replace the real assertion message with a teardown error —
     * hiding the very thing the test was written to report.
     */
    public void clearCartQuietly() {
        try {
            CartScreen cart = new CartScreen(driver);
            cart.open();
            cart.clear();
        } catch (Exception e) {
            log.warn("Could not clear the cart during teardown: {}", e.getMessage());
        }
    }

    /** Best-effort wishlist clear for @AfterMethod. Same reasoning as clearCartQuietly. */
    public void clearWishlistQuietly() {
        try {
            WishlistScreen wishlist = new WishlistScreen(driver);
            wishlist.open();
            wishlist.clear();
        } catch (Exception e) {
            log.warn("Could not clear the wishlist during teardown: {}", e.getMessage());
        }
    }
}
