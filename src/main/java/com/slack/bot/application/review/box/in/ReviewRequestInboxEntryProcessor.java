package com.slack.bot.application.review.box.in;

import com.slack.bot.infrastructure.review.box.in.ReviewRequestInbox;
import com.slack.bot.infrastructure.review.box.in.repository.ReviewRequestInboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewRequestInboxEntryProcessor {

    private final Clock clock;
    private final ReviewRequestInboxRepository reviewRequestInboxRepository;
    private final ReviewRequestInboxTransactionalProcessor reviewRequestInboxTransactionalProcessor;

    public void processClaimedInbox(Long inboxId, Instant claimedProcessingStartedAt) {
        if (inboxId == null || claimedProcessingStartedAt == null) {
            return;
        }

        reviewRequestInboxRepository.findById(inboxId)
                                    .ifPresentOrElse(
                                            inbox -> processClaimedInbox(inbox, claimedProcessingStartedAt),
                                            () -> log.warn(
                                                    "PROCESSING으로 전이된 review_request inbox를 조회하지 못했습니다. inboxId={}",
                                                    inboxId
                                            )
                                    );
    }

    private void processClaimedInbox(
            ReviewRequestInbox inbox,
            Instant claimedProcessingStartedAt
    ) {
        if (!hasProcessingLease(inbox, claimedProcessingStartedAt)) {
            logLeaseLost(inbox.getId(), claimedProcessingStartedAt, actualProcessingStartedAt(inbox));
            return;
        }

        Instant renewedProcessingStartedAt = currentLeaseStartedAt();
        boolean renewed = reviewRequestInboxRepository.renewProcessingLease(
                inbox.getId(),
                claimedProcessingStartedAt,
                renewedProcessingStartedAt
        );
        if (!renewed) {
            logLeaseLost(inbox.getId(), claimedProcessingStartedAt, renewedProcessingStartedAt);
            return;
        }

        inbox.renewProcessingLease(renewedProcessingStartedAt);
        reviewRequestInboxTransactionalProcessor.processInTransaction(
                inbox.getId(),
                renewedProcessingStartedAt
        );
    }

    private Instant currentLeaseStartedAt() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private boolean hasProcessingLease(
            ReviewRequestInbox inbox,
            Instant claimedProcessingStartedAt
    ) {
        return inbox.hasClaimedProcessingLease(claimedProcessingStartedAt);
    }

    private Instant actualProcessingStartedAt(ReviewRequestInbox inbox) {
        if (!inbox.hasClaimedProcessingLease()) {
            return null;
        }

        return inbox.currentProcessingLeaseStartedAt();
    }

    private void logLeaseLost(
            Long inboxId,
            Instant claimedProcessingStartedAt,
            Instant actualProcessingStartedAt
    ) {
        log.warn(
                "review_request inbox 처리 lease를 상실해 처리를 건너뜁니다. inboxId={}, claimedProcessingStartedAt={}, actualProcessingStartedAt={}",
                inboxId,
                claimedProcessingStartedAt,
                actualProcessingStartedAt
        );
    }
}
