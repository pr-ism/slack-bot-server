package com.slack.bot.application.cleanup;

import com.slack.bot.infrastructure.interaction.box.in.repository.SlackInteractionInboxRepository;
import com.slack.bot.infrastructure.interaction.box.out.repository.SlackNotificationOutboxRepository;
import com.slack.bot.infrastructure.review.box.in.repository.ReviewRequestInboxRepository;
import com.slack.bot.infrastructure.review.box.out.repository.ReviewNotificationOutboxRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoxCleanupService {

    private final SlackInteractionInboxRepository slackInteractionInboxRepository;
    private final SlackNotificationOutboxRepository slackNotificationOutboxRepository;
    private final ReviewRequestInboxRepository reviewRequestInboxRepository;
    private final ReviewNotificationOutboxRepository reviewNotificationOutboxRepository;

    public CleanupResult cleanCompletedBoxes(Instant completedBefore, int deleteBatchSize) {
        validateCompletedBefore(completedBefore);
        validateDeleteBatchSize(deleteBatchSize);

        DomainCleanupResult interactionInboxResult = cleanDomain(
                "interaction inbox",
                () -> slackInteractionInboxRepository.deleteCompletedBefore(completedBefore, deleteBatchSize)
        );
        DomainCleanupResult interactionOutboxResult = cleanDomain(
                "interaction outbox",
                () -> slackNotificationOutboxRepository.deleteCompletedBefore(completedBefore, deleteBatchSize)
        );
        DomainCleanupResult reviewInboxResult = cleanDomain(
                "review inbox",
                () -> reviewRequestInboxRepository.deleteCompletedBefore(completedBefore, deleteBatchSize)
        );
        DomainCleanupResult reviewOutboxResult = cleanDomain(
                "review outbox",
                () -> reviewNotificationOutboxRepository.deleteCompletedBefore(completedBefore, deleteBatchSize)
        );

        return new CleanupResult(
                interactionInboxResult,
                interactionOutboxResult,
                reviewInboxResult,
                reviewOutboxResult
        );
    }

    private void validateCompletedBefore(Instant completedBefore) {
        if (completedBefore == null) {
            throw new IllegalArgumentException("completedBefore는 비어 있을 수 없습니다.");
        }
    }

    private void validateDeleteBatchSize(int deleteBatchSize) {
        if (deleteBatchSize <= 0) {
            throw new IllegalArgumentException("deleteBatchSize는 0보다 커야 합니다.");
        }
    }

    private DomainCleanupResult cleanDomain(String domainName, CleanupAction cleanupAction) {
        try {
            return DomainCleanupResult.succeeded(cleanupAction.clean());
        } catch (Exception exception) {
            log.error("{} cleanup 실행에 실패했습니다.", domainName, exception);
            return DomainCleanupResult.failedResult();
        }
    }

    @FunctionalInterface
    private interface CleanupAction {

        int clean();
    }

    public record CleanupResult(
            DomainCleanupResult interactionInbox,
            DomainCleanupResult interactionOutbox,
            DomainCleanupResult reviewInbox,
            DomainCleanupResult reviewOutbox
    ) {

        public int interactionInboxDeleted() {
            return interactionInbox.deletedCount();
        }

        public int interactionOutboxDeleted() {
            return interactionOutbox.deletedCount();
        }

        public int reviewInboxDeleted() {
            return reviewInbox.deletedCount();
        }

        public int reviewOutboxDeleted() {
            return reviewOutbox.deletedCount();
        }

        public int totalDeleted() {
            return interactionInbox.deletedCount()
                    + interactionOutbox.deletedCount()
                    + reviewInbox.deletedCount()
                    + reviewOutbox.deletedCount();
        }

        public boolean hasFailure() {
            if (interactionInbox.failed()) {
                return true;
            }
            if (interactionOutbox.failed()) {
                return true;
            }
            if (reviewInbox.failed()) {
                return true;
            }

            return reviewOutbox.failed();
        }
    }

    public record DomainCleanupResult(int deletedCount, boolean failed) {

        public static DomainCleanupResult succeeded(int deletedCount) {
            return new DomainCleanupResult(deletedCount, false);
        }

        public static DomainCleanupResult failedResult() {
            return new DomainCleanupResult(0, true);
        }
    }
}
