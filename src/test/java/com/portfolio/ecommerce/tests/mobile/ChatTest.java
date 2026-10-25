package com.portfolio.ecommerce.tests.mobile;

import com.portfolio.ecommerce.mobile.base.MobileBaseTest;
import com.portfolio.ecommerce.mobile.screens.ChatScreen;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * Chat functionality: open the chat screen, navigate into conversations, and
 * verify the chat UI renders correctly.
 *
 * Chat is accessible to signed-in shoppers from Profile → Chat. Conversations may or may
 * not exist depending on the test account and environment — tests that require data skip
 * gracefully.
 */
@Epic("Mobile (iOS)")
@Feature("Chat")
public class ChatTest extends MobileBaseTest {

    private ChatScreen openChatScreen() {
        ensureSignedIn();
        ChatScreen chat = new ChatScreen(getDriver());
        chat.openFromProfile();
        return chat;
    }

    @Test(description = "TC-IOS071: Chat screen loads for a signed-in shopper")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Chat screen")
    @Description("Opens Profile → Chat and verifies the Chat screen loads with the title visible.")
    public void chatScreenLoads() {
        ChatScreen chat = openChatScreen();
        Assert.assertTrue(chat.isLoaded(), "Chat screen should load for a signed-in shopper");
    }

    @Test(description = "TC-IOS072: Chat shows conversation list or empty state")
    @Severity(SeverityLevel.NORMAL)
    @Story("Chat list")
    @Description("Opens Chat and verifies either a list of conversations or the empty state "
            + "message is displayed.")
    public void chatShowsConversationListOrEmptyState() {
        ChatScreen chat = openChatScreen();
        Assert.assertTrue(chat.isLoaded(), "Chat screen should load");

        int count = chat.conversationCount();
        boolean empty = chat.hasEmptyState();
        log.info("Conversations: {}, empty state: {}", count, empty);

        logStep("Verify conversation list or empty state");
        Assert.assertTrue(count > 0 || empty,
                "Chat should show either a conversation list or an empty state");
    }

    @Test(description = "TC-IOS073: Opening a conversation shows message bubbles")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Open conversation")
    @Description("Opens the first conversation in the Chat screen and verifies message "
            + "bubbles are displayed. Skips if no conversations exist.")
    public void openingConversationShowsMessageBubbles() {
        ChatScreen chat = openChatScreen();
        Assert.assertTrue(chat.isLoaded(), "Chat screen should load");

        if (chat.hasEmptyState() || chat.conversationCount() == 0) {
            throw new SkipException("No conversations available in this test account");
        }

        logStep("Open the first conversation");
        chat.openFirstConversation();

        logStep("Verify message bubbles are displayed");
        int messageCount = chat.messageCount();
        log.info("Messages in conversation: {}", messageCount);
        Assert.assertTrue(messageCount > 0,
                "A conversation should contain at least one message bubble");
    }

    @Test(description = "TC-IOS084: Sending a message shows it in the conversation and it persists")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Send message")
    @Description("Opens the first conversation, sends a unique message, and verifies it appears "
            + "in the thread. Then navigates back to the chat list, reopens the same conversation, "
            + "and verifies the message is still there — proving it was persisted, not just echoed "
            + "into the local view. Skips if the account has no conversations.")
    public void sendingAMessageDisplaysItAndPersists() {
        ChatScreen chat = openChatScreen();
        Assert.assertTrue(chat.isLoaded(), "Chat screen should load");

        if (chat.hasEmptyState() || chat.conversationCount() == 0) {
            throw new SkipException("No conversations available in this test account");
        }

        logStep("Open the first conversation");
        chat.openFirstConversation();

        // Only proceed if a conversation actually opened (a composer is present). If the row did
        // not navigate — e.g. this environment has no real, replyable conversation — skip rather
        // than fail: the send-and-verify path cannot be exercised without a composer.
        if (!chat.hasMessageInput()) {
            throw new SkipException("No message composer after opening a conversation — "
                    + "this account has no replyable conversation to exercise sending");
        }

        // A unique marker so the assertion cannot pass on a pre-existing bubble.
        String message = "QA automated message " + System.currentTimeMillis();

        logStep("Send the message: " + message);
        chat.sendMessage(message);

        logStep("Verify the message is displayed in the conversation");
        Assert.assertTrue(chat.isMessageDisplayed(message),
                "The message just sent should appear as a bubble in the conversation: " + message);

        logStep("Go back to the chat list and reopen the same conversation");
        chat.goBack();
        pause(1500);
        Assert.assertTrue(chat.isLoaded(), "Should return to the chat list");
        chat.openFirstConversation();

        logStep("Verify the message persisted after leaving and returning");
        Assert.assertTrue(chat.isMessageDisplayed(message),
                "The sent message should still be present after navigating away and back: " + message);
    }

    @Test(description = "TC-IOS074: Chat back navigation from conversation returns to chat list")
    @Severity(SeverityLevel.NORMAL)
    @Story("Chat navigation")
    @Description("Opens a conversation, taps back, and verifies the app returns to the "
            + "chat list.")
    public void chatBackNavigationReturnsToList() {
        ChatScreen chat = openChatScreen();
        Assert.assertTrue(chat.isLoaded(), "Chat screen should load");

        if (chat.hasEmptyState() || chat.conversationCount() == 0) {
            throw new SkipException("No conversations available in this test account");
        }

        logStep("Open a conversation");
        chat.openFirstConversation();
        Assert.assertTrue(chat.messageCount() > 0, "Conversation should have messages");

        logStep("Navigate back");
        chat.goBack();
        pause(1500);

        logStep("Verify we return to the chat list");
        Assert.assertTrue(chat.isLoaded(),
                "Should return to the chat list after navigating back from a conversation");
    }
}
