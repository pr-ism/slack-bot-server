package com.slack.bot.application.interactivity.box.out.exception;

public class OutboxWorkspaceNotFoundException extends RuntimeException {

    private OutboxWorkspaceNotFoundException(String message) {
        super(message);
    }

    public static OutboxWorkspaceNotFoundException forTeamId(String teamId) {
        return new OutboxWorkspaceNotFoundException("outbox 발송 대상 워크스페이스를 찾을 수 없습니다. teamId=" + teamId);
    }

    public static OutboxWorkspaceNotFoundException forToken(String token) {
        return new OutboxWorkspaceNotFoundException(
                "outbox 적재 대상 워크스페이스를 찾을 수 없습니다. tokenPrefix=" + maskToken(token)
        );
    }

    private static String maskToken(String token) {
        if (token == null || token.isBlank()) {
            return "empty";
        }

        int end = Math.min(token.length(), 8);
        return token.substring(0, end) + "***";
    }
}
