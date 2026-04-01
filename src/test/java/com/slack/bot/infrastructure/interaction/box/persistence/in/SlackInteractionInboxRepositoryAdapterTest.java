package com.slack.bot.infrastructure.interaction.box.persistence.in;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackInteractionInboxRepositoryAdapterTest {

    @Test
    void recoverTimeoutProcessing은_interactionType이_null이면_예외를_던진다() {
        // given
        SlackInteractionInboxRepositoryAdapter adapter = new SlackInteractionInboxRepositoryAdapter(null, null, null);

        // when & then
        assertThatThrownBy(() -> adapter.recoverTimeoutProcessing(
                null,
                Instant.parse("2026-02-24T00:02:00Z"),
                Instant.parse("2026-02-24T00:05:00Z"),
                "timeout",
                3,
                100
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("interactionType은 비어 있을 수 없습니다.");
    }
}
