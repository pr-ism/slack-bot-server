package com.slack.bot.application.review.box.in;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.slack.bot.infrastructure.review.box.in.ReviewRequestInbox;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxStatus;
import com.slack.bot.infrastructure.review.box.in.repository.ReviewRequestInboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewRequestInboxEntryProcessorUnitTest {

    private static final Instant CLAIMED_PROCESSING_STARTED_AT = Instant.parse("2026-02-15T00:00:00Z");
    private static final Instant RENEWED_PROCESSING_STARTED_AT = Instant.parse("2026-02-15T00:00:05.123456Z");

    @Mock
    ReviewRequestInboxRepository reviewRequestInboxRepository;

    @Mock
    ReviewRequestInboxTransactionalProcessor reviewRequestInboxTransactionalProcessor;

    ReviewRequestInboxEntryProcessor reviewRequestInboxEntryProcessor;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(RENEWED_PROCESSING_STARTED_AT, ZoneOffset.UTC);

        reviewRequestInboxEntryProcessor = new ReviewRequestInboxEntryProcessor(
                fixedClock,
                reviewRequestInboxRepository,
                reviewRequestInboxTransactionalProcessor
        );
    }

    @Test
    void claimed_inbox를_조회하지_못하면_추가_처리없이_종료한다() {
        // given
        given(reviewRequestInboxRepository.findById(10L)).willReturn(Optional.empty());

        // when
        reviewRequestInboxEntryProcessor.processClaimedInbox(10L, CLAIMED_PROCESSING_STARTED_AT);

        // then
        verify(reviewRequestInboxRepository, never()).renewProcessingLease(any(), any(), any());
        verify(reviewRequestInboxTransactionalProcessor, never()).processInTransaction(any(), any());
    }

    @Test
    void claim_lease가_다르면_트랜잭션_처리를_건너뛴다() {
        // given
        ReviewRequestInbox inbox = processingInbox(11L, Instant.parse("2026-02-15T00:00:01Z"));
        given(reviewRequestInboxRepository.findById(11L)).willReturn(Optional.of(inbox));

        // when
        reviewRequestInboxEntryProcessor.processClaimedInbox(11L, CLAIMED_PROCESSING_STARTED_AT);

        // then
        verify(reviewRequestInboxRepository, never()).renewProcessingLease(any(), any(), any());
        verify(reviewRequestInboxTransactionalProcessor, never()).processInTransaction(any(), any());
    }

    @Test
    void lease_연장에_실패하면_트랜잭션_처리를_건너뛴다() {
        // given
        ReviewRequestInbox inbox = processingInbox(12L, CLAIMED_PROCESSING_STARTED_AT);
        given(reviewRequestInboxRepository.findById(12L)).willReturn(Optional.of(inbox));
        given(reviewRequestInboxRepository.renewProcessingLease(12L, CLAIMED_PROCESSING_STARTED_AT, RENEWED_PROCESSING_STARTED_AT))
                .willReturn(false);

        // when
        reviewRequestInboxEntryProcessor.processClaimedInbox(12L, CLAIMED_PROCESSING_STARTED_AT);

        // then
        verify(reviewRequestInboxTransactionalProcessor, never()).processInTransaction(any(), any());
    }

    @Test
    void lease_연장에_성공하면_갱신된_lease로_트랜잭션_처리를_위임한다() {
        // given
        ReviewRequestInbox inbox = processingInbox(13L, CLAIMED_PROCESSING_STARTED_AT);
        given(reviewRequestInboxRepository.findById(13L)).willReturn(Optional.of(inbox));
        given(reviewRequestInboxRepository.renewProcessingLease(13L, CLAIMED_PROCESSING_STARTED_AT, RENEWED_PROCESSING_STARTED_AT))
                .willReturn(true);

        // when
        reviewRequestInboxEntryProcessor.processClaimedInbox(13L, CLAIMED_PROCESSING_STARTED_AT);

        // then
        verify(reviewRequestInboxTransactionalProcessor).processInTransaction(13L, RENEWED_PROCESSING_STARTED_AT);
    }

    private ReviewRequestInbox processingInbox(Long inboxId, Instant processingStartedAt) {
        ReviewRequestInbox inbox = ReviewRequestInbox.pending(
                "review-entry-" + inboxId,
                "test-api-key",
                100L + inboxId,
                "{}",
                CLAIMED_PROCESSING_STARTED_AT
        );
        ReflectionTestUtils.setField(inbox, "id", inboxId);
        ReflectionTestUtils.setField(inbox, "status", ReviewRequestInboxStatus.PROCESSING);
        ReflectionTestUtils.setField(inbox, "processingStartedAt", processingStartedAt);
        ReflectionTestUtils.setField(inbox, "processingAttempt", 1);
        return inbox;
    }
}
