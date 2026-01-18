package com.slack.bot.presentation.oauth;

import com.slack.bot.application.oauth.SlackOauthService;
import com.slack.bot.application.oauth.SlackWorkspaceService;
import com.slack.bot.application.oauth.dto.response.SlackTokenResponse;
import com.slack.bot.global.config.properties.SlackProperties;
import com.slack.bot.presentation.common.ResponseEntityConst;
import com.slack.bot.presentation.oauth.dto.response.SlackInstallUrlResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/slack")
@RequiredArgsConstructor
public class SlackOauthController {

    private final SlackProperties slackProperties;
    private final SlackOauthService slackOauthService;
    private final SlackWorkspaceService slackWorkspaceService;

    @GetMapping("/install")
    public ResponseEntity<SlackInstallUrlResponse> installUrl() {
        String slackOauthUrl = UriComponentsBuilder.fromUriString("https://slack.com/oauth/v2/authorize")
                                                  .queryParam("client_id", slackProperties.clientId())
                                                  .queryParam("scope", slackProperties.scopes())
                                                  .queryParam("redirect_uri", slackProperties.redirectUri())
                                                  .build()
                                                  .toUriString();
        SlackInstallUrlResponse response = new SlackInstallUrlResponse(slackOauthUrl);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam("code") String code) {
        SlackTokenResponse tokenResponse = slackOauthService.exchangeCodeForToken(code);

        slackWorkspaceService.registerWorkspace(tokenResponse);
        return ResponseEntityConst.NO_CONTENT;
    }
}
