package com.slack.bot.application.interaction.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStatus;
import com.slack.bot.infrastructure.interaction.box.out.repository.SlackNotificationOutboxRepository;
import com.slack.bot.infrastructure.interaction.client.NotificationTransportApiClient;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@MockitoSpyBean(types = SlackNotificationOutboxRepository.class)
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackNotificationOutboxLeaseIntegrationTest {

    @Autowired
    SlackNotificationOutboxProcessor slackNotificationOutboxProcessor;

    @Autowired
    SlackNotificationOutboxRepository slackNotificationOutboxRepository;

    @Autowired
    NotificationTransportApiClient notificationTransportApiClient;

    @Autowired
    Clock clock;

    @BeforeEach
    void setUp() {
        reset(clock, notificationTransportApiClient, slackNotificationOutboxRepository);
    }

    @Test
    @Sql(scripts = "/sql/fixtures/box/out/pending_outbox_channel_text_only.sql")
    void lease를_연장한_outbox는_전송_중_timeout_recovery가_실행되어도_복구되지_않는다() {
        // given
        Instant base = Instant.parse("2026-03-24T00:00:00Z");
        doReturn(
                base,
                base.plusSeconds(70),
                base.plusSeconds(70),
                base.plusSeconds(71)
        ).when(clock).instant();

        doAnswer(invocation -> {
            int recoveredCount = slackNotificationOutboxProcessor.recoverTimeoutProcessing();
            assertThat(recoveredCount).isZero();
            return null;
        }).when(notificationTransportApiClient).sendMessage("xoxb-test-token", "C1", "hello-102");

        // when
        slackNotificationOutboxProcessor.processPending(1);

        // then
        SlackNotificationOutbox actual = slackNotificationOutboxRepository.findById(102L).orElseThrow();

        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(SlackNotificationOutboxStatus.SENT),
                () -> assertThat(actual.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(actual.getFailureType()).isEqualTo(SlackInteractionFailureType.NONE)
        );
        verify(notificationTransportApiClient).sendMessage("xoxb-test-token", "C1", "hello-102");
    }

    @Test
    @Sql(scripts = "/sql/fixtures/box/out/pending_outbox_channel_text_only.sql")
    void lease_연장에_실패한_outbox는_timeout_recovery로_복구된다() {
        // given
        Instant base = Instant.parse("2026-03-24T00:00:00Z");
        doReturn(
                base,
                base.plusSeconds(10),
                base.plusSeconds(70)
        ).when(clock).instant();
        doReturn(false).when(slackNotificationOutboxRepository).renewProcessingLease(any(), any(), any());

        // when
        slackNotificationOutboxProcessor.processPending(1);
        int recoveredCount = slackNotificationOutboxProcessor.recoverTimeoutProcessing();

        // then
        SlackNotificationOutbox actual = slackNotificationOutboxRepository.findById(102L).orElseThrow();

        assertAll(
                () -> assertThat(recoveredCount).isEqualTo(1),
                () -> assertThat(actual.getStatus()).isEqualTo(SlackNotificationOutboxStatus.RETRY_PENDING),
                () -> assertThat(actual.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(actual.getFailureReason()).isNotBlank()
        );
        verify(notificationTransportApiClient, never()).sendMessage(anyString(), anyString(), anyString());
    }
}
