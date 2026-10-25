package com.portfolio.ecommerce.mobile.android.screens;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.HasOnScreenKeyboard;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.List;
import java.util.Map;

/**
 * The Help & Support screen on Android (Account → Help & Support) and its New-ticket form.
 *
 * Support is a TICKET system, matching iOS. Untagged in this build, so keyed on visible copy:
 *   Landing : header "Help & Support", status chips (All / Open / In progress / Resolved /
 *             Closed), empty state "No support tickets", and a "Create ticket" button.
 *   Form    : header "New ticket", "Subject" and "Message" labels over two EditTexts,
 *             Category / Priority choices, and a "Submit ticket" button.
 *
 * Compose does not reflect a disabled button as enabled=false in the a11y tree, so the empty
 * form is validated by BEHAVIOUR (tapping Submit leaves you on the form) rather than by reading
 * the button's enabled state.
 */
public class AndroidSupportScreen extends AndroidBaseScreen {

    private static final By TITLE          = textContains("Help & Support");
    private static final By EMPTY          = textContains("No support tickets");
    private static final By STATUS_OPEN    = AppiumBy.androidUIAutomator("new UiSelector().text(\"Open\")");

    /**
     * The create-ticket entry appears two ways depending on state: as a prominent "Create ticket"
     * TEXT button on the empty screen, and as a "New ticket" icon (content-desc) once tickets
     * exist. Match all the variants.
     */
    private static final By CREATE_TICKET_TEXT  = textContains("Create ticket");
    private static final By CREATE_TICKET_DESC  = descContains("Create ticket");
    private static final By NEW_TICKET_TEXT     = textContains("New ticket");
    private static final By NEW_TICKET_DESC     = descContains("New ticket");

    private static final By FORM_TITLE     = textContains("New ticket");
    private static final By SUBJECT_LABEL  = AppiumBy.androidUIAutomator("new UiSelector().text(\"Subject\")");
    private static final By MESSAGE_LABEL  = AppiumBy.androidUIAutomator("new UiSelector().text(\"Message\")");
    private static final By EDIT_TEXT      = AppiumBy.className("android.widget.EditText");
    private static final By SUBMIT_TICKET  = textContains("Submit ticket");

    public AndroidSupportScreen(AppiumDriver driver) {
        super(driver);
    }

    @Step("Wait for the Support screen")
    public boolean isLoaded() {
        return waitFor(TITLE, 12000);
    }

    @Step("Check the empty-tickets state")
    public boolean hasEmptyState() {
        return isPresent(EMPTY);
    }

    @Step("Check a status filter is offered")
    public boolean hasStatusFilters() {
        return isPresent(STATUS_OPEN);
    }

    @Step("Check a Create-ticket entry is available")
    public boolean hasCreateTicketEntry() {
        for (int i = 0; i < 4; i++) {
            if (anyPresent(CREATE_TICKET_TEXT, CREATE_TICKET_DESC, NEW_TICKET_DESC)
                    || isPresent(NEW_TICKET_TEXT)) {
                return true;
            }
            swipeUp();
        }
        return false;
    }

    @Step("Open the Create-ticket form")
    public boolean openCreateTicket() {
        for (int i = 0; i < 4; i++) {
            // Prefer the explicit create controls; the "New ticket" icon (content-desc) is how the
            // entry renders once tickets already exist.
            if (clickAny(CREATE_TICKET_TEXT, CREATE_TICKET_DESC, NEW_TICKET_DESC)) {
                return true;
            }
            swipeUp();
        }
        return false;
    }

    @Step("Wait for the New-ticket form")
    public boolean isTicketFormLoaded() {
        return waitFor(FORM_TITLE, 12000) || isPresent(SUBMIT_TICKET);
    }

    @Step("Check the ticket form shows Subject and Message")
    public boolean hasTicketForm() {
        return isPresent(SUBJECT_LABEL) && isPresent(MESSAGE_LABEL)
                && all(EDIT_TEXT).size() >= 2;
    }

    @Step("Fill the ticket subject: {subject}")
    public boolean fillSubject(String subject) {
        return typeIntoEditText(0, subject);
    }

    @Step("Fill the ticket message: {message}")
    public boolean fillMessage(String message) {
        return typeIntoEditText(1, message);
    }

    private boolean typeIntoEditText(int index, String value) {
        List<WebElement> fields = all(EDIT_TEXT);
        if (fields.size() <= index) return false;
        try {
            WebElement f = fields.get(index);
            f.click();
            pause(400);
            f.sendKeys(value);
            pause(400);
            closeKeyboard();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Tap Submit ticket. */
    @Step("Submit the ticket")
    public boolean submit() {
        boolean tapped = scrollUntilPresent(SUBMIT_TICKET, 4) && click(SUBMIT_TICKET);
        pause(2000);
        return tapped;
    }

    /** True once a ticket was created — the New-ticket form has gone and a ticket/list is shown. */
    @Step("Check the ticket was created")
    public boolean isSubmitted() {
        return !isPresent(FORM_TITLE)
                && (isPresent(textContains("Open")) || isPresent(textContains("submitted"))
                    || isPresent(textContains("created")) || isPresent(TITLE));
    }

    /**
     * Close the IME without leaving the form. hideKeyboard() sends BACK on Android, which this
     * app's back handler uses to pop the screen — so use the field's own editor action instead.
     */
    private void closeKeyboard() {
        try {
            if (((HasOnScreenKeyboard) driver).isKeyboardShown()) {
                driver.executeScript("mobile: performEditorAction", Map.of("action", "done"));
                pause(800);
            }
        } catch (Exception ignored) { }
    }
}
