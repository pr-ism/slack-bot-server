package com.slack.bot.application.interaction.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interaction.BlockActionInteractionService;
import com.slack.bot.application.interaction.box.BoxFailureReasonTruncator;
import com.slack.bot.application.interaction.box.in.exception.InboxProcessingLeaseLostException;
import com.slack.bot.application.interaction.box.retry.InteractionRetryExceptionClassifier;
import com.slack.bot.application.interaction.view.ViewSubmissionInteractionCoordinator;
import com.slack.bot.global.config.properties.InteractionRetryProperties;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxStatus;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import com.slack.bot.infrastructure.interaction.box.in.repository.SlackInteractionInboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackInteractionInboxEntryProcessorUnitTest {

    private static final Instant CLAIMED_PROCESSING_STARTED_AT = Instant.parse("2026-02-15T00:00:00Z");

    @Mock
    BlockActionInteractionService blockActionInteractionService;

    @Mock
    ViewSubmissionInteractionCoordinator viewSubmissionInteractionCoordinator;

    @Mock
    SlackInteractionInboxRepository slackInteractionInboxRepository;

    SlackInteractionInboxEntryProcessor slackInteractionInboxEntryProcessor;

    @BeforeEach
    void setUp() {
        InteractionRetryProperties retryProperties = new InteractionRetryProperties(
                new InteractionRetryProperties.InboxRetryProperties(2, 100, 2.0, 1000),
                new InteractionRetryProperties.OutboxRetryProperties(2, 100, 2.0, 1000)
        );
        InteractionRetryExceptionClassifier classifier = InteractionRetryExceptionClassifier.create();

        slackInteractionInboxEntryProcessor = new SlackInteractionInboxEntryProcessor(
                Clock.systemUTC(),
                new ObjectMapper(),
                createInboxRetryTemplate(retryProperties, classifier),
                new BoxFailureReasonTruncator(),
                retryProperties,
                blockActionInteractionService,
                slackInteractionInboxRepository,
                classifier,
                viewSubmissionInteractionCoordinator
        );
    }

    private RetryTemplate createInboxRetryTemplate(
            InteractionRetryProperties retryProperties,
            InteractionRetryExceptionClassifier classifier
    ) {
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(
                retryProperties.inbox().maxAttempts(),
                classifier.getRetryableExceptions(),
                true,
                false
        ));
        retryTemplate.setBackOffPolicy(new NoBackOffPolicy());

        return retryTemplate;
    }

    @Test
    void claimed_inbox를_조회하지_못하면_추가_처리없이_종료한다() {
        // given
        given(slackInteractionInboxRepository.findById(10L)).willReturn(Optional.empty());

        // when
        slackInteractionInboxEntryProcessor.processClaimedBlockAction(10L, CLAIMED_PROCESSING_STARTED_AT);

        // then
        verify(slackInteractionInboxRepository, never()).saveIfProcessingLeaseMatched(any(), any(), any());
        verify(blockActionInteractionService, never()).handle(any());
    }

    @Test
    void inboxId가_null이면_즉시_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> slackInteractionInboxEntryProcessor.processClaimedBlockAction(
                null,
                CLAIMED_PROCESSING_STARTED_AT
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("inboxId는 비어 있을 수 없습니다.");
    }

    @Test
    void claimedProcessingStartedAt이_null이면_즉시_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> slackInteractionInboxEntryProcessor.processClaimedBlockAction(
                10L,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("claimedProcessingStartedAt은 비어 있을 수 없습니다.");
    }

    @Test
    void 나노초를_포함한_claimedProcessingStartedAt도_DB_정밀도에_맞춰_정상_처리한다() {
        // given
        Instant claimedProcessingStartedAt = Instant.parse("2026-02-15T00:00:00.123456789Z");
        Instant persistedProcessingStartedAt = Instant.parse("2026-02-15T00:00:00.123456Z");
        SlackInteractionInbox actual = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "normalized-lease",
                "{}"
        );
        setProcessingState(actual, persistedProcessingStartedAt, 1);

        given(slackInteractionInboxRepository.findById(18L)).willReturn(Optional.of(actual));
        given(slackInteractionInboxRepository.saveIfProcessingLeaseMatched(eq(actual), any(), eq(persistedProcessingStartedAt)))
                .willReturn(true);

        // when
        slackInteractionInboxEntryProcessor.processClaimedBlockAction(18L, claimedProcessingStartedAt);

        // then
        verify(blockActionInteractionService).handle(any());
        verify(slackInteractionInboxRepository).saveIfProcessingLeaseMatched(eq(actual), any(), eq(persistedProcessingStartedAt));
    }

    @Test
    void claimed_lease가_없는_inbox는_처리를_건너뛴다() {
        // given
        SlackInteractionInbox actual = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "missing-lease",
                "{}"
        );
        ReflectionTestUtils.setField(actual, "processingAttempt", 1);

        given(slackInteractionInboxRepository.findById(16L)).willReturn(Optional.of(actual));

        // when
        slackInteractionInboxEntryProcessor.processClaimedBlockAction(16L, CLAIMED_PROCESSING_STARTED_AT);

        // then
        verify(blockActionInteractionService, never()).handle(any());
        verify(slackInteractionInboxRepository, never()).saveIfProcessingLeaseMatched(any(), any(), any());
    }

    @Test
    void claimed_lease와_다른_processing_lease를_가진_inbox는_처리를_건너뛴다() {
        // given
        SlackInteractionInbox actual = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "mismatched-lease",
                "{}"
        );
        setProcessingState(actual, Instant.parse("2026-02-15T00:00:03Z"), 1);

        given(slackInteractionInboxRepository.findById(17L)).willReturn(Optional.of(actual));

        // when
        slackInteractionInboxEntryProcessor.processClaimedBlockAction(17L, CLAIMED_PROCESSING_STARTED_AT);

        // then
        verify(blockActionInteractionService, never()).handle(any());
        verify(slackInteractionInboxRepository, never()).saveIfProcessingLeaseMatched(any(), any(), any());
    }

    @Test
    void block_action_정상처리시_handle이_호출되고_PROCESSED로_저장된다() {
        // given
        SlackInteractionInbox actual = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "block-action-success",
                "{}"
        );
        setProcessingState(actual, Instant.parse("2026-02-15T00:00:00Z"), 1);

        given(slackInteractionInboxRepository.findById(11L)).willReturn(Optional.of(actual));
        given(slackInteractionInboxRepository.saveIfProcessingLeaseMatched(eq(actual), any(), eq(CLAIMED_PROCESSING_STARTED_AT)))
                .willReturn(true);

        // when
        slackInteractionInboxEntryProcessor.processClaimedBlockAction(11L, CLAIMED_PROCESSING_STARTED_AT);

        // then
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSED),
                () -> assertThat(actual.getProcessingAttempt()).isEqualTo(1)
        );
        verify(blockActionInteractionService).handle(any());
        verify(viewSubmissionInteractionCoordinator, never()).handleEnqueued(any());
        verify(slackInteractionInboxRepository).saveIfProcessingLeaseMatched(eq(actual), any(), eq(CLAIMED_PROCESSING_STARTED_AT));
    }

    @Test
    void 재시도_가능_예외이고_첫_시도면_RETRY_PENDING으로_마킹된다() {
        // given
        SlackInteractionInbox actual = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "retry-first-attempt",
                "{}"
        );
        setProcessingState(actual, Instant.parse("2026-02-15T00:00:00Z"), 1);

        given(slackInteractionInboxRepository.findById(10L)).willReturn(Optional.of(actual));
        given(slackInteractionInboxRepository.saveIfProcessingLeaseMatched(eq(actual), any(), eq(CLAIMED_PROCESSING_STARTED_AT)))
                .willReturn(true);
        willThrow(new ResourceAccessException("temporary network failure"))
                .given(blockActionInteractionService)
                .handle(any());

        // when
        slackInteractionInboxEntryProcessor.processClaimedBlockAction(10L, CLAIMED_PROCESSING_STARTED_AT);

        // then
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(SlackInteractionInboxStatus.RETRY_PENDING),
                () -> assertThat(actual.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(actual.getFailure().type()).isEqualTo(SlackInteractionFailureType.RETRYABLE)
        );
        verify(slackInteractionInboxRepository).saveIfProcessingLeaseMatched(eq(actual), any(), eq(CLAIMED_PROCESSING_STARTED_AT));
    }

    @Test
    void 재시도_가능_예외이고_최대_시도에_도달하면_FAILED와_RETRY_EXHAUSTED로_마킹된다() {
        // given
        Instant currentProcessingStartedAt = Instant.parse("2026-02-15T00:02:00Z");
        SlackInteractionInbox actual = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "retry-max-attempt",
                "{}"
        );
        setProcessingState(actual, Instant.parse("2026-02-15T00:00:00Z"), 1);
        actual.markRetryPending(Instant.parse("2026-02-15T00:01:00Z"), "previous retry failure");
        setProcessingState(actual, currentProcessingStartedAt, 2);

        given(slackInteractionInboxRepository.findById(10L)).willReturn(Optional.of(actual));
        given(slackInteractionInboxRepository.saveIfProcessingLeaseMatched(eq(actual), any(), eq(currentProcessingStartedAt)))
                .willReturn(true);
        willThrow(new ResourceAccessException("temporary network failure"))
                .given(blockActionInteractionService)
                .handle(any());

        // when
        slackInteractionInboxEntryProcessor.processClaimedBlockAction(10L, currentProcessingStartedAt);

        // then
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(SlackInteractionInboxStatus.FAILED),
                () -> assertThat(actual.getProcessingAttempt()).isEqualTo(2),
                () -> assertThat(actual.getFailure().type()).isEqualTo(SlackInteractionFailureType.RETRY_EXHAUSTED)
        );
        verify(slackInteractionInboxRepository).saveIfProcessingLeaseMatched(eq(actual), any(), eq(currentProcessingStartedAt));
    }

    @Test
    void 긴_실패사유는_잘려서_저장된다() {
        // given
        SlackInteractionInbox actual = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "long-failure-reason",
                "{}"
        );
        setProcessingState(actual, Instant.parse("2026-02-15T00:00:00Z"), 1);

        given(slackInteractionInboxRepository.findById(10L)).willReturn(Optional.of(actual));
        given(slackInteractionInboxRepository.saveIfProcessingLeaseMatched(eq(actual), any(), eq(CLAIMED_PROCESSING_STARTED_AT)))
                .willReturn(true);
        willThrow(new IllegalArgumentException("x".repeat(600)))
                .given(blockActionInteractionService)
                .handle(any());

        // when
        slackInteractionInboxEntryProcessor.processClaimedBlockAction(10L, CLAIMED_PROCESSING_STARTED_AT);

        // then
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(SlackInteractionInboxStatus.FAILED),
                () -> assertThat(actual.getFailure().type()).isEqualTo(SlackInteractionFailureType.BUSINESS_INVARIANT),
                () -> assertThat(actual.getFailure().reason()).hasSize(500)
        );
        verify(slackInteractionInboxRepository).saveIfProcessingLeaseMatched(eq(actual), any(), eq(CLAIMED_PROCESSING_STARTED_AT));
    }

    @Test
    void 예외메시지가_비어있으면_unknown_failure로_저장된다() {
        // given
        SlackInteractionInbox actual = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "empty-failure-reason",
                "{}"
        );
        setProcessingState(actual, Instant.parse("2026-02-15T00:00:00Z"), 1);

        given(slackInteractionInboxRepository.findById(10L)).willReturn(Optional.of(actual));
        given(slackInteractionInboxRepository.saveIfProcessingLeaseMatched(eq(actual), any(), eq(CLAIMED_PROCESSING_STARTED_AT)))
                .willReturn(true);
        willThrow(new RuntimeException())
                .given(blockActionInteractionService)
                .handle(any());

        // when
        slackInteractionInboxEntryProcessor.processClaimedBlockAction(10L, CLAIMED_PROCESSING_STARTED_AT);

        // then
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(SlackInteractionInboxStatus.FAILED),
                () -> assertThat(actual.getFailure().type()).isEqualTo(SlackInteractionFailureType.BUSINESS_INVARIANT),
                () -> assertThat(actual.getFailure().reason()).isEqualTo("unknown failure")
        );
        verify(slackInteractionInboxRepository).saveIfProcessingLeaseMatched(eq(actual), any(), eq(CLAIMED_PROCESSING_STARTED_AT));
    }

    @Test
    void view_submission_정상처리시_handleEnqueued가_호출되고_PROCESSED로_저장된다() {
        // given
        SlackInteractionInbox actual = SlackInteractionInbox.pending(
                SlackInteractionInboxType.VIEW_SUBMISSION,
                "view-submission-success",
                "{\"type\":\"view_submission\"}"
        );
        setProcessingState(actual, Instant.parse("2026-02-15T00:00:00Z"), 1);

        given(slackInteractionInboxRepository.findById(20L)).willReturn(Optional.of(actual));
        given(slackInteractionInboxRepository.saveIfProcessingLeaseMatched(eq(actual), any(), eq(CLAIMED_PROCESSING_STARTED_AT)))
                .willReturn(true);

        // when
        slackInteractionInboxEntryProcessor.processClaimedViewSubmission(20L, CLAIMED_PROCESSING_STARTED_AT);

        // then
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSED),
                () -> assertThat(actual.getProcessingAttempt()).isEqualTo(1)
        );
        verify(viewSubmissionInteractionCoordinator).handleEnqueued(any());
        verify(blockActionInteractionService, never()).handle(any());
        verify(slackInteractionInboxRepository).saveIfProcessingLeaseMatched(eq(actual), any(), eq(CLAIMED_PROCESSING_STARTED_AT));
    }

    @Test
    void 최종_저장시_lease를_상실하면_예외를_던진다() {
        // given
        SlackInteractionInbox actual = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "lease-lost",
                "{}"
        );
        setProcessingState(actual, CLAIMED_PROCESSING_STARTED_AT, 1);

        given(slackInteractionInboxRepository.findById(30L)).willReturn(Optional.of(actual));
        given(slackInteractionInboxRepository.saveIfProcessingLeaseMatched(eq(actual), any(), eq(CLAIMED_PROCESSING_STARTED_AT)))
                .willReturn(false);

        // when & then
        assertThatThrownBy(() -> slackInteractionInboxEntryProcessor.processClaimedBlockAction(30L, CLAIMED_PROCESSING_STARTED_AT))
                .isInstanceOf(InboxProcessingLeaseLostException.class)
                .hasMessageContaining("processing lease");
    }

    private void setProcessingState(
            SlackInteractionInbox inbox,
            Instant processingStartedAt,
            int processingAttempt
    ) {
        inbox.claim(processingStartedAt);
        ReflectionTestUtils.setField(inbox, "processingAttempt", processingAttempt);
    }
}
