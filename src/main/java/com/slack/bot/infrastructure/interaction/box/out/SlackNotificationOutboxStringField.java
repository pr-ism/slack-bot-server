package com.slack.bot.infrastructure.interaction.box.out;

import lombok.Getter;

@Getter
public class SlackNotificationOutboxStringField {

    private final SlackNotificationOutboxFieldState state;
    private final String value;

    public static SlackNotificationOutboxStringField absent() {
        return new SlackNotificationOutboxStringField(
                SlackNotificationOutboxFieldState.ABSENT,
                ""
        );
    }

    public static SlackNotificationOutboxStringField present(String value) {
        validateValue(value);

        return new SlackNotificationOutboxStringField(
                SlackNotificationOutboxFieldState.PRESENT,
                value
        );
    }

    private SlackNotificationOutboxStringField(
            SlackNotificationOutboxFieldState state,
            String value
    ) {
        this.state = state;
        this.value = value;
    }

    public boolean isPresent() {
        return state == SlackNotificationOutboxFieldState.PRESENT;
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
