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

@IntegrationTest
@MockitoSpyBean(types = ReviewNotificationOutboxRepository.class)
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
                () -> assertThat(actual.getFailureType()).isNull()
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
}
