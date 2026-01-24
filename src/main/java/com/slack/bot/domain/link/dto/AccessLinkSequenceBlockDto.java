package com.slack.bot.domain.link.dto;

public record AccessLinkSequenceBlockDto(long start, long end) {

    public AccessLinkSequenceBlockDto {
        if (start <= 0L || end < start) {
            throw new IllegalArgumentException("블록 범위가 올바르지 않습니다.");
        }
    }
}
