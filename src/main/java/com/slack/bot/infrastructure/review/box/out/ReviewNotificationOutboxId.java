package com.slack.bot.infrastructure.review.box.out;

public sealed interface ReviewNotificationOutboxId
        permits ReviewNotificationOutboxId.UnassignedReviewNotificationOutboxId,
                ReviewNotificationOutboxId.AssignedReviewNotificationOutboxId {

    static ReviewNotificationOutboxId unassigned() {
        return UnassignedReviewNotificationOutboxId.INSTANCE;
    }

    static ReviewNotificationOutboxId assigned(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("outboxId는 비어 있을 수 없습니다.");
        }

        return new AssignedReviewNotificationOutboxId(value);
    }

    boolean isAssigned();

    default Long value() {
        throw new IllegalStateException("outboxId가 할당되지 않았습니다.");
    }

    final class UnassignedReviewNotificationOutboxId implements ReviewNotificationOutboxId {

        private static final UnassignedReviewNotificationOutboxId INSTANCE =
                new UnassignedReviewNotificationOutboxId();

        private UnassignedReviewNotificationOutboxId() {
        }

        @Override
        public boolean isAssigned() {
            return false;
        }
    }

    record AssignedReviewNotificationOutboxId(Long value) implements ReviewNotificationOutboxId {

        public AssignedReviewNotificationOutboxId {
            if (value == null || value <= 0) {
                throw new IllegalArgumentException("outboxId는 비어 있을 수 없습니다.");
            }
        }

        @Override
        public boolean isAssigned() {
            return true;
        }
    }
}
