package com.slack.bot.presentation.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.event.SlackEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/slack/events")
@RequiredArgsConstructor
public class SlackEventController {

    private final SlackEventService slackEventService;

    @PostMapping
    public ResponseEntity<Object> handleEvent(@RequestBody JsonNode payload) {
        if (isUrlVerification(payload)) {
            String challenge = payload.get("challenge")
                                      .asText();

            return ResponseEntity.ok(challenge);
        }

        slackEventService.handleEvent(payload);
        return ResponseEntity.ok()
                             .build();
    }

    private boolean isUrlVerification(JsonNode payload) {
        return payload.has("type") && "url_verification".equals(payload.get("type").asText());
    }
}
