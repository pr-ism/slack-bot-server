package com.slack.bot.infrastructure.interaction.box.in;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackInteractionInboxStatusTest {

    @ParameterizedTest
    @MethodSource("completedStatusArguments")
    void 완료_status는_validateHistoryStatus를_통과한다(SlackInteractionInboxStatus status) {
        // when & then
        assertDoesNotThrow(() -> status.validateHistoryStatus());
    }

    @ParameterizedTest
    @MethodSource("notCompletedStatusArguments")
    void 미완료_status는_validateHistoryStatus에서_예외를_던진다(SlackInteractionInboxStatus status) {
        // when & then
        assertThatThrownBy(() -> status.validateHistoryStatus())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("history status는 완료된 상태여야 합니다.");
    }

    @ParameterizedTest
    @MethodSource("successfulFailureArguments")
    void PROCESSED는_absent_failure만_허용한다(
            BoxFailureSnapshot<SlackInteractionFailureType> failure,
            boolean shouldPass
    ) {
        // when & then
        if (shouldPass) {
            assertDoesNotThrow(() -> SlackInteractionInboxStatus.PROCESSED.validateHistoryFailure(failure));
            return;
        }

        assertThatThrownBy(() -> SlackInteractionInboxStatus.PROCESSED.validateHistoryFailure(failure))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PROCESSED history에는 실패 정보가 없어야 합니다.");
    }

    @ParameterizedTest
    @MethodSource("retryPendingFailureArguments")
    void RETRY_PENDING는_허용된_failureType만_통과한다(
            BoxFailureSnapshot<SlackInteractionFailureType> failure,
            boolean shouldPass,
            String expectedMessage
    ) {
        // when & then
        if (shouldPass) {
            assertDoesNotThrow(() -> SlackInteractionInboxStatus.RETRY_PENDING.validateHistoryFailure(failure));
            return;
        }

        assertThatThrownBy(() -> SlackInteractionInboxStatus.RETRY_PENDING.validateHistoryFailure(failure))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);
    }

    @ParameterizedTest
    @MethodSource("failedFailureArguments")
    void FAILED는_허용된_failureType만_통과한다(
            BoxFailureSnapshot<SlackInteractionFailureType> failure,
            boolean shouldPass,
            String expectedMessage
    ) {
        // when & then
        if (shouldPass) {
            assertDoesNotThrow(() -> SlackInteractionInboxStatus.FAILED.validateHistoryFailure(failure));
            return;
        }

        assertThatThrownBy(() -> SlackInteractionInboxStatus.FAILED.validateHistoryFailure(failure))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);
    }

    private static Stream<Arguments> completedStatusArguments() {
        return Stream.of(
                Arguments.of(SlackInteractionInboxStatus.RETRY_PENDING),
                Arguments.of(SlackInteractionInboxStatus.PROCESSED),
                Arguments.of(SlackInteractionInboxStatus.FAILED)
        );
    }

    private static Stream<Arguments> notCompletedStatusArguments() {
        return Stream.of(
                Arguments.of(SlackInteractionInboxStatus.PENDING),
                Arguments.of(SlackInteractionInboxStatus.PROCESSING)
        );
    }

    private static Stream<Arguments> successfulFailureArguments() {
        return Stream.of(
                Arguments.of(BoxFailureSnapshot.absent(), true),
                Arguments.of(
                        BoxFailureSnapshot.present("failure", SlackInteractionFailureType.BUSINESS_INVARIANT),
                        false
                )
        );
    }

    private static Stream<Arguments> retryPendingFailureArguments() {
        return Stream.of(
                Arguments.of(
                        BoxFailureSnapshot.present("retryable", SlackInteractionFailureType.RETRYABLE),
                        true,
                        ""
                ),
                Arguments.of(
                        BoxFailureSnapshot.present("timeout", SlackInteractionFailureType.PROCESSING_TIMEOUT),
                        true,
                        ""
                ),
                Arguments.of(
                        BoxFailureSnapshot.absent(),
                        false,
                        "완료 실패 정보는 비어 있을 수 없습니다."
                ),
                Arguments.of(
                        BoxFailureSnapshot.present("failed", SlackInteractionFailureType.RETRY_EXHAUSTED),
                        false,
                        "RETRY_PENDING history의 failureType이 올바르지 않습니다."
                )
        );
    }

    private static Stream<Arguments> failedFailureArguments() {
        return Stream.of(
                Arguments.of(
                        BoxFailureSnapshot.present("invariant", SlackInteractionFailureType.BUSINESS_INVARIANT),
                        true,
                        ""
                ),
                Arguments.of(
                        BoxFailureSnapshot.present("exhausted", SlackInteractionFailureType.RETRY_EXHAUSTED),
                        true,
                        ""
                ),
                Arguments.of(
                        BoxFailureSnapshot.absent(),
                        false,
                        "완료 실패 정보는 비어 있을 수 없습니다."
                ),
                Arguments.of(
                        BoxFailureSnapshot.present("retryable", SlackInteractionFailureType.RETRYABLE),
                        false,
                        "FAILED history의 failureType이 올바르지 않습니다."
                )
        );
    }
}
