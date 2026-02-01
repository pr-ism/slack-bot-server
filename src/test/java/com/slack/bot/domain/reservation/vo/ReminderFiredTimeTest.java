package com.slack.bot.domain.reservation.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReminderFiredTimeTest {

    @Test
    void 발송_시간으로_초기화한다() {
        // given
        Instant now = Instant.now();

        // when
        ReminderFiredTime firedTime = ReminderFiredTime.of(now);

        // then
        assertAll(
                () -> assertThat(firedTime.isFired()).isTrue(),
                () -> assertThat(firedTime.getValue()).isEqualTo(now)
        );
    }

    @Test
    void 발송_시간이_null이면_미발송_상태로_초기화된다() {
        // when
        ReminderFiredTime firedTime = ReminderFiredTime.of(null);

        // then
        assertAll(
                () -> assertThat(firedTime.isFired()).isFalse(),
                () -> assertThat(firedTime.getValue()).isNull(),
                () -> assertThat(firedTime).isEqualTo(ReminderFiredTime.notFired())
        );
    }

    @Test
    void 미발송_상태를_생성한다() {
        // when
        ReminderFiredTime notFired = ReminderFiredTime.notFired();

        // then
        assertAll(
                () -> assertThat(notFired.isFired()).isFalse(),
                () -> assertThat(notFired.getValue()).isNull()
        );
    }
}
