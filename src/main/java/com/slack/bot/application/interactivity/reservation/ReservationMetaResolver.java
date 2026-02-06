package com.slack.bot.application.interactivity.reservation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interactivity.dto.ReviewScheduleMetaDto;
import com.slack.bot.application.interactivity.reservation.exception.ReservationMetaInvalidException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationMetaResolver {

    private final ObjectMapper objectMapper;

    public ReviewScheduleMetaDto parseMeta(String metaJson) {
        JsonNode root = readRoot(metaJson);

        String teamId = textRequired(root, "team_id");
        String channelId = textRequired(root, "channel_id");
        Long pullRequestId = longRequired(root, "pull_request_id");
        int pullRequestNumber = intRequired(root, "pull_request_number");
        String pullRequestTitle = textRequired(root, "pull_request_title");
        String pullRequestUrl = textRequired(root, "pull_request_url");
        String projectId = textRequired(root, "project_id");
        String authorGithubId = textRequired(root, "author_github_id");
        String authorSlackId = textRequired(root, "author_slack_id");
        String reservationId = textRequired(root, "reservation_id");

        return ReviewScheduleMetaDto.builder()
                .teamId(teamId)
                .channelId(channelId)
                .pullRequestId(pullRequestId)
                .pullRequestNumber(pullRequestNumber)
                .pullRequestTitle(pullRequestTitle)
                .pullRequestUrl(pullRequestUrl)
                .authorGithubId(authorGithubId)
                .authorSlackId(authorSlackId)
                .reservationId(reservationId)
                .projectId(projectId)
                .build();
    }

    private JsonNode readRoot(String metaJson) {
        try {
            return objectMapper.readTree(metaJson);
        } catch (JsonProcessingException e) {
            throw new ReservationMetaInvalidException("메타데이터 파싱 실패", e);
        }
    }

    private String textRequired(JsonNode node, String field) {
        if (node == null) {
            throw new ReservationMetaInvalidException(field + "는 비어 있을 수 없습니다.");
        }

        String value = node.path(field).asText(null);

        if (value == null || value.isBlank()) {
            throw new ReservationMetaInvalidException(field + "는 비어 있을 수 없습니다.");
        }
        return value;
    }

    private int intRequired(JsonNode node, String field) {
        if (node == null) {
            throw new ReservationMetaInvalidException(field + "는 비어 있을 수 없습니다.");
        }

        JsonNode valueNode = node.path(field);

        if (valueNode.isMissingNode() || valueNode.isNull()) {
            throw new ReservationMetaInvalidException(field + "는 비어 있을 수 없습니다.");
        }
        if (!valueNode.isNumber()) {
            throw new ReservationMetaInvalidException(field + "는 유효한 정수여야 합니다.");
        }
        return valueNode.asInt();
    }

    private Long longRequired(JsonNode node, String field) {
        if (node == null) {
            throw new ReservationMetaInvalidException(field + "는 비어 있을 수 없습니다.");
        }

        JsonNode valueNode = node.path(field);

        if (valueNode.isMissingNode() || valueNode.isNull()) {
            throw new ReservationMetaInvalidException(field + "는 비어 있을 수 없습니다.");
        }
        if (!valueNode.isNumber()) {
            throw new ReservationMetaInvalidException(field + "는 유효한 정수여야 합니다.");
        }
        return valueNode.asLong();
    }
}
