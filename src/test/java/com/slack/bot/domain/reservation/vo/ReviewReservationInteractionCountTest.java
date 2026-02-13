package com.slack.bot.domain.reservation.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewReservationInteractionCountTest {

    @Test
    void 기본값을_생성하면_변경_및_취소_횟수는_0이다() {
        // when
        ReviewReservationInteractionCount actual = ReviewReservationInteractionCount.defaults();

        // then
        assertAll(
                () -> assertThat(actual.getScheduleCancelCount()).isZero(),
                () -> assertThat(actual.getScheduleChangeCount()).isZero()
        );
    }

    @Test
    void 예약_취소_횟수를_증가시킨다() {
        // given
        ReviewReservationInteractionCount count = ReviewReservationInteractionCount.defaults();

        // when
        count.increaseScheduleCancelCount();

        // then
        assertAll(
                () -> assertThat(count.getScheduleCancelCount()).isEqualTo(1),
                () -> assertThat(count.getScheduleChangeCount()).isZero()
        );
    }

    @Test
    void 예약_변경_횟수를_증가시킨다() {
        // given
        ReviewReservationInteractionCount count = ReviewReservationInteractionCount.defaults();

        // when
        count.increaseScheduleChangeCount();

        // then
        assertAll(
                () -> assertThat(count.getScheduleCancelCount()).isZero(),
                () -> assertThat(count.getScheduleChangeCount()).isEqualTo(1)
        );
    }

    @Test
    void 예약_변경_및_취소_횟수를_직접_생성한다() {
        // when
        ReviewReservationInteractionCount actual = ReviewReservationInteractionCount.create(2, 3);

        // then
        assertAll(
                () -> assertThat(actual.getScheduleCancelCount()).isEqualTo(2),
                () -> assertThat(actual.getScheduleChangeCount()).isEqualTo(3)
        );
    }

    @Test
    void 예약_취소_횟수는_음수로_생성할_수_없다() {
        // when & then
        assertThatThrownBy(() -> ReviewReservationInteractionCount.create(-1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("예약 취소 횟수는 음수일 수 없습니다.");
    }

    @Test
    void 예약_변경_횟수는_음수로_생성할_수_없다() {
        // when & then
        assertThatThrownBy(() -> ReviewReservationInteractionCount.create(0, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("예약 변경 횟수는 음수일 수 없습니다.");
    }
}
