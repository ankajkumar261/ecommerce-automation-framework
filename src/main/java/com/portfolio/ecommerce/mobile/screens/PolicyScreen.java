package com.portfolio.ecommerce.mobile.screens;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Screen Object for Privacy Policy and Terms and Conditions screens.
 *
 * Both share the same layout pattern — a titled page with scrollable text content.
 * Reached from Profile -> Privacy Policy or Profile -> Terms and Conditions.
 *
 * Layout:
 *   title            StaticText  "Privacy Policy" or "Terms and Conditions"
 *   content          ScrollView  the full policy text (may be very long)
 *   back             Button      "BackButton"
 */
public class PolicyScreen extends BaseScreen {

    private static final By BACK = AppiumBy.accessibilityId("BackButton");

    /** Content body — a large scrollable text area. */
    private static final By CONTENT = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeStaticText' AND value.length > 50");

    /** Any StaticText element on the screen, for geometry-based scrolling checks. */
    private static final By ANY_TEXT = AppiumBy.className("XCUIElementTypeStaticText");

    public PolicyScreen(AppiumDriver driver) {
        super(driver);
    }

    @Step("Open Privacy Policy from Profile")
    public void openPrivacyPolicy() {
        ProfileScreen profile = new ProfileScreen(driver);
        profile.open();
        profile.openPrivacyPolicy();
    }

    @Step("Open Terms and Conditions from Profile")
    public void openTermsAndConditions() {
        ProfileScreen profile = new ProfileScreen(driver);
        profile.open();
        profile.openTermsAndConditions();
    }

    @Step("Wait for the {title} screen")
    public boolean isLoaded(String title) {
        return isVisible(AppiumBy.accessibilityId(title), wait)
                || isPresent(CONTENT);
    }

    @Step("Check the content body is loaded")
    public boolean hasContent() {
        return scrollIntoView(CONTENT) != null;
    }

    /**
     * Scroll from the current position to the bottom and verify content keeps loading.
     *
     * Returns true if at least one scroll revealed new content (no freeze or empty state).
     */
    @Step("Scroll to the bottom")
    public boolean scrollToBottom() {
        boolean foundNewContent = false;
        for (int i = 0; i < 15; i++) {
            // Record the current bottom-most element's y position.
            int beforeY = bottomMostElementY();

            scrollDown();
            pause(500);

            int afterY = bottomMostElementY();
            if (afterY > beforeY) {
                foundNewContent = true;
            }
            // If we cannot scroll any further, we have reached the bottom.
            if (afterY <= beforeY) {
                // Try once more to confirm.
                scrollDown();
                pause(500);
                int finalY = bottomMostElementY();
                if (finalY <= afterY) {
                    break;
                }
                foundNewContent = true;
            }
        }
        return foundNewContent;
    }

    /**
     * True if the content is tall enough to require scrolling (i.e. extends below the fold).
     */
    @Step("Check the content requires scrolling")
    public boolean contentIsScrollable() {
        java.util.List<WebElement> texts = findAll(ANY_TEXT);
        if (texts.size() < 2) return false;
        int lastY = 0;
        for (WebElement el : texts) {
            try {
                int y = el.getRect().getY();
                if (y > lastY) lastY = y;
            } catch (Exception ignored) { }
        }
        // If the last text element is below the screen midpoint, content is scrollable.
        int screenHeight = driver.manage().window().getSize().height;
        return lastY > screenHeight / 2;
    }

    @Step("Go back")
    public void goBack() {
        try { driver.findElement(BACK).click(); } catch (Exception ignored) { }
    }

    /** The y-coordinate of the lowest text element currently on screen. */
    private int bottomMostElementY() {
        int maxY = 0;
        for (WebElement el : findAll(ANY_TEXT)) {
            try {
                int y = el.getRect().getY() + el.getRect().getHeight();
                if (y > maxY) maxY = y;
            } catch (Exception ignored) { }
        }
        return maxY;
    }
}
