package com.slack.bot.application.interactivity.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.domain.reservation.ReservationStatus;
import com.slack.bot.domain.reservation.ReviewReservation;
import com.slack.bot.domain.reservation.vo.ReservationPullRequest;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewScheduleMetaBuilderTest {

    private ObjectMapper objectMapper;
    private ReviewScheduleMetaBuilder metaBuilder;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        metaBuilder = new ReviewScheduleMetaBuilder(objectMapper);
    }

    @Test
    void 리뷰_예약_정보를_JSON_메타데이터로_변환한다() throws Exception {
        // given
        ReservationPullRequest pullRequest = ReservationPullRequest.builder()
                .pullRequestId(100L)
                .pullRequestNumber(42)
                .pullRequestTitle("Fix bug")
                .pullRequestUrl("https://github.com/org/repo/pull/42")
                .build();

        ReviewReservation reservation = ReviewReservation.builder()
                .teamId("T123")
                .channelId("C456")
                .projectId(10L)
                .reservationPullRequest(pullRequest)
                .authorSlackId("U789")
                .reviewerSlackId("U012")
                .scheduledAt(Instant.parse("2024-02-01T10:00:00Z"))
                .status(ReservationStatus.ACTIVE)
                .build();

        // when
        String actual = metaBuilder.buildForChange(reservation);

        // then
        JsonNode node = objectMapper.readTree(actual);

        assertAll(
                () -> assertThat(node.get("team_id").asText()).isEqualTo("T123"),
                () -> assertThat(node.get("channel_id").asText()).isEqualTo("C456"),
                () -> assertThat(node.get("project_id").asLong()).isEqualTo(10L),
                () -> assertThat(node.get("pull_request_id").asLong()).isEqualTo(100L),
                () -> assertThat(node.get("pull_request_number").asInt()).isEqualTo(42),
                () -> assertThat(node.get("pull_request_title").asText()).isEqualTo("Fix bug"),
                () -> assertThat(node.get("pull_request_url").asText()).isEqualTo("https://github.com/org/repo/pull/42"),
                () -> assertThat(node.get("author_slack_id").asText()).isEqualTo("U789"),
                () -> assertThat(node.get("reservation_id").isNull()).isTrue()
        );
    }

    @Test
    void 모든_필드가_JSON에_포함된다() throws Exception {
        // given
        ReservationPullRequest pullRequest = ReservationPullRequest.builder()
                .pullRequestId(1L)
                .pullRequestNumber(1)
                .pullRequestTitle("Title")
                .pullRequestUrl("https://example.com")
                .build();

        ReviewReservation reservation = ReviewReservation.builder()
                .teamId("T1")
                .channelId("C1")
                .projectId(1L)
                .reservationPullRequest(pullRequest)
                .authorSlackId("U1")
                .reviewerSlackId("U2")
                .scheduledAt(Instant.now())
                .status(ReservationStatus.ACTIVE)
                .build();

        // when
        String actual = metaBuilder.buildForChange(reservation);

        // then
        JsonNode node = objectMapper.readTree(actual);

        assertAll(
                () -> assertThat(node.has("team_id")).isTrue(),
                () -> assertThat(node.has("channel_id")).isTrue(),
                () -> assertThat(node.has("project_id")).isTrue(),
                () -> assertThat(node.has("pull_request_id")).isTrue(),
                () -> assertThat(node.has("pull_request_number")).isTrue(),
                () -> assertThat(node.has("pull_request_title")).isTrue(),
                () -> assertThat(node.has("pull_request_url")).isTrue(),
                () -> assertThat(node.has("author_slack_id")).isTrue(),
                () -> assertThat(node.has("reservation_id")).isTrue()
        );
    }
}
