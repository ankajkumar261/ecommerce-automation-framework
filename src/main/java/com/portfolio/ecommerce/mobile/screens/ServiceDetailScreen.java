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
 * Screen Object for the Storefront app Service Detail screen.
 *
 * Reached by tapping a service card from the Shop tab. Layout:
 *   title            StaticText  the service name
 *   price range      StaticText  e.g. "From ₹350"
 *   description      StaticText  service description
 *   book button      Button      "Book" / "Book now"
 *   date picker      various     date selection elements
 *   time picker      various     time slot selection elements
 *   confirmation     StaticText  booking confirmed / reference number
 */
public class ServiceDetailScreen extends BaseScreen {

    private static final By BOOK_BUTTON = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND (label BEGINSWITH 'Book' OR name BEGINSWITH 'Book')");

    private static final By PRICE_RANGE = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeStaticText' AND value BEGINSWITH 'From ₹'");

    private static final By CONFIRMATION = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeStaticText' AND (value CONTAINS 'confirmed' "
                    + "OR value CONTAINS 'Confirmed' OR value CONTAINS 'booking' "
                    + "OR value CONTAINS 'Booked')");

    private static final By BACK = AppiumBy.accessibilityId("BackButton");

    public ServiceDetailScreen(AppiumDriver driver) {
        super(driver);
    }

    @Step("Wait for the Service Detail screen")
    public boolean isLoaded() {
        return isPresent(BOOK_BUTTON) || isPresent(PRICE_RANGE);
    }

    /** The service name shown on the detail screen. */
    @Step("Read the service name")
    public String serviceName() {
        // The title is the first prominent StaticText — match by position or accessibility.
        WebElement title = firstDisplayed(AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeStaticText' AND value != ''"));
        return title == null ? "" : String.valueOf(title.getAttribute("value"));
    }

    @Step("Check a price range is displayed")
    public boolean hasPrice() {
        return scrollIntoView(PRICE_RANGE) != null;
    }

    /** The price range text, e.g. "From ₹350". Empty when absent. */
    @Step("Read the price range")
    public String priceRange() {
        WebElement el = scrollIntoView(PRICE_RANGE);
        return el == null ? "" : String.valueOf(el.getAttribute("value"));
    }

    @Step("Tap the Book button")
    public void tapBook() {
        WebElement btn = scrollIntoView(BOOK_BUTTON);
        if (btn == null) {
            throw new NoSuchElementException("Book button not found on service detail");
        }
        btn.click();
        pause(1600);
    }

    /**
     * Select a date on the booking calendar.
     *
     * Matches a date button by its label or text. The calendar is typically a horizontal
     * scroll of day chips.
     */
    @Step("Select date: {date}")
    public boolean selectDate(String date) {
        By dateLocator = AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeButton' AND (label CONTAINS '" + date.replace("'", "\\'")
                        + "' OR name CONTAINS '" + date.replace("'", "\\'") + "')");
        WebElement el = scrollIntoView(dateLocator);
        if (el == null) return false;
        el.click();
        pause(1000);
        return true;
    }

    /**
     * Select a time slot.
     *
     * Time slots typically render as buttons with the time as their label.
     */
    @Step("Select time: {time}")
    public boolean selectTime(String time) {
        By timeLocator = AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeButton' AND (label CONTAINS '" + time.replace("'", "\\'")
                        + "' OR name CONTAINS '" + time.replace("'", "\\'") + "')");
        WebElement el = scrollIntoView(timeLocator);
        if (el == null) return false;
        el.click();
        pause(1000);
        return true;
    }

    /** True if a date/time selection UI is currently visible. */
    @Step("Check if date/time picker is showing")
    public boolean hasDateTimePicker() {
        return isPresent(AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeButton' AND (label CONTAINS 'Confirm' "
                        + "OR label CONTAINS 'Done' OR label CONTAINS 'Select')"));
    }

    @Step("Confirm the booking")
    public boolean confirmBooking() {
        By confirmBtn = AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeButton' AND (label CONTAINS 'Confirm' "
                        + "OR label CONTAINS 'Done' OR label CONTAINS 'Book')");
        WebElement btn = scrollIntoView(confirmBtn);
        if (btn == null) return false;
        btn.click();
        pause(2000);
        return true;
    }

    @Step("Check if booking confirmation is shown")
    public boolean isBookingConfirmed() {
        return scrollIntoView(CONFIRMATION) != null;
    }

    /** Read the confirmation message. */
    @Step("Read the confirmation message")
    public String confirmationMessage() {
        WebElement el = scrollIntoView(CONFIRMATION);
        return el == null ? "" : String.valueOf(el.getAttribute("value"));
    }

    @Step("Go back")
    public void goBack() {
        try { driver.findElement(BACK).click(); } catch (Exception ignored) { }
    }
}
