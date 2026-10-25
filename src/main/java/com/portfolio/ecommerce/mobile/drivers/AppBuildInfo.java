package com.portfolio.ecommerce.mobile.drivers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * The marketing version and build number of the app build the suite actually ran against.
 *
 * A report that says "45 checks passed on iPhone 17 Pro" is only half an answer — the other
 * half is WHICH build passed them. Without it a green run cannot be tied back to the binary
 * that produced it, and a regression cannot be bisected to the build that introduced it.
 *
 * Why this reads the simulator rather than asking Appium: XCUITest exposes the running app's
 * bundle id and pid (`mobile: activeAppInfo`) but not its version, so the only source of truth
 * is the installed bundle's own Info.plist. `simctl get_app_container` hands us its path.
 * That also means this works before the driver starts, which is when the suite listener needs it.
 *
 * Nothing here throws. A missing version is a gap in a report; it is not a reason to fail a
 * run that is otherwise fine, so every failure path degrades to {@link #unknown()}.
 */
public record AppBuildInfo(String version, String build) {

    private static final Logger log = LogManager.getLogger(AppBuildInfo.class);

    /** What both fields hold when the app could not be inspected. */
    public static final String UNKNOWN = "unknown";

    /** simctl on a cold simulator is not instant, but it is nowhere near this slow. */
    private static final int TIMEOUT_SECONDS = 30;

    public static AppBuildInfo unknown() {
        return new AppBuildInfo(UNKNOWN, UNKNOWN);
    }

    public boolean isKnown() {
        return !UNKNOWN.equals(version) || !UNKNOWN.equals(build);
    }

    /** "1.4.2 (183)" — the form used in logs and the Allure environment block. */
    @Override
    public String toString() {
        return isKnown() ? version + " (" + build + ")" : UNKNOWN;
    }

    /**
     * Read the version and build of {@code bundleId} as installed on the target simulator.
     *
     * @param udid     the simulator to inspect; blank falls back to whichever one is booted,
     *                 mirroring how AppiumDriverManager treats a blank mobile.udid
     * @param bundleId the app under test
     */
    public static AppBuildInfo read(String udid, String bundleId) {
        return AppiumDriverManager.isAndroid()
                ? readAndroid(udid, bundleId)
                : readIos(udid, bundleId);
    }

    private static AppBuildInfo readIos(String udid, String bundleId) {
        String device = (udid == null || udid.isBlank()) ? "booted" : udid;

        String container = exec("xcrun", "simctl", "get_app_container", device, bundleId, "app");
        if (container == null) {
            log.warn("Could not locate {} on simulator '{}' — the report will not name an app build. "
                            + "Is the app installed on it?", bundleId, device);
            return unknown();
        }

        String plist  = container + "/Info.plist";
        String version = plistValue(plist, "CFBundleShortVersionString");
        String build   = plistValue(plist, "CFBundleVersion");

        if (version == null && build == null) {
            log.warn("Found {} at {} but could not read its version from Info.plist", bundleId, container);
            return unknown();
        }
        return new AppBuildInfo(
                version == null ? UNKNOWN : version,
                build   == null ? UNKNOWN : build);
    }

    /**
     * Android: ask the package manager on the device.
     *
     * `dumpsys package <pkg>` prints, among much else, a line like
     *     versionCode=1 minSdk=26 targetSdk=35
     * and another
     *     versionName=1.0
     * which map onto the same two fields iOS gets from Info.plist — versionName is the
     * marketing version, versionCode the build number.
     */
    private static AppBuildInfo readAndroid(String udid, String appPackage) {
        String adb = adbPath();
        String out = (udid == null || udid.isBlank())
                ? exec(adb, "shell", "dumpsys", "package", appPackage)
                : exec(adb, "-s", udid, "shell", "dumpsys", "package", appPackage);

        if (out == null) {
            log.warn("Could not query {} via adb — the report will not name an app build. Is a "
                    + "device attached (`adb devices`) with the app installed?", appPackage);
            return unknown();
        }
        String version = firstMatch(out, "versionName=(\\S+)");
        String build   = firstMatch(out, "versionCode=(\\S+)");

        if (version == null && build == null) {
            log.warn("dumpsys returned no versionName/versionCode for {}", appPackage);
            return unknown();
        }
        return new AppBuildInfo(
                version == null ? UNKNOWN : version,
                build   == null ? UNKNOWN : build);
    }

    private static String firstMatch(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Locate adb. ANDROID_HOME first, because a Maven run does not necessarily inherit the
     * interactive shell's PATH; bare "adb" as the fallback for when it does.
     */
    private static String adbPath() {
        String home = System.getenv("ANDROID_HOME");
        if (home == null || home.isBlank()) {
            home = System.getenv("ANDROID_SDK_ROOT");
        }
        if (home != null && !home.isBlank()) {
            Path candidate = Path.of(home, "platform-tools", "adb");
            if (Files.isExecutable(candidate)) {
                return candidate.toString();
            }
        }
        return "adb";
    }

    /** One key out of a plist. plutil handles the binary plists Xcode actually ships. */
    private static String plistValue(String plistPath, String key) {
        return exec("plutil", "-extract", key, "raw", "-o", "-", plistPath);
    }

    /**
     * Run a command and return its trimmed stdout, or null if it failed, timed out or printed
     * nothing. stdout and stderr are read separately so a warning on stderr can never end up
     * being reported as a version number.
     */
    private static String exec(String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).start();

            // Both streams hold a few bytes at most, so draining them in sequence cannot
            // fill a pipe buffer and deadlock.
            String stdout = drain(process.getInputStream());
            String stderr = drain(process.getErrorStream());

            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.debug("`{}` did not finish within {}s", String.join(" ", command), TIMEOUT_SECONDS);
                return null;
            }
            if (process.exitValue() != 0) {
                log.debug("`{}` exited {}: {}", String.join(" ", command), process.exitValue(), stderr.trim());
                return null;
            }
            String value = stdout.trim();
            return value.isEmpty() ? null : value;

        } catch (IOException e) {
            log.debug("Could not run `{}`: {}", String.join(" ", command), e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static String drain(InputStream stream) throws IOException {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
