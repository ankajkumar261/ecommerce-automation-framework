package com.portfolio.ecommerce.tests.website.storefront;

import com.portfolio.ecommerce.website.base.BaseTest;
import com.portfolio.ecommerce.website.config.ConfigReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Section 5 — CART + WISHLIST (server-side).
 *
 * The cart lives on the server (GET /api/cart); the Zustand store is only a UI mirror,
 * so a cart must persist across requests. This suite drives the cart/wishlist REST API:
 *   add     POST   /api/cart          (productId, quantity, size)
 *   update  PUT    /api/cart/:itemId  (quantity)
 *   remove  DELETE /api/cart/:itemId
 *   clear   DELETE /api/cart/clear
 *   wishlist POST/GET/DELETE /api/customer/wishlist
 *
 * SAFETY: mutations are scoped to the test customer and are reverted in a finally block
 * (cart cleared / wishlist item removed), so no lasting state is left behind.
 */
@Epic("Cart & Wishlist")
@Feature("Server-side cart and wishlist")
public class CartWishlistApiTest extends BaseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected String getUrl() {
        return ConfigReader.getInstance().getStorefrontUrl();
    }

    private String apiBase() {
        String base = ConfigReader.getInstance().getApiUrl();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private JsonNode tree(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            throw new AssertionError("Response was not valid JSON: " + body, e);
        }
    }

    private String customerToken() {
        ConfigReader config = ConfigReader.getInstance();
        APIResponse resp = getPage().request().post(apiBase() + "/auth/login",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setData(Map.of("email", config.getCustomerEmail(),
                                "password", config.getCustomerPassword())));
        Assert.assertTrue(resp.ok(), "Customer login should succeed");
        String token = tree(resp.text()).path("data").path("token").asText(null);
        if (token == null) token = tree(resp.text()).path("token").asText(null);
        Assert.assertNotNull(token, "Customer login must return a token");
        return token;
    }

    /** A purchasable product: id + unit price + an in-stock size. */
    private record Buyable(String id, double price, String size) {}

    private Buyable pickBuyableProduct() {
        APIResponse resp = getPage().request().get(apiBase() + "/public/products?limit=50");
        Assert.assertEquals(resp.status(), 200, "public products should return 200");
        for (JsonNode p : tree(resp.text()).path("data").path("products")) {
            JsonNode sizes = p.path("sizes");
            JsonNode sizeStock = p.path("sizeStock");
            if (sizes.isArray() && sizes.size() > 0) {
                for (JsonNode s : sizes) {
                    String size = s.asText();
                    if (sizeStock.path(size).asInt(0) >= 3) {
                        return new Buyable(p.path("_id").asText(), p.path("price").asDouble(), size);
                    }
                }
            }
        }
        throw new org.testng.SkipException("No product with an in-stock size found to exercise the cart");
    }

    // ── Cart helpers ──────────────────────────────────────────
    private JsonNode getCart(String token) {
        APIResponse resp = getPage().request().get(apiBase() + "/cart",
                RequestOptions.create().setHeader("Authorization", "Bearer " + token));
        Assert.assertEquals(resp.status(), 200, "GET /cart should return 200 for an authed customer");
        return tree(resp.text()).path("data");
    }

    private APIResponse addToCart(String token, String productId, String size, int quantity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("productId", productId);
        payload.put("quantity", quantity);
        payload.put("size", size);
        return getPage().request().post(apiBase() + "/cart",
                RequestOptions.create()
                        .setHeader("Authorization", "Bearer " + token)
                        .setHeader("Content-Type", "application/json")
                        .setData(payload));
    }

    private void clearCart(String token) {
        getPage().request().delete(apiBase() + "/cart/clear",
                RequestOptions.create().setHeader("Authorization", "Bearer " + token));
    }

    private String firstItemId(JsonNode cart) {
        JsonNode item = cart.path("items").get(0);
        for (String key : new String[]{"_id", "itemId", "id"}) {
            String v = item.path(key).asText("");
            if (!v.isBlank()) return v;
        }
        throw new AssertionError("Could not find an item id on the cart item: " + item);
    }

    // ─────────────────────────────────────────────────────────
    // AUTH
    // ─────────────────────────────────────────────────────────

    @Test(description = "TC-CART-API001: The cart endpoint requires authentication")
    @Description("Calls GET /cart with no token and verifies it returns 401 — the server-side cart is not "
            + "exposed anonymously.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Cart is protected")
    public void cartRequiresAuth() {
        APIResponse resp = getPage().request().get(apiBase() + "/cart");
        Assert.assertEquals(resp.status(), 401, "Anonymous GET /cart must be 401");
    }

    @Test(description = "TC-CART-API002: The wishlist endpoint requires authentication")
    @Description("Calls GET /customer/wishlist with no token and verifies it returns 401.")
    @Severity(SeverityLevel.NORMAL)
    @Story("Wishlist is protected")
    public void wishlistRequiresAuth() {
        APIResponse resp = getPage().request().get(apiBase() + "/customer/wishlist");
        Assert.assertEquals(resp.status(), 401, "Anonymous GET /customer/wishlist must be 401");
    }

    // ─────────────────────────────────────────────────────────
    // SERVER-SIDE CART
    // ─────────────────────────────────────────────────────────

    @Test(description = "TC-CART-API003: Adding an item persists server-side and totals recompute")
    @Description("Adds a product (qty 2) via the API, re-reads the cart, and verifies it persisted server-side "
            + "with the right quantity and a recomputed subtotal (unit price × quantity); clears the cart afterward.")
    @Severity(SeverityLevel.BLOCKER)
    @Story("Server-side persistence + totals")
    public void addPersistsServerSideAndRecomputesTotals() {
        String token = customerToken();
        Buyable product = pickBuyableProduct();
        try {
            clearCart(token);
            APIResponse add = addToCart(token, product.id(), product.size(), 2);
            Assert.assertTrue(add.ok(), "Adding a valid item should succeed (HTTP " + add.status() + ")");

            JsonNode cart = getCart(token);
            Assert.assertEquals(cart.path("items").size(), 1, "Cart should hold exactly the added item");
            Assert.assertEquals(cart.path("totalItems").asInt(), 2, "totalItems should reflect quantity 2");

            JsonNode item = cart.path("items").get(0);
            Assert.assertEquals(item.path("quantity").asInt(), 2, "Item quantity should be 2");
            Assert.assertTrue(cart.path("subtotal").asDouble() > 0, "Subtotal should be > 0 after adding");
            // Internal consistency of the recomputed total, independent of discount:
            if (item.path("price").isNumber()) {
                Assert.assertEquals(cart.path("subtotal").asDouble(),
                        item.path("price").asDouble() * item.path("quantity").asInt(), 1.0,
                        "Subtotal must equal unit price × quantity");
            }
        } finally {
            clearCart(token);
        }
    }

    @Test(description = "TC-CART-API004: Updating quantity then removing an item is reflected in the cart")
    @Description("Adds an item, updates its quantity to 3 and verifies totalItems updates, then removes the "
            + "item and verifies the cart is empty; clears the cart afterward.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Update + remove")
    public void updateThenRemoveReflectsInCart() {
        String token = customerToken();
        Buyable product = pickBuyableProduct();
        try {
            clearCart(token);
            Assert.assertTrue(addToCart(token, product.id(), product.size(), 1).ok(), "add should succeed");

            String itemId = firstItemId(getCart(token));
            APIResponse upd = getPage().request().put(apiBase() + "/cart/" + itemId,
                    RequestOptions.create()
                            .setHeader("Authorization", "Bearer " + token)
                            .setHeader("Content-Type", "application/json")
                            .setData(Map.of("quantity", 3)));
            Assert.assertTrue(upd.ok(), "Updating quantity should succeed");
            Assert.assertEquals(getCart(token).path("totalItems").asInt(), 3,
                    "totalItems should update to 3 after the quantity change");

            APIResponse del = getPage().request().delete(apiBase() + "/cart/" + itemId,
                    RequestOptions.create().setHeader("Authorization", "Bearer " + token));
            Assert.assertTrue(del.ok(), "Removing the item should succeed");
            Assert.assertEquals(getCart(token).path("items").size(), 0,
                    "Cart should be empty after removing the only item");
        } finally {
            clearCart(token);
        }
    }

    @Test(description = "TC-CART-API005: Adding with a non-positive quantity is rejected")
    @Description("Attempts to add an item with quantity 0 and expects the API to reject it with 400. "
            + "KNOWN DEFECT: the API instead returns 201 and clamps to 1, so this test currently fails on purpose.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Quantity negatives")
    public void nonPositiveQuantityRejected() {
        String token = customerToken();
        Buyable product = pickBuyableProduct();
        try {
            clearCart(token);
            // KNOWN DEFECT (kept RED on purpose): the cart API accepts quantity=0 (returns 201 and
            // clamps to 1) instead of rejecting it with 400 as the spec's negative case (§5) requires.
            APIResponse zero = addToCart(token, product.id(), product.size(), 0);
            Assert.assertFalse(zero.ok(), "Quantity 0 must be rejected, got HTTP " + zero.status());

            APIResponse negative = addToCart(token, product.id(), product.size(), -1);
            Assert.assertFalse(negative.ok(), "Negative quantity must be rejected, got HTTP " + negative.status());

            Assert.assertEquals(getCart(token).path("items").size(), 0,
                    "A rejected add must not place anything in the cart");
        } finally {
            clearCart(token);
        }
    }

    // ─────────────────────────────────────────────────────────
    // WISHLIST
    // ─────────────────────────────────────────────────────────

    @Test(description = "TC-CART-API006: Wishlist add/remove round-trips consistently")
    @Description("Adds a product to the wishlist and verifies it appears, then removes it and verifies it is "
            + "gone — the wishlist state is consistent across add and remove.")
    @Severity(SeverityLevel.NORMAL)
    @Story("Wishlist consistency")
    public void wishlistAddRemoveRoundTrips() {
        String token = customerToken();
        Buyable product = pickBuyableProduct();
        boolean added = false;
        try {
            APIResponse add = getPage().request().post(apiBase() + "/customer/wishlist",
                    RequestOptions.create()
                            .setHeader("Authorization", "Bearer " + token)
                            .setHeader("Content-Type", "application/json")
                            .setData(Map.of("productId", product.id())));
            Assert.assertTrue(add.ok(), "Adding to the wishlist should succeed (HTTP " + add.status() + ")");
            added = true;

            Assert.assertTrue(wishlistContains(token, product.id()),
                    "The product should appear in the wishlist after adding");

            APIResponse del = getPage().request().delete(apiBase() + "/customer/wishlist/" + product.id(),
                    RequestOptions.create().setHeader("Authorization", "Bearer " + token));
            Assert.assertTrue(del.ok(), "Removing from the wishlist should succeed");
            added = false;
            Assert.assertFalse(wishlistContains(token, product.id()),
                    "The product should be gone from the wishlist after removal");
        } finally {
            if (added) {
                getPage().request().delete(apiBase() + "/customer/wishlist/" + product.id(),
                        RequestOptions.create().setHeader("Authorization", "Bearer " + token));
            }
        }
    }

    private boolean wishlistContains(String token, String productId) {
        APIResponse resp = getPage().request().get(apiBase() + "/customer/wishlist",
                RequestOptions.create().setHeader("Authorization", "Bearer " + token));
        Assert.assertEquals(resp.status(), 200, "GET wishlist should return 200");
        JsonNode data = tree(resp.text()).path("data");
        JsonNode list = data.path("products");
        if (!list.isArray()) list = data.path("wishlist");
        if (!list.isArray()) list = data;
        for (JsonNode p : list) {
            // Entry may be an id string or an object with _id / product._id.
            if (productId.equals(p.asText(null))) return true;
            if (productId.equals(p.path("_id").asText(null))) return true;
            if (productId.equals(p.path("product").path("_id").asText(null))) return true;
        }
        return false;
    }
}
