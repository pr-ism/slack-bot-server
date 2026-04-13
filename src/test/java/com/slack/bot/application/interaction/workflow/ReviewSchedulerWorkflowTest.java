package com.slack.bot.application.interaction.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.api.model.view.View;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interaction.client.NotificationApiClient;
import com.slack.bot.application.interaction.publisher.ReviewInteractionEvent;
import com.slack.bot.application.interaction.publisher.ReviewInteractionEventPublisher;
import com.slack.bot.application.interaction.publisher.ReviewReservationRequestEvent;
import com.slack.bot.application.interaction.reply.InteractionErrorType;
import com.slack.bot.domain.reservation.ReviewReservation;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@RecordApplicationEvents
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
    void 예약_메타가_없으면_오류_알림을_보내고_빈_값을_반환한다(ApplicationEvents actualApplicationEvents) {
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
        verify(notificationApiClient).sendEphemeralMessage(
                        "xoxb-test-token",
                        "C1",
                        "U1",
                        InteractionErrorType.INVALID_META.message()
                );
        verify(notificationApiClient, never()).openModal(any(), any(), any(View.class));
        assertAll(
                () -> assertThat(actual).isEmpty(),
                () -> assertThat(actualApplicationEvents.stream(ReviewInteractionEvent.class).toList()).isEmpty()
        );
    }

    @Test
    void 예약_메타가_잘못되면_오류_알림을_보내고_빈_값을_반환한다(ApplicationEvents actualApplicationEvents) {
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
        verify(notificationApiClient).sendEphemeralMessage(
                        "xoxb-test-token",
                        "C1",
                        "U1",
                        InteractionErrorType.INVALID_META.message()
                );
        verify(notificationApiClient, never()).openModal(any(), any(), any(View.class));
        assertAll(
                () -> assertThat(actual).isEmpty(),
                () -> assertThat(actualApplicationEvents.stream(ReviewInteractionEvent.class).toList()).isEmpty()
        );
    }

    @Test
    void 트리거_ID가_없으면_오류_알림을_보내고_빈_값을_반환한다(ApplicationEvents actualApplicationEvents) {
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
        verify(notificationApiClient).sendEphemeralMessage(
                        "xoxb-test-token",
                        "C1",
                        "U1",
                        InteractionErrorType.INVALID_META.message()
                );
        verify(notificationApiClient, never()).openModal(any(), any(), any(View.class));
        assertAll(
                () -> assertThat(actual).isEmpty(),
                () -> assertThat(actualApplicationEvents.stream(ReviewInteractionEvent.class).toList()).isEmpty()
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/reservation/project_123.sql",
            "classpath:sql/fixtures/interaction/active_review_reservation_t1_project_123_u1.sql"
    })
    void 활성_예약이_있으면_모달을_열지_않고_기존_예약을_반환한다(ApplicationEvents actualApplicationEvents) {
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
        assertThat(actualApplicationEvents.stream(ReviewReservationRequestEvent.class).toList())
                        .singleElement()
                        .satisfies(actualEvent -> assertAll(
                                () -> assertThat(actualEvent.teamId()).isEqualTo("T1"),
                                () -> assertThat(actualEvent.channelId()).isEqualTo("C1"),
                                () -> assertThat(actualEvent.slackUserId()).isEqualTo("U1"),
                                () -> assertThat(actualEvent.metaJson()).isEqualTo(metaJson)
                        ));
        verify(notificationApiClient, never()).openModal(any(), any(), any(View.class));
        assertAll(
                () -> assertThat(actual).hasValueSatisfying(reservation ->
                        assertThat(reservation.getId()).isEqualTo(100L))
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/reservation/project_123.sql")
    void 처리_중_오류가_발생하면_오류_알림을_보내고_빈_값을_반환한다(ApplicationEvents actualApplicationEvents) {
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
        verify(notificationApiClient).sendEphemeralMessage(
                        "xoxb-test-token",
                        "C1",
                        "U1",
                        InteractionErrorType.RESERVATION_LOAD_FAILURE.message()
                );
        verify(notificationApiClient, never()).openModal(any(), any(), any(View.class));
        assertAll(
                () -> assertThat(actual).isEmpty(),
                () -> assertThat(actualApplicationEvents.stream(ReviewInteractionEvent.class).toList()).isEmpty()
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/reservation/project_123.sql")
    void 활성_예약이_없으면_모달을_열고_빈_값을_반환한다(ApplicationEvents actualApplicationEvents) {
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
        assertThat(actualApplicationEvents.stream(ReviewReservationRequestEvent.class).toList())
                        .singleElement()
                        .satisfies(actualEvent -> assertAll(
                                () -> assertThat(actualEvent.teamId()).isEqualTo("T1"),
                                () -> assertThat(actualEvent.channelId()).isEqualTo("C1"),
                                () -> assertThat(actualEvent.slackUserId()).isEqualTo("U1"),
                                () -> assertThat(actualEvent.metaJson()).isEqualTo(metaJson)
                        ));
        verify(notificationApiClient).openModal(
                        eq("xoxb-test-token"),
                        eq("TRIGGER_1"),
                        any(View.class)
                );
        assertThat(actual).isEmpty();
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/reservation/project_123.sql",
            "classpath:sql/fixtures/interaction/active_review_reservation_t1_project_123_u1.sql"
    })
    void 같은_리뷰어의_다른_PR이면_중복으로_보지_않고_모달을_연다(ApplicationEvents actualApplicationEvents) {
        // given
        String metaJson = metaJsonWithProjectIdAndGithubPullRequest("123", 11L, 11);
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
                () -> assertThat(actualApplicationEvents.stream(ReviewReservationRequestEvent.class).toList())
                                        .singleElement()
                                        .satisfies(actualEvent -> assertAll(
                                                () -> assertThat(actualEvent.teamId()).isEqualTo("T1"),
                                                () -> assertThat(actualEvent.githubPullRequestId()).isEqualTo(11L),
                                                () -> assertThat(actualEvent.metaJson()).isEqualTo(metaJson)
                                        ))
        );
        verify(notificationApiClient).openModal(
                        eq("xoxb-test-token"),
                        eq("TRIGGER_1"),
                        any(View.class)
                );
    }

    @Test
    void 리뷰이가_리뷰_예약을_누르면_예약_불가_알림을_보내고_모달을_열지_않는다(ApplicationEvents actualApplicationEvents) {
        // given
        JsonNode payload = payloadWithTriggerId("TRIGGER_1");
        String metaJson = objectMapper.createObjectNode()
                                      .put("team_id", "T1")
                                      .put("channel_id", "C1")
                                      .put("github_pull_request_id", 10L)
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
        assertThat(actual).isEmpty();
        verify(notificationApiClient).sendEphemeralMessage(
                        "xoxb-test-token",
                        "C1",
                        "U1",
                        InteractionErrorType.REVIEWEE_CANNOT_RESERVE.message()
                );
        verify(notificationApiClient, never()).openModal(any(), any(), any(View.class));
        assertThat(actualApplicationEvents.stream(ReviewInteractionEvent.class).toList()).isEmpty();
    }

    @Test
    @Sql("classpath:sql/fixtures/reservation/project_123.sql")
    void 요청자_Slack_ID가_공백이면_리뷰이로_판단하지_않고_모달을_연다(ApplicationEvents actualApplicationEvents) {
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
                () -> assertThat(actualApplicationEvents.stream(ReviewReservationRequestEvent.class).toList())
                                        .singleElement()
                                        .satisfies(actualEvent -> assertAll(
                                                () -> assertThat(actualEvent.teamId()).isEqualTo("T1"),
                                                () -> assertThat(actualEvent.slackUserId()).isEqualTo(" "),
                                                () -> assertThat(actualEvent.metaJson()).isEqualTo(metaJson)
                                        ))
        );
        verify(notificationApiClient).openModal(
                        eq("xoxb-test-token"),
                        eq("TRIGGER_1"),
                        any(View.class)
                );
        verify(notificationApiClient, never()).sendEphemeralMessage(
                        "xoxb-test-token",
                        "C1",
                        " ",
                        InteractionErrorType.REVIEWEE_CANNOT_RESERVE.message()
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
        return metaJsonWithProjectIdAndGithubPullRequest(projectId, 10L, 10);
    }

    private String metaJsonWithProjectIdAndGithubPullRequest(
            String projectId,
            Long githubPullRequestId,
            int pullRequestNumber
    ) {
        return objectMapper.createObjectNode()
                           .put("team_id", "T1")
                           .put("channel_id", "C1")
                           .put("github_pull_request_id", githubPullRequestId)
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
