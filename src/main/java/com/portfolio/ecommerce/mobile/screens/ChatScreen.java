package com.portfolio.ecommerce.mobile.screens;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen Object for the Storefront app Chat screen.
 *
 * Reached from Profile -> Chat. Layout:
 *   Conversations list:
 *     title            StaticText  "Chat" / "Messages"
 *     conversation     Button      each with storefront name, last message preview, timestamp
 *     empty state      StaticText  "No conversations yet"
 *
 *   Conversation detail:
 *     messages         StaticText  message bubbles (sent / received)
 *     input            TextField   message text field
 *     send button      Button      "Send" / paper-plane icon
 *     back             Button      "BackButton"
 */
public class ChatScreen extends BaseScreen {

    private static final By CHAT_TITLE = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeStaticText' AND (value == 'Chat' OR value == 'Messages')");

    /** A conversation row in the chat list. */
    private static final By CONVERSATION_ROW = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND (label CONTAINS 'Message' "
                    + "OR label CONTAINS 'Chat' OR label CONTAINS 'Storefront')");

    /** Message bubble — a StaticText carrying the message body. */
    private static final By MESSAGE_BUBBLE = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeStaticText' AND value != ''");

    /**
     * The message composer. A chat composer is commonly a multiline text VIEW rather than a
     * single-line text FIELD, so match either — keying only on TextField misses the composer
     * on builds that use a growing input.
     */
    private static final By INPUT = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeTextField' OR type == 'XCUIElementTypeTextView'");

    /** Send button — matches a paper-plane icon or "Send" label. */
    private static final By SEND_BUTTON = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND (name == 'paperplane' "
                    + "OR label == 'Send' OR name == 'arrow.up.circle')");

    /** Empty state when no conversations exist. */
    private static final By EMPTY_STATE = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeStaticText' AND (value CONTAINS 'No conversation' "
                    + "OR value CONTAINS 'no messages' OR value CONTAINS 'empty')");

    private static final By BACK = AppiumBy.accessibilityId("BackButton");

    public ChatScreen(AppiumDriver driver) {
        super(driver);
    }

    // ── chat list ────────────────────────────────────────────

    @Step("Open Chat from Profile")
    public void openFromProfile() {
        ProfileScreen profile = new ProfileScreen(driver);
        profile.open();
        profile.openChat();
    }

    @Step("Wait for the Chat screen")
    public boolean isLoaded() {
        boolean shown = isVisible(CHAT_TITLE, wait) || isPresent(CONVERSATION_ROW);
        // The list loads async: it renders a header and placeholder rows first, then resolves to
        // either real conversations or the empty state a beat later. Settle here so callers that
        // immediately count rows or check the empty state read the RESOLVED screen, not the
        // skeleton — otherwise a transient placeholder is miscounted as a conversation.
        pause(1200);
        return shown;
    }

    @Step("Count the conversations")
    public int conversationCount() {
        // Never report conversations while the empty state is on screen — the row locator is
        // broad and can match a placeholder during load, which would send a caller into a
        // conversation that does not exist.
        if (hasEmptyState()) return 0;
        return findAll(CONVERSATION_ROW).size();
    }

    /**
     * True when the chat list has resolved to its empty state. Checked directly (no scroll):
     * the empty-state copy is centred and on screen when present, and a scroll here would just
     * add latency to the common "no conversations" path.
     */
    @Step("Check the empty state is shown")
    public boolean hasEmptyState() {
        return isPresent(EMPTY_STATE);
    }

    /** Open a conversation by its position in the list (0-indexed). */
    @Step("Open conversation at position: {index}")
    public void openConversation(int index) {
        List<WebElement> rows = findAll(CONVERSATION_ROW);
        if (index >= rows.size()) {
            throw new NoSuchElementException(
                    "Conversation index " + index + " out of range (" + rows.size() + " conversations)");
        }
        rows.get(index).click();
        pause(1400);
    }

    /** Open the first available conversation. */
    @Step("Open the first conversation")
    public void openFirstConversation() {
        WebElement row = scrollIntoView(CONVERSATION_ROW);
        if (row == null) {
            throw new NoSuchElementException("No conversations to open");
        }
        row.click();
        pause(1400);
    }

    // ── conversation detail ──────────────────────────────────

    /** True if a message composer is on screen — i.e. a conversation is actually open. */
    @Step("Check the message composer is present")
    public boolean hasMessageInput() {
        return firstDisplayed(INPUT) != null;
    }

    @Step("Send a message: {message}")
    public void sendMessage(String message) {
        WebElement input = firstDisplayed(INPUT);
        if (input == null) {
            throw new NoSuchElementException("Message input field not found");
        }
        input.click();
        pause(300);
        input.clear();
        input.sendKeys(message);
        pause(500);
        hideKeyboard();

        WebElement send = firstDisplayed(SEND_BUTTON);
        if (send == null) {
            throw new NoSuchElementException("Send button not found");
        }
        send.click();
        pause(1500);
    }

    /** The text of the last visible message bubble. */
    @Step("Read the last message")
    public String lastMessage() {
        List<WebElement> bubbles = findAll(MESSAGE_BUBBLE);
        if (bubbles.isEmpty()) return "";
        return String.valueOf(bubbles.get(bubbles.size() - 1).getAttribute("value"));
    }

    /** The total number of visible message bubbles. */
    @Step("Count the messages")
    public int messageCount() {
        return findAll(MESSAGE_BUBBLE).size();
    }

    /** True if any message bubble contains the given text. */
    @Step("Check if message is displayed: {text}")
    public boolean isMessageDisplayed(String text) {
        if (text == null || text.isBlank()) return false;
        for (WebElement bubble : findAll(MESSAGE_BUBBLE)) {
            try {
                String value = String.valueOf(bubble.getAttribute("value"));
                if (value.contains(text)) return true;
            } catch (Exception ignored) { }
        }
        return false;
    }

    @Step("Go back")
    public void goBack() {
        WebElement back = firstDisplayed(BACK);
        if (back != null) {
            back.click();
            pause(1000);
        }
    }
}
