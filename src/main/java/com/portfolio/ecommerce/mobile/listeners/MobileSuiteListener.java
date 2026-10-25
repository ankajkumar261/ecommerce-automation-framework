package com.portfolio.ecommerce.mobile.listeners;

import com.portfolio.ecommerce.mobile.drivers.AppBuildInfo;
import com.portfolio.ecommerce.mobile.drivers.AppiumDriverManager;
import com.portfolio.ecommerce.website.config.ConfigReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.ISuite;
import org.testng.ISuiteListener;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Suite-level plumbing for the mobile (iOS) suites.
 *
 * Registered in mobile-smoke.xml / mobile-regression.xml only — deliberately NOT via
 * META-INF/services, which would attach it to the web suites too.
 *
 * Two jobs:
 *   1. Refuse to run against an environment the installed app cannot reach (see below).
 *   2. Write the Allure environment block, so a report says which simulator — and which app
 *      version and build number — produced it.
 *
 * Per-test logging lives in MobileBaseTest#tearDown, not here, so it can sit alongside
 * the failure evidence it belongs with.
 */
public class MobileSuiteListener implements ISuiteListener {

    private static final Logger log = LogManager.getLogger(MobileSuiteListener.class);

    @Override
    public void onStart(ISuite suite) {
        ConfigReader config = ConfigReader.getInstance();
        assertEnvIsReachable(config);

        // Read once here, not per test: the app cannot change mid-run (noReset keeps the
        // installed binary in place), and this shells out to simctl.
        AppBuildInfo app = AppBuildInfo.read(config.getMobileUdid(), config.getMobileBundleId());
        log.info("App under test | {} | version {}", config.getMobileBundleId(), app);

        writeAllureEnvironment(config, app);
    }

    /**
     * Fail the run immediately when -Denv names a backend the app build does not talk to.
     *
     * The app's backend is baked into the installed binary, so -Denv=live does not point
     * the app anywhere new — it only changes which config file the framework reads and what
     * ConfigReader logs. Left unguarded, `mvn test -Pmobile,live` produces a report stamped
     * ENV = LIVE that is in fact describing dev data. That is worse than no report.
     */
    private void assertEnvIsReachable(ConfigReader config) {
        String active    = ConfigReader.getEnv();
        String supported = config.getMobileSupportedEnv();

        if (!supported.equalsIgnoreCase(active)) {
            throw new IllegalStateException(
                    "The mobile suite cannot run against env '" + active + "'. The installed app "
                            + "build (" + AppiumDriverManager.BUNDLE_ID + ") only talks to '" + supported
                            + "', so this run would produce a report labelled '" + active
                            + "' while exercising '" + supported + "' data.\n"
                            + "Run the mobile suite without -Denv (or with -Denv=" + supported + "). "
                            + "If an app build pointed at '" + active + "' now exists, update "
                            + "mobile.supported.env in config-" + active + ".properties.");
        }
        log.info("Mobile target | env={} | {}", active, AppiumDriverManager.describeTarget());
    }

    /**
     * Allure reads environment.properties out of the results directory and renders it as
     * the report's Environment block. Without it the report cannot tell you which simulator,
     * app build or environment produced a given result — which matters most when comparing
     * a failure against a run from a different machine.
     */
    private void writeAllureEnvironment(ConfigReader config, AppBuildInfo app) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("Environment", ConfigReader.getEnv());
        env.put("Platform", config.getMobilePlatform());
        env.put("Device", config.getMobileDeviceName());
        env.put("Platform.Version", config.getMobilePlatformVersion());
        env.put("UDID", config.getMobileUdid());
        env.put("App.Bundle.Id", config.getMobileBundleId());
        env.put("App.Version", app.version());
        env.put("App.Build", app.build());
        env.put("Appium.Server", config.getAppiumUrl());
        env.put("Order.Placement.Enabled", String.valueOf(config.isMobileOrderPlacementEnabled()));
        env.put("Retry.Count", String.valueOf(config.getRetryCount()));

        StringBuilder body = new StringBuilder();
        env.forEach((k, v) -> body.append(k).append('=').append(v == null ? "" : v).append('\n'));

        try {
            Path dir = Paths.get(System.getProperty("allure.results.directory", "target/allure-results"));
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("environment.properties"), body.toString(), StandardCharsets.UTF_8);
            log.info("Allure environment written to {}", dir.resolve("environment.properties"));
        } catch (Exception e) {
            // Never fail a run over reporting metadata.
            log.warn("Could not write the Allure environment block: {}", e.getMessage());
        }
    }
}
