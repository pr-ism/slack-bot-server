package com.slack.bot.application.command.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.command.client.exception.SlackUserInfoRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class MemberConnectionSlackApiClient {

    private final RestClient slackClient;

    public String resolveUserName(String token, String slackUserId) {
        String authorization = "Bearer " + token;
        JsonNode response = fetchUserInfo(authorization, slackUserId);

        return resolveDisplayName(response, slackUserId);
    }

    private JsonNode fetchUserInfo(String authorization, String slackUserId) {
        return slackClient.get()
                          .uri(uriBuilder -> uriBuilder
                                  .pathSegment("users.info")
                                  .queryParam("user", slackUserId)
                                  .build())
                          .header("Authorization", authorization)
                          .retrieve()
                          .onStatus(status -> status.isError(), (request, response) -> {
                              throw new SlackUserInfoRequestException(response.getStatusCode().value());
                          })
                          .body(JsonNode.class);
    }

    private String resolveDisplayName(JsonNode response, String slackUserId) {
        if (response == null || !response.path("ok").asBoolean()) {
            return slackUserId;
        }

        JsonNode userNode = response.path("user");
        JsonNode profileNode = userNode.path("profile");
        String displayName = profileNode.path("display_name").asText();
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }

        String realName = profileNode.path("real_name").asText();
        if (realName != null && !realName.isBlank()) {
            return realName;
        }

        String userName = userNode.path("name").asText();
        if (userName != null && !userName.isBlank()) {
            return userName;
        }

        return slackUserId;
    }
}
