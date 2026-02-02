package com.slack.bot.application.review.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record ReviewMessageDto(JsonNode blocks, JsonNode attachments, String fallbackText) {
}
