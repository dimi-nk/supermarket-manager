package com.supermarketmanager.core;

import com.supermarketmanager.core.text.Text;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextTest {

    @Test
    void normalisesGreek() {
        assertThat(Text.norm("Δέλτα")).isEqualTo(Text.norm("ΔΕΛΤΑ")).isEqualTo("δελτα");
        assertThat(Text.norm("Στραγγιστός")).isEqualTo("στραγγιστοσ");
        assertThat(Text.norm("  Γάλα -- Φρέσκο!! ")).isEqualTo("γαλα φρεσκο");
    }

    @Test
    void tokensMatchAtWordStart() {
        assertThat(Text.containsAllTokens("ΔΕΛΤΑ ΓΙΑΟΥΡΤΙ ΒΙΟΛΟΓΙΚΟ", "βιο")).isTrue();
        assertThat(Text.containsAllTokens("ΑΒΙΟΤΙΚΟ ΠΡΟΙΟΝ", "βιο")).isFalse();
        assertThat(Text.containsAllTokens("Fage Total 2%", "fage total")).isTrue();
        assertThat(Text.containsAllTokens("Fage Total 2%", "fage light")).isFalse();
        assertThat(Text.containsAllTokens("anything", "  ")).isFalse();
    }

    @Test
    void roundsMoney() {
        assertThat(Text.roundMoney(1.005)).isEqualTo(1.01);
        assertThat(Text.roundMoney(2.675)).isEqualTo(2.68);
    }
}
