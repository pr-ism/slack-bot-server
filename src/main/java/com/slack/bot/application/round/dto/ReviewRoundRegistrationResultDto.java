package com.slack.bot.application.round.dto;

import java.util.List;

public record ReviewRoundRegistrationResultDto(String coalescingKey, List<String> reviewersToMention) {

    public boolean shouldNotify() {
        return reviewersToMention != null && !reviewersToMention.isEmpty();
    }
}
