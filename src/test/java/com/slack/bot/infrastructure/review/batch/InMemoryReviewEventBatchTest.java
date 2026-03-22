package com.slack.bot.infrastructure.review.batch;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.review.ReviewEventBatch;
import com.slack.bot.application.review.dto.request.ReviewAssignmentRequest;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInbox;
import com.slack.bot.infrastructure.review.persistence.box.in.JpaReviewRequestInboxRepository;
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

    @Autowired
    JpaReviewRequestInboxRepository jpaReviewRequestInboxRepository;

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

        ReviewAssignmentRequest request1 = createRequest("commit-hash-1", List.of("reviewer-gh-1"));
        ReviewAssignmentRequest request2 = createRequest("commit-hash-1", List.of("reviewer-gh-1", "unknown-reviewer"));

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
                1001L,
                1,
                "Title A",
                "https://github.com/pr/a",
                "author-gh",
                "commit-hash-a",
                List.of("reviewer-gh-1"),
                List.of()
        );
        ReviewAssignmentRequest requestB = new ReviewAssignmentRequest(
                "my-repo",
                1002L,
                2,
                "Title B",
                "https://github.com/pr/b",
                "author-gh",
                "commit-hash-b",
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

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/review/project_t1.sql",
            "classpath:sql/fixtures/review/workspace_t1.sql",
            "classpath:sql/fixtures/review/channel_t1.sql",
            "classpath:sql/fixtures/review/project_member_t1_mapped.sql"
    })
    void 여러_라운드가_열려도_이전_라운드에서_미리뷰인_동일_리뷰어는_재발송되지_않는다() {
        // given
        spyNotificationService.resetCount();

        ReviewAssignmentRequest roundOne = createRequest("commit-hash-round-1", List.of("reviewer-gh-1"));
        ReviewAssignmentRequest roundTwo = createRequest("commit-hash-round-2", List.of("reviewer-gh-1"));

        // when
        eventBatch.buffer("test-api-key", roundOne);
        eventBatch.buffer("test-api-key", roundOne);
        await().atMost(3, SECONDS)
               .untilAsserted(() -> assertThat(spyNotificationService.getSendCount()).isEqualTo(1));

        eventBatch.buffer("test-api-key", roundTwo);
        eventBatch.buffer("test-api-key", roundTwo);

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
    void 배치_enqueue는_requestJson에_apiKey를_노출하지_않고_roundNumber만_reviewRoundKey로_저장한다() {
        // given
        ReviewAssignmentRequest request = createRequest("commit-hash-1", List.of("reviewer-gh-1"));

        // when
        eventBatch.buffer("test-api-key", request);

        // then
        await().atMost(3, SECONDS).untilAsserted(() -> {
            ReviewRequestInbox inbox = jpaReviewRequestInboxRepository.findAll().getFirst();

            assertThat(inbox.getRequestJson())
                    .contains("\"reviewRoundKey\":\"1\"")
                    .doesNotContain("test-api-key");
        });
    }

    private ReviewAssignmentRequest createRequest(String startCommitHash, List<String> reviewers) {
        return createRequest(startCommitHash, reviewers, List.of());
    }

    private ReviewAssignmentRequest createRequest(
            String startCommitHash,
            List<String> pendingReviewers,
            List<String> reviewedReviewers
    ) {
        return new ReviewAssignmentRequest(
                "my-repo",
                1000L,
                42,
                "Fix bug",
                "https://github.com/pr/1",
                "author-gh",
                startCommitHash,
                pendingReviewers,
                reviewedReviewers
        );
    }
}
