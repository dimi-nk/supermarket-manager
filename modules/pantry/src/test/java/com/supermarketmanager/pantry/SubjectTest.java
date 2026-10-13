package com.supermarketmanager.pantry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubjectTest {
    @Test
    void parsesAndPrintsKeys() {
        assertThat(Subject.parse("category:yoghurt")).isEqualTo(Subject.category("yoghurt"));
        assertThat(Subject.parse("PRODUCT:fage:2x200")).isEqualTo(Subject.product("fage:2x200"));
        assertThat(Subject.product("x").key()).isEqualTo("product:x");
        assertThat(Subject.isKey("category:a")).isTrue();
        assertThat(Subject.isKey("line-1")).isFalse();
    }

    @Test
    void rejectsMalformedKeys() {
        assertThatThrownBy(() -> Subject.parse("yoghurt")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Subject.parse("shelf:a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Subject.parse("category:")).isInstanceOf(IllegalArgumentException.class);
    }
}
