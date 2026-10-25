package com.portfolio.ecommerce.website.drivers;

import com.portfolio.ecommerce.website.config.ConfigReader;
import com.microsoft.playwright.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BrowserManager {

    private static final Logger log = LogManager.getLogger(BrowserManager.class);

    private static final ThreadLocal<Playwright> playwrightTL = new ThreadLocal<>();
    private static final ThreadLocal<Browser> browserTL = new ThreadLocal<>();

    private static final ThreadLocal<BrowserContext> primaryContextTL = new ThreadLocal<>();
    private static final ThreadLocal<Page> primaryPageTL = new ThreadLocal<>();

    private static final ThreadLocal<BrowserContext> secondaryContextTL = new ThreadLocal<>();
    private static final ThreadLocal<Page> secondaryPageTL = new ThreadLocal<>();

    private BrowserManager() {
    }

    public static void initBrowser() {

        ConfigReader config = ConfigReader.getInstance();

        log.info(
                "Launching Playwright | browser={} | headless={} | slowMo={}ms",
                config.getBrowser(),
                config.isHeadless(),
                config.getSlowMo()
        );

        Playwright playwright = Playwright.create();
        playwrightTL.set(playwright);

        Browser browser = launchBrowser(playwright, config);
        browserTL.set(browser);

        BrowserContext primaryContext = browser.newContext(
                new Browser.NewContextOptions()
                        .setViewportSize(1920, 1080)
                        .setIgnoreHTTPSErrors(true)
        );

        primaryContext.setDefaultTimeout(config.getElementTimeout());

        primaryContextTL.set(primaryContext);

        Page primaryPage = primaryContext.newPage();
        primaryPageTL.set(primaryPage);

        log.debug(
                "Primary context + page created (thread={})",
                Thread.currentThread().getId()
        );
    }

    public static Page getPage() {

        Page page = primaryPageTL.get();

        if (page == null) {
            throw new IllegalStateException(
                    "Primary Page is null. Did you call BrowserManager.initBrowser()?"
            );
        }

        return page;
    }

    public static Page getSecondaryPage() {

        if (secondaryPageTL.get() == null) {

            ConfigReader config = ConfigReader.getInstance();

            BrowserContext secondaryContext =
                    browserTL.get().newContext(
                            new Browser.NewContextOptions()
                                    .setViewportSize(1920, 1080)
                                    .setIgnoreHTTPSErrors(true)
                    );

            secondaryContext.setDefaultTimeout(
                    config.getElementTimeout()
            );

            secondaryContextTL.set(secondaryContext);

            Page secondaryPage = secondaryContext.newPage();
            secondaryPageTL.set(secondaryPage);

            log.debug(
                    "Secondary context + page created on demand (thread={})",
                    Thread.currentThread().getId()
            );
        }

        return secondaryPageTL.get();
    }

    public static void quitBrowser() {

        log.info(
                "Closing browser (thread={})",
                Thread.currentThread().getId()
        );

        closeSilently(primaryPageTL.get(), "primary page");
        closeSilently(secondaryPageTL.get(), "secondary page");

        closeSilently(primaryContextTL.get(), "primary context");
        closeSilently(secondaryContextTL.get(), "secondary context");

        closeSilently(browserTL.get(), "browser");
        closeSilently(playwrightTL.get(), "playwright");

        primaryPageTL.remove();
        secondaryPageTL.remove();

        primaryContextTL.remove();
        secondaryContextTL.remove();

        browserTL.remove();
        playwrightTL.remove();

        log.debug(
                "Browser resources cleared (thread={})",
                Thread.currentThread().getId()
        );
    }

    private static Browser launchBrowser(
            Playwright playwright,
            ConfigReader config
    ) {

        BrowserType.LaunchOptions options =
                new BrowserType.LaunchOptions()
                        .setHeadless(config.isHeadless())
                        .setSlowMo(config.getSlowMo());

        String browserName =
                config.getBrowser().toLowerCase().trim();

        return switch (browserName) {

            case "firefox" -> {
                log.info("Using Firefox");
                yield playwright.firefox().launch(options);
            }

            case "webkit" -> {
                log.info("Using WebKit");
                yield playwright.webkit().launch(options);
            }

            default -> {
                log.info("Using Chromium");
                yield playwright.chromium().launch(options);
            }
        };
    }

    private static void closeSilently(
            AutoCloseable resource,
            String name
    ) {

        if (resource != null) {

            try {
                resource.close();
                log.debug("Closed: {}", name);

            } catch (Exception e) {

                log.warn(
                        "Error closing {}: {}",
                        name,
                        e.getMessage()
                );
            }
        }
    }
}