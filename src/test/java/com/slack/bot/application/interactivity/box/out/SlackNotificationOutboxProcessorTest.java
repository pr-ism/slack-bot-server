package com.slack.bot.application.interactivity.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interactivity.client.exception.SlackBotMessageDispatchException;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.infrastructure.interaction.box.SlackInteractivityFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxMessageType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStatus;
import com.slack.bot.infrastructure.interaction.box.out.repository.SlackNotificationOutboxRepository;
import com.slack.bot.infrastructure.interaction.client.NotificationTransportApiClient;
import com.slack.bot.infrastructure.workspace.persistence.JpaWorkspaceRepository;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackNotificationOutboxProcessorTest {

    @Autowired
    SlackNotificationOutboxProcessor slackNotificationOutboxProcessor;

    @Autowired
    SlackNotificationOutboxRepository slackNotificationOutboxRepository;

    @Autowired
    NotificationTransportApiClient notificationTransportApiClient;

    @Autowired
    JpaWorkspaceRepository jpaWorkspaceRepository;

    @Test
    @Sql(scripts = "/sql/fixtures/outbox/pending_outbox.sql")
    void EPHEMERAL_TEXT_outbox는_한번만_전송되고_SENT로_마킹된다() {
        // given
        SlackNotificationOutbox pending = slackNotificationOutboxRepository.findById(100L)
                                                                           .orElseThrow();

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        SlackNotificationOutbox processed = slackNotificationOutboxRepository.findById(pending.getId())
                                                                             .orElseThrow();

        assertAll(
                () -> verify(notificationTransportApiClient).sendEphemeralMessage("xoxb-test-token", "C1", "U1", "hello-100"),
                () -> assertThat(processed.getStatus()).isEqualTo(SlackNotificationOutboxStatus.SENT)
        );
    }

    @Test
    @Sql(scripts = "/sql/fixtures/outbox/pending_outbox.sql")
    void EPHEMERAL_BLOCKS_outbox는_블록_메시지를_전송한다() {
        // given
        SlackNotificationOutbox pending = slackNotificationOutboxRepository.findById(101L)
                                                                           .orElseThrow();

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        SlackNotificationOutbox processed = slackNotificationOutboxRepository.findById(pending.getId())
                                                                             .orElseThrow();

        assertAll(
                () -> verify(notificationTransportApiClient).sendEphemeralBlockMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        eq("U1"),
                        any(),
                        eq("fallback")
                ),
                () -> assertThat(processed.getStatus()).isEqualTo(SlackNotificationOutboxStatus.SENT)
        );
    }

    @Test
    @Sql(scripts = "/sql/fixtures/outbox/pending_outbox.sql")
    void CHANNEL_TEXT_outbox는_채널_텍스트를_전송한다() {
        // given
        SlackNotificationOutbox pending = slackNotificationOutboxRepository.findById(102L)
                                                                           .orElseThrow();

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        SlackNotificationOutbox processed = slackNotificationOutboxRepository.findById(pending.getId())
                                                                             .orElseThrow();

        assertAll(
                () -> verify(notificationTransportApiClient).sendMessage("xoxb-test-token", "C1", "hello-102"),
                () -> assertThat(processed.getStatus()).isEqualTo(SlackNotificationOutboxStatus.SENT)
        );
    }

    @Test
    @Sql(scripts = "/sql/fixtures/outbox/pending_outbox_channel_text_only.sql")
    void workspace_토큰이_갱신되어도_outbox는_최신_토큰으로_전송한다() {
        // given
        Workspace workspace = jpaWorkspaceRepository.findByTeamId("T1")
                                                    .orElseThrow();
        workspace.reconnect("xoxb-rotated-token", workspace.getBotUserId());
        jpaWorkspaceRepository.save(workspace);

        // when
        slackNotificationOutboxProcessor.processPending(1);

        // then
        verify(notificationTransportApiClient, times(1)).sendMessage("xoxb-rotated-token", "C1", "hello-102");
    }

    @Test
    @Sql(scripts = "/sql/fixtures/outbox/processing_timeout_outbox_channel_text.sql")
    void PROCESSING_타임아웃_outbox는_RETRY_PENDING으로_복구된_후_정상_재처리된다() {
        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        SlackNotificationOutbox processed = slackNotificationOutboxRepository.findById(200L)
                                                                             .orElseThrow();

        assertAll(
                () -> verify(notificationTransportApiClient).sendMessage("xoxb-test-token", "C1", "hello-timeout-200"),
                () -> assertThat(processed.getStatus()).isEqualTo(SlackNotificationOutboxStatus.SENT),
                () -> assertThat(processed.getProcessingAttempt()).isEqualTo(1)
        );
    }

    @Test
    @Sql(scripts = "/sql/fixtures/outbox/pending_outbox.sql")
    void CHANNEL_BLOCKS_outbox는_채널_블록을_전송한다() {
        // given
        SlackNotificationOutbox pending = slackNotificationOutboxRepository.findById(103L)
                                                                           .orElseThrow();

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        SlackNotificationOutbox processed = slackNotificationOutboxRepository.findById(pending.getId())
                                                                             .orElseThrow();

        assertAll(
                () -> verify(notificationTransportApiClient).sendBlockMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        any(),
                        eq("fallback")
                ),
                () -> assertThat(processed.getStatus()).isEqualTo(SlackNotificationOutboxStatus.SENT)
        );
    }

    @Test
    @Sql(scripts = "/sql/fixtures/outbox/pending_outbox.sql")
    void 알림_전송_실패시_outbox는_FAILED로_마킹되고_실패사유는_잘려서_저장된다() {
        // given
        String longMessage = "x".repeat(600);
        willThrow(new SlackBotMessageDispatchException(longMessage))
                .given(notificationTransportApiClient)
                .sendEphemeralMessage("xoxb-test-token", "C1", "U1", "hello-104");
        SlackNotificationOutbox pending = slackNotificationOutboxRepository.findById(104L)
                                                                           .orElseThrow();

        // when
        slackNotificationOutboxProcessor.processPending(10);
        SlackNotificationOutbox firstFailed = slackNotificationOutboxRepository.findById(pending.getId())
                                                                               .orElseThrow();

        slackNotificationOutboxProcessor.processPending(10);
        SlackNotificationOutbox secondFailed = slackNotificationOutboxRepository.findById(pending.getId())
                                                                                .orElseThrow();

        // then
        assertAll(
                () -> assertThat(firstFailed.getStatus()).isEqualTo(SlackNotificationOutboxStatus.RETRY_PENDING),
                () -> assertThat(firstFailed.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(secondFailed.getStatus()).isEqualTo(SlackNotificationOutboxStatus.FAILED),
                () -> assertThat(secondFailed.getProcessingAttempt()).isEqualTo(2),
                () -> assertThat(secondFailed.getFailureType()).isEqualTo(SlackInteractivityFailureType.RETRY_EXHAUSTED),
                () -> assertThat(secondFailed.getFailureReason()).isNotNull(),
                () -> assertThat(secondFailed.getFailureReason()).hasSize(500),
                () -> assertThat(secondFailed.getFailureReason()).isEqualTo("x".repeat(500))
        );
    }

    @Test
    @Sql(scripts = "/sql/fixtures/outbox/pending_outbox.sql")
    void 비즈니스_불변식_실패_outbox는_재시도하지않고_즉시_FAILED로_마킹된다() {
        // given
        SlackNotificationOutbox pending = slackNotificationOutboxRepository.findById(105L)
                                                                           .orElseThrow();

        // when
        slackNotificationOutboxProcessor.processPending(10);
        SlackNotificationOutbox failed = slackNotificationOutboxRepository.findById(pending.getId())
                                                                          .orElseThrow();

        // then
        assertAll(
                () -> assertThat(failed.getStatus()).isEqualTo(SlackNotificationOutboxStatus.FAILED),
                () -> assertThat(failed.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(failed.getFailureType()).isEqualTo(SlackInteractivityFailureType.BUSINESS_INVARIANT)
        );
    }

    @Test
    void workspace가_없는_outbox는_비즈니스_불변식_실패로_FAILED_마킹된다() {
        // given
        SlackNotificationOutbox outbox = SlackNotificationOutbox.builder()
                                                                 .messageType(SlackNotificationOutboxMessageType.CHANNEL_TEXT)
                                                                 .idempotencyKey("OUTBOX-NO-WORKSPACE")
                                                                 .teamId("UNKNOWN-TEAM")
                                                                 .channelId("C1")
                                                                 .text("hello")
                                                                 .build();
        SlackNotificationOutbox pending = slackNotificationOutboxRepository.save(outbox);

        // when
        slackNotificationOutboxProcessor.processPending(10);
        SlackNotificationOutbox failed = slackNotificationOutboxRepository.findById(pending.getId())
                                                                          .orElseThrow();

        // then
        assertAll(
                () -> assertThat(failed.getStatus()).isEqualTo(SlackNotificationOutboxStatus.FAILED),
                () -> assertThat(failed.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(failed.getFailureType()).isEqualTo(SlackInteractivityFailureType.BUSINESS_INVARIANT)
        );
    }

}
