package com.slack.bot.application.review.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.review.dto.request.ReviewRequestEventRequest;
import com.slack.bot.application.review.meta.exception.ProjectNotFoundException;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewActionMetaBuilderTest {

    @Autowired
    ReviewActionMetaBuilder metaBuilder;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/review/project_t1.sql",
            "classpath:sql/fixtures/review/project_member_t1_mapped.sql"
    })
    void 메타데이터_JSON에_필수_필드가_포함된다() throws Exception {
        // given
        ReviewRequestEventRequest request = new ReviewRequestEventRequest(
                "my-repo",
                "PR-1",
                42,
                "Fix bug",
                "https://github.com/pr/1",
                "author-gh",
                List.of("reviewer-gh-1"),
                List.of()
        );

        // when
        String json = metaBuilder.build("T1", "C1", "test-api-key", request);

        // then
        JsonNode node = objectMapper.readTree(json);

        assertAll(
                () -> assertThat(node.get("team_id").asText()).isEqualTo("T1"),
                () -> assertThat(node.get("channel_id").asText()).isEqualTo("C1"),
                () -> assertThat(node.get("project_id").asLong()).isEqualTo(1L),
                () -> assertThat(node.get("pull_request_url").asText()).isEqualTo("https://github.com/pr/1"),
                () -> assertThat(node.get("repo").asText()).isEqualTo("my-repo"),
                () -> assertThat(node.get("author_github_id").asText()).isEqualTo("author-gh"),
                () -> assertThat(node.get("author_slack_id").asText()).isEqualTo("U1"),
                () -> assertThat(node.get("reviewer_github_ids").get(0).asText()).isEqualTo("reviewer-gh-1")
        );
    }

    @Test
    void 프로젝트가_없으면_예외가_발생한다() {
        // given
        ReviewRequestEventRequest request = new ReviewRequestEventRequest(
                "my-repo",
                "PR-1",
                1,
                "Title",
                "https://github.com/pr/1",
                "author-gh",
                List.of(),
                List.of()
        );

        // when & then
        assertThatThrownBy(() -> metaBuilder.build("T1", "C1", "unknown-key", request))
                .isInstanceOf(ProjectNotFoundException.class)
                .hasMessageContaining("unknown-key");
    }
}
