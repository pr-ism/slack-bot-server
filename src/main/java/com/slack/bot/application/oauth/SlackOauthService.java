package com.slack.bot.application.oauth;

import com.slack.bot.application.oauth.dto.response.SlackTokenResponse;
import com.slack.bot.application.oauth.exception.SlackOauthErrorResponseException;
import com.slack.bot.application.oauth.exception.SlackOauthEmptyResponseException;
import com.slack.bot.global.config.properties.SlackProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class SlackOauthService {

    private final SlackProperties slackProperties;
    private final RestClient.Builder slackRestClientBuilder;

    public SlackTokenResponse exchangeCodeForToken(String code) {
        MultiValueMap<String, String> parameters = createSlackOauthParameters(code);

        return exchangeSlackToken(parameters);
    }

    private SlackTokenResponse exchangeSlackToken(MultiValueMap<String, String> parameters) {
        SlackTokenResponse response = slackRestClientBuilder.build()
                                                            .post()
                                                            .uri("oauth.v2.access")
                                                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                                            .body(parameters)
                                                            .retrieve()
                                                            .body(SlackTokenResponse.class);

        if (response == null) {
            throw new SlackOauthEmptyResponseException("응답이 비어 있습니다.");
        }
        if (!response.ok()) {
            throw new SlackOauthErrorResponseException("요청에 실패했습니다.");
        }

        return response;
    }

    private MultiValueMap<String, String> createSlackOauthParameters(String code) {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();

        parameters.add("client_id", slackProperties.clientId());
        parameters.add("client_secret", slackProperties.clientSecret());
        parameters.add("code", code);
        parameters.add("redirect_uri", slackProperties.redirectUri());
        return parameters;
    }
}
