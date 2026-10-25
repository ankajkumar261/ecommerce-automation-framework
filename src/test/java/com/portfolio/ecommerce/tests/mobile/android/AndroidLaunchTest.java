package com.portfolio.ecommerce.tests.mobile.android;

import com.portfolio.ecommerce.mobile.drivers.AppBuildInfo;
import com.portfolio.ecommerce.mobile.drivers.AppiumDriverManager;
import com.portfolio.ecommerce.website.config.ConfigReader;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.By;
import io.qameta.allure.Description;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * The Android LAUNCH SPIKE — the one test that exists before the port proper.
 *
 * Its whole job is to prove the plumbing works end to end on Android: the driver factory picks
 * UiAutomator2, a session forms, the app starts, and the accessibility tree can be read. Those
 * are the things that break for reasons that have nothing to do with locators, and they are far
 * cheaper to debug on one test than on forty-five.
 *
 * IT DELIBERATELY DOES NOT EXTEND MobileBaseTest. That base class navigates with the iOS screen
 * objects (resetToHome -> HomeScreen), whose locators are XCUITest-only, so inheriting it would
 * fail for reasons unrelated to what is being proved here. When the screens become
 * platform-aware, the Android tests move onto MobileBaseTest like the iOS ones.
 *
 * LOCATORS: the app now exposes testTag values as resource-ids, so this matches on ids rather
 * than on visible copy. Home currently tags the bottom tabs, product cards and the wishlist
 * heart — 7 of its 14 clickable elements. The search field, category cards, top-bar icons and
 * "View all" are still untagged, so anything needing those still has no stable locator.
 */
public class AndroidLaunchTest {

    private static final Logger log = LogManager.getLogger(AndroidLaunchTest.class);

    /**
     * Test tags the app now exposes, verified present on the Home screen.
     *
     * These arrive as BARE resource-ids — "bottom_tab_home", not
     * "com.example.storefront:id/bottom_tab_home" — which is how Compose surfaces a testTag once
     * testTagsAsResourceId is on. Any check that greps for the package prefix will miss them.
     */
    private static final String TAB_HOME     = "bottom_tab_home";
    private static final String PRODUCT_CARD = "product_card";

    /**
     * Match a Compose test tag.
     *
     * NOT AppiumBy.id(). That strategy qualifies a bare id with the app package and looks for
     * "com.example.storefront:id/bottom_tab_home", which does not exist — Compose surfaces the tag
     * as the literal string. Measured on the live app: AppiumBy.id found 0 elements with the bare
     * name AND 0 with the package-prefixed name, while UiSelector().resourceId found them.
     *
     * UiSelector rather than XPath because resourceId is an indexed lookup; //*[@resource-id=...]
     * walks the whole tree, the shape BaseScreen's performance note warns about.
     */
    /** Poll for a tagged element rather than reading the tree once. */
    private static boolean awaitTag(String testTag, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (!AppiumDriverManager.getDriver().findElements(tag(testTag)).isEmpty()) return true;
            try { Thread.sleep(500); } catch (InterruptedException ignored) { return false; }
        }
        return false;
    }

    private static By tag(String testTag) {
        return AppiumBy.androidUIAutomator("new UiSelector().resourceId(\"" + testTag + "\")");
    }

    @Test(description = "TC-AND001: The app launches on Android and Home renders")
    @Severity(SeverityLevel.BLOCKER)
    @Story("App shell")
    @Description("Starts a UiAutomator2 session against the installed app, then verifies the "
            + "session is live, the app under test is the one in the foreground, and Home has "
            + "rendered. Proves the driver/session plumbing before any locator work begins.")
    public void appLaunchesAndHomeRenders() {
        ConfigReader config = ConfigReader.getInstance();

        Assert.assertTrue(AppiumDriverManager.isAndroid(),
                "This test only runs with mobile.platform=android. Run it with -Pmobile-android; "
                        + "the profile sets that for you.");

        AppiumDriver driver = AppiumDriverManager.getOrStartDriver();

        // COLD START, deliberately. noReset means the session resumes wherever the app was left,
        // and this test ran first with the app sitting on the Account screen from an earlier
        // session — no tabs there, so it reported "Home did not render" for a healthy app.
        // Relaunching is what makes "the app launches and Home renders" actually test that.
        AppiumDriverManager.relaunchApp();

        Assert.assertNotNull(driver.getSessionId(),
                "The Appium session did not form. Check that `appium` is running and a device is "
                        + "attached (`adb devices`).");

        log.info("App under test | {} | version {}",
                config.getMobileAppPackage(),
                AppBuildInfo.read(config.getMobileUdid(), config.getMobileAppPackage()));

        // The tree has to be readable at all — this is what UiAutomator2 gives every locator.
        String source = driver.getPageSource();
        Assert.assertNotNull(source, "Could not read the page source");
        Assert.assertTrue(source.contains(config.getMobileAppPackage()),
                "The app under test (" + config.getMobileAppPackage() + ") is not in the "
                        + "foreground. Something else is on screen.");

        // Locate by test tag, not by visible copy. The earlier version of this test matched the
        // string "Shop the categories" because the app exposed nothing else; it now exposes real
        // resource-ids, so the brittle text match is gone.
        // WAIT, do not check once. This runs first in the suite, immediately after the session
        // forms, and the app is still starting: an instant findElements sees an empty tree and
        // reports "Home did not render" for an app that renders fine a second later.
        Assert.assertTrue(awaitTag(TAB_HOME, 25000),
                "Home did not render within 25s: no element with resource-id '" + TAB_HOME + "'.");

        Assert.assertTrue(awaitTag(PRODUCT_CARD, 25000),
                "No product cards on Home within 25s: no element with resource-id '"
                        + PRODUCT_CARD + "'. The catalogue may be empty on this environment.");

        log.info("Tagged elements found | {}={} | {}={}",
                TAB_HOME, driver.findElements(tag(TAB_HOME)).size(),
                PRODUCT_CARD, driver.findElements(tag(PRODUCT_CARD)).size());
    }

    // NO @AfterClass teardown here. The suite now runs several Android classes and they share
    // one session; quitting after this class would force every later class to pay for a fresh
    // session. AndroidBaseTest owns the suite-scoped teardown, and AppiumDriverManager registers
    // a shutdown hook for the case where this class runs on its own.
}
