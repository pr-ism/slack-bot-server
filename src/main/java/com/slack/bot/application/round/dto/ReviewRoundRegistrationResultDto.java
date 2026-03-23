package com.slack.bot.application.round.dto;

import java.util.List;
import java.util.Objects;

public record ReviewRoundRegistrationResultDto(
        String batchKey,
        int roundNumber,
        List<String> reviewersToMention
) {

    public ReviewRoundRegistrationResultDto {
        Objects.requireNonNull(batchKey, "batchKey는 null일 수 없습니다.");
        if (roundNumber <= 0) {
            throw new IllegalArgumentException("roundNumber는 1 이상이어야 합니다.");
        }
        reviewersToMention = List.copyOf(Objects.requireNonNullElse(reviewersToMention, List.of()));
    }

    public boolean shouldNotify() {
        return !reviewersToMention.isEmpty();
    }
}
