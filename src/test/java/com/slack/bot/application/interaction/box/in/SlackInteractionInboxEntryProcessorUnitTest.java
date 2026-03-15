package com.slack.bot.application.interaction.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interaction.BlockActionInteractionService;
import com.slack.bot.application.interaction.box.BoxFailureReasonTruncator;
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
import org.springframework.web.client.ResourceAccessException;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackInteractionInboxEntryProcessorUnitTest {

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
                new InteractionRetryProperties.Retry(2, 100, 2.0, 1000),
                new InteractionRetryProperties.Retry(2, 100, 2.0, 1000)
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
    void markProcessing_선점에_실패하면_처리를_건너뛴다() {
        // given
        SlackInteractionInbox pending = org.mockito.Mockito.mock(SlackInteractionInbox.class);
        given(pending.getId()).willReturn(10L);
        given(slackInteractionInboxRepository.markProcessingIfClaimable(eq(10L), any())).willReturn(false);

        // when
        slackInteractionInboxEntryProcessor.processBlockAction(pending);

        // then
        verify(slackInteractionInboxRepository, never()).findById(anyLong());
        verify(slackInteractionInboxRepository, never()).save(any());
        verify(blockActionInteractionService, never()).handle(any());
    }

    @Test
    void markProcessing_성공후_inbox_재조회에_실패하면_추가_처리없이_종료한다() {
        // given
        SlackInteractionInbox pending = org.mockito.Mockito.mock(SlackInteractionInbox.class);
        given(pending.getId()).willReturn(10L);
        given(slackInteractionInboxRepository.markProcessingIfClaimable(eq(10L), any())).willReturn(true);
        given(slackInteractionInboxRepository.findById(10L)).willReturn(Optional.empty());

        // when
        slackInteractionInboxEntryProcessor.processBlockAction(pending);

        // then
        verify(slackInteractionInboxRepository, never()).save(any());
        verify(blockActionInteractionService, never()).handle(any());
    }

    @Test
    void block_action_정상처리시_handle이_호출되고_PROCESSED로_저장된다() {
        // given
        SlackInteractionInbox pending = org.mockito.Mockito.mock(SlackInteractionInbox.class);
        given(pending.getId()).willReturn(11L);

        SlackInteractionInbox actual = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "block-action-success",
                "{}"
        );
        actual.markProcessing(Instant.parse("2026-02-15T00:00:00Z"));

        given(slackInteractionInboxRepository.markProcessingIfClaimable(eq(11L), any())).willReturn(true);
        given(slackInteractionInboxRepository.findById(11L)).willReturn(Optional.of(actual));

        // when
        slackInteractionInboxEntryProcessor.processBlockAction(pending);

        // then
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSED),
                () -> assertThat(actual.getProcessingAttempt()).isEqualTo(1)
        );
        verify(blockActionInteractionService).handle(any());
        verify(viewSubmissionInteractionCoordinator, never()).handleEnqueued(any());
        verify(slackInteractionInboxRepository).save(actual);
    }

    @Test
    void 재시도_가능_예외이고_첫_시도면_RETRY_PENDING으로_마킹된다() {
        // given
        SlackInteractionInbox pending = org.mockito.Mockito.mock(SlackInteractionInbox.class);
        given(pending.getId()).willReturn(10L);

        SlackInteractionInbox actual = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "retry-first-attempt",
                "{}"
        );
        actual.markProcessing(Instant.parse("2026-02-15T00:00:00Z"));

        given(slackInteractionInboxRepository.markProcessingIfClaimable(eq(10L), any())).willReturn(true);
        given(slackInteractionInboxRepository.findById(10L)).willReturn(Optional.of(actual));
        willThrow(new ResourceAccessException("temporary network failure"))
                .given(blockActionInteractionService)
                .handle(any());

        // when
        slackInteractionInboxEntryProcessor.processBlockAction(pending);

        // then
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(SlackInteractionInboxStatus.RETRY_PENDING),
                () -> assertThat(actual.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(actual.getFailureType()).isNull()
        );
        verify(slackInteractionInboxRepository).save(actual);
    }

    @Test
    void 재시도_가능_예외이고_최대_시도에_도달하면_FAILED와_RETRY_EXHAUSTED로_마킹된다() {
        // given
        SlackInteractionInbox pending = org.mockito.Mockito.mock(SlackInteractionInbox.class);
        given(pending.getId()).willReturn(10L);

        SlackInteractionInbox actual = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "retry-max-attempt",
                "{}"
        );
        actual.markProcessing(Instant.parse("2026-02-15T00:00:00Z"));
        actual.markRetryPending(Instant.parse("2026-02-15T00:01:00Z"), "previous retry failure");
        actual.markProcessing(Instant.parse("2026-02-15T00:02:00Z"));

        given(slackInteractionInboxRepository.markProcessingIfClaimable(eq(10L), any())).willReturn(true);
        given(slackInteractionInboxRepository.findById(10L)).willReturn(Optional.of(actual));
        willThrow(new ResourceAccessException("temporary network failure"))
                .given(blockActionInteractionService)
                .handle(any());

        // when
        slackInteractionInboxEntryProcessor.processBlockAction(pending);

        // then
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(SlackInteractionInboxStatus.FAILED),
                () -> assertThat(actual.getProcessingAttempt()).isEqualTo(2),
                () -> assertThat(actual.getFailureType()).isEqualTo(SlackInteractionFailureType.RETRY_EXHAUSTED)
        );
        verify(slackInteractionInboxRepository).save(actual);
    }

    @Test
    void 긴_실패사유는_잘려서_저장된다() {
        // given
        SlackInteractionInbox pending = org.mockito.Mockito.mock(SlackInteractionInbox.class);
        given(pending.getId()).willReturn(10L);

        SlackInteractionInbox actual = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "long-failure-reason",
                "{}"
        );
        actual.markProcessing(Instant.parse("2026-02-15T00:00:00Z"));

        given(slackInteractionInboxRepository.markProcessingIfClaimable(eq(10L), any())).willReturn(true);
        given(slackInteractionInboxRepository.findById(10L)).willReturn(Optional.of(actual));
        willThrow(new IllegalArgumentException("x".repeat(600)))
                .given(blockActionInteractionService)
                .handle(any());

        // when
        slackInteractionInboxEntryProcessor.processBlockAction(pending);

        // then
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(SlackInteractionInboxStatus.FAILED),
                () -> assertThat(actual.getFailureType()).isEqualTo(SlackInteractionFailureType.BUSINESS_INVARIANT),
                () -> assertThat(actual.getFailureReason()).hasSize(500)
        );
        verify(slackInteractionInboxRepository).save(actual);
    }

    @Test
    void 예외메시지가_비어있으면_unknown_failure로_저장된다() {
        // given
        SlackInteractionInbox pending = org.mockito.Mockito.mock(SlackInteractionInbox.class);
        given(pending.getId()).willReturn(10L);

        SlackInteractionInbox actual = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "empty-failure-reason",
                "{}"
        );
        actual.markProcessing(Instant.parse("2026-02-15T00:00:00Z"));

        given(slackInteractionInboxRepository.markProcessingIfClaimable(eq(10L), any())).willReturn(true);
        given(slackInteractionInboxRepository.findById(10L)).willReturn(Optional.of(actual));
        willThrow(new RuntimeException())
                .given(blockActionInteractionService)
                .handle(any());

        // when
        slackInteractionInboxEntryProcessor.processBlockAction(pending);

        // then
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(SlackInteractionInboxStatus.FAILED),
                () -> assertThat(actual.getFailureType()).isEqualTo(SlackInteractionFailureType.BUSINESS_INVARIANT),
                () -> assertThat(actual.getFailureReason()).isEqualTo("unknown failure")
        );
        verify(slackInteractionInboxRepository).save(actual);
    }

    @Test
    void view_submission_정상처리시_handleEnqueued가_호출되고_PROCESSED로_저장된다() {
        // given
        SlackInteractionInbox pending = org.mockito.Mockito.mock(SlackInteractionInbox.class);
        given(pending.getId()).willReturn(20L);

        SlackInteractionInbox actual = SlackInteractionInbox.pending(
                SlackInteractionInboxType.VIEW_SUBMISSION,
                "view-submission-success",
                "{\"type\":\"view_submission\"}"
        );
        actual.markProcessing(Instant.parse("2026-02-15T00:00:00Z"));

        given(slackInteractionInboxRepository.markProcessingIfClaimable(eq(20L), any())).willReturn(true);
        given(slackInteractionInboxRepository.findById(20L)).willReturn(Optional.of(actual));

        // when
        slackInteractionInboxEntryProcessor.processViewSubmission(pending);

        // then
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSED),
                () -> assertThat(actual.getProcessingAttempt()).isEqualTo(1)
        );
        verify(viewSubmissionInteractionCoordinator).handleEnqueued(any());
        verify(blockActionInteractionService, never()).handle(any());
        verify(slackInteractionInboxRepository).save(actual);
    }
}
