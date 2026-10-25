package com.portfolio.ecommerce.mobile.screens;

import com.portfolio.ecommerce.mobile.drivers.AppiumDriverManager;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.HasOnScreenKeyboard;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Shared plumbing for the iOS screen objects: waits, presence checks and scrolling.
 *
 * Scrolling matters more on iOS than it looks. XCUITest reports elements that exist in the
 * accessibility tree even when they are rendered below the fold (visible="false"), and
 * ExpectedConditions.elementToBeClickable requires the element to be DISPLAYED. So a locator
 * can match perfectly and still time out — the element simply is not on screen yet. Anything
 * that lives further down a scrolling screen must be scrolled to before it can be tapped.
 */
public abstract class BaseScreen {

    /** How many swipes to attempt before giving up on finding an element. */
    protected static final int MAX_SWIPES = 8;

    protected final AppiumDriver driver;
    protected final WebDriverWait wait;
    protected final WebDriverWait shortWait;

    protected BaseScreen(AppiumDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        this.shortWait = new WebDriverWait(driver, Duration.ofSeconds(5));
    }

    // ── platform ──────────────────────────────────────────────

    /**
     * Pick the locator for the platform this run targets.
     *
     * THE ONE SEAM BETWEEN iOS AND ANDROID. Screen logic — what to tap, in what order, what to
     * wait for — is identical on both; only the locator VALUES differ. Keeping that difference
     * here means a screen object stays a single class:
     *
     *   private static final By CHECKOUT_TITLE = platformBy(
     *           AppiumBy.accessibilityId("Checkout"),          // iOS
     *           AppiumBy.androidUIAutomator("new UiSelector().text(\"Checkout\")"));
     *
     * and porting is a matter of filling in second arguments, not duplicating screens.
     *
     * Static so it can be used in field initialisers, which is where locators belong.
     *
     * NOTE: this resolves at CLASS-LOAD time. That is fine because one run targets one platform
     * — the suites never mix — but it does mean you cannot flip mobile.platform mid-JVM.
     */
    protected static By platformBy(By ios, By android) {
        return AppiumDriverManager.isAndroid() ? android : ios;
    }

    /**
     * Android locator for an element whose own node carries no text — the Compose case.
     *
     * Compose puts the click handler on a bare parent View and the label on a CHILD, so on the
     * Home screen all 14 clickable nodes have neither text nor content-desc of their own. This
     * walks up from the labelled node to the nearest clickable ancestor, which is the thing you
     * actually want to tap.
     *
     * Use it only where it is needed. It is an XPath with an ancestor axis, so it traverses the
     * whole tree — the expensive shape the performance note below warns about. Once the app
     * exposes testTag values as resource-ids, these should be replaced with plain id lookups.
     */
    protected static By androidClickableByText(String text) {
        return AppiumBy.xpath(
                "//*[@text=" + xpathLiteral(text) + " or @content-desc=" + xpathLiteral(text) + "]"
                        + "/ancestor-or-self::*[@clickable='true'][1]");
    }

    /** Quote a value for XPath, coping with apostrophes ("You're browsing as a guest"). */
    private static String xpathLiteral(String value) {
        if (!value.contains("'")) {
            return "'" + value + "'";
        }
        // XPath 1.0 has no escape character; the only way is to concat the pieces.
        return "concat('" + value.replace("'", "', \"'\", '") + "')";
    }

    // ── waits / presence ──────────────────────────────────────

    protected WebElement waitClickable(By by) {
        return wait.until(ExpectedConditions.elementToBeClickable(by));
    }

    protected WebElement waitVisible(By by) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(by));
    }

    /** Quick, non-throwing presence check (matches the tree, on screen or not). */
    protected boolean isPresent(By by) {
        return !driver.findElements(by).isEmpty();
    }

    /** True only if the element is present AND actually rendered on screen. */
    protected boolean isDisplayed(By by) {
        return firstDisplayed(by) != null;
    }

    /*
     * PERFORMANCE NOTE — MEASURED, DO NOT "OPTIMISE" THIS AGAIN THE OBVIOUS WAY.
     *
     * It looks wasteful that isDisplayed() costs a findElements plus an isDisplayed() call per
     * match, and tempting to push the filter server-side with an NSPredicate like
     * "visible == 1 AND name == 'house.fill'" so one round-trip does the lot.
     *
     * That was tried and measured: the smoke suite went from 363s to 875s — 2.4x SLOWER.
     * On XCUITest, `visible` is a computed property, so a predicate mentioning it forces the
     * server to hit-test the WHOLE tree; predicates using CONTAINS are full-tree scans for the
     * same reason. Plain accessibility-id lookups are indexed and cheap by comparison, and two
     * cheap lookups beat one expensive scan by a wide margin on this app.
     *
     * If this path needs to get faster, reduce the NUMBER of navigations (resetToHome is ~27%
     * of a run) rather than trying to make each query cleverer.
     */

    /** The first match that is actually on screen, or null if none are. */
    protected WebElement firstDisplayed(By by) {
        for (WebElement el : driver.findElements(by)) {
            try {
                if (el.isDisplayed()) return el;
            } catch (Exception ignored) { }
        }
        return null;
    }

    protected boolean isVisible(By locator, WebDriverWait w) {
        try {
            w.until(ExpectedConditions.visibilityOfElementLocated(locator));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ── scrolling ─────────────────────────────────────────────

    /**
     * Swipe down the screen until the locator is not just present but ON SCREEN, then
     * return it. Returns null if it never comes into view.
     */
    protected WebElement scrollIntoView(By by) {
        WebElement el = firstDisplayed(by);
        if (el != null) return el;

        for (int i = 0; i < MAX_SWIPES; i++) {
            scrollDown();
            pause(350);
            el = firstDisplayed(by);
            if (el != null) return el;
        }
        return null;
    }

    /**
     * Bring an element into view searching in BOTH directions — up first, then down.
     *
     * scrollIntoView only ever scrolls DOWN, which cannot reach anything above the current
     * position. That matters because screens keep their scroll offset: a product detail screen
     * left scrolled to SPECIFICATIONS by one test opens with the size selector ABOVE the
     * viewport for the next, and a downward-only search will never find it however long it
     * waits. Use this for anything near the TOP of a scrolling screen.
     */
    protected WebElement bringIntoView(By by) {
        WebElement el = firstDisplayed(by);
        if (el != null) return el;

        for (int i = 0; i < MAX_SWIPES / 2; i++) {
            scrollUp();
            pause(350);
            el = firstDisplayed(by);
            if (el != null) return el;
        }
        return scrollIntoView(by);
    }

    /** Swipe up (content moves up) to reveal what is further down the screen. */
    protected void scrollDown() {
        swipe(0.8, 0.25);
    }

    /** Swipe down (content moves down) to get back toward the top. */
    protected void scrollUp() {
        swipe(0.25, 0.8);
    }

    /** Vertical swipe from startYFraction to endYFraction of screen height. */
    protected void swipe(double startYFraction, double endYFraction) {
        try {
            Dimension size = driver.manage().window().getSize();
            int x = size.width / 2;
            int startY = (int) (size.height * startYFraction);
            int endY = (int) (size.height * endYFraction);
            PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
            Sequence swipe = new Sequence(finger, 1);
            swipe.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), x, startY));
            swipe.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
            swipe.addAction(finger.createPointerMove(Duration.ofMillis(600), PointerInput.Origin.viewport(), x, endY));
            swipe.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
            driver.perform(Collections.singletonList(swipe));
        } catch (Exception ignored) { }
    }

    protected void pause(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException ignored) { }
    }

    /**
     * Is the on-screen keyboard up?
     *
     * AppiumDriver does not declare this — it lives on HasOnScreenKeyboard, which IOSDriver and
     * AndroidDriver both implement. One cast here beats the same cast at every call site, and
     * keeps the screen objects free of driver-type detail.
     */
    protected boolean isKeyboardShown() {
        try {
            return ((HasOnScreenKeyboard) driver).isKeyboardShown();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Dismiss the on-screen keyboard.
     *
     * Worth doing after every text entry: the keyboard covers the lower half of the screen,
     * and anything under it reports visible="false" — so results, and buttons like Sign in,
     * are untappable until it is gone.
     */
    protected void hideKeyboard() {
        try {
            if (isKeyboardShown()) {
                driver.executeScript("mobile: hideKeyboard", Map.of(
                        "keys", List.of("Return", "Done", "Go", "Search", "Next", "return", "done")));
            }
        } catch (Exception ignored) { }
    }

    // ── iOS "Save Password?" system prompt ────────────────────

    /** Text that identifies the AutoFill prompt, so we never dismiss one of the app's own alerts. */
    private static final String[] SAVE_PASSWORD_MARKERS = {"Save Password", "Securely store"};

    /**
     * Dismiss the iOS "Save Password?" prompt (Not Now / Save) if it is on screen.
     *
     * WHY THE ALERT INTERFACE AND NOT findElements: this prompt is presented by a SEPARATE system
     * process (Password AutoFill), not by the app, so it never enters the app's accessibility
     * tree. It appears in none of the 18 page-source dumps under test-output/screenshots, and an
     * earlier version of this method that looked for a "Not Now" accessibility id therefore
     * matched nothing and silently did nothing at all. switchTo().alert() does reach system
     * alerts.
     *
     * It is dismissed rather than accepted: dismiss() taps the cancel-role button, which on this
     * prompt is "Not Now".
     *
     * The text check is what keeps this safe to call from the per-test reset. Without it, this
     * would dismiss whatever alert happened to be up — including the app's own sign-out
     * confirmation, where the cancel-role button is "Cancel".
     *
     * NOTE: the real fix is upstream — turning off Settings → General → AutoFill & Passwords →
     * "AutoFill Passwords and Passkeys" on the device stops iOS offering the prompt in the first
     * place. This is the safety net for a device where that has not been done.
     */
    public boolean dismissSavePasswordPrompt() {
        try {
            Alert alert = driver.switchTo().alert();
            String text = alert.getText();
            if (text != null) {
                for (String marker : SAVE_PASSWORD_MARKERS) {
                    if (text.contains(marker)) {
                        alert.dismiss();
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
            // No alert up, or it vanished between the query and the read. Either way: nothing to do.
        }
        return false;
    }

    /**
     * Wait up to {@code timeoutMs} for the prompt to appear, then dismiss it.
     *
     * Use this ONLY where the prompt is expected — straight after submitting credentials. It
     * costs the full timeout when nothing shows up, which is why the per-test reset uses the
     * instant check above instead of paying that on all 45 checks.
     */
    public boolean awaitAndDismissSavePasswordPrompt(long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        do {
            if (dismissSavePasswordPrompt()) {
                return true;
            }
            pause(250);
        } while (System.currentTimeMillis() < end);
        return false;
    }

    // ── navigation ────────────────────────────────────────────

    /** The bottom tab bar: Home, Shop, Cart, Search, Profile. */
    protected static final By HOME_TAB_BAR = AppiumBy.accessibilityId("house.fill");
    private static final By BACK_BUTTON    = AppiumBy.accessibilityId("BackButton");

    /**
     * True when we are at a TAB ROOT: the tab bar is up and nothing is pushed over it.
     *
     * The back button is the half that matters. A pushed screen does not necessarily hide the
     * tab bar — product detail opened from Search shows the tab bar AND a back button at the
     * same time — so "the tab bar is visible" on its own is not proof that we are anywhere in
     * particular. Treating it as proof is what let a product detail masquerade as the Search
     * screen for the next test.
     */
    protected boolean isAtTabRoot() {
        // Short-circuits on the cheaper half: no tab bar means definitely not at a tab root,
        // so the second lookup is skipped. See the performance note above before changing
        // these to predicates.
        return isDisplayed(HOME_TAB_BAR) && !isDisplayed(BACK_BUTTON);
    }

    /**
     * Unwind the navigation stack until we are back at the current tab's root.
     *
     * Three ways back, in order of preference, because not every pushed screen labels its
     * back control the same way:
     *   1. the app's own "BackButton"
     *   2. the leading button in whatever navigation bar is up
     *   3. the iOS interactive-pop gesture (swipe in from the left edge), which works even on
     *      a screen whose chrome we cannot find at all
     *
     * Now that the whole suite shares one app session, this is also how a test that finished
     * deep inside a flow hands a usable app to the next one — so it needs to be hard to
     * defeat, not just good enough for the happy path.
     */
    protected void returnToTabBar() {
        for (int i = 0; i < 5; i++) {
            if (isAtTabRoot()) return;
            if (!goBackOnce()) return;
            pause(500);   // pop animation is quick (reduceMotion on); no need to wait a full second
        }
    }

    /**
     * Switch to a bottom tab and land on that tab's ROOT, whatever either tab was showing.
     *
     * Each tab keeps its OWN navigation stack, and tapping the tab restores that stack rather
     * than popping it — so a test that opened a product from Search leaves the Search tab
     * sitting on product detail, and the next tap of the Search tab lands right back there.
     * That is why the stack has to be unwound twice: once on the tab we are leaving (to reach
     * the tab bar) and once on the tab we arrive at.
     *
     * Returns the tab button, or null if the tab bar never became reachable.
     */
    protected WebElement openTab(By tabLocator) {
        returnToTabBar();
        WebElement tab = firstDisplayed(tabLocator);
        if (tab == null) {
            return null;
        }
        try { tab.click(); } catch (Exception ignored) { }
        pause(600);
        returnToTabBar();   // ...and pop whatever this tab was left showing
        return tab;
    }

    /** One step back up the navigation stack. False if we could not find any way back. */
    private boolean goBackOnce() {
        WebElement back = firstDisplayed(BACK_BUTTON);
        if (back == null) {
            back = firstDisplayed(NAV_BAR_BACK);
        }
        if (back != null) {
            try {
                back.click();
                return true;
            } catch (Exception ignored) { }
        }
        return swipeBackFromLeftEdge();
    }

    /** The leading button of any navigation bar — the unlabelled back chevron on most screens. */
    private static final By NAV_BAR_BACK = AppiumBy.iOSClassChain(
            "**/XCUIElementTypeNavigationBar/XCUIElementTypeButton[1]");

    /** iOS "swipe from the left edge to go back". Returns false if the gesture could not run. */
    private boolean swipeBackFromLeftEdge() {
        try {
            Dimension size = driver.manage().window().getSize();
            int startX = 2;
            int endX = (int) (size.width * 0.85);
            int y = size.height / 2;
            PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
            Sequence swipe = new Sequence(finger, 1);
            swipe.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), startX, y));
            swipe.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
            swipe.addAction(finger.createPointerMove(Duration.ofMillis(500), PointerInput.Origin.viewport(), endX, y));
            swipe.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
            driver.perform(Collections.singletonList(swipe));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** All matches for a locator (present in the tree, on screen or not). */
    protected List<WebElement> findAll(By by) {
        return driver.findElements(by);
    }

    // ── tapping things iOS says are not "visible" ─────────────

    /**
     * Tap the centre of an element's rect with a raw pointer action, bypassing click().
     *
     * Needed for controls nested inside an ACCESSIBLE container. iOS collapses such children
     * into the parent for accessibility purposes and reports them visible="false" even though
     * they are plainly on screen — the product-detail quantity stepper is the example: the
     * "Quantity 1" button is accessible and its own minus/plus children are not. element.click()
     * refuses to touch those ("not hittable"), but their rects are real, so tapping the
     * coordinates works.
     *
     * Do NOT reach for this to paper over a genuinely off-screen element — scroll to that one
     * instead, or the tap lands on whatever happens to occupy those coordinates.
     */
    protected void tapElementCentre(WebElement element) {
        Rectangle r = element.getRect();
        tapAt(r.getX() + r.getWidth() / 2, r.getY() + r.getHeight() / 2);
    }

    /** Raw single tap at viewport coordinates. */
    protected void tapAt(int x, int y) {
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence tap = new Sequence(finger, 1);
        tap.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), x, y));
        tap.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
        tap.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        driver.perform(Collections.singletonList(tap));
    }

    // ── reading money off a summary row ───────────────────────

    /** Any StaticText whose value is a rupee amount. */
    protected static final By RUPEE_AMOUNT = AppiumBy.iOSNsPredicateString(
            "type == 'XCUIElementTypeStaticText' AND value BEGINSWITH '₹'");

    /**
     * The ₹ amount rendered on the same row as the given label, e.g. "₹7,022" for "Total".
     * Empty string when the label or its amount is not on screen.
     *
     * Summary rows (Subtotal / Shipping / Platform fee / Total) put the label and its amount in
     * separate StaticTexts with no parent tying them together, so the only thing relating them
     * is that they share a baseline. Hence the y-coordinate match rather than a tree walk.
     */
    protected String amountBesideLabel(String label) {
        WebElement lbl = scrollIntoView(AppiumBy.accessibilityId(label));
        if (lbl == null) return "";
        int y = lbl.getRect().getY();
        for (WebElement amt : findAll(RUPEE_AMOUNT)) {
            try {
                if (Math.abs(amt.getRect().getY() - y) < 20) {
                    return amt.getAttribute("value");
                }
            } catch (Exception ignored) { }
        }
        return "";
    }

    /**
     * "₹7,022" -> 7022. Returns -1 for anything without digits, so a missing row is
     * distinguishable from a genuine zero (shipping is often "Free", which parses to -1 —
     * callers that expect that should say so explicitly).
     */
    protected static int parseAmount(String displayed) {
        if (displayed == null) return -1;
        String digits = displayed.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? -1 : Integer.parseInt(digits);
    }
}
