package com.portfolio.ecommerce.mobile.android.screens;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;

/**
 * The Messages (chat) screen on Android (Account → Messages).
 *
 * Untagged; keyed on the header "Messages" and the "No conversations yet" empty state. The app
 * seeds chats from a product/order page, so a fresh account shows the empty state — the tests
 * skip gracefully when there is no conversation to open.
 */
public class AndroidChatScreen extends AndroidBaseScreen {

    private static final By TITLE = textContains("Messages");
    private static final By EMPTY = textContains("No conversations");
    /** A conversation row — untagged; when present, rows sit inside the list below the header. */
    private static final By CONVERSATION_ROW = AppiumBy.androidUIAutomator(
            "new UiSelector().className(\"android.view.View\").clickable(true)");
    private static final By INPUT = AppiumBy.className("android.widget.EditText");

    public AndroidChatScreen(AppiumDriver driver) {
        super(driver);
    }

    @Step("Wait for the Messages screen")
    public boolean isLoaded() {
        return waitFor(TITLE, 12000);
    }

    @Step("Check the empty-conversations state")
    public boolean hasEmptyState() {
        return isPresent(EMPTY);
    }

    @Step("Check whether a message composer is present")
    public boolean hasMessageInput() {
        return isPresent(INPUT);
    }
}
