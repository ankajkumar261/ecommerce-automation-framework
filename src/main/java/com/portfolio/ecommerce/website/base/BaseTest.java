package com.portfolio.ecommerce.website.base;

import com.portfolio.ecommerce.website.config.ConfigReader;
import com.portfolio.ecommerce.website.drivers.BrowserManager;
import com.microsoft.playwright.Page;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.ITestResult;
import org.testng.SkipException;
import org.testng.annotations.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * BaseTest — Parent class for ALL test classes
 *
 * Every test class extends this. It handles:
 *   @BeforeSuite  → one-time setup (create output folders)
 *   @BeforeMethod → launch browser before each test
 *   @AfterMethod  → screenshot on failure + close browser
 *   @AfterSuite   → suite-level teardown log
 *
 * Usage:
 *   public class AdminLoginTest extends BaseTest { ... }
 */
public class BaseTest {

    private static final Logger log = LogManager.getLogger(BaseTest.class);
    protected ConfigReader config = ConfigReader.getInstance();

    // ── Which URL to open — subclasses can override ───────────
    // Default is admin URL. Override in vendor/storefront tests.
    protected String getUrl() {
        return config.getAdminUrl();
    }

    // ─────────────────────────────────────────────────────────
    // SUITE SETUP / TEARDOWN
    // ─────────────────────────────────────────────────────────

    @BeforeSuite(alwaysRun = true)
    public void suiteSetup() {
        log.info("╔══════════════════════════════════════════╗");
        log.info("║   ECOMMERCE AUTOMATION SUITE STARTING      ║");
        log.info("╚══════════════════════════════════════════╝");

        // Create output folders if they don't exist
        createFolder(config.getScreenshotDir());
        createFolder("test-output/logs");
        log.info("Output folders ready");
    }

    @AfterSuite(alwaysRun = true)
    public void suiteTeardown() {
        log.info("╔══════════════════════════════════════════╗");
        log.info("║   ECOMMERCE AUTOMATION SUITE COMPLETE      ║");
        log.info("╚══════════════════════════════════════════╝");
    }

    // ─────────────────────────────────────────────────────────
    // TEST SETUP / TEARDOWN (runs before & after EVERY test)
    // ─────────────────────────────────────────────────────────

    @BeforeMethod(alwaysRun = true)
    public void setUp(ITestResult result) {
        String testName = result.getMethod().getMethodName();
        log.info("▶ START: {}", testName);

        // Launch browser + create page instances
        BrowserManager.initBrowser();

        // Navigate primary page to the URL for this test class.
        // Retry once on a transient network error (e.g. a brief connection
        // refused / 5xx during a deploy) so a momentary hiccup doesn't cascade
        // the whole suite into skips. A genuinely down server still fails after
        // the retry — this masks flakiness, not a real outage.
        String url = getUrl();
        log.info("Navigating to: {}", url);
        com.microsoft.playwright.Page.NavigateOptions navOpts =
                new com.microsoft.playwright.Page.NavigateOptions()
                        .setTimeout(45000)
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED);
        try {
            BrowserManager.getPage().navigate(url, navOpts);
        } catch (com.microsoft.playwright.PlaywrightException e) {
            log.warn("First navigation to {} failed ({}). Retrying once in 3s...",
                    url, e.getMessage());
            sleep(3);
            BrowserManager.getPage().navigate(url, navOpts);
        }
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        String testName = result.getMethod().getMethodName();

        // ── On FAILURE: take screenshot + attach to Allure ───
        if (result.getStatus() == ITestResult.FAILURE) {
            log.error("✗ FAILED: {}", testName);
            log.error("Reason: {}", result.getThrowable().getMessage());
            takeScreenshotOnFailure(testName);
        }

        // ── On SUCCESS ────────────────────────────────────────
        else if (result.getStatus() == ITestResult.SUCCESS) {
            log.info("✓ PASSED: {}", testName);
        }

        // ── On SKIP ───────────────────────────────────────────
        else if (result.getStatus() == ITestResult.SKIP) {
            log.warn("⊘ SKIPPED: {}", testName);
        }

        // Always close browser — even if test threw an exception
        BrowserManager.quitBrowser();
    }

    // ─────────────────────────────────────────────────────────
    // SCREENSHOT HELPER
    // ─────────────────────────────────────────────────────────

    /**
     * Takes a full-page screenshot on test failure.
     * Saves to disk AND attaches to Allure report.
     */
    private void takeScreenshotOnFailure(String testName) {
        try {
            Page page = BrowserManager.getPage();
            if (page == null || page.isClosed()) {
                log.warn("Page already closed — cannot take screenshot for: {}", testName);
                return;
            }

            // Build filename: TestName_2024-06-12_14-30-55.png
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String fileName  = testName + "_" + timestamp + ".png";
            Path   filePath  = Paths.get(config.getScreenshotDir(), fileName);

            // Save to disk
            byte[] screenshot = page.screenshot(
                    new Page.ScreenshotOptions().setFullPage(true));
            Files.write(filePath, screenshot);
            log.info("Screenshot saved: {}", filePath);

            // Attach to Allure report
            Allure.addAttachment(
                    "Screenshot on failure — " + testName,
                    "image/png",
                    new ByteArrayInputStream(screenshot),
                    "png"
            );
            log.info("Screenshot attached to Allure report");

        } catch (IOException e) {
            log.error("Failed to save screenshot for {}: {}", testName, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error taking screenshot: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────
    // UTILITY METHODS (available to all test classes)
    // ─────────────────────────────────────────────────────────

    /**
     * Returns the primary Page (Admin / Storefront tests).
     */
    protected Page getPage() {
        return BrowserManager.getPage();
    }

    /**
     * Returns the secondary Page (Vendor panel tests).
     */
    protected Page getSecondaryPage() {
        return BrowserManager.getSecondaryPage();
    }

    /**
     * Flags a known, still-open application defect. Parks the current test as SKIPPED
     * (yellow in Allure) instead of failing, so:
     *   - the Maven build stays green (a skip does not fail the build),
     *   - the case lands in the Allure "Known Bugs" category (categories.json matches "KNOWN BUG"),
     *   - real failures in OTHER tests still break the build,
     *   - when the developer fixes the defect this guard is no longer hit, the real
     *     assertion after it runs, and the test turns green on its own.
     *
     * Usage inside a test:
     *   if (!appBehavesCorrectly) {
     *       knownBug("BOUT-123", "expected X but got Y");
     *   }
     *   // ...correct-behaviour assertion here (runs once the bug is fixed)...
     */
    protected void knownBug(String ticket, String details) {
        throw new SkipException("KNOWN BUG " + ticket + ": " + details);
    }

    /**
     * Adds a step log visible in both console and Allure report.
     * Use inside test methods to document what you're doing.
     *
     * Example:
     *   logStep("Entering admin credentials");
     */
    @Step("{stepDescription}")
    protected void logStep(String stepDescription) {
        log.info("  → {}", stepDescription);
    }

    /**
     * Pauses execution for N seconds — use sparingly.
     * Prefer Playwright's built-in waitFor methods instead.
     */
    protected void sleep(int seconds) {
        try {
            log.debug("Sleeping {}s", seconds);
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Creates a directory if it doesn't already exist.
     */
    private void createFolder(String path) {
        try {
            Files.createDirectories(Paths.get(path));
        } catch (IOException e) {
            log.warn("Could not create folder {}: {}", path, e.getMessage());
        }
    }
}