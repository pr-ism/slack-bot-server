package com.slack.bot.application.interactivity.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interactivity.client.NotificationApiClient;
import com.slack.bot.application.interactivity.publisher.ReviewInteractionEventPublisher;
import com.slack.bot.application.interactivity.publisher.ReviewReservationRequestEvent;
import com.slack.bot.application.interactivity.reply.InteractivityErrorType;
import com.slack.bot.domain.reservation.ReviewReservation;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewSchedulerWorkflowTest {

    @Autowired
    ReviewSchedulerWorkflow reviewSchedulerWorkflow;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NotificationApiClient notificationApiClient;

    @Autowired
    ReviewInteractionEventPublisher reviewInteractionEventPublisher;

    @Test
    void 예약_메타가_없으면_오류_알림을_보내고_빈_값을_반환한다() {
        // given
        JsonNode payload = payloadWithTriggerId("TRIGGER_1");
        JsonNode action = actionWithMetaJson("");

        // when
        Optional<ReviewReservation> actual = reviewSchedulerWorkflow.handleOpenScheduler(
                payload,
                action,
                "T1",
                "C1",
                "U1",
                "xoxb-test-token"
        );

        // then
        assertAll(
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        "xoxb-test-token",
                        "C1",
                        "U1",
                        InteractivityErrorType.INVALID_META.message()
                ),
                () -> verify(notificationApiClient, never()).openModal(any(), any(), any()),
                () -> assertThat(actual).isEmpty()
        );
    }

    @Test
    void 예약_메타가_잘못되면_오류_알림을_보내고_빈_값을_반환한다() {
        // given
        JsonNode payload = payloadWithTriggerId("TRIGGER_1");
        JsonNode action = actionWithMetaJson("{invalid-json");

        // when
        Optional<ReviewReservation> actual = reviewSchedulerWorkflow.handleOpenScheduler(
                payload,
                action,
                "T1",
                "C1",
                "U1",
                "xoxb-test-token"
        );

        // then
        assertAll(
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        "xoxb-test-token",
                        "C1",
                        "U1",
                        InteractivityErrorType.INVALID_META.message()
                ),
                () -> verify(notificationApiClient, never()).openModal(any(), any(), any()),
                () -> assertThat(actual).isEmpty()
        );
    }

    @Test
    void 트리거_ID가_없으면_오류_알림을_보내고_빈_값을_반환한다() {
        // given
        JsonNode payload = objectMapper.createObjectNode();
        JsonNode action = actionWithMetaJson(metaJsonWithProjectId("123"));

        // when
        Optional<ReviewReservation> actual = reviewSchedulerWorkflow.handleOpenScheduler(
                payload,
                action,
                "T1",
                "C1",
                "U1",
                "xoxb-test-token"
        );

        // then
        assertAll(
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        "xoxb-test-token",
                        "C1",
                        "U1",
                        InteractivityErrorType.INVALID_META.message()
                ),
                () -> verify(notificationApiClient, never()).openModal(any(), any(), any()),
                () -> assertThat(actual).isEmpty()
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/reservation/project_123.sql",
            "classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql"
    })
    void 활성_예약이_있으면_모달을_열지_않고_기존_예약을_반환한다() {
        // given
        String metaJson = metaJsonWithProjectId("123");
        JsonNode payload = payloadWithTriggerId("TRIGGER_1");
        JsonNode action = actionWithMetaJson(metaJson);

        // when
        Optional<ReviewReservation> actual = reviewSchedulerWorkflow.handleOpenScheduler(
                payload,
                action,
                "T1",
                "C1",
                "U1",
                "xoxb-test-token"
        );

        // then
        ArgumentCaptor<ReviewReservationRequestEvent> eventCaptor = ArgumentCaptor.forClass(ReviewReservationRequestEvent.class);

        assertAll(
                () -> verify(reviewInteractionEventPublisher).publish(eventCaptor.capture()),
                () -> verify(notificationApiClient, never()).openModal(any(), any(), any()),
                () -> assertThat(actual).isPresent(),
                () -> assertThat(actual.get().getId()).isEqualTo(100L),
                () -> assertThat(eventCaptor.getValue().teamId()).isEqualTo("T1"),
                () -> assertThat(eventCaptor.getValue().metaJson()).isEqualTo(metaJson)
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/reservation/project_123.sql")
    void 처리_중_오류가_발생하면_오류_알림을_보내고_빈_값을_반환한다() {
        // given
        String metaJson = metaJsonWithProjectId("123");
        JsonNode payload = payloadWithTriggerId("TRIGGER_1");
        JsonNode action = actionWithMetaJson(metaJson);

        doThrow(new IllegalStateException("publish failed"))
                .when(reviewInteractionEventPublisher)
                .publish(any());

        // when
        Optional<ReviewReservation> actual = reviewSchedulerWorkflow.handleOpenScheduler(
                payload,
                action,
                "T1",
                "C1",
                "U1",
                "xoxb-test-token"
        );

        // then
        assertAll(
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        "xoxb-test-token",
                        "C1",
                        "U1",
                        InteractivityErrorType.RESERVATION_LOAD_FAILURE.message()
                ),
                () -> verify(notificationApiClient, never()).openModal(any(), any(), any()),
                () -> assertThat(actual).isEmpty()
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/reservation/project_123.sql")
    void 활성_예약이_없으면_모달을_열고_빈_값을_반환한다() {
        // given
        String metaJson = metaJsonWithProjectId("123");
        JsonNode payload = payloadWithTriggerId("TRIGGER_1");
        JsonNode action = actionWithMetaJson(metaJson);

        // when
        Optional<ReviewReservation> actual = reviewSchedulerWorkflow.handleOpenScheduler(
                payload,
                action,
                "T1",
                "C1",
                "U1",
                "xoxb-test-token"
        );

        // then
        ArgumentCaptor<ReviewReservationRequestEvent> eventCaptor = ArgumentCaptor.forClass(ReviewReservationRequestEvent.class);

        assertAll(
                () -> verify(reviewInteractionEventPublisher).publish(eventCaptor.capture()),
                () -> verify(notificationApiClient).openModal(
                        eq("xoxb-test-token"),
                        eq("TRIGGER_1"),
                        any()
                ),
                () -> assertThat(actual).isEmpty(),
                () -> assertThat(eventCaptor.getValue().teamId()).isEqualTo("T1"),
                () -> assertThat(eventCaptor.getValue().metaJson()).isEqualTo(metaJson)
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/reservation/project_123.sql",
            "classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql"
    })
    void 같은_리뷰어의_다른_PR이면_중복으로_보지_않고_모달을_연다() {
        // given
        String metaJson = metaJsonWithProjectIdAndPullRequest("123", 11L, 11);
        JsonNode payload = payloadWithTriggerId("TRIGGER_1");
        JsonNode action = actionWithMetaJson(metaJson);

        // when
        Optional<ReviewReservation> actual = reviewSchedulerWorkflow.handleOpenScheduler(
                payload,
                action,
                "T1",
                "C1",
                "U1",
                "xoxb-test-token"
        );

        // then
        assertAll(
                () -> assertThat(actual).isEmpty(),
                () -> verify(notificationApiClient).openModal(
                        eq("xoxb-test-token"),
                        eq("TRIGGER_1"),
                        any()
                )
        );
    }

    @Test
    void 리뷰이가_리뷰_예약을_누르면_예약_불가_알림을_보내고_모달을_열지_않는다() {
        // given
        JsonNode payload = payloadWithTriggerId("TRIGGER_1");
        String metaJson = objectMapper.createObjectNode()
                                      .put("team_id", "T1")
                                      .put("channel_id", "C1")
                                      .put("pull_request_id", 10L)
                                      .put("pull_request_number", 10)
                                      .put("pull_request_title", "PR 제목")
                                      .put("pull_request_url", "https://github.com/org/repo/pull/10")
                                      .put("project_id", "123")
                                      .put("author_github_id", "author-gh")
                                      .put("author_slack_id", "U1")
                                      .put("reservation_id", "R1")
                                      .toString();
        JsonNode action = actionWithMetaJson(metaJson);

        // when
        Optional<ReviewReservation> actual = reviewSchedulerWorkflow.handleOpenScheduler(
                payload,
                action,
                "T1",
                "C1",
                "U1",
                "xoxb-test-token"
        );

        // then
        assertAll(
                () -> assertThat(actual).isEmpty(),
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        "xoxb-test-token",
                        "C1",
                        "U1",
                        InteractivityErrorType.REVIEWEE_CANNOT_RESERVE.message()
                ),
                () -> verify(notificationApiClient, never()).openModal(any(), any(), any()),
                () -> verify(reviewInteractionEventPublisher, never()).publish(any())
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/reservation/project_123.sql")
    void 요청자_Slack_ID가_공백이면_리뷰이로_판단하지_않고_모달을_연다() {
        // given
        String metaJson = metaJsonWithProjectId("123");
        JsonNode payload = payloadWithTriggerId("TRIGGER_1");
        JsonNode action = actionWithMetaJson(metaJson);

        // when
        Optional<ReviewReservation> actual = reviewSchedulerWorkflow.handleOpenScheduler(
                payload,
                action,
                "T1",
                "C1",
                " ",
                "xoxb-test-token"
        );

        // then
        assertAll(
                () -> assertThat(actual).isEmpty(),
                () -> verify(notificationApiClient).openModal(
                        eq("xoxb-test-token"),
                        eq("TRIGGER_1"),
                        any()
                ),
                () -> verify(notificationApiClient, never()).sendEphemeralMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        eq(" "),
                        eq(InteractivityErrorType.REVIEWEE_CANNOT_RESERVE.message())
                )
        );
    }

    private JsonNode payloadWithTriggerId(String triggerId) {
        return objectMapper.createObjectNode()
                           .put("trigger_id", triggerId);
    }

    private JsonNode actionWithMetaJson(String metaJson) {
        return objectMapper.createObjectNode()
                           .put("value", metaJson);
    }

    private String metaJsonWithProjectId(String projectId) {
        return metaJsonWithProjectIdAndPullRequest(projectId, 10L, 10);
    }

    private String metaJsonWithProjectIdAndPullRequest(String projectId, Long pullRequestId, int pullRequestNumber) {
        return objectMapper.createObjectNode()
                           .put("team_id", "T1")
                           .put("channel_id", "C1")
                           .put("pull_request_id", pullRequestId)
                           .put("pull_request_number", pullRequestNumber)
                           .put("pull_request_title", "PR 제목 " + pullRequestNumber)
                           .put("pull_request_url", "https://github.com/org/repo/pull/" + pullRequestNumber)
                           .put("project_id", projectId)
                           .put("author_github_id", "author-gh")
                           .put("author_slack_id", "U_AUTHOR")
                           .put("reservation_id", "R1")
                           .toString();
    }
}
