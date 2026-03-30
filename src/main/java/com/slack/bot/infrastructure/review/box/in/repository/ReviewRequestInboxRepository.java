package com.slack.bot.infrastructure.review.box.in.repository;

import com.slack.bot.infrastructure.review.box.in.ReviewRequestInbox;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxHistory;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface ReviewRequestInboxRepository {

    void upsertPending(
            String idempotencyKey,
            String apiKey,
            Long githubPullRequestId,
            String requestJson,
            Instant availableAt
    );

    Optional<Long> claimNextId(
            Instant processingStartedAt,
            Instant availableBeforeOrAt,
            Collection<Long> excludedInboxIds
    );

    boolean renewProcessingLease(
            Long inboxId,
            Instant currentProcessingStartedAt,
            Instant renewedProcessingStartedAt
    );

    Optional<ReviewRequestInbox> findById(Long inboxId);

    int recoverTimeoutProcessing(
            Instant processingStartedBefore,
            Instant failedAt,
            String failureReason,
            int maxAttempts
    );

    boolean saveIfProcessingLeaseMatched(
            ReviewRequestInbox inbox,
            Instant claimedProcessingStartedAt
    );

    boolean saveIfProcessingLeaseMatched(
            ReviewRequestInbox inbox,
            ReviewRequestInboxHistory history,
            Instant claimedProcessingStartedAt
    );

    ReviewRequestInbox save(ReviewRequestInbox inbox);
}
