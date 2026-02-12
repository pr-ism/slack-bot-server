package com.slack.bot.application.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.setting.dto.request.UpdateNotificationSettingsRequest;
import com.slack.bot.application.setting.dto.response.NotificationSettingsResponse;
import com.slack.bot.domain.setting.DeliverySpace;
import com.slack.bot.domain.setting.NotificationSettings;
import com.slack.bot.infrastructure.setting.JpaNotificationSettings;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NotificationSettingsServiceExceptionHandlingTest {

    @Autowired
    NotificationSettingsService notificationSettingsService;

    @Autowired
    JpaNotificationSettings jpaNotificationSettings;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void 알림_설정이_없으면_update에서_save로_생성한다() {
        // given
        Long projectMemberId = 1L;
        UpdateNotificationSettingsRequest request = new UpdateNotificationSettingsRequest(
                DeliverySpace.TRIGGER_CHANNEL,
                false,
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
                () -> assertThat(actual.projectMemberId()).isEqualTo(projectMemberId),
                () -> assertThat(actual.reservationConfirmedSpace()).isEqualTo(DeliverySpace.TRIGGER_CHANNEL),
                () -> assertThat(actual.reservationCanceledConfirmationEnabled()).isFalse(),
                () -> assertThat(actual.reservationChannelEphemeralEnabled()).isTrue(),
                () -> assertThat(actual.reviewReminderEnabled()).isTrue(),
                () -> assertThat(actual.prMentionEnabled()).isFalse(),
                () -> assertThat(actual.reviewCompletedEnabled()).isTrue(),
                () -> assertThat(saved.getProjectMemberId()).isEqualTo(projectMemberId),
                () -> assertThat(jpaNotificationSettings.count()).isEqualTo(1)
        );
    }

    @Test
    void 중복_저장_상황에서도_동시_요청이_정상_처리된다() throws Exception {
        // given
        Long projectMemberId = 2L;
        UpdateNotificationSettingsRequest request = new UpdateNotificationSettingsRequest(
                DeliverySpace.TRIGGER_CHANNEL,
                false,
                false,
                true,
                false,
                true
        );

        // when
        int attempts = 20;
        int parallelism = 4;
        try (ExecutorService executorService = Executors.newFixedThreadPool(parallelism)) {
            for (int i = 0; i < attempts; i++) {
                jpaNotificationSettings.deleteAll();

                CountDownLatch readyLatch = new CountDownLatch(parallelism);
                CountDownLatch startLatch = new CountDownLatch(1);
                List<Future<NotificationSettingsResponse>> futures = new ArrayList<>();

                for (int j = 0; j < parallelism; j++) {
                    futures.add(executorService.submit(() -> {
                        readyLatch.countDown();
                        startLatch.await(2, TimeUnit.SECONDS);
                        return notificationSettingsService.updateSettings(projectMemberId, request);
                    }));
                }

                readyLatch.await(2, TimeUnit.SECONDS);
                startLatch.countDown();

                List<NotificationSettingsResponse> responses = new ArrayList<>();
                for (Future<NotificationSettingsResponse> future : futures) {
                    responses.add(future.get(2, TimeUnit.SECONDS));
                }

                NotificationSettings saved = transactionTemplate.execute(status ->
                        jpaNotificationSettings.findByProjectMemberId(projectMemberId).orElseThrow()
                );

                // then
                assertAll(
                        () -> assertThat(responses).allSatisfy(response ->
                                assertThat(response.projectMemberId()).isEqualTo(projectMemberId)
                        ),
                        () -> assertThat(saved.getProjectMemberId()).isEqualTo(projectMemberId),
                        () -> assertThat(jpaNotificationSettings.count()).isEqualTo(1)
                );
            }
        }
    }
}
