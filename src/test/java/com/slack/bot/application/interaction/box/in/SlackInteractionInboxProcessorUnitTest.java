package com.slack.bot.application.interaction.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.slack.bot.application.interaction.box.SlackInteractionIdempotencyKeyGenerator;
import com.slack.bot.application.interaction.box.SlackInteractionIdempotencyScope;
import com.slack.bot.global.config.properties.InteractionRetryProperties;
import com.slack.bot.global.config.properties.InteractionWorkerProperties;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import com.slack.bot.infrastructure.interaction.box.in.repository.SlackInteractionInboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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

    @Mock
    SlackInteractionInboxRepository slackInteractionInboxRepository;

    @Mock
    SlackInteractionIdempotencyKeyGenerator idempotencyKeyGenerator;

    @Mock
    SlackInteractionInboxIdempotencyPayloadEncoder idempotencyPayloadEncoder;

    @Mock
    SlackInteractionInboxEntryProcessor slackInteractionInboxEntryProcessor;

    SlackInteractionInboxProcessor slackInteractionInboxProcessor;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-02-18T00:00:00Z"), ZoneOffset.UTC);
        InteractionRetryProperties interactionRetryProperties = new InteractionRetryProperties(
                new InteractionRetryProperties.InboxRetryProperties(2, 100L, 2.0, 1_000L),
                new InteractionRetryProperties.OutboxRetryProperties(2, 100L, 2.0, 1_000L)
        );
        InteractionWorkerProperties interactionWorkerProperties = new InteractionWorkerProperties(
                new InteractionWorkerProperties.InboxProperties(
                        new InteractionWorkerProperties.BlockActionsProperties(true, 200L, 60000L),
                        new InteractionWorkerProperties.ViewSubmissionProperties(true, 200L, 60000L)
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
                slackInteractionInboxEntryProcessor
        );
    }

    @Test
    void block_actions_한건_처리중_예외가_나도_다음_엔트리를_계속_처리한다() {
        // given
        SlackInteractionInbox first = mock(SlackInteractionInbox.class);
        SlackInteractionInbox second = mock(SlackInteractionInbox.class);
        SlackInteractionInbox third = mock(SlackInteractionInbox.class);
        given(first.getId()).willReturn(1L);
        given(slackInteractionInboxRepository.findClaimable(SlackInteractionInboxType.BLOCK_ACTIONS, 3))
                .willReturn(List.of(first, second, third));
        willThrow(new RuntimeException("db failure"))
                .given(slackInteractionInboxEntryProcessor)
                .processBlockAction(first);

        InOrder inOrder = inOrder(slackInteractionInboxEntryProcessor);

        // when & then
        assertThatCode(() -> slackInteractionInboxProcessor.processPendingBlockActions(3))
                .doesNotThrowAnyException();
        inOrder.verify(slackInteractionInboxEntryProcessor).processBlockAction(first);
        inOrder.verify(slackInteractionInboxEntryProcessor).processBlockAction(second);
        inOrder.verify(slackInteractionInboxEntryProcessor).processBlockAction(third);
        verify(slackInteractionInboxRepository).findClaimable(SlackInteractionInboxType.BLOCK_ACTIONS, 3);
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
        SlackInteractionInbox first = mock(SlackInteractionInbox.class);
        SlackInteractionInbox second = mock(SlackInteractionInbox.class);
        given(first.getId()).willReturn(11L);
        given(slackInteractionInboxRepository.findClaimable(SlackInteractionInboxType.VIEW_SUBMISSION, 2))
                .willReturn(List.of(first, second));
        willThrow(new RuntimeException("db failure"))
                .given(slackInteractionInboxEntryProcessor)
                .processViewSubmission(first);

        InOrder inOrder = inOrder(slackInteractionInboxEntryProcessor);

        // when & then
        assertThatCode(() -> slackInteractionInboxProcessor.processPendingViewSubmissions(2))
                .doesNotThrowAnyException();
        inOrder.verify(slackInteractionInboxEntryProcessor).processViewSubmission(first);
        inOrder.verify(slackInteractionInboxEntryProcessor).processViewSubmission(second);
        verify(slackInteractionInboxRepository).findClaimable(SlackInteractionInboxType.VIEW_SUBMISSION, 2);
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
