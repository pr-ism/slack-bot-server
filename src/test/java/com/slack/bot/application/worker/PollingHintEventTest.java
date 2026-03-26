package com.slack.bot.application.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PollingHintEventTest {

    @Test
    void target이_있으면_이벤트를_생성한다() {
        // given
        PollingHintTarget target = PollingHintTarget.BLOCK_ACTION_INBOX;

        // when
        PollingHintEvent pollingHintEvent = new PollingHintEvent(target);

        // then
        assertAll(
                () -> assertThat(pollingHintEvent.target()).isEqualTo(target)
        );
    }

    @Test
    void target이_null이면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> new PollingHintEvent(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("target은 비어 있을 수 없습니다.");
    }
}
