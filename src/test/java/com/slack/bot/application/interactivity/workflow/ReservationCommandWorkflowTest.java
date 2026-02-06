package com.slack.bot.application.interactivity.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interactivity.client.NotificationApiClient;
import com.slack.bot.application.interactivity.publisher.ReviewInteractionEventPublisher;
import com.slack.bot.application.interactivity.publisher.ReviewReservationCancelEvent;
import com.slack.bot.application.interactivity.publisher.ReviewReservationChangeEvent;
import com.slack.bot.application.interactivity.reply.InteractivityErrorType;
import com.slack.bot.domain.reservation.ReservationStatus;
import com.slack.bot.domain.reservation.ReviewReservation;
import com.slack.bot.domain.reservation.repository.ReviewReservationRepository;
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
class ReservationCommandWorkflowTest {

    @Autowired
    ReservationCommandWorkflow reservationCommandWorkflow;

    @Autowired
    ReviewReservationRepository reviewReservationRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NotificationApiClient notificationApiClient;

    @Autowired
    ReviewInteractionEventPublisher reviewInteractionEventPublisher;

    @Test
    void 예약_ID가_없으면_아무_동작도_하지_않는다() {
        // given
        JsonNode action = actionWithReservationId(" ");

        // when
        Optional<ReviewReservation> actual = reservationCommandWorkflow.handleCancel(
                action,
                "T1",
                "C1",
                "U1",
                "xoxb-test-token"
        );

        // then
        assertAll(
                () -> assertThat(actual).isEmpty(),
                () -> verify(notificationApiClient, never()).sendEphemeralMessage(any(), any(), any(), any()),
                () -> verify(reviewInteractionEventPublisher, never()).publish(any())
        );
    }

    @Test
    void 예약_ID가_숫자가_아니면_아무_동작도_하지_않는다() {
        // given
        JsonNode action = actionWithReservationId("invalid");

        // when
        Optional<ReviewReservation> actual = reservationCommandWorkflow.handleCancel(
                action,
                "T1",
                "C1",
                "U1",
                "xoxb-test-token"
        );

        // then
        assertAll(
                () -> assertThat(actual).isEmpty(),
                () -> verify(notificationApiClient, never()).sendEphemeralMessage(any(), any(), any(), any()),
                () -> verify(reviewInteractionEventPublisher, never()).publish(any())
        );
    }

    @Test
    void 예약이_없으면_예약_없음_알림을_보내고_빈_값을_반환한다() {
        // given
        JsonNode action = actionWithReservationId("999");

        // when
        Optional<ReviewReservation> actual = reservationCommandWorkflow.handleCancel(
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
                        eq("xoxb-test-token"),
                        eq("C1"),
                        eq("U1"),
                        eq(InteractivityErrorType.RESERVATION_NOT_FOUND.message())
                ),
                () -> verify(reviewInteractionEventPublisher, never()).publish(any())
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql")
    void 리뷰_예약_취소_요청은_예약을_취소하고_취소_이벤트를_발행한다() {
        // given
        JsonNode action = actionWithReservationId("100");

        // when
        Optional<ReviewReservation> actual = reservationCommandWorkflow.handleCancel(
                action,
                "T1",
                "C1",
                "U1",
                "xoxb-test-token"
        );

        // then
        Optional<ReviewReservation> cancelled = reviewReservationRepository.findById(100L);
        ArgumentCaptor<ReviewReservationCancelEvent> eventCaptor = ArgumentCaptor.forClass(ReviewReservationCancelEvent.class);

        assertAll(
                () -> verify(reviewInteractionEventPublisher).publish(eventCaptor.capture()),
                () -> verify(notificationApiClient, never()).sendEphemeralMessage(any(), any(), any(), any()),
                () -> assertThat(actual).isPresent(),
                () -> assertThat(actual.get().getId()).isEqualTo(100L),
                () -> assertThat(cancelled).isPresent(),
                () -> assertThat(cancelled.get().getStatus()).isEqualTo(ReservationStatus.CANCELLED),
                () -> assertThat(eventCaptor.getValue().reservationId()).isEqualTo(100L)
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql")
    void 리뷰_예약_취소는_본인만_가능하다는_알림을_보낸다() {
        // given
        JsonNode action = actionWithReservationId("100");

        // when
        Optional<ReviewReservation> actual = reservationCommandWorkflow.handleCancel(
                action,
                "T1",
                "C1",
                "U2",
                "xoxb-test-token"
        );

        // then
        assertAll(
                () -> assertThat(actual).isEmpty(),
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        eq("U2"),
                        eq(InteractivityErrorType.NOT_OWNER_CANCEL.message())
                ),
                () -> verify(reviewInteractionEventPublisher, never()).publish(any())
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql")
    void 리뷰_예약_변경_요청은_모달을_열고_변경_이벤트를_발행한다() {
        // given
        JsonNode payload = objectMapper.createObjectNode()
                .put("trigger_id", "TRIGGER_1");
        JsonNode action = actionWithReservationId("100");

        // when
        reservationCommandWorkflow.handleChange(
                payload,
                action,
                "T1",
                "C1",
                "U1",
                "xoxb-test-token"
        );

        // then
        ArgumentCaptor<ReviewReservationChangeEvent> eventCaptor = ArgumentCaptor.forClass(ReviewReservationChangeEvent.class);

        assertAll(
                () -> verify(reviewInteractionEventPublisher).publish(eventCaptor.capture()),
                () -> verify(notificationApiClient).openModal(
                        eq("xoxb-test-token"),
                        eq("TRIGGER_1"),
                        any()
                ),
                () -> assertThat(eventCaptor.getValue().reservationId()).isEqualTo(100L)
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql")
    void 리뷰_예약_변경은_본인만_가능하다는_알림을_보낸다() {
        // given
        JsonNode payload = objectMapper.createObjectNode()
                .put("trigger_id", "TRIGGER_1");
        JsonNode action = actionWithReservationId("100");

        // when
        reservationCommandWorkflow.handleChange(
                payload,
                action,
                "T1",
                "C1",
                "U2",
                "xoxb-test-token"
        );

        // then
        assertAll(
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        eq("U2"),
                        eq(InteractivityErrorType.NOT_OWNER_CHANGE.message())
                ),
                () -> verify(reviewInteractionEventPublisher, never()).publish(any())
        );
    }

    @Test
    void 리뷰_예약_변경_요청에서_예약이_없으면_예약_없음_알림을_보낸다() {
        // given
        JsonNode payload = objectMapper.createObjectNode()
                .put("trigger_id", "TRIGGER_1");
        JsonNode action = actionWithReservationId("999");

        // when
        reservationCommandWorkflow.handleChange(
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
                        eq("xoxb-test-token"),
                        eq("C1"),
                        eq("U1"),
                        eq(InteractivityErrorType.RESERVATION_NOT_FOUND.message())
                ),
                () -> verify(reviewInteractionEventPublisher, never()).publish(any()),
                () -> verify(notificationApiClient, never()).openModal(any(), any(), any())
        );
    }

    private JsonNode actionWithReservationId(String reservationId) {
        return objectMapper.createObjectNode()
                           .put("value", reservationId);
    }
}
