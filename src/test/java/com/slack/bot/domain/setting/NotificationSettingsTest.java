package com.slack.bot.domain.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.slack.bot.domain.setting.vo.OptionalNotifications;
import com.slack.bot.domain.setting.vo.ReservationConfirmed;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NotificationSettingsTest {

    @Test
    void 알림_설정을_기본값으로_생성하면_리뷰_예약_완료_확인_메시지는_DM으로_전달된다() {
        // when
        NotificationSettings actual = assertDoesNotThrow(() -> NotificationSettings.defaults(1L));

        // then
        assertAll(
                () -> assertThat(actual.getProjectMemberId()).isEqualTo(1L),
                () -> assertThat(actual.getReservationConfirmed().getDeliverySpace()).isEqualTo(DeliverySpace.DM),
                () -> assertThat(actual.getOptionalNotifications().isReservationCanceledConfirmationEnabled()).isTrue(),
                () -> assertThat(actual.getOptionalNotifications().isReviewReminderEnabled()).isTrue(),
                () -> assertThat(actual.getOptionalNotifications().isPrMentionEnabled()).isTrue(),
                () -> assertThat(actual.getOptionalNotifications().isReviewCompletedEnabled()).isTrue()
        );
    }

    @Test
    void 프로젝트_멤버_식별자가_비어_있다면_알림_설정을_기본값으로_생성할_수_없다() {
        // when & then
        assertThatThrownBy(() -> NotificationSettings.defaults(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("프로젝트 멤버의 식별자는 비어 있을 수 없습니다.");
    }

    @Test
    void 알림_설정을_생성한다() {
        // given
        ReservationConfirmed reservationConfirmed = ReservationConfirmed.defaults();
        OptionalNotifications optional = OptionalNotifications.defaults();

        // when
        NotificationSettings actual = assertDoesNotThrow(() -> NotificationSettings.create(1L, reservationConfirmed, optional));

        // then
        assertAll(
                () -> assertThat(actual.getProjectMemberId()).isEqualTo(1L),
                () -> assertThat(actual.getReservationConfirmed()).isEqualTo(reservationConfirmed),
                () -> assertThat(actual.getOptionalNotifications()).isEqualTo(optional)
        );
    }

    @Test
    void 리뷰_예약_완료_확인_메시지_설정이_비어_있으면_알림_설정을_생성할_수_없다() {
        // given
        OptionalNotifications optional = OptionalNotifications.defaults();

        // when & then
        assertThatThrownBy(() -> NotificationSettings.create(1L, null, optional))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("리뷰 예약 확인 설정은 비어 있을 수 없습니다.");
    }

    @Test
    void 선택_알림_설정이_비어_있으면_알림_설정을_생성할_수_없다() {
        // given
        ReservationConfirmed reservationConfirmed = ReservationConfirmed.defaults();

        // when & then
        assertThatThrownBy(() -> NotificationSettings.create(1L, reservationConfirmed, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("알림 설정은 비어 있을 수 없습니다.");
    }

    @Test
    void 알림_설정에서_리뷰_예약_완료_확인_메시지_전달_공간을_변경한다() {
        // given
        NotificationSettings settings = NotificationSettings.defaults(1L);

        // when
        settings.changeReservationConfirmedSpace(DeliverySpace.TRIGGER_CHANNEL);

        // then
        assertThat(settings.getReservationConfirmed().getDeliverySpace()).isEqualTo(DeliverySpace.TRIGGER_CHANNEL);
    }

    @Test
    void 알림_설정에서_리뷰_예약_취소_확인_메시지를_비활성화한다() {
        // given
        NotificationSettings settings = NotificationSettings.defaults(1L);

        // when
        settings.updateReservationCanceledConfirmation(false);

        // then
        assertThat(settings.getOptionalNotifications().isReservationCanceledConfirmationEnabled()).isFalse();
    }

    @Test
    void 알림_설정에서_리뷰_예약_취소_확인_메시지를_다시_활성화한다() {
        // given
        NotificationSettings settings = NotificationSettings.defaults(1L);

        settings.updateReservationCanceledConfirmation(false);

        // when
        settings.updateReservationCanceledConfirmation(true);

        // then
        assertThat(settings.getOptionalNotifications().isReservationCanceledConfirmationEnabled()).isTrue();
    }

    @Test
    void 알림_설정에서_리뷰_리마인드_메시지를_비활성화한다() {
        // given
        NotificationSettings settings = NotificationSettings.defaults(1L);

        // when
        settings.updateReviewReminder(false);

        // then
        assertThat(settings.getOptionalNotifications().isReviewReminderEnabled()).isFalse();
    }

    @Test
    void 알림_설정에서_리뷰_리마인드_메시지를_다시_활성화한다() {
        // given
        NotificationSettings settings = NotificationSettings.defaults(1L);

        settings.updateReviewReminder(false);

        // when
        settings.updateReviewReminder(true);

        // then
        assertThat(settings.getOptionalNotifications().isReviewReminderEnabled()).isTrue();
    }

    @Test
    void 알림_설정에서_PR_멘션_메시지를_비활성화한다() {
        // given
        NotificationSettings settings = NotificationSettings.defaults(1L);

        // when
        settings.updatePrMention(false);

        // then
        assertThat(settings.getOptionalNotifications().isPrMentionEnabled()).isFalse();
    }

    @Test
    void 알림_설정에서_PR_멘션_메시지를_다시_활성화한다() {
        // given
        NotificationSettings settings = NotificationSettings.defaults(1L);

        settings.updatePrMention(false);

        // when
        settings.updatePrMention(true);

        // then
        assertThat(settings.getOptionalNotifications().isPrMentionEnabled()).isTrue();
    }

    @Test
    void 알림_설정에서_리뷰_완료_알림_메시지를_비활성화한다() {
        // given
        NotificationSettings settings = NotificationSettings.defaults(1L);

        // when
        settings.updateReviewCompleted(false);

        // then
        assertThat(settings.getOptionalNotifications().isReviewCompletedEnabled()).isFalse();
    }

    @Test
    void 알림_설정에서_리뷰_완료_알림_메시지를_다시_활성화한다() {
        // given
        NotificationSettings settings = NotificationSettings.defaults(1L);

        settings.updateReviewCompleted(false);

        // when
        settings.updateReviewCompleted(true);

        // then
        assertThat(settings.getOptionalNotifications().isReviewCompletedEnabled()).isTrue();
    }
}
