package com.slack.bot.infrastructure.common;

import java.time.Instant;

public sealed interface BoxProcessingLease
        permits BoxProcessingLease.IdleBoxProcessingLease, BoxProcessingLease.ClaimedBoxProcessingLease {

    static BoxProcessingLease idle() {
        return IdleBoxProcessingLease.INSTANCE;
    }

    static BoxProcessingLease claimed(Instant startedAt) {
        if (startedAt == null) {
            throw new IllegalArgumentException("processingStartedAt은 비어 있을 수 없습니다.");
        }

        return new ClaimedBoxProcessingLease(startedAt);
    }

    boolean isClaimed();

    default Instant startedAt() {
        throw new IllegalStateException("lease를 보유하지 않은 상태입니다.");
    }

    final class IdleBoxProcessingLease implements BoxProcessingLease {

        private static final IdleBoxProcessingLease INSTANCE = new IdleBoxProcessingLease();

        private IdleBoxProcessingLease() {
        }

        @Override
        public boolean isClaimed() {
            return false;
        }
    }

    record ClaimedBoxProcessingLease(Instant startedAt) implements BoxProcessingLease {

        public ClaimedBoxProcessingLease {
            if (startedAt == null) {
                throw new IllegalArgumentException("processingStartedAt은 비어 있을 수 없습니다.");
            }
        }

        @Override
        public boolean isClaimed() {
            return true;
        }
    }
}
