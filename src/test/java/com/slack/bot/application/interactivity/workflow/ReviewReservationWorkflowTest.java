package com.slack.bot.application.interactivity.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interactivity.client.NotificationApiClient;
import com.slack.bot.application.interactivity.dto.ReviewScheduleMetaDto;
import com.slack.bot.application.interactivity.publisher.ReviewInteractionEvent;
import com.slack.bot.application.interactivity.publisher.ReviewReservationScheduledEvent;
import com.slack.bot.application.interactivity.reply.InteractivityErrorType;
import com.slack.bot.application.interactivity.reply.dto.response.SlackActionResponse;
import com.slack.bot.application.interactivity.reservation.ReviewReservationCoordinator;
import com.slack.bot.application.interactivity.reservation.exception.ActiveReservationAlreadyExistsException;
import com.slack.bot.application.interactivity.reservation.exception.ReservationScheduleInPastException;
import com.slack.bot.domain.reservation.ReservationStatus;
import com.slack.bot.domain.reservation.ReviewReservation;
import com.slack.bot.domain.reservation.vo.ReservationPullRequest;
import com.slack.bot.domain.reservation.repository.ReviewReservationRepository;
import java.time.Clock;
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
class ReviewReservationWorkflowTest {

    @Autowired
    Clock clock;

    @Autowired
    NotificationApiClient notificationApiClient;

    @Autowired
    ReviewReservationWorkflow reviewReservationWorkflow;

    @Autowired
    ReviewReservationRepository reviewReservationRepository;

    @Autowired
    ReviewReservationCoordinator reviewReservationCoordinator;

    @Test
    @Sql("classpath:sql/fixtures/interactivity/project_123.sql")
    void 신규_예약을_생성하면_clear_응답을_반환한다(ApplicationEvents applicationEvents) {
        // given
        ReviewScheduleMetaDto meta = meta("123", null);
        Instant scheduledAt = Instant.now().plusSeconds(3600);

        // when
        SlackActionResponse actual = reviewReservationWorkflow.reserveReview(meta, "U1", "xoxb-test-token", scheduledAt);

        // then
        Optional<ReviewReservation> saved = reviewReservationRepository.findActive("T1", 123L, "U1");

        assertAll(
                () -> assertThat(actual).isEqualTo(SlackActionResponse.clear()),
                () -> assertThat(saved).isPresent(),
                () -> assertThat(saved.get().getReservationPullRequest().getPullRequestId()).isEqualTo(10L),
                () -> assertThat(saved.get().getReservationPullRequest().getPullRequestTitle()).isEqualTo("PR 제목"),
                () -> assertThat(applicationEvents.stream(ReviewReservationScheduledEvent.class).toList())
                        .singleElement()
                        .satisfies(event -> assertAll(
                                () -> assertThat(event.teamId()).isEqualTo("T1"),
                                () -> assertThat(event.channelId()).isEqualTo("C1"),
                                () -> assertThat(event.slackUserId()).isEqualTo("U1"),
                                () -> assertThat(event.projectId()).isEqualTo(123L),
                                () -> assertThat(event.pullRequestId()).isEqualTo(10L)
                        ))
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/interactivity/project_123.sql",
            "classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql"
    })
    void 중복된_신규_예약이면_빈_응답을_반환한다(ApplicationEvents applicationEvents) {
        // given
        ReviewScheduleMetaDto meta = meta("123", null);
        Instant scheduledAt = Instant.now().plusSeconds(3600);

        // when
        SlackActionResponse actual = reviewReservationWorkflow.reserveReview(meta, "U1", "xoxb-test-token", scheduledAt);

        // then
        Optional<ReviewReservation> active = reviewReservationRepository.findById(100L);

        assertAll(
                () -> assertThat(actual).isEqualTo(SlackActionResponse.empty()),
                () -> assertThat(active).isPresent(),
                () -> assertThat(active.get().getId()).isEqualTo(100L),
                () -> assertThat(applicationEvents.stream(ReviewInteractionEvent.class).toList()).isEmpty()
        );
    }

    @Test
    void 예약_처리에_실패하면_에러_알림을_보내고_빈_응답을_반환한다(ApplicationEvents applicationEvents) {
        // given
        ReviewScheduleMetaDto meta = meta("999", null);
        Instant scheduledAt = Instant.now().plusSeconds(3600);

        // when
        SlackActionResponse actual = reviewReservationWorkflow.reserveReview(meta, "U1", "xoxb-test-token", scheduledAt);

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo(SlackActionResponse.empty()),
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        eq("U1"),
                        eq(InteractivityErrorType.RESERVATION_FAILURE.message())
                ),
                () -> assertThat(applicationEvents.stream(ReviewInteractionEvent.class).toList()).isEmpty()
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/interactivity/project_123.sql")
    void reservation_ID가_숫자가_아니면_신규_예약으로_처리한다(ApplicationEvents applicationEvents) {
        // given
        ReviewScheduleMetaDto meta = meta("123", "abc");
        Instant scheduledAt = Instant.now().plusSeconds(3600);

        // when
        SlackActionResponse actual = reviewReservationWorkflow.reserveReview(meta, "U1", "xoxb-test-token", scheduledAt);

        // then
        Optional<ReviewReservation> saved = reviewReservationRepository.findActive("T1", 123L, "U1");

        assertAll(
                () -> assertThat(actual).isEqualTo(SlackActionResponse.clear()),
                () -> assertThat(saved).isPresent(),
                () -> assertThat(saved.get().getId()).isNotNull(),
                () -> assertThat(applicationEvents.stream(ReviewReservationScheduledEvent.class).toList())
                        .singleElement()
                        .satisfies(event -> assertAll(
                                () -> assertThat(event.teamId()).isEqualTo("T1"),
                                () -> assertThat(event.channelId()).isEqualTo("C1"),
                                () -> assertThat(event.slackUserId()).isEqualTo("U1"),
                                () -> assertThat(event.projectId()).isEqualTo(123L),
                                () -> assertThat(event.pullRequestId()).isEqualTo(10L)
                        ))
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/interactivity/project_123.sql",
            "classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql"
    })
    void 예약_ID가_있으면_기존_예약을_변경한다(ApplicationEvents applicationEvents) {
        // given
        ReviewScheduleMetaDto meta = meta("123", "100");
        Instant scheduledAt = Instant.parse("2099-01-01T10:00:00Z");

        // when
        SlackActionResponse actual = reviewReservationWorkflow.reserveReview(meta, "U1", "xoxb-test-token", scheduledAt);

        // then
        Optional<ReviewReservation> changed = reviewReservationRepository.findById(100L);

        assertAll(
                () -> assertThat(actual).isEqualTo(SlackActionResponse.clear()),
                () -> assertThat(changed).isPresent(),
                () -> assertThat(changed.get().getScheduledAt()).isEqualTo(scheduledAt),
                () -> verify(notificationApiClient, never()).sendEphemeralMessage(any(), any(), any(), any()),
                () -> verify(notificationApiClient, never()).sendEphemeralBlockMessage(any(), any(), any(), any(), any()),
                () -> assertThat(applicationEvents.stream(ReviewReservationScheduledEvent.class).toList())
                        .singleElement()
                        .satisfies(event -> assertAll(
                                () -> assertThat(event.teamId()).isEqualTo("T1"),
                                () -> assertThat(event.channelId()).isEqualTo("C1"),
                                () -> assertThat(event.slackUserId()).isEqualTo("U1"),
                                () -> assertThat(event.reservationId()).isEqualTo(100L),
                                () -> assertThat(event.projectId()).isEqualTo(123L),
                                () -> assertThat(event.pullRequestId()).isEqualTo(10L)
                        ))
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/interactivity/project_123.sql",
            "classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql"
    })
    void 동시성_중복_예외가_발생하면_활성_예약을_조회해_중복_안내를_수행한다(ApplicationEvents applicationEvents) {
        // given
        ReviewScheduleMetaDto meta = meta("123", null);
        ReservationPullRequest reservationPullRequest = ReservationPullRequest.builder()
                                                                              .pullRequestId(10L)
                                                                              .pullRequestNumber(10)
                                                                              .pullRequestTitle("PR 제목")
                                                                              .pullRequestUrl("https://github.com/org/repo/pull/10")
                                                                              .build();
        ReviewReservation activeReservation = ReviewReservation.builder()
                                                               .teamId("T1")
                                                               .channelId("C1")
                                                               .projectId(123L)
                                                               .reservationPullRequest(reservationPullRequest)
                                                               .authorSlackId("U_AUTHOR")
                                                               .reviewerSlackId("U1")
                                                               .scheduledAt(Instant.now().plusSeconds(600))
                                                               .status(ReservationStatus.ACTIVE)
                                                               .build();

        doThrow(new ActiveReservationAlreadyExistsException("concurrency"))
                .doReturn(Optional.of(activeReservation))
                .when(reviewReservationCoordinator)
                .findActive(anyString(), anyLong(), anyString(), anyLong());

        // when
        SlackActionResponse actual = reviewReservationWorkflow.reserveReview(
                meta,
                "U1",
                "xoxb-test-token",
                Instant.now().plusSeconds(3600)
        );

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo(SlackActionResponse.empty()),
                () -> assertThat(applicationEvents.stream(ReviewInteractionEvent.class).toList()).isEmpty()
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/interactivity/project_123.sql")
    void 즉시_알림_분기를_통과한다(ApplicationEvents applicationEvents) {
        // given
        Instant nowForValidation = Instant.parse("2099-01-01T00:00:00Z");
        Instant scheduledAt = Instant.parse("2099-01-01T00:00:10Z");
        Instant nowForImmediate = Instant.parse("2099-01-01T00:00:20Z");
        ReviewScheduleMetaDto meta = meta("123", null);
        doReturn(nowForValidation, nowForImmediate).when(clock).instant();

        // when
        SlackActionResponse actual = reviewReservationWorkflow.reserveReview(meta, "U1", "xoxb-test-token", scheduledAt);

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo(SlackActionResponse.clear()),
                () -> assertThat(applicationEvents.stream(ReviewReservationScheduledEvent.class).toList())
                        .singleElement()
                        .satisfies(event -> assertAll(
                                () -> assertThat(event.teamId()).isEqualTo("T1"),
                                () -> assertThat(event.channelId()).isEqualTo("C1"),
                                () -> assertThat(event.slackUserId()).isEqualTo("U1"),
                                () -> assertThat(event.projectId()).isEqualTo(123L),
                                () -> assertThat(event.pullRequestId()).isEqualTo(10L)
                        ))
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/interactivity/project_123.sql")
    void 예약_시간이_과거로_판정되면_모달_에러로_메시지를_반환한다(ApplicationEvents applicationEvents) {
        // given
        ReviewScheduleMetaDto meta = meta("123", null);
        Instant scheduledAt = Instant.now(clock).plusSeconds(3600);
        doThrow(new ReservationScheduleInPastException("리뷰 예약 시간은 현재보다 이후여야 합니다."))
                .when(reviewReservationCoordinator)
                .create(any());

        // when
        SlackActionResponse actual = reviewReservationWorkflow.reserveReview(
                meta,
                "U1",
                "xoxb-test-token",
                scheduledAt
        );

        // then
        assertAll(
                () -> assertThat(actual.responseAction()).isEqualTo("errors"),
                () -> assertThat(actual.errors()).containsEntry("time_block", "리뷰 예약 시간은 현재보다 이후여야 합니다."),
                () -> assertThat(applicationEvents.stream(ReviewInteractionEvent.class).toList()).isEmpty()
        );
    }

    private ReviewScheduleMetaDto meta(String projectId, String reservationId) {
        return ReviewScheduleMetaDto.builder()
                                    .teamId("T1")
                                    .channelId("C1")
                                    .pullRequestId(10L)
                                    .pullRequestNumber(10)
                                    .pullRequestTitle("PR 제목")
                                    .pullRequestUrl("https://github.com/org/repo/pull/10")
                                    .authorGithubId("author-gh")
                                    .authorSlackId("U_AUTHOR")
                                    .reservationId(reservationId)
                                    .projectId(projectId)
                                    .build();
    }
}
