package com.portfolio.ecommerce.mobile.drivers;

import com.portfolio.ecommerce.website.config.ConfigReader;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.InteractsWithApps;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.time.Duration;

/**
 * Creates and holds the Appium driver for the Storefront customer app, on iOS or Android.
 *
 * Prerequisites to run an iOS test:
 *   1) Appium server running:   appium           (listening on http://127.0.0.1:4723)
 *   2) iPhone 17 Pro simulator booted, with the Storefront app installed
 *
 * Prerequisites to run an Android test:
 *   1) Appium server running:   appium
 *   2) An emulator booted or a device attached (`adb devices`), with the app installed
 *
 * WHICH PLATFORM IS DECIDED BY mobile.platform ("ios" / "android"), so the same suite code
 * targets either. The driver type is {@link AppiumDriver}, not IOSDriver: every call the
 * screen objects make is available on AppiumDriver, or on an interface both platform drivers
 * implement. The app-lifecycle calls (terminateApp / activateApp / queryAppState) are the
 * latter: AppiumDriver does NOT declare them, so they go through {@link #apps()} below.
 *
 * ONE SESSION PER RUN. The driver is created on first use and then reused by every test in
 * the run — see {@link #getOrStartDriver()}. Starting a driver per test method meant a cold
 * app launch per test (~15s each) and, because the app does not persist its session across a
 * relaunch, a full logout/login per test too. Everything now happens inside a single launch.
 *
 * Device, app and server details come from config-${env}.properties (the mobile.* keys)
 * and are each -D overridable, so a different device needs no recompile.
 */
public class AppiumDriverManager {

    private static final Logger log = LogManager.getLogger(AppiumDriverManager.class);

    private static final ConfigReader CONFIG = ConfigReader.getInstance();

    /**
     * The app under test. Read once so the value cannot drift mid-run, and exposed
     * because the screen objects need it too — this is the single source of truth
     * for the app id.
     *
     * The same string serves both platforms: iOS calls it a bundle id, Android an app package,
     * and this app happens to use "com.example.storefront" for both. If they ever diverge, this
     * becomes a platform switch rather than one constant.
     */
    public static final String BUNDLE_ID = CONFIG.getMobileBundleId();

    private static AppiumDriver driver;
    private static boolean shutdownHookRegistered;

    /** True when this run targets Android. The one place the platform string is interpreted. */
    public static boolean isAndroid() {
        return "android".equalsIgnoreCase(CONFIG.getMobilePlatform());
    }

    /**
     * The live driver as an app-lifecycle handle.
     *
     * AppiumDriver deliberately does not declare terminateApp / activateApp / queryAppState —
     * they belong to InteractsWithApps, which IOSDriver and AndroidDriver both implement. One
     * cast here keeps every caller platform-agnostic.
     */
    private static InteractsWithApps apps() {
        return (InteractsWithApps) driver;
    }

    private AppiumDriverManager() { }

    /**
     * The driver for this run: returns the live session if there is one, otherwise launches
     * the app. This is what the base test calls before every test method, so the first test
     * pays for the launch and the rest get it for free.
     */
    public static synchronized AppiumDriver getOrStartDriver() {
        if (isSessionAlive()) {
            return driver;
        }
        if (driver != null) {
            log.warn("The mobile session is no longer alive — starting a new one");
            quitDriver();
        }
        return startDriver();
    }

    /** Launch the app and return a ready driver for the configured platform. Always a cold launch. */
    public static synchronized AppiumDriver startDriver() {
        return isAndroid() ? startAndroidDriver() : startIosDriver();
    }

    /**
     * Android via UiAutomator2.
     *
     * Deliberately leaner than the iOS options below. The XCUITest tuning there exists to work
     * around costs specific to that driver (idle waits, snapshot depth); UiAutomator2 does not
     * have them, and adding speculative capabilities to a driver we have not profiled yet would
     * be guessing. Tune this when there is a measurement to tune against.
     */
    private static AppiumDriver startAndroidDriver() {
        String appiumUrl  = CONFIG.getAppiumUrl();
        String deviceName = CONFIG.getMobileDeviceName();
        String appPackage = CONFIG.getMobileAppPackage();
        String activity   = CONFIG.getMobileAppActivity();
        try {
            UiAutomator2Options options = new UiAutomator2Options()
                    .setDeviceName(deviceName)
                    .setAppPackage(appPackage)
                    .setAppActivity(activity)
                    // The launcher activity is not always the one left on screen once the app
                    // settles (splash screens, deep links). "*" accepts whichever it lands on
                    // rather than failing the session on a mismatch.
                    .setAppWaitActivity("*")
                    // Android 13+ asks for notification permission on first launch. Granting it
                    // up front stops a system dialog covering the app mid-test — the Android
                    // equivalent of the iOS "Save Password?" prompt problem.
                    .setAutoGrantPermissions(true)
                    .setDisableWindowAnimation(true);

            // noReset has no typed setter on UiAutomator2Options in java-client 9.3.0, so it goes
            // through the raw capability. Same intent as iOS: keep the app and its data installed
            // so the suite's one-session-one-login model holds.
            options.setCapability("appium:noReset", true);
            options.setCapability("appium:newCommandTimeout", 300);

            String udid = CONFIG.getMobileUdid();
            if (!udid.isEmpty()) {
                options.setUdid(udid);
            }

            driver = new AndroidDriver(new URL(appiumUrl), options);
            driver.manage().timeouts().implicitlyWait(Duration.ZERO);
            registerShutdownHook();

            // Log the udid the SESSION actually bound to, not the configured device name.
            // The name is a label — "Android Emulator" appeared in the log for a run that was
            // really on a physical phone, which made it imphealthcareble to tell from the report which
            // device produced the results. This prints the truth.
            log.info("Android driver started | udid={} | device={} | app={}/{}",
                    actualUdid(), deviceName, appPackage, activity);
            return driver;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to start the Android Appium driver. Make sure the Appium server is "
                            + "running (`appium`) on " + appiumUrl + ", a device or emulator is "
                            + "attached (`adb devices`), and the app (" + appPackage + ") is "
                            + "installed on it.", e);
        }
    }

    /** iOS via XCUITest. */
    private static AppiumDriver startIosDriver() {
        String appiumUrl  = CONFIG.getAppiumUrl();
        String deviceName = CONFIG.getMobileDeviceName();
        try {
            XCUITestOptions options = new XCUITestOptions()
                    .setDeviceName(deviceName)
                    .setBundleId(BUNDLE_ID)
                    .setNoReset(true)                              // keep app installed (fast) — we log out in-app instead
                    .setForceAppLaunch(true)                       // relaunch the app fresh at the start of the run
                    .setShouldTerminateApp(true)                   // kill it first if it's already running
                    // ── PERFORMANCE ─────────────────────────────────────────────
                    // By default XCUITest waits for the app to be "idle" (animations +
                    // network quiescent) before EVERY command, up to ~10s each. This
                    // SwiftUI app rarely goes fully idle, so that wait was costing 30–40s
                    // per test. Drop it, and reduce motion, so commands run immediately.
                    .setWaitForIdleTimeout(Duration.ofMillis(CONFIG.getMobileWaitForIdleMs()))
                    .setReduceMotion(true)
                    // Generous: this session has to survive the WHOLE suite, including the
                    // gaps between test methods and between <test> blocks.
                    .setNewCommandTimeout(Duration.ofSeconds(300));

            // Optional in config: a UDID pins one specific simulator, and without it
            // XCUITest picks by name+version instead. Setting either to "" would make
            // the match fail outright, so only apply them when actually configured.
            String udid = CONFIG.getMobileUdid();
            if (!udid.isEmpty()) {
                options.setUdid(udid);
            }
            String platformVersion = CONFIG.getMobilePlatformVersion();
            if (!platformVersion.isEmpty()) {
                options.setPlatformVersion(platformVersion);
            }

            driver = new IOSDriver(new URL(appiumUrl), options);
            driver.manage().timeouts().implicitlyWait(Duration.ZERO);

            // Trim the accessibility-tree snapshot each findElements has to serialise (the
            // real cost on this content-heavy app). 40 is shallow enough to be faster than the
            // default 50 but deep enough to still reach the category chips / product cards.
            try {
                driver.setSetting("snapshotMaxDepth", CONFIG.getMobileSnapshotMaxDepth());
            } catch (Exception ignored) { }
            registerShutdownHook();

            log.info("iOS driver started | device={} | app={}", deviceName, BUNDLE_ID);
            return driver;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to start the iOS Appium driver. Make sure the Appium server is running "
                            + "(`appium`) on " + appiumUrl + " and the " + deviceName + " simulator is "
                            + "booted with the Storefront app (" + BUNDLE_ID + ") installed.", e);
        }
    }

    /**
     * The device this session is really bound to, read back from the negotiated capabilities.
     *
     * Worth having because device selection can be implicit: with a blank udid Appium takes
     * whichever device adb lists first, so a run can land on a different device than intended
     * and every log line still reads the same.
     */
    public static String actualUdid() {
        if (driver == null) {
            return "no-session";
        }
        try {
            Object udid = driver.getCapabilities().getCapability("deviceUDID");
            if (udid == null) {
                udid = driver.getCapabilities().getCapability("udid");
            }
            return udid == null ? "unreported" : String.valueOf(udid);
        } catch (Exception e) {
            return "unreported";
        }
    }

    /** Device/app details of the live session, for the Allure environment block. */
    public static String describeTarget() {
        String os = isAndroid() ? "Android" : "iOS";
        return CONFIG.getMobileDeviceName()
                + " (" + os + " " + CONFIG.getMobilePlatformVersion() + ") — " + BUNDLE_ID;
    }

    public static AppiumDriver getDriver() {
        return driver;
    }

    /** True if we hold a driver whose session the Appium server still recognises. */
    private static boolean isSessionAlive() {
        if (driver == null || driver.getSessionId() == null) {
            return false;
        }
        try {
            apps().queryAppState(BUNDLE_ID);   // cheapest round-trip that proves the session works
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Cold-restart the app WITHOUT throwing the driver session away.
     *
     * The recovery hatch for a test that leaves the app somewhere the screen objects cannot
     * navigate out of. Note that the app does not keep its login across this, so callers must
     * treat the shopper as signed out afterwards.
     */
    public static void relaunchApp() {
        if (driver == null) return;
        try {
            apps().terminateApp(BUNDLE_ID);
            apps().activateApp(BUNDLE_ID);
            Thread.sleep(2500);
            log.info("Relaunched the app inside the existing session");
        } catch (Exception e) {
            log.warn("Could not relaunch the app: {}", e.getMessage());
        }
    }

    public static synchronized void quitDriver() {
        if (driver != null) {
            try { driver.quit(); } catch (Exception ignored) { }
            driver = null;
            log.info("iOS driver quit");
        }
    }

    /** Release the simulator session even if the run is killed before @AfterSuite fires. */
    private static void registerShutdownHook() {
        if (shutdownHookRegistered) return;
        Runtime.getRuntime().addShutdownHook(new Thread(AppiumDriverManager::quitDriver));
        shutdownHookRegistered = true;
    }
}
