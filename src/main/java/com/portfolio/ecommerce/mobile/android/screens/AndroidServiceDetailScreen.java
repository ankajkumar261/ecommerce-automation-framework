package com.portfolio.ecommerce.mobile.android.screens;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;

/**
 * The service detail screen on Android (opened from Shop → Services → a service card).
 *
 * Untagged in this build, so keyed on its visible copy: the header "Service details", the
 * section "ABOUT THIS SERVICE", a "From ₹…" price, and a "Book service" button.
 */
public class AndroidServiceDetailScreen extends AndroidBaseScreen {

    private static final By TITLE       = textContains("Service details");
    private static final By ABOUT       = textContains("ABOUT THIS SERVICE");
    private static final By PRICE       = AppiumBy.androidUIAutomator(
            "new UiSelector().textContains(\"₹\")");
    private static final By BOOK_BUTTON = textContains("Book service");

    public AndroidServiceDetailScreen(AppiumDriver driver) {
        super(driver);
    }

    @Step("Wait for the service detail screen")
    public boolean isLoaded() {
        return waitFor(TITLE, 12000) || isPresent(ABOUT) || isPresent(BOOK_BUTTON);
    }

    @Step("Check a price is shown")
    public boolean hasPrice() {
        return isPresent(PRICE);
    }

    @Step("Check the Book button is offered")
    public boolean hasBook() {
        return isPresent(BOOK_BUTTON);
    }

    @Step("Tap Book service")
    public boolean tapBook() {
        boolean tapped = scrollUntilPresent(BOOK_BUTTON, 4) && click(BOOK_BUTTON);
        pause(2000);
        return tapped;
    }

    /** After tapping Book, true if a booking UI (date/slot/confirm) or a confirmation is showing. */
    @Step("Check the booking flow progressed")
    public boolean bookingFlowProgressed() {
        return isPresent(textContains("Select"))
                || isPresent(textContains("Slot"))
                || isPresent(textContains("Date"))
                || isPresent(textContains("Confirm"))
                || isPresent(textContains("Booking"))
                || isPresent(textContains("booked"));
    }
}
