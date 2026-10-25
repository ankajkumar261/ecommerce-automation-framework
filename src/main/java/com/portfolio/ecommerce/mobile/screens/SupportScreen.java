package com.portfolio.ecommerce.mobile.screens;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;

/**
 * Screen Object for the Storefront app Support screen.
 *
 * Reached from Profile -> Support. Support is a TICKET system, not an FAQ + inline contact
 * form. Layout confirmed against the live dev build:
 *
 *   Landing (ticket list):
 *     title            NavigationBar / StaticText  "Support"
 *     empty state      StaticText  "No tickets yet" / "Create one if you need help..."
 *     create entry     Button      "Create ticket"  and a plus "New ticket"
 *
 *   New-ticket form (opened via the create entry):
 *     title            StaticText  "New ticket"
 *     subject          TextField   placeholder "Briefly, what do you need help with?"
 *     message          TextField   placeholder "Share a bit more context…"  (0/3000 counter)
 *     category         Button      "General" (+ others)
 *     priority         Buttons     "Low" / "Medium" / "High"
 *     submit           Button      "Create ticket" — DISABLED until subject + message are filled
 *     back             Button      "BackButton"
 *
 * The disabled Submit is the form's validation mechanism: an empty ticket cannot be created.
 */
public class SupportScreen extends BaseScreen {

    private static final By SUPPORT_TITLE = AppiumBy.iOSNsPredicateString(
            "(type == 'XCUIElementTypeStaticText' OR type == 'XCUIElementTypeNavigationBar') "
                    + "AND (value == 'Support' OR name == 'Support')");

    /** Empty state on the ticket list when the account has no tickets. */
    private static final By NO_TICKETS = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeStaticText' AND (value CONTAINS 'No tickets' "
                    + "OR value CONTAINS 'no tickets')");

    /** The entry points that open the New-ticket form (a labelled button and a plus icon). */
    private static final By CREATE_TICKET_ENTRY = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND (label == 'Create ticket' "
                    + "OR label == 'New ticket' OR name == 'plus')");

    // ── New-ticket form ──────────────────────────────────────
    private static final By FORM_TITLE = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeStaticText' AND value == 'New ticket'");

    /** The subject input — matched on its placeholder. */
    private static final By SUBJECT_FIELD = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeTextField' AND placeholderValue CONTAINS 'Briefly'");

    /** The message/body input — matched on its placeholder. */
    private static final By MESSAGE_FIELD = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeTextField' AND placeholderValue CONTAINS 'more context'");

    /** The form's submit control. Present on the form; its enabled state is the validation gate. */
    private static final By SUBMIT_BUTTON = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND label == 'Create ticket'");

    /** Confirmation / success after creating a ticket. */
    private static final By CONFIRMATION = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeStaticText' AND (value CONTAINS 'created' "
                    + "OR value CONTAINS 'submitted' OR value CONTAINS 'Thank' "
                    + "OR value CONTAINS 'received' OR value CONTAINS 'Open' "
                    + "OR value CONTAINS 'Pending')");

    private static final By BACK = AppiumBy.accessibilityId("BackButton");

    public SupportScreen(AppiumDriver driver) {
        super(driver);
    }

    @Step("Open Support from Profile")
    public void openFromProfile() {
        ProfileScreen profile = new ProfileScreen(driver);
        profile.open();
        profile.openSupport();
    }

    @Step("Wait for the Support screen")
    public boolean isLoaded() {
        return isVisible(SUPPORT_TITLE, wait);
    }

    /** True if the ticket list is empty ("No tickets yet"). */
    @Step("Check the empty ticket state is shown")
    public boolean hasEmptyState() {
        return isPresent(NO_TICKETS);
    }

    /** True if the screen offers a way to create a new support ticket. */
    @Step("Check a Create-ticket entry is available")
    public boolean hasCreateTicketEntry() {
        return scrollIntoView(CREATE_TICKET_ENTRY) != null;
    }

    /** Open the New-ticket form from the Support landing screen. */
    @Step("Open the Create-ticket form")
    public void openCreateTicket() {
        WebElement entry = scrollIntoView(CREATE_TICKET_ENTRY);
        if (entry == null) {
            throw new NoSuchElementException("No 'Create ticket' entry on the Support screen");
        }
        entry.click();
        pause(1500);
    }

    /** True once the New-ticket form is on screen. */
    @Step("Wait for the New-ticket form")
    public boolean isTicketFormLoaded() {
        return isVisible(FORM_TITLE, wait) || isPresent(SUBJECT_FIELD);
    }

    /** True if the New-ticket form shows both its Subject and Message inputs. */
    @Step("Check the ticket form fields are visible")
    public boolean hasTicketForm() {
        return scrollIntoView(SUBJECT_FIELD) != null && scrollIntoView(MESSAGE_FIELD) != null;
    }

    @Step("Fill the ticket subject: {subject}")
    public void fillSubject(String subject) {
        WebElement field = scrollIntoView(SUBJECT_FIELD);
        if (field == null) {
            throw new NoSuchElementException("Subject field not found on the New-ticket form");
        }
        field.click();
        pause(300);
        field.clear();
        field.sendKeys(subject);
        pause(300);
        hideKeyboard();
    }

    @Step("Fill the ticket message: {message}")
    public void fillMessage(String message) {
        WebElement field = scrollIntoView(MESSAGE_FIELD);
        if (field == null) {
            throw new NoSuchElementException("Message field not found on the New-ticket form");
        }
        field.click();
        pause(300);
        field.clear();
        field.sendKeys(message);
        pause(300);
        hideKeyboard();
    }

    /**
     * True if the Create-ticket submit button is enabled. The form disables it until the
     * required fields are filled, so this is the form's validation gate: false on an empty
     * form, true once subject and message carry text.
     */
    @Step("Check if Create-ticket is enabled")
    public boolean isSubmitEnabled() {
        WebElement btn = scrollIntoView(SUBMIT_BUTTON);
        return btn != null && btn.isEnabled();
    }

    /** Tap Create-ticket. Returns false if the button is absent or disabled (nothing happens). */
    @Step("Submit the new ticket")
    public boolean submit() {
        WebElement btn = scrollIntoView(SUBMIT_BUTTON);
        if (btn == null || !btn.isEnabled()) return false;
        btn.click();
        pause(2500);
        return true;
    }

    /** True once a ticket has been created (a success message or an Open/Pending status shows). */
    @Step("Check if the ticket was created")
    public boolean isSubmitted() {
        return scrollIntoView(CONFIRMATION) != null;
    }

    @Step("Read the confirmation message")
    public String confirmationMessage() {
        WebElement el = scrollIntoView(CONFIRMATION);
        return el == null ? "" : String.valueOf(el.getAttribute("value"));
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
  