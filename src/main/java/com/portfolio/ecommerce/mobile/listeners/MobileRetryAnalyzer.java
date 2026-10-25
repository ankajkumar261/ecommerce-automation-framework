package com.portfolio.ecommerce.mobile.listeners;

import com.portfolio.ecommerce.website.config.ConfigReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/**
 * Re-runs a failed mobile test up to retry.count times (config-${env}.properties).
 *
 * Attached to every mobile @Test by {@link MobileRetryTransformer}, so no annotation churn.
 *
 * WHY A MOBILE-SPECIFIC ANALYZER: the whole suite shares one app session, so a retry does
 * NOT start from a clean slate the way a web retry does — it restarts from whatever state
 * the failure left behind. That is only safe because MobileBaseTest#tearDown relaunches the
 * app and clears the run's auth flag on every failure, which happens BEFORE TestNG asks this
 * analyzer anything. This class therefore does not relaunch again: doing so would cost a
 * second cold launch per failure for no benefit. If that teardown ever stops recovering,
 * retries here start compounding failures instead of absorbing flakes.
 *
 * Retry hides flakiness, not defects. A test that only passes on its second attempt is still
 * a problem — the log line below exists so those are visible rather than silently green.
 */
public class MobileRetryAnalyzer implements IRetryAnalyzer {

    private static final Logger log = LogManager.getLogger(MobileRetryAnalyzer.class);

    /** Read once: config is immutable for the run, and this is consulted per failure. */
    private static final int MAX_RETRIES = ConfigReader.getInstance().getRetryCount();

    private int attempts;

    @Override
    public boolean retry(ITestResult result) {
        if (attempts >= MAX_RETRIES) {
            return false;
        }
        attempts++;
        log.warn("↻ RETRY {}/{}: {} — first attempt failed with: {}",
                attempts, MAX_RETRIES,
                result.getMethod().getMethodName(),
                result.getThrowable() == null ? "(no message)" : result.getThrowable().getMessage());
        return true;
    }
}
