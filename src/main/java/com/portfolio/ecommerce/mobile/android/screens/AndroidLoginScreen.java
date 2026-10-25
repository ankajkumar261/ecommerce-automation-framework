package com.portfolio.ecommerce.mobile.android.screens;

import com.portfolio.ecommerce.mobile.drivers.AppiumDriverManager;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.HasOnScreenKeyboard;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.Map;

/**
 * Sign-in on Android.
 *
 * The screen opens on the PHONE tab (phone number + Send OTP). Email/password is a second mode
 * behind signin_mode_email, so every email sign-in has to switch first — landing on the phone
 * tab and typing an email into signin_phone is the obvious wrong turn here.
 *
 * Tags: signin_mode_phone, signin_mode_email, signin_phone, signin_send_otp,
 *       signin_email, signin_password, signin_submit.
 *
 * OTP is deliberately not automated. It needs a code delivered out of band, so the suite uses
 * the email path; signin_send_otp is exposed only so a test can assert the phone mode offers it.
 */
public class AndroidLoginScreen extends AndroidBaseScreen {

    public static final By PROFILE_BUTTON = tag("profile_button");
    public static final By MODE_EMAIL     = tag("signin_mode_email");
    public static final By MODE_PHONE     = tag("signin_mode_phone");
    public static final By EMAIL_FIELD    = tag("signin_email");
    public static final By PASSWORD_FIELD = tag("signin_password");
    public static final By SUBMIT         = tag("signin_submit");
    public static final By PHONE_FIELD    = tag("signin_phone");
    public static final By SEND_OTP       = tag("signin_send_otp");

    public AndroidLoginScreen(AppiumDriver driver) {
        super(driver);
    }

    /**
     * Open the sign-in screen from the top-bar profile icon.
     *
     * Returns false when there is nothing to open — including when the shopper is ALREADY signed
     * in, because profile_button then leads to the Account screen instead. Callers that just need
     * an authenticated session should use {@link #ensureSignedIn}, not this.
     */
    @Step("Open sign-in from the profile icon")
    public boolean open() {
        return click(PROFILE_BUTTON) && waitFor(MODE_EMAIL, 15000);
    }

    /**
     * The Account screen — what profile_button reaches once signed in.
     *
     * Detected by its content, not by a tag: this screen carries NO test tags at all, so there is
     * nothing else to key on. Worth asking for account_* tags; until then this is text-coupled and
     * will break if the wording changes.
     */
    private static final By ACCOUNT_MARKER = text("My Orders");

    /**
     * Make sure the shopper is signed in, whatever state the app is in.
     *
     * Tolerates all three starting points — already on Account, on the sign-in form, or on Home —
     * because the suite shares one app session and a previous test may have left any of them.
     */
    @Step("Ensure the shopper is signed in")
    public boolean ensureSignedIn(String email, String password) {
        if (isOnAccountScreen()) {
            return true;
        }
        if (!isShowing()) {
            // The profile icon only exists on the tabbed screens. Coming from cart or checkout
            // there is none, the tap failed, and this reported "could not sign in" for a session
            // that was already authenticated — which is why these tests failed intermittently
            // depending on what the previous test left on screen.
            if (!isPresent(PROFILE_BUTTON)) {
                returnToTabs();
            }
            if (!click(PROFILE_BUTTON)) {
                return false;
            }
            pause(2500);
            if (isOnAccountScreen()) {
                return true;                   // already signed in
            }
        }
        return signIn(email, password);
    }

    /**
     * Get back to a tabbed screen, where the profile icon exists.
     *
     * RELAUNCH, do not press BACK in a loop. Back from Home LEAVES THE APP, and a loop that
     * keeps pressing until it finds the button will happily walk straight out to the device
     * launcher — measured: 14 of 21 tests failed in one run because the app was gone after the
     * first attempt, and every later test had nothing to talk to. A relaunch always lands on
     * Home and cannot escape the app.
     */
    private void returnToTabs() {
        if (isPresent(PROFILE_BUTTON)) {
            return;
        }
        AppiumDriverManager.relaunchApp();
        waitFor(PROFILE_BUTTON, 15000);
    }

    /** True when the authenticated Account screen is showing. */
    public boolean isOnAccountScreen() {
        return isPresent(ACCOUNT_MARKER);
    }

    /** Tap the profile icon and let the caller decide what it landed on. */
    @Step("Tap the profile icon")
    public boolean tapProfile() {
        boolean tapped = click(PROFILE_BUTTON);
        pause(3000);
        return tapped;
    }

    @Step("Check the sign-in screen is showing")
    public boolean isShowing() {
        return isPresent(MODE_EMAIL) || isPresent(EMAIL_FIELD);
    }

    @Step("Switch to email sign-in")
    public boolean switchToEmail() {
        if (isPresent(EMAIL_FIELD)) {
            return true;                       // already there
        }
        return click(MODE_EMAIL) && waitFor(EMAIL_FIELD, 10000);
    }

    /**
     * Sign in with email and password.
     *
     * DISMISS THE KEYBOARD BEFORE SUBMITTING. signin_submit sits at y≈1376–1513 and the IME
     * covers the lower third, so with the keyboard up the button is reported not-displayed and
     * the tap is dropped silently — the run then carries on as an unauthenticated session and
     * fails somewhere unrelated. Its bounds do not move, so the fix is dismissing the keyboard,
     * not scrolling.
     */
    @Step("Sign in as {email}")
    public boolean signIn(String email, String password) {
        if (!switchToEmail()) {
            return false;
        }
        if (!typeInto(EMAIL_FIELD, email) || !typeInto(PASSWORD_FIELD, password)) {
            return false;
        }
        closeKeyboard();
        pause(600);

        if (!click(SUBMIT)) {
            return false;
        }
        // Authentication is a network round-trip; the sign-in form going away is the signal.
        long end = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < end) {
            if (!isPresent(EMAIL_FIELD)) {
                return true;
            }
            pause(500);
        }
        return false;
    }

    private boolean typeInto(By field, String value) {
        WebElement el = first(field);
        if (el == null) {
            return false;
        }
        try {
            el.click();
            pause(400);
            el.sendKeys(value);
            pause(500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Close the IME WITHOUT leaving the screen.
     *
     * Do not use hideKeyboard() here. On Android it dismisses the IME by sending BACK, and this
     * app's back handler pops the whole sign-in screen: measured step by step, the app was on
     * Home immediately after the call, signin_submit no longer existed, and the run carried on
     * unauthenticated with no error. Guarding on isKeyboardShown() does not help — the keyboard
     * really is up; BACK simply does two things at once.
     *
     * performEditorAction fires the field's own "done" action instead, which closes the keyboard
     * and leaves the form intact. Verified: keyboard=false, form still present, submit displayed.
     */
    private void closeKeyboard() {
        try {
            if (((HasOnScreenKeyboard) driver).isKeyboardShown()) {
                driver.executeScript("mobile: performEditorAction", Map.of("action", "done"));
                pause(1200);
            }
        } catch (Exception ignored) {
            // Unsupported — the submit click below reports the real outcome.
        }
    }

    /**
     * Is the shopper signed in?
     *
     * Reads the CURRENT screen rather than navigating. An earlier version tapped profile_button
     * first, which reported "not signed in" for a signed-in app: on the Account screen that button
     * does not exist, so the tap failed and the method returned false for the very state it was
     * meant to confirm.
     */
    @Step("Check whether the shopper is signed in")
    public boolean isSignedIn() {
        if (isOnAccountScreen()) {
            return true;                       // already looking at it
        }
        if (isShowing()) {
            return false;                      // sign-in form up => guest
        }
        // Anywhere else: ask the profile icon and see where it lands. A successful sign-in leaves
        // the app on HOME, not on Account, so checking for the Account screen alone reported
        // "not signed in" immediately after signing in.
        if (!click(PROFILE_BUTTON)) {
            return false;
        }
        pause(3000);
        return isOnAccountScreen();
    }

    @Step("Check the phone sign-in mode offers OTP")
    public boolean phoneModeOffersOtp() {
        if (!isPresent(PHONE_FIELD) && !click(MODE_PHONE)) {
            return false;
        }
        return waitFor(PHONE_FIELD, 8000) && isPresent(SEND_OTP);
    }
}
