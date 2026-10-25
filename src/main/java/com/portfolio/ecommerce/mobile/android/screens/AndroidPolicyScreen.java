package com.portfolio.ecommerce.mobile.android.screens;

import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;

/**
 * The Privacy Policy and Terms & Conditions screens on Android (Account → LEGAL).
 *
 * Untagged; keyed on their headers ("Privacy Policy" / "Terms & Conditions"). Content renders in
 * a scrollable container, so scrollToBottom swipes through the whole document.
 */
public class AndroidPolicyScreen extends AndroidBaseScreen {

    public AndroidPolicyScreen(AppiumDriver driver) {
        super(driver);
    }

    private static By title(String heading) {
        return textContains(heading);
    }

    @Step("Wait for the {heading} screen")
    public boolean isLoaded(String heading) {
        return waitFor(title(heading), 12000);
    }

    /** True if there is body content beyond the header (more than just the title on screen). */
    @Step("Check the policy content is displayed")
    public boolean hasContent() {
        // Any sizeable amount of static text below the header indicates the body rendered.
        return all(io.appium.java_client.AppiumBy.className("android.widget.TextView")).size() > 1
                || all(io.appium.java_client.AppiumBy.className("android.view.View")).size() > 3;
    }

    /** Swipe through the document from top to bottom. Returns true (best-effort). */
    @Step("Scroll to the bottom of the policy")
    public boolean scrollToBottom() {
        for (int i = 0; i < 8; i++) {
            swipeUp();
        }
        return true;
    }
}
