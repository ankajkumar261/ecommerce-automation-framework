package com.portfolio.ecommerce.mobile.data;

/**
 * Constants shared by the mobile tests.
 *
 * Only genuinely fixed values live here. Anything that varies per environment — customer
 * credentials, the COD-eligible product name — stays in config-${env}.properties and is
 * read through ConfigReader, because a name that exists only on dev makes a live run fail
 * for data reasons that look exactly like script failures.
 */
public final class MobileTestData {

    private MobileTestData() { }

    /**
     * The size the tests add to the cart. Every product in the catalogue carries S/M/L/XL,
     * and Add to cart does not appear until a size is chosen, so this has to be a real one.
     */
    public static final String DEFAULT_SIZE = "M";

    /** A term the catalogue matches. Assertions check "every result contains it", not a fixed list. */
    public static final String SEARCH_TERM = "Suit";

    /** A term nothing can match — for the empty-state assertions. */
    public static final String NONSENSE_SEARCH_TERM = "zzzqqqxyz";

    /** A coupon code the backend cannot recognise, for the invalid-coupon path. */
    public static final String INVALID_COUPON = "QAAUTONOTREAL";

    /**
     * Prefix for records these tests create (addresses, notes). Anything carrying this tag is
     * ours to edit or delete; anything without it belongs to somebody else and must be left
     * alone — the dev account is shared with manual QA and the web suite.
     */
    public static final String AUTOMATION_TAG_PREFIX = "QA-AUTO-";

    /** A tag unique to this moment, so parallel or repeated runs never collide. */
    public static String uniqueTag() {
        return AUTOMATION_TAG_PREFIX + System.currentTimeMillis();
    }
}
