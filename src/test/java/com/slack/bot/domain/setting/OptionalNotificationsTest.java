package com.slack.bot.domain.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.slack.bot.domain.setting.vo.OptionalNotifications;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OptionalNotificationsTest {

    @Test
    void 선택_알림_설정을_기본값으로_생성하면_모든_선택_알림이_활성화된다() {
        // when & then
        OptionalNotifications actual = assertDoesNotThrow(OptionalNotifications::defaults);

        assertAll(
                () -> assertThat(actual.isReservationCanceledConfirmationEnabled()).isTrue(),
                () -> assertThat(actual.isReviewReminderEnabled()).isTrue(),
                () -> assertThat(actual.isPullRequestMentionEnabled()).isTrue(),
                () -> assertThat(actual.isReviewCompletedEnabled()).isTrue()
        );
    }

    @Test
    void 선택_알림_설정에서_리뷰_예약_취소_확인_메시지를_비활성화한다() {
        // given
        OptionalNotifications optional = OptionalNotifications.defaults();

        // when
        OptionalNotifications actual = optional.updateReservationCanceledConfirmation(false);

        // then
        assertAll(
                () -> assertThat(actual.isReservationCanceledConfirmationEnabled()).isFalse(),
                () -> assertThat(actual.isReviewReminderEnabled()).isTrue(),
                () -> assertThat(actual.isPullRequestMentionEnabled()).isTrue(),
                () -> assertThat(actual.isReviewCompletedEnabled()).isTrue()
        );
    }

    @Test
    void 선택_알림_설정에서_리뷰_예약_취소_확인_메시지를_다시_활성화한다() {
        // given
        OptionalNotifications optional = OptionalNotifications.defaults()
                                                              .updateReservationCanceledConfirmation(false);

        // when
        OptionalNotifications actual = optional.updateReservationCanceledConfirmation(true);

        // then
        assertAll(
                () -> assertThat(actual.isReservationCanceledConfirmationEnabled()).isTrue(),
                () -> assertThat(actual.isReviewReminderEnabled()).isTrue(),
                () -> assertThat(actual.isPullRequestMentionEnabled()).isTrue(),
                () -> assertThat(actual.isReviewCompletedEnabled()).isTrue()
        );
    }

    @Test
    void 선택_알림_설정에서_예약_채널_에페메랄_메시지를_비활성화한다() {
        // given
        OptionalNotifications optional = OptionalNotifications.defaults();

        // when
        OptionalNotifications actual = optional.updateReservationChannelEphemeral(false);

        // then
        assertAll(
                () -> assertThat(actual.isReservationCanceledConfirmationEnabled()).isTrue(),
                () -> assertThat(actual.isReservationChannelEphemeralEnabled()).isFalse(),
                () -> assertThat(actual.isReviewReminderEnabled()).isTrue(),
                () -> assertThat(actual.isPullRequestMentionEnabled()).isTrue(),
                () -> assertThat(actual.isReviewCompletedEnabled()).isTrue()
        );
    }

    @Test
    void 선택_알림_설정에서_예약_채널_에페메랄_메시지를_다시_활성화한다() {
        // given
        OptionalNotifications optional = OptionalNotifications.defaults()
                                                              .updateReservationChannelEphemeral(false);

        // when
        OptionalNotifications actual = optional.updateReservationChannelEphemeral(true);

        // then
        assertAll(
                () -> assertThat(actual.isReservationCanceledConfirmationEnabled()).isTrue(),
                () -> assertThat(actual.isReservationChannelEphemeralEnabled()).isTrue(),
                () -> assertThat(actual.isReviewReminderEnabled()).isTrue(),
                () -> assertThat(actual.isPullRequestMentionEnabled()).isTrue(),
                () -> assertThat(actual.isReviewCompletedEnabled()).isTrue()
        );
    }

    @Test
    void 선택_알림_설정에서_리뷰_리마인드_메시지를_비활성화한다() {
        // given
        OptionalNotifications optional = OptionalNotifications.defaults();

        // when
        OptionalNotifications actual = optional.updateReviewReminder(false);

        // then
        assertAll(
                () -> assertThat(actual.isReservationCanceledConfirmationEnabled()).isTrue(),
                () -> assertThat(actual.isReviewReminderEnabled()).isFalse(),
                () -> assertThat(actual.isPullRequestMentionEnabled()).isTrue(),
                () -> assertThat(actual.isReviewCompletedEnabled()).isTrue()
        );
    }

    @Test
    void 선택_알림_설정에서_리뷰_리마인드_메시지를_다시_활성화한다() {
        // given
        OptionalNotifications optional = OptionalNotifications.defaults()
                                                              .updateReviewReminder(false);

        // when
        OptionalNotifications actual = optional.updateReviewReminder(true);

        // then
        assertAll(
                () -> assertThat(actual.isReservationCanceledConfirmationEnabled()).isTrue(),
                () -> assertThat(actual.isReviewReminderEnabled()).isTrue(),
                () -> assertThat(actual.isPullRequestMentionEnabled()).isTrue(),
                () -> assertThat(actual.isReviewCompletedEnabled()).isTrue()
        );
    }

    @Test
    void 선택_알림_설정에서_PR_멘션_메시지를_비활성화한다() {
        // given
        OptionalNotifications optional = OptionalNotifications.defaults();

        // when
        OptionalNotifications actual = optional.updatePrMention(false);

        // then
        assertAll(
                () -> assertThat(actual.isReservationCanceledConfirmationEnabled()).isTrue(),
                () -> assertThat(actual.isReviewReminderEnabled()).isTrue(),
                () -> assertThat(actual.isPullRequestMentionEnabled()).isFalse(),
                () -> assertThat(actual.isReviewCompletedEnabled()).isTrue()
        );
    }

    @Test
    void 선택_알림_설정에서_PR_멘션_메시지를_다시_활성화한다() {
        // given
        OptionalNotifications optional = OptionalNotifications.defaults()
                                                              .updatePrMention(false);

        // when
        OptionalNotifications actual = optional.updatePrMention(true);

        // then
        assertAll(
                () -> assertThat(actual.isReservationCanceledConfirmationEnabled()).isTrue(),
                () -> assertThat(actual.isReviewReminderEnabled()).isTrue(),
                () -> assertThat(actual.isPullRequestMentionEnabled()).isTrue(),
                () -> assertThat(actual.isReviewCompletedEnabled()).isTrue()
        );
    }

    @Test
    void 선택_알림_설정에서_리뷰_완료_알림_메시지를_비활성화한다() {
        // given
        OptionalNotifications optional = OptionalNotifications.defaults();

        // when
        OptionalNotifications actual = optional.updateReviewCompleted(false);

        // then
        assertAll(
                () -> assertThat(actual.isReservationCanceledConfirmationEnabled()).isTrue(),
                () -> assertThat(actual.isReviewReminderEnabled()).isTrue(),
                () -> assertThat(actual.isPullRequestMentionEnabled()).isTrue(),
                () -> assertThat(actual.isReviewCompletedEnabled()).isFalse()
        );
    }

    @Test
    void 선택_알림_설정에서_리뷰_완료_알림_메시지를_다시_활성화한다() {
        // given
        OptionalNotifications optional = OptionalNotifications.defaults()
                                                              .updateReviewCompleted(false);

        // when
        OptionalNotifications actual = optional.updateReviewCompleted(true);

        // then
        assertAll(
                () -> assertThat(actual.isReservationCanceledConfirmationEnabled()).isTrue(),
                () -> assertThat(actual.isReviewReminderEnabled()).isTrue(),
                () -> assertThat(actual.isPullRequestMentionEnabled()).isTrue(),
                () -> assertThat(actual.isReviewCompletedEnabled()).isTrue()
        );
    }
}
