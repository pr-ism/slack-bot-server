package com.slack.bot.infrastructure.review.batch;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.review.ReviewEventBatch;
import com.slack.bot.application.review.dto.request.ReviewAssignmentRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InMemoryReviewEventBatchTest {

    @Autowired
    ReviewEventBatch eventBatch;

    @Autowired
    SpyReviewNotificationService spyNotificationService;

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/review/project_t1.sql",
            "classpath:sql/fixtures/review/workspace_t1.sql",
            "classpath:sql/fixtures/review/channel_t1.sql",
            "classpath:sql/fixtures/review/project_member_t1_mapped.sql"
    })
    void 같은_PR에_대한_여러_이벤트가_하나의_알림으로_배치된다() {
        // given
        spyNotificationService.resetCount();

        ReviewAssignmentRequest request1 = createRequest(List.of("reviewer-gh-1"));
        ReviewAssignmentRequest request2 = createRequest(List.of("reviewer-gh-1", "unknown-reviewer"));

        // when
        eventBatch.buffer("test-api-key", request1);
        eventBatch.buffer("test-api-key", request2);

        // then
        await().atMost(3, SECONDS)
               .untilAsserted(() -> assertThat(spyNotificationService.getSendCount()).isEqualTo(1));
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/review/project_t1.sql",
            "classpath:sql/fixtures/review/workspace_t1.sql",
            "classpath:sql/fixtures/review/channel_t1.sql",
            "classpath:sql/fixtures/review/project_member_t1_mapped.sql"
    })
    void 다른_PR에_대한_이벤트는_각각_별도로_발송된다() {
        // given
        spyNotificationService.resetCount();

        ReviewAssignmentRequest requestA = new ReviewAssignmentRequest(
                "my-repo",
                "PR-A",
                1,
                "Title A",
                "https://github.com/pr/a",
                "author-gh",
                List.of("reviewer-gh-1"),
                List.of()
        );
        ReviewAssignmentRequest requestB = new ReviewAssignmentRequest(
                "my-repo",
                "PR-B",
                2,
                "Title B",
                "https://github.com/pr/b",
                "author-gh",
                List.of("reviewer-gh-1"),
                List.of()
        );

        // when
        eventBatch.buffer("test-api-key", requestA);
        eventBatch.buffer("test-api-key", requestB);

        // then
        await().atMost(3, SECONDS)
               .untilAsserted(() -> assertThat(spyNotificationService.getSendCount()).isEqualTo(2));
    }

    private ReviewAssignmentRequest createRequest(List<String> reviewers) {
        return new ReviewAssignmentRequest(
                "my-repo",
                "PR-1",
                42,
                "Fix bug",
                "https://github.com/pr/1",
                "author-gh",
                reviewers,
                List.of()
        );
    }
}
