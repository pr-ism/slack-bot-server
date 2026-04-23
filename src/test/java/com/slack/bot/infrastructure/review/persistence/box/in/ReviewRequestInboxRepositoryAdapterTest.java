package com.slack.bot.infrastructure.review.persistence.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.infrastructure.common.BoxEventTime;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInbox;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxFailureType;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxHistory;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxStatus;
import com.slack.bot.infrastructure.review.box.in.repository.ReviewRequestInboxRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewRequestInboxRepositoryAdapterTest {

    @Autowired
    ReviewRequestInboxRepository reviewRequestInboxRepository;

    @Autowired
    ReviewRequestInboxMybatisMapper reviewRequestInboxMybatisMapper;

    @Autowired
    ReviewRequestInboxHistoryMybatisMapper reviewRequestInboxHistoryMybatisMapper;

    @Autowired
    NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void 신규_upsertPending은_failure_snapshot을_sentinel값으로_저장한다() {
        // given
        Instant availableAt = Instant.parse("2026-02-24T00:00:00Z");

        // when
        reviewRequestInboxRepository.upsertPending(
                "api-key:41",
                "api-key",
                41L,
                "{\"pullRequestTitle\":\"new\"}",
                availableAt
        );

        // then
        ReviewRequestInbox actual = findByIdempotencyKey("api-key:41");
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(ReviewRequestInboxStatus.PENDING),
                () -> assertThat(actual.getProcessingAttempt()).isZero(),
                () -> assertThat(actual.hasClaimedProcessingLease()).isFalse(),
                () -> assertThat(actual.getProcessedTime().isPresent()).isFalse(),
                () -> assertThat(actual.getFailedTime().isPresent()).isFalse(),
                () -> assertThat(actual.getFailure().isPresent()).isFalse(),
                () -> assertThat(actual.getAvailableAt()).isEqualTo(availableAt)
        );
    }

    @Test
    void PROCESSING_상태면_upsertPending이_행을_덮어쓰지_않는다() {
        // given
        Instant oldAvailableAt = Instant.parse("2026-02-24T00:00:00Z");
        Instant processingStartedAt = Instant.parse("2026-02-24T00:01:00Z");
        ReviewRequestInbox inbox = ReviewRequestInbox.pending(
                "api-key:42",
                "api-key",
                42L,
                "{\"pullRequestTitle\":\"old\"}",
                oldAvailableAt
        );
        setProcessingState(inbox, processingStartedAt, 1);
        ReviewRequestInbox saved = reviewRequestInboxRepository.save(inbox);

        // when
        reviewRequestInboxRepository.upsertPending(
                "api-key:42",
                "api-key-updated",
                42L,
                "{\"pullRequestTitle\":\"new\"}",
                Instant.parse("2026-02-24T00:02:00Z")
        );

        // then
        ReviewRequestInbox actual = reviewRequestInboxMybatisMapper.findDomainById(saved.getId()).orElseThrow();
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSING),
                () -> assertThat(actual.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(actual.currentProcessingLeaseStartedAt()).isEqualTo(processingStartedAt),
                () -> assertThat(actual.getApiKey()).isEqualTo("api-key"),
                () -> assertThat(actual.getRequestJson()).isEqualTo("{\"pullRequestTitle\":\"old\"}"),
                () -> assertThat(actual.getAvailableAt()).isEqualTo(oldAvailableAt)
        );
    }

    @Test
    void RETRY_PENDING_상태면_upsertPending이_최신_요청으로_갱신하고_PENDING으로_돌린다() {
        // given
        ReviewRequestInbox inbox = ReviewRequestInbox.pending(
                "api-key:43",
                "api-key",
                43L,
                "{\"pullRequestTitle\":\"old\"}",
                Instant.parse("2026-02-24T00:00:00Z")
        );
        setProcessingState(inbox, Instant.parse("2026-02-24T00:01:00Z"), 1);
        inbox.markRetryPending(Instant.parse("2026-02-24T00:02:00Z"), "temporary failure");
        ReviewRequestInbox saved = reviewRequestInboxRepository.save(inbox);

        // when
        Instant newAvailableAt = Instant.parse("2026-02-24T00:03:00Z");
        reviewRequestInboxRepository.upsertPending(
                "api-key:43",
                "api-key",
                43L,
                "{\"pullRequestTitle\":\"new\"}",
                newAvailableAt
        );

        // then
        ReviewRequestInbox actual = reviewRequestInboxMybatisMapper.findDomainById(saved.getId()).orElseThrow();
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(ReviewRequestInboxStatus.PENDING),
                () -> assertThat(actual.getProcessingAttempt()).isZero(),
                () -> assertThat(actual.hasClaimedProcessingLease()).isFalse(),
                () -> assertThat(actual.getProcessedTime().isPresent()).isFalse(),
                () -> assertThat(actual.getFailedTime().isPresent()).isFalse(),
                () -> assertThat(actual.getFailure().isPresent()).isFalse(),
                () -> assertThat(actual.getRequestJson()).isEqualTo("{\"pullRequestTitle\":\"new\"}"),
                () -> assertThat(actual.getAvailableAt()).isEqualTo(newAvailableAt)
        );
    }

    @Test
    void claimNextId는_제외된_inbox와_아직_처리할_수_없는_inbox를_건너뛴다() {
        // given
        Instant base = Instant.parse("2026-02-24T00:10:00Z");
        ReviewRequestInbox excludedInbox = reviewRequestInboxRepository.save(ReviewRequestInbox.pending(
                "api-key:claim-excluded",
                "api-key",
                5101L,
                "{\"pullRequestTitle\":\"excluded\"}",
                base.minusSeconds(30L)
        ));
        ReviewRequestInbox claimableInbox = reviewRequestInboxRepository.save(ReviewRequestInbox.pending(
                "api-key:claim-target",
                "api-key",
                5102L,
                "{\"pullRequestTitle\":\"target\"}",
                base.minusSeconds(20L)
        ));
        ReviewRequestInbox notYetAvailableInbox = reviewRequestInboxRepository.save(ReviewRequestInbox.pending(
                "api-key:claim-future",
                "api-key",
                5103L,
                "{\"pullRequestTitle\":\"future\"}",
                base.plusSeconds(60L)
        ));

        // when
        Optional<Long> claimedInboxId = reviewRequestInboxRepository.claimNextId(
                base,
                base,
                List.of(excludedInbox.getId())
        );

        // then
        ReviewRequestInbox actualExcludedInbox = reviewRequestInboxMybatisMapper.findDomainById(
                excludedInbox.getId()
        ).orElseThrow();
        ReviewRequestInbox actualClaimableInbox = reviewRequestInboxMybatisMapper.findDomainById(
                claimableInbox.getId()
        ).orElseThrow();
        ReviewRequestInbox actualNotYetAvailableInbox = reviewRequestInboxMybatisMapper.findDomainById(
                notYetAvailableInbox.getId()
        ).orElseThrow();
        assertAll(
                () -> assertThat(claimedInboxId).contains(claimableInbox.getId()),
                () -> assertThat(actualExcludedInbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.PENDING),
                () -> assertThat(actualNotYetAvailableInbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.PENDING),
                () -> assertThat(actualClaimableInbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSING),
                () -> assertThat(actualClaimableInbox.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(actualClaimableInbox.currentProcessingLeaseStartedAt()).isEqualTo(base)
        );
    }

    @Test
    void claimNextId는_RETRY_PENDING_inbox의_실패_상세값을_초기화한다() {
        // given
        Instant processingStartedAt = Instant.parse("2026-02-24T00:20:00Z");
        ReviewRequestInbox inbox = ReviewRequestInbox.pending(
                "api-key:claim-retry",
                "api-key",
                5201L,
                "{\"pullRequestTitle\":\"retry\"}",
                Instant.parse("2026-02-24T00:00:00Z")
        );
        setProcessingState(inbox, Instant.parse("2026-02-24T00:01:00Z"), 1);
        inbox.markRetryPending(Instant.parse("2026-02-24T00:02:00Z"), "temporary failure");
        ReviewRequestInbox saved = reviewRequestInboxRepository.save(inbox);

        // when
        Optional<Long> claimedInboxId = reviewRequestInboxRepository.claimNextId(
                processingStartedAt,
                processingStartedAt,
                List.of()
        );

        // then
        ReviewRequestInbox actualInbox = reviewRequestInboxMybatisMapper.findDomainById(saved.getId()).orElseThrow();
        assertAll(
                () -> assertThat(claimedInboxId).contains(saved.getId()),
                () -> assertThat(actualInbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSING),
                () -> assertThat(actualInbox.getProcessingAttempt()).isEqualTo(2),
                () -> assertThat(actualInbox.currentProcessingLeaseStartedAt()).isEqualTo(processingStartedAt),
                () -> assertThat(actualInbox.getProcessedTime().isPresent()).isFalse(),
                () -> assertThat(actualInbox.getFailedTime().isPresent()).isFalse(),
                () -> assertThat(actualInbox.getFailure().isPresent()).isFalse()
        );
    }

    @Test
    void PROCESSING_타임아웃을_RETRY_PENDING으로_복구하면_history에는_PROCESSING_TIMEOUT을_남긴다() {
        // given
        ReviewRequestInbox inbox = ReviewRequestInbox.pending(
                "api-key:44",
                "api-key",
                44L,
                "{\"pullRequestTitle\":\"old\"}",
                Instant.parse("2026-02-24T00:00:00Z")
        );
        setProcessingState(inbox, Instant.parse("2026-02-24T00:01:00Z"), 1);
        ReviewRequestInbox saved = reviewRequestInboxRepository.save(inbox);
        Instant failedAt = Instant.parse("2026-02-24T00:05:00Z");
        LocalDateTime expectedUpdatedAt = LocalDateTime.ofInstant(failedAt, ZoneOffset.UTC);

        // when
        int recoveredCount = reviewRequestInboxRepository.recoverTimeoutProcessing(
                Instant.parse("2026-02-24T00:02:00Z"),
                failedAt,
                "timeout",
                3,
                100
        );

        // then
        ReviewRequestInbox actual = reviewRequestInboxMybatisMapper.findDomainById(saved.getId()).orElseThrow();
        ReviewRequestInboxHistory history = findLatestHistory(saved.getId());
        LocalDateTime actualUpdatedAt = findUpdatedAt(saved.getId());
        assertAll(
                () -> assertThat(recoveredCount).isEqualTo(1),
                () -> assertThat(actual.getStatus()).isEqualTo(ReviewRequestInboxStatus.RETRY_PENDING),
                () -> assertThat(actual.getFailure().type()).isEqualTo(ReviewRequestInboxFailureType.PROCESSING_TIMEOUT),
                () -> assertThat(actualUpdatedAt).isEqualTo(expectedUpdatedAt),
                () -> assertThat(history.getStatus()).isEqualTo(ReviewRequestInboxStatus.RETRY_PENDING),
                () -> assertThat(history.getFailure().type()).isEqualTo(ReviewRequestInboxFailureType.PROCESSING_TIMEOUT),
                () -> assertThat(history.getFailure().reason()).isEqualTo("timeout"),
                () -> assertThat(history.getCompletedAt()).isEqualTo(failedAt)
        );
    }

    @Test
    void PROCESSING_타임아웃_복구는_배치_크기만큼만_처리한다() {
        // given
        Instant base = Instant.parse("2026-02-24T00:10:00Z");
        List<Long> inboxIds = new ArrayList<>();
        for (int index = 0; index < 101; index++) {
            ReviewRequestInbox inbox = ReviewRequestInbox.pending(
                    "api-key:batch:" + index,
                    "api-key",
                    1000L + index,
                    "{\"pullRequestTitle\":\"batch-" + index + "\"}",
                    Instant.parse("2026-02-24T00:00:00Z")
            );
            setProcessingState(inbox, base.minusSeconds(120L + index), 1);
            ReviewRequestInbox saved = reviewRequestInboxRepository.save(inbox);
            inboxIds.add(saved.getId());
        }

        // when
        int recoveredCount = reviewRequestInboxRepository.recoverTimeoutProcessing(
                base.minusSeconds(60),
                base,
                "timeout",
                3,
                100
        );

        // then
        List<ReviewRequestInbox> actualInboxes = inboxIds.stream()
                                                         .map(inboxId -> reviewRequestInboxMybatisMapper.findDomainById(inboxId).orElseThrow())
                                                         .toList();
        List<ReviewRequestInboxHistory> actualHistories = reviewRequestInboxHistoryMybatisMapper.findAllDomains();
        assertAll(
                () -> assertThat(recoveredCount).isEqualTo(100),
                () -> assertThat(actualInboxes).filteredOn(inbox -> inbox.getStatus() == ReviewRequestInboxStatus.RETRY_PENDING)
                        .hasSize(100),
                () -> assertThat(actualInboxes).filteredOn(inbox -> inbox.getStatus() == ReviewRequestInboxStatus.PROCESSING)
                        .hasSize(1),
                () -> assertThat(actualHistories).filteredOn(history -> history.getStatus() == ReviewRequestInboxStatus.RETRY_PENDING)
                        .hasSize(100),
                () -> assertThat(actualHistories).filteredOn(
                        history -> history.getFailure().type() == ReviewRequestInboxFailureType.PROCESSING_TIMEOUT
                ).hasSize(100)
        );
    }

    @Test
    void 완료된_review_inbox와_history를_같이_삭제한다() {
        // given
        ReviewRequestInbox oldProcessedInbox = ReviewRequestInbox.pending(
                "cleanup-review-processed",
                "api-key",
                3001L,
                "{\"pullRequestTitle\":\"old\"}",
                Instant.parse("2026-02-24T00:00:00Z")
        );
        setProcessingState(oldProcessedInbox, Instant.parse("2026-02-24T00:01:00Z"), 1);
        ReviewRequestInboxHistory oldProcessedHistory = oldProcessedInbox.markProcessed(
                Instant.parse("2026-02-24T00:02:00Z")
        );
        ReviewRequestInbox savedProcessedInbox = saveInboxWithHistory(oldProcessedInbox, oldProcessedHistory);

        ReviewRequestInbox recentFailedInbox = ReviewRequestInbox.pending(
                "cleanup-review-failed",
                "api-key",
                3002L,
                "{\"pullRequestTitle\":\"recent\"}",
                Instant.parse("2026-02-24T00:00:00Z")
        );
        setProcessingState(recentFailedInbox, Instant.parse("2026-02-24T00:03:00Z"), 1);
        ReviewRequestInboxHistory recentFailedHistory = recentFailedInbox.markFailed(
                Instant.parse("2026-02-24T00:09:00Z"),
                "failure",
                ReviewRequestInboxFailureType.NON_RETRYABLE
        );
        ReviewRequestInbox savedFailedInbox = saveInboxWithHistory(recentFailedInbox, recentFailedHistory);

        // when
        int deletedCount = reviewRequestInboxRepository.deleteCompletedBefore(
                Instant.parse("2026-02-24T00:05:00Z"),
                100
        );

        // then
        List<ReviewRequestInboxHistory> histories = reviewRequestInboxHistoryMybatisMapper.findAllDomains();
        assertAll(
                () -> assertThat(deletedCount).isEqualTo(1),
                () -> assertThat(reviewRequestInboxMybatisMapper.findDomainById(savedProcessedInbox.getId())).isEmpty(),
                () -> assertThat(reviewRequestInboxMybatisMapper.findDomainById(savedFailedInbox.getId())).isPresent(),
                () -> assertThat(histories)
                        .filteredOn(history -> history.getInboxId().equals(savedProcessedInbox.getId()))
                        .isEmpty(),
                () -> assertThat(histories)
                        .filteredOn(history -> history.getInboxId().equals(savedFailedInbox.getId()))
                        .hasSize(1)
        );
    }

    @Test
    void deleteCompletedBefore는_batch_크기만큼만_삭제한다() {
        // given
        ReviewRequestInbox firstInbox = createProcessedInbox(
                "cleanup-review-batch-1",
                3101L,
                Instant.parse("2026-02-24T00:01:00Z")
        );
        ReviewRequestInbox secondInbox = createProcessedInbox(
                "cleanup-review-batch-2",
                3102L,
                Instant.parse("2026-02-24T00:02:00Z")
        );

        // when
        int deletedCount = reviewRequestInboxRepository.deleteCompletedBefore(
                Instant.parse("2026-02-24T00:05:00Z"),
                1
        );

        // then
        assertAll(
                () -> assertThat(deletedCount).isEqualTo(1),
                () -> assertThat(reviewRequestInboxMybatisMapper.findDomainById(firstInbox.getId())).isEmpty(),
                () -> assertThat(reviewRequestInboxMybatisMapper.findDomainById(secondInbox.getId())).isPresent()
        );
    }

    @Test
    void PROCESSING_타임아웃_복구는_재시도_가능과_소진건을_합쳐_배치_크기만큼만_처리한다() {
        // given
        Instant base = Instant.parse("2026-02-24T00:20:00Z");
        List<Long> inboxIds = new ArrayList<>();

        for (int index = 0; index < 50; index++) {
            ReviewRequestInbox retryableInbox = ReviewRequestInbox.pending(
                    "api-key:mixed:retry:" + index,
                    "api-key",
                    2000L + index,
                    "{\"pullRequestTitle\":\"retry-" + index + "\"}",
                    Instant.parse("2026-02-24T00:00:00Z")
            );
            setProcessingState(retryableInbox, base.minusSeconds(200L + index), 1);
            ReviewRequestInbox saved = reviewRequestInboxRepository.save(retryableInbox);
            inboxIds.add(saved.getId());
        }

        for (int index = 0; index < 51; index++) {
            ReviewRequestInbox exhaustedInbox = ReviewRequestInbox.pending(
                    "api-key:mixed:failed:" + index,
                    "api-key",
                    3000L + index,
                    "{\"pullRequestTitle\":\"failed-" + index + "\"}",
                    Instant.parse("2026-02-24T00:00:00Z")
            );
            setProcessingState(exhaustedInbox, base.minusSeconds(100L + index), 3);
            ReviewRequestInbox saved = reviewRequestInboxRepository.save(exhaustedInbox);
            inboxIds.add(saved.getId());
        }

        // when
        int recoveredCount = reviewRequestInboxRepository.recoverTimeoutProcessing(
                base.minusSeconds(60),
                base,
                "timeout",
                3,
                100
        );

        // then
        List<ReviewRequestInbox> actualInboxes = inboxIds.stream()
                                                         .map(inboxId -> reviewRequestInboxMybatisMapper.findDomainById(inboxId).orElseThrow())
                                                         .toList();
        List<ReviewRequestInboxHistory> actualHistories = reviewRequestInboxHistoryMybatisMapper.findAllDomains();

        assertAll(
                () -> assertThat(recoveredCount).isEqualTo(100),
                () -> assertThat(actualInboxes).filteredOn(inbox -> inbox.getStatus() == ReviewRequestInboxStatus.RETRY_PENDING)
                        .hasSize(50),
                () -> assertThat(actualInboxes).filteredOn(inbox -> inbox.getStatus() == ReviewRequestInboxStatus.FAILED)
                        .hasSize(50),
                () -> assertThat(actualInboxes).filteredOn(inbox -> inbox.getStatus() == ReviewRequestInboxStatus.PROCESSING)
                        .hasSize(1),
                () -> assertThat(actualHistories).filteredOn(
                        history -> history.getFailure().type() == ReviewRequestInboxFailureType.PROCESSING_TIMEOUT
                ).hasSize(50),
                () -> assertThat(actualHistories).filteredOn(
                        history -> history.getFailure().type() == ReviewRequestInboxFailureType.RETRY_EXHAUSTED
                ).hasSize(50)
        );
    }

    @Test
    void 잠겨있는_PROCESSING_타임아웃_행은_recovery에서_건너뛴다() throws Exception {
        // given
        ReviewRequestInbox inbox = ReviewRequestInbox.pending(
                "api-key:locked",
                "api-key",
                9000L,
                "{\"pullRequestTitle\":\"locked\"}",
                Instant.parse("2026-02-24T00:00:00Z")
        );
        setProcessingState(inbox, Instant.parse("2026-02-24T00:01:00Z"), 1);
        ReviewRequestInbox saved = reviewRequestInboxRepository.save(inbox);
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Future<?> lockFuture = executorService.submit(() -> {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    namedParameterJdbcTemplate.query(
                            """
                            SELECT id
                            FROM review_request_inbox
                            WHERE id = :inboxId
                            FOR UPDATE
                            """,
                            new MapSqlParameterSource().addValue("inboxId", saved.getId()),
                            (resultSet, rowNum) -> resultSet.getLong(1)
                    );
                    lockAcquired.countDown();
                    awaitLatch(releaseLock);
                });
            });
            awaitLatch(lockAcquired);

            // when
            int skippedCount = reviewRequestInboxRepository.recoverTimeoutProcessing(
                    Instant.parse("2026-02-24T00:02:00Z"),
                    Instant.parse("2026-02-24T00:05:00Z"),
                    "timeout",
                    3,
                    100
            );

            // then
            ReviewRequestInbox lockedInbox = reviewRequestInboxMybatisMapper.findDomainById(saved.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(skippedCount).isZero(),
                    () -> assertThat(lockedInbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSING),
                    () -> assertThat(
                            reviewRequestInboxHistoryMybatisMapper.findDomainsByInboxIdOrderByIdDesc(saved.getId())
                    ).isEmpty()
            );

            releaseLock.countDown();
            lockFuture.get(5, TimeUnit.SECONDS);
        } finally {
            executorService.shutdownNow();
        }
    }

    private void setProcessingState(ReviewRequestInbox inbox, Instant processingStartedAt, int processingAttempt) {
        ReflectionTestUtils.setField(inbox, "status", ReviewRequestInboxStatus.PROCESSING);
        ReflectionTestUtils.setField(inbox, "processingLease", BoxProcessingLease.claimed(processingStartedAt));
        ReflectionTestUtils.setField(inbox, "processedTime", BoxEventTime.absent());
        ReflectionTestUtils.setField(inbox, "processingAttempt", processingAttempt);
        ReflectionTestUtils.setField(inbox, "failedTime", BoxEventTime.absent());
        ReflectionTestUtils.setField(inbox, "failure", BoxFailureSnapshot.absent());
    }

    private ReviewRequestInbox createProcessedInbox(
            String idempotencyKey,
            Long githubPullRequestId,
            Instant processedAt
    ) {
        ReviewRequestInbox inbox = ReviewRequestInbox.pending(
                idempotencyKey,
                "api-key",
                githubPullRequestId,
                "{\"pullRequestTitle\":\"processed\"}",
                Instant.parse("2026-02-24T00:00:00Z")
        );
        setProcessingState(inbox, processedAt.minusSeconds(30L), 1);
        ReviewRequestInboxHistory history = inbox.markProcessed(processedAt);
        return saveInboxWithHistory(inbox, history);
    }

    private ReviewRequestInbox saveInboxWithHistory(
            ReviewRequestInbox inbox,
            ReviewRequestInboxHistory history
    ) {
        ReviewRequestInbox savedInbox = reviewRequestInboxRepository.save(inbox);
        reviewRequestInboxHistoryMybatisMapper.insert(
                ReviewRequestInboxHistoryRow.from(history.bindInboxId(savedInbox.getId()))
        );
        return savedInbox;
    }

    private ReviewRequestInbox findByIdempotencyKey(String idempotencyKey) {
        for (ReviewRequestInbox inbox : reviewRequestInboxMybatisMapper.findAllDomains()) {
            if (idempotencyKey.equals(inbox.getIdempotencyKey())) {
                return inbox;
            }
        }

        throw new IllegalArgumentException("해당 idempotencyKey의 inbox가 없습니다.");
    }

    private ReviewRequestInboxHistory findLatestHistory(Long inboxId) {
        List<ReviewRequestInboxHistory> histories =
                reviewRequestInboxHistoryMybatisMapper.findDomainsByInboxIdOrderByIdDesc(inboxId);
        if (histories.isEmpty()) {
            throw new IllegalArgumentException("해당 inboxId의 history가 없습니다.");
        }

        return histories.getFirst();
    }

    private LocalDateTime findUpdatedAt(Long inboxId) {
        return namedParameterJdbcTemplate.queryForObject(
                """
                SELECT updated_at
                FROM review_request_inbox
                WHERE id = :inboxId
                """,
                new MapSqlParameterSource().addValue("inboxId", inboxId),
                LocalDateTime.class
        );
    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            boolean completed = latch.await(5, TimeUnit.SECONDS);
            if (!completed) {
                throw new IllegalStateException("락 대기 중 시간이 초과되었습니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("락 대기 중 인터럽트가 발생했습니다.", e);
        }
    }
}
