package com.portfolio.ecommerce.website.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * ConfigReader — Singleton
 *
 * Reads the environment's config file once at startup and exposes
 * typed getters to the rest of the framework.
 *
 * Which file is read is chosen by the 'env' selector:
 *   -Denv=dev   ->  config-dev.properties   (default when nothing is passed)
 *   -Denv=live  ->  config-live.properties
 * The TEST_ENV environment variable works too, for CI.
 *
 * Defaulting to dev is deliberate: a bare `mvn test` must never reach production.
 *
 * Usage anywhere in the project:
 *   ConfigReader config = ConfigReader.getInstance();
 *   String url = config.getAdminUrl();
 */
public class ConfigReader {

    private static final Logger log = LogManager.getLogger(ConfigReader.class);
    private static final String ENV = resolveEnv();
    private static final String CONFIG_FILE = "config-" + ENV + ".properties";

    /**
     * Resolves the target environment from -Denv, falling back to the TEST_ENV
     * variable, then to "dev". Live always has to be asked for explicitly.
     */
    private static String resolveEnv() {
        String env = System.getProperty("env");
        if (env == null || env.trim().isEmpty()) {
            env = System.getenv("TEST_ENV");
        }
        if (env == null || env.trim().isEmpty()) {
            env = "dev";
        }
        return env.trim().toLowerCase();
    }

    /** The environment this run is pointed at ("dev", "live", ...). */
    public static String getEnv() {
        return ENV;
    }

    // ── Singleton instance ────────────────────────────────────
    private static ConfigReader instance;
    private final Properties properties;

    // ── Private constructor (Singleton pattern) ───────────────
    private ConfigReader() {
        properties = new Properties();
        loadProperties();
    }

    /**
     * Returns the single shared instance.
     * Thread-safe via synchronized (safe for parallel TestNG runs).
     */
    public static synchronized ConfigReader getInstance() {
        if (instance == null) {
            instance = new ConfigReader();
        }
        return instance;
    }

    // ── Internal loader ───────────────────────────────────────
    private void loadProperties() {
        try (InputStream input = getClass()
                .getClassLoader()
                .getResourceAsStream(CONFIG_FILE)) {

            if (input == null) {
                throw new FileNotFoundException(
                        CONFIG_FILE + " not found in src/test/resources/ "
                        + "(env='" + ENV + "' — expected one of: dev, live)");
            }

            properties.load(input);

            // Log the environment on every run so a report can never be misread
            // as belonging to the wrong environment.
            log.info("═══ ENV = {} ═══  (config: {})", ENV.toUpperCase(), CONFIG_FILE);
            log.info("   admin      : {}", properties.getProperty("admin.url"));
            log.info("   storefront : {}", properties.getProperty("storefront.url"));
            log.info("   api        : {}", properties.getProperty("api.url"));

        } catch (IOException e) {
            log.error("Failed to load {}: {}", CONFIG_FILE, e.getMessage());
            throw new RuntimeException("Cannot load config file: " + CONFIG_FILE, e);
        }
    }

    // ─────────────────────────────────────────────────────────
    // GETTERS
    // ─────────────────────────────────────────────────────────

    // ── URLs ──────────────────────────────────────────────────

    public String getAdminUrl() {return get("admin.url");}

    public String getVendorUrl() {
        return get("vendor.url");
    }

    public String getStorefrontUrl() {
        return get("storefront.url");
    }

    /** Backend REST API base, e.g. https://.../api (used by API-level assertions). */
    public String getApiUrl() {
        return get("api.url");
    }

    // ── Browser ───────────────────────────────────────────────

    /**
     * Returns browser name: chromium | firefox | webkit
     * Can be overridden via system property: -Dbrowser=firefox
     */
    public String getBrowser() {
        // System property takes priority (useful for CI)
        String sysProp = System.getProperty("browser");
        return (sysProp != null && !sysProp.isEmpty())
                ? sysProp
                : get("browser");
    }

    /**
     * Returns true if headless mode is enabled.
     * Can be overridden via system property: -Dheadless=true
     */
    public boolean isHeadless() {
        String sysProp = System.getProperty("headless");
        if (sysProp != null && !sysProp.isEmpty()) {
            return Boolean.parseBoolean(sysProp);
        }
        return Boolean.parseBoolean(get("headless"));
    }

    /**
     * SlowMo delay in milliseconds (0 = off).
     */
    public int getSlowMo() {
        return getInt("slow.mo");
    }

    // ── Timeouts ──────────────────────────────────────────────

    public int getPageLoadTimeout() {
        return getInt("page.load.timeout");
    }

    public int getElementTimeout() {
        return getInt("element.timeout");
    }

    // ── Credentials ───────────────────────────────────────────

    public String getAdminEmail() {
        return get("admin.email");
    }

    public String getAdminPassword() {
        return get("admin.password");
    }

    public String getVendorEmail() {
        return get("vendor.email");
    }

    public String getVendorPassword() {
        return get("vendor.password");
    }

    public String getCustomerEmail() {
        return get("customer.email");
    }

    public String getCustomerPassword() {
        return get("customer.password");
    }

    /**
     * Name of the storefront owned by the configured vendor, as shown in the
     * add-product wizard's storefront picker. Per-environment test data.
     */
    public String getVendorStorefront() {
        return get("vendor.storefront");
    }

    /** A product category that exists in this environment's catalogue. */
    public String getVendorCategory() {
        return get("vendor.category");
    }

    /**
     * Name of a product in this environment's catalogue that has codEnabled=true, i.e. one
     * that actually offers Cash on Delivery at checkout.
     *
     * COD is a per-product flag, not a global setting: a cart holding any non-COD item shows
     * only "Pay online" and the notice "An item in your order is online-only...". Checkout
     * tests therefore have to put THIS product in the cart — picking "the first product on
     * Home" lands on the Razorpay path, which is not automatable. Per-environment test data:
     * on dev exactly one product qualifies, so if this goes stale the checkout tests fail for
     * data reasons rather than script reasons.
     */
    public String getCodProduct() {
        return get("checkout.cod.product");
    }

    /** A verified, pending, not-onboarded vendor — used by the approval-gate suite. */
    public String getPendingVendorEmail() {
        return get("vendor.pending.email");
    }

    public String getPendingVendorPassword() {
        return get("vendor.pending.password");
    }

    // ── Misc ──────────────────────────────────────────────────

    public String getScreenshotDir() {
        return get("screenshot.dir");
    }

    public int getRetryCount() {
        return getInt("retry.count");
    }

    // ── Online payment (Razorpay test mode) ──────────────────
    // Master switch: online-payment automation only runs when this is true, so it can
    // never fire against a live (real-money) gateway. Override with -Dpayment.testmode=true.
    // Optional key — defaults to false when absent from config.properties.

    public boolean isPaymentTestMode() {
        String sysProp = System.getProperty("payment.testmode");
        if (sysProp != null && !sysProp.isEmpty()) {
            return Boolean.parseBoolean(sysProp);
        }
        return Boolean.parseBoolean(getOptional("payment.testmode", "false"));
    }

    /** Razorpay test card number (defaults to the canonical 4111... Visa test card). */
    public String getTestCardNumber() {
        return getOptional("payment.test.card", "4111 1111 1111 1111");
    }

    // ── Mobile (iOS / Appium) ────────────────────────────────
    // Every getter here is OPTIONAL with a fallback, deliberately: these keys are
    // new, and a config file that predates them must still load for the web suites.
    // Each is -D overridable, but only because pom.xml forwards it to the forked
    // JVM in <systemPropertyVariables> — Surefire does not pass -D through by default.

    /** Target mobile platform: "ios" or "android". Decides which driver AppiumDriverManager builds. */
    public String getMobilePlatform() {
        return mobileProp("mobile.platform", "ios");
    }

    public String getAppiumUrl() {
        return mobileProp("mobile.appium.url", "http://127.0.0.1:4723");
    }

    /**
     * DEVICE KEYS ARE PER-PLATFORM, and must never fall back across platforms.
     *
     * mobile.udid / mobile.device.name / mobile.platform.version in config-${env}.properties
     * describe the iOS simulator. An Android run that fell back to them was handed the iOS
     * simulator's UDID and iOS version, and the session failed with a message about adb — the
     * cause and the symptom in completely different places. So Android reads its own
     * mobile.android.* keys and falls back to BLANK, which Appium reads as "pick the attached
     * device" rather than "look for a device that cannot exist".
     */
    private boolean isAndroidTarget() {
        return "android".equalsIgnoreCase(getMobilePlatform());
    }

    public String getMobileDeviceName() {
        return isAndroidTarget()
                ? mobileProp("mobile.android.device.name", "Android Emulator")
                : mobileProp("mobile.device.name", "iPhone 17 Pro");
    }

    public String getMobileUdid() {
        return isAndroidTarget()
                ? mobileProp("mobile.android.udid", "")
                : mobileProp("mobile.udid", "");
    }

    public String getMobilePlatformVersion() {
        return isAndroidTarget()
                ? mobileProp("mobile.android.platform.version", "")
                : mobileProp("mobile.platform.version", "");
    }

    public String getMobileBundleId() {
        return mobileProp("mobile.bundle.id", "com.example.storefront");
    }

    // ── Android only ──────────────────────────────────────────
    // The Android build uses the same identifier as the iOS bundle id, so the package defaults
    // to it rather than repeating the string. Read off the APK with:
    //   aapt2 dump badging app-release.apk

    /** Android app package. Defaults to the bundle id, which this app shares across platforms. */
    public String getMobileAppPackage() {
        String pkg = mobileProp("mobile.app.package", "");
        return pkg.isEmpty() ? getMobileBundleId() : pkg;
    }

    /** Android launcher activity — what Appium starts. */
    public String getMobileAppActivity() {
        return mobileProp("mobile.app.activity", "com.example.storefront.MainActivity");
    }

    public int getMobileWaitForIdleMs() {
        return mobileInt("mobile.wait.for.idle.ms", 200);
    }

    public int getMobileSnapshotMaxDepth() {
        return mobileInt("mobile.snapshot.max.depth", 40);
    }

    /**
     * Master switch for the mobile tests that place a REAL Cash-on-Delivery order.
     * False by default: orders are append-only on the shared account and the app
     * offers no way to undo one. Turn on deliberately: -Dmobile.place.orders=true
     */
    public boolean isMobileOrderPlacementEnabled() {
        return Boolean.parseBoolean(mobileProp("mobile.place.orders", "false"));
    }

    /**
     * The only environment the installed app build actually talks to. The mobile
     * listener compares this against {@link #getEnv()} and aborts the run when they
     * disagree, so a mobile report can never be stamped with the wrong environment.
     */
    public String getMobileSupportedEnv() {
        return mobileProp("mobile.supported.env", "dev");
    }

    /** System property wins (for -D overrides), then config, then the fallback. */
    private String mobileProp(String key, String fallback) {
        String sysProp = System.getProperty(key);
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            return sysProp.trim();
        }
        return getOptional(key, fallback);
    }

    private int mobileInt(String key, int fallback) {
        try {
            return Integer.parseInt(mobileProp(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            log.warn("Property '{}' is not an integer — falling back to {}", key, fallback);
            return fallback;
        }
    }

    // ─────────────────────────────────────────────────────────
    // INTERNAL HELPERS
    // ─────────────────────────────────────────────────────────

    /**
     * Reads a required String property.
     * Throws clearly if key is missing — no silent nulls.
     */
    private String get(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new RuntimeException(
                    "Missing required property '" + key + "' in " + CONFIG_FILE);
        }
        return value.trim();
    }

    /**
     * Reads an optional String property, returning {@code fallback} when the key is
     * absent or blank (never throws). Use for keys that need not exist in config.properties.
     */
    private String getOptional(String key, String fallback) {
        String value = properties.getProperty(key);
        return (value == null || value.trim().isEmpty()) ? fallback : value.trim();
    }

    /**
     * Reads a required Integer property.
     */
    private int getInt(String key) {
        try {
            return Integer.parseInt(get(key));
        } catch (NumberFormatException e) {
            throw new RuntimeException(
                    "Property '" + key + "' must be an integer in " + CONFIG_FILE);
        }
    }
}