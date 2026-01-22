package com.slack.bot.presentation.oauth;

import com.slack.bot.application.oauth.OauthVerificationStateService;
import com.slack.bot.application.oauth.OauthService;
import com.slack.bot.application.oauth.RegisterWorkspaceService;
import com.slack.bot.application.oauth.TokenParsingService;
import com.slack.bot.application.oauth.dto.response.SlackTokenResponse;
import com.slack.bot.global.config.properties.SlackProperties;
import com.slack.bot.presentation.common.ResponseEntityConst;
import com.slack.bot.presentation.oauth.dto.response.SlackInstallUrlResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/slack")
@RequiredArgsConstructor
public class SlackOauthController {

    private final OauthService oauthService;
    private final SlackProperties slackProperties;
    private final TokenParsingService tokenParsingService;
    private final RegisterWorkspaceService registerWorkspaceService;
    private final OauthVerificationStateService oauthVerificationStateService;

    @GetMapping("/install")
    public ResponseEntity<SlackInstallUrlResponse> getInstallUrl(
            @CookieValue(value = "accessToken") String accessToken
    ) {
        Long userId = tokenParsingService.encode(accessToken);
        String state = oauthVerificationStateService.generateSlackOauthState(userId);
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
            @RequestParam(value = "state") String state
    ) {
        Long userId = oauthVerificationStateService.resolveUserIdByState(state);
        SlackTokenResponse tokenResponse = oauthService.exchangeCodeForToken(code);

        registerWorkspaceService.registerWorkspace(tokenResponse, userId);
        return ResponseEntityConst.NO_CONTENT;
    }
}
