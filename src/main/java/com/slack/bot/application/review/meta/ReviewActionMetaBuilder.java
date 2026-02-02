package com.slack.bot.application.review.meta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.review.dto.request.ReviewRequestEventRequest;
import com.slack.bot.application.review.meta.exception.ProjectNotFoundException;
import com.slack.bot.application.review.meta.exception.ReviewActionMetaException;
import com.slack.bot.domain.member.repository.ProjectMemberRepository;
import com.slack.bot.domain.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewActionMetaBuilder {

    private final ObjectMapper objectMapper;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    public String build(String teamId, String channelId, String apiKey, ReviewRequestEventRequest report) {
        Long projectId = projectRepository.findIdByApiKey(apiKey)
                                          .orElseThrow(() -> new ProjectNotFoundException(apiKey));

        ObjectNode meta = objectMapper.createObjectNode();

        meta.put("team_id", teamId);
        meta.put("channel_id", channelId);
        meta.put("project_id", projectId);
        meta.put("pull_request_url", report.pullRequestUrl());
        meta.put("pull_request_title", report.pullRequestTitle());
        meta.put("repo", report.repositoryName());
        meta.put("pull_request_number", report.pullRequestNumber());
        meta.put("author_github_id", report.authorGithubId());

        if (report.pendingReviewers() != null && !report.pendingReviewers().isEmpty()) {
            meta.putPOJO("reviewer_github_ids", report.pendingReviewers());
        }

        projectMemberRepository.findByGithubUser(teamId, report.authorGithubId())
                               .ifPresent(mapping -> meta.put("author_slack_id", mapping.getSlackUserId()));

        try {
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            throw new ReviewActionMetaException("리뷰 스케줄러 메타데이터 직렬화 실패", e);
        }
    }
}
