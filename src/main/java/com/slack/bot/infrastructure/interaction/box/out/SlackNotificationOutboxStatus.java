package com.slack.bot.infrastructure.interaction.box.out;

import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import java.util.EnumSet;
import java.util.Set;

public enum SlackNotificationOutboxStatus {
    PENDING(notCompleted()),
    RETRY_PENDING(requiresFailureType(
            "RETRY_PENDING history의 failureType이 올바르지 않습니다.",
            EnumSet.of(
                    SlackInteractionFailureType.RETRYABLE,
                    SlackInteractionFailureType.PROCESSING_TIMEOUT
            )
    )),
    PROCESSING(notCompleted()),
    SENT(requiresNoFailure("SENT history에는 실패 정보가 없어야 합니다.")),
    FAILED(requiresFailureType(
            "FAILED history의 failureType이 올바르지 않습니다.",
            EnumSet.of(
                    SlackInteractionFailureType.BUSINESS_INVARIANT,
                    SlackInteractionFailureType.RETRY_EXHAUSTED
            )
    ));

    private final HistoryValidationRule historyValidationRule;

    SlackNotificationOutboxStatus(HistoryValidationRule historyValidationRule) {
        this.historyValidationRule = historyValidationRule;
    }

    public void validateHistoryStatus() {
        historyValidationRule.validateStatus();
    }

    public void validateHistoryFailure(BoxFailureSnapshot<SlackInteractionFailureType> failure) {
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
            Set<SlackInteractionFailureType> allowedFailureTypes
    ) {
        return new HistoryValidationRule(
                () -> { },
                failure -> {
                    validateFailureNotNull(failure);
                    if (!failure.isPresent()) {
                        throw new IllegalArgumentException("완료 실패 정보는 비어 있을 수 없습니다.");
                    }

                    SlackInteractionFailureType failureType = failure.type();
                    validateCompleteFailureType(failureType);
                    if (!allowedFailureTypes.contains(failureType)) {
                        throw new IllegalArgumentException(invalidFailureTypeMessage);
                    }
                }
        );
    }

    private static void validateFailureNotNull(BoxFailureSnapshot<SlackInteractionFailureType> failure) {
        if (failure == null) {
            throw new IllegalArgumentException("failure는 비어 있을 수 없습니다.");
        }
    }

    private static void validateCompleteFailureType(SlackInteractionFailureType failureType) {
        if (failureType == null
                || failureType == SlackInteractionFailureType.ABSENT
                || failureType == SlackInteractionFailureType.NONE) {
            throw new IllegalArgumentException("완료 실패 정보의 failureType이 올바르지 않습니다.");
        }
    }

    private record HistoryValidationRule(
            Runnable statusValidator,
            HistoryFailureValidator failureValidator
    ) {

        private void validateStatus() {
            statusValidator.run();
        }

        private void validateFailure(BoxFailureSnapshot<SlackInteractionFailureType> failure) {
            validateStatus();
            failureValidator.validate(failure);
        }
    }

    @FunctionalInterface
    private interface HistoryFailureValidator {

        void validate(BoxFailureSnapshot<SlackInteractionFailureType> failure);
    }
}
