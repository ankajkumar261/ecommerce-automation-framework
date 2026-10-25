package com.portfolio.ecommerce.tests.mobile.android;

import com.portfolio.ecommerce.mobile.android.base.AndroidBaseTest;
import com.portfolio.ecommerce.mobile.android.screens.AndroidLoginScreen;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Sign-in on Android.
 *
 * This is the test that unlocks the rest of the port: cart, wishlist, orders and checkout all
 * need an authenticated session, and until the app exposed profile_button and the sign-in field
 * tags there was no way to reach the sign-in screen at all.
 */
@Epic("Mobile — Android")
@Feature("Authentication")
public class AndroidLoginTest extends AndroidBaseTest {

    @Test(description = "TC-AND010: The profile icon opens sign-in for a guest")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Sign in")
    @Description("A guest tapping the profile icon reaches the sign-in screen, which opens on the "
            + "phone tab and offers an email alternative. This is the entry point every "
            + "authenticated test depends on.")
    public void profileIconOpensSignInForAGuest() {
        AndroidLoginScreen login = new AndroidLoginScreen(driver);

        logStep("Tap the profile icon");
        login.tapProfile();

        // Decide AFTER tapping, not before. Checking up front asked "are we on Account?" while
        // still on Home — the answer was no, the test proceeded, and then failed because the
        // profile icon led to Account rather than sign-in. Where the tap LANDS is the only
        // reliable signal, and the app keeps its session across tests.
        if (login.isOnAccountScreen()) {
            throw new org.testng.SkipException(
                    "The shopper is already signed in on this device, so the guest entry point "
                            + "cannot be exercised. Sign out in the app and re-run to cover it.");
        }

        Assert.assertTrue(login.isShowing(),
                "The profile icon opened neither the sign-in screen nor the Account screen");

        logStep("The phone tab offers OTP");
        Assert.assertTrue(login.phoneModeOffersOtp(),
                "The phone sign-in mode does not offer Send OTP");
    }

    @Test(description = "TC-AND011: A shopper can sign in with email and password")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Sign in")
    @Description("Signs in through the email tab and verifies the authenticated Account screen is "
            + "reached. Tolerant of the app already being signed in, since the suite shares one "
            + "session across tests.")
    public void shopperCanSignInWithEmail() {
        AndroidLoginScreen login = new AndroidLoginScreen(driver);

        logStep("Ensure a signed-in session as " + config.getCustomerEmail());
        Assert.assertTrue(
                login.ensureSignedIn(config.getCustomerEmail(), config.getCustomerPassword()),
                "Could not sign in. Check the credentials in config-dev.properties, and that the "
                        + "sign-in form still exposes signin_email / signin_password / signin_submit.");

        logStep("Verify the Account screen");
        Assert.assertTrue(login.isSignedIn(),
                "Signed in but the Account screen was not reached");
    }
}
