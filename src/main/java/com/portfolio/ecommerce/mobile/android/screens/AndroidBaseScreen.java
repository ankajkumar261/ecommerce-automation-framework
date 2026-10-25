package com.portfolio.ecommerce.mobile.android.screens;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;

/**
 * Shared plumbing for the ANDROID screen objects.
 *
 * Deliberately separate from the iOS BaseScreen. The iOS screens are built on XCUITest
 * locators (NSPredicate, class chains, XCUIElementType XPath) and are tuned for XCUITest's
 * costs; nothing here changes them, so the 45 iOS checks are untouched by the Android work.
 *
 * LOCATING BY TEST TAG. The app sets Compose testTag values and exposes them via
 * testTagsAsResourceId, so each tag arrives as a BARE resource-id — "product_card", not
 * "com.example.storefront:id/product_card".
 *
 * Use {@link #tag(String)} for those, NOT AppiumBy.id(): the id strategy qualifies a bare id
 * with the app package and looks for an id that does not exist. Measured on the live app —
 * AppiumBy.id found 0 elements both bare and package-prefixed, while UiSelector().resourceId
 * found them. UiSelector is also an indexed lookup rather than the whole-tree walk an
 * //*[@resource-id=...] XPath performs.
 */
public abstract class AndroidBaseScreen {

    protected final AppiumDriver driver;

    protected AndroidBaseScreen(AppiumDriver driver) {
        this.driver = driver;
    }

    /** Locator for a Compose test tag. */
    public static By tag(String testTag) {
        return AppiumBy.androidUIAutomator("new UiSelector().resourceId(\"" + testTag + "\")");
    }

    /** Locator for visible copy — only for elements the app has not tagged yet. */
    protected static By text(String value) {
        return AppiumBy.androidUIAutomator("new UiSelector().text(\"" + value + "\")");
    }

    protected List<WebElement> all(By by) {
        return driver.findElements(by);
    }

    protected boolean isPresent(By by) {
        return !driver.findElements(by).isEmpty();
    }

    /** Wait until at least one match exists, up to timeoutMs. Returns false rather than throwing. */
    protected boolean waitFor(By by, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (isPresent(by)) return true;
            pause(400);
        }
        return false;
    }

    protected WebElement first(By by) {
        List<WebElement> els = driver.findElements(by);
        return els.isEmpty() ? null : els.get(0);
    }

    protected boolean click(By by) {
        WebElement el = first(by);
        if (el == null) return false;
        try {
            el.click();
            pause(1500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    protected void pause(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException ignored) { }
    }

    protected Duration shortWait() {
        return Duration.ofSeconds(10);
    }

    // ── scrolling ─────────────────────────────────────────────
    // Compose lists are not classic RecyclerViews, so UiScrollable is unreliable against them.
    // A W3C swipe gesture works regardless of the underlying scroll container.

    /** Swipe up (content moves up → reveals what is further DOWN the screen). */
    public void swipeUp() {
        swipe(0.5, 0.75, 0.5, 0.25);
    }

    /** Swipe down (content moves down → back toward the TOP). */
    public void swipeDown() {
        swipe(0.5, 0.25, 0.5, 0.75);
    }

    private void swipe(double xStartFrac, double yStartFrac, double xEndFrac, double yEndFrac) {
        try {
            var size = driver.manage().window().getSize();
            int xs = (int) (size.width * xStartFrac);
            int ys = (int) (size.height * yStartFrac);
            int xe = (int) (size.width * xEndFrac);
            int ye = (int) (size.height * yEndFrac);
            var finger = new org.openqa.selenium.interactions.PointerInput(
                    org.openqa.selenium.interactions.PointerInput.Kind.TOUCH, "finger");
            var seq = new org.openqa.selenium.interactions.Sequence(finger, 1);
            seq.addAction(finger.createPointerMove(Duration.ZERO,
                    org.openqa.selenium.interactions.PointerInput.Origin.viewport(), xs, ys));
            seq.addAction(finger.createPointerDown(
                    org.openqa.selenium.interactions.PointerInput.MouseButton.LEFT.asArg()));
            seq.addAction(finger.createPointerMove(Duration.ofMillis(600),
                    org.openqa.selenium.interactions.PointerInput.Origin.viewport(), xe, ye));
            seq.addAction(finger.createPointerUp(
                    org.openqa.selenium.interactions.PointerInput.MouseButton.LEFT.asArg()));
            driver.perform(java.util.Collections.singletonList(seq));
            pause(600);
        } catch (Exception ignored) { }
    }

    /** Swipe up until the locator is present or maxSwipes is reached. Returns true if found. */
    protected boolean scrollUntilPresent(By by, int maxSwipes) {
        if (isPresent(by)) return true;
        for (int i = 0; i < maxSwipes; i++) {
            swipeUp();
            if (isPresent(by)) return true;
        }
        return false;
    }

    /** Locator for any element whose visible text contains the given substring. */
    protected static By textContains(String value) {
        return AppiumBy.androidUIAutomator(
                "new UiSelector().textContains(\"" + value + "\")");
    }

    /** Locator by content-description (accessibility label) substring — icons/FABs carry these. */
    protected static By descContains(String value) {
        return AppiumBy.androidUIAutomator(
                "new UiSelector().descriptionContains(\"" + value + "\")");
    }

    /** True if any of the given locators is present. */
    protected boolean anyPresent(By... locators) {
        for (By by : locators) {
            if (isPresent(by)) return true;
        }
        return false;
    }

    /** Tap the first of the given locators that is present. Returns true if one was tapped. */
    protected boolean clickAny(By... locators) {
        for (By by : locators) {
            if (isPresent(by) && click(by)) return true;
        }
        return false;
    }

    /** Scroll (by swiping up) until text is visible, then tap it. Returns true if tapped. */
    protected boolean scrollToTextAndTap(String value, int maxSwipes) {
        By by = textContains(value);
        if (scrollUntilPresent(by, maxSwipes)) {
            return click(by);
        }
        return false;
    }
}
