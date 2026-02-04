package com.slack.bot.application.interactivity.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interactivity.dto.ReviewScheduleMetaDto;
import com.slack.bot.application.interactivity.reservation.dto.ReservationContextDto;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AuthorResolverTest {

    @Autowired
    AuthorResolver authorResolver;

    @Test
    void authorSlackId가_존재하면_그대로_반환한다() {
        // given
        ReviewScheduleMetaDto meta = ReviewScheduleMetaDto.builder()
                .authorSlackId("U123")
                .teamId("T1")
                .authorGithubId("github-user")
                .build();

        // when
        String actual = authorResolver.resolveAuthorSlackId(meta);

        // then
        assertThat(actual).isEqualTo("U123");
    }

    @Test
    @Sql("classpath:sql/fixtures/reservation/project_member_team1_user1.sql")
    void authorSlackId가_공백이면_GitHub_조회를_시도한다() {
        // given
        ReviewScheduleMetaDto meta = ReviewScheduleMetaDto.builder()
                .authorSlackId("  ")
                .teamId("T1")
                .authorGithubId("git-1")
                .build();

        // when
        String actual = authorResolver.resolveAuthorSlackId(meta);

        // then
        assertThat(actual).isEqualTo("U1");
    }

    @Test
    @Sql("classpath:sql/fixtures/reservation/project_member_team1_user1.sql")
    void authorSlackId가_null이면_GitHub_조회를_시도한다() {
        // given
        ReviewScheduleMetaDto meta = ReviewScheduleMetaDto.builder()
                .authorSlackId(null)
                .teamId("T1")
                .authorGithubId("git-1")
                .build();

        // when
        String actual = authorResolver.resolveAuthorSlackId(meta);

        // then
        assertThat(actual).isEqualTo("U1");
    }

    @Test
    void teamId가_null이면_UNKNOWN을_반환한다() {
        // given
        ReviewScheduleMetaDto meta = ReviewScheduleMetaDto.builder()
                .authorSlackId(null)
                .teamId(null)
                .authorGithubId("github-user")
                .build();

        // when
        String actual = authorResolver.resolveAuthorSlackId(meta);

        // then
        assertThat(actual).isEqualTo(ReservationContextDto.UNKNOWN_AUTHOR_SLACK_ID);
    }

    @Test
    void authorGithubId가_null이면_UNKNOWN을_반환한다() {
        // given
        ReviewScheduleMetaDto meta = ReviewScheduleMetaDto.builder()
                .authorSlackId(null)
                .teamId("T1")
                .authorGithubId(null)
                .build();

        // when
        String actual = authorResolver.resolveAuthorSlackId(meta);

        // then
        assertThat(actual).isEqualTo(ReservationContextDto.UNKNOWN_AUTHOR_SLACK_ID);
    }

    @Test
    void GitHub_조회_결과가_없으면_UNKNOWN을_반환한다() {
        // given
        ReviewScheduleMetaDto meta = ReviewScheduleMetaDto.builder()
                .authorSlackId(null)
                .teamId("T1")
                .authorGithubId("unknown-user")
                .build();

        // when
        String actual = authorResolver.resolveAuthorSlackId(meta);

        // then
        assertThat(actual).isEqualTo(ReservationContextDto.UNKNOWN_AUTHOR_SLACK_ID);
    }
}
