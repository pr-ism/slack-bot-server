package com.slack.bot.application.interactivity.block.handler.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InMemoryStartReviewMarkStoreTest {

    @Test
    void get과_remove가_정상_동작한다() {
        // given
        InMemoryStartReviewMarkStore store = new InMemoryStartReviewMarkStore();
        String key = "T1:123:10:U1";
        Instant markedAt = Instant.parse("2026-02-13T00:00:00Z");
        store.put(key, markedAt);

        // when
        Instant found = store.get(key);
        store.remove(key);
        Instant afterRemove = store.get(key);

        // then
        assertThat(found).isEqualTo(markedAt);
        assertThat(afterRemove).isNull();
    }
}
