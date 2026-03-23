package com.slack.bot.infrastructure.review.box.in.repository;

import com.slack.bot.infrastructure.review.box.in.ReviewRequestInbox;
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

    Optional<ReviewRequestInbox> findById(Long inboxId);

    int recoverTimeoutProcessing(
            Instant processingStartedBefore,
            Instant failedAt,
            String failureReason,
            int maxAttempts
    );

    ReviewRequestInbox save(ReviewRequestInbox inbox);
}
