package com.slack.bot.application.interactivity.reply.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SlackActionResponse(
        @JsonProperty("response_action")
        String responseAction,
        Object view,
        Map<String, String> errors
) {

    public static SlackActionResponse empty() {
        return new SlackActionResponse(null, null, null);
    }

    public static SlackActionResponse push(Object view) {
        return new SlackActionResponse("push", view, null);
    }

    public static SlackActionResponse clear() {
        return new SlackActionResponse("clear", null, null);
    }

    public static SlackActionResponse errors(Map<String, String> errors) {
        if (errors == null) {
            return new SlackActionResponse("errors", null, null);
        }

        return new SlackActionResponse("errors", null, errors);
    }
}
