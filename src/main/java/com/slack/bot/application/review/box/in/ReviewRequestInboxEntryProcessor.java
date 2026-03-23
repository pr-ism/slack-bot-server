package com.slack.bot.application.review.box.in;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interaction.box.BoxFailureReasonTruncator;
import com.slack.bot.application.interaction.box.retry.InteractionRetryExceptionClassifier;
import com.slack.bot.application.review.ReviewNotificationService;
import com.slack.bot.application.review.box.ReviewNotificationSourceContext;
import com.slack.bot.application.review.dto.ReviewNotificationPayload;
import com.slack.bot.global.config.properties.InteractionRetryProperties;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInbox;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxFailureType;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxStatus;
import com.slack.bot.infrastructure.review.box.in.repository.ReviewRequestInboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewRequestInboxEntryProcessor {

    private static final String UNKNOWN_FAILURE_REASON = "unknown failure";
    private static final String REVIEW_REQUEST_INBOX_SOURCE_PREFIX = "REVIEW_REQUEST_INBOX";

    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final ReviewNotificationService reviewNotificationService;
    private final ReviewNotificationSourceContext reviewNotificationSourceContext;
    private final BoxFailureReasonTruncator failureReasonTruncator;
    private final InteractionRetryProperties interactionRetryProperties;
    private final ReviewRequestInboxRepository reviewRequestInboxRepository;
    private final InteractionRetryExceptionClassifier retryExceptionClassifier;

    @Transactional
    public void processClaimedInbox(Long inboxId) {
        if (inboxId == null) {
            return;
        }

        reviewRequestInboxRepository.findById(inboxId)
                                    .ifPresentOrElse(
                                            inbox -> processInTransaction(inbox),
                                            () -> log.warn(
                                                    "PROCESSING으로 전이된 review_request inbox를 조회하지 못했습니다. inboxId={}",
                                                    inboxId
                                            )
                                    );
    }

    private void processInTransaction(ReviewRequestInbox inbox) {
        deserializeRequest(inbox).ifPresent(request -> {
            String sourceKey = resolveSourceKey(inbox);
            reviewNotificationSourceContext.withSourceKey(
                    sourceKey,
                    () -> reviewNotificationService.sendSimpleNotification(inbox.getApiKey(), request)
            );

            inbox.markProcessed(clock.instant());
            reviewRequestInboxRepository.save(inbox);
        });
    }

    private Optional<ReviewNotificationPayload> deserializeRequest(ReviewRequestInbox inbox) {
        try {
            return Optional.of(
                    objectMapper.readValue(
                            inbox.getRequestJson(),
                            ReviewNotificationPayload.class
                    )
            );
        } catch (Exception e) {
            log.error("review_request inbox 처리에 실패했습니다. inboxId={}", inbox.getId(), e);
            markFailureStatus(inbox, e);
            reviewRequestInboxRepository.save(inbox);
            return Optional.empty();
        }
    }

    private void markFailureStatus(ReviewRequestInbox inbox, Exception e) {
        if (inbox.getStatus() != ReviewRequestInboxStatus.PROCESSING) {
            log.warn(
                    "PROCESSING이 아닌 상태에서 실패 처리를 시도했습니다. inboxId={}, status={}",
                    inbox.getId(),
                    inbox.getStatus()
            );
            return;
        }

        String reason = resolveFailureReason(e);

        if (!retryExceptionClassifier.isRetryable(e)) {
            inbox.markFailed(clock.instant(), reason, ReviewRequestInboxFailureType.NON_RETRYABLE);
            return;
        }

        if (inbox.getProcessingAttempt() < interactionRetryProperties.inbox().maxAttempts()) {
            inbox.markRetryPending(clock.instant(), reason);
            return;
        }

        inbox.markFailed(clock.instant(), reason, ReviewRequestInboxFailureType.RETRY_EXHAUSTED);
    }

    private String resolveFailureReason(Exception e) {
        String reason = failureReasonTruncator.truncate(e.getMessage());

        if (reason == null || reason.isBlank()) {
            return UNKNOWN_FAILURE_REASON;
        }

        return reason;
    }

    private String resolveSourceKey(ReviewRequestInbox inbox) {
        long availableAtEpochMillis = resolveAvailableAtEpochMillis(inbox.getAvailableAt());

        return REVIEW_REQUEST_INBOX_SOURCE_PREFIX
                + ":" + inbox.getIdempotencyKey()
                + ":" + availableAtEpochMillis;
    }

    private long resolveAvailableAtEpochMillis(Instant availableAt) {
        if (availableAt == null) {
            return 0L;
        }

        return availableAt.toEpochMilli();
    }
}
