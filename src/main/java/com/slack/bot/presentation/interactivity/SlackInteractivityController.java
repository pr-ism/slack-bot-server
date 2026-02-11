package com.slack.bot.presentation.interactivity;

import com.slack.bot.application.interactivity.SlackInteractionServiceFacade;
import com.slack.bot.application.interactivity.reply.dto.response.SlackActionResponse;
import com.slack.bot.global.security.SlackSignatureVerifier;
import com.slack.bot.presentation.interactivity.dto.request.SlackInteractivityHttpRequest;
import com.slack.bot.presentation.interactivity.exception.SlackSignatureVerificationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/slack/interactive")
@RequiredArgsConstructor
public class SlackInteractivityController {

    private final SlackSignatureVerifier slackSignatureVerifier;
    private final SlackInteractionServiceFacade slackInteractionServiceFacade;

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<SlackActionResponse> handleInteractivity(SlackInteractivityHttpRequest request) {
        validateRequest(request);

        if (!isAuthorized(request)) {
            throw new SlackSignatureVerificationException("슬랙 요청 시그니처 검증에 실패했습니다.");
        }

        SlackActionResponse response = slackInteractionServiceFacade.handle(request.payloadJson());

        return ResponseEntity.ok()
                             .contentType(MediaType.APPLICATION_JSON)
                             .body(response);
    }

    private boolean isAuthorized(SlackInteractivityHttpRequest parsed) {
        return slackSignatureVerifier.verify(parsed.timestamp(), parsed.signature(), parsed.rawBody());
    }

    private void validateRequest(SlackInteractivityHttpRequest parsed) {
        if (parsed == null) {
            throw new IllegalArgumentException("슬랙 인터랙티브 요청이 비어 있습니다.");
        }
        if (parsed.payloadJson() == null || parsed.payloadJson().isBlank()) {
            throw new IllegalArgumentException("슬랙 인터랙티브 payload는 비어 있을 수 없습니다.");
        }
        if (parsed.timestamp() == null || parsed.timestamp().isBlank()) {
            throw new IllegalArgumentException("슬랙 요청 timestamp가 필요합니다.");
        }
        if (parsed.signature() == null || parsed.signature().isBlank()) {
            throw new IllegalArgumentException("슬랙 요청 signature가 필요합니다.");
        }
        if (parsed.rawBody() == null || parsed.rawBody().isBlank()) {
            throw new IllegalArgumentException("슬랙 원본 요청 본문이 필요합니다.");
        }
        try {
            Long.parseLong(parsed.timestamp());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("슬랙 요청 timestamp 형식이 올바르지 않습니다.", e);
        }
    }
}
