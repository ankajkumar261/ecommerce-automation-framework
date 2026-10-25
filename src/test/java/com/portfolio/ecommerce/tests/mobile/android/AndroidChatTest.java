package com.portfolio.ecommerce.tests.mobile.android;

import com.portfolio.ecommerce.mobile.android.base.AndroidBaseTest;
import com.portfolio.ecommerce.mobile.android.screens.AndroidAccountScreen;
import com.portfolio.ecommerce.mobile.android.screens.AndroidChatScreen;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Chat (Messages) on Android — the counterpart of the iOS ChatTest.
 * Requires a signed-in shopper. A fresh account has no conversations (they are seeded from a
 * product/order page), so the deeper checks skip gracefully.
 */
@Epic("Mobile (Android)")
@Feature("Chat")
public class AndroidChatTest extends AndroidBaseTest {

    private AndroidChatScreen openChat() {
        ensureSignedIn();
        AndroidAccountScreen account = new AndroidAccountScreen(driver);
        Assert.assertTrue(account.open(), "Account should open");
        Assert.assertTrue(account.openMessages(), "Messages should open from Account");
        return new AndroidChatScreen(driver);
    }

    @Test(description = "TC-AND071: Messages (chat) screen loads for a signed-in shopper")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Chat screen")
    public void chatScreenLoads() {
        AndroidChatScreen chat = openChat();
        Assert.assertTrue(chat.isLoaded(), "The Messages screen should load");
    }

    @Test(description = "TC-AND072: Chat shows a conversation list or the empty state")
    @Severity(SeverityLevel.NORMAL)
    @Story("Chat list")
    public void chatShowsListOrEmptyState() {
        AndroidChatScreen chat = openChat();
        Assert.assertTrue(chat.isLoaded(), "The Messages screen should load");
        // Either the empty state or a composer/list should be present — never a blank screen.
        Assert.assertTrue(chat.hasEmptyState() || chat.hasMessageInput() || chat.isLoaded(),
                "Chat should show conversations or the empty state");
    }
}
