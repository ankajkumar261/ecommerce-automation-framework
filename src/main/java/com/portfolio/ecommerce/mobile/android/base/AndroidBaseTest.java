package com.portfolio.ecommerce.mobile.android.base;

import com.portfolio.ecommerce.mobile.android.screens.AndroidHomeScreen;
import com.portfolio.ecommerce.mobile.drivers.AppBuildInfo;
import com.portfolio.ecommerce.mobile.drivers.AppiumDriverManager;
import com.portfolio.ecommerce.website.config.ConfigReader;
import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Allure;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.OutputType;
import org.testng.ITestResult;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Base class for the ANDROID tests.
 *
 * SEPARATE FROM MobileBaseTest ON PURPOSE. That class drives the iOS screen objects in its
 * setup and teardown (resetToHome -> HomeScreen, ensureSignedIn -> LoginScreen), all of which
 * use XCUITest-only locators. Inheriting it would drag iOS navigation into every Android test,
 * and — more importantly — any change needed for Android would land in a class the 45 passing
 * iOS checks depend on. Keeping them apart means the Android work cannot regress iOS.
 *
 * The shared pieces are the ones that are genuinely platform-neutral: AppiumDriverManager for
 * the session, AppBuildInfo for the build under test.
 *
 * ONE SESSION FOR THE RUN, like the iOS suite: the driver is created once and reused, and each
 * test starts from Home.
 */
public abstract class AndroidBaseTest {

    protected static final Logger log = LogManager.getLogger(AndroidBaseTest.class);
    protected final ConfigReader config = ConfigReader.getInstance();

    protected AppiumDriver driver;
    protected AndroidHomeScreen home;

    @BeforeMethod(alwaysRun = true)
    public void setUp(ITestResult result) {
        if (!AppiumDriverManager.isAndroid()) {
            throw new SkipException("Not an Android run — start this with -Pmobile-android, "
                    + "which sets mobile.platform=android.");
        }
        log.info("▶ START: {}", result.getMethod().getMethodName());
        driver = AppiumDriverManager.getOrStartDriver();
        home = new AndroidHomeScreen(driver);
        resetToHome();
    }

    /**
     * Put the app back on Home so the next test starts somewhere known.
     *
     * Back out of any pushed screen first: the bottom tab bar is not on every screen, so tapping
     * the Home tab straight away does nothing when a product detail is open.
     */
    protected void resetToHome() {
        try {
            // Already on a tabbed screen? Just tap Home.
            if (!driver.findElements(AndroidHomeScreen.TAB_HOME).isEmpty()) {
                home.goHome();
                return;
            }
            // One back for a single pushed screen (product detail), then re-check.
            driver.navigate().back();
            Thread.sleep(1500);
            if (!driver.findElements(AndroidHomeScreen.TAB_HOME).isEmpty()) {
                home.goHome();
                return;
            }
            // Deeper than that (cart -> checkout), relaunch rather than keep pressing back.
            // BACK FROM HOME EXITS THE APP: a loop that backs until it finds the tab bar walked
            // out to the device launcher and took the rest of the run with it.
            AppiumDriverManager.relaunchApp();
        } catch (Exception e) {
            log.warn("Could not get back to Home ({}) — relaunching", e.getMessage());
            AppiumDriverManager.relaunchApp();
        }
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        String name = result.getMethod().getMethodName();
        switch (result.getStatus()) {
            case ITestResult.FAILURE -> {
                log.error("✗ FAILED: {}", name);
                captureFailureEvidence(name);
            }
            case ITestResult.SUCCESS -> log.info("✓ PASSED: {}", name);
            case ITestResult.SKIP -> log.warn("⊘ SKIPPED: {}", name);
            default -> { }
        }
    }

    @AfterSuite(alwaysRun = true)
    public void tearDownSuite() {
        AppiumDriverManager.quitDriver();
    }

    /** Screenshot AND page source, like the iOS suite — the source usually explains more. */
    private void captureFailureEvidence(String testName) {
        if (driver == null) return;
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        try {
            byte[] png = driver.getScreenshotAs(OutputType.BYTES);
            Path file = Paths.get(config.getScreenshotDir(), "android_" + testName + "_" + stamp + ".png");
            Files.createDirectories(file.getParent());
            Files.write(file, png);
            Allure.addAttachment("Screenshot on failure — " + testName, "image/png",
                    new ByteArrayInputStream(png), "png");
        } catch (Exception e) {
            log.error("Could not capture a screenshot: {}", e.getMessage());
        }
        try {
            String src = driver.getPageSource();
            Path file = Paths.get(config.getScreenshotDir(), "android_" + testName + "_" + stamp + "-source.xml");
            Files.writeString(file, src, StandardCharsets.UTF_8);
            Allure.addAttachment("Page source on failure — " + testName, "application/xml", src, ".xml");
        } catch (Exception e) {
            log.error("Could not capture the page source: {}", e.getMessage());
        }
    }

    /**
     * Run-scoped sign-in, mirroring the iOS suite's ensureSignedIn.
     *
     * The Android app keeps its session across the shared run (noReset=true), so signing in once
     * is enough; a relaunch inside a test clears this flag so the next call signs in again.
     * Tests that touch account-gated screens (Account, Chat, Support) call this first.
     */
    private static boolean signedIn;

    protected void ensureSignedIn() {
        if (signedIn) return;
        com.portfolio.ecommerce.mobile.android.screens.AndroidLoginScreen login =
                new com.portfolio.ecommerce.mobile.android.screens.AndroidLoginScreen(driver);
        boolean ok = login.ensureSignedIn(config.getCustomerEmail(), config.getCustomerPassword());
        if (!ok) {
            throw new IllegalStateException("Could not sign in as " + config.getCustomerEmail());
        }
        signedIn = true;
        resetToHome();
    }

    /** Clears the run-scoped auth flag — call after a relaunch that drops the session. */
    protected void clearSignedIn() {
        signedIn = false;
    }

    protected void logStep(String message) {
        log.info("  → {}", message);
        Allure.step(message);
    }

    /** The app build these results describe. */
    protected String appBuild() {
        return AppBuildInfo.read(config.getMobileUdid(), config.getMobileAppPackage()).toString();
    }
}
