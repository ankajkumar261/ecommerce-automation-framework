package com.portfolio.ecommerce.mobile.base;

import com.portfolio.ecommerce.mobile.drivers.AppiumDriverManager;
import com.portfolio.ecommerce.mobile.flows.ShopperFlows;
import com.portfolio.ecommerce.mobile.screens.HomeScreen;
import com.portfolio.ecommerce.mobile.screens.LoginScreen;
import com.portfolio.ecommerce.website.config.ConfigReader;
import io.appium.java_client.AppiumDriver;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.OutputType;
import org.testng.Assert;
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
 * Base class for iOS (mobile) tests.
 *
 * ONE APP SESSION, ONE LOGIN, FOR THE WHOLE RUN.
 *
 * This used to start a fresh driver per test method, mirroring the web side. On mobile that
 * is the wrong shape: every test paid for a cold app launch, and because the app does not
 * keep its session across a relaunch, every test that needed a shopper also had to sign out
 * and sign back in — which is what kept breaking the flow half way through a run.
 *
 * Now the driver is created once ({@link AppiumDriverManager#getOrStartDriver()}) and torn
 * down at the end of the suite, and authentication is a run-scoped fixture:
 *
 *   ensureSignedIn()   signs in the FIRST time it is called and is a no-op after that
 *   ensureSignedOut()  drops back to guest, for the few tests that assert a guest gate
 *
 * so a full suite costs one login instead of one per test. What each test still owns is its
 * own DATA state — the cart and the wishlist live on the server and persist between runs, so
 * tests keep clearing those for themselves.
 *
 * Between test methods the app is walked back to the Home tab rather than relaunched
 * ({@link #resetToHome()}), so a test that finished deep inside checkout does not strand the
 * next one on a screen it does not recognise.
 */
public abstract class MobileBaseTest {

    protected static final Logger log = LogManager.getLogger(MobileBaseTest.class);

    protected final ConfigReader config = ConfigReader.getInstance();

    /**
     * Auth state of the single app session shared by every test in the run.
     *
     * Static on purpose: it has to survive across test CLASSES, not just methods. It is only
     * ever set by the two helpers below, and cleared whenever the app is relaunched (which is
     * the one thing that silently logs the app out).
     */
    private static boolean signedIn;

    protected AppiumDriver driver;

    /**
     * The shared setup journeys (empty the cart, add a product, go to checkout).
     * Rebuilt each test because it holds the driver, which a relaunch can replace.
     */
    protected ShopperFlows flows;

    @BeforeMethod(alwaysRun = true)
    public void setUp(ITestResult result) {
        log.info("▶ START: {}", result.getMethod().getMethodName());
        driver = AppiumDriverManager.getOrStartDriver();
        flows = new ShopperFlows(driver);
        resetToHome();
    }

    /**
     * Capture evidence, then put the app back in a state the next test can start from.
     *
     * The web BaseTest closes the browser here; mobile cannot, because the whole suite
     * shares one app session. So this method has to do the job the browser teardown does
     * for free: leave nothing behind that would cascade into the next test.
     */
    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        String testName = result.getMethod().getMethodName();

        switch (result.getStatus()) {
            case ITestResult.FAILURE -> {
                log.error("✗ FAILED: {}", testName);
                if (result.getThrowable() != null) {
                    log.error("Reason: {}", result.getThrowable().getMessage());
                }
                captureFailureEvidence(testName);
                recoverAfterFailure();
            }
            case ITestResult.SUCCESS -> log.info("✓ PASSED: {}", testName);
            case ITestResult.SKIP -> log.warn("⊘ SKIPPED: {}", testName);
            default -> { }
        }
    }

    @AfterSuite(alwaysRun = true)
    public void tearDownSuite() {
        AppiumDriverManager.quitDriver();
        signedIn = false;
    }

    // ── failure diagnostics ───────────────────────────────────

    /**
     * Screenshot AND page source, to disk and to Allure.
     *
     * The page source matters more than the picture on iOS: the commonest failure here is
     * "the element is in the tree but not displayed" — covered by the keyboard, behind a
     * system prompt, or scrolled off — and a screenshot cannot tell you which. The dumps
     * in test-output/recon are the same format, so a failure attachment can be diffed
     * against a known-good one.
     *
     * Everything is individually guarded: if the session is already dead, capturing
     * evidence must not turn a test FAILURE into a configuration ERROR and bury the
     * original assertion message.
     */
    private void captureFailureEvidence(String testName) {
        if (driver == null) {
            log.warn("No driver — cannot capture evidence for {}", testName);
            return;
        }
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        try {
            byte[] png = driver.getScreenshotAs(OutputType.BYTES);
            Path file = Paths.get(config.getScreenshotDir(), testName + "_" + stamp + ".png");
            Files.createDirectories(file.getParent());
            Files.write(file, png);
            Allure.addAttachment("Screenshot on failure — " + testName,
                    "image/png", new ByteArrayInputStream(png), "png");
            log.info("Screenshot saved: {}", file);
        } catch (Exception e) {
            log.error("Could not capture a screenshot for {}: {}", testName, e.getMessage());
        }

        try {
            String source = driver.getPageSource();
            Path file = Paths.get(config.getScreenshotDir(), testName + "_" + stamp + "-source.xml");
            Files.createDirectories(file.getParent());
            Files.writeString(file, source, StandardCharsets.UTF_8);
            Allure.addAttachment("Page source on failure — " + testName,
                    "application/xml", source, ".xml");
            log.info("Page source saved: {}", file);
        } catch (Exception e) {
            log.error("Could not capture the page source for {}: {}", testName, e.getMessage());
        }
    }

    /**
     * Relaunch the app after a failure rather than trusting resetToHome() to unwind it.
     *
     * resetToHome() does recover from most wreckage, but its escape hatch is
     * HomeScreen#isLoaded(), which polls for 30 seconds before giving up. A test that
     * dies under a modal therefore costs the NEXT test half a minute before anything
     * happens. A relaunch is ~2.5s and deterministic, so we pay that instead.
     *
     * The app drops its session across a relaunch, so the auth flag is cleared with it and
     * the next ensureSignedIn() signs in again.
     */
    private void recoverAfterFailure() {
        try {
            AppiumDriverManager.relaunchApp();
            signedIn = false;
        } catch (Exception e) {
            log.warn("Could not relaunch the app after a failure: {}", e.getMessage());
        }
    }

    protected AppiumDriver getDriver() {
        return driver;
    }

    // ── run-scoped authentication ─────────────────────────────

    /**
     * Make sure the shopper is signed in, signing in only if this run has not already done so.
     * Every test that needs an account calls this instead of driving LoginScreen itself.
     */
    @Step("Ensure the shopper is signed in")
    protected void ensureSignedIn() {
        if (signedIn) {
            logStep("Already signed in — reusing this run's session");
            return;
        }
        ConfigReader config = ConfigReader.getInstance();
        LoginScreen login = new LoginScreen(driver);

        logStep("Signing in for this run as " + config.getCustomerEmail());
        login.loginAs(config.getCustomerEmail(), config.getCustomerPassword());
        Assert.assertTrue(login.isSignedIn(), "Precondition: the customer should be signed in");

        signedIn = true;
        resetToHome();   // isSignedIn() verifies on Profile, so come back to Home
    }

    /**
     * Drop the app back to the guest state, for the tests that assert a sign-in gate.
     * The next {@link #ensureSignedIn()} will sign in again, so ordering these tests early
     * (see mobile.xml) keeps the run down to a single login.
     */
    @Step("Ensure the app is in the guest state")
    protected void ensureSignedOut() {
        LoginScreen login = new LoginScreen(driver);
        if (signedIn) {
            logStep("Signing out — this test needs the guest state");
        }
        login.logoutIfLoggedIn();
        signedIn = false;
        Assert.assertTrue(login.isGuest(), "Precondition: the app should be a guest");
        resetToHome();
    }

    /**
     * Record that the app is signed in, for the login test itself — it performs the sign-in
     * as its subject rather than as a fixture, and the rest of the run should inherit it.
     */
    protected void markSignedIn() {
        signedIn = true;
    }

    /** True if this run has already signed in. */
    protected static boolean isSessionSignedIn() {
        return signedIn;
    }

    /**
     * Cold-restart the app, for the tests whose SUBJECT is what survives a relaunch.
     *
     * The app does not keep its session across this, so the auth flag is cleared with it and
     * the caller must ensureSignedIn() again before touching anything account-scoped. That
     * cost is why these tests belong in the last suite block — every test after one of them
     * pays for a fresh sign-in.
     */
    @Step("Relaunch the app")
    protected void relaunchApp() {
        AppiumDriverManager.relaunchApp();
        signedIn = false;
    }

    // ── inter-test state hygiene ──────────────────────────────

    /**
     * Put the app back on the Home tab before handing it to the next test.
     *
     * Cheap in the normal case (it is a couple of Back taps and a tab tap), and the reason we
     * do not need to relaunch between tests. If the app is somewhere the screen objects
     * cannot unwind, we fall back to a relaunch — which logs the app out, so the auth flag is
     * cleared with it and the next ensureSignedIn() signs in again.
     */
    @Step("Reset the app to the Home tab")
    protected void resetToHome() {
        try {
            LoginScreen login = new LoginScreen(driver);
            // Clear the iOS "Save Password?" prompt FIRST. It sits above everything, so while it
            // is up every check below reads the wrong thing and every tap goes to the prompt
            // instead of the app. iOS can present it well after the login that triggered it.
            login.dismissSavePasswordPrompt();
            if (login.isSignInSheetShown()) {
                login.dismissSignInSheet();
            }
            HomeScreen home = new HomeScreen(driver);
            home.open();
            if (!home.isLoaded()) {
                throw new IllegalStateException("Home did not load after resetting");
            }
        } catch (Exception e) {
            log.warn("Could not get back to Home ({}) — relaunching the app", e.getMessage());
            AppiumDriverManager.relaunchApp();
            signedIn = false;   // the app does not keep its session across a relaunch
        }
    }

    // ── test helpers ──────────────────────────────────────────

    /**
     * Flags a known, still-open application defect. Parks the test as SKIPPED (yellow in
     * Allure) instead of failing, so the build stays green, the case lands in the Allure
     * "Known Bugs" category (categories.json matches "KNOWN BUG"), real failures elsewhere
     * still break the build, and the test turns green on its own once the defect is fixed
     * and the guard stops being hit.
     *
     * Same contract as the web BaseTest — see com.portfolio.ecommerce.website.base.BaseTest#knownBug.
     */
    protected void knownBug(String ticket, String details) {
        throw new SkipException("KNOWN BUG " + ticket + ": " + details);
    }

    /**
     * Guard for the tests that place a REAL Cash-on-Delivery order. Orders are append-only
     * on the shared account and the app cannot undo one, so these stay off unless somebody
     * asks for them: -Dmobile.place.orders=true
     */
    protected void requireOrderPlacementEnabled() {
        if (!config.isMobileOrderPlacementEnabled()) {
            throw new SkipException(
                    "Order placement is off (mobile.place.orders=false). This test creates a real "
                            + "order that cannot be undone — enable it deliberately with "
                            + "-Dmobile.place.orders=true on a dev run.");
        }
    }

    @Step("{message}")
    protected void logStep(String message) {
        log.info("  → {}", message);
    }

    // ── test-level scrolling and timing helpers ───────────────

    /**
     * Swipe up on the screen to reveal content further down.
     *
     * Duplicated from BaseScreen's protected methods because MobileBaseTest does not extend
     * BaseScreen — it holds the driver, not a specific screen. Tests that need to scroll
     * outside a screen object call this instead.
     */
    protected void scrollDown() {
        try {
            var size = driver.manage().window().getSize();
            int x = size.width / 2;
            int startY = (int) (size.height * 0.8);
            int endY   = (int) (size.height * 0.25);
            var finger = new org.openqa.selenium.interactions.PointerInput(
                    org.openqa.selenium.interactions.PointerInput.Kind.TOUCH, "finger");
            var swipe = new org.openqa.selenium.interactions.Sequence(finger, 1);
            swipe.addAction(finger.createPointerMove(java.time.Duration.ZERO,
                    org.openqa.selenium.interactions.PointerInput.Origin.viewport(), x, startY));
            swipe.addAction(finger.createPointerDown(
                    org.openqa.selenium.interactions.PointerInput.MouseButton.LEFT.asArg()));
            swipe.addAction(finger.createPointerMove(java.time.Duration.ofMillis(600),
                    org.openqa.selenium.interactions.PointerInput.Origin.viewport(), x, endY));
            swipe.addAction(finger.createPointerUp(
                    org.openqa.selenium.interactions.PointerInput.MouseButton.LEFT.asArg()));
            driver.perform(java.util.Collections.singletonList(swipe));
        } catch (Exception ignored) { }
    }

    /** Swipe down to scroll content back up toward the top. */
    protected void scrollUp() {
        try {
            var size = driver.manage().window().getSize();
            int x = size.width / 2;
            int startY = (int) (size.height * 0.25);
            int endY   = (int) (size.height * 0.8);
            var finger = new org.openqa.selenium.interactions.PointerInput(
                    org.openqa.selenium.interactions.PointerInput.Kind.TOUCH, "finger");
            var swipe = new org.openqa.selenium.interactions.Sequence(finger, 1);
            swipe.addAction(finger.createPointerMove(java.time.Duration.ZERO,
                    org.openqa.selenium.interactions.PointerInput.Origin.viewport(), x, startY));
            swipe.addAction(finger.createPointerDown(
                    org.openqa.selenium.interactions.PointerInput.MouseButton.LEFT.asArg()));
            swipe.addAction(finger.createPointerMove(java.time.Duration.ofMillis(600),
                    org.openqa.selenium.interactions.PointerInput.Origin.viewport(), x, endY));
            swipe.addAction(finger.createPointerUp(
                    org.openqa.selenium.interactions.PointerInput.MouseButton.LEFT.asArg()));
            driver.perform(java.util.Collections.singletonList(swipe));
        } catch (Exception ignored) { }
    }

    /** Sleep for the given number of milliseconds, ignoring interruption. */
    protected void pause(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException ignored) { }
    }

    /** Quick, non-throwing presence check: true if at least one element matches the locator. */
    protected boolean isPresent(org.openqa.selenium.By by) {
        return !driver.findElements(by).isEmpty();
    }
}
