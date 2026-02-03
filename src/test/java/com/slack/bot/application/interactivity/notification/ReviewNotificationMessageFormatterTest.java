package com.slack.bot.application.interactivity.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.slack.bot.application.interactivity.dto.ReviewScheduleMetaDto;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewNotificationMessageFormatterTest {

    private ReviewNotificationMessageFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new ReviewNotificationMessageFormatter();
    }

    @Test
    void 리뷰_예약_시간까지_남은_시간이_0분_이하일_때_곧을_반환한다() {
        // when
        String actual = formatter.formatDelay(0);

        // then
        assertThat(actual).isEqualTo("곧 ");
    }

    @Test
    void 리뷰_예약_시간까지_남은_시간이_음수일_때_곧을_반환한다() {
        // when
        String actual = formatter.formatDelay(-10);

        // then
        assertThat(actual).isEqualTo("곧 ");
    }

    @Test
    void 리뷰_예약_시간까지_남은_시간이_60분_미만일_때_분_단위로_반환한다() {
        // when
        String actual = formatter.formatDelay(30);

        // then
        assertThat(actual).isEqualTo("30분 뒤");
    }

    @Test
    void 리뷰_예약_시간까지_남은_시간이_1시간일_때_시간_단위로_반환한다() {
        // when
        String actual = formatter.formatDelay(60);

        // then
        assertThat(actual).isEqualTo("1시간 뒤");
    }

    @Test
    void 리뷰_예약_시간까지_남은_시간이_24시간_미만일_때_시간_단위로_반환한다() {
        // when
        String actual = formatter.formatDelay(150);

        // then
        assertThat(actual).isEqualTo("2시간 뒤");
    }

    @Test
    void 리뷰_예약_시간까지_남은_시간이_24시간_이상일_때_일_시간_분_단위로_반환한다() {
        // when
        String actual = formatter.formatDelay(1500);

        // then
        assertThat(actual).isEqualTo("1일 1시간 0분 뒤");
    }

    @Test
    void 리뷰_예약_시간까지_남은_시간이_2일_3시간_30분일_때_정확히_반환한다() {
        // when
        String actual = formatter.formatDelay(2 * 24 * 60 + 3 * 60 + 30);

        // then
        assertThat(actual).isEqualTo("2일 3시간 30분 뒤");
    }

    @Test
    void 같은_날짜일_때_시각만_표시한다() {
        // given
        ZonedDateTime now = ZonedDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneId.of("UTC"));
        ZonedDateTime scheduledAt = ZonedDateTime.of(2024, 1, 15, 14, 45, 0, 0, ZoneId.of("UTC"));

        // when
        String actual = formatter.formatScheduledAtText(now, scheduledAt);

        // then
        assertThat(actual).isEqualTo("14시 45분");
    }

    @Test
    void 다른_날짜일_때_전체_날짜와_시각을_표시한다() {
        // given
        ZonedDateTime now = ZonedDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneId.of("UTC"));
        ZonedDateTime scheduledAt = ZonedDateTime.of(2024, 1, 16, 14, 45, 0, 0, ZoneId.of("UTC"));

        // when
        String actual = formatter.formatScheduledAtText(now, scheduledAt);

        // then
        assertThat(actual).isEqualTo("2024년 1월 16일 14시 45분");
    }

    @Test
    void pull_request_메타가_null일_때_빈_문자열을_반환한다() {
        // when
        String actual = formatter.formatPullRequestLine(null);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void pull_request_제목과_URL이_모두_있을_때_슬랙_링크_형식으로_반환한다() {
        // given
        ReviewScheduleMetaDto meta = ReviewScheduleMetaDto.builder()
                .teamId("T1")
                .channelId("C1")
                .pullRequestId(1L)
                .pullRequestNumber(42)
                .pullRequestTitle("Fix bug")
                .pullRequestUrl("https://github.com/org/repo/pull/42")
                .authorGithubId("author")
                .authorSlackId("U-AUTHOR")
                .reservationId("100")
                .projectId("1")
                .build();

        // when
        String actual = formatter.formatPullRequestLine(meta);

        // then
        assertThat(actual).isEqualTo("<https://github.com/org/repo/pull/42|Fix bug>");
    }

    @Test
    void pull_request_URL만_있을_때_URL을_반환한다() {
        // given
        ReviewScheduleMetaDto meta = ReviewScheduleMetaDto.builder()
                .teamId("T1")
                .channelId("C1")
                .pullRequestId(1L)
                .pullRequestNumber(42)
                .pullRequestTitle(null)
                .pullRequestUrl("https://github.com/org/repo/pull/42")
                .authorGithubId("author")
                .authorSlackId("U-AUTHOR")
                .reservationId("100")
                .projectId("1")
                .build();

        // when
        String actual = formatter.formatPullRequestLine(meta);

        // then
        assertThat(actual).isEqualTo("https://github.com/org/repo/pull/42");
    }

    @Test
    void pull_request_URL만_있고_제목이_빈문자일_때_URL을_반환한다() {
        // given
        ReviewScheduleMetaDto meta = ReviewScheduleMetaDto.builder()
                .teamId("T1")
                .channelId("C1")
                .pullRequestId(1L)
                .pullRequestNumber(42)
                .pullRequestTitle("")
                .pullRequestUrl("https://github.com/org/repo/pull/42")
                .authorGithubId("author")
                .authorSlackId("U-AUTHOR")
                .reservationId("100")
                .projectId("1")
                .build();

        // when
        String actual = formatter.formatPullRequestLine(meta);

        // then
        assertThat(actual).isEqualTo("https://github.com/org/repo/pull/42");
    }

    @Test
    void pull_request_URL이_없을_때_pull_request_번호를_반환한다() {
        // given
        ReviewScheduleMetaDto meta = ReviewScheduleMetaDto.builder()
                .teamId("T1")
                .channelId("C1")
                .pullRequestId(1L)
                .pullRequestNumber(42)
                .pullRequestTitle("Fix bug")
                .pullRequestUrl(null)
                .authorGithubId("author")
                .authorSlackId("U-AUTHOR")
                .reservationId("100")
                .projectId("1")
                .build();

        // when
        String actual = formatter.formatPullRequestLine(meta);

        // then
        assertThat(actual).isEqualTo("PR #42");
    }

    @Test
    void pull_request_URL이_빈문자일_때_pull_request_번호를_반환한다() {
        // given
        ReviewScheduleMetaDto meta = ReviewScheduleMetaDto.builder()
                .teamId("T1")
                .channelId("C1")
                .pullRequestId(1L)
                .pullRequestNumber(42)
                .pullRequestTitle("Fix bug")
                .pullRequestUrl("")
                .authorGithubId("author")
                .authorSlackId("U-AUTHOR")
                .reservationId("100")
                .projectId("1")
                .build();

        // when
        String actual = formatter.formatPullRequestLine(meta);

        // then
        assertThat(actual).isEqualTo("PR #42");
    }

    @Test
    void 즉시_시작_메시지를_정확히_생성한다() {
        // given
        ReviewScheduleMetaDto meta = ReviewScheduleMetaDto.builder()
                .teamId("T1")
                .channelId("C1")
                .pullRequestId(1L)
                .pullRequestNumber(42)
                .pullRequestTitle("Fix bug")
                .pullRequestUrl("https://github.com/org/repo/pull/42")
                .authorGithubId("author")
                .authorSlackId("U-AUTHOR")
                .reservationId("100")
                .projectId("1")
                .build();

        // when
        String actual = formatter.buildStartNowText("U-AUTHOR", "U-REVIEWER", meta);

        // then
        assertThat(actual).isEqualTo("<@U-AUTHOR> 지금부터 <@U-REVIEWER> 님이 리뷰를 시작합니다.\n<https://github.com/org/repo/pull/42|Fix bug>");
    }

    @Test
    void 예약_메시지를_정확히_생성한다() {
        // given
        ZonedDateTime now = ZonedDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneId.of("UTC"));
        ZonedDateTime scheduledAt = ZonedDateTime.of(2024, 1, 15, 11, 30, 0, 0, ZoneId.of("UTC"));
        ReviewScheduleMetaDto meta = ReviewScheduleMetaDto.builder()
                .teamId("T1")
                .channelId("C1")
                .pullRequestId(1L)
                .pullRequestNumber(42)
                .pullRequestTitle("Fix bug")
                .pullRequestUrl("https://github.com/org/repo/pull/42")
                .authorGithubId("author")
                .authorSlackId("U-AUTHOR")
                .reservationId("100")
                .projectId("1")
                .build();

        // when
        String actual = formatter.buildScheduledText("U-AUTHOR", "U-REVIEWER", now, scheduledAt, meta);

        // then
        assertThat(actual).isEqualTo("<@U-AUTHOR> <@U-REVIEWER> 님이 1시간 뒤(11시 30분)에 리뷰를 시작하겠습니다.\n<https://github.com/org/repo/pull/42|Fix bug>");
    }

    @Test
    void 예약_시간이_과거일_때_곧으로_표시한다() {
        // given
        ZonedDateTime now = ZonedDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneId.of("UTC"));
        ZonedDateTime scheduledAt = ZonedDateTime.of(2024, 1, 15, 9, 30, 0, 0, ZoneId.of("UTC"));
        ReviewScheduleMetaDto meta = ReviewScheduleMetaDto.builder()
                .teamId("T1")
                .channelId("C1")
                .pullRequestId(1L)
                .pullRequestNumber(42)
                .pullRequestTitle("Fix bug")
                .pullRequestUrl("https://github.com/org/repo/pull/42")
                .authorGithubId("author")
                .authorSlackId("U-AUTHOR")
                .reservationId("100")
                .projectId("1")
                .build();

        // when
        String actual = formatter.buildScheduledText("U-AUTHOR", "U-REVIEWER", now, scheduledAt, meta);

        // then
        assertThat(actual).contains("곧 ");
    }
}
