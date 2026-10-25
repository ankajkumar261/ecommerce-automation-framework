package com.portfolio.ecommerce.mobile.screens;

import com.portfolio.ecommerce.mobile.drivers.AppiumDriverManager;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;

import java.time.Duration;
import java.util.Collections;

/**
 * Screen Object for the Storefront app login flow.
 *
 * Path: Profile (guest) -> "Sign in / Create account" -> choose "Email" ->
 *       enter email -> enter password -> "Sign in".
 *
 * Locator notes:
 *  - Text fields are matched by TYPE, not by their placeholder value, because the value
 *    changes once we type into them (matching on "you@example.test" would then fail).
 *  - Buttons/labels are matched by accessibility id (the element's "name").
 */
public class LoginScreen extends BaseScreen {

    // Entry points / controls
    private static final By PROFILE_TAB        = AppiumBy.accessibilityId("person");
    private static final By SIGN_IN_CREATE      = AppiumBy.accessibilityId("Sign in / Create account");
    /** The sheet opens on the PHONE tab; this button switches it to email+password.
     *  NB: there is also a bare StaticText "Email" on the sheet — matching that instead
     *  silently leaves us on the phone tab, where there is no password field at all. */
    private static final By EMAIL_OPTION        = AppiumBy.accessibilityId("Email sign-in");
    private static final By SIGN_IN_BUTTON      = AppiumBy.accessibilityId("Sign in");
    private static final By SIGN_OUT_BUTTON     = AppiumBy.accessibilityId("Sign Out");

    // Auth-state markers on the Profile screen — the only trustworthy signal that we are
    // actually signed in. The sign-in sheet closing proves nothing: dismissing it (or
    // tapping its X) also closes it while leaving the app a guest.
    private static final By GUEST_MARKER        = AppiumBy.accessibilityId("You're browsing as a guest");

    // Fields (by type — robust against value changing after input)
    private static final By EMAIL_FIELD         = AppiumBy.className("XCUIElementTypeTextField");
    private static final By PASSWORD_FIELD       = AppiumBy.className("XCUIElementTypeSecureTextField");

    public LoginScreen(AppiumDriver driver) {
        super(driver);
    }

    /** If the app is currently logged in, sign out so we start from the guest state.
     *  Safe to call when already logged out (does nothing). */
    @Step("Log out if already logged in")
    public void logoutIfLoggedIn() {
        // Open Profile if we're on Home.
        if (isPresent(PROFILE_TAB)) {
            waitClickable(PROFILE_TAB).click();
        }
        // Already a guest? "Sign in / Create account" is showing -> nothing to do.
        if (isPresent(SIGN_IN_CREATE)) {
            return;
        }
        // Scroll the Sign Out button into view, then tap it.
        //
        // This MUST be a displayed-check, not isPresent(). Sign Out sits at the very bottom of
        // the Profile screen (y=948 on an 874pt screen) and XCUITest reports off-screen elements
        // in the tree with visible="false" — so isPresent() is already true before any scrolling.
        // The old loop guarded on it, never scrolled once, and clicked a button that was not on
        // screen: confirmSignOut() then waited out its 8s on an alert that never came, and the
        // relaunch below ran anyway. The app came back to Home STILL SIGNED IN, which failed
        // every guest-state precondition for the rest of the run.
        WebElement signOut = scrollIntoView(SIGN_OUT_BUTTON);
        if (signOut != null) {
            signOut.click();
            confirmSignOut();
            // Relaunch the app so it reopens fresh at the top as a guest
            // (avoids fighting the scroll position to find the Sign in button).
            AppiumDriverManager.relaunchApp();
        }
    }

    /**
     * Confirm the "Sign Out" alert (tap its Sign Out button, not Cancel).
     *
     * The app raises a real XCUIElementTypeAlert — title "Sign Out", body "Are you sure you want
     * to sign out?", buttons "Cancel" and "Sign Out" — and it stays up until one is tapped.
     *
     * This match is ALERT-SCOPED ONLY, deliberately. The previous version also tried a
     * screen-wide `name == "Sign Out" OR label == "Sign Out"` as a fallback, and started matching
     * the instant the screen button was tapped — before the alert had rendered. So on its very
     * first pass the fallback matched the Profile screen's OWN Sign Out button, "confirmed" by
     * clicking that again, and returned. The alert was left standing, the relaunch in
     * logoutIfLoggedIn() then killed the app with it still up, and the account was never signed
     * out — which surfaced later as five unrelated-looking guest-state failures.
     */
    private void confirmSignOut() {
        By alertConfirm = AppiumBy.iOSClassChain(
                "**/XCUIElementTypeAlert/**/XCUIElementTypeButton[`label == \"Sign Out\"`]");

        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end) {
            for (WebElement el : driver.findElements(alertConfirm)) {
                try {
                    el.click();
                    awaitGuestState();
                    return;
                } catch (Exception ignored) { }
            }
            pause(300);
        }
        // Fail loudly here rather than let the run limp on: without this confirmation the app
        // stays signed in, and every guest-state precondition after it fails for reasons that
        // look nothing like the real cause.
        throw new IllegalStateException(
                "The sign-out confirmation alert never appeared, or its 'Sign Out' button could "
                        + "not be tapped within 10s. The app is still signed in.");
    }

    /**
     * Wait for the app to actually REACH the guest state, while it is still running.
     *
     * Tapping the alert's "Sign Out" starts the sign-out; it does not finish it. The caller
     * relaunches the app immediately afterwards, and terminating it mid-flight left the
     * relaunched instance still showing the signed-in Profile — the guest state only appeared
     * on some later cold start, long after the assertion that cared had already failed.
     *
     * Best-effort on purpose: if the app has not settled in time we still fall through to the
     * relaunch, and isGuest() gets the final word.
     */
    private void awaitGuestState() {
        long end = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < end) {
            if (isPresent(GUEST_MARKER) || isPresent(SIGN_IN_CREATE)) {
                return;
            }
            pause(300);
        }
    }

    /** Start the sign-in flow. Tolerant of where the app starts:
     *  already on the sign-in screen, on the guest Profile screen, or on Home. */
    @Step("Open the sign-in screen")
    public void openSignIn() {
        // Already on the sign-in screen (email field showing)? Nothing to navigate.
        if (isPresent(EMAIL_FIELD)) {
            return;
        }
        // If the "Sign in / Create account" button isn't visible, we're likely on Home —
        // open Profile first to reveal it.
        if (!isPresent(SIGN_IN_CREATE) && isPresent(PROFILE_TAB)) {
            waitClickable(PROFILE_TAB).click();
        }
        waitClickable(SIGN_IN_CREATE).click();

        // Choose the Email login option (vs phone/OTP) if that choice is shown.
        if (isPresent(EMAIL_OPTION)) {
            waitClickable(EMAIL_OPTION).click();
        }
    }

    @Step("Enter email: {email}")
    public void enterEmail(String email) {
        WebElement field = waitVisible(EMAIL_FIELD);
        field.click();
        field.clear();
        field.sendKeys(email);
    }

    @Step("Enter password")
    public void enterPassword(String password) {
        WebElement field = waitVisible(PASSWORD_FIELD);
        field.click();
        field.clear();
        field.sendKeys(password);
    }

    @Step("Tap Sign in")
    public void tapSignIn() {
        // The keyboard is up after typing the password and covers the Sign in button.
        dismissKeyboard();
        // iOS may show a "Save Password?" system prompt that also covers the button.
        dismissSavePasswordPrompt();

        By signInButton = AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeButton' AND (name == 'Sign in' OR label == 'Sign in')");

        // The button must be ON SCREEN, not merely present: with the keyboard up it stays in
        // the tree but is covered, and tapping it then does nothing at all — which is how a
        // failed login used to masquerade as a successful one.
        WebElement btn = firstDisplayed(signInButton);
        if (btn == null) {
            dismissKeyboard();
            dismissSavePasswordPrompt();
            btn = firstDisplayed(signInButton);
        }
        if (btn == null) {
            btn = firstDisplayed(SIGN_IN_BUTTON);
        }
        if (btn == null) {
            throw new NoSuchElementException(
                    "The 'Sign in' button never became visible — the keyboard or a system "
                            + "prompt is probably still covering it.");
        }
        btn.click();

        // The prompt usually lands right AFTER submitting, once the sign-in round-trip completes
        // — so wait for it here rather than checking instantly and missing it. (The
        // implementation lives in BaseScreen: this prompt is not specific to the login screen,
        // and the per-test reset needs it too.)
        awaitAndDismissSavePasswordPrompt(3_000);
    }

    /** Dismiss the on-screen keyboard using Appium's mobile command, then a neutral tap. */
    private void dismissKeyboard() {
        try {
            if (isKeyboardShown()) {
                driver.executeScript("mobile: hideKeyboard", java.util.Map.of(
                        "keys", java.util.List.of("return", "Return", "Done", "done", "Go", "Search", "Next")));
            }
        } catch (Exception ignored) { }

        // If still up, tap a neutral area to dismiss it.
        try {
            if (isKeyboardShown()) {
                tapNeutralArea();
            }
        } catch (Exception ignored) { }
    }

    /** W3C tap near the top-centre of the screen (above fields and keyboard). */
    private void tapNeutralArea() {
        try {
            Dimension size = driver.manage().window().getSize();
            int x = size.width / 2;
            int y = (int) (size.height * 0.10);
            PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
            Sequence tap = new Sequence(finger, 1);
            tap.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), x, y));
            tap.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
            tap.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
            driver.perform(Collections.singletonList(tap));
        } catch (Exception ignored) { }
    }

    /**
     * True only if the app is genuinely authenticated.
     *
     * This deliberately does NOT just check that the sign-in sheet closed. The sheet also
     * closes when it is dismissed without logging in — treating that as success made this
     * test pass while the app sat in guest state, which is exactly the failure it exists to
     * catch. So we go to Profile and read the real markers: the guest banner must be gone
     * and a Sign Out control must exist.
     */
    @Step("Verify the app is actually signed in")
    public boolean isSignedIn() {
        // First give the sheet a chance to close (login is a network round-trip).
        long end = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < end
                && (isPresent(EMAIL_FIELD) || isPresent(PASSWORD_FIELD))) {
            pause(500);
        }

        // Now confirm the authenticated state on Profile.
        openProfile();
        end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end) {
            if (!isPresent(GUEST_MARKER) && !isPresent(SIGN_IN_CREATE) && isPresent(SIGN_OUT_BUTTON)) {
                return true;
            }
            pause(500);
        }
        return false;
    }

    /** Open the Profile tab if we're not already on it. */
    @Step("Open the Profile tab")
    public void openProfile() {
        WebElement tab = firstDisplayed(PROFILE_TAB);
        if (tab != null) {
            try { tab.click(); } catch (Exception ignored) { }
            pause(1500);
        }
    }

    /** True if the app is currently a guest (used by tests that need the logged-out state). */
    public boolean isGuest() {
        openProfile();
        // Poll rather than read once, mirroring isSignedIn(): this is called straight after a
        // sign-out + relaunch, and the Profile screen needs a moment to render the guest state.
        // A single immediate check reported "still signed in" on an app that was mid-transition.
        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end) {
            if (isPresent(GUEST_MARKER) || isPresent(SIGN_IN_CREATE)) {
                return true;
            }
            pause(300);
        }
        return false;
    }

    /**
     * True if the sign-in sheet is currently up.
     *
     * The app raises this sheet when a guest attempts something that needs an account
     * (adding to the cart, saving to the wishlist), so it doubles as the assertion for
     * "this action is gated behind sign-in".
     */
    @Step("Check the sign-in sheet is shown")
    public boolean isSignInSheetShown() {
        // Three separate accessibility-id lookups, NOT one OR predicate: combining them is a
        // full-tree scan and measured much slower. See the performance note in BaseScreen.
        return isPresent(EMAIL_OPTION)
                || isPresent(AppiumBy.accessibilityId("Phone sign-in"))
                || isPresent(AppiumBy.accessibilityId("Welcome to the Storefront"));
    }

    /** Dismiss the sign-in sheet with its X, if it is up. */
    @Step("Dismiss the sign-in sheet")
    public void dismissSignInSheet() {
        WebElement close = firstDisplayed(AppiumBy.accessibilityId("xmark"));
        if (close != null) {
            try { close.click(); } catch (Exception ignored) { }
            pause(1500);
        }
    }

    /** Full login: (log out if needed) -> Sign in / Create account -> Email -> creds -> Sign in. */
    @Step("Log in as: {email}")
    public void loginAs(String email, String password) {
        logoutIfLoggedIn();
        openSignIn();
        enterEmail(email);
        enterPassword(password);
        tapSignIn();
    }
}