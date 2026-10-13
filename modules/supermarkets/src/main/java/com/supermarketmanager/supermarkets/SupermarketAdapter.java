package com.supermarketmanager.supermarkets;

import com.supermarketmanager.core.model.Product;
import com.supermarketmanager.core.model.StoreBasket;

import java.util.List;
import java.util.Set;

/**
 * One retailer's (usually reverse-engineered) API. Adapters are read-only by default; pushing a cart needs the
 * user's own session and is never done without an explicit, confirmed request.
 */
public interface SupermarketAdapter {

    enum Capability { SEARCH, CART_HANDOFF, CART_PUSH }

    /** Retailer id, matching price-provider retailer ids where possible (e.g. "ab"). */
    String id();

    String displayName();

    Set<Capability> capabilities();

    /** Products at this retailer; every product has exactly one offer (this retailer's). */
    List<Product> search(String query, int pages);

    /** Everything a person needs to fill the cart by hand: product links and pack counts. */
    default CartHandoff handoff(StoreBasket basket) {
        return CartHandoff.of(this, basket);
    }

    /**
     * Fills the retailer cart using the user's session. Not implemented by any adapter yet: it needs a captured,
     * logged-in HAR of the retailer's site (see docs/SUPERMARKET-ADAPTERS.md). Never places an order.
     */
    default CartPushResult pushCart(StoreBasket basket, java.util.Map<String, String> session, boolean dryRun) {
        throw new UnsupportedOperationException(displayName() + " does not support filling the cart yet");
    }

    record CartPushResult(boolean dryRun, List<String> added, List<String> failed, String cartUrl) {
    }
}
