package com.portfolio.ecommerce.mobile.android.screens;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Product detail on Android — the best-tagged screen in the app.
 *
 * Tags: size_selector, size_option_s/m/l/xl/custom, quantity_decrease, quantity_increase,
 * product_add_to_cart.
 *
 * WHAT CANNOT BE ASSERTED HERE, and why. The size options do not expose their selected state:
 * every size_option_* node reports selected="false" and checked="false" whether or not it is the
 * chosen size. So the Android equivalent of the iOS "selecting a size marks it as chosen" check
 * cannot be written against these tags — the app would need to set that semantics property.
 * Tapping a size is verifiable; the resulting selection is not.
 */
public class AndroidProductDetailScreen extends AndroidBaseScreen {

    public static final By SIZE_SELECTOR = tag("size_selector");
    public static final By ADD_TO_CART   = tag("product_add_to_cart");
    public static final By QTY_INCREASE  = tag("quantity_increase");
    public static final By QTY_DECREASE  = tag("quantity_decrease");

    /**
     * The quantity value is an untagged TextView sitting BETWEEN the two stepper buttons, so it
     * is reached structurally rather than by a tag of its own. A tag on the value itself would
     * be more robust — worth asking for.
     */
    private static final By QTY_VALUE = AppiumBy.xpath(
            "//*[@resource-id='quantity_decrease']/following-sibling::android.widget.TextView[1]");

    public AndroidProductDetailScreen(AppiumDriver driver) {
        super(driver);
    }

    @Step("Wait for the product detail screen")
    public boolean isLoaded() {
        return waitFor(ADD_TO_CART, 20000);
    }

    @Step("Check the size options are offered")
    public boolean hasSizeOptions() {
        return isPresent(SIZE_SELECTOR) && sizeOptionCount() > 0;
    }

    /** How many size chips the product offers, tagged size_option_*. */
    public int sizeOptionCount() {
        int n = 0;
        for (String s : new String[]{"s", "m", "l", "xl", "custom"}) {
            if (isPresent(tag("size_option_" + s))) n++;
        }
        return n;
    }

    /**
     * Tap a size that is actually selectable.
     *
     * Sizes the product does not stock are rendered but NOT clickable (size_option_s and
     * size_option_custom were both clickable="false" on the product used for recon), so this
     * picks the first clickable one rather than assuming a particular size exists.
     */
    /** The labels of every size the product offers that is actually selectable (in stock). */
    @Step("List the selectable sizes")
    public java.util.List<String> availableSizes() {
        java.util.List<String> sizes = new java.util.ArrayList<>();
        for (String s : new String[]{"s", "m", "l", "xl", "custom"}) {
            WebElement el = first(tag("size_option_" + s));
            if (el != null && "true".equals(el.getAttribute("clickable"))) {
                sizes.add(s.toUpperCase());
            }
        }
        return sizes;
    }

    /** Select a specific size by its label (e.g. "M"). Returns false if it is not selectable. */
    @Step("Select size {label}")
    public boolean selectSize(String label) {
        WebElement el = first(tag("size_option_" + label.toLowerCase()));
        if (el == null || !"true".equals(el.getAttribute("clickable"))) return false;
        try {
            el.click();
            pause(1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Step("Select the first available size")
    public String selectFirstAvailableSize() {
        for (String s : new String[]{"s", "m", "l", "xl"}) {
            WebElement el = first(tag("size_option_" + s));
            if (el == null) continue;
            if (!"true".equals(el.getAttribute("clickable"))) continue;
            try {
                el.click();
                pause(1200);
                return s.toUpperCase();
            } catch (Exception ignored) { }
        }
        return null;
    }

    @Step("Read the quantity")
    public int quantity() {
        WebElement el = first(QTY_VALUE);
        if (el == null) return -1;
        try {
            return Integer.parseInt(el.getText().trim());
        } catch (Exception e) {
            return -1;
        }
    }

    @Step("Increase the quantity")
    public boolean increaseQuantity() {
        return click(QTY_INCREASE);
    }

    @Step("Decrease the quantity")
    public boolean decreaseQuantity() {
        return click(QTY_DECREASE);
    }

    /**
     * Add this product to the cart.
     *
     * The app navigates to the Cart on success, so callers can go straight to AndroidCartScreen.
     */
    @Step("Add to cart")
    public boolean addToCart() {
        if (!click(ADD_TO_CART)) {
            return false;
        }
        pause(3000);
        return true;
    }

    @Step("Check Add to cart is available")
    public boolean canAddToCart() {
        WebElement el = first(ADD_TO_CART);
        return el != null && "true".equals(el.getAttribute("clickable"));
    }
}
