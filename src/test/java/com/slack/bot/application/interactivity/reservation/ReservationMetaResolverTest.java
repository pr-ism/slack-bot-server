package com.slack.bot.application.interactivity.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interactivity.dto.ReviewScheduleMetaDto;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReservationMetaResolverTest {

    @Autowired
    ReservationMetaResolver reservationMetaResolver;

    @Test
    void 예약_관련_메타데이터를_정상적으로_파싱한다() {
        // given
        String metaJson = """
                {
                  "team_id": "T1",
                  "channel_id": "C1",
                  "project_id": "123",
                  "pull_request_id": 10,
                  "pull_request_number": 10,
                  "pull_request_title": "PR 제목",
                  "pull_request_url": "https://github.com/org/repo/pull/10",
                  "author_github_id": "author-gh",
                  "author_slack_id": "U_AUTHOR",
                  "reservation_id": "R1"
                }
                """;

        // when
        ReviewScheduleMetaDto actual = reservationMetaResolver.parseMeta(metaJson);

        // then
        assertAll(
                () -> assertThat(actual.teamId()).isEqualTo("T1"),
                () -> assertThat(actual.channelId()).isEqualTo("C1"),
                () -> assertThat(actual.projectId()).isEqualTo("123"),
                () -> assertThat(actual.pullRequestId()).isEqualTo(10L),
                () -> assertThat(actual.pullRequestNumber()).isEqualTo(10),
                () -> assertThat(actual.pullRequestTitle()).isEqualTo("PR 제목"),
                () -> assertThat(actual.pullRequestUrl()).isEqualTo("https://github.com/org/repo/pull/10"),
                () -> assertThat(actual.authorGithubId()).isEqualTo("author-gh"),
                () -> assertThat(actual.authorSlackId()).isEqualTo("U_AUTHOR"),
                () -> assertThat(actual.reservationId()).isEqualTo("R1")
        );
    }

    @Test
    void 값이_공백이면_null로_파싱한다() {
        // given
        String metaJson = """
                {
                  "team_id": "T1",
                  "channel_id": "C1",
                  "project_id": "123",
                  "pull_request_id": 10,
                  "pull_request_number": 10,
                  "pull_request_title": "PR 제목",
                  "pull_request_url": "https://github.com/org/repo/pull/10",
                  "author_github_id": " ",
                  "author_slack_id": "U_AUTHOR",
                  "reservation_id": "R1"
                }
                """;

        // when
        ReviewScheduleMetaDto actual = reservationMetaResolver.parseMeta(metaJson);

        // then
        assertAll(
                () -> assertThat(actual.authorGithubId()).isNull(),
                () -> assertThat(actual.authorSlackId()).isEqualTo("U_AUTHOR")
        );
    }

    @Test
    void pull_request_number가_없으면_예외를_던진다() {
        // given
        String metaJson = """
                {
                  "team_id": "T1",
                  "channel_id": "C1",
                  "project_id": "123",
                  "pull_request_id": 10,
                  "pull_request_title": "PR 제목",
                  "pull_request_url": "https://github.com/org/repo/pull/10",
                  "author_github_id": "author-gh",
                  "author_slack_id": "U_AUTHOR",
                  "reservation_id": "R1"
                }
                """;

        // when & then
        assertThatThrownBy(() -> reservationMetaResolver.parseMeta(metaJson))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pull_request_number는 비어 있을 수 없습니다.");
    }

    @Test
    void 풀_리퀘스트_ID가_없어도_파싱한다() {
        // given
        String metaJson = """
                {
                  "team_id": "T1",
                  "channel_id": "C1",
                  "project_id": "123",
                  "pull_request_number": 10,
                  "pull_request_title": "PR 제목",
                  "pull_request_url": "https://github.com/org/repo/pull/10",
                  "author_github_id": "author-gh",
                  "author_slack_id": "U_AUTHOR",
                  "reservation_id": "R1"
                }
                """;

        // when
        ReviewScheduleMetaDto actual = reservationMetaResolver.parseMeta(metaJson);

        // then
        assertAll(
                () -> assertThat(actual.pullRequestId()).isNull(),
                () -> assertThat(actual.pullRequestNumber()).isEqualTo(10)
        );
    }

    @Test
    void 잘못된_메타는_예외를_던진다() {
        // given
        String invalidMeta = "{invalid-json";

        // when & then
        assertThatThrownBy(() -> reservationMetaResolver.parseMeta(invalidMeta))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("메타데이터 파싱 실패");
    }
}
