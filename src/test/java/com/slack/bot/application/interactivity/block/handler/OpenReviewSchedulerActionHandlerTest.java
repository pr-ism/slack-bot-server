package com.slack.bot.application.interactivity.block.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interactivity.block.BlockActionType;
import com.slack.bot.application.interactivity.block.dto.BlockActionCommandDto;
import com.slack.bot.application.interactivity.block.dto.BlockActionOutcomeDto;
import com.slack.bot.application.interactivity.client.NotificationApiClient;
import com.slack.bot.application.interactivity.publisher.ReviewInteractionEventPublisher;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OpenReviewSchedulerActionHandlerTest {

    @Autowired
    OpenReviewSchedulerActionHandler openReviewSchedulerActionHandler;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NotificationApiClient notificationApiClient;

    @Autowired
    ReviewInteractionEventPublisher reviewInteractionEventPublisher;

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/reservation/project_123.sql",
            "classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql"
    })
    void 활성_리뷰_예약이_이미_있으면_중복_예약으로_응답하고_모달은_열지_않는다() {
        // given
        BlockActionCommandDto command = commandWithMeta(metaJsonWithProjectId("123"), "TRIGGER_1");

        // when
        BlockActionOutcomeDto actual = openReviewSchedulerActionHandler.handle(command);

        // then
        assertAll(
                () -> assertThat(actual.duplicateReservation()).isNotNull(),
                () -> assertThat(actual.duplicateReservation().getId()).isEqualTo(100L),
                () -> assertThat(actual.cancelledReservation()).isNull(),
                () -> verify(reviewInteractionEventPublisher).publish(any()),
                () -> verify(notificationApiClient, never()).openModal(any(), any(), any())
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/reservation/project_123.sql")
    void 활성_예약이_없으면_예약_시간_선택_모달을_열고_빈_결과를_응답한다() {
        // given
        BlockActionCommandDto command = commandWithMeta(metaJsonWithProjectId("123"), "TRIGGER_1");

        // when
        BlockActionOutcomeDto actual = openReviewSchedulerActionHandler.handle(command);

        // then
        assertAll(
                () -> assertThat(actual.duplicateReservation()).isNull(),
                () -> assertThat(actual.cancelledReservation()).isNull(),
                () -> verify(reviewInteractionEventPublisher).publish(any()),
                () -> verify(notificationApiClient).openModal(
                        eq("xoxb-test-token"),
                        eq("TRIGGER_1"),
                        any()
                )
        );
    }

    private BlockActionCommandDto commandWithMeta(String metaJson, String triggerId) {
        JsonNode payload = objectMapper.createObjectNode()
                                       .put("trigger_id", triggerId);
        JsonNode action = objectMapper.createObjectNode()
                                      .put("value", metaJson);

        return new BlockActionCommandDto(
                payload,
                action,
                BlockActionType.OPEN_REVIEW_SCHEDULER.value(),
                BlockActionType.OPEN_REVIEW_SCHEDULER,
                "T1",
                "C1",
                "U1",
                "xoxb-test-token"
        );
    }

    private String metaJsonWithProjectId(String projectId) {
        return objectMapper.createObjectNode()
                .put("team_id", "T1")
                .put("channel_id", "C1")
                .put("pull_request_id", 10L)
                .put("pull_request_number", 10)
                .put("pull_request_title", "PR 제목")
                .put("pull_request_url", "https://github.com/org/repo/pull/10")
                .put("project_id", projectId)
                .put("author_github_id", "author-gh")
                .put("author_slack_id", "U_AUTHOR")
                .put("reservation_id", "R1")
                .toString();
    }
}
