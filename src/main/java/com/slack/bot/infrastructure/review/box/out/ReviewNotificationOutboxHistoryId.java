package com.slack.bot.infrastructure.review.box.out;

public sealed interface ReviewNotificationOutboxHistoryId
        permits ReviewNotificationOutboxHistoryId.UnassignedReviewNotificationOutboxHistoryId,
                ReviewNotificationOutboxHistoryId.AssignedReviewNotificationOutboxHistoryId {

    static ReviewNotificationOutboxHistoryId unassigned() {
        return UnassignedReviewNotificationOutboxHistoryId.INSTANCE;
    }

    static ReviewNotificationOutboxHistoryId assigned(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("historyId는 비어 있을 수 없습니다.");
        }

        return new AssignedReviewNotificationOutboxHistoryId(value);
    }

    boolean isAssigned();

    default Long value() {
        throw new IllegalStateException("historyId가 할당되지 않았습니다.");
    }

    final class UnassignedReviewNotificationOutboxHistoryId implements ReviewNotificationOutboxHistoryId {

        private static final UnassignedReviewNotificationOutboxHistoryId INSTANCE =
                new UnassignedReviewNotificationOutboxHistoryId();

        private UnassignedReviewNotificationOutboxHistoryId() {
        }

        @Override
        public boolean isAssigned() {
            return false;
        }
    }

    record AssignedReviewNotificationOutboxHistoryId(Long value) implements ReviewNotificationOutboxHistoryId {

        public AssignedReviewNotificationOutboxHistoryId {
            if (value == null || value <= 0) {
                throw new IllegalArgumentException("historyId는 비어 있을 수 없습니다.");
            }
        }

        @Override
        public boolean isAssigned() {
            return true;
        }
    }
}
