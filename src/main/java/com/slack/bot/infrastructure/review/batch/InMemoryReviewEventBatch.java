package com.slack.bot.infrastructure.review.batch;

import com.slack.bot.application.review.ReviewEventBatch;
import com.slack.bot.application.review.box.in.ReviewRequestInboxProcessor;
import com.slack.bot.application.review.dto.ReviewNotificationPayload;
import com.slack.bot.application.review.dto.request.ReviewAssignmentRequest;
import com.slack.bot.application.round.ReviewRequestRoundCoordinator;
import com.slack.bot.application.round.dto.ReviewRoundRegistrationResultDto;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InMemoryReviewEventBatch implements ReviewEventBatch {

    private static final long DEFAULT_BATCH_WINDOW_MILLIS = 5_000L;
    private static final int DEFAULT_PROCESS_BATCH_SIZE = 30;

    private final TaskScheduler taskScheduler;
    private final ReviewRequestInboxProcessor reviewRequestInboxProcessor;
    private final ReviewRequestRoundCoordinator reviewRequestRoundCoordinator;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> scheduledVersions = new ConcurrentHashMap<>();
    private final AtomicLong scheduleVersionSequence = new AtomicLong();

    @Value("${review.notification.batch.window-millis:" + DEFAULT_BATCH_WINDOW_MILLIS + "}")
    private long batchWindowMillis;

    @Value("${review.notification.inbox.batch-size:" + DEFAULT_PROCESS_BATCH_SIZE + "}")
    private int batchSize;

    @Override
    public void buffer(String apiKey, ReviewAssignmentRequest request) {
        ReviewRoundRegistrationResultDto roundResult = reviewRequestRoundCoordinator.register(apiKey, request);
        if (!roundResult.shouldNotify()) {
            return;
        }

        String reviewRoundKey = Integer.toString(roundResult.roundNumber());
        ReviewNotificationPayload requestForNotification = ReviewNotificationPayload.of(
                request,
                roundResult.reviewersToMention(),
                reviewRoundKey
        );
        reviewRequestInboxProcessor.enqueue(apiKey, requestForNotification, batchWindowMillis);
        scheduleFlush(buildBatchKey(apiKey, request.githubPullRequestId(), reviewRoundKey));
    }

    private String buildBatchKey(String apiKey, Long githubPullRequestId, String reviewRoundKey) {
        return apiKey + ":" + githubPullRequestId + ":" + reviewRoundKey;
    }

    private void scheduleFlush(String batchKey) {
        long version = scheduleVersionSequence.incrementAndGet();
        scheduledVersions.put(batchKey, version);

        ScheduledFuture<?> nextFuture = taskScheduler.schedule(
                () -> flush(batchKey, version),
                Instant.now().plusMillis(batchWindowMillis)
        );
        ScheduledFuture<?> previousFuture = scheduledTasks.put(batchKey, nextFuture);
        if (previousFuture != null) {
            previousFuture.cancel(false);
        }
    }

    private void flush(String batchKey, long version) {
        try {
            reviewRequestInboxProcessor.processPending(batchSize);
        } finally {
            Long currentVersion = scheduledVersions.get(batchKey);
            if (currentVersion != null && currentVersion == version) {
                scheduledVersions.remove(batchKey);
                scheduledTasks.remove(batchKey);
            }
        }
    }
}
