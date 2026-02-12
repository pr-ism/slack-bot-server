package com.slack.bot.application.interactivity.reply;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.params.ParameterizedTest;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InteractivityErrorTypeTest {

    private static Stream<Arguments> messageTestArguments() {
        return Stream.of(
                Arguments.of(InteractivityErrorType.INVALID_META, "리뷰 정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요."),
                Arguments.of(InteractivityErrorType.REVIEWEE_CANNOT_RESERVE, "리뷰이는 해당 PR에 대한 리뷰를 할 수 없습니다."),
                Arguments.of(InteractivityErrorType.RESERVATION_LOAD_FAILURE, "예약 정보를 불러올 수 없습니다. 잠시 후 다시 시도해주세요."),
                Arguments.of(InteractivityErrorType.RESERVATION_NOT_FOUND, "예약 정보를 찾을 수 없습니다. 새로 예약해 주세요."),
                Arguments.of(InteractivityErrorType.RESERVATION_ALREADY_CANCELLED, "이미 취소된 예약입니다."),
                Arguments.of(InteractivityErrorType.RESERVATION_CHANGE_NOT_ALLOWED_CANCELLED, "이미 취소한 예약이므로 리뷰 예약 시간을 변경할 수 없습니다."),
                Arguments.of(InteractivityErrorType.RESERVATION_ALREADY_STARTED, "이미 리뷰가 시작되어 예약을 취소하거나 변경할 수 없습니다."),
                Arguments.of(InteractivityErrorType.NOT_OWNER_CANCEL, "본인의 예약만 취소할 수 있습니다."),
                Arguments.of(InteractivityErrorType.NOT_OWNER_CHANGE, "본인의 예약만 변경할 수 있습니다."),
                Arguments.of(InteractivityErrorType.RESERVATION_FAILURE, "리뷰 예약을 처리하지 못했습니다. 잠시 후 다시 시도해주세요.")
        );
    }

    @ParameterizedTest
    @MethodSource("messageTestArguments")
    void 에러_메시지를_조회한다(InteractivityErrorType type, String expected) {
        // when
        String actual = type.message();

        // when & then
        assertThat(actual).isEqualTo(expected);
    }
}
