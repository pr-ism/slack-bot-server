package com.slack.bot.application.oauth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.slack.bot.domain.workspace.Workspace;

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

    public Workspace toEntity() {
        String teamId = this.team == null ? null : this.team.id;
        return Workspace.create(teamId, this.accessToken);
    }

    public String teamId() {
        return this.team.id;
    }

}
