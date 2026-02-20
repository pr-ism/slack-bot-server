package com.slack.bot.application.interactivity.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.api.model.view.View;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interactivity.client.NotificationApiClient;
import com.slack.bot.application.interactivity.publisher.ReviewInteractionEvent;
import com.slack.bot.application.interactivity.publisher.ReviewReservationCancelEvent;
import com.slack.bot.application.interactivity.publisher.ReviewReservationChangeEvent;
import com.slack.bot.application.interactivity.reply.InteractivityErrorType;
import com.slack.bot.domain.reservation.ReservationStatus;
import com.slack.bot.domain.reservation.ReviewReservation;
import com.slack.bot.domain.reservation.repository.ReviewReservationRepository;
import java.time.Instant;
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
class ReservationCommandWorkflowTest {

    @Autowired
    ReservationCommandWorkflow reservationCommandWorkflow;

    @Autowired
    ReviewReservationRepository reviewReservationRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NotificationApiClient notificationApiClient;

    @Test
    void 예약_ID가_없으면_아무_동작도_하지_않는다(ApplicationEvents applicationEvents) {
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
                () -> assertThat(applicationEvents.stream(ReviewInteractionEvent.class).toList()).isEmpty()
        );
    }

    @Test
    void 예약_ID가_숫자가_아니면_아무_동작도_하지_않는다(ApplicationEvents applicationEvents) {
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
                () -> assertThat(applicationEvents.stream(ReviewInteractionEvent.class).toList()).isEmpty()
        );
    }

    @Test
    void 예약이_없으면_예약_없음_알림을_보내고_빈_값을_반환한다(ApplicationEvents applicationEvents) {
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
                () -> assertThat(applicationEvents.stream(ReviewInteractionEvent.class).toList()).isEmpty()
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql")
    void 리뷰_예약_취소_요청은_예약을_취소하고_취소_이벤트를_발행한다(ApplicationEvents applicationEvents) {
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

        assertAll(
                () -> assertThat(applicationEvents.stream(ReviewReservationCancelEvent.class).toList())
                        .singleElement()
                        .satisfies(event -> assertAll(
                                () -> assertThat(event.teamId()).isEqualTo("T1"),
                                () -> assertThat(event.channelId()).isEqualTo("C1"),
                                () -> assertThat(event.slackUserId()).isEqualTo("U1"),
                                () -> assertThat(event.reservationId()).isEqualTo(100L)
                        )),
                () -> verify(notificationApiClient, never()).sendEphemeralMessage(any(), any(), any(), any()),
                () -> assertThat(actual).isPresent(),
                () -> assertThat(actual.get().getId()).isEqualTo(100L),
                () -> assertThat(cancelled).isPresent(),
                () -> assertThat(cancelled.get().getStatus()).isEqualTo(ReservationStatus.CANCELLED)
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql")
    void 리뷰_예약_취소는_본인만_가능하다는_알림을_보낸다(ApplicationEvents applicationEvents) {
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
                () -> assertThat(applicationEvents.stream(ReviewInteractionEvent.class).toList()).isEmpty()
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql")
    void 이미_취소된_예약은_취소할_수_없다는_알림을_보낸다(ApplicationEvents applicationEvents) {
        // given
        JsonNode action = actionWithReservationId("100");
        ReviewReservation reservation = reviewReservationRepository.findById(100L).orElseThrow();
        reservation.markCancelled();
        reviewReservationRepository.save(reservation);

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
                        eq(InteractivityErrorType.RESERVATION_ALREADY_CANCELLED.message())
                ),
                () -> assertThat(applicationEvents.stream(ReviewInteractionEvent.class).toList()).isEmpty()
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql")
    void 이미_시작된_예약은_취소할_수_없다는_알림을_보낸다(ApplicationEvents applicationEvents) {
        // given
        JsonNode action = actionWithReservationId("100");
        ReviewReservation reservation = reviewReservationRepository.findById(100L).orElseThrow();
        reservation.reschedule(Instant.parse("2020-01-01T00:00:00Z"));
        reviewReservationRepository.save(reservation);

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
                        eq(InteractivityErrorType.RESERVATION_ALREADY_STARTED.message())
                ),
                () -> assertThat(applicationEvents.stream(ReviewInteractionEvent.class).toList()).isEmpty()
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql")
    void 리뷰_예약_변경_요청은_모달을_열고_변경_이벤트를_발행한다(ApplicationEvents applicationEvents) {
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
        assertAll(
                () -> assertThat(applicationEvents.stream(ReviewReservationChangeEvent.class).toList())
                        .singleElement()
                        .satisfies(event -> assertAll(
                                () -> assertThat(event.teamId()).isEqualTo("T1"),
                                () -> assertThat(event.channelId()).isEqualTo("C1"),
                                () -> assertThat(event.slackUserId()).isEqualTo("U1"),
                                () -> assertThat(event.reservationId()).isEqualTo(100L)
                        )),
                () -> verify(notificationApiClient).openModal(
                        eq("xoxb-test-token"),
                        eq("TRIGGER_1"),
                        any(View.class)
                )
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql")
    void 리뷰_예약_변경은_본인만_가능하다는_알림을_보낸다(ApplicationEvents applicationEvents) {
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
                () -> assertThat(applicationEvents.stream(ReviewInteractionEvent.class).toList()).isEmpty()
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql")
    void 이미_취소된_예약은_변경할_수_없다는_알림을_보낸다(ApplicationEvents applicationEvents) {
        // given
        JsonNode payload = objectMapper.createObjectNode()
                                       .put("trigger_id", "TRIGGER_1");
        JsonNode action = actionWithReservationId("100");
        ReviewReservation reservation = reviewReservationRepository.findById(100L).orElseThrow();
        reservation.markCancelled();
        reviewReservationRepository.save(reservation);

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
                        eq(InteractivityErrorType.RESERVATION_CHANGE_NOT_ALLOWED_CANCELLED.message())
                ),
                () -> verify(notificationApiClient, never()).openModal(any(), any(), any(View.class)),
                () -> assertThat(applicationEvents.stream(ReviewInteractionEvent.class).toList()).isEmpty()
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql")
    void 이미_시작된_예약은_변경할_수_없다는_알림을_보낸다(ApplicationEvents applicationEvents) {
        // given
        JsonNode payload = objectMapper.createObjectNode()
                                       .put("trigger_id", "TRIGGER_1");
        JsonNode action = actionWithReservationId("100");
        ReviewReservation reservation = reviewReservationRepository.findById(100L).orElseThrow();
        reservation.reschedule(Instant.parse("2020-01-01T00:00:00Z"));
        reviewReservationRepository.save(reservation);

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
                        eq(InteractivityErrorType.RESERVATION_ALREADY_STARTED.message())
                ),
                () -> verify(notificationApiClient, never()).openModal(any(), any(), any(View.class)),
                () -> assertThat(applicationEvents.stream(ReviewInteractionEvent.class).toList()).isEmpty()
        );
    }

    @Test
    void 리뷰_예약_변경_요청에서_예약이_없으면_예약_없음_알림을_보낸다(ApplicationEvents applicationEvents) {
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
                () -> assertThat(applicationEvents.stream(ReviewInteractionEvent.class).toList()).isEmpty(),
                () -> verify(notificationApiClient, never()).openModal(any(), any(), any(View.class))
        );
    }

    private JsonNode actionWithReservationId(String reservationId) {
        return objectMapper.createObjectNode()
                           .put("value", reservationId);
    }
}
