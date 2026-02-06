package com.slack.bot.application.interactivity.reservation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interactivity.dto.ReviewScheduleMetaDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationMetaResolver {

    private final ObjectMapper objectMapper;

    public ReviewScheduleMetaDto parseMeta(String metaJson) {
        JsonNode root = readRoot(metaJson);

        String teamId = textOrNull(root, "team_id");
        String channelId = textOrNull(root, "channel_id");
        Long pullRequestId = longOrNull(root, "pull_request_id");
        Integer pullRequestNumber = intOrNull(root, "pull_request_number");
        String pullRequestTitle = textOrNull(root, "pull_request_title");
        String pullRequestUrl = textOrNull(root, "pull_request_url");
        String projectId = textOrNull(root, "project_id");
        String authorGithubId = textOrNull(root, "author_github_id");
        String authorSlackId = textOrNull(root, "author_slack_id");
        String reservationId = textOrNull(root, "reservation_id");

        if (pullRequestNumber == null) {
            throw new IllegalArgumentException("pull_request_number는 비어 있을 수 없습니다.");
        }

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
            throw new IllegalArgumentException("메타데이터 파싱 실패", e);
        }
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }

        String value = node.path(field).asText(null);

        if (value != null && !value.isBlank()) {
            return value;
        }
        return null;
    }

    private Integer intOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }

        JsonNode valueNode = node.path(field);

        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }
        return valueNode.asInt();
    }

    private Long longOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }

        JsonNode valueNode = node.path(field);

        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }
        return valueNode.asLong();
    }
}
