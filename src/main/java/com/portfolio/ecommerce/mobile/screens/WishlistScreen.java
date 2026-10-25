package com.portfolio.ecommerce.mobile.screens;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen Object for the Storefront app Wishlist (Profile -> Wishlist).
 *
 * Layout (from the live app):
 *   title        StaticText  "Wishlist"
 *   grid         Button      product cards, two per row
 *   saved heart  Button      "heart.fill" (label "Love") on each card — tapping it removes
 *                            the item immediately, with no confirmation
 *   empty state  StaticText  "No saved items yet"
 *                            "Tap the heart on any product to save it here."
 *   back         Button      "BackButton" (label "Profile")
 *
 * The wishlist requires a signed-in shopper: as a guest, tapping a heart opens the sign-in
 * sheet instead of saving anything.
 *
 * Like the cart, the wishlist is server-side and PERSISTS between runs, so tests that assert
 * absolute counts should clear() first.
 */
public class WishlistScreen extends BaseScreen {

    private static final By TITLE      = AppiumBy.accessibilityId("Wishlist");
    private static final By PROFILE_TAB = AppiumBy.accessibilityId("person");
    private static final By SAVED_HEART = AppiumBy.accessibilityId("heart.fill");

    private static final By PRODUCT_CARD = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeButton' AND label CONTAINS 'View →' AND label CONTAINS '₹'");

    private static final By EMPTY_MESSAGE = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeStaticText' AND value BEGINSWITH 'No saved items'");

    public WishlistScreen(AppiumDriver driver) {
        super(driver);
    }

    // ── navigation ────────────────────────────────────────────

    /** Open Profile -> Wishlist, from wherever (and whatever tab) we happen to be. */
    @Step("Open the Wishlist")
    public void open() {
        // Land on a tab ROOT first: Profile is reached from the tab bar, and a screen pushed
        // over a tab still shows that bar, so we cannot trust it as a starting point.
        returnToTabBar();
        WebElement profile = firstDisplayed(PROFILE_TAB);
        if (profile == null) throw new NoSuchElementException("Profile tab not found");
        profile.click();
        pause(1200);

        WebElement entry = scrollIntoView(TITLE);
        if (entry == null) throw new NoSuchElementException("Wishlist entry not found on Profile");
        entry.click();
        pause(1400);
    }

    @Step("Wait for the Wishlist screen")
    public boolean isLoaded() {
        return isVisible(TITLE, wait);
    }

    // ── reading ───────────────────────────────────────────────

    @Step("Count the saved products")
    public int productCount() {
        return findAll(PRODUCT_CARD).size();
    }

    @Step("Read the saved product names")
    public List<String> productNames() {
        List<String> names = new ArrayList<>();
        for (WebElement card : findAll(PRODUCT_CARD)) {
            try {
                String label = card.getAttribute("label");
                if (label == null) continue;
                String[] parts = label.split(",");
                if (parts.length >= 2) names.add(parts[1].trim());
            } catch (Exception ignored) { }
        }
        return names;
    }

    @Step("Check the wishlist contains: {productName}")
    public boolean containsProduct(String productName) {
        if (productName == null || productName.isBlank()) return false;
        for (String name : productNames()) {
            if (name.equalsIgnoreCase(productName.trim())) return true;
        }
        return false;
    }

    @Step("Check the wishlist is empty")
    public boolean isEmpty() {
        return productCount() == 0 && isPresent(EMPTY_MESSAGE);
    }

    /** The empty-state headline, e.g. "No saved items yet". */
    @Step("Read the empty-state message")
    public String emptyMessage() {
        WebElement el = firstDisplayed(EMPTY_MESSAGE);
        return el == null ? "" : String.valueOf(el.getAttribute("value"));
    }

    // ── removing ──────────────────────────────────────────────

    /**
     * Unsave a product by tapping the heart on its card.
     *
     * The grid is two cards wide, so the hearts cannot be matched by order alone — each
     * heart is located by falling inside its own card's bounds.
     */
    @Step("Remove from wishlist: {productName}")
    public void removeProduct(String productName) {
        WebElement card = cardFor(productName);
        if (card == null) {
            throw new NoSuchElementException("Not in the wishlist: " + productName);
        }
        WebElement heart = heartInside(card);
        if (heart == null) {
            throw new NoSuchElementException("No heart control found on the card for: " + productName);
        }
        heart.click();
        pause(1400);
    }

    /** Remove whichever product is first in the grid. */
    @Step("Remove the first saved product")
    public void removeFirst() {
        WebElement heart = firstDisplayed(SAVED_HEART);
        if (heart == null) return;
        heart.click();
        pause(1400);
    }

    /** Empty the wishlist so a test starts from a known state. */
    @Step("Clear the wishlist")
    public void clear() {
        for (int i = 0; i < 20 && productCount() > 0; i++) {
            WebElement heart = firstDisplayed(SAVED_HEART);
            if (heart == null) break;
            try { heart.click(); } catch (Exception ignored) { }
            pause(1200);
        }
    }

    /** The product card whose label carries the given product name. */
    private WebElement cardFor(String productName) {
        String needle = productName.trim().toLowerCase();
        for (WebElement card : findAll(PRODUCT_CARD)) {
            try {
                String label = card.getAttribute("label");
                if (label != null && label.toLowerCase().contains(needle)) return card;
            } catch (Exception ignored) { }
        }
        return null;
    }

    /** The heart control whose centre falls within the given card's bounds. */
    private WebElement heartInside(WebElement card) {
        Rectangle c = card.getRect();
        for (WebElement heart : findAll(SAVED_HEART)) {
            try {
                Rectangle h = heart.getRect();
                int cx = h.getX() + h.getWidth() / 2;
                int cy = h.getY() + h.getHeight() / 2;
                if (cx >= c.getX() && cx <= c.getX() + c.getWidth()
                        && cy >= c.getY() && cy <= c.getY() + c.getHeight()) {
                    return heart;
                }
            } catch (Exception ignored) { }
        }
        return null;
    }
}
