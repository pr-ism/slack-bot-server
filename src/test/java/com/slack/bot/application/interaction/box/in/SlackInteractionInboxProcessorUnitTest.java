package com.slack.bot.application.interaction.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import com.slack.bot.application.interaction.box.SlackInteractionIdempotencyKeyGenerator;
import com.slack.bot.application.interaction.box.SlackInteractionIdempotencyScope;
import com.slack.bot.application.worker.PollingHintPublisher;
import com.slack.bot.application.worker.PollingHintTarget;
import com.slack.bot.global.config.properties.InteractionRetryProperties;
import com.slack.bot.global.config.properties.InteractionWorkerProperties;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import com.slack.bot.infrastructure.interaction.box.in.repository.SlackInteractionInboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackInteractionInboxProcessorUnitTest {

    private static final int BLOCK_ACTION_RECOVERY_BATCH_SIZE = 41;
    private static final int VIEW_SUBMISSION_RECOVERY_BATCH_SIZE = 42;
    private static final Instant CLAIMED_PROCESSING_STARTED_AT = Instant.parse("2026-02-18T00:00:00Z");

    @Mock
    SlackInteractionInboxRepository slackInteractionInboxRepository;

    @Mock
    SlackInteractionIdempotencyKeyGenerator idempotencyKeyGenerator;

    @Mock
    SlackInteractionInboxIdempotencyPayloadEncoder idempotencyPayloadEncoder;

    @Mock
    SlackInteractionInboxEntryProcessor slackInteractionInboxEntryProcessor;

    @Mock
    PollingHintPublisher pollingHintPublisher;

    SlackInteractionInboxProcessor slackInteractionInboxProcessor;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(CLAIMED_PROCESSING_STARTED_AT, ZoneOffset.UTC);
        InteractionRetryProperties interactionRetryProperties = new InteractionRetryProperties(
                new InteractionRetryProperties.InboxRetryProperties(2, 100L, 2.0, 1_000L),
                new InteractionRetryProperties.OutboxRetryProperties(2, 100L, 2.0, 1_000L)
        );
        InteractionWorkerProperties interactionWorkerProperties = new InteractionWorkerProperties(
                new InteractionWorkerProperties.InboxProperties(
                        new InteractionWorkerProperties.BlockActionsProperties(
                                200L,
                                60000L,
                                30000L,
                                BLOCK_ACTION_RECOVERY_BATCH_SIZE
                        ),
                        new InteractionWorkerProperties.ViewSubmissionProperties(
                                200L,
                                60000L,
                                30000L,
                                VIEW_SUBMISSION_RECOVERY_BATCH_SIZE
                        )
                ),
                new InteractionWorkerProperties.OutboxProperties()
        );

        slackInteractionInboxProcessor = new SlackInteractionInboxProcessor(
                clock,
                interactionRetryProperties,
                interactionWorkerProperties,
                slackInteractionInboxRepository,
                idempotencyPayloadEncoder,
                idempotencyKeyGenerator,
                slackInteractionInboxEntryProcessor,
                pollingHintPublisher
        );
    }

    @Test
    void block_action_timeout_복구시_배치_크기를_전달하고_polling_hint를_발행한다() {
        // given
        given(slackInteractionInboxRepository.recoverTimeoutProcessing(
                eq(SlackInteractionInboxType.BLOCK_ACTIONS),
                any(),
                any(),
                any(),
                eq(2),
                eq(BLOCK_ACTION_RECOVERY_BATCH_SIZE)
        )).willReturn(3);

        // when
        slackInteractionInboxProcessor.recoverBlockActionTimeoutProcessing();

        // then
        verify(slackInteractionInboxRepository).recoverTimeoutProcessing(
                eq(SlackInteractionInboxType.BLOCK_ACTIONS),
                any(),
                any(),
                any(),
                eq(2),
                eq(BLOCK_ACTION_RECOVERY_BATCH_SIZE)
        );
        verify(pollingHintPublisher).publish(PollingHintTarget.BLOCK_ACTION_INBOX);
    }

    @Test
    void view_submission_timeout_복구시_배치_크기를_전달하고_polling_hint를_발행한다() {
        // given
        given(slackInteractionInboxRepository.recoverTimeoutProcessing(
                eq(SlackInteractionInboxType.VIEW_SUBMISSION),
                any(),
                any(),
                any(),
                eq(2),
                eq(VIEW_SUBMISSION_RECOVERY_BATCH_SIZE)
        )).willReturn(2);

        // when
        slackInteractionInboxProcessor.recoverViewSubmissionTimeoutProcessing();

        // then
        verify(slackInteractionInboxRepository).recoverTimeoutProcessing(
                eq(SlackInteractionInboxType.VIEW_SUBMISSION),
                any(),
                any(),
                any(),
                eq(2),
                eq(VIEW_SUBMISSION_RECOVERY_BATCH_SIZE)
        );
        verify(pollingHintPublisher).publish(PollingHintTarget.VIEW_SUBMISSION_INBOX);
    }

    @Test
    void view_submission_timeout_복구건이_없으면_polling_hint를_발행하지_않는다() {
        // given
        given(slackInteractionInboxRepository.recoverTimeoutProcessing(
                eq(SlackInteractionInboxType.VIEW_SUBMISSION),
                any(),
                any(),
                any(),
                eq(2),
                eq(VIEW_SUBMISSION_RECOVERY_BATCH_SIZE)
        )).willReturn(0);

        // when
        slackInteractionInboxProcessor.recoverViewSubmissionTimeoutProcessing();

        // then
        verify(pollingHintPublisher, never()).publish(PollingHintTarget.VIEW_SUBMISSION_INBOX);
    }

    @Test
    void block_actions_한건_처리중_예외가_나도_다음_엔트리를_계속_처리한다() {
        // given
        given(slackInteractionInboxRepository.claimNextId(
                eq(SlackInteractionInboxType.BLOCK_ACTIONS),
                any(),
                anyCollection()
        ))
                .willReturn(Optional.of(1L), Optional.of(2L), Optional.of(3L));
        willThrow(new RuntimeException("db failure"))
                .given(slackInteractionInboxEntryProcessor)
                .processClaimedBlockAction(1L, CLAIMED_PROCESSING_STARTED_AT);

        InOrder inOrder = inOrder(slackInteractionInboxEntryProcessor);

        // when & then
        assertThatCode(() -> slackInteractionInboxProcessor.processPendingBlockActions(3))
                .doesNotThrowAnyException();
        inOrder.verify(slackInteractionInboxEntryProcessor).processClaimedBlockAction(1L, CLAIMED_PROCESSING_STARTED_AT);
        inOrder.verify(slackInteractionInboxEntryProcessor).processClaimedBlockAction(2L, CLAIMED_PROCESSING_STARTED_AT);
        inOrder.verify(slackInteractionInboxEntryProcessor).processClaimedBlockAction(3L, CLAIMED_PROCESSING_STARTED_AT);
    }

    @Test
    void enqueueBlockAction_시_인코더가_호출되어_idempotency_key를_생성한다() {
        // given
        String payloadJson = "{\"actions\":[]}";
        String encodedPayload = "{\"encoded\":true}";
        given(idempotencyPayloadEncoder.encodeBlockAction(payloadJson))
                .willReturn(encodedPayload);
        given(idempotencyKeyGenerator.generate(SlackInteractionIdempotencyScope.BLOCK_ACTIONS, encodedPayload))
                .willReturn("idempotent-key-123");
        given(slackInteractionInboxRepository.enqueue(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "idempotent-key-123",
                payloadJson
        )).willReturn(true);

        // when
        boolean actual = slackInteractionInboxProcessor.enqueueBlockAction(payloadJson);

        // then
        assertThat(actual).isTrue();
        verify(idempotencyPayloadEncoder).encodeBlockAction(payloadJson);
        verify(idempotencyKeyGenerator)
                        .generate(SlackInteractionIdempotencyScope.BLOCK_ACTIONS, encodedPayload);
        verify(slackInteractionInboxRepository).enqueue(
                        SlackInteractionInboxType.BLOCK_ACTIONS,
                        "idempotent-key-123",
                        payloadJson
                );
    }

    @Test
    void view_submission_한건_처리중_예외가_나도_다음_엔트리를_계속_처리한다() {
        // given
        given(slackInteractionInboxRepository.claimNextId(
                eq(SlackInteractionInboxType.VIEW_SUBMISSION),
                any(),
                anyCollection()
        ))
                .willReturn(Optional.of(11L), Optional.of(12L));
        willThrow(new RuntimeException("db failure"))
                .given(slackInteractionInboxEntryProcessor)
                .processClaimedViewSubmission(11L, CLAIMED_PROCESSING_STARTED_AT);

        InOrder inOrder = inOrder(slackInteractionInboxEntryProcessor);

        // when & then
        assertThatCode(() -> slackInteractionInboxProcessor.processPendingViewSubmissions(2))
                .doesNotThrowAnyException();
        inOrder.verify(slackInteractionInboxEntryProcessor).processClaimedViewSubmission(11L, CLAIMED_PROCESSING_STARTED_AT);
        inOrder.verify(slackInteractionInboxEntryProcessor).processClaimedViewSubmission(12L, CLAIMED_PROCESSING_STARTED_AT);
    }

    @Test
    void enqueueViewSubmission_시_인코더가_호출되어_idempotency_key를_생성한다() {
        // given
        String payloadJson = "{\"view\":{\"id\":\"V123\"}}";
        String encodedPayload = "{\"encoded\":true}";
        given(idempotencyPayloadEncoder.encodeViewSubmission(payloadJson))
                .willReturn(encodedPayload);
        given(idempotencyKeyGenerator.generate(SlackInteractionIdempotencyScope.VIEW_SUBMISSION, encodedPayload))
                .willReturn("idempotent-key-456");
        given(slackInteractionInboxRepository.enqueue(
                SlackInteractionInboxType.VIEW_SUBMISSION,
                "idempotent-key-456",
                payloadJson
        )).willReturn(true);

        // when
        boolean actual = slackInteractionInboxProcessor.enqueueViewSubmission(payloadJson);

        // then
        assertThat(actual).isTrue();
        verify(idempotencyPayloadEncoder).encodeViewSubmission(payloadJson);
        verify(idempotencyKeyGenerator)
                        .generate(SlackInteractionIdempotencyScope.VIEW_SUBMISSION, encodedPayload);
        verify(slackInteractionInboxRepository).enqueue(
                        SlackInteractionInboxType.VIEW_SUBMISSION,
                        "idempotent-key-456",
                        payloadJson
                );
    }
}
