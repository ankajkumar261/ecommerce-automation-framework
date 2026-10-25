package com.portfolio.ecommerce.mobile.android.screens;

import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Cart tab on Android.
 *
 * Tags: cart_item, cart_quantity_increase, cart_quantity_decrease, coupon_field, coupon_apply,
 * cart_checkout_button.
 *
 * NO cart_item_remove TAG EXISTS. It was requested and has not arrived, so removing a line is
 * not automatable yet — there is no remove control in the tree to target, by tag or otherwise.
 * The iOS equivalent (TC-IOS006, "removing the only item empties the cart") therefore has no
 * Android counterpart in this suite. Add one as soon as that tag lands.
 *
 * MONEY IS READ FROM TEXT, not from a tag. The amounts are untagged StaticTexts, so
 * amountAfter() finds the label then takes the next ₹ value in the source. That is why the
 * arithmetic checks below compare totals rather than asserting exact strings — a copy change
 * to a label would break the lookup, and it should fail loudly rather than silently read zero.
 */
public class AndroidCartScreen extends AndroidBaseScreen {

    public static final By CART_ITEM   = tag("cart_item");
    public static final By QTY_UP      = tag("cart_quantity_increase");
    public static final By QTY_DOWN    = tag("cart_quantity_decrease");
    public static final By COUPON      = tag("coupon_field");
    public static final By COUPON_APPLY = tag("coupon_apply");
    public static final By CHECKOUT    = tag("cart_checkout_button");

    public AndroidCartScreen(AppiumDriver driver) {
        super(driver);
    }

    @Step("Wait for the Cart")
    public boolean isLoaded() {
        return waitFor(AndroidHomeScreen.TAB_CART, 15000);
    }

    /**
     * Open the Cart via its bottom tab, from anywhere.
     *
     * The Cart tab only exists on the tabbed screens; coming from a pushed screen (a product
     * detail, or the cart shown as an overlay on some builds) there is no tab bar, so back out
     * until it reappears, then tap it.
     */
    @Step("Open the Cart tab")
    public boolean open() {
        for (int i = 0; i < 3 && driver.findElements(AndroidHomeScreen.TAB_CART).isEmpty(); i++) {
            driver.navigate().back();
            pause(1200);
        }
        click(AndroidHomeScreen.TAB_CART);
        return isLoaded();
    }

    @Step("Count the cart line items")
    public int itemCount() {
        waitFor(CART_ITEM, 12000);
        return all(CART_ITEM).size();
    }

    @Step("Check the cart is empty")
    public boolean isEmpty() {
        return all(CART_ITEM).isEmpty();
    }

    @Step("Increase the quantity of the first line")
    public boolean increaseFirstQuantity() {
        return click(QTY_UP);
    }

    @Step("Decrease the quantity of the first line")
    public boolean decreaseFirstQuantity() {
        return click(QTY_DOWN);
    }

    @Step("Apply the coupon: {code}")
    public boolean applyCoupon(String code) {
        var field = first(COUPON);
        if (field == null) {
            return false;
        }
        try {
            field.click();
            pause(500);
            field.sendKeys(code);
            pause(500);
        } catch (Exception e) {
            return false;
        }
        boolean applied = click(COUPON_APPLY);
        // Close the keyboard before anyone reads the summary: it covers the Subtotal row, and a
        // covered row reads as -1 — which the coupon test then reported as "the coupon changed
        // the subtotal". performEditorAction, not hideKeyboard: BACK would leave the cart.
        closeKeyboard();
        return applied;
    }

    /** Close the IME without navigating. See AndroidLoginScreen#closeKeyboard for why. */
    private void closeKeyboard() {
        try {
            if (((io.appium.java_client.HasOnScreenKeyboard) driver).isKeyboardShown()) {
                driver.executeScript("mobile: performEditorAction", java.util.Map.of("action", "done"));
                pause(1200);
            }
        } catch (Exception ignored) {
            // Nothing to close, or unsupported.
        }
    }

    @Step("Go to Checkout")
    public boolean goToCheckout() {
        return click(CHECKOUT);
    }

    /** The cart subtotal in rupees, or -1 when it cannot be read. */
    public int subtotal() {
        return amountAfter("Subtotal");
    }

    /**
     * The first ₹ amount appearing after {@code label}.
     *
     * XPath, NOT getPageSource(). The first version serialised the whole tree to the client on
     * every read, and the cart tests read several times each — UiAutomator2's instrumentation
     * then died mid-suite ("UnreachableBrowserException: it may have died"), taking every later
     * test with it. XPath is evaluated on the device and returns one node.
     *
     * Positional because the amounts carry no tag. Returns -1 rather than 0 when nothing matches,
     * so "could not read it" is never mistaken for "nothing is owed".
     */
    protected int amountAfter(String label) {
        try {
            var els = driver.findElements(By.xpath(
                    "//*[@text='" + label + "']/following::*[starts-with(@text,'₹')][1]"));
            if (els.isEmpty()) {
                return -1;
            }
            String text = els.get(0).getAttribute("text");
            if (text == null) {
                return -1;
            }
            Matcher m = Pattern.compile("₹\\s*([\\d,]+)").matcher(text);
            return m.find() ? Integer.parseInt(m.group(1).replace(",", "")) : -1;
        } catch (Exception e) {
            return -1;
        }
    }
}
