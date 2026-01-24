package com.slack.bot.application.oauth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.slack.bot.domain.workspace.Workspace;

public record SlackTokenResponse(
        boolean ok,

        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("bot_user_id")
        String botUserId,

        Team team
) {

    public record Team(String id, String name) {
    }

    public record AuthedUser(String id) {
    }

    public Workspace toEntity(Long userId) {
        String teamId = this.team == null ? null : this.team.id;

        return Workspace.builder()
                        .userId(userId)
                        .teamId(teamId)
                        .accessToken(accessToken)
                        .botUserId(botUserId)
                        .build();
    }

    public String teamId() {
        return this.team.id;
    }

}
