package com.slack.bot.application.review.participant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.review.dto.request.ReviewRequestEventRequest;
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
        ReviewRequestEventRequest request = new ReviewRequestEventRequest(
                "repo",
                "PR-1",
                1,
                "title",
                "url",
                "author-gh",
                List.of("reviewer-gh-1"),
                List.of()
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
        ReviewRequestEventRequest request = new ReviewRequestEventRequest(
                "repo",
                "PR-1",
                1,
                "title",
                "url",
                "author-gh",
                List.of("reviewer-gh-1", "unknown-reviewer"),
                List.of()
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
    void 리뷰어가_없으면_none으로_표시된다() {
        // given
        ReviewRequestEventRequest request = new ReviewRequestEventRequest(
                "repo",
                "PR-1",
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
