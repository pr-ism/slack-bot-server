package com.slack.bot.application.interactivity.block.factory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.slack.bot.application.command.exception.WorkspaceNotFoundException;
import com.slack.bot.application.interactivity.block.dto.BlockActionContextDto;
import com.slack.bot.application.interactivity.block.dto.BlockActionIdentityDto;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BlockActionContextFactory {

    private final WorkspaceRepository workspaceRepository;

    public Optional<BlockActionContextDto> create(JsonNode payload) {
        BlockActionIdentityDto identity = extractIdentity(payload);

        if (!identity.valid()) {
            return Optional.empty();
        }

        JsonNode action = firstAction(payload);

        if (action.isMissingNode()) {
            return Optional.empty();
        }

        String actionId = readActionId(action);

        if (actionId == null) {
            log.warn("필수 필드가 없는 인터랙션 요청입니다. action_id가 없습니다.");

            return Optional.empty();
        }

        String token = resolveToken(identity.teamId());
        BlockActionContextDto context = BlockActionContextDto.builder()
                                                             .teamId(identity.teamId())
                                                             .channelId(identity.channelId())
                                                             .slackUserId(identity.slackUserId())
                                                             .actionId(actionId)
                                                             .action(action)
                                                             .botToken(token)
                                                             .build();

        return Optional.of(context);
    }

    private BlockActionIdentityDto extractIdentity(JsonNode payload) {
        String slackUserId = readSlackUserId(payload);
        String teamId = readTeamId(payload);

        if (slackUserId == null || teamId == null) {
            log.warn("필수 필드가 없는 인터랙션 요청입니다. teamId={}, user={}", teamId, slackUserId);

            return BlockActionIdentityDto.empty();
        }

        String channelId = readChannelId(payload);

        return BlockActionIdentityDto.valid(teamId, channelId, slackUserId);
    }

    private String resolveToken(String teamId) {
        return workspaceRepository.findByTeamId(teamId)
                                  .orElseThrow(() -> new WorkspaceNotFoundException())
                                  .getAccessToken();
    }

    private JsonNode firstAction(JsonNode payload) {
        JsonNode actions = payload.path("actions");

        if (!actions.isArray() || actions.isEmpty()) {
            log.warn("필수 필드가 없는 인터랙션 요청입니다. actions가 없습니다.");

            return MissingNode.getInstance();
        }

        return actions.get(0);
    }

    private String readActionId(JsonNode action) {
        return action.path("action_id")
                     .asText(null);
    }

    private String readSlackUserId(JsonNode payload) {
        return payload.path("user")
                      .path("id")
                      .asText(null);
    }

    private String readTeamId(JsonNode payload) {
        return payload.path("team")
                      .path("id")
                      .asText(null);
    }

    private String readChannelId(JsonNode payload) {
        return payload.path("channel")
                      .path("id")
                      .asText(null);
    }
}
