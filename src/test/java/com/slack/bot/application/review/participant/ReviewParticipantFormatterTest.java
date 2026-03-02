package com.slack.bot.application.review.participant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.review.dto.ReviewNotificationPayload;
import com.slack.bot.application.review.participant.dto.ReviewParticipantsDto;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewParticipantFormatterTest {

    @Autowired
    ReviewParticipantFormatter formatter;

    @Test
    @Sql(scripts = "classpath:sql/fixtures/review/project_member_t1_mapped.sql")
    void 매핑된_사용자는_슬랙_멘션으로_변환된다() {
        // given
        ReviewNotificationPayload request = new ReviewNotificationPayload(
                "repo",
                1L,
                1,
                "title",
                "url",
                "author-gh",
                List.of("reviewer-gh-1"),
                List.of("reviewer-gh-1")
        );

        // when
        ReviewParticipantsDto result = formatter.format("T1", request);

        // then
        assertAll(
                () -> assertThat(result.authorText()).isEqualTo("<@U1>"),
                () -> assertThat(result.pendingReviewersText()).isEqualTo("<@U2>"),
                () -> assertThat(result.unmappedGithubIds()).isEmpty()
        );
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/review/project_member_t1_mapped.sql")
    void 매핑되지_않은_사용자는_GitHub_ID로_표시되고_unmapped_목록에_포함된다() {
        // given
        ReviewNotificationPayload request = new ReviewNotificationPayload(
                "repo",
                1L,
                1,
                "title",
                "url",
                "author-gh",
                List.of("reviewer-gh-1", "unknown-reviewer"),
                List.of("reviewer-gh-1", "unknown-reviewer")
        );

        // when
        ReviewParticipantsDto result = formatter.format("T1", request);

        // then
        assertAll(
                () -> assertThat(result.pendingReviewersText()).contains("<@U2>", "unknown-reviewer"),
                () -> assertThat(result.unmappedGithubIds()).containsExactly("unknown-reviewer")
        );
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/review/project_member_t1_mapped_two_reviewers.sql")
    void 멘션_대상에_없는_매핑_리뷰어는_display_name_평문으로_표시된다() {
        // given
        ReviewNotificationPayload request = new ReviewNotificationPayload(
                "repo",
                1L,
                1,
                "title",
                "url",
                "author-gh",
                List.of("reviewer-gh-1", "reviewer-gh-2"),
                List.of("reviewer-gh-1")
        );

        // when
        ReviewParticipantsDto result = formatter.format("T1", request);

        // then
        assertAll(
                () -> assertThat(result.pendingReviewersText()).isEqualTo("<@U2>, 리뷰어2"),
                () -> assertThat(result.unmappedGithubIds()).isEmpty()
        );
    }

    @Test
    void 리뷰어가_없으면_none으로_표시된다() {
        // given
        ReviewNotificationPayload request = new ReviewNotificationPayload(
                "repo",
                1L,
                1,
                "title",
                "url",
                "some-author",
                List.of(),
                List.of()
        );

        // when
        ReviewParticipantsDto result = formatter.format("T1", request);

        // then
        assertThat(result.pendingReviewersText()).isEqualTo("(none)");
    }
}
