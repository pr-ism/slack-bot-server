package com.slack.bot.application.interactivity.block;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewReservationBlockTypeTest {

    @Test
    void RESERVATION_타입은_리뷰_예약_메시지_타입이다() {
        // given
        ReviewReservationBlockType type = ReviewReservationBlockType.RESERVATION;

        // when
        boolean actual = type.isReservation();

        // then
        assertThat(actual).isTrue();
    }

    @Test
    void RESERVATION_타입은_리뷰_예약_취소_메시지_타입이_아니다() {
        // given
        ReviewReservationBlockType type = ReviewReservationBlockType.RESERVATION;

        // when
        boolean actual = type.isCancellation();

        // then
        assertThat(actual).isFalse();
    }

    @Test
    void CANCELLATION_타입은_리뷰_예약_취소_메시지_타입이다() {
        // given
        ReviewReservationBlockType type = ReviewReservationBlockType.CANCELLATION;

        // when
        boolean actual = type.isCancellation();

        // then
        assertThat(actual).isTrue();
    }

    @Test
    void CANCELLATION_타입은_리뷰_예약_메시지_타입이_아니다() {
        // given
        ReviewReservationBlockType type = ReviewReservationBlockType.CANCELLATION;

        // when
        boolean actual = type.isReservation();

        // then
        assertThat(actual).isFalse();
    }
}
