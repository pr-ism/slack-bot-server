package com.slack.bot.infrastructure.common;

public sealed interface BoxFailureSnapshot<T extends Enum<T>>
        permits BoxFailureSnapshot.AbsentBoxFailureSnapshot, BoxFailureSnapshot.PresentBoxFailureSnapshot {

    static <T extends Enum<T>> BoxFailureSnapshot<T> absent() {
        return new AbsentBoxFailureSnapshot<>();
    }

    static <T extends Enum<T>> BoxFailureSnapshot<T> present(String reason, T type) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("failureReason은 비어 있을 수 없습니다.");
        }
        if (type == null) {
            throw new IllegalArgumentException("failureType은 비어 있을 수 없습니다.");
        }

        return new PresentBoxFailureSnapshot<>(reason, type);
    }

    boolean isPresent();

    default String reason() {
        throw new IllegalStateException("실패 정보가 없는 상태입니다.");
    }

    default T type() {
        throw new IllegalStateException("실패 정보가 없는 상태입니다.");
    }

    final class AbsentBoxFailureSnapshot<T extends Enum<T>> implements BoxFailureSnapshot<T> {

        @Override
        public boolean isPresent() {
            return false;
        }
    }

    record PresentBoxFailureSnapshot<T extends Enum<T>>(String reason, T type) implements BoxFailureSnapshot<T> {

        public PresentBoxFailureSnapshot {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("failureReason은 비어 있을 수 없습니다.");
            }
            if (type == null) {
                throw new IllegalArgumentException("failureType은 비어 있을 수 없습니다.");
            }
        }

        @Override
        public boolean isPresent() {
            return true;
        }
    }
}
