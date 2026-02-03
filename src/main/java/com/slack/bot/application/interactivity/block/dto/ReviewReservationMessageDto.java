package com.slack.bot.application.interactivity.block.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record ReviewReservationMessageDto(JsonNode blocks, JsonNode attachments, String fallbackText) {

    public static ReviewReservationMessageDto ofBlocks(JsonNode blocks, String fallbackText) {
        return new ReviewReservationMessageDto(blocks, null, fallbackText);
    }
}
