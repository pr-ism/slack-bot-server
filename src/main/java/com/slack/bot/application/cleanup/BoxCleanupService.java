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

        int interactionInboxDeleted = cleanDomain(
                "interaction inbox",
                () -> slackInteractionInboxRepository.deleteCompletedBefore(completedBefore, deleteBatchSize)
        );
        int interactionOutboxDeleted = cleanDomain(
                "interaction outbox",
                () -> slackNotificationOutboxRepository.deleteCompletedBefore(completedBefore, deleteBatchSize)
        );
        int reviewInboxDeleted = cleanDomain(
                "review inbox",
                () -> reviewRequestInboxRepository.deleteCompletedBefore(completedBefore, deleteBatchSize)
        );
        int reviewOutboxDeleted = cleanDomain(
                "review outbox",
                () -> reviewNotificationOutboxRepository.deleteCompletedBefore(completedBefore, deleteBatchSize)
        );

        return new CleanupResult(
                interactionInboxDeleted,
                interactionOutboxDeleted,
                reviewInboxDeleted,
                reviewOutboxDeleted
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

    private int cleanDomain(String domainName, CleanupAction cleanupAction) {
        try {
            return cleanupAction.clean();
        } catch (Exception exception) {
            log.error("{} cleanup 실행에 실패했습니다.", domainName, exception);
            return 0;
        }
    }

    @FunctionalInterface
    private interface CleanupAction {

        int clean();
    }

    public record CleanupResult(
            int interactionInboxDeleted,
            int interactionOutboxDeleted,
            int reviewInboxDeleted,
            int reviewOutboxDeleted
    ) {

        public int totalDeleted() {
            return interactionInboxDeleted
                    + interactionOutboxDeleted
                    + reviewInboxDeleted
                    + reviewOutboxDeleted;
        }
    }
}
