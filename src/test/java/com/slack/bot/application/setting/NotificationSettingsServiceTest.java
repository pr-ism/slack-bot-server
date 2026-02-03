package com.slack.bot.application.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.setting.dto.request.UpdateNotificationSettingsRequest;
import com.slack.bot.application.setting.dto.response.NotificationSettingsResponse;
import com.slack.bot.domain.setting.DeliverySpace;
import com.slack.bot.domain.setting.NotificationSettings;
import com.slack.bot.domain.setting.vo.OptionalNotifications;
import com.slack.bot.domain.setting.vo.ReservationConfirmed;
import com.slack.bot.infrastructure.setting.JpaNotificationSettings;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NotificationSettingsServiceTest {

    @Autowired
    NotificationSettingsService notificationSettingsService;

    @Autowired
    JpaNotificationSettings jpaNotificationSettings;

    @Test
    void 알림_설정이_없으면_기본값으로_생성한다() {
        // given
        Long projectMemberId = 1L;

        // when
        NotificationSettingsResponse actual = notificationSettingsService.findSettings(projectMemberId);

        // then
        NotificationSettings saved = jpaNotificationSettings.findByProjectMemberId(projectMemberId)
                                                            .orElseThrow();

        assertAll(
                () -> assertThat(actual.projectMemberId()).isEqualTo(projectMemberId),
                () -> assertThat(actual.reservationConfirmedSpace()).isEqualTo(DeliverySpace.DIRECT_MESSAGE),
                () -> assertThat(actual.reservationCanceledConfirmationEnabled()).isTrue(),
                () -> assertThat(actual.reviewReminderEnabled()).isTrue(),
                () -> assertThat(actual.prMentionEnabled()).isTrue(),
                () -> assertThat(actual.reviewCompletedEnabled()).isTrue(),
                () -> assertThat(saved.getProjectMemberId()).isEqualTo(projectMemberId)
        );
    }

    @Test
    void 기존_알림_설정이_있으면_그대로_가져온다() {
        // given
        Long projectMemberId = 2L;
        ReservationConfirmed reservationConfirmed = ReservationConfirmed.defaults()
                                                                        .changeSpace(DeliverySpace.TRIGGER_CHANNEL);
        OptionalNotifications optionalNotifications = OptionalNotifications.defaults()
                                                                           .updateReservationCanceledConfirmation(false)
                                                                           .updateReviewReminder(false)
                                                                           .updatePrMention(false)
                                                                           .updateReviewCompleted(false);
        NotificationSettings settings = NotificationSettings.create(projectMemberId, reservationConfirmed, optionalNotifications);
        jpaNotificationSettings.save(settings);

        // when
        NotificationSettingsResponse actual = notificationSettingsService.findSettings(projectMemberId);

        // then
        assertAll(
                () -> assertThat(actual.projectMemberId()).isEqualTo(projectMemberId),
                () -> assertThat(actual.reservationConfirmedSpace()).isEqualTo(DeliverySpace.TRIGGER_CHANNEL),
                () -> assertThat(actual.reservationCanceledConfirmationEnabled()).isFalse(),
                () -> assertThat(actual.reviewReminderEnabled()).isFalse(),
                () -> assertThat(actual.prMentionEnabled()).isFalse(),
                () -> assertThat(actual.reviewCompletedEnabled()).isFalse(),
                () -> assertThat(jpaNotificationSettings.count()).isEqualTo(1)
        );
    }

    @Test
    void 알림_설정을_업데이트한다() {
        // given
        Long projectMemberId = 3L;
        NotificationSettings settings = NotificationSettings.defaults(projectMemberId);
        jpaNotificationSettings.save(settings);

        UpdateNotificationSettingsRequest request = new UpdateNotificationSettingsRequest(
                DeliverySpace.TRIGGER_CHANNEL,
                false,
                true,
                false,
                true
        );

        // when
        NotificationSettingsResponse actual = notificationSettingsService.updateSettings(projectMemberId, request);

        // then
        NotificationSettings saved = jpaNotificationSettings.findByProjectMemberId(projectMemberId)
                                                            .orElseThrow();

        assertAll(
                () -> assertThat(actual.reservationConfirmedSpace()).isEqualTo(DeliverySpace.TRIGGER_CHANNEL),
                () -> assertThat(actual.reservationCanceledConfirmationEnabled()).isFalse(),
                () -> assertThat(actual.reviewReminderEnabled()).isTrue(),
                () -> assertThat(actual.prMentionEnabled()).isFalse(),
                () -> assertThat(actual.reviewCompletedEnabled()).isTrue(),
                () -> assertThat(saved.getReservationConfirmed().getDeliverySpace()).isEqualTo(DeliverySpace.TRIGGER_CHANNEL),
                () -> assertThat(saved.getOptionalNotifications().isReservationCanceledConfirmationEnabled()).isFalse(),
                () -> assertThat(saved.getOptionalNotifications().isReviewReminderEnabled()).isTrue(),
                () -> assertThat(saved.getOptionalNotifications().isPullRequestMentionEnabled()).isFalse(),
                () -> assertThat(saved.getOptionalNotifications().isReviewCompletedEnabled()).isTrue()
        );
    }
}
