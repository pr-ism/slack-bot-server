package com.slack.bot.application.interactivity.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BlockActionTypeTest {

    @Test
    void 리뷰_클레임_액션_ID_패턴을_인식한다() {
        // when
        BlockActionType actual = BlockActionType.from("claim_12345");

        // then
        assertThat(actual).isEqualTo(BlockActionType.CLAIM_PREFIX);
    }

    @Test
    void 리뷰_스케줄러_열기_액션을_인식한다() {
        // when
        BlockActionType actual = BlockActionType.from("open_review_scheduler");

        // then
        assertThat(actual).isEqualTo(BlockActionType.OPEN_REVIEW_SCHEDULER);
    }

    @Test
    void 리뷰_예약_취소_액션을_인식한다() {
        // when
        BlockActionType actual = BlockActionType.from("cancel_review_reservation");

        // then
        assertThat(actual).isEqualTo(BlockActionType.CANCEL_REVIEW_RESERVATION);
    }

    @Test
    void 리뷰_예약_변경_액션을_인식한다() {
        // when
        BlockActionType actual = BlockActionType.from("change_review_reservation");

        // then
        assertThat(actual).isEqualTo(BlockActionType.CHANGE_REVIEW_RESERVATION);
    }

    @Test
    void 정의되지_않은_액션은_알_수_없는_액션으로_처리한다() {
        // when
        BlockActionType actual = BlockActionType.from("unknown_action");

        // then
        assertThat(actual).isEqualTo(BlockActionType.UNKNOWN);
    }

    @Test
    void 액션_ID가_없으면_알_수_없는_액션으로_처리한다() {
        // when
        BlockActionType actual = BlockActionType.from(null);

        // then
        assertThat(actual).isEqualTo(BlockActionType.UNKNOWN);
    }

    @Test
    void 빈_액션_ID는_알_수_없는_액션으로_처리한다() {
        // when
        BlockActionType actual = BlockActionType.from("");

        // then
        assertThat(actual).isEqualTo(BlockActionType.UNKNOWN);
    }

    @Test
    void 클레임_접두사로_시작하는_모든_액션과_매칭된다() {
        // given
        BlockActionType type = BlockActionType.CLAIM_PREFIX;

        // when
        boolean actual = type.matches("claim_abc123");

        // then
        assertThat(actual).isTrue();
    }

    @Test
    void 리뷰_스케줄러_열기_액션은_정확히_일치하는_ID만_매칭된다() {
        // given
        BlockActionType type = BlockActionType.OPEN_REVIEW_SCHEDULER;

        // when & then
        assertAll(
                () -> assertThat(type.matches("open_review_scheduler")).isTrue(),
                () -> assertThat(type.matches("open_review_scheduler_extra")).isFalse()
        );
    }

    @Test
    void 알_수_없는_액션은_어떤_ID와도_매칭되지_않는다() {
        // given
        BlockActionType type = BlockActionType.UNKNOWN;

        // when
        boolean actual = type.matches("any_action_id");

        // then
        assertThat(actual).isFalse();
    }

    @Test
    void 액션_ID가_null이면_매칭되지_않는다() {
        // when & then
        assertAll(
                () -> assertThat(BlockActionType.CLAIM_PREFIX.matches(null)).isFalse(),
                () -> assertThat(BlockActionType.OPEN_REVIEW_SCHEDULER.matches(null)).isFalse()
        );
    }

    @Test
    void 액션_ID가_blank이면_매칭되지_않는다() {
        // when & then
        assertAll(
                () -> assertThat(BlockActionType.CLAIM_PREFIX.matches(" ")).isFalse(),
                () -> assertThat(BlockActionType.OPEN_REVIEW_SCHEDULER.matches("")).isFalse()
        );
    }

    @Test
    void 리뷰_클레임_액션인지_확인할_수_있다() {
        // when & then
        assertAll(
                () -> assertThat(BlockActionType.CLAIM_PREFIX.isClaimPrefix()).isTrue(),
                () -> assertThat(BlockActionType.OPEN_REVIEW_SCHEDULER.isClaimPrefix()).isFalse()
        );
    }

    @Test
    void 리뷰_스케줄러_열기_액션인지_확인할_수_있다() {
        // when & then
        assertAll(
                () -> assertThat(BlockActionType.OPEN_REVIEW_SCHEDULER.isOpenReviewScheduler()).isTrue(),
                () -> assertThat(BlockActionType.CLAIM_PREFIX.isOpenReviewScheduler()).isFalse()
        );
    }

    @Test
    void 리뷰_예약_취소_액션인지_확인할_수_있다() {
        // when & then
        assertAll(
                () -> assertThat(BlockActionType.CANCEL_REVIEW_RESERVATION.isCancelReviewReservation()).isTrue(),
                () -> assertThat(BlockActionType.CLAIM_PREFIX.isCancelReviewReservation()).isFalse()
        );
    }

    @Test
    void 리뷰_예약_변경_액션인지_확인할_수_있다() {
        // when & then
        assertAll(
                () -> assertThat(BlockActionType.CHANGE_REVIEW_RESERVATION.isChangeReviewReservation()).isTrue(),
                () -> assertThat(BlockActionType.CLAIM_PREFIX.isChangeReviewReservation()).isFalse()
        );
    }

    @Test
    void 알_수_없는_액션인지_확인할_수_있다() {
        // when & then
        assertAll(
                () -> assertThat(BlockActionType.UNKNOWN.isUnknown()).isTrue(),
                () -> assertThat(BlockActionType.CLAIM_PREFIX.isUnknown()).isFalse()
        );
    }

    @Test
    void 각_액션_타입의_문자열_값을_조회할_수_있다() {
        // when & then
        assertAll(
                () -> assertThat(BlockActionType.CLAIM_PREFIX.value()).isEqualTo("claim_"),
                () -> assertThat(BlockActionType.OPEN_REVIEW_SCHEDULER.value()).isEqualTo("open_review_scheduler"),
                () -> assertThat(BlockActionType.CANCEL_REVIEW_RESERVATION.value()).isEqualTo("cancel_review_reservation"),
                () -> assertThat(BlockActionType.CHANGE_REVIEW_RESERVATION.value()).isEqualTo("change_review_reservation"),
                () -> assertThat(BlockActionType.UNKNOWN.value()).isEmpty()
        );
    }
}
