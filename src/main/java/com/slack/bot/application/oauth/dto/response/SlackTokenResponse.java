package com.slack.bot.application.oauth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SlackTokenResponse(
        boolean ok,

        @JsonProperty("access_token")
        String accessToken,

        Team team,

        @JsonProperty("authed_user")
        AuthedUser authedUser
) {

    public record Team(String id, String name) {
    }

    public record AuthedUser(String id) {
    }
}
