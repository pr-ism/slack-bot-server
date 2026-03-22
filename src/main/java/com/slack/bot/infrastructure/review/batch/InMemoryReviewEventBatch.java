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

        ReviewNotificationPayload requestForNotification = ReviewNotificationPayload.of(
                request,
                roundResult.reviewersToMention(),
                roundResult.coalescingKey()
        );
        reviewRequestInboxProcessor.enqueue(apiKey, requestForNotification, batchWindowMillis);
        scheduleFlush(roundResult.coalescingKey());
    }

    private void scheduleFlush(String coalescingKey) {
        long version = scheduleVersionSequence.incrementAndGet();
        scheduledVersions.put(coalescingKey, version);

        ScheduledFuture<?> nextFuture = taskScheduler.schedule(
                () -> flush(coalescingKey, version),
                Instant.now().plusMillis(batchWindowMillis)
        );
        ScheduledFuture<?> previousFuture = scheduledTasks.put(coalescingKey, nextFuture);
        if (previousFuture != null) {
            previousFuture.cancel(false);
        }
    }

    private void flush(String coalescingKey, long version) {
        try {
            reviewRequestInboxProcessor.processPending(batchSize);
        } finally {
            Long currentVersion = scheduledVersions.get(coalescingKey);
            if (currentVersion != null && currentVersion == version) {
                scheduledVersions.remove(coalescingKey);
                scheduledTasks.remove(coalescingKey);
            }
        }
    }
}
