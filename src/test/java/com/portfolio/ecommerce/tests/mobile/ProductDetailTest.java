package com.portfolio.ecommerce.tests.mobile;

import com.portfolio.ecommerce.mobile.base.MobileBaseTest;
import com.portfolio.ecommerce.mobile.data.MobileTestData;
import com.portfolio.ecommerce.mobile.flows.ShopperFlows;
import com.portfolio.ecommerce.mobile.screens.CartScreen;
import com.portfolio.ecommerce.mobile.screens.HomeScreen;
import com.portfolio.ecommerce.mobile.screens.ProductDetailScreen;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.util.List;

/**
 * iOS product detail: what the screen tells a shopper about a product, and the two controls
 * that decide what actually reaches the cart — the size selector and the quantity stepper.
 *
 * ProductBrowseTest covers GETTING here (tap a card, land on the right product). This class
 * covers what is on the screen once you have.
 *
 * Two things about this screen shape the tests:
 *
 *  - Selection is directly readable. The chosen size button carries value="1" and the others
 *    carry no value at all, so "which size is selected" is a fact we can assert rather than
 *    something inferred from Add-to-cart having appeared.
 *  - The quantity stepper is one accessible button wrapping its own minus/number/plus, and
 *    those children report visible="false". They are on screen, so they are tapped by
 *    coordinate (ProductDetailScreen#increaseQuantity) — element.click() refuses them.
 *
 * Most of these are read-only. The two that add to the cart clear it afterwards.
 */
@Epic("Mobile (iOS)")
@Feature("Product detail")
public class ProductDetailTest extends MobileBaseTest {

    /** Only the tests that add to the cart dirty anything; clearing is cheap and unconditional. */
    @AfterClass(alwaysRun = true)
    public void emptyTheCartAfterwards() {
        if (isSessionSignedIn()) {
            flows.clearCartQuietly();
        }
    }

    @Test(description = "TC-IOS021: Product detail shows price, stock and size options")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Product information")
    @Description("Opens the first product from Home and verifies the detail screen carries the "
            + "three things a shopper decides on: a price, a stock line, and the size options.")
    public void productDetailShowsPriceStockAndSizeOptions() {
        ProductDetailScreen pdp = flows.openFirstProductFromHome().detail();

        Assert.assertTrue(pdp.hasPrice(), "Product detail should show a ₹ price");

        String stock = pdp.stockLabel();
        log.info("Stock label: {}", stock);
        Assert.assertFalse(stock.isBlank(),
                "Product detail should state its stock position rather than leaving the shopper "
                        + "to guess whether the item is available");

        List<String> sizes = pdp.sizeOptions();
        log.info("Sizes offered: {}", sizes);
        Assert.assertFalse(sizes.isEmpty(),
                "Product detail should offer at least one size — without one, Add to cart never "
                        + "appears and the product cannot be bought at all");
    }

    @Test(description = "TC-IOS022: Selecting a size marks it as the chosen one")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Size selection")
    @Description("Selects a different size and verifies the app moves the selected marker onto "
            + "it and off the previous one — so exactly one size is ever chosen.")
    public void selectingASizeMarksItAsTheChosenOne() {
        ProductDetailScreen pdp = flows.openFirstProductFromHome().detail();

        List<String> sizes = pdp.sizeOptions();
        if (sizes.size() < 2) {
            throw new SkipException("This product offers fewer than two sizes (" + sizes
                    + "), so there is no selection to move.");
        }

        String first = pdp.selectedSize();
        String target = sizes.stream().filter(s -> !s.equals(first)).findFirst().orElseThrow();
        log.info("Moving the selection from '{}' to '{}'", first, target);

        logStep("Select size " + target);
        pdp.selectSize(target);

        Assert.assertTrue(pdp.isSizeSelected(target),
                "Size " + target + " should be marked as selected after tapping it");
        Assert.assertEquals(pdp.selectedSizeCount(), 1,
                "Exactly one size should be selected — the app should have cleared the marker "
                        + "from '" + first + "' when '" + target + "' was chosen");
    }

    @Test(description = "TC-IOS085: Every available size can be selected one by one")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Size selection")
    @Description("Walks through EVERY size the product offers, selecting each in turn, and "
            + "verifies that after each tap that size is the chosen one and it is the ONLY one "
            + "chosen. This covers the 'verify all available sizes / select each one by one' "
            + "requirement rather than only switching to a single alternate size.")
    public void everyAvailableSizeCanBeSelectedOneByOne() {
        ProductDetailScreen pdp = flows.openFirstProductFromHome().detail();

        List<String> sizes = pdp.sizeOptions();
        log.info("Sizes offered: {}", sizes);
        Assert.assertFalse(sizes.isEmpty(),
                "Product detail should offer at least one size to select");

        for (String size : sizes) {
            logStep("Select size " + size);
            pdp.selectSize(size);

            Assert.assertTrue(pdp.isSizeSelected(size),
                    "Size '" + size + "' should be marked as selected immediately after tapping it");
            Assert.assertEquals(pdp.selectedSize(), size,
                    "The reported selected size should be '" + size + "' after selecting it");
            Assert.assertEquals(pdp.selectedSizeCount(), 1,
                    "Exactly one size should ever be selected — selecting '" + size
                            + "' should have cleared the previous marker");
        }
    }

    @Test(description = "TC-IOS086: Increasing then decreasing the quantity is reflected correctly")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Quantity")
    @Description("Steps the product-detail quantity up and back down, verifying the stepper "
            + "reflects each change, that the selected size is unaffected by quantity changes, "
            + "and that the stepper does not drop below its floor of 1.")
    public void increasingThenDecreasingQuantityIsReflected() {
        ProductDetailScreen pdp = flows.openFirstProductFromHome().detail();

        Assert.assertEquals(pdp.quantity(), 1, "The stepper should start at 1");
        String sizeBefore = pdp.selectedSize();
        log.info("Selected size before changing quantity: {}", sizeBefore);

        logStep("Increase the quantity to 2");
        pdp.increaseQuantity();
        Assert.assertEquals(pdp.quantity(), 2, "The stepper should read 2 after one tap of +");

        logStep("Increase the quantity to 3");
        pdp.increaseQuantity();
        Assert.assertEquals(pdp.quantity(), 3, "The stepper should read 3 after a second tap of +");

        logStep("Decrease the quantity back to 2");
        pdp.decreaseQuantity();
        Assert.assertEquals(pdp.quantity(), 2, "The stepper should read 2 after one tap of −");

        logStep("Decrease the quantity back to 1");
        pdp.decreaseQuantity();
        Assert.assertEquals(pdp.quantity(), 1, "The stepper should read 1 after stepping down again");

        logStep("Attempt to decrease below 1");
        pdp.decreaseQuantity();
        Assert.assertEquals(pdp.quantity(), 1,
                "The stepper should not drop below 1 — a shopper cannot add zero of an item");

        logStep("Verify the selected size was not disturbed by the quantity changes");
        Assert.assertEquals(pdp.selectedSize(), sizeBefore,
                "Changing the quantity should not change which size is selected");
        Assert.assertEquals(pdp.selectedSizeCount(), 1,
                "Exactly one size should still be selected after changing the quantity");
    }

    @Test(description = "TC-IOS023: A size is preselected when the product opens")
    @Severity(SeverityLevel.NORMAL)
    @Story("Size selection")
    @Description("Verifies the product opens with exactly one size already chosen, so Add to "
            + "cart is reachable without the shopper having to discover the size step first.")
    public void aSizeIsPreselectedWhenTheProductOpens() {
        ProductDetailScreen pdp = flows.openFirstProductFromHome().detail();

        int selected = pdp.selectedSizeCount();
        log.info("Sizes marked as selected on open: {} (of {})", selected, pdp.sizeOptions());

        Assert.assertEquals(selected, 1,
                "Exactly one size should be preselected when the product opens. Zero means the "
                        + "shopper has to find the size step before Add to cart appears; more than "
                        + "one means the app cannot say what it would add.");
    }

    @Test(description = "TC-IOS024: Increasing the quantity before adding puts two in the cart")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Quantity")
    @Description("Steps the product-detail quantity up to 2 before adding, and verifies the cart "
            + "receives two units as ONE line item rather than one unit or two separate lines.")
    public void increasingQuantityBeforeAddingPutsTwoInTheCart() {
        ensureSignedIn();
        CartScreen cart = flows.emptyCart();

        ShopperFlows.OpenedProduct opened = flows.openFirstProductFromHome();
        ProductDetailScreen pdp = opened.detail();

        Assert.assertEquals(pdp.quantity(), 1, "The stepper should start at 1");

        logStep("Step the quantity up to 2");
        pdp.increaseQuantity();
        Assert.assertEquals(pdp.quantity(), 2,
                "The product-detail stepper should read 2 after one tap of +");

        flows.addOpenProductToCart(opened, MobileTestData.DEFAULT_SIZE);

        cart.open();
        Assert.assertTrue(cart.isLoaded(), "Cart screen should load");
        Assert.assertEquals(cart.itemCount(), 2,
                "The cart should hold 2 units — the quantity chosen on product detail should "
                        + "carry through the add, not reset to 1");
        Assert.assertEquals(cart.lineItemCount(), 1,
                "Two units of the same product should be ONE line item, not two");
    }

    @Test(description = "TC-IOS025: The Description accordion collapses and expands")
    @Severity(SeverityLevel.NORMAL)
    @Story("Product information")
    @Description("The detail sections ship OPEN, so this drives the accordion the way a shopper "
            + "would: body showing to start, hidden after a tap, showing again after a second. "
            + "A one-way check would pass on an accordion that is simply stuck open.")
    public void theDescriptionAccordionCollapsesAndExpands() {
        ProductDetailScreen pdp = flows.openFirstProductFromHome().detail();

        Assert.assertTrue(pdp.isSectionExpanded("DESCRIPTION"),
                "DESCRIPTION should be showing its body when the product opens");

        logStep("Tap the header to collapse it");
        pdp.toggleSection("DESCRIPTION");
        Assert.assertFalse(pdp.isSectionExpanded("DESCRIPTION"),
                "Tapping the DESCRIPTION header should hide its body");

        logStep("Tap again to reopen it");
        pdp.toggleSection("DESCRIPTION");
        Assert.assertTrue(pdp.isSectionExpanded("DESCRIPTION"),
                "Tapping the DESCRIPTION header again should bring its body back");
    }

    @Test(description = "TC-IOS026: Specifications list material, sizes and gender")
    @Severity(SeverityLevel.NORMAL)
    @Story("Product information")
    @Description("Expands SPECIFICATIONS and verifies each advertised attribute row carries an "
            + "actual value rather than a blank.")
    public void specificationsListMaterialSizesAndGender() {
        ProductDetailScreen pdp = flows.openFirstProductFromHome().detail();

        // The sections ship open, so this only acts if something left it closed.
        logStep("Make sure the SPECIFICATIONS section is open");
        pdp.ensureSectionExpanded("SPECIFICATIONS");

        for (String row : new String[]{"Material", "Sizes", "Gender"}) {
            String value = pdp.specification(row);
            log.info("Specification {} = '{}'", row, value);
            Assert.assertFalse(value.isBlank(),
                    "The '" + row + "' specification should carry a value. A blank row is worse "
                            + "than an absent one — it tells the shopper the attribute exists and "
                            + "then says nothing about it.");
        }
    }

    @Test(description = "TC-IOS027: A COD-eligible product advertises Cash on Delivery on its detail")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Payment eligibility")
    @Description("Verifies the COD badge on product detail agrees with what checkout later "
            + "offers: present for the configured COD product, absent for a non-COD one. A "
            + "mismatch means the shopper is promised COD and then denied it at payment.")
    public void codEligibleProductAdvertisesCashOnDeliveryOnItsDetail() {
        logStep("Open the configured COD-eligible product: " + config.getCodProduct());
        ProductDetailScreen codProduct = flows.openProductByName(config.getCodProduct()).detail();

        Assert.assertTrue(codProduct.advertisesCod(),
                "'" + config.getCodProduct() + "' is configured as COD-eligible, so its detail "
                        + "screen should carry the 'Cash on Delivery available' badge. If this "
                        + "fails, codEnabled may have been turned off for it — check "
                        + "checkout.cod.product for this environment.");

        logStep("Now open a product that is not COD-eligible");
        HomeScreen home = new HomeScreen(getDriver());
        home.open();
        String firstName = home.firstProductName();
        if (firstName.equalsIgnoreCase(config.getCodProduct())) {
            throw new SkipException("The first product on Home is the COD-eligible one ('"
                    + firstName + "'), so it cannot demonstrate the badge being withheld.");
        }

        ProductDetailScreen nonCod = flows.openFirstProductFromHome().detail();
        Assert.assertFalse(nonCod.advertisesCod(),
                "'" + firstName + "' is not COD-eligible, so its detail screen must NOT advertise "
                        + "Cash on Delivery — checkout will refuse it.");
    }

    @Test(description = "TC-IOS028: Product detail matches the card it was opened from")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Product information")
    @Description("Compares the name AND price on the detail screen against what the Home card "
            + "advertised. Card and detail are rendered separately from the same record, so a "
            + "mismatch means a shopper is shown one price and charged against another.")
    public void productDetailMatchesTheCardItWasOpenedFrom() {
        HomeScreen home = new HomeScreen(getDriver());
        home.open();
        Assert.assertTrue(home.isLoaded(), "Home should load");

        String advertisedName  = home.firstProductName();
        String advertisedPrice = home.firstProductPrice();
        log.info("Card advertises: '{}' at {}", advertisedName, advertisedPrice);
        Assert.assertFalse(advertisedPrice.isBlank(),
                "Precondition: the product card should advertise a ₹ price");

        ProductDetailScreen pdp = flows.openFirstProductFromHome().detail();

        Assert.assertTrue(pdp.showsProductNamed(advertisedName),
                "Product detail should show the same product the card named: " + advertisedName);
        Assert.assertEquals(pdp.price(), advertisedPrice,
                "Product detail should show the same price the card advertised for '"
                        + advertisedName + "'");
    }
}
