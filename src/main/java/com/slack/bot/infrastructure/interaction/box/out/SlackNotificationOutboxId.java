package com.slack.bot.infrastructure.interaction.box.out;

public sealed interface SlackNotificationOutboxId
        permits SlackNotificationOutboxId.UnassignedSlackNotificationOutboxId,
                SlackNotificationOutboxId.AssignedSlackNotificationOutboxId {

    static SlackNotificationOutboxId unassigned() {
        return UnassignedSlackNotificationOutboxId.INSTANCE;
    }

    static SlackNotificationOutboxId assigned(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("outboxId는 비어 있을 수 없습니다.");
        }

        return new AssignedSlackNotificationOutboxId(value);
    }

    boolean isAssigned();

    default Long value() {
        throw new IllegalStateException("outboxId가 할당되지 않았습니다.");
    }

    final class UnassignedSlackNotificationOutboxId implements SlackNotificationOutboxId {

        private static final UnassignedSlackNotificationOutboxId INSTANCE = new UnassignedSlackNotificationOutboxId();

        private UnassignedSlackNotificationOutboxId() {
        }

        @Override
        public boolean isAssigned() {
            return false;
        }
    }

    record AssignedSlackNotificationOutboxId(Long value) implements SlackNotificationOutboxId {

        public AssignedSlackNotificationOutboxId {
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
