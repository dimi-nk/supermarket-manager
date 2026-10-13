package com.supermarketmanager.prices;

import com.supermarketmanager.core.fill.StorePlan;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class PriceSettingsRepository {
    private final JdbcClient jdbc;

    public PriceSettingsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public PriceSettings settings() {
        return jdbc.sql("SELECT * FROM prices_settings WHERE id = 1").query((rs, i) -> new PriceSettings(
                rs.getInt("max_retailers"), rs.getDouble("pack_rounding_tolerance"), rs.getInt("max_price_age_days"),
                rs.getInt("search_pages"))).single();
    }

    public void save(PriceSettings s) {
        jdbc.sql("UPDATE prices_settings SET max_retailers = ?, pack_rounding_tolerance = ?, max_price_age_days = ?, search_pages = ? WHERE id = 1")
                .params(s.maxRetailers(), s.packRoundingTolerance(), s.maxPriceAgeDays(), s.searchPages()).update();
    }

    public List<RetailerSetting> retailers() {
        return jdbc.sql("SELECT * FROM prices_retailer ORDER BY retailer").query((rs, i) -> new RetailerSetting(
                rs.getString("retailer"), rs.getString("display_name"), rs.getDouble("fee"),
                rs.getObject("free_above") == null ? null : rs.getDouble("free_above"),
                rs.getObject("min_basket") == null ? null : rs.getDouble("min_basket"))).list();
    }

    public void saveRetailer(RetailerSetting r) {
        jdbc.sql("""
                INSERT INTO prices_retailer (retailer, display_name, fee, free_above, min_basket) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (retailer) DO UPDATE SET display_name = excluded.display_name, fee = excluded.fee,
                    free_above = excluded.free_above, min_basket = excluded.min_basket""")
                .params(r.retailer(), r.displayName(), r.fee(), r.freeAbove(), r.minBasket()).update();
    }

    public int deleteRetailer(String retailer) {
        return jdbc.sql("DELETE FROM prices_retailer WHERE retailer = ?").param(retailer).update();
    }

    public Map<String, StorePlan.Fees> fees() {
        return retailers().stream().collect(Collectors.toMap(RetailerSetting::retailer,
                r -> new StorePlan.Fees(r.fee(), r.freeAbove(), r.minBasket())));
    }
}
