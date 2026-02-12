package com.slack.bot.application.interactivity.reservation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.interactivity.reservation.exception.ReviewScheduleMetaException;
import com.slack.bot.domain.member.ProjectMember;
import com.slack.bot.domain.member.repository.ProjectMemberRepository;
import com.slack.bot.domain.reservation.ReviewReservation;
import com.slack.bot.domain.reservation.vo.ReservationPullRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewScheduleMetaBuilder {

    private final ObjectMapper objectMapper;
    private final ProjectMemberRepository projectMemberRepository;

    public String buildForChange(ReviewReservation reservation) {
        ReservationPullRequest reservationPullRequest = reservation.getReservationPullRequest();
        String authorGithubId = resolveAuthorGithubId(reservation);
        ObjectNode meta = objectMapper.createObjectNode();

        meta.put("team_id", reservation.getTeamId());
        meta.put("channel_id", reservation.getChannelId());
        meta.put("project_id", reservation.getProjectId());
        meta.put("pull_request_id", reservationPullRequest.getPullRequestId());
        meta.put("pull_request_number", reservationPullRequest.getPullRequestNumber());
        meta.put("pull_request_title", reservationPullRequest.getPullRequestTitle());
        meta.put("pull_request_url", reservationPullRequest.getPullRequestUrl());
        meta.put("author_github_id", authorGithubId);
        meta.put("author_slack_id", reservation.getAuthorSlackId());
        meta.put("reservation_id", reservation.getId());

        try {
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            throw new ReviewScheduleMetaException("리뷰 스케줄러 메타데이터 직렬화 실패", e);
        }
    }

    private String resolveAuthorGithubId(ReviewReservation reservation) {
        String teamId = reservation.getTeamId();
        String authorSlackId = reservation.getAuthorSlackId();

        return projectMemberRepository.findBySlackUser(teamId, authorSlackId)
                                      .map(ProjectMember::getGithubId)
                                      .map(githubId -> githubId == null ? null : githubId.getValue())
                                      .filter(value -> value != null && !value.isBlank())
                                      .orElseThrow(() -> new ReviewScheduleMetaException(
                                              "리뷰 스케줄러 메타데이터 생성 실패: author_github_id를 찾을 수 없습니다.",
                                              new IllegalStateException("author_github_id missing")
                                      ));
    }
}
