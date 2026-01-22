package com.slack.bot.presentation.oauth;

import com.slack.bot.application.oauth.OauthService;
import com.slack.bot.application.oauth.RegisterWorkspaceService;
import com.slack.bot.application.oauth.dto.response.SlackTokenResponse;
import com.slack.bot.application.oauth.exception.SlackOauthInvalidStateException;
import com.slack.bot.global.config.properties.SlackProperties;
import com.slack.bot.presentation.common.ResponseEntityConst;
import com.slack.bot.presentation.oauth.dto.response.SlackInstallUrlResponse;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/slack")
@RequiredArgsConstructor
public class SlackOauthController {

    private static final String OAUTH_STATE_SESSION_ATTRIBUTE = "SLACK_OAUTH_STATE";

    private final SlackProperties slackProperties;
    private final OauthService oauthService;
    private final RegisterWorkspaceService registerWorkspaceService;

    @GetMapping("/install")
    public ResponseEntity<SlackInstallUrlResponse> installUrl(HttpSession session) {
        String state = generateAndStoreState(session);
        String slackOauthUrl = UriComponentsBuilder.fromUriString("https://slack.com/oauth/v2/authorize")
                                                  .queryParam("client_id", slackProperties.clientId())
                                                  .queryParam("scope", slackProperties.scopes())
                                                  .queryParam("redirect_uri", slackProperties.redirectUri())
                                                  .queryParam("state", state)
                                                  .build()
                                                  .toUriString();
        SlackInstallUrlResponse response = new SlackInstallUrlResponse(slackOauthUrl);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam("code") String code,
            @RequestParam(value = "state", required = false) String state,
            HttpSession session
    ) {
        validateState(session, state);
        SlackTokenResponse tokenResponse = oauthService.exchangeCodeForToken(code);

        registerWorkspaceService.registerWorkspace(tokenResponse, Long.MIN_VALUE);
        clearState(session);
        return ResponseEntityConst.NO_CONTENT;
    }

    private String generateAndStoreState(HttpSession session) {
        String state = UUID.randomUUID().toString();

        session.setAttribute(OAUTH_STATE_SESSION_ATTRIBUTE, state);
        return state;
    }

    private void validateState(HttpSession session, String state) {
        String storedState = (String) session.getAttribute(OAUTH_STATE_SESSION_ATTRIBUTE);

        if (storedState == null || !storedState.equals(state)) {
            throw new SlackOauthInvalidStateException();
        }
    }

    private void clearState(HttpSession session) {
        session.removeAttribute(OAUTH_STATE_SESSION_ATTRIBUTE);
    }
}
