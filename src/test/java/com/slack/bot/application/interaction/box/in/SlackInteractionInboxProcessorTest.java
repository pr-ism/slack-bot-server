package com.slack.bot.application.interaction.box.in;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interaction.block.BlockActionType;
import com.slack.bot.application.interaction.client.NotificationApiClient;
import com.slack.bot.domain.reservation.ReservationStatus;
import com.slack.bot.domain.reservation.ReviewReservation;
import com.slack.bot.domain.reservation.repository.ReviewReservationRepository;
import com.slack.bot.infrastructure.common.FailureSnapshotDefaults;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxHistory;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxStatus;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import com.slack.bot.infrastructure.interaction.box.persistence.in.JpaSlackInteractionInboxHistoryRepository;
import com.slack.bot.infrastructure.interaction.box.in.repository.SlackInteractionInboxRepository;
import com.slack.bot.infrastructure.interaction.box.persistence.in.JpaSlackInteractionInboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackInteractionInboxProcessorTest {

    @Autowired
    SlackInteractionInboxProcessor slackInteractionInboxProcessor;

    @Autowired
    NotificationApiClient notificationApiClient;

    @Autowired
    ReviewReservationRepository actualReviewReservationRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JpaSlackInteractionInboxRepository jpaSlackInteractionInboxRepository;

    @Autowired
    JpaSlackInteractionInboxHistoryRepository jpaSlackInteractionInboxHistoryRepository;

    @Autowired
    SlackInteractionInboxRepository actualSlackInteractionInboxRepository;

    @Autowired
    Clock clock;

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/interaction/active_review_reservation_t1_project_123_u1.sql"
    })
    void block_actions_인박스를_처리하면_취소_예약_도메인_흐름이_수행된다() throws Exception {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1"))
                .willReturn("D-REVIEWER");
        String payloadJson = objectMapper.writeValueAsString(cancelReservationPayload("100"));

        // when
        boolean actual = slackInteractionInboxProcessor.enqueueBlockAction(payloadJson);

        slackInteractionInboxProcessor.processPendingBlockActions(10);

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            Optional<ReviewReservation> actualReservation = actualReviewReservationRepository.findById(100L);

            assertAll(
                    () -> assertThat(actual).isTrue(),
                    () -> assertThat(jpaSlackInteractionInboxRepository.findAll())
                            .hasSize(1)
                            .first()
                            .extracting(inbox -> inbox.getStatus())
                            .isEqualTo(SlackInteractionInboxStatus.PROCESSED),
                    () -> assertThat(actualReservation)
                            .isPresent()
                            .get()
                            .extracting(reservation -> reservation.getStatus())
                            .isEqualTo(ReservationStatus.CANCELLED)
            );
            verify(notificationApiClient, times(1)).openDirectMessageChannel("xoxb-test-token", "U1");
            verify(notificationApiClient, times(1)).sendBlockMessage(
                    eq("xoxb-test-token"),
                    eq("D-REVIEWER"),
                    any(),
                    any()
            );
        });
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/interaction/active_review_reservation_t1_project_123_u1.sql",
            "classpath:sql/fixtures/box/processing_timeout_block_action_inbox.sql"
    })
    void PROCESSING_타임아웃_block_actions_inbox는_RETRY_PENDING으로_복구된_후_정상_재처리된다() {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1"))
                .willReturn("D-REVIEWER");

        // when
        slackInteractionInboxProcessor.recoverBlockActionTimeoutProcessing();
        slackInteractionInboxProcessor.processPendingBlockActions(10);

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            SlackInteractionInbox actualProcessedInbox = jpaSlackInteractionInboxRepository.findById(200L).orElseThrow();
            Optional<ReviewReservation> actualReservation = actualReviewReservationRepository.findById(100L);

            assertAll(
                    () -> assertThat(actualProcessedInbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSED),
                    () -> assertThat(actualProcessedInbox.getProcessingAttempt()).isEqualTo(2),
                    () -> assertThat(actualReservation)
                            .isPresent()
                            .get()
                            .extracting(reservation -> reservation.getStatus())
                            .isEqualTo(ReservationStatus.CANCELLED)
            );
            verify(notificationApiClient, times(1)).openDirectMessageChannel("xoxb-test-token", "U1");
            verify(notificationApiClient, times(1)).sendBlockMessage(
                    eq("xoxb-test-token"),
                    eq("D-REVIEWER"),
                    any(),
                    any()
            );
        });
    }

    @Test
    void PROCESSING_타임아웃_block_actions_inbox가_최대시도에_도달하면_FAILED_RETRY_EXHAUSTED로_격리된다() {
        // given
        SlackInteractionInbox timeoutInbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "BLOCK-ACTION-TIMEOUT-POISON-PILL",
                "{\"team\":{\"id\":\"T1\"},\"channel\":{\"id\":\"C1\"},\"user\":{\"id\":\"U1\"},\"actions\":[{\"action_id\":\"cancel_review_reservation\",\"value\":\"100\"}]}"
        );
        Instant base = clock.instant();
        setProcessingState(timeoutInbox, base.minusSeconds(120), 1);
        timeoutInbox.markRetryPending(base.minusSeconds(110), "actualFirst failure");
        setProcessingState(timeoutInbox, base.minusSeconds(100), 2);
        SlackInteractionInbox actualSaved = jpaSlackInteractionInboxRepository.save(timeoutInbox);

        // when
        slackInteractionInboxProcessor.recoverBlockActionTimeoutProcessing();

        // then
        SlackInteractionInbox actual = jpaSlackInteractionInboxRepository.findById(actualSaved.getId()).orElseThrow();

        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(SlackInteractionInboxStatus.FAILED),
                () -> assertThat(actual.getProcessingAttempt()).isEqualTo(2),
                () -> assertThat(actual.getFailureType()).isEqualTo(SlackInteractionFailureType.RETRY_EXHAUSTED),
                () -> assertThat(actual.getFailureReason()).isNotBlank()
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/interaction/active_review_reservation_t1_project_123_u1.sql"
    })
    void 동일_payload를_중복_enqueue하면_한번만_적재되고_한번만_처리된다() throws Exception {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1"))
                .willReturn("D-REVIEWER");
        String payloadJson = objectMapper.writeValueAsString(cancelReservationPayload("100"));

        // when
        boolean actualFirst = slackInteractionInboxProcessor.enqueueBlockAction(payloadJson);
        boolean actualSecond = slackInteractionInboxProcessor.enqueueBlockAction(payloadJson);
        slackInteractionInboxProcessor.processPendingBlockActions(10);

        // then
        assertAll(
                () -> assertThat(actualFirst).isTrue(),
                () -> assertThat(actualSecond).isFalse()
        );
        verify(notificationApiClient).openDirectMessageChannel("xoxb-test-token", "U1");
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/interaction/project_123.sql"
    })
    void view_submission_인박스를_처리하면_리뷰_예약_워크플로우가_비동기로_수행된다() throws Exception {
        // given
        String payloadJson = objectMapper.writeValueAsString(viewSubmissionPayload("30"));

        // when
        boolean actualEnqueued = slackInteractionInboxProcessor.enqueueViewSubmission(payloadJson);
        slackInteractionInboxProcessor.processPendingViewSubmissions(10);

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            Optional<ReviewReservation> actualSaved = actualReviewReservationRepository.findActive("T1", 123L, "U1");

            assertAll(
                    () -> assertThat(actualEnqueued).isTrue(),
                    () -> assertThat(actualSaved).isPresent(),
                    () -> assertThat(actualSaved.get().getReservationPullRequest().getGithubPullRequestId()).isEqualTo(10L)
            );
        });
    }

    @Test
    void 비즈니스_유효성_실패는_재시도하지않고_즉시_FAILED로_마킹된다() {
        // given
        String invalidPayload = "{invalid-json";

        // when
        boolean actual = slackInteractionInboxProcessor.enqueueBlockAction(invalidPayload);
        slackInteractionInboxProcessor.processPendingBlockActions(10);
        SlackInteractionInbox inbox = jpaSlackInteractionInboxRepository.findAll().getFirst();
        SlackInteractionInbox actualAfterFirst = jpaSlackInteractionInboxRepository.findById(inbox.getId()).orElseThrow();
        List<SlackInteractionInboxHistory> histories = historiesOf(inbox.getId());

        // then
        assertAll(
                () -> assertThat(actual).isTrue(),
                () -> assertThat(actualAfterFirst.getStatus()).isEqualTo(SlackInteractionInboxStatus.FAILED),
                () -> assertThat(actualAfterFirst.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(actualAfterFirst.getFailureType()).isEqualTo(SlackInteractionFailureType.BUSINESS_INVARIANT),
                () -> assertThat(histories).hasSize(1),
                () -> assertThat(histories.getFirst().getStatus()).isEqualTo(SlackInteractionInboxStatus.FAILED),
                () -> assertThat(histories.getFirst().getFailureType()).isEqualTo(SlackInteractionFailureType.BUSINESS_INVARIANT)
        );
    }

    private List<SlackInteractionInboxHistory> historiesOf(Long inboxId) {
        return jpaSlackInteractionInboxHistoryRepository.findAll()
                                                        .stream()
                                                        .filter(history -> inboxId.equals(history.getInboxId()))
                                                        .sorted(Comparator.comparingInt(history -> history.getProcessingAttempt()))
                                                        .toList();
    }

    private ObjectNode cancelReservationPayload(String reservationId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("team", objectMapper.createObjectNode().put("id", "T1"));
        payload.set("channel", objectMapper.createObjectNode().put("id", "C1"));
        payload.set("user", objectMapper.createObjectNode().put("id", "U1"));
        payload.set("actions", actions(BlockActionType.CANCEL_REVIEW_RESERVATION.value(), reservationId));
        return payload;
    }

    private ObjectNode viewSubmissionPayload(String selectedOption) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "view_submission");
        payload.set("team", objectMapper.createObjectNode().put("id", "T1"));
        payload.set("user", objectMapper.createObjectNode().put("id", "U1"));

        ObjectNode view = objectMapper.createObjectNode();
        view.put("callback_id", "review_time_submit");
        view.put("private_metadata", metaJsonWithProjectId("123", null));

        ObjectNode state = objectMapper.createObjectNode();
        ObjectNode values = objectMapper.createObjectNode();
        ObjectNode timeBlock = objectMapper.createObjectNode();
        ObjectNode timeAction = objectMapper.createObjectNode();
        ObjectNode selected = objectMapper.createObjectNode();
        selected.put("value", selectedOption);
        timeAction.set("selected_option", selected);
        timeBlock.set("time_action", timeAction);
        values.set("time_block", timeBlock);
        state.set("values", values);
        view.set("state", state);

        payload.set("view", view);
        return payload;
    }

    private ArrayNode actions(String actionId, String value) {
        ArrayNode actions = objectMapper.createArrayNode();
        actions.add(objectMapper.createObjectNode()
                                .put("action_id", actionId)
                                .put("value", value));
        return actions;
    }

    private String metaJsonWithProjectId(String projectId, String reservationId) {
        ObjectNode meta = objectMapper.createObjectNode()
                                      .put("team_id", "T1")
                                      .put("channel_id", "C1")
                                      .put("github_pull_request_id", 10L)
                                      .put("pull_request_number", 10)
                                      .put("pull_request_title", "PR 제목")
                                      .put("pull_request_url", "https://github.com/org/repo/pull/10")
                                      .put("project_id", projectId)
                                      .put("author_github_id", "author-gh")
                                      .put("author_slack_id", "U_AUTHOR");

        if (reservationId != null) {
            meta.put("reservation_id", reservationId);
        }

        return meta.toString();
    }

    private void setProcessingState(
            SlackInteractionInbox inbox,
            Instant processingStartedAt,
            int processingAttempt
    ) {
        ReflectionTestUtils.setField(inbox, "status", SlackInteractionInboxStatus.PROCESSING);
        ReflectionTestUtils.setField(inbox, "processingStartedAt", processingStartedAt);
        ReflectionTestUtils.setField(inbox, "processingAttempt", processingAttempt);
        ReflectionTestUtils.setField(inbox, "failedAt", FailureSnapshotDefaults.NO_FAILURE_AT);
        ReflectionTestUtils.setField(inbox, "failureReason", FailureSnapshotDefaults.NO_FAILURE_REASON);
        ReflectionTestUtils.setField(inbox, "failureType", SlackInteractionFailureType.NONE);
    }
}
