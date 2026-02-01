package com.slack.bot.domain.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReservationStatusTest {

    @Test
    void ACTIVE_상태_여부를_확인한다() {
        // given
        ReservationStatus active = ReservationStatus.ACTIVE;

        // when
        boolean actual = active.isActive();

        // then
        assertThat(actual).isTrue();
    }

    @Test
    void CANCELLED_상태_여부를_확인한다() {
        // given
        ReservationStatus cancelled = ReservationStatus.CANCELLED;

        // when
        boolean actual = cancelled.isCancelled();

        // then
        assertThat(actual).isTrue();
    }
}
