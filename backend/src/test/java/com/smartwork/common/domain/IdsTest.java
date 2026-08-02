package com.smartwork.common.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UUID v7 generation")
class IdsTest {

    @Test
    void generatesVersion7WithTheRfcVariant() {
        UUID id = Ids.newId();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);   // RFC 4122
    }

    @Test
    void encodesTheGenerationTimestamp() {
        long before = System.currentTimeMillis();
        UUID id = Ids.newId();
        long after = System.currentTimeMillis();

        assertThat(Ids.timestampOf(id)).isBetween(before, after);
    }

    @Test
    void idsGeneratedLaterSortLater() {
        // This is the whole point of v7 over v4: index locality and recoverable ordering.
        UUID earlier = Ids.newId(1_700_000_000_000L);
        UUID later = Ids.newId(1_700_000_001_000L);

        assertThat(Ids.timestampOf(earlier)).isLessThan(Ids.timestampOf(later));
    }

    @Test
    void idsAreUniqueWithinTheSameMillisecond() {
        Set<UUID> generated = new HashSet<>();
        List<UUID> all = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            UUID id = Ids.newId(1_700_000_000_000L);
            generated.add(id);
            all.add(id);
        }

        assertThat(generated).hasSameSizeAs(all);
    }

    @Test
    void rejectsNonV7InputToTimestampOf() {
        UUID v4 = UUID.randomUUID();

        assertThat(v4.version()).isEqualTo(4);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> Ids.timestampOf(v4))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
