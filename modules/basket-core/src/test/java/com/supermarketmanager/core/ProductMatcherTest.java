package com.supermarketmanager.core;

import com.supermarketmanager.core.fill.ProductMatcher;
import com.supermarketmanager.core.model.Product;
import com.supermarketmanager.core.model.ProductRef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMatcherTest {
    final Product fage = new Product("posokanei", "77", "ΦΑΓΕ TOTAL ΓΙΑΟΥΡΤΙ ΣΤΡΑΓΓΙΣΤΟ 2% 500Γ", "ΦΑΓΕ", "5201054017456", "g", 500.0, false, List.of());
    final Product noBarcode = new Product("ab", "7079032", "Γιαούρτι Στραγγιστό 2% 1kg", "TOTAL", null, "kg", 1.0, false, List.of());

    @Test
    void barcodeDecidesWhenBothHaveOne() {
        assertThat(ProductMatcher.matches(new ProductRef(null, null, "anything", null, "5201054017456", null, null), fage)).isTrue();
        assertThat(ProductMatcher.matches(new ProductRef(null, null, "ΦΑΓΕ total", "ΦΑΓΕ", "999", null, null), fage)).isFalse();
    }

    @Test
    void sourceAndIdMatchExactly() {
        assertThat(ProductMatcher.matches(new ProductRef("77", "posokanei", null, null, null, null, null), fage)).isTrue();
        assertThat(ProductMatcher.matches(new ProductRef("78", "posokanei", null, null, null, null, null), fage)).isFalse();
    }

    @Test
    void namesIgnoreSizesAndAccentsButNeedTheBrand() {
        assertThat(ProductMatcher.matches(new ProductRef(null, null, "Φάγε Total στραγγιστό 2% 500g", "Φάγε", null, 0.5, null), fage)).isTrue();
        assertThat(ProductMatcher.matches(new ProductRef(null, null, "Στραγγιστό", "ΔΕΛΤΑ", null, null, null), fage)).isFalse();
        assertThat(ProductMatcher.matches(new ProductRef(null, null, "Στραγγιστό", "total", null, null, null), noBarcode)).isTrue();
    }

    @Test
    void aRequestedBarcodeIsNeverGuessedFromTheName() {
        assertThat(ProductMatcher.matches(new ProductRef(null, null, "Γιαούρτι Στραγγιστό", "TOTAL", "520", null, null), noBarcode)).isFalse();
        assertThat(ProductMatcher.matches(null, noBarcode)).isTrue();
    }
}
