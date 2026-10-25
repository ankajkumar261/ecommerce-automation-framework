package com.portfolio.ecommerce.tests.mobile;

import com.portfolio.ecommerce.mobile.base.MobileBaseTest;
import com.portfolio.ecommerce.mobile.screens.LoginScreen;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * iOS sign-in, the paths that must FAIL.
 *
 * The web suite has five of these (StorefrontLoginTest); mobile had none — every login test was
 * the happy path, which means nothing was checking that the app actually refuses bad
 * credentials rather than waving them through.
 *
 * WHAT THESE ASSERT, AND WHY IT IS NOT THE ERROR MESSAGE: each test asserts the app is NOT
 * AUTHENTICATED afterwards, via LoginScreen#isGuest — the guest banner present, or the
 * "Sign in / Create account" button still offered. The error copy the app shows for a bad
 * password is not evidenced anywhere in the recon dumps, so asserting on specific wording
 * would be inventing a contract; asserting on auth state is the thing that actually matters
 * and cannot be satisfied by accident.
 *
 * Note that the sheet closing is explicitly NOT treated as success or failure. It also closes
 * when dismissed, which is exactly the confusion LoginScreen#isSignedIn was written to avoid.
 *
 * SUITE PLACEMENT: these belong in the guest block, before LoginTest. They start signed out and
 * leave the app signed out, so running them early costs nothing; running them after the shared
 * sign-in would throw it away and make every later test pay for a fresh login.
 */
@Epic("Mobile (iOS)")
@Feature("Login")
public class LoginNegativeTest extends MobileBaseTest {

    private static final String WRONG_PASSWORD = "WrongPassword@999";
    private static final String UNKNOWN_EMAIL  = "missing-shopper@example.test";

    /**
     * Attempt a sign-in that is expected to fail, and report whether the app stayed a guest.
     *
     * Deliberately drives LoginScreen directly rather than going through ensureSignedIn(),
     * because the run-scoped fixture exists to AVOID signing in twice — here the sign-in
     * attempt is the subject of the test.
     */
    private boolean attemptSignInAndCheckStillGuest(String email, String password) {
        ensureSignedOut();

        LoginScreen login = new LoginScreen(getDriver());
        login.openSignIn();
        login.enterEmail(email);
        login.enterPassword(password);
        login.tapSignIn();
        settleAfterSubmit(login);

        boolean stillGuest = login.isGuest();
        if (!stillGuest) {
            // Leave the app the way the next test expects to find it.
            login.logoutIfLoggedIn();
        }
        return stillGuest;
    }

    /**
     * Wait just long enough for the sign-in attempt to resolve.
     *
     * Polls for the sheet to close (a success would close it) instead of sleeping a flat 3
     * seconds. A rejection normally comes back well inside that, and these three tests are
     * paying the wait each — so the flat sleep was costing most of a minute a run for nothing.
     * If the sheet is still up at the end, that IS the expected outcome and the caller asserts it.
     */
    private void settleAfterSubmit(LoginScreen login) {
        long end = System.currentTimeMillis() + 4_000;
        while (System.currentTimeMillis() < end) {
            if (!login.isSignInSheetShown()) return;
            try { Thread.sleep(250); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Test(description = "TC-IOS041: Signing in with the wrong password is rejected")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Invalid credentials")
    @Description("Submits a real customer email with a wrong password and verifies the app is "
            + "still a guest afterwards — the single most important negative case, because "
            + "letting this through would authenticate anyone who knows an email address.")
    public void signingInWithTheWrongPasswordIsRejected() {
        logStep("Attempt to sign in as " + config.getCustomerEmail() + " with a wrong password");
        boolean stillGuest = attemptSignInAndCheckStillGuest(
                config.getCustomerEmail(), WRONG_PASSWORD);

        Assert.assertTrue(stillGuest,
                "A wrong password must NOT sign the shopper in. The app reported an "
                        + "authenticated state after submitting '" + WRONG_PASSWORD + "' for "
                        + config.getCustomerEmail() + ".");
    }

    @Test(description = "TC-IOS042: Signing in with an unknown email is rejected")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Invalid credentials")
    @Description("Submits an email address that has no account and verifies the app stays a "
            + "guest rather than creating or assuming one.")
    public void signingInWithAnUnknownEmailIsRejected() {
        logStep("Attempt to sign in with an address that has no account");
        boolean stillGuest = attemptSignInAndCheckStillGuest(
                UNKNOWN_EMAIL, config.getCustomerPassword());

        Assert.assertTrue(stillGuest,
                "An unknown email must NOT sign anyone in. The app reported an authenticated "
                        + "state for '" + UNKNOWN_EMAIL + "', which has no account.");
    }

    @Test(description = "TC-IOS043: Signing in with an empty password is rejected")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Invalid credentials")
    @Description("Submits a valid email with the password field left blank. The app may either "
            + "keep the Sign in button unusable or reject the submission; either is correct, and "
            + "both are covered by asserting the shopper is not authenticated afterwards.")
    public void signingInWithAnEmptyPasswordIsRejected() {
        ensureSignedOut();

        LoginScreen login = new LoginScreen(getDriver());
        login.openSignIn();
        login.enterEmail(config.getCustomerEmail());

        logStep("Leave the password blank and try to submit");
        // tapSignIn throws when the button never becomes usable, which is itself a PASS here:
        // a disabled Sign in button is a perfectly good way to refuse an empty password.
        boolean submissionWasBlocked = false;
        try {
            login.tapSignIn();
        } catch (Exception e) {
            submissionWasBlocked = true;
            log.info("Submission was blocked outright: {}", e.getMessage());
        }

        if (!submissionWasBlocked) {
            settleAfterSubmit(login);
        }

        boolean stillGuest = login.isGuest();
        if (!stillGuest) {
            login.logoutIfLoggedIn();
        }

        Assert.assertTrue(stillGuest,
                "An empty password must NOT sign the shopper in, whether the app blocks the "
                        + "button or rejects the submission.");
    }
}
