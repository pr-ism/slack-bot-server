package com.slack.bot.application.interactivity.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interactivity.client.NotificationApiClient;
import com.slack.bot.application.interactivity.dto.ReviewScheduleMetaDto;
import com.slack.bot.application.interactivity.reply.InteractivityErrorType;
import com.slack.bot.application.interactivity.reply.dto.response.SlackActionResponse;
import com.slack.bot.application.interactivity.reservation.ReviewReservationCoordinator;
import com.slack.bot.application.interactivity.reservation.exception.ActiveReservationAlreadyExistsException;
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
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
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
    @Sql("classpath:sql/fixtures/reservation/project_123.sql")
    void 신규_예약을_생성하면_clear_응답을_반환한다() {
        // given
        ReviewScheduleMetaDto meta = meta("123", null);
        Instant scheduledAt = Instant.now().plusSeconds(3600);

        // when
        Object actual = reviewReservationWorkflow.reserveReview(meta, "U1", "xoxb-test-token", scheduledAt);

        // then
        Optional<ReviewReservation> saved = reviewReservationRepository.findActive("T1", 123L, "U1");

        assertAll(
                () -> assertThat(actual).isEqualTo(SlackActionResponse.clear()),
                () -> assertThat(saved).isPresent(),
                () -> assertThat(saved.get().getReservationPullRequest().getPullRequestId()).isEqualTo(10L),
                () -> assertThat(saved.get().getReservationPullRequest().getPullRequestTitle()).isEqualTo("PR 제목")
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/reservation/project_123.sql",
            "classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql"
    })
    void 중복된_신규_예약이면_빈_응답을_반환한다() {
        // given
        ReviewScheduleMetaDto meta = meta("123", null);
        Instant scheduledAt = Instant.now().plusSeconds(3600);

        // when
        Object actual = reviewReservationWorkflow.reserveReview(meta, "U1", "xoxb-test-token", scheduledAt);

        // then
        Optional<ReviewReservation> active = reviewReservationRepository.findById(100L);

        assertAll(
                () -> assertThat(actual).isEqualTo(SlackActionResponse.empty()),
                () -> assertThat(active).isPresent(),
                () -> assertThat(active.get().getId()).isEqualTo(100L)
        );
    }

    @Test
    void 예약_처리에_실패하면_에러_알림을_보내고_빈_응답을_반환한다() {
        // given
        ReviewScheduleMetaDto meta = meta("999", null);
        Instant scheduledAt = Instant.now().plusSeconds(3600);

        // when
        Object actual = reviewReservationWorkflow.reserveReview(meta, "U1", "xoxb-test-token", scheduledAt);

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo(SlackActionResponse.empty()),
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        eq("U1"),
                        eq(InteractivityErrorType.RESERVATION_FAILURE.message())
                )
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/reservation/project_123.sql")
    void reservation_ID가_숫자가_아니면_신규_예약으로_처리한다() {
        // given
        ReviewScheduleMetaDto meta = meta("123", "abc");
        Instant scheduledAt = Instant.now().plusSeconds(3600);

        // when
        Object actual = reviewReservationWorkflow.reserveReview(meta, "U1", "xoxb-test-token", scheduledAt);

        // then
        Optional<ReviewReservation> saved = reviewReservationRepository.findActive("T1", 123L, "U1");

        assertAll(
                () -> assertThat(actual).isEqualTo(SlackActionResponse.clear()),
                () -> assertThat(saved).isPresent(),
                () -> assertThat(saved.get().getId()).isNotNull()
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/reservation/project_123.sql",
            "classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql"
    })
    void 예약_ID가_있으면_기존_예약을_변경한다() {
        // given
        ReviewScheduleMetaDto meta = meta("123", "100");
        Instant scheduledAt = Instant.parse("2099-01-01T10:00:00Z");

        // when
        Object actual = reviewReservationWorkflow.reserveReview(meta, "U1", "xoxb-test-token", scheduledAt);

        // then
        Optional<ReviewReservation> changed = reviewReservationRepository.findById(100L);

        assertAll(
                () -> assertThat(actual).isEqualTo(SlackActionResponse.clear()),
                () -> assertThat(changed).isPresent(),
                () -> assertThat(changed.get().getScheduledAt()).isEqualTo(scheduledAt)
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/reservation/project_123.sql",
            "classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql"
    })
    void 동시성_중복_예외가_발생하면_활성_예약을_조회해_중복_안내를_수행한다() {
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
                .findActive(anyString(), anyLong(), anyString());

        // when
        Object actual = reviewReservationWorkflow.reserveReview(
                meta,
                "U1",
                "xoxb-test-token",
                Instant.now().plusSeconds(3600)
        );

        // then
        assertThat(actual).isEqualTo(SlackActionResponse.empty());
    }

    @Test
    @Sql("classpath:sql/fixtures/reservation/project_123.sql")
    void 즉시_알림_분기를_통과한다() {
        // given
        Instant nowForValidation = Instant.parse("2026-01-01T00:00:00Z");
        Instant scheduledAt = Instant.parse("2026-01-01T00:00:10Z");
        Instant nowForImmediate = Instant.parse("2026-01-01T00:00:20Z");
        ReviewScheduleMetaDto meta = meta("123", null);
        doReturn(nowForValidation, nowForImmediate).when(clock).instant();

        // when
        Object actual = reviewReservationWorkflow.reserveReview(meta, "U1", "xoxb-test-token", scheduledAt);

        // then
        assertThat(actual).isEqualTo(SlackActionResponse.clear());
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
