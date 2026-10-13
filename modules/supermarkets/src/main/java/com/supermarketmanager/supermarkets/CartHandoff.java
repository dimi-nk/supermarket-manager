package com.supermarketmanager.supermarkets;

import com.supermarketmanager.core.model.FillPart;
import com.supermarketmanager.core.model.ResolvedLine;
import com.supermarketmanager.core.model.StoreBasket;

import java.util.ArrayList;
import java.util.List;

/** A store basket turned into a click-through list for the retailer's site. */
public record CartHandoff(String retailer, String retailerName, double total, List<Item> items) {

    public record Item(String lineId, String product, String brand, int packs, Double price, Double cost, String url) {
    }

    static CartHandoff of(SupermarketAdapter adapter, StoreBasket basket) {
        var items = new ArrayList<Item>();
        for (ResolvedLine line : basket.lines()) {
            for (FillPart p : line.parts()) {
                items.add(new Item(line.lineId(), p.candidate().name(), p.candidate().brand(), p.packs(), p.candidate().price(),
                        p.cost(), p.candidate().url()));
            }
        }
        return new CartHandoff(basket.retailer(), adapter == null ? basket.retailerName() : adapter.displayName(), basket.total(), items);
    }
}
