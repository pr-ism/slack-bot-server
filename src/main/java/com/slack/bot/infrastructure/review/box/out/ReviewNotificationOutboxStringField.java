package com.slack.bot.infrastructure.review.box.out;

import lombok.Getter;

@Getter
public class ReviewNotificationOutboxStringField {

    private final ReviewNotificationOutboxFieldState state;
    private final String value;

    public static ReviewNotificationOutboxStringField absent() {
        return new ReviewNotificationOutboxStringField(
                ReviewNotificationOutboxFieldState.ABSENT,
                ""
        );
    }

    public static ReviewNotificationOutboxStringField present(String value) {
        validateValue(value);

        return new ReviewNotificationOutboxStringField(
                ReviewNotificationOutboxFieldState.PRESENT,
                value
        );
    }

    private ReviewNotificationOutboxStringField(
            ReviewNotificationOutboxFieldState state,
            String value
    ) {
        this.state = state;
        this.value = value;
    }

    public boolean isPresent() {
        return state == ReviewNotificationOutboxFieldState.PRESENT;
    }

    public String value() {
        if (!isPresent()) {
            throw new IllegalStateException("값이 없는 필드입니다.");
        }

        return value;
    }

    public String valueOrBlank() {
        return value;
    }

    private static void validateValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("필드 값은 비어 있을 수 없습니다.");
        }
    }
}
