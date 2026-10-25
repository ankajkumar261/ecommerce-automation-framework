package com.portfolio.ecommerce.tests.mobile;

import com.portfolio.ecommerce.mobile.base.MobileBaseTest;
import com.portfolio.ecommerce.mobile.screens.LoginScreen;
import com.portfolio.ecommerce.website.config.ConfigReader;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * iOS login test:
 *   Profile (guest) -> Sign in / Create account -> Email -> credentials -> Sign in
 *   -> land back on the Home (Discover) screen.
 *
 * Watch the simulator while this runs — it types the email, types the password, and taps
 * Sign in on its own (the mobile equivalent of watching Chromium on the web side).
 *
 * This is the one test that drives LoginScreen directly instead of calling ensureSignedIn():
 * signing in is its subject, not its setup. It puts the app into the guest state first, and
 * afterwards hands the resulting session to the rest of the run via markSignedIn() — which is
 * why mobile.xml lists it early. Credentials come from config so this signs in as the SAME
 * shopper every other test expects to be using.
 */
@Epic("Mobile (iOS)")
@Feature("Login")
public class LoginTest extends MobileBaseTest {

    @Test(groups = {"mobile-smoke"}, description = "TC-IOS002: Customer signs in and lands on Home")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Email sign-in")
    @Description("From the guest Profile, opens Sign in, chooses Email, enters credentials, taps "
            + "Sign in, and verifies the app is genuinely authenticated afterwards.")
    public void customerSignsIn() {
        ConfigReader config = ConfigReader.getInstance();
        LoginScreen login = new LoginScreen(getDriver());

        logStep("Start from the guest state");
        ensureSignedOut();

        logStep("Sign in with email and password");
        login.openSignIn();
        login.enterEmail(config.getCustomerEmail());
        login.enterPassword(config.getCustomerPassword());
        login.tapSignIn();

        logStep("Verify login succeeded");
        Assert.assertTrue(login.isSignedIn(),
                "After Sign in the app should be authenticated (guest banner gone, Sign Out present)");

        // Hand this session to the rest of the run so nothing has to sign in again.
        markSignedIn();
    }
}
