package com.portfolio.ecommerce.mobile.screens;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.List;
import java.util.Map;

/**
 * Screen Object for Profile -> Addresses, and the "Add address" sheet.
 *
 * WHY THIS EXISTS. Checkout asserts the shopper already HAS a saved delivery address, but no
 * test ever created one — the address was account state someone had set up by hand. When it was
 * removed from the dev account, TC-IOS019 failed with "should have a saved delivery address" and
 * looked for all the world like a product defect. Owning the precondition removes that whole
 * class of false alarm, and covers the address functionality on the way.
 *
 * Layout of the sheet (from the live dev app):
 *   NavigationBar  "Add address"          Button "Cancel"
 *   TextField      placeholder "Search for street, area, landmark…"
 *   Buttons        "Home" / "Work" / "Other"     <- address type
 *   TextFields, each with its OWN placeholder:
 *      "Recipient name"  "10-digit mobile number"  "e.g. 221B / Flat 12B"
 *      "e.g. Palm Residency"  "Near…"  "e.g. DLF Phase 4, Sector 56"
 *      "e.g. Gurgaon"  "State"  "6-digit postal code"
 *   StaticText     "GPS pin" / "Required for delivery"   Button "Use current"
 *   StaticText     "Set as default address"
 *   Button         "Save address"
 *
 * FIELDS ARE MATCHED BY placeholderValue, NOT value. The value attribute holds the placeholder
 * only until something is typed, then becomes the typed text — matching on it would work once
 * and then stop. placeholderValue does not change, which is what makes it a usable key.
 */
public class AddressScreen extends BaseScreen {

    private static final By ADDRESSES_ROW  = AppiumBy.accessibilityId("Addresses");
    private static final By ADD_BUTTON     = AppiumBy.accessibilityId("plus");
    private static final By SAVE_BUTTON    = AppiumBy.accessibilityId("Save address");
    private static final By USE_CURRENT    = AppiumBy.accessibilityId("Use current");
    private static final By CANCEL_BUTTON  = AppiumBy.accessibilityId("Cancel");
    private static final By EMPTY_STATE    = AppiumBy.accessibilityId("No saved addresses");
    private static final By NAV_ADDRESSES  = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeNavigationBar' AND name == 'Addresses'");

    /**
     * The saved-address rows, one per address.
     *
     * Keyed on the row's "More options for <name>" button, which is per-row and semantic.
     * NOT on an icon: the first attempt matched mappin.and.ellipse — the icon on the EMPTY
     * state — while a real saved row renders its address-type icon instead (house.fill for a
     * Home address). So the locator found rows only when there were none.
     */
    private static final By SAVED_ROW = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND name BEGINSWITH 'More options for'");

    public AddressScreen(AppiumDriver driver) {
        super(driver);
    }

    // ── navigation ────────────────────────────────────────────

    @Step("Open Profile -> Addresses")
    public boolean open() {
        new LoginScreen(driver).openProfile();
        pause(1200);
        WebElement row = scrollIntoView(ADDRESSES_ROW);
        if (row == null) {
            return false;
        }
        row.click();
        pause(2000);
        return isPresent(NAV_ADDRESSES);
    }

    @Step("Check the Addresses screen is showing")
    public boolean isLoaded() {
        return isVisible(NAV_ADDRESSES, wait);
    }

    @Step("Check whether any address is saved")
    public boolean hasSavedAddress() {
        // The empty state is the definitive signal; the row check covers the case where the
        // wording of that empty state changes.
        return !isPresent(EMPTY_STATE) && !driver.findElements(SAVED_ROW).isEmpty();
    }

    public int savedAddressCount() {
        return driver.findElements(SAVED_ROW).size();
    }

    @Step("Open the Add address sheet")
    public boolean openAddSheet() {
        WebElement plus = firstDisplayed(ADD_BUTTON);
        if (plus == null) {
            return false;
        }
        plus.click();
        pause(2200);
        return isPresent(field("Recipient name"));
    }

    // ── the form ──────────────────────────────────────────────

    /** A text field, keyed on its placeholder. */
    private static By field(String placeholder) {
        return AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeTextField' AND placeholderValue == '" + placeholder + "'");
    }

    @Step("Fill the address form for {fullName}")
    public void fillForm(String fullName, String phone, String house, String building,
                         String locality, String city, String state, String pincode) {
        type("Recipient name", fullName);
        type("10-digit mobile number", phone);
        type("e.g. 221B / Flat 12B", house);
        type("e.g. Palm Residency", building);
        type("e.g. DLF Phase 4, Sector 56", locality);
        type("e.g. Gurgaon", city);
        type("State", state);
        hideKeyboard();
        pause(500);
        type("6-digit postal code", pincode);
        hideKeyboard();
        pause(600);
    }

    /**
     * Type into one field, keyed on its placeholder.
     *
     * HIDE THE KEYBOARD FIRST. Typing into a field raises the keyboard over the lower half of the
     * sheet, and anything under it reports visible="false" — so the NEXT field cannot be found at
     * all, and this threw "no field with placeholder 'e.g. Palm Residency'" part way down the
     * form. Dismissing between fields keeps each one reachable.
     *
     * The value is read back afterwards. Without that check a tap that missed, or a field that
     * silently rejected input, would look like a filled form and surface later as an unexplained
     * save failure.
     */
    private void type(String placeholder, String value) {
        hideKeyboard();
        pause(300);

        WebElement el = scrollIntoSheetView(field(placeholder));
        if (el == null) {
            throw new IllegalStateException(
                    "The address form has no reachable field with placeholder '" + placeholder
                            + "'. Either the form layout changed, or the field never scrolled into "
                            + "view — re-run the recon dump before editing this.");
        }
        el.click();
        pause(350);
        el.sendKeys(value);
        pause(400);
        hideKeyboard();
        pause(300);

        String actual = el.getAttribute("value");
        if (actual == null || !actual.replace(" ", "").contains(value.replace(" ", ""))) {
            throw new IllegalStateException(
                    "Field '" + placeholder + "' did not accept the value. Expected it to contain '"
                            + value + "', reads '" + actual + "'.");
        }
    }

    /**
     * Set the GPS pin the form marks "Required for delivery".
     *
     * Best-effort: if the control cannot be reached the caller still tries to save, and the save
     * assertion reports the truth either way. Set the simulator's location first, or this has
     * nothing to read:  xcrun simctl location &lt;udid&gt; set 32.0998,76.2691
     */
    @Step("Set the GPS pin from the current location")
    public boolean useCurrentLocation() {
        WebElement btn = scrollIntoSheetView(USE_CURRENT);
        if (btn == null) {
            return false;
        }
        btn.click();
        pause(3500);
        acceptLocationPromptIfShown();
        return true;
    }

    /** iOS asks for location permission the first time; accept it so it cannot block the save. */
    private void acceptLocationPromptIfShown() {
        try {
            var alert = driver.switchTo().alert();
            String text = alert.getText();
            if (text != null && text.toLowerCase().contains("location")) {
                alert.accept();
                pause(1500);
            }
        } catch (Exception ignored) {
            // No alert — already granted, or never asked.
        }
    }

    @Step("Save the address")
    public boolean save() {
        WebElement btn = scrollIntoSheetView(SAVE_BUTTON);
        if (btn == null) {
            return false;
        }
        btn.click();
        pause(4500);
        // The sheet closing is the signal the save was accepted; a validation error keeps it up.
        return !isPresent(field("Recipient name"));
    }

    @Step("Cancel the Add address sheet")
    public void cancelSheet() {
        WebElement cancel = firstDisplayed(CANCEL_BUTTON);
        if (cancel != null) {
            cancel.click();
            pause(1500);
        }
    }

    /**
     * Is any element on the Addresses screen showing this text?
     *
     * Matches ANY element, not a Button: the address itself renders as a StaticText
     * ("302, QA Residency, Fatehpur, Kangra, Himachal Pradesh, 176063"), so a button-scoped
     * match found nothing even with the address plainly listed.
     *
     * Assert on the PINCODE rather than the name — the app title-cases what you typed
     * ("QA Automation" comes back as "Qa Automation"), so the name is not a safe key.
     */
    @Step("Check the address list shows: {text}")
    public boolean isAddressListed(String text) {
        return scrollIntoView(AppiumBy.iOSNsPredicateString("name CONTAINS '" + text + "'")) != null;
    }

    /**
     * Bring a control inside the Add-address sheet into view.
     *
     * The sheet is a ScrollView taller than the screen — "Save address" sits at y≈1158 on an
     * 874pt screen — so the lower controls are in the tree but off-screen, and tapping them
     * without scrolling silently does nothing.
     *
     * It has to be `mobile: swipe`. Measured on this sheet: the W3C pointer sequence
     * (BaseScreen#swipe) did not move it, and `mobile: scroll` failed with "Failed to perform
     * scroll with visible cell due to max scroll count reached". `mobile: swipe` up works.
     */
    private WebElement scrollIntoSheetView(By by) {
        WebElement el = firstDisplayed(by);
        if (el != null) {
            return el;
        }
        for (int i = 0; i < MAX_SWIPES; i++) {
            try {
                driver.executeScript("mobile: swipe", Map.of("direction", "up"));
            } catch (Exception ignored) {
                return null;
            }
            pause(700);
            el = firstDisplayed(by);
            if (el != null) {
                return el;
            }
        }
        return null;
    }
}
