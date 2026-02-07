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
import com.slack.bot.application.interactivity.reply.InteractivityErrorType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChangeReviewReservationActionHandlerTest {

    @Autowired
    ChangeReviewReservationActionHandler changeReviewReservationActionHandler;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NotificationApiClient notificationApiClient;

    @Autowired
    ReviewInteractionEventPublisher reviewInteractionEventPublisher;

    @Test
    @Sql("classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql")
    void 리뷰_예약_변경_요청이면_모달을_열고_이벤트를_발행하며_빈_응답을_반환한다() {
        // given
        BlockActionCommandDto command = commandWithReservationId("100", "U1", "TRIGGER_1");

        // when
        BlockActionOutcomeDto actual = changeReviewReservationActionHandler.handle(command);

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

    @Test
    @Sql("classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql")
    void 본인_소유가_아닌_리뷰_예약_변경_요청이면_권한_오류_알림을_보내고_모달을_열지_않는다() {
        // given
        BlockActionCommandDto command = commandWithReservationId("100", "U2", "TRIGGER_1");

        // when
        BlockActionOutcomeDto actual = changeReviewReservationActionHandler.handle(command);

        // then
        assertAll(
                () -> assertThat(actual.duplicateReservation()).isNull(),
                () -> assertThat(actual.cancelledReservation()).isNull(),
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        eq("U2"),
                        eq(InteractivityErrorType.NOT_OWNER_CHANGE.message())
                ),
                () -> verify(notificationApiClient, never()).openModal(any(), any(), any()),
                () -> verify(reviewInteractionEventPublisher, never()).publish(any())
        );
    }

    private BlockActionCommandDto commandWithReservationId(String reservationId, String slackUserId, String triggerId) {
        JsonNode payload = objectMapper.createObjectNode()
                                       .put("trigger_id", triggerId);
        JsonNode action = objectMapper.createObjectNode()
                                      .put("value", reservationId);

        return new BlockActionCommandDto(
                payload,
                action,
                BlockActionType.CHANGE_REVIEW_RESERVATION.value(),
                BlockActionType.CHANGE_REVIEW_RESERVATION,
                "T1",
                "C1",
                slackUserId,
                "xoxb-test-token"
        );
    }
}
