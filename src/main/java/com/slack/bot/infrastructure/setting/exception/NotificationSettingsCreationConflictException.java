package com.slack.bot.infrastructure.setting.exception;

public class NotificationSettingsCreationConflictException extends RuntimeException {

    public NotificationSettingsCreationConflictException(Long projectMemberId, Throwable cause) {
        super("알림 설정 생성 중 유니크 제약조건을 위반했으나 기존 설정을 찾을 수 없습니다. projectMemberId=" + projectMemberId, cause);
    }
}
