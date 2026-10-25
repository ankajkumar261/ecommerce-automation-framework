package com.portfolio.ecommerce.tests.mobile.android;

import com.portfolio.ecommerce.mobile.android.base.AndroidBaseTest;
import com.portfolio.ecommerce.mobile.android.screens.AndroidAccountScreen;
import com.portfolio.ecommerce.mobile.android.screens.AndroidPolicyScreen;
import com.portfolio.ecommerce.mobile.android.screens.AndroidSupportScreen;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Support (ticket flow), Privacy Policy and Terms & Conditions on Android — the counterpart of
 * the iOS SupportAndPolicyTest. Requires a signed-in shopper.
 *
 * Support is a ticket system: the landing lists tickets (empty here) and offers Create ticket;
 * the New-ticket form has Subject / Message / Category / Priority and a Submit ticket button.
 */
@Epic("Mobile (Android)")
@Feature("Support and Policies")
public class AndroidSupportAndPolicyTest extends AndroidBaseTest {

    private AndroidSupportScreen openSupport() {
        ensureSignedIn();
        AndroidAccountScreen account = new AndroidAccountScreen(driver);
        Assert.assertTrue(account.open(), "Account should open");
        Assert.assertTrue(account.openSupport(), "Help & Support should open from Account");
        return new AndroidSupportScreen(driver);
    }

    // ── Support ─────────────────────────────────────────────

    @Test(description = "TC-AND075: Support screen loads with a Create-ticket entry")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Support screen")
    public void supportScreenLoads() {
        AndroidSupportScreen support = openSupport();
        Assert.assertTrue(support.isLoaded(), "The Support screen should load");
        Assert.assertTrue(support.hasCreateTicketEntry(),
                "Support should offer a way to create a ticket");
    }

    @Test(description = "TC-AND076: Create-ticket form shows Subject and Message")
    @Severity(SeverityLevel.NORMAL)
    @Story("Support ticket form")
    public void createTicketFormShowsFields() {
        AndroidSupportScreen support = openSupport();
        Assert.assertTrue(support.isLoaded(), "Support should load");
        Assert.assertTrue(support.openCreateTicket(), "Create ticket should open the form");
        Assert.assertTrue(support.isTicketFormLoaded(), "The New-ticket form should load");
        Assert.assertTrue(support.hasTicketForm(),
                "The form should show Subject and Message fields");
    }

    @Test(description = "TC-AND090: An empty ticket form cannot be submitted")
    @Severity(SeverityLevel.NORMAL)
    @Story("Support ticket form")
    public void emptyTicketFormCannotBeSubmitted() {
        AndroidSupportScreen support = openSupport();
        Assert.assertTrue(support.isLoaded(), "Support should load");
        Assert.assertTrue(support.openCreateTicket(), "Create ticket should open the form");
        Assert.assertTrue(support.isTicketFormLoaded(), "The New-ticket form should load");

        logStep("Tap Submit on the empty form");
        support.submit();

        logStep("Verify no ticket was created and we stay on the form");
        Assert.assertFalse(support.isSubmitted(),
                "An empty ticket form must NOT create a ticket");
        Assert.assertTrue(support.isTicketFormLoaded(),
                "The shopper should remain on the New-ticket form after an empty submit");
    }

    // ── Privacy Policy & Terms ──────────────────────────────

    @Test(description = "TC-AND080: Privacy Policy loads, shows content and scrolls")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Privacy Policy")
    public void privacyPolicyLoadsAndScrolls() {
        ensureSignedIn();
        AndroidAccountScreen account = new AndroidAccountScreen(driver);
        Assert.assertTrue(account.open(), "Account should open");
        Assert.assertTrue(account.openPrivacyPolicy(), "Privacy Policy should open");

        AndroidPolicyScreen policy = new AndroidPolicyScreen(driver);
        Assert.assertTrue(policy.isLoaded("Privacy Policy"), "Privacy Policy should load");
        Assert.assertTrue(policy.hasContent(), "Privacy Policy should show content");
        policy.scrollToBottom();
        Assert.assertTrue(policy.isLoaded("Privacy Policy"),
                "Privacy Policy should still be loaded after scrolling");
    }

    @Test(description = "TC-AND082: Terms & Conditions loads, shows content and scrolls")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Terms and Conditions")
    public void termsLoadsAndScrolls() {
        ensureSignedIn();
        AndroidAccountScreen account = new AndroidAccountScreen(driver);
        Assert.assertTrue(account.open(), "Account should open");
        Assert.assertTrue(account.openTerms(), "Terms & Conditions should open");

        AndroidPolicyScreen policy = new AndroidPolicyScreen(driver);
        Assert.assertTrue(policy.isLoaded("Terms & Conditions"), "Terms should load");
        Assert.assertTrue(policy.hasContent(), "Terms should show content");
        policy.scrollToBottom();
        Assert.assertTrue(policy.isLoaded("Terms & Conditions"),
                "Terms should still be loaded after scrolling");
    }
}
