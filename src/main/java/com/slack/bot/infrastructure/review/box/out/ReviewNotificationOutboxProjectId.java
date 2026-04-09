package com.slack.bot.infrastructure.review.box.out;

public class ReviewNotificationOutboxProjectId {

    private final ReviewNotificationOutboxFieldState state;
    private final long value;

    public static ReviewNotificationOutboxProjectId absent() {
        return new ReviewNotificationOutboxProjectId(
                ReviewNotificationOutboxFieldState.ABSENT,
                0L
        );
    }

    public static ReviewNotificationOutboxProjectId present(Long value) {
        validateValue(value);

        return new ReviewNotificationOutboxProjectId(
                ReviewNotificationOutboxFieldState.PRESENT,
                value
        );
    }

    private ReviewNotificationOutboxProjectId(
            ReviewNotificationOutboxFieldState state,
            long value
    ) {
        this.state = state;
        this.value = value;
    }

    public boolean isPresent() {
        return state == ReviewNotificationOutboxFieldState.PRESENT;
    }

    public long value() {
        if (!isPresent()) {
            throw new IllegalStateException("projectId가 없는 아웃박스입니다.");
        }

        return value;
    }

    private static void validateValue(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("projectId는 1 이상의 값이어야 합니다.");
        }
    }
}
