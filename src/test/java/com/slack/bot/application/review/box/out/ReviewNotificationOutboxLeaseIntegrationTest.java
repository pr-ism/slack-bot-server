package com.slack.bot.application.review.box.out;

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
import com.slack.bot.application.worker.PollingHintPublisher;
import com.slack.bot.application.worker.PollingHintTarget;
import com.slack.bot.infrastructure.common.FailureSnapshotDefaults;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.client.NotificationTransportApiClient;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxHistory;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxStatus;
import com.slack.bot.infrastructure.review.box.out.repository.ReviewNotificationOutboxRepository;
import com.slack.bot.infrastructure.review.persistence.box.out.JpaReviewNotificationOutboxHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;

@IntegrationTest
@MockitoSpyBean(types = ReviewNotificationOutboxRepository.class)
@MockitoSpyBean(types = PollingHintPublisher.class)
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewNotificationOutboxLeaseIntegrationTest {

    @Autowired
    ReviewNotificationOutboxProcessor reviewNotificationOutboxProcessor;

    @Autowired
    ReviewNotificationOutboxRepository reviewNotificationOutboxRepository;

    @Autowired
    NotificationTransportApiClient notificationTransportApiClient;

    @Autowired
    Clock clock;

    @Autowired
    JpaReviewNotificationOutboxHistoryRepository jpaReviewNotificationOutboxHistoryRepository;

    @Autowired
    PollingHintPublisher pollingHintPublisher;

    @BeforeEach
    void setUp() {
        reset(clock, notificationTransportApiClient, reviewNotificationOutboxRepository);
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/review/workspace_t1.sql")
    void lease를_연장한_review_outbox는_전송_중_timeout_recovery가_실행되어도_복구되지_않는다() {
        // given
        Instant base = Instant.parse("2026-03-24T00:00:00Z");
        doReturn(
                base,
                base.plusSeconds(70),
                base.plusSeconds(70),
                base.plusSeconds(71)
        ).when(clock).instant();
        ReviewNotificationOutbox outbox = reviewNotificationOutboxRepository.save(pendingOutbox("review-lease-success"));

        doAnswer(invocation -> {
            int recoveredCount = reviewNotificationOutboxProcessor.recoverTimeoutProcessing(60_000L);
            assertThat(recoveredCount).isZero();
            return null;
        }).when(notificationTransportApiClient).sendBlockMessage(
                anyString(),
                anyString(),
                any(),
                any(),
                anyString()
        );

        // when
        reviewNotificationOutboxProcessor.processPending(1);

        // then
        ReviewNotificationOutbox actual = reviewNotificationOutboxRepository.findById(outbox.getId()).orElseThrow();

        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(ReviewNotificationOutboxStatus.SENT),
                () -> assertThat(actual.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(actual.getFailureType()).isEqualTo(SlackInteractionFailureType.NONE)
        );
        verify(notificationTransportApiClient).sendBlockMessage(
                anyString(),
                anyString(),
                any(),
                any(),
                anyString()
        );
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/review/workspace_t1.sql")
    void lease_연장에_실패한_review_outbox는_timeout_recovery로_복구된다() {
        // given
        Instant base = Instant.parse("2026-03-24T00:00:00Z");
        doReturn(
                base,
                base.plusSeconds(10),
                base.plusSeconds(70)
        ).when(clock).instant();
        doReturn(false).when(reviewNotificationOutboxRepository).renewProcessingLease(any(), any(), any());
        ReviewNotificationOutbox outbox = reviewNotificationOutboxRepository.save(pendingOutbox("review-lease-failure"));

        // when
        reviewNotificationOutboxProcessor.processPending(1);
        int recoveredCount = reviewNotificationOutboxProcessor.recoverTimeoutProcessing(60_000L);

        // then
        ReviewNotificationOutbox actual = reviewNotificationOutboxRepository.findById(outbox.getId()).orElseThrow();
        List<ReviewNotificationOutboxHistory> histories = historiesOf(outbox.getId());

        assertAll(
                () -> assertThat(recoveredCount).isEqualTo(1),
                () -> assertThat(actual.getStatus()).isEqualTo(ReviewNotificationOutboxStatus.RETRY_PENDING),
                () -> assertThat(actual.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(actual.getFailureReason()).isNotBlank(),
                () -> assertThat(histories).hasSize(1),
                () -> assertThat(histories.getFirst().getStatus()).isEqualTo(ReviewNotificationOutboxStatus.RETRY_PENDING)
        );
        verify(pollingHintPublisher).publish(PollingHintTarget.REVIEW_NOTIFICATION_OUTBOX);
        verify(notificationTransportApiClient, never()).sendBlockMessage(
                anyString(),
                anyString(),
                any(),
                any(),
                anyString()
        );
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/review/workspace_t1.sql")
    void review_outbox_timeout_recovery는_배치_크기만큼만_처리한다() {
        // given
        Instant base = Instant.parse("2026-03-24T00:10:00Z");
        List<Long> outboxIds = new java.util.ArrayList<>();
        for (int index = 0; index < 101; index++) {
            ReviewNotificationOutbox outbox = pendingOutbox("review-timeout-batch-" + index);
            setProcessingState(outbox, base.minusSeconds(120L + index), 1);
            ReviewNotificationOutbox saved = reviewNotificationOutboxRepository.save(outbox);
            outboxIds.add(saved.getId());
        }

        // when
        int recoveredCount = reviewNotificationOutboxProcessor.recoverTimeoutProcessing(60_000L);

        // then
        List<ReviewNotificationOutbox> actualOutboxes = outboxIds.stream()
                                                                 .map(outboxId -> reviewNotificationOutboxRepository.findById(
                                                                         outboxId
                                                                 ).orElseThrow())
                                                                 .toList();
        List<ReviewNotificationOutboxHistory> actualHistories = jpaReviewNotificationOutboxHistoryRepository.findAll();

        assertAll(
                () -> assertThat(recoveredCount).isEqualTo(100),
                () -> assertThat(actualOutboxes).filteredOn(outbox -> outbox.getStatus() == ReviewNotificationOutboxStatus.RETRY_PENDING)
                        .hasSize(100),
                () -> assertThat(actualOutboxes).filteredOn(outbox -> outbox.getStatus() == ReviewNotificationOutboxStatus.PROCESSING)
                        .hasSize(1),
                () -> assertThat(actualHistories).filteredOn(history -> history.getStatus() == ReviewNotificationOutboxStatus.RETRY_PENDING)
                        .hasSize(100)
        );
        verify(notificationTransportApiClient, never()).sendBlockMessage(
                anyString(),
                anyString(),
                any(),
                any(),
                anyString()
        );
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/review/workspace_t1.sql")
    void review_outbox_timeout_recovery는_재시도_가능과_소진건을_합쳐_배치_크기만큼만_처리한다() {
        // given
        Instant base = Instant.parse("2026-03-24T00:20:00Z");
        List<Long> outboxIds = new java.util.ArrayList<>();
        for (int index = 0; index < 50; index++) {
            ReviewNotificationOutbox retryableOutbox = pendingOutbox("review-timeout-mixed-retry-" + index);
            setProcessingState(retryableOutbox, base.minusSeconds(200L + index), 1);
            ReviewNotificationOutbox saved = reviewNotificationOutboxRepository.save(retryableOutbox);
            outboxIds.add(saved.getId());
        }
        for (int index = 0; index < 51; index++) {
            ReviewNotificationOutbox exhaustedOutbox = pendingOutbox("review-timeout-mixed-failed-" + index);
            setProcessingState(exhaustedOutbox, base.minusSeconds(100L + index), 2);
            ReviewNotificationOutbox saved = reviewNotificationOutboxRepository.save(exhaustedOutbox);
            outboxIds.add(saved.getId());
        }

        // when
        int recoveredCount = reviewNotificationOutboxProcessor.recoverTimeoutProcessing(60_000L);

        // then
        List<ReviewNotificationOutbox> actualOutboxes = outboxIds.stream()
                                                                 .map(outboxId -> reviewNotificationOutboxRepository.findById(
                                                                         outboxId
                                                                 ).orElseThrow())
                                                                 .toList();
        List<ReviewNotificationOutboxHistory> actualHistories = jpaReviewNotificationOutboxHistoryRepository.findAll();

        assertAll(
                () -> assertThat(recoveredCount).isEqualTo(100),
                () -> assertThat(actualOutboxes).filteredOn(outbox -> outbox.getStatus() == ReviewNotificationOutboxStatus.RETRY_PENDING)
                        .hasSize(50),
                () -> assertThat(actualOutboxes).filteredOn(outbox -> outbox.getStatus() == ReviewNotificationOutboxStatus.FAILED)
                        .hasSize(50),
                () -> assertThat(actualOutboxes).filteredOn(outbox -> outbox.getStatus() == ReviewNotificationOutboxStatus.PROCESSING)
                        .hasSize(1),
                () -> assertThat(actualHistories).filteredOn(
                        history -> history.getFailureType() == SlackInteractionFailureType.NONE
                ).hasSize(50),
                () -> assertThat(actualHistories).filteredOn(
                        history -> history.getFailureType() == SlackInteractionFailureType.RETRY_EXHAUSTED
                ).hasSize(50)
        );
        verify(notificationTransportApiClient, never()).sendBlockMessage(
                anyString(),
                anyString(),
                any(),
                any(),
                anyString()
        );
    }

    private ReviewNotificationOutbox pendingOutbox(String idempotencyKey) {
        return ReviewNotificationOutbox.builder()
                                       .idempotencyKey(idempotencyKey)
                                       .teamId("T1")
                                       .channelId("C1")
                                       .blocksJson("[]")
                                       .fallbackText("fallback")
                                       .build();
    }

    private List<ReviewNotificationOutboxHistory> historiesOf(Long outboxId) {
        return jpaReviewNotificationOutboxHistoryRepository.findAll()
                                                           .stream()
                                                           .filter(history -> outboxId.equals(history.getOutboxId()))
                                                           .sorted(Comparator.comparingInt(history -> history.getProcessingAttempt()))
                                                           .toList();
    }

    private void setProcessingState(
            ReviewNotificationOutbox outbox,
            Instant processingStartedAt,
            int processingAttempt
    ) {
        ReflectionTestUtils.setField(outbox, "status", ReviewNotificationOutboxStatus.PROCESSING);
        ReflectionTestUtils.setField(outbox, "processingStartedAt", processingStartedAt);
        ReflectionTestUtils.setField(outbox, "sentAt", FailureSnapshotDefaults.NO_SENT_AT);
        ReflectionTestUtils.setField(outbox, "processingAttempt", processingAttempt);
        ReflectionTestUtils.setField(outbox, "failedAt", FailureSnapshotDefaults.NO_FAILURE_AT);
        ReflectionTestUtils.setField(outbox, "failureReason", FailureSnapshotDefaults.NO_FAILURE_REASON);
        ReflectionTestUtils.setField(outbox, "failureType", SlackInteractionFailureType.NONE);
    }
}
