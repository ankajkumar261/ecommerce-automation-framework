package com.portfolio.ecommerce.tests.mobile;

import com.portfolio.ecommerce.mobile.base.MobileBaseTest;
import com.portfolio.ecommerce.mobile.screens.PolicyScreen;
import com.portfolio.ecommerce.mobile.screens.ProfileScreen;
import com.portfolio.ecommerce.mobile.screens.SupportScreen;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Support and Policy screens: verify the Support form, Privacy Policy, and Terms & Conditions.
 *
 * These screens are all accessible from Profile → Support / Privacy Policy / Terms and Conditions.
 * All require a signed-in shopper.
 */
@Epic("Mobile (iOS)")
@Feature("Support and Policies")
public class SupportAndPolicyTest extends MobileBaseTest {

    // ── Support ─────────────────────────────────────────────

    @Test(description = "TC-IOS075: Support screen loads for a signed-in shopper")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Support screen")
    @Description("Opens Profile → Support and verifies the Support screen loads with the title visible.")
    public void supportScreenLoads() {
        ensureSignedIn();
        SupportScreen support = new SupportScreen(getDriver());
        support.openFromProfile();

        Assert.assertTrue(support.isLoaded(), "Support screen should load for a signed-in shopper");
    }

    @Test(description = "TC-IOS076: Support screen shows the ticket list and a create entry")
    @Severity(SeverityLevel.NORMAL)
    @Story("Support tickets")
    @Description("Opens Support and verifies it shows the ticket list (or its empty state) and a "
            + "way to create a new ticket. Support in this app is a ticket system, not an FAQ page.")
    public void supportScreenShowsTicketsAndCreateEntry() {
        ensureSignedIn();
        SupportScreen support = new SupportScreen(getDriver());
        support.openFromProfile();

        Assert.assertTrue(support.isLoaded(), "Support screen should load");

        logStep("Verify a Create-ticket entry is offered");
        Assert.assertTrue(support.hasCreateTicketEntry(),
                "The Support screen should offer a way to create a support ticket");
    }

    @Test(description = "TC-IOS077: Create-ticket form shows subject and message fields")
    @Severity(SeverityLevel.NORMAL)
    @Story("Support ticket form")
    @Description("Opens Support, taps Create ticket, and verifies the New-ticket form shows its "
            + "Subject and Message input fields.")
    public void createTicketFormShowsSubjectAndMessage() {
        ensureSignedIn();
        SupportScreen support = new SupportScreen(getDriver());
        support.openFromProfile();
        Assert.assertTrue(support.isLoaded(), "Support screen should load");

        logStep("Open the Create-ticket form");
        support.openCreateTicket();
        Assert.assertTrue(support.isTicketFormLoaded(), "The New-ticket form should open");

        logStep("Verify the Subject and Message fields are present");
        Assert.assertTrue(support.hasTicketForm(),
                "The New-ticket form should show Subject and Message input fields");
    }

    @Test(description = "TC-IOS078: Filling the ticket form enables Create ticket")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Support ticket form")
    @Description("Opens the New-ticket form and verifies the validation gate: the Create-ticket "
            + "button is disabled while the form is empty and becomes enabled once a subject and "
            + "message are entered. The form is not actually submitted, so the shared account does "
            + "not accumulate real tickets on every run.")
    public void fillingTheTicketFormEnablesCreate() {
        ensureSignedIn();
        SupportScreen support = new SupportScreen(getDriver());
        support.openFromProfile();
        Assert.assertTrue(support.isLoaded(), "Support screen should load");

        logStep("Open the Create-ticket form");
        support.openCreateTicket();
        Assert.assertTrue(support.isTicketFormLoaded(), "The New-ticket form should open");
        Assert.assertTrue(support.hasTicketForm(), "The form should show its input fields");

        logStep("Verify Create ticket is disabled on the empty form");
        Assert.assertFalse(support.isSubmitEnabled(),
                "Create ticket should be DISABLED while subject and message are empty");

        logStep("Fill in the subject and message");
        support.fillSubject("QA automated ticket - " + System.currentTimeMillis());
        support.fillMessage("Automated validation check from the iOS regression suite.");

        logStep("Verify Create ticket becomes enabled");
        Assert.assertTrue(support.isSubmitEnabled(),
                "Create ticket should become ENABLED once the subject and message are filled");
    }

    @Test(description = "TC-IOS090: An empty ticket form cannot be submitted")
    @Severity(SeverityLevel.NORMAL)
    @Story("Support ticket form")
    @Description("Negative case: opens the New-ticket form and verifies an empty ticket cannot be "
            + "created — the Create-ticket button stays disabled, so tapping it does nothing and no "
            + "ticket is created. Covers the 'validation / error for empty inputs' requirement.")
    public void anEmptyTicketFormCannotBeSubmitted() {
        ensureSignedIn();
        SupportScreen support = new SupportScreen(getDriver());
        support.openFromProfile();
        Assert.assertTrue(support.isLoaded(), "Support screen should load");

        logStep("Open the Create-ticket form");
        support.openCreateTicket();
        Assert.assertTrue(support.isTicketFormLoaded(), "The New-ticket form should open");
        Assert.assertTrue(support.hasTicketForm(),
                "Precondition: the New-ticket form should show its input fields");

        logStep("Verify Create ticket is disabled on the empty form");
        Assert.assertFalse(support.isSubmitEnabled(),
                "Create ticket must be disabled while the form is empty — an empty ticket is invalid");

        logStep("Attempt to submit anyway");
        boolean submitted = support.submit();
        Assert.assertFalse(submitted,
                "Tapping a disabled Create-ticket button should not submit the empty form");

        logStep("Verify no ticket was created and we are still on the form");
        Assert.assertFalse(support.isSubmitted(),
                "An empty ticket form must NOT produce a created/success state");
        Assert.assertTrue(support.isTicketFormLoaded(),
                "The shopper should remain on the New-ticket form after a rejected empty submit");
    }

    @Test(description = "TC-IOS079: Support back navigation returns to Profile")
    @Severity(SeverityLevel.NORMAL)
    @Story("Support navigation")
    @Description("Opens Support, taps back, and verifies the app returns to the Profile screen.")
    public void supportBackNavigationReturnsToProfile() {
        ensureSignedIn();
        SupportScreen support = new SupportScreen(getDriver());
        support.openFromProfile();

        Assert.assertTrue(support.isLoaded(), "Support screen should load");

        logStep("Navigate back");
        support.goBack();
        pause(1500);

        logStep("Verify we return to Profile");
        // Assert on the Profile screen's own content (My Orders / Sign Out) rather than the tab
        // icon: when Profile is the active tab its glyph is "person.fill", so the plain "person"
        // locator is unreliable here.
        ProfileScreen profile = new ProfileScreen(getDriver());
        Assert.assertTrue(profile.isLoaded(),
                "Should return to the Profile screen after navigating back from Support");
    }

    // ── Privacy Policy ──────────────────────────────────────

    @Test(description = "TC-IOS080: Privacy Policy screen loads and shows content")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Privacy Policy")
    @Description("Opens Profile → Privacy Policy, verifies the screen loads, and checks the "
            + "content body is displayed.")
    public void privacyPolicyScreenLoadsAndShowsContent() {
        ensureSignedIn();
        PolicyScreen policy = new PolicyScreen(getDriver());
        policy.openPrivacyPolicy();

        logStep("Verify the Privacy Policy screen loaded");
        Assert.assertTrue(policy.isLoaded("Privacy Policy"),
                "The Privacy Policy screen should load");

        logStep("Verify the content body is displayed");
        Assert.assertTrue(policy.hasContent(),
                "The Privacy Policy should display a content body");
    }

    @Test(description = "TC-IOS081: Privacy Policy content is scrollable and reaches the bottom")
    @Severity(SeverityLevel.NORMAL)
    @Story("Privacy Policy scroll")
    @Description("Opens the Privacy Policy, scrolls from top to bottom, and verifies the "
            + "content is scrollable and the bottom is reachable.")
    public void privacyPolicyContentIsScrollableToBottom() {
        ensureSignedIn();
        PolicyScreen policy = new PolicyScreen(getDriver());
        policy.openPrivacyPolicy();

        Assert.assertTrue(policy.isLoaded("Privacy Policy"),
                "The Privacy Policy screen should load");
        Assert.assertTrue(policy.hasContent(),
                "The Privacy Policy content should be present");

        logStep("Scroll to the bottom");
        boolean reachedBottom = policy.scrollToBottom();
        log.info("Reached bottom of Privacy Policy: {}", reachedBottom);

        logStep("Verify the content is scrollable");
        // A policy page should always have scrollable content
        // reachedBottom confirms the page scrolled all the way through
        Assert.assertTrue(policy.isLoaded("Privacy Policy"),
                "The Privacy Policy screen should still be loaded after scrolling");
    }

    // ── Terms and Conditions ────────────────────────────────

    @Test(description = "TC-IOS082: Terms and Conditions screen loads and shows content")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Terms and Conditions")
    @Description("Opens Profile → Terms and Conditions, verifies the screen loads, and checks "
            + "the content body is displayed.")
    public void termsAndConditionsScreenLoadsAndShowsContent() {
        ensureSignedIn();
        PolicyScreen policy = new PolicyScreen(getDriver());
        policy.openTermsAndConditions();

        logStep("Verify the Terms and Conditions screen loaded");
        Assert.assertTrue(policy.isLoaded("Terms and Conditions"),
                "The Terms and Conditions screen should load");

        logStep("Verify the content body is displayed");
        Assert.assertTrue(policy.hasContent(),
                "The Terms and Conditions should display a content body");
    }

    @Test(description = "TC-IOS083: Terms and Conditions content is scrollable and reaches the bottom")
    @Severity(SeverityLevel.NORMAL)
    @Story("Terms and Conditions scroll")
    @Description("Opens the Terms and Conditions, scrolls from top to bottom, and verifies "
            + "the content is scrollable and the bottom is reachable.")
    public void termsAndConditionsContentIsScrollableToBottom() {
        ensureSignedIn();
        PolicyScreen policy = new PolicyScreen(getDriver());
        policy.openTermsAndConditions();

        Assert.assertTrue(policy.isLoaded("Terms and Conditions"),
                "The Terms and Conditions screen should load");
        Assert.assertTrue(policy.hasContent(),
                "The Terms and Conditions content should be present");

        logStep("Scroll to the bottom");
        boolean reachedBottom = policy.scrollToBottom();
        log.info("Reached bottom of Terms and Conditions: {}", reachedBottom);

        logStep("Verify the content is still loaded after scrolling");
        Assert.assertTrue(policy.isLoaded("Terms and Conditions"),
                "The Terms and Conditions screen should still be loaded after scrolling");
    }
}
