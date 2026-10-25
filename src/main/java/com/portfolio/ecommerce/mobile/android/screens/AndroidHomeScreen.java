package com.portfolio.ecommerce.mobile.android.screens;

import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;

/**
 * Home and the bottom tab bar on Android.
 *
 * Tags the app exposes here: bottom_tab_home / _shop / _cart / _search, product_card,
 * product_wishlist. The top-bar Cart and Profile icons, the search box, the category cards and
 * "View all" are NOT tagged, so this screen object cannot reach them — which is why there is no
 * openCategory() or openProfile() below.
 */
public class AndroidHomeScreen extends AndroidBaseScreen {

    public static final By TAB_HOME   = tag("bottom_tab_home");
    public static final By TAB_SHOP   = tag("bottom_tab_shop");
    public static final By TAB_CART   = tag("bottom_tab_cart");
    public static final By TAB_SEARCH = tag("bottom_tab_search");

    public static final By PRODUCT_CARD     = tag("product_card");
    public static final By PRODUCT_WISHLIST = tag("product_wishlist");

    public AndroidHomeScreen(AppiumDriver driver) {
        super(driver);
    }

    @Step("Wait for Home")
    public boolean isLoaded() {
        return waitFor(TAB_HOME, 20000);
    }

    @Step("Count the product cards on screen")
    public int productCardCount() {
        // Home loads its products over the network; give them a moment before counting, or an
        // early count reports zero on a screen that is merely still loading.
        waitFor(PRODUCT_CARD, 20000);
        return all(PRODUCT_CARD).size();
    }

    @Step("Check the bottom tab bar is complete")
    public boolean hasAllTabs() {
        return isPresent(TAB_HOME) && isPresent(TAB_SHOP)
                && isPresent(TAB_CART) && isPresent(TAB_SEARCH);
    }

    @Step("Open the first product on Home")
    public boolean openFirstProduct() {
        return waitFor(PRODUCT_CARD, 20000) && click(PRODUCT_CARD);
    }

    @Step("Tap the wishlist heart on the first product card")
    public boolean tapFirstWishlistHeart() {
        return waitFor(PRODUCT_WISHLIST, 20000) && click(PRODUCT_WISHLIST);
    }

    public static final By CATEGORY_CARD = tag("category_card");

    @Step("Count the category tiles")
    public int categoryCount() {
        waitFor(CATEGORY_CARD, 15000);
        return all(CATEGORY_CARD).size();
    }

    @Step("Open the first category")
    public boolean openFirstCategory() {
        return waitFor(CATEGORY_CARD, 15000) && click(CATEGORY_CARD);
    }

    @Step("Open the Search tab")
    public boolean openSearchTab() {
        return click(TAB_SEARCH);
    }

    @Step("Go to the Home tab")
    public boolean goHome() {
        return click(TAB_HOME);
    }
}
