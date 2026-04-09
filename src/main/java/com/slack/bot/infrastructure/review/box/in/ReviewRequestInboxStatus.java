package com.slack.bot.infrastructure.review.box.in;

import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import java.util.EnumSet;
import java.util.Set;

public enum ReviewRequestInboxStatus {
    PENDING(notCompleted()),
    RETRY_PENDING(requiresFailureType(
            "RETRY_PENDING history의 failureType이 올바르지 않습니다.",
            EnumSet.of(
                    ReviewRequestInboxFailureType.RETRYABLE,
                    ReviewRequestInboxFailureType.PROCESSING_TIMEOUT
            )
    )),
    PROCESSING(notCompleted()),
    PROCESSED(requiresNoFailure("PROCESSED history에는 실패 정보가 없어야 합니다.")),
    FAILED(requiresFailureType(
            "FAILED history의 failureType이 올바르지 않습니다.",
            EnumSet.of(
                    ReviewRequestInboxFailureType.NON_RETRYABLE,
                    ReviewRequestInboxFailureType.RETRY_EXHAUSTED
            )
    ));

    private final HistoryValidationRule historyValidationRule;

    ReviewRequestInboxStatus(HistoryValidationRule historyValidationRule) {
        this.historyValidationRule = historyValidationRule;
    }

    public void validateHistoryStatus() {
        historyValidationRule.validateStatus();
    }

    public void validateHistoryFailure(BoxFailureSnapshot<ReviewRequestInboxFailureType> failure) {
        historyValidationRule.validateFailure(failure);
    }

    private static HistoryValidationRule notCompleted() {
        return new HistoryValidationRule(
                () -> {
                    throw new IllegalArgumentException("history status는 완료된 상태여야 합니다.");
                },
                failure -> { }
        );
    }

    private static HistoryValidationRule requiresNoFailure(String message) {
        return new HistoryValidationRule(
                () -> { },
                failure -> {
                    validateFailureNotNull(failure);
                    if (failure.isPresent()) {
                        throw new IllegalArgumentException(message);
                    }
                }
        );
    }

    private static HistoryValidationRule requiresFailureType(
            String invalidFailureTypeMessage,
            Set<ReviewRequestInboxFailureType> allowedFailureTypes
    ) {
        return new HistoryValidationRule(
                () -> { },
                failure -> {
                    validateFailureNotNull(failure);
                    if (!failure.isPresent()) {
                        throw new IllegalArgumentException("완료 실패 정보는 비어 있을 수 없습니다.");
                    }

                    ReviewRequestInboxFailureType failureType = failure.type();
                    if (!allowedFailureTypes.contains(failureType)) {
                        throw new IllegalArgumentException(invalidFailureTypeMessage);
                    }
                }
        );
    }

    private static void validateFailureNotNull(BoxFailureSnapshot<ReviewRequestInboxFailureType> failure) {
        if (failure == null) {
            throw new IllegalArgumentException("failure는 비어 있을 수 없습니다.");
        }
    }

    private record HistoryValidationRule(
            Runnable statusValidator,
            HistoryFailureValidator failureValidator
    ) {

        private void validateStatus() {
            statusValidator.run();
        }

        private void validateFailure(BoxFailureSnapshot<ReviewRequestInboxFailureType> failure) {
            validateStatus();
            failureValidator.validate(failure);
        }
    }

    @FunctionalInterface
    private interface HistoryFailureValidator {

        void validate(BoxFailureSnapshot<ReviewRequestInboxFailureType> failure);
    }
}
