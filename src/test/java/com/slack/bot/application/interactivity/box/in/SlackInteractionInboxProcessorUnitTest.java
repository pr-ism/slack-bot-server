package com.slack.bot.application.interactivity.box.in;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.slack.bot.application.interactivity.box.SlackInteractionIdempotencyKeyGenerator;
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
    SlackInteractionInboxEntryProcessor slackInteractionInboxEntryProcessor;

    SlackInteractionInboxProcessor slackInteractionInboxProcessor;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-02-18T00:00:00Z"), ZoneOffset.UTC);
        InteractionWorkerProperties interactionWorkerProperties = new InteractionWorkerProperties(
                new InteractionWorkerProperties.Inbox(
                        new InteractionWorkerProperties.BlockActions(true, 200L, 60000L),
                        new InteractionWorkerProperties.ViewSubmission(true, 200L, 60000L)
                ),
                new InteractionWorkerProperties.Outbox()
        );

        slackInteractionInboxProcessor = new SlackInteractionInboxProcessor(
                clock,
                interactionWorkerProperties,
                slackInteractionInboxRepository,
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

        // when & then
        assertThatCode(() -> slackInteractionInboxProcessor.processPendingBlockActions(3))
                .doesNotThrowAnyException();

        InOrder inOrder = inOrder(slackInteractionInboxEntryProcessor);
        inOrder.verify(slackInteractionInboxEntryProcessor).processBlockAction(first);
        inOrder.verify(slackInteractionInboxEntryProcessor).processBlockAction(second);
        inOrder.verify(slackInteractionInboxEntryProcessor).processBlockAction(third);

        verify(slackInteractionInboxRepository).findClaimable(SlackInteractionInboxType.BLOCK_ACTIONS, 3);
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

        // when & then
        assertThatCode(() -> slackInteractionInboxProcessor.processPendingViewSubmissions(2))
                .doesNotThrowAnyException();

        InOrder inOrder = inOrder(slackInteractionInboxEntryProcessor);
        inOrder.verify(slackInteractionInboxEntryProcessor).processViewSubmission(first);
        inOrder.verify(slackInteractionInboxEntryProcessor).processViewSubmission(second);

        verify(slackInteractionInboxRepository).findClaimable(SlackInteractionInboxType.VIEW_SUBMISSION, 2);
    }
}
