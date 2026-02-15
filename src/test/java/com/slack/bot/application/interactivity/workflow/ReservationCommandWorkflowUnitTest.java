package com.slack.bot.application.interactivity.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interactivity.client.NotificationApiClient;
import com.slack.bot.application.interactivity.publisher.ReviewInteractionEventPublisher;
import com.slack.bot.application.interactivity.publisher.ReviewReservationCancelEvent;
import com.slack.bot.application.interactivity.reply.SlackActionErrorNotifier;
import com.slack.bot.application.interactivity.reservation.ReviewReservationCoordinator;
import com.slack.bot.application.interactivity.reservation.ReviewScheduleMetaBuilder;
import com.slack.bot.application.interactivity.view.factory.ReviewReservationTimeViewFactory;
import com.slack.bot.domain.reservation.ReservationStatus;
import com.slack.bot.domain.reservation.ReviewReservation;
import com.slack.bot.domain.reservation.vo.ReservationPullRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReservationCommandWorkflowUnitTest {

    @Mock
    NotificationApiClient slackApiClient;

    @Mock
    SlackActionErrorNotifier errorNotifier;

    @Mock
    ReviewReservationTimeViewFactory slackViews;

    @Mock
    ReviewScheduleMetaBuilder reviewScheduleMetaBuilder;

    @Mock
    ReviewReservationCoordinator reviewReservationCoordinator;

    @Mock
    ReviewInteractionEventPublisher reviewInteractionEventPublisher;

    @Test
    void 취소에_성공하면_취소_이벤트를_발행하고_취소된_예약을_반환한다() {
        // given
        Clock clock = Clock.fixed(Instant.parse("2026-02-15T00:00:00Z"), ZoneOffset.UTC);
        ReservationCommandWorkflow workflow = new ReservationCommandWorkflow(
                clock,
                slackApiClient,
                errorNotifier,
                slackViews,
                reviewScheduleMetaBuilder,
                reviewReservationCoordinator,
                reviewInteractionEventPublisher
        );
        ReviewReservation reservation = activeReservation();
        JsonNode action = actionWithReservationId("100");

        when(reviewReservationCoordinator.findById(100L)).thenReturn(Optional.of(reservation));
        when(reviewReservationCoordinator.cancel(100L)).thenReturn(Optional.of(reservation));

        // when
        Optional<ReviewReservation> actual = workflow.handleCancel(
                action,
                "T1",
                "C1",
                "U1",
                "xoxb-test-token"
        );

        // then
        assertAll(
                () -> assertThat(actual).contains(reservation),
                () -> verify(reviewInteractionEventPublisher).publish(any(ReviewReservationCancelEvent.class)),
                () -> verify(errorNotifier, never()).notify(any(), any(), any(), any())
        );
    }

    @Test
    void 취소에_실패하면_원본_예약을_반환하고_취소_이벤트를_발행하지_않는다() {
        // given
        Clock clock = Clock.fixed(Instant.parse("2026-02-15T00:00:00Z"), ZoneOffset.UTC);
        ReservationCommandWorkflow workflow = new ReservationCommandWorkflow(
                clock,
                slackApiClient,
                errorNotifier,
                slackViews,
                reviewScheduleMetaBuilder,
                reviewReservationCoordinator,
                reviewInteractionEventPublisher
        );
        ReviewReservation reservation = activeReservation();
        JsonNode action = actionWithReservationId("100");

        when(reviewReservationCoordinator.findById(100L)).thenReturn(Optional.of(reservation));
        when(reviewReservationCoordinator.cancel(100L)).thenReturn(Optional.empty());

        // when
        Optional<ReviewReservation> actual = workflow.handleCancel(
                action,
                "T1",
                "C1",
                "U1",
                "xoxb-test-token"
        );

        // then
        assertAll(
                () -> assertThat(actual).contains(reservation),
                () -> verify(reviewInteractionEventPublisher, never()).publish(any(ReviewReservationCancelEvent.class)),
                () -> verify(errorNotifier, never()).notify(any(), any(), any(), any())
        );
    }

    private JsonNode actionWithReservationId(String reservationId) {
        return new ObjectMapper().createObjectNode()
                                 .put("value", reservationId);
    }

    private ReviewReservation activeReservation() {
        return ReviewReservation.builder()
                                .teamId("T1")
                                .channelId("C1")
                                .projectId(123L)
                                .reservationPullRequest(
                                        ReservationPullRequest.builder()
                                                              .pullRequestId(10L)
                                                              .pullRequestNumber(1)
                                                              .pullRequestTitle("title")
                                                              .pullRequestUrl("https://example.com/pr/1")
                                                              .build()
                                )
                                .authorSlackId("U2")
                                .reviewerSlackId("U1")
                                .scheduledAt(Instant.parse("2099-01-01T10:00:00Z"))
                                .status(ReservationStatus.ACTIVE)
                                .build();
    }
}
