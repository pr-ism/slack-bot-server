package com.slack.bot.application.review.box.in;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.review.box.ReviewNotificationSourceContext;
import com.slack.bot.application.review.box.out.ReviewNotificationOutboxEnqueuer;
import com.slack.bot.application.review.dto.ReviewNotificationPayload;
import com.slack.bot.application.worker.PollingHintPublisher;
import com.slack.bot.application.worker.PollingHintTarget;
import com.slack.bot.infrastructure.common.BoxEventTime;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.review.batch.SpyReviewNotificationService;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxFailureType;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInbox;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxHistory;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxStatus;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import com.slack.bot.infrastructure.review.box.in.repository.ReviewRequestInboxRepository;
import com.slack.bot.infrastructure.review.persistence.box.in.JpaReviewRequestInboxHistoryRepository;
import com.slack.bot.infrastructure.review.persistence.box.out.JpaReviewNotificationOutboxRepository;
import com.slack.bot.infrastructure.review.persistence.box.in.JpaReviewRequestInboxRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;

@IntegrationTest
@MockitoSpyBean(types = PollingHintPublisher.class)
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
    JpaReviewNotificationOutboxRepository jpaReviewNotificationOutboxRepository;

    @Autowired
    JpaReviewRequestInboxHistoryRepository jpaReviewRequestInboxHistoryRepository;

    @Autowired
    SpyReviewNotificationService spyReviewNotificationService;

    @Autowired
    ReviewNotificationSourceContext reviewNotificationSourceContext;

    @Autowired
    ReviewNotificationOutboxEnqueuer reviewNotificationOutboxEnqueuer;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    PollingHintPublisher pollingHintPublisher;

    @BeforeEach
    void setUp() {
        reset(
                reviewRequestInboxRepository,
                reviewNotificationOutboxEnqueuer,
                spyReviewNotificationService,
                objectMapper,
                pollingHintPublisher
        );
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
        ReviewNotificationPayload request = request(101L, "Fix bug");

        // when
        reviewRequestInboxProcessor.enqueue("test-api-key", request, 0);
        reviewRequestInboxProcessor.processPending(10);

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            List<ReviewRequestInbox> inboxes = jpaReviewRequestInboxRepository.findAllDomains();
            List<ReviewNotificationOutbox> outboxes = jpaReviewNotificationOutboxRepository.findAll();
            ReviewRequestInbox inbox = findOnlyInbox();

            assertAll(
                    () -> assertThat(inboxes).hasSize(1),
                    () -> assertThat(outboxes).hasSize(1),
                    () -> assertThat(spyReviewNotificationService.getSendCount()).isEqualTo(1),
                    () -> assertThat(inbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSED),
                    () -> assertThat(inbox.getProcessingAttempt()).isEqualTo(1),
                    () -> assertThat(inbox.getIdempotencyKey()).hasSize(64),
                    () -> assertThat(inbox.getFailure().isPresent()).isFalse(),
                    () -> assertThat(reviewNotificationSourceContext.currentSourceKey()).isEmpty()
            );
        });
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/review/project_t1.sql",
            "classpath:sql/fixtures/review/workspace_t1.sql",
            "classpath:sql/fixtures/review/channel_t1.sql",
            "classpath:sql/fixtures/review/project_member_t1_mapped.sql"
    })
    void 동일_idempotency_key로_중복_enqueue하면_1건만_처리되고_최신_요청으로_업데이트된다() {
        // given
        ReviewNotificationPayload first = request(202L, "Old title");
        ReviewNotificationPayload second = request(202L, "New title");

        // when
        reviewRequestInboxProcessor.enqueue("test-api-key", first, 0);
        reviewRequestInboxProcessor.enqueue("test-api-key", second, 0);
        reviewRequestInboxProcessor.processPending(10);

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            List<ReviewRequestInbox> inboxes = jpaReviewRequestInboxRepository.findAllDomains();
            List<ReviewNotificationOutbox> outboxes = jpaReviewNotificationOutboxRepository.findAll();
            ReviewRequestInbox inbox = findOnlyInbox();

            assertAll(
                    () -> assertThat(inboxes).hasSize(1),
                    () -> assertThat(outboxes).hasSize(1),
                    () -> assertThat(spyReviewNotificationService.getSendCount()).isEqualTo(1),
                    () -> assertThat(inbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSED),
                    () -> assertThat(inbox.getIdempotencyKey()).hasSize(64),
                    () -> assertThat(objectMapper.readTree(inbox.getRequestJson()).path("pullRequestTitle").asText())
                            .isEqualTo("New title")
            );
        });
    }

    @Test
    void 잘못된_requestJson은_재시도없이_FAILED_NON_RETRYABLE로_마킹된다() {
        // given
        reviewRequestInboxRepository.upsertPending(
                "test-api-key:999",
                "test-api-key",
                999L,
                "{invalid-json",
                Instant.now().minusSeconds(1)
        );

        // when
        reviewRequestInboxProcessor.processPending(10);

        // then
        ReviewRequestInbox inbox = findOnlyInbox();
        assertAll(
                () -> assertThat(spyReviewNotificationService.getSendCount()).isZero(),
                () -> assertThat(inbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.FAILED),
                () -> assertThat(inbox.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(inbox.getFailure().type()).isEqualTo(ReviewRequestInboxFailureType.NON_RETRYABLE),
                () -> assertThat(inbox.getFailure().reason()).isNotBlank(),
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
        ReviewNotificationPayload request = request(404L, "Timeout case");
        ReviewRequestInbox inbox = ReviewRequestInbox.pending(
                "test-api-key:404",
                "test-api-key",
                404L,
                objectMapper.writeValueAsString(request),
                Instant.now().minusSeconds(120)
        );
        setProcessingState(inbox, Instant.now().minusSeconds(120), 1);
        ReviewRequestInbox saved = reviewRequestInboxRepository.save(inbox);

        // when
        reviewRequestInboxProcessor.recoverTimeoutProcessing(1_000);
        reviewRequestInboxProcessor.processPending(10);

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            ReviewRequestInbox processed = jpaReviewRequestInboxRepository.findDomainById(saved.getId()).orElseThrow();
            List<ReviewRequestInboxHistory> histories = historiesOf(saved.getId());

            assertAll(
                    () -> assertThat(spyReviewNotificationService.getSendCount()).isEqualTo(1),
                    () -> assertThat(processed.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSED),
                    () -> assertThat(processed.getProcessingAttempt()).isEqualTo(2),
                    () -> assertThat(processed.getFailure().isPresent()).isFalse(),
                    () -> assertThat(histories).hasSize(2),
                    () -> assertThat(histories).extracting(history -> history.getStatus())
                            .containsExactly(
                                    ReviewRequestInboxStatus.RETRY_PENDING,
                                    ReviewRequestInboxStatus.PROCESSED
                            )
            );
            verify(pollingHintPublisher).publish(PollingHintTarget.REVIEW_REQUEST_INBOX);
        });
    }

    @Test
    void PROCESSING_타임아웃_건이_최대시도에_도달하면_FAILED_RETRY_EXHAUSTED로_격리된다() throws Exception {
        // given
        ReviewNotificationPayload request = request(405L, "Timeout poison pill");
        ReviewRequestInbox inbox = ReviewRequestInbox.pending(
                "test-api-key:405",
                "test-api-key",
                405L,
                objectMapper.writeValueAsString(request),
                Instant.now().minusSeconds(120)
        );
        setProcessingState(inbox, Instant.now().minusSeconds(120), 1);
        inbox.markRetryPending(Instant.now().minusSeconds(110), "first timeout failure");
        setProcessingState(inbox, Instant.now().minusSeconds(100), 2);
        ReviewRequestInbox saved = reviewRequestInboxRepository.save(inbox);

        // when
        reviewRequestInboxProcessor.recoverTimeoutProcessing(1_000);

        // then
        ReviewRequestInbox failed = jpaReviewRequestInboxRepository.findDomainById(saved.getId()).orElseThrow();
        assertAll(
                () -> assertThat(spyReviewNotificationService.getSendCount()).isZero(),
                () -> assertThat(failed.getStatus()).isEqualTo(ReviewRequestInboxStatus.FAILED),
                () -> assertThat(failed.getProcessingAttempt()).isEqualTo(2),
                () -> assertThat(failed.getFailure().type()).isEqualTo(ReviewRequestInboxFailureType.RETRY_EXHAUSTED),
                () -> assertThat(failed.getFailure().reason()).isNotBlank()
        );
    }

    @Test
    void batchWindowMillis가_음수이면_enqueue는_예외를_던진다() {
        // given
        ReviewNotificationPayload request = request(501L, "batch-window");

        // when & then
        assertThatThrownBy(() -> reviewRequestInboxProcessor.enqueue("test-api-key", request, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("batchWindowMillis는 0 이상이어야 합니다.");
    }

    @Test
    void apiKey가_공백이면_enqueue는_예외를_던진다() {
        // given
        ReviewNotificationPayload request = request(502L, "blank-api-key");

        // when & then
        assertThatThrownBy(() -> reviewRequestInboxProcessor.enqueue(" ", request, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("apiKey는 비어 있을 수 없습니다.");
    }

    @Test
    void batchWindowMillis가_0이면_enqueue후_review_request_inbox_wake_up_hint를_발행한다() {
        // given
        ReviewNotificationPayload request = request(504L, "wake-up-now");

        // when
        reviewRequestInboxProcessor.enqueue("test-api-key", request, 0);

        // then
        verify(pollingHintPublisher).publish(PollingHintTarget.REVIEW_REQUEST_INBOX);
    }

    private List<ReviewRequestInboxHistory> historiesOf(Long inboxId) {
        return jpaReviewRequestInboxHistoryRepository.findAllDomains()
                                                     .stream()
                                                     .filter(history -> inboxId.equals(history.getInboxId()))
                                                     .sorted(Comparator.comparingInt(history -> history.getProcessingAttempt()))
                                                     .toList();
    }

    @Test
    void batchWindowMillis가_0보다_크면_enqueue후_wake_up_hint를_발행하지_않는다() {
        // given
        ReviewNotificationPayload request = request(505L, "wake-up-later");

        // when
        reviewRequestInboxProcessor.enqueue("test-api-key", request, 1_000);

        // then
        verify(pollingHintPublisher, never()).publish(PollingHintTarget.REVIEW_REQUEST_INBOX);
    }

    @Test
    void request가_비어_있으면_enqueue는_할_수_없다() {
        // when & then
        assertThatThrownBy(() -> reviewRequestInboxProcessor.enqueue("test-api-key", null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("request는 비어 있을 수 없습니다.");
    }

    @Test
    void githubPullRequestId가_0이면_enqueue는_예외를_던진다() {
        // given
        ReviewNotificationPayload invalidRequest = new ReviewNotificationPayload(
                "my-repo",
                0L,
                0,
                "invalid-pr",
                "https://github.com/pr/0",
                "author-gh",
                List.of("reviewer-gh-1"),
                List.of("reviewer-gh-1")
        );

        // when & then
        assertThatThrownBy(() -> reviewRequestInboxProcessor.enqueue("test-api-key", invalidRequest, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("githubPullRequestId는 비어 있을 수 없습니다.");
    }

    @Test
    void 기본_enqueue는_같은_apiKey와_pullRequestId면_같은_해시_idempotencyKey를_사용한다() {
        // given
        ReviewNotificationPayload first = request(901L, "first-title");
        ReviewNotificationPayload second = request(901L, "second-title");

        // when
        reviewRequestInboxProcessor.enqueue("test-api-key", first, 0);
        String firstIdempotencyKey = findOnlyInbox().getIdempotencyKey();
        reviewRequestInboxProcessor.enqueue("test-api-key", second, 0);

        // then
        List<ReviewRequestInbox> inboxes = jpaReviewRequestInboxRepository.findAllDomains();
        ReviewRequestInbox inbox = findOnlyInbox();
        assertAll(
                () -> assertThat(inboxes).hasSize(1),
                () -> assertThat(inbox.getIdempotencyKey()).hasSize(64),
                () -> assertThat(inbox.getIdempotencyKey()).isEqualTo(firstIdempotencyKey)
        );
    }

    @Test
    void 기본_enqueue는_apiKey가_다르면_다른_해시_idempotencyKey를_사용한다() {
        // given
        ReviewNotificationPayload request = request(902L, "same-pr");

        // when
        reviewRequestInboxProcessor.enqueue("test-api-key-1", request, 0);
        String firstIdempotencyKey = findOnlyInbox().getIdempotencyKey();
        jpaReviewRequestInboxRepository.deleteAll();
        reviewRequestInboxProcessor.enqueue("test-api-key-2", request, 0);
        String secondIdempotencyKey = findOnlyInbox().getIdempotencyKey();

        // then
        assertThat(firstIdempotencyKey).isNotEqualTo(secondIdempotencyKey);
    }

    @Test
    void 기본_enqueue는_reviewRoundKey가_다르면_다른_해시_idempotencyKey를_사용한다() {
        // given
        ReviewNotificationPayload first = request(903L, "same-pr", "1");
        ReviewNotificationPayload second = request(903L, "same-pr", "2");

        // when
        reviewRequestInboxProcessor.enqueue("test-api-key", first, 0);
        String firstIdempotencyKey = findOnlyInbox().getIdempotencyKey();
        jpaReviewRequestInboxRepository.deleteAll();
        reviewRequestInboxProcessor.enqueue("test-api-key", second, 0);
        String secondIdempotencyKey = findOnlyInbox().getIdempotencyKey();

        // then
        assertThat(firstIdempotencyKey).isNotEqualTo(secondIdempotencyKey);
    }

    @Test
    void request_직렬화에_실패하면_enqueue는_예외를_던진다() throws Exception {
        // given
        ReviewNotificationPayload request = request(503L, "serialization-fail");
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
        assertThatThrownBy(() -> reviewRequestInboxProcessor.recoverTimeoutProcessing(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("processingTimeoutMs는 0보다 커야 합니다.");
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/review/project_t1.sql",
            "classpath:sql/fixtures/review/workspace_t1.sql",
            "classpath:sql/fixtures/review/channel_t1.sql",
            "classpath:sql/fixtures/review/project_member_t1_mapped.sql"
    })
    void retryable_예외가_발생하면_timeout_recovery_전까지_PROCESSING으로_남는다() {
        // given
        ReviewNotificationPayload request = request(601L, "retry-pending");
        reviewRequestInboxProcessor.enqueue("test-api-key", request, 0);
        doThrow(new ResourceAccessException("temporary network issue"))
                .when(reviewNotificationOutboxEnqueuer)
                .enqueueReviewNotification(
                        any(),
                        anyLong(),
                        any(),
                        any(),
                        any()
                );

        // when
        reviewRequestInboxProcessor.processPending(10);

        // then
        ReviewRequestInbox inbox = findOnlyInbox();
        assertAll(
                () -> assertThat(inbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSING),
                () -> assertThat(inbox.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(inbox.getFailure().isPresent()).isFalse(),
                () -> assertThat(jpaReviewNotificationOutboxRepository.findAll()).isEmpty()
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/review/project_t1.sql",
            "classpath:sql/fixtures/review/workspace_t1.sql",
            "classpath:sql/fixtures/review/channel_t1.sql",
            "classpath:sql/fixtures/review/project_member_t1_mapped.sql"
    })
    void 최대시도에_도달한_retryable_예외도_timeout_recovery_전까지_PROCESSING으로_남는다() throws Exception {
        // given
        ReviewNotificationPayload request = request(602L, "retry-exhausted");
        ReviewRequestInbox inbox = ReviewRequestInbox.pending(
                "test-api-key:602",
                "test-api-key",
                602L,
                objectMapper.writeValueAsString(request),
                Instant.now().minusSeconds(60)
        );
        setProcessingState(inbox, Instant.now().minusSeconds(55), 1);
        inbox.markRetryPending(Instant.now().minusSeconds(50), "first failure");
        ReviewRequestInbox saved = reviewRequestInboxRepository.save(inbox);

        doThrow(new ResourceAccessException("temporary network issue"))
                .when(reviewNotificationOutboxEnqueuer)
                .enqueueReviewNotification(
                        any(),
                        anyLong(),
                        any(),
                        any(),
                        any()
                );

        // when
        reviewRequestInboxProcessor.processPending(10);

        // then
        ReviewRequestInbox failed = jpaReviewRequestInboxRepository.findDomainById(saved.getId()).orElseThrow();
        assertAll(
                () -> assertThat(failed.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSING),
                () -> assertThat(failed.getProcessingAttempt()).isEqualTo(2),
                () -> assertThat(failed.getFailure().isPresent()).isFalse(),
                () -> assertThat(jpaReviewNotificationOutboxRepository.findAll()).isEmpty()
        );
    }

    @Test
    void PROCESSING으로_전이된_inbox를_findById로_조회못하면_예외없이_다음으로_진행한다() {
        // given
        ReviewNotificationPayload request = request(701L, "missing-claimed");
        reviewRequestInboxProcessor.enqueue("test-api-key", request, 0);
        ReviewRequestInbox saved = findOnlyInbox();

        doReturn(Optional.empty()).when(reviewRequestInboxRepository).findById(saved.getId());

        // when
        reviewRequestInboxProcessor.processPending(10);

        // then
        ReviewRequestInbox reloaded = jpaReviewRequestInboxRepository.findDomainById(saved.getId()).orElseThrow();
        assertAll(
                () -> assertThat(spyReviewNotificationService.getSendCount()).isZero(),
                () -> assertThat(reloaded.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSING),
                () -> assertThat(reloaded.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(jpaReviewNotificationOutboxRepository.findAll()).isEmpty()
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/review/project_t1.sql",
            "classpath:sql/fixtures/review/workspace_t1.sql",
            "classpath:sql/fixtures/review/channel_t1.sql",
            "classpath:sql/fixtures/review/project_member_t1_mapped.sql"
    })
    void claimed_inbox_처리중_예상치못한_예외가_나도_다음_inbox는_계속_처리한다() {
        // given
        reviewRequestInboxProcessor.enqueue("test-api-key", request(801L, "first"), 0);
        reviewRequestInboxProcessor.enqueue("test-api-key", request(802L, "second"), 0);
        ReviewRequestInbox firstInbox = findInboxByGithubPullRequestId(801L);
        ReviewRequestInbox secondInbox = findInboxByGithubPullRequestId(802L);

        doThrow(new RuntimeException("unexpected repository failure"))
                .when(reviewRequestInboxRepository)
                .findById(firstInbox.getId());

        // when
        reviewRequestInboxProcessor.processPending(10);

        // then
        ReviewRequestInbox reloadedFirst = jpaReviewRequestInboxRepository.findDomainById(firstInbox.getId()).orElseThrow();
        ReviewRequestInbox reloadedSecond = jpaReviewRequestInboxRepository.findDomainById(secondInbox.getId()).orElseThrow();
        assertAll(
                () -> assertThat(reloadedFirst.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSING),
                () -> assertThat(reloadedSecond.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSED),
                () -> assertThat(spyReviewNotificationService.getSendCount()).isEqualTo(1)
        );
    }

    private ReviewNotificationPayload request(Long githubPullRequestId, String pullRequestTitle) {
        return request(githubPullRequestId, pullRequestTitle, null);
    }

    private ReviewNotificationPayload request(Long githubPullRequestId, String pullRequestTitle, String reviewRoundKey) {
        return new ReviewNotificationPayload(
                "my-repo",
                githubPullRequestId,
                Math.toIntExact(githubPullRequestId),
                pullRequestTitle,
                "https://github.com/pr/" + githubPullRequestId,
                "author-gh",
                List.of("reviewer-gh-1"),
                List.of("reviewer-gh-1"),
                reviewRoundKey
        );
    }

    private void setProcessingState(ReviewRequestInbox inbox, Instant processingStartedAt, int processingAttempt) {
        ReflectionTestUtils.setField(inbox, "status", ReviewRequestInboxStatus.PROCESSING);
        ReflectionTestUtils.setField(inbox, "processingLease", BoxProcessingLease.claimed(processingStartedAt));
        ReflectionTestUtils.setField(inbox, "processedTime", BoxEventTime.absent());
        ReflectionTestUtils.setField(inbox, "failedTime", BoxEventTime.absent());
        ReflectionTestUtils.setField(inbox, "failure", BoxFailureSnapshot.absent());
        ReflectionTestUtils.setField(inbox, "processingAttempt", processingAttempt);
    }

    private ReviewRequestInbox findOnlyInbox() {
        List<ReviewRequestInbox> inboxes = jpaReviewRequestInboxRepository.findAllDomains();
        assertThat(inboxes).hasSize(1);
        return inboxes.getFirst();
    }

    private ReviewRequestInbox findInboxByGithubPullRequestId(Long githubPullRequestId) {
        return jpaReviewRequestInboxRepository.findAllDomains()
                                             .stream()
                                             .filter(inbox -> githubPullRequestId.equals(inbox.getGithubPullRequestId()))
                                             .findFirst()
                                             .orElseThrow();
    }

}
