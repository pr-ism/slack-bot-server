package com.slack.bot.application.review.box.out;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interaction.box.BoxFailureReasonTruncator;
import com.slack.bot.application.interaction.box.retry.EqualJitterExponentialBackOffPolicy;
import com.slack.bot.application.interaction.box.retry.InteractionRetryExceptionClassifier;
import com.slack.bot.application.review.dto.ReviewMessageDto;
import com.slack.bot.application.worker.PollingHintPublisher;
import com.slack.bot.application.worker.PollingHintTarget;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import com.slack.bot.global.config.properties.InteractionRetryProperties;
import com.slack.bot.global.config.properties.ReviewWorkerProperties;
import com.slack.bot.infrastructure.common.FailureSnapshotDefaults;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.client.NotificationTransportApiClient;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxStringField;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxStatus;
import com.slack.bot.infrastructure.review.box.out.repository.ReviewNotificationOutboxRepository;
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
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewNotificationOutboxProcessorTest {

    private static final Instant CLAIMED_PROCESSING_STARTED_AT = Instant.parse("2026-02-24T00:00:00Z");
    private static final int OUTBOX_RECOVERY_BATCH_SIZE = 55;

    @Mock
    WorkspaceRepository workspaceRepository;

    @Mock
    NotificationTransportApiClient notificationTransportApiClient;

    @Mock
    ReviewNotificationOutboxRepository reviewNotificationOutboxRepository;

    @Mock
    ReviewNotificationMessageRenderer reviewNotificationMessageRenderer;

    @Mock
    PollingHintPublisher pollingHintPublisher;

    ReviewNotificationOutboxProcessor processor;

    InteractionRetryExceptionClassifier classifier;

    @BeforeEach
    void setUp() {
        InteractionRetryProperties retryProperties = new InteractionRetryProperties(
                new InteractionRetryProperties.InboxRetryProperties(2, 100, 2.0, 1_000),
                new InteractionRetryProperties.OutboxRetryProperties(2, 100, 2.0, 1_000)
        );
        classifier = InteractionRetryExceptionClassifier.create();

        Clock fixedClock = Clock.fixed(CLAIMED_PROCESSING_STARTED_AT, ZoneOffset.UTC);

        processor = new ReviewNotificationOutboxProcessor(
                fixedClock,
                new ObjectMapper(),
                reviewNotificationMessageRenderer,
                createOutboxRetryTemplate(retryProperties, classifier),
                workspaceRepository,
                new BoxFailureReasonTruncator(),
                retryProperties,
                new ReviewWorkerProperties(
                        new ReviewWorkerProperties.InboxProperties(),
                        new ReviewWorkerProperties.OutboxProperties(
                                1_000L,
                                30_000L,
                                50,
                                60_000L,
                                OUTBOX_RECOVERY_BATCH_SIZE
                        )
                ),
                pollingHintPublisher,
                notificationTransportApiClient,
                reviewNotificationOutboxRepository,
                classifier
        );

        lenient().when(reviewNotificationOutboxRepository.renewProcessingLease(any(), any(), any())).thenReturn(true);
    }

    private RetryTemplate createOutboxRetryTemplate(
            InteractionRetryProperties retryProperties,
            InteractionRetryExceptionClassifier exceptionClassifier
    ) {
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(
                retryProperties.outbox().maxAttempts(),
                exceptionClassifier.getRetryableExceptions(),
                true,
                false
        ));

        EqualJitterExponentialBackOffPolicy backOffPolicy = new EqualJitterExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(retryProperties.outbox().initialDelayMs());
        backOffPolicy.setMultiplier(retryProperties.outbox().multiplier());
        backOffPolicy.setMaxInterval(retryProperties.outbox().maxDelayMs());
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }

    @Test
    void processingTimeout이_0이하면_예외가_발생한다() {
        assertThatThrownBy(() -> processor.recoverTimeoutProcessing(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("processingTimeoutMs");
    }

    @Test
    void processingTimeout이_음수이면_예외가_발생한다() {
        assertThatThrownBy(() -> processor.recoverTimeoutProcessing(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("processingTimeoutMs");
    }

    @Test
    void processing_timeout_복구시_batch_size와_maxAttempts를_전달하고_polling_hint를_발행한다() {
        // given
        given(reviewNotificationOutboxRepository.recoverTimeoutProcessing(
                any(),
                any(),
                anyString(),
                eq(2),
                eq(OUTBOX_RECOVERY_BATCH_SIZE)
        )).willReturn(2);

        // when
        processor.recoverTimeoutProcessing(60_000L);

        // then
        verify(reviewNotificationOutboxRepository).recoverTimeoutProcessing(
                any(),
                any(),
                anyString(),
                eq(2),
                eq(OUTBOX_RECOVERY_BATCH_SIZE)
        );
        verify(pollingHintPublisher).publish(PollingHintTarget.REVIEW_NOTIFICATION_OUTBOX);
    }

    @Test
    void processing_timeout_복구건이_없으면_polling_hint를_발행하지_않는다() {
        // given
        given(reviewNotificationOutboxRepository.recoverTimeoutProcessing(
                any(),
                any(),
                anyString(),
                eq(2),
                eq(OUTBOX_RECOVERY_BATCH_SIZE)
        )).willReturn(0);

        // when
        processor.recoverTimeoutProcessing(60_000L);

        // then
        verify(pollingHintPublisher, never()).publish(PollingHintTarget.REVIEW_NOTIFICATION_OUTBOX);
    }

    @Test
    void 선점에_실패하면_처리를_진행하지_않는다() {
        // given
        given(reviewNotificationOutboxRepository.claimNextId(any(), anyCollection()))
                .willReturn(Optional.empty());

        // when
        processor.processPending(10);

        // then
        verify(reviewNotificationOutboxRepository, never()).findById(anyLong());
        verify(reviewNotificationOutboxRepository, never()).saveIfProcessingLeaseMatched(any(), any(), any());
    }

    @Test
    void 선점_성공후_재조회에_실패하면_저장없이_종료한다() {
        // given
        given(reviewNotificationOutboxRepository.claimNextId(any(), anyCollection()))
                .willReturn(Optional.of(10L), Optional.empty());
        given(reviewNotificationOutboxRepository.findById(10L)).willReturn(Optional.empty());

        // when
        processor.processPending(10);

        // then
        verify(reviewNotificationOutboxRepository, never()).saveIfProcessingLeaseMatched(any(), any(), any());
    }

    @Test
    void claim_lease가_달라지면_전송을_건너뛴다() {
        // given
        ReviewNotificationOutbox claimed = spyClaimed("C1", 1, null);
        ReflectionTestUtils.setField(
                claimed,
                "processingStartedAt",
                Instant.parse("2026-02-24T00:00:01Z")
        );
        given(reviewNotificationOutboxRepository.claimNextId(any(), anyCollection()))
                .willReturn(Optional.of(10L), Optional.empty());
        given(reviewNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(claimed));

        // when
        processor.processPending(10);

        // then
        verify(notificationTransportApiClient, never())
                .sendBlockMessage(anyString(), anyString(), any(), any(), anyString());
        verify(reviewNotificationOutboxRepository, never()).saveIfProcessingLeaseMatched(any(), any(), any());
    }

    @Test
    void lease_연장에_실패하면_전송을_건너뛴다() {
        // given
        ReviewNotificationOutbox claimed = spyClaimed("C1", 1, null);
        given(reviewNotificationOutboxRepository.claimNextId(any(), anyCollection()))
                .willReturn(Optional.of(10L), Optional.empty());
        given(reviewNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(claimed));
        given(reviewNotificationOutboxRepository.renewProcessingLease(any(), any(), any())).willReturn(false);

        // when
        processor.processPending(10);

        // then
        verify(notificationTransportApiClient, never())
                .sendBlockMessage(anyString(), anyString(), any(), any(), anyString());
        verify(claimed, never()).markSent(any());
        verify(claimed, never()).markFailed(any(), anyString(), any());
        verify(reviewNotificationOutboxRepository, never()).saveIfProcessingLeaseMatched(any(), any(), any());
    }

    @Test
    void 정상_처리되면_메시지를_전송하고_SENT로_저장한다() {
        // given
        ReviewNotificationOutbox claimed = spyClaimed("C1", 1, null);
        given(reviewNotificationOutboxRepository.claimNextId(any(), anyCollection()))
                .willReturn(Optional.of(10L), Optional.empty());
        given(reviewNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(claimed));

        Workspace workspace = mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));

        // when
        processor.processPending(10);

        // then
        verify(notificationTransportApiClient).sendBlockMessage(
                eq("xoxb-test-token"),
                eq("C1"),
                any(JsonNode.class),
                argThat(attachments -> attachments != null && attachments.isNull()),
                eq("fallback")
        );
        verify(claimed).markSent(any());
        verify(reviewNotificationOutboxRepository)
                .saveIfProcessingLeaseMatched(eq(claimed), any(), eq(CLAIMED_PROCESSING_STARTED_AT));
    }

    @Test
    void 전송_성공후_SENT_저장_실패는_실패상태로_재마킹하지_않는다() {
        // given
        ReviewNotificationOutbox claimed = spyClaimed("C1", 1, null);
        given(reviewNotificationOutboxRepository.claimNextId(any(), anyCollection()))
                .willReturn(Optional.of(10L), Optional.empty());
        given(reviewNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(claimed));

        Workspace workspace = mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));

        doThrow(new RuntimeException("db failure"))
                .when(reviewNotificationOutboxRepository)
                .saveIfProcessingLeaseMatched(eq(claimed), any(), eq(CLAIMED_PROCESSING_STARTED_AT));

        // when
        processor.processPending(10);

        // then
        verify(notificationTransportApiClient).sendBlockMessage(
                eq("xoxb-test-token"),
                eq("C1"),
                any(JsonNode.class),
                argThat(attachments -> attachments != null && attachments.isNull()),
                eq("fallback")
        );
        verify(claimed).markSent(any());
        verify(claimed, never()).markRetryPending(any(), anyString());
        verify(claimed, never()).markFailed(any(), anyString(), any());
    }

    @Test
    void 비재시도_예외면_FAILED_BUSINESS_INVARIANT로_저장한다() {
        // given
        ReviewNotificationOutbox claimed = spyClaimed("C1", 1, null);
        given(reviewNotificationOutboxRepository.claimNextId(any(), anyCollection()))
                .willReturn(Optional.of(10L), Optional.empty());
        given(reviewNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(claimed));

        Workspace workspace = mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));

        RuntimeException exception = new RuntimeException("business failure");
        doThrow(exception)
                .when(notificationTransportApiClient)
                .sendBlockMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        any(JsonNode.class),
                        argThat(attachments -> attachments != null && attachments.isNull()),
                        eq("fallback")
                );

        // when
        processor.processPending(10);

        // then
        verify(claimed).markFailed(any(), anyString(), eq(SlackInteractionFailureType.BUSINESS_INVARIANT));
        verify(reviewNotificationOutboxRepository)
                .saveIfProcessingLeaseMatched(eq(claimed), any(), eq(CLAIMED_PROCESSING_STARTED_AT));
    }

    @Test
    void 실패상태_저장에_실패해도_다음_outbox_처리를_계속한다() {
        // given
        ReviewNotificationOutbox firstClaimed = spyClaimed("C1", 1, null);
        ReviewNotificationOutbox secondClaimed = spyClaimed("C2", 1, null);

        given(reviewNotificationOutboxRepository.claimNextId(any(), anyCollection()))
                .willReturn(Optional.of(10L), Optional.of(20L), Optional.empty());
        given(reviewNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(firstClaimed));
        given(reviewNotificationOutboxRepository.findById(20L)).willReturn(Optional.of(secondClaimed));

        Workspace workspace = mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));

        doThrow(new ResourceAccessException("io failure"))
                .when(notificationTransportApiClient)
                .sendBlockMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        any(JsonNode.class),
                        argThat(attachments -> attachments != null && attachments.isNull()),
                        eq("fallback")
                );
        doThrow(new RuntimeException("db failure"))
                .when(reviewNotificationOutboxRepository)
                .saveIfProcessingLeaseMatched(eq(firstClaimed), any(), eq(CLAIMED_PROCESSING_STARTED_AT));

        // when & then
        assertThatCode(() -> processor.processPending(10)).doesNotThrowAnyException();

        verify(firstClaimed).markRetryPending(any(), anyString());
        verify(secondClaimed).markSent(any());
        verify(reviewNotificationOutboxRepository)
                .saveIfProcessingLeaseMatched(eq(secondClaimed), any(), eq(CLAIMED_PROCESSING_STARTED_AT));
        verify(reviewNotificationOutboxRepository, times(2))
                .saveIfProcessingLeaseMatched(any(ReviewNotificationOutbox.class), any(), eq(CLAIMED_PROCESSING_STARTED_AT));
    }

    @Test
    void 예상치못한_예외가_발생해도_다음_outbox_처리를_계속한다() {
        // given
        ReviewNotificationOutbox firstClaimed = spyClaimed("C1", 1, null);
        ReviewNotificationOutbox secondClaimed = spyClaimed("C2", 1, null);

        given(reviewNotificationOutboxRepository.claimNextId(any(), anyCollection()))
                .willReturn(Optional.of(10L), Optional.of(20L), Optional.empty());
        given(reviewNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(firstClaimed));
        given(reviewNotificationOutboxRepository.findById(20L)).willReturn(Optional.of(secondClaimed));

        Workspace workspace = mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));

        doThrow(new IllegalStateException("invalid status"))
                .when(firstClaimed)
                .markSent(any());

        // when & then
        assertThatCode(() -> processor.processPending(10)).doesNotThrowAnyException();

        verify(notificationTransportApiClient).sendBlockMessage(
                eq("xoxb-test-token"),
                eq("C1"),
                any(JsonNode.class),
                argThat(attachments -> attachments != null && attachments.isNull()),
                eq("fallback")
        );
        verify(notificationTransportApiClient).sendBlockMessage(
                eq("xoxb-test-token"),
                eq("C2"),
                any(JsonNode.class),
                argThat(attachments -> attachments != null && attachments.isNull()),
                eq("fallback")
        );
        verify(reviewNotificationOutboxRepository, never()).save(firstClaimed);
        verify(reviewNotificationOutboxRepository, never())
                .saveIfProcessingLeaseMatched(eq(firstClaimed), any(), eq(CLAIMED_PROCESSING_STARTED_AT));
        verify(secondClaimed).markSent(any());
        verify(reviewNotificationOutboxRepository)
                .saveIfProcessingLeaseMatched(eq(secondClaimed), any(), eq(CLAIMED_PROCESSING_STARTED_AT));
    }

    @Test
    void 재시도_가능_예외이고_시도횟수가_남아있으면_RETRY_PENDING으로_저장한다() {
        // given
        ReviewNotificationOutbox claimed = spyClaimed("C1", 1, null);
        given(reviewNotificationOutboxRepository.claimNextId(any(), anyCollection()))
                .willReturn(Optional.of(10L), Optional.empty());
        given(reviewNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(claimed));

        Workspace workspace = mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));

        RuntimeException exception = new ResourceAccessException("io failure");
        doThrow(exception)
                .when(notificationTransportApiClient)
                .sendBlockMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        any(JsonNode.class),
                        argThat(attachments -> attachments != null && attachments.isNull()),
                        eq("fallback")
                );

        // when
        processor.processPending(10);

        // then
        verify(claimed).markRetryPending(any(), anyString());
        verify(reviewNotificationOutboxRepository)
                .saveIfProcessingLeaseMatched(eq(claimed), any(), eq(CLAIMED_PROCESSING_STARTED_AT));
    }

    @Test
    void 재시도_가능_예외이고_최대시도_도달이면_FAILED_RETRY_EXHAUSTED로_저장한다() {
        // given
        ReviewNotificationOutbox claimed = spyClaimed("C1", 2, null);
        given(reviewNotificationOutboxRepository.claimNextId(any(), anyCollection()))
                .willReturn(Optional.of(10L), Optional.empty());
        given(reviewNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(claimed));

        Workspace workspace = mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));

        RuntimeException exception = new ResourceAccessException("io failure");
        doThrow(exception)
                .when(notificationTransportApiClient)
                .sendBlockMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        any(JsonNode.class),
                        argThat(attachments -> attachments != null && attachments.isNull()),
                        eq("fallback")
                );

        // when
        processor.processPending(10);

        // then
        verify(claimed).markFailed(any(), anyString(), eq(SlackInteractionFailureType.RETRY_EXHAUSTED));
        verify(reviewNotificationOutboxRepository)
                .saveIfProcessingLeaseMatched(eq(claimed), any(), eq(CLAIMED_PROCESSING_STARTED_AT));
    }

    @Test
    void 팀에_해당하는_workspace가_없으면_FAILED_BUSINESS_INVARIANT로_저장한다() {
        // given
        ReviewNotificationOutbox claimed = spyClaimed("C1", 1, null);
        given(reviewNotificationOutboxRepository.claimNextId(any(), anyCollection()))
                .willReturn(Optional.of(10L), Optional.empty());
        given(reviewNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(claimed));
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.empty());

        // when
        processor.processPending(10);

        // then
        verify(claimed).markFailed(any(), anyString(), eq(SlackInteractionFailureType.BUSINESS_INVARIANT));
        verify(reviewNotificationOutboxRepository)
                .saveIfProcessingLeaseMatched(eq(claimed), any(), eq(CLAIMED_PROCESSING_STARTED_AT));
    }

    @Test
    void attachments_json이_있으면_attachment를_보존해_전송한다() {
        // given
        ReviewNotificationOutbox claimed = spyClaimed(
                "C1",
                1,
                "[{\"color\":\"#6366F1\",\"blocks\":[{\"type\":\"actions\"}]}]"
        );
        given(reviewNotificationOutboxRepository.claimNextId(any(), anyCollection()))
                .willReturn(Optional.of(10L), Optional.empty());
        given(reviewNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(claimed));

        Workspace workspace = mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));

        // when
        processor.processPending(10);

        // then
        verify(notificationTransportApiClient).sendBlockMessage(
                eq("xoxb-test-token"),
                eq("C1"),
                any(JsonNode.class),
                any(JsonNode.class),
                eq("fallback")
        );
        verify(reviewNotificationOutboxRepository)
                .saveIfProcessingLeaseMatched(eq(claimed), any(), eq(CLAIMED_PROCESSING_STARTED_AT));
    }

    @Test
    void semantic_payload가_있으면_renderer로_최종_payload를_조립해_전송한다() throws Exception {
        // given
        ReviewNotificationOutbox claimed = spy(ReviewNotificationOutbox.semantic(
                "semantic-outbox",
                1L,
                "T1",
                "C1",
                """
                {"repositoryName":"repo","githubPullRequestId":101,
                "pullRequestNumber":42,"pullRequestTitle":"Fix bug",
                "pullRequestUrl":"https://github.com/pr/1",
                "authorGithubId":"author-gh",
                "pendingReviewers":["reviewer-gh-1"],
                "reviewersToMention":["reviewer-gh-1"]}
                """
        ));
        setProcessingState(claimed, CLAIMED_PROCESSING_STARTED_AT, 1);
        given(reviewNotificationOutboxRepository.claimNextId(any(), anyCollection()))
                .willReturn(Optional.of(10L), Optional.empty());
        given(reviewNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(claimed));
        given(reviewNotificationMessageRenderer.render(claimed)).willReturn(new ReviewMessageDto(
                new ObjectMapper().readTree("[{\"type\":\"section\"}]"),
                new ObjectMapper().readTree("[{\"blocks\":[{\"type\":\"actions\"}]}]"),
                "fallback"
        ));

        Workspace workspace = mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));

        // when
        processor.processPending(10);

        // then
        verify(reviewNotificationMessageRenderer).render(claimed);
        verify(notificationTransportApiClient).sendBlockMessage(
                eq("xoxb-test-token"),
                eq("C1"),
                any(JsonNode.class),
                any(JsonNode.class),
                eq("fallback")
        );
        verify(reviewNotificationOutboxRepository)
                .saveIfProcessingLeaseMatched(eq(claimed), any(), eq(CLAIMED_PROCESSING_STARTED_AT));
    }

    private ReviewNotificationOutbox spyClaimed(String channelId, int processingAttempt, String attachmentsJson) {
        ReviewNotificationOutboxStringField attachmentsField = ReviewNotificationOutboxStringField.absent();
        if (attachmentsJson != null) {
            attachmentsField = ReviewNotificationOutboxStringField.present(attachmentsJson);
        }

        ReviewNotificationOutbox outbox = ReviewNotificationOutbox.channelBlocks(
                "OUTBOX-" + channelId + "-" + processingAttempt,
                "T1",
                channelId,
                "[]",
                attachmentsField,
                ReviewNotificationOutboxStringField.present("fallback")
        );
        Instant base = CLAIMED_PROCESSING_STARTED_AT;
        setProcessingState(outbox, base, 1);

        for (int attempt = 1; attempt < processingAttempt; attempt++) {
            outbox.markRetryPending(base.minusSeconds(110 - attempt), "retry failure");
            setProcessingState(outbox, base, attempt + 1);
        }

        return spy(outbox);
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
