package com.slack.bot.application.round.dto;

import java.util.List;
import java.util.Objects;

public record ReviewRoundRegistrationResultDto(String coalescingKey, List<String> reviewersToMention) {

    public ReviewRoundRegistrationResultDto {
        Objects.requireNonNull(coalescingKey, "coalescingKey는 null일 수 없습니다.");
        reviewersToMention = List.copyOf(Objects.requireNonNullElse(reviewersToMention, List.of()));
    }

    public boolean shouldNotify() {
        return !reviewersToMention.isEmpty();
    }
}
