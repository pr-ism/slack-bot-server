package com.slack.bot.infrastructure.common;

import java.time.Instant;

public sealed interface BoxEventTime permits BoxEventTime.AbsentBoxEventTime, BoxEventTime.PresentBoxEventTime {

    static BoxEventTime absent() {
        return AbsentBoxEventTime.INSTANCE;
    }

    static BoxEventTime present(Instant occurredAt) {
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt은 비어 있을 수 없습니다.");
        }

        return new PresentBoxEventTime(occurredAt);
    }

    boolean isPresent();

    default Instant occurredAt() {
        throw new IllegalStateException("발생 시각이 없는 상태입니다.");
    }

    final class AbsentBoxEventTime implements BoxEventTime {

        private static final AbsentBoxEventTime INSTANCE = new AbsentBoxEventTime();

        private AbsentBoxEventTime() {
        }

        @Override
        public boolean isPresent() {
            return false;
        }
    }

    record PresentBoxEventTime(Instant occurredAt) implements BoxEventTime {

        public PresentBoxEventTime {
            if (occurredAt == null) {
                throw new IllegalArgumentException("occurredAt은 비어 있을 수 없습니다.");
            }
        }

        @Override
        public boolean isPresent() {
            return true;
        }
    }
}
