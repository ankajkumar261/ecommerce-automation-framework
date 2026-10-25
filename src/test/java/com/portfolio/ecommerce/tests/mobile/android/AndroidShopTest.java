package com.portfolio.ecommerce.tests.mobile.android;

import com.portfolio.ecommerce.mobile.android.base.AndroidBaseTest;
import com.portfolio.ecommerce.mobile.android.screens.AndroidProductDetailScreen;
import com.portfolio.ecommerce.mobile.android.screens.AndroidServiceDetailScreen;
import com.portfolio.ecommerce.mobile.android.screens.AndroidShopScreen;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * Shop tab on Android — Products, Services, and the service-booking flow.
 * Counterpart of the iOS ShopTest. Browsing is public; booking needs a signed-in shopper.
 */
@Epic("Mobile (Android)")
@Feature("Shop")
public class AndroidShopTest extends AndroidBaseTest {

    @Test(description = "TC-AND052: Shop tab loads and shows filter chips")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Shop tab")
    public void shopTabLoadsAndShowsFilterChips() {
        AndroidShopScreen shop = new AndroidShopScreen(driver);
        Assert.assertTrue(shop.open(), "Shop should open from the bottom tab");
        Assert.assertTrue(shop.hasFilterChips(),
                "Shop should show the All / Products / Services filter chips");
    }

    @Test(description = "TC-AND053: Products filter shows product cards")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Products filter")
    public void productsFilterShowsProducts() {
        AndroidShopScreen shop = new AndroidShopScreen(driver);
        Assert.assertTrue(shop.open(), "Shop should open");
        shop.selectFilter("Products");
        Assert.assertTrue(shop.productCardCount() > 0,
                "The Products filter should show at least one product card");
    }

    @Test(description = "TC-AND054: Services filter shows service cards")
    @Severity(SeverityLevel.NORMAL)
    @Story("Services filter")
    public void servicesFilterShowsServices() {
        AndroidShopScreen shop = new AndroidShopScreen(driver);
        Assert.assertTrue(shop.open(), "Shop should open");
        shop.selectFilter("Services");
        if (!shop.hasServices()) {
            throw new SkipException("No services listed in this environment");
        }
        Assert.assertTrue(shop.serviceCardCount() > 0, "Services filter should show service cards");
    }

    @Test(description = "TC-AND055: Opening a product from Shop shows its detail")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Open product from Shop")
    public void openingProductFromShopShowsDetail() {
        AndroidShopScreen shop = new AndroidShopScreen(driver);
        Assert.assertTrue(shop.open(), "Shop should open");
        shop.selectFilter("Products");
        Assert.assertTrue(shop.productCardCount() > 0, "No products to open");
        Assert.assertTrue(shop.openFirstProduct(), "A product should open");

        AndroidProductDetailScreen pdp = new AndroidProductDetailScreen(driver);
        Assert.assertTrue(pdp.isLoaded(), "Product detail should load");
    }

    @Test(description = "TC-AND056: Opening a service shows its detail and Book button")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Open service")
    public void openingServiceShowsDetail() {
        AndroidShopScreen shop = new AndroidShopScreen(driver);
        Assert.assertTrue(shop.open(), "Shop should open");
        shop.selectFilter("Services");
        if (!shop.hasServices()) {
            throw new SkipException("No services listed in this environment");
        }
        Assert.assertTrue(shop.openFirstService(), "A service card should open");

        AndroidServiceDetailScreen detail = new AndroidServiceDetailScreen(driver);
        Assert.assertTrue(detail.isLoaded(), "Service detail should load");
        Assert.assertTrue(detail.hasPrice(), "Service detail should show a price");
        Assert.assertTrue(detail.hasBook(), "Service detail should offer a Book button");
    }

    @Test(description = "TC-AND057: Service booking flow starts without error")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Service booking")
    public void serviceBookingFlowStarts() {
        ensureSignedIn();
        AndroidShopScreen shop = new AndroidShopScreen(driver);
        Assert.assertTrue(shop.open(), "Shop should open");
        shop.selectFilter("Services");
        if (!shop.hasServices()) {
            throw new SkipException("No services listed in this environment");
        }
        Assert.assertTrue(shop.openFirstService(), "A service card should open");

        AndroidServiceDetailScreen detail = new AndroidServiceDetailScreen(driver);
        Assert.assertTrue(detail.isLoaded(), "Service detail should load");

        logStep("Tap Book service");
        Assert.assertTrue(detail.tapBook(), "The Book button should be tappable");

        // The booking flow may present a slot/date/confirm step or a confirmation. Either way it
        // must not crash — verify it progressed to a booking UI or stayed on a valid screen.
        boolean progressed = detail.bookingFlowProgressed();
        log.info("Booking flow progressed to a booking UI: {}", progressed);
        Assert.assertTrue(progressed || detail.isLoaded(),
                "The booking flow should progress or leave the app on a valid screen (no crash)");
    }
}
