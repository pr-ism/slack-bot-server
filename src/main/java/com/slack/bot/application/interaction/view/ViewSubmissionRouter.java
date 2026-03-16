package com.slack.bot.application.interaction.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.command.exception.WorkspaceNotFoundException;
import com.slack.bot.application.interaction.box.ProcessingSourceContext;
import com.slack.bot.application.interaction.box.in.ViewSubmissionInboxGateway;
import com.slack.bot.application.interaction.dto.ReviewScheduleMetaDto;
import com.slack.bot.application.interaction.reply.dto.response.SlackActionResponse;
import com.slack.bot.application.interaction.reservation.ReservationMetaResolver;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewSubmissionRouter {

    private final WorkspaceRepository workspaceRepository;
    private final ReservationMetaResolver reservationMetaResolver;
    private final ProcessingSourceContext processingSourceContext;
    private final ViewSubmissionInboxGateway viewSubmissionInboxGateway;
    private final ReviewTimeSubmissionProcessor reviewTimeSubmissionProcessor;

    public SlackActionResponse handle(JsonNode payload) {
        if (!processingSourceContext.isInboxProcessing()) {
            return viewSubmissionInboxGateway.handle(payload).response();
        }

        return handleInInbox(payload);
    }

    private SlackActionResponse handleInInbox(JsonNode payload) {
        ViewCallbackId callbackId = ViewCallbackId.from(readCallbackId(payload));
        String teamId = resolveTeamId(payload);
        validateTeamId(teamId);

        String token = resolveToken(teamId);
        String reviewerId = readReviewerId(payload);
        String metaJson = readMetaJson(payload);
        ReviewScheduleMetaDto meta = reservationMetaResolver.parseMeta(metaJson);

        if (callbackId.isReviewTimeSubmit()) {
            return reviewTimeSubmissionProcessor.handleDefaultTimeSubmit(payload, metaJson, meta, reviewerId, token);
        }
        if (callbackId.isReviewTimeCustomSubmit()) {
            return reviewTimeSubmissionProcessor.handleCustomTimeSubmit(payload, meta, reviewerId, token);
        }

        return SlackActionResponse.empty();
    }

    private void validateTeamId(String teamId) {
        if (teamId == null) {
            log.warn("team_id가 없는 view_submission 입니다.");
            throw new IllegalArgumentException("team_id가 없는 view_submission 입니다.");
        }
    }

    private String readCallbackId(JsonNode payload) {
        return payload.path("view")
                      .path("callback_id")
                      .asText();
    }

    private String readReviewerId(JsonNode payload) {
        return payload.path("user")
                      .path("id")
                      .asText(null);
    }

    private String readMetaJson(JsonNode payload) {
        return payload.path("view")
                      .path("private_metadata")
                      .asText();
    }

    private String resolveToken(String teamId) {
        Workspace workspace = workspaceRepository.findByTeamId(teamId)
                                                 .orElseThrow(() -> new WorkspaceNotFoundException());

        return workspace.getAccessToken();
    }

    private String resolveTeamId(JsonNode payload) {
        String teamId = payload.path("team")
                               .path("id")
                               .asText(null);

        if (teamId != null && !teamId.isBlank()) {
            return teamId;
        }

        teamId = payload.path("team_id")
                        .asText(null);

        if (teamId != null && !teamId.isBlank()) {
            return teamId;
        }

        teamId = payload.path("user")
                        .path("team_id")
                        .asText(null);

        if (teamId != null && !teamId.isBlank()) {
            return teamId;
        }

        teamId = payload.path("view")
                        .path("team_id")
                        .asText(null);

        if (teamId != null && !teamId.isBlank()) {
            return teamId;
        }

        return null;
    }
}
