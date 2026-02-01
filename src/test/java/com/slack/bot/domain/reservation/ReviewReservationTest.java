package com.slack.bot.domain.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.slack.bot.domain.reservation.vo.ReservationPullRequest;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewReservationTest {

    @Test
    void 리뷰_예약을_초기화한다() {
        // given
        ReservationPullRequest pullRequest = createValidPullRequest();
        Instant now = Instant.now();

        // when
        ReviewReservation reservation = assertDoesNotThrow(
                () -> ReviewReservation.builder()
                                       .teamId("T1")
                                       .channelId("C1")
                                       .projectId(1L)
                                       .reservationPullRequest(pullRequest)
                                       .authorSlackId("U1")
                                       .reviewerSlackId("U2")
                                       .when(now)
                                       .status(ReservationStatus.ACTIVE)
                                       .build()
        );

        // then
        assertAll(
                () -> assertThat(reservation.getTeamId()).isEqualTo("T1"),
                () -> assertThat(reservation.getChannelId()).isEqualTo("C1"),
                () -> assertThat(reservation.getProjectId()).isEqualTo(1L),
                () -> assertThat(reservation.getReservationPullRequest()).isEqualTo(pullRequest),
                () -> assertThat(reservation.getAuthorSlackId()).isEqualTo("U1"),
                () -> assertThat(reservation.getReviewerSlackId()).isEqualTo("U2"),
                () -> assertThat(reservation.getWhen()).isEqualTo(now),
                () -> assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE)
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void Team_ID가_비어_있으면_초기화할_수_없다(String teamId) {
        // when & then
        assertThatThrownBy(() -> ReviewReservation.builder()
                                                  .teamId(teamId)
                                                  .channelId("C1")
                                                  .projectId(1L)
                                                  .reservationPullRequest(createValidPullRequest())
                                                  .authorSlackId("U1")
                                                  .reviewerSlackId("U2")
                                                  .when(Instant.now())
                                                  .status(ReservationStatus.ACTIVE)
                                                  .build()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("teamId는 비어 있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void Channel_ID가_비어_있으면_초기화할_수_없다(String channelId) {
        // when & then
        assertThatThrownBy(() -> ReviewReservation.builder()
                                                  .teamId("T1")
                                                  .channelId(channelId)
                                                  .projectId(1L)
                                                  .reservationPullRequest(createValidPullRequest())
                                                  .authorSlackId("U1")
                                                  .reviewerSlackId("U2")
                                                  .when(Instant.now())
                                                  .status(ReservationStatus.ACTIVE)
                                                  .build()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("channelId는 비어 있을 수 없습니다.");
    }

    @Test
    void Project_ID가_없으면_초기화할_수_없다() {
        // when & then
        assertThatThrownBy(() -> ReviewReservation.builder()
                                                  .teamId("T1")
                                                  .channelId("C1")
                                                  .projectId(null)
                                                  .reservationPullRequest(createValidPullRequest())
                                                  .authorSlackId("U1")
                                                  .reviewerSlackId("U2")
                                                  .when(Instant.now())
                                                  .status(ReservationStatus.ACTIVE)
                                                  .build()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("projectId는 비어 있을 수 없습니다.");
    }

    @Test
    void 예약_대상_PR_정보가_없으면_초기화할_수_없다() {
        // when & then
        assertThatThrownBy(() -> ReviewReservation.builder()
                                                  .teamId("T1")
                                                  .channelId("C1")
                                                  .projectId(1L)
                                                  .reservationPullRequest(null)
                                                  .authorSlackId("U1")
                                                  .reviewerSlackId("U2")
                                                  .when(Instant.now())
                                                  .status(ReservationStatus.ACTIVE)
                                                  .build()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("예약 대상 PR 정보는 비어 있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 요청자_Slack_ID가_비어_있으면_초기화할_수_없다(String authorSlackId) {
        // when & then
        assertThatThrownBy(() -> ReviewReservation.builder()
                                                  .teamId("T1")
                                                  .channelId("C1")
                                                  .projectId(1L)
                                                  .reservationPullRequest(createValidPullRequest())
                                                  .authorSlackId(authorSlackId)
                                                  .reviewerSlackId("U2")
                                                  .when(Instant.now())
                                                  .status(ReservationStatus.ACTIVE)
                                                  .build()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("authorSlackId는 비어 있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 리뷰어_Slack_ID가_비어_있으면_초기화할_수_없다(String reviewerSlackId) {
        // when & then
        assertThatThrownBy(() -> ReviewReservation.builder()
                                                  .teamId("T1")
                                                  .channelId("C1")
                                                  .projectId(1L)
                                                  .reservationPullRequest(createValidPullRequest())
                                                  .authorSlackId("U1")
                                                  .reviewerSlackId(reviewerSlackId)
                                                  .when(Instant.now())
                                                  .status(ReservationStatus.ACTIVE)
                                                  .build()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("reviewerSlackId는 비어 있을 수 없습니다.");
    }

    @Test
    void 리뷰_예약_시간이_없으면_초기화할_수_없다() {
        // when & then
        assertThatThrownBy(() -> ReviewReservation.builder()
                                                  .teamId("T1")
                                                  .channelId("C1")
                                                  .projectId(1L)
                                                  .reservationPullRequest(createValidPullRequest())
                                                  .authorSlackId("U1")
                                                  .reviewerSlackId("U2")
                                                  .when(null)
                                                  .status(ReservationStatus.ACTIVE)
                                                  .build()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("리뷰 예약 시간은 비어 있을 수 없습니다.");
    }

    @Test
    void 예약_상태가_없으면_초기화할_수_없다() {
        // when & then
        assertThatThrownBy(() -> ReviewReservation.builder()
                                                  .teamId("T1")
                                                  .channelId("C1")
                                                  .projectId(1L)
                                                  .reservationPullRequest(createValidPullRequest())
                                                  .authorSlackId("U1")
                                                  .reviewerSlackId("U2")
                                                  .when(Instant.now())
                                                  .status(null)
                                                  .build()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("예약 상태는 비어 있을 수 없습니다.");
    }

    @Test
    void 활성_상태_여부를_확인한다() {
        // given
        ReviewReservation activeReservation = ReviewReservation.builder()
                                                               .teamId("T1")
                                                               .channelId("C1")
                                                               .projectId(1L)
                                                               .reservationPullRequest(createValidPullRequest())
                                                               .authorSlackId("U1")
                                                               .reviewerSlackId("U2")
                                                               .when(Instant.now())
                                                               .status(ReservationStatus.ACTIVE)
                                                               .build();

        ReviewReservation cancelledReservation = ReviewReservation.builder()
                                                                  .teamId("T1")
                                                                  .channelId("C1")
                                                                  .projectId(1L)
                                                                  .reservationPullRequest(createValidPullRequest())
                                                                  .authorSlackId("U1")
                                                                  .reviewerSlackId("U2")
                                                                  .when(Instant.now())
                                                                  .status(ReservationStatus.CANCELLED)
                                                                  .build();

        // when & then
        assertAll(
                () -> assertThat(activeReservation.isActive()).isTrue(),
                () -> assertThat(cancelledReservation.isActive()).isFalse()
        );
    }

    @Test
    void 예약을_취소_처리한다() {
        // given
        ReviewReservation reservation = ReviewReservation.builder()
                                                         .teamId("T1")
                                                         .channelId("C1")
                                                         .projectId(1L)
                                                         .reservationPullRequest(createValidPullRequest())
                                                         .authorSlackId("U1")
                                                         .reviewerSlackId("U2")
                                                         .when(Instant.now())
                                                         .status(ReservationStatus.ACTIVE)
                                                         .build();

        // when
        reservation.markCancelled();

        // then
        assertAll(
                () -> assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED),
                () -> assertThat(reservation.isActive()).isFalse()
        );
    }

    private ReservationPullRequest createValidPullRequest() {
        return ReservationPullRequest.builder()
                .pullRequestId(1L)
                .pullRequestNumber(123)
                .pullRequestTitle("feat: 기능 추가")
                .pullRequestUrl("https://github.com/org/repo/pull/123")
                .build();
    }
}
