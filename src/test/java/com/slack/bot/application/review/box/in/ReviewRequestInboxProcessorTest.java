package com.slack.bot.application.review.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.review.box.ReviewNotificationSourceContext;
import com.slack.bot.application.review.dto.request.ReviewAssignmentRequest;
import com.slack.bot.infrastructure.interaction.box.SlackInteractivityFailureType;
import com.slack.bot.infrastructure.review.batch.SpyReviewNotificationService;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInbox;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxStatus;
import com.slack.bot.infrastructure.review.box.in.repository.ReviewRequestInboxRepository;
import com.slack.bot.infrastructure.review.persistence.box.in.JpaReviewRequestInboxRepository;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.client.ResourceAccessException;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewRequestInboxProcessorTest {

    @Autowired
    ReviewRequestInboxProcessor reviewRequestInboxProcessor;

    @Autowired
    ReviewRequestInboxRepository reviewRequestInboxRepository;

    @Autowired
    JpaReviewRequestInboxRepository jpaReviewRequestInboxRepository;

    @Autowired
    SpyReviewNotificationService spyReviewNotificationService;

    @Autowired
    ReviewNotificationSourceContext reviewNotificationSourceContext;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        reset(reviewRequestInboxRepository, spyReviewNotificationService, objectMapper);
        spyReviewNotificationService.resetCount();
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/review/project_t1.sql",
            "classpath:sql/fixtures/review/workspace_t1.sql",
            "classpath:sql/fixtures/review/channel_t1.sql",
            "classpath:sql/fixtures/review/project_member_t1_mapped.sql"
    })
    void enqueue후_processPending을_호출하면_인박스가_PROCESSED로_마킹되고_알림이_전송된다() {
        // given
        ReviewAssignmentRequest request = request(101L, "Fix bug");

        // when
        reviewRequestInboxProcessor.enqueue("test-api-key", request, 0);
        reviewRequestInboxProcessor.processPending(10, 1_000);

        // then
        List<ReviewRequestInbox> inboxes = jpaReviewRequestInboxRepository.findAll();
        ReviewRequestInbox inbox = inboxes.getFirst();
        assertAll(
                () -> assertThat(inboxes).hasSize(1),
                () -> assertThat(spyReviewNotificationService.getSendCount()).isEqualTo(1),
                () -> assertThat(inbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSED),
                () -> assertThat(inbox.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(inbox.getFailureType()).isNull(),
                () -> assertThat(reviewNotificationSourceContext.currentSourceKey()).isEmpty()
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/review/project_t1.sql",
            "classpath:sql/fixtures/review/workspace_t1.sql",
            "classpath:sql/fixtures/review/channel_t1.sql",
            "classpath:sql/fixtures/review/project_member_t1_mapped.sql"
    })
    void 동일_coalescing_key로_중복_enqueue하면_1건만_처리되고_최신_요청으로_업데이트된다() {
        // given
        ReviewAssignmentRequest first = request(202L, "Old title");
        ReviewAssignmentRequest second = request(202L, "New title");

        // when
        reviewRequestInboxProcessor.enqueue("test-api-key", first, 0);
        reviewRequestInboxProcessor.enqueue("test-api-key", second, 0);
        reviewRequestInboxProcessor.processPending(10, 1_000);

        // then
        List<ReviewRequestInbox> inboxes = jpaReviewRequestInboxRepository.findAll();
        ReviewRequestInbox inbox = inboxes.getFirst();
        assertAll(
                () -> assertThat(inboxes).hasSize(1),
                () -> assertThat(spyReviewNotificationService.getSendCount()).isEqualTo(1),
                () -> assertThat(inbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSED),
                () -> assertThat(objectMapper.readTree(inbox.getRequestJson()).path("pullRequestTitle").asText())
                        .isEqualTo("New title")
        );
    }

    @Test
    void 잘못된_requestJson은_재시도없이_FAILED_BUSINESS_INVARIANT로_마킹된다() {
        // given
        reviewRequestInboxRepository.upsertPending(
                "test-api-key:999",
                "test-api-key",
                999L,
                "{invalid-json",
                Instant.now().minusSeconds(1)
        );

        // when
        reviewRequestInboxProcessor.processPending(10, 1_000);

        // then
        ReviewRequestInbox inbox = jpaReviewRequestInboxRepository.findAll().getFirst();
        assertAll(
                () -> assertThat(spyReviewNotificationService.getSendCount()).isZero(),
                () -> assertThat(inbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.FAILED),
                () -> assertThat(inbox.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(inbox.getFailureType()).isEqualTo(SlackInteractivityFailureType.BUSINESS_INVARIANT),
                () -> assertThat(inbox.getFailureReason()).isNotBlank(),
                () -> assertThat(reviewNotificationSourceContext.currentSourceKey()).isEmpty()
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/review/project_t1.sql",
            "classpath:sql/fixtures/review/workspace_t1.sql",
            "classpath:sql/fixtures/review/channel_t1.sql",
            "classpath:sql/fixtures/review/project_member_t1_mapped.sql"
    })
    void PROCESSING_타임아웃_건은_RETRY_PENDING_복구후_재처리되어_PROCESSED가_된다() throws Exception {
        // given
        ReviewAssignmentRequest request = request(404L, "Timeout case");
        ReviewRequestInbox inbox = ReviewRequestInbox.pending(
                "test-api-key:404",
                "test-api-key",
                404L,
                objectMapper.writeValueAsString(request),
                Instant.now().minusSeconds(120)
        );
        inbox.markProcessing(Instant.now().minusSeconds(120));
        ReviewRequestInbox saved = jpaReviewRequestInboxRepository.save(inbox);

        // when
        reviewRequestInboxProcessor.processPending(10, 1_000);

        // then
        ReviewRequestInbox processed = jpaReviewRequestInboxRepository.findById(saved.getId()).orElseThrow();
        assertAll(
                () -> assertThat(spyReviewNotificationService.getSendCount()).isEqualTo(1),
                () -> assertThat(processed.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSED),
                () -> assertThat(processed.getProcessingAttempt()).isEqualTo(2),
                () -> assertThat(processed.getFailureReason()).isNull()
        );
    }

    @Test
    void batchWindowMillis가_음수이면_enqueue는_예외를_던진다() {
        // given
        ReviewAssignmentRequest request = request(501L, "batch-window");

        // when & then
        assertThatThrownBy(() -> reviewRequestInboxProcessor.enqueue("test-api-key", request, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("batchWindowMillis는 0 이상이어야 합니다.");
    }

    @Test
    void apiKey가_공백이면_enqueue는_예외를_던진다() {
        // given
        ReviewAssignmentRequest request = request(502L, "blank-api-key");

        // when & then
        assertThatThrownBy(() -> reviewRequestInboxProcessor.enqueue(" ", request, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("apiKey는 비어 있을 수 없습니다.");
    }

    @Test
    void githubPullRequestId가_0이면_enqueue는_예외를_던진다() {
        // given
        ReviewAssignmentRequest invalidRequest = new ReviewAssignmentRequest(
                "my-repo",
                0L,
                0,
                "invalid-pr",
                "https://github.com/pr/0",
                "author-gh",
                List.of("reviewer-gh-1"),
                List.of()
        );

        // when & then
        assertThatThrownBy(() -> reviewRequestInboxProcessor.enqueue("test-api-key", invalidRequest, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("githubPullRequestId는 비어 있을 수 없습니다.");
    }

    @Test
    void request_직렬화에_실패하면_enqueue는_예외를_던진다() throws Exception {
        // given
        ReviewAssignmentRequest request = request(503L, "serialization-fail");
        doThrow(new JsonProcessingException("serialize fail") {
        }).when(objectMapper).writeValueAsString(any());

        // when & then
        assertThatThrownBy(() -> reviewRequestInboxProcessor.enqueue("test-api-key", request, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("review request 직렬화에 실패했습니다.");
    }

    @Test
    void processingTimeoutMs가_0이면_processPending은_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> reviewRequestInboxProcessor.processPending(10, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("processingTimeoutMs는 0보다 커야 합니다.");
    }

    @Test
    void retryable_예외가_발생하고_최대시도_미만이면_RETRY_PENDING으로_마킹된다() {
        // given
        ReviewAssignmentRequest request = request(601L, "retry-pending");
        reviewRequestInboxProcessor.enqueue("test-api-key", request, 0);
        doThrow(new ResourceAccessException("temporary network issue"))
                .when(spyReviewNotificationService)
                .sendSimpleNotification(any(), any(ReviewAssignmentRequest.class));

        // when
        reviewRequestInboxProcessor.processPending(10, 1_000);

        // then
        ReviewRequestInbox inbox = jpaReviewRequestInboxRepository.findAll().getFirst();
        assertAll(
                () -> assertThat(inbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.RETRY_PENDING),
                () -> assertThat(inbox.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(inbox.getFailureType()).isNull(),
                () -> assertThat(inbox.getFailureReason()).isNotBlank()
        );
    }

    @Test
    void retryable_예외가_최대시도에_도달하면_FAILED_RETRY_EXHAUSTED로_마킹된다() throws Exception {
        // given
        ReviewAssignmentRequest request = request(602L, "retry-exhausted");
        ReviewRequestInbox inbox = ReviewRequestInbox.pending(
                "test-api-key:602",
                "test-api-key",
                602L,
                objectMapper.writeValueAsString(request),
                Instant.now().minusSeconds(60)
        );
        inbox.markProcessing(Instant.now().minusSeconds(55));
        inbox.markRetryPending(Instant.now().minusSeconds(50), "first failure");
        ReviewRequestInbox saved = jpaReviewRequestInboxRepository.save(inbox);

        doThrow(new ResourceAccessException("temporary network issue"))
                .when(spyReviewNotificationService)
                .sendSimpleNotification(any(), any(ReviewAssignmentRequest.class));

        // when
        reviewRequestInboxProcessor.processPending(10, 1_000);

        // then
        ReviewRequestInbox failed = jpaReviewRequestInboxRepository.findById(saved.getId()).orElseThrow();
        assertAll(
                () -> assertThat(failed.getStatus()).isEqualTo(ReviewRequestInboxStatus.FAILED),
                () -> assertThat(failed.getProcessingAttempt()).isEqualTo(2),
                () -> assertThat(failed.getFailureType()).isEqualTo(SlackInteractivityFailureType.RETRY_EXHAUSTED),
                () -> assertThat(failed.getFailureReason()).isNotBlank()
        );
    }

    @Test
    void PROCESSING으로_전이된_inbox를_findById로_조회못하면_예외없이_다음으로_진행한다() {
        // given
        ReviewAssignmentRequest request = request(701L, "missing-claimed");
        reviewRequestInboxProcessor.enqueue("test-api-key", request, 0);
        ReviewRequestInbox saved = jpaReviewRequestInboxRepository.findAll().getFirst();

        doReturn(true).when(reviewRequestInboxRepository).markProcessingIfClaimable(eq(saved.getId()), any(), any());
        doReturn(Optional.empty()).when(reviewRequestInboxRepository).findById(eq(saved.getId()));

        // when
        reviewRequestInboxProcessor.processPending(10, 1_000);

        // then
        ReviewRequestInbox reloaded = jpaReviewRequestInboxRepository.findById(saved.getId()).orElseThrow();
        assertAll(
                () -> assertThat(spyReviewNotificationService.getSendCount()).isZero(),
                () -> assertThat(reloaded.getStatus()).isEqualTo(ReviewRequestInboxStatus.PENDING)
        );
    }

    private ReviewAssignmentRequest request(Long githubPullRequestId, String pullRequestTitle) {
        return new ReviewAssignmentRequest(
                "my-repo",
                githubPullRequestId,
                Math.toIntExact(githubPullRequestId),
                pullRequestTitle,
                "https://github.com/pr/" + githubPullRequestId,
                "author-gh",
                List.of("reviewer-gh-1"),
                List.of()
        );
    }

    @Test
    void availableAt이_null이면_epochMillis는_0을_반환한다() throws Exception {
        // given
        Method method = ReviewRequestInboxProcessor.class.getDeclaredMethod(
                "resolveAvailableAtEpochMillis",
                Instant.class
        );
        method.setAccessible(true);

        // when
        long actual = (long) method.invoke(reviewRequestInboxProcessor, new Object[]{null});

        // then
        assertThat(actual).isZero();
    }
}
