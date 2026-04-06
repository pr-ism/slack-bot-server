package com.slack.bot.infrastructure.interaction.box.out;

public sealed interface SlackNotificationOutboxHistoryId
        permits SlackNotificationOutboxHistoryId.UnassignedSlackNotificationOutboxHistoryId,
                SlackNotificationOutboxHistoryId.AssignedSlackNotificationOutboxHistoryId {

    static SlackNotificationOutboxHistoryId unassigned() {
        return UnassignedSlackNotificationOutboxHistoryId.INSTANCE;
    }

    static SlackNotificationOutboxHistoryId assigned(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("historyId는 비어 있을 수 없습니다.");
        }

        return new AssignedSlackNotificationOutboxHistoryId(value);
    }

    boolean isAssigned();

    default Long value() {
        throw new IllegalStateException("historyId가 할당되지 않았습니다.");
    }

    final class UnassignedSlackNotificationOutboxHistoryId implements SlackNotificationOutboxHistoryId {

        private static final UnassignedSlackNotificationOutboxHistoryId INSTANCE =
                new UnassignedSlackNotificationOutboxHistoryId();

        private UnassignedSlackNotificationOutboxHistoryId() {
        }

        @Override
        public boolean isAssigned() {
            return false;
        }
    }

    record AssignedSlackNotificationOutboxHistoryId(Long value) implements SlackNotificationOutboxHistoryId {

        public AssignedSlackNotificationOutboxHistoryId {
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
