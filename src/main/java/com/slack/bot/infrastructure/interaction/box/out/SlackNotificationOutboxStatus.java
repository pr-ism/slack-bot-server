package com.slack.bot.infrastructure.interaction.box.out;

import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;

public enum SlackNotificationOutboxStatus {
    PENDING {
        @Override
        public void validateHistoryStatus() {
            throw new IllegalArgumentException("history status는 완료된 상태여야 합니다.");
        }
    },
    RETRY_PENDING {
        @Override
        protected void validateHistoryFailureType(SlackInteractionFailureType failureType) {
            if (failureType == SlackInteractionFailureType.RETRYABLE
                    || failureType == SlackInteractionFailureType.PROCESSING_TIMEOUT) {
                return;
            }

            throw new IllegalArgumentException("RETRY_PENDING history의 failureType이 올바르지 않습니다.");
        }
    },
    PROCESSING {
        @Override
        public void validateHistoryStatus() {
            throw new IllegalArgumentException("history status는 완료된 상태여야 합니다.");
        }
    },
    SENT {
        @Override
        public void validateHistoryFailure(BoxFailureSnapshot<SlackInteractionFailureType> failure) {
            if (failure == null) {
                throw new IllegalArgumentException("failure는 비어 있을 수 없습니다.");
            }
            if (failure.isPresent()) {
                throw new IllegalArgumentException("SENT history에는 실패 정보가 없어야 합니다.");
            }
        }
    },
    FAILED {
        @Override
        protected void validateHistoryFailureType(SlackInteractionFailureType failureType) {
            if (failureType == SlackInteractionFailureType.BUSINESS_INVARIANT
                    || failureType == SlackInteractionFailureType.RETRY_EXHAUSTED) {
                return;
            }

            throw new IllegalArgumentException("FAILED history의 failureType이 올바르지 않습니다.");
        }
    };

    public void validateHistoryStatus() {
    }

    public void validateHistoryFailure(BoxFailureSnapshot<SlackInteractionFailureType> failure) {
        validateHistoryStatus();
        if (failure == null) {
            throw new IllegalArgumentException("failure는 비어 있을 수 없습니다.");
        }
        if (!failure.isPresent()) {
            throw new IllegalArgumentException("완료 실패 정보는 비어 있을 수 없습니다.");
        }

        validateHistoryFailureType(failure.type());
    }

    protected void validateHistoryFailureType(SlackInteractionFailureType failureType) {
        if (failureType == null
                || failureType == SlackInteractionFailureType.ABSENT
                || failureType == SlackInteractionFailureType.NONE) {
            throw new IllegalArgumentException("완료 실패 정보의 failureType이 올바르지 않습니다.");
        }
    }
}
