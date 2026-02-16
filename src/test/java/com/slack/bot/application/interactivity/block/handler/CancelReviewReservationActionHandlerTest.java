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
import com.slack.bot.application.interactivity.publisher.ReviewInteractionEvent;
import com.slack.bot.application.interactivity.publisher.ReviewReservationCancelEvent;
import com.slack.bot.application.interactivity.reply.InteractivityErrorType;
import com.slack.bot.domain.reservation.ReservationStatus;
import com.slack.bot.domain.reservation.ReviewReservation;
import com.slack.bot.domain.reservation.repository.ReviewReservationRepository;
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
class CancelReviewReservationActionHandlerTest {

    @Autowired
    CancelReviewReservationActionHandler cancelReviewReservationActionHandler;

    @Autowired
    ReviewReservationRepository reviewReservationRepository;

    @Autowired
    NotificationApiClient notificationApiClient;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @Sql("classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql")
    void 리뷰_예약_취소_요청이면_예약을_취소하고_취소_예약을_응답한다(ApplicationEvents applicationEvents) {
        // given
        BlockActionCommandDto command = commandWithReservationId("100", "U1");

        // when
        BlockActionOutcomeDto actual = cancelReviewReservationActionHandler.handle(command);

        // then
        Optional<ReviewReservation> cancelled = reviewReservationRepository.findById(100L);

        assertAll(
                () -> assertThat(actual.duplicateReservation()).isNull(),
                () -> assertThat(actual.cancelledReservation()).isNotNull(),
                () -> assertThat(actual.cancelledReservation().getId()).isEqualTo(100L),
                () -> assertThat(cancelled).isPresent(),
                () -> assertThat(cancelled.get().getStatus()).isEqualTo(ReservationStatus.CANCELLED),
                () -> assertThat(applicationEvents.stream(ReviewReservationCancelEvent.class).toList())
                        .singleElement()
                        .satisfies(event -> assertAll(
                                () -> assertThat(event.teamId()).isEqualTo("T1"),
                                () -> assertThat(event.channelId()).isEqualTo("C1"),
                                () -> assertThat(event.slackUserId()).isEqualTo("U1"),
                                () -> assertThat(event.reservationId()).isEqualTo(100L)
                        )),
                () -> verify(notificationApiClient, never()).sendEphemeralMessage(any(), any(), any(), any())
        );
    }

    @Test
    void 존재하지_않는_예약_취소_요청이면_예약_없음_알림을_보내고_빈_응답을_반환한다(ApplicationEvents applicationEvents) {
        // given
        BlockActionCommandDto command = commandWithReservationId("999", "U1");

        // when
        BlockActionOutcomeDto actual = cancelReviewReservationActionHandler.handle(command);

        // then
        assertAll(
                () -> assertThat(actual.duplicateReservation()).isNull(),
                () -> assertThat(actual.cancelledReservation()).isNull(),
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        eq("U1"),
                        eq(InteractivityErrorType.RESERVATION_NOT_FOUND.message())
                ),
                () -> assertThat(applicationEvents.stream(ReviewInteractionEvent.class).toList()).isEmpty()
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql")
    void 비소유자의_취소_요청이면_권한_없음_알림을_보내고_빈_응답을_반환한다(ApplicationEvents applicationEvents) {
        // given
        BlockActionCommandDto command = commandWithReservationId("100", "U2");

        // when
        BlockActionOutcomeDto actual = cancelReviewReservationActionHandler.handle(command);

        // then
        assertAll(
                () -> assertThat(actual.duplicateReservation()).isNull(),
                () -> assertThat(actual.cancelledReservation()).isNull(),
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        eq("U2"),
                        eq(InteractivityErrorType.NOT_OWNER_CANCEL.message())
                ),
                () -> assertThat(applicationEvents.stream(ReviewInteractionEvent.class).toList()).isEmpty()
        );
    }

    private BlockActionCommandDto commandWithReservationId(String reservationId, String slackUserId) {
        JsonNode action = objectMapper.createObjectNode()
                                      .put("value", reservationId);

        return new BlockActionCommandDto(
                objectMapper.createObjectNode(),
                action,
                BlockActionType.CANCEL_REVIEW_RESERVATION.value(),
                BlockActionType.CANCEL_REVIEW_RESERVATION,
                "T1",
                "C1",
                slackUserId,
                "xoxb-test-token"
        );
    }
}
