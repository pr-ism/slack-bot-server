package com.slack.bot.application.review.participant;

import com.slack.bot.application.review.dto.request.ReviewRequestEventRequest;
import com.slack.bot.application.review.participant.dto.ReviewParticipantsDto;
import com.slack.bot.domain.member.repository.ProjectMemberRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewParticipantFormatter {

    private final ProjectMemberRepository projectMemberRepository;

    public ReviewParticipantsDto format(String teamId, ReviewRequestEventRequest report) {
        List<String> unmappedGithubIds = new ArrayList<>();

        String authorText = resolveSlackMention(teamId, report.authorGithubId(), unmappedGithubIds);
        String pendingText = formatReviewers(teamId, report.pendingReviewers(), unmappedGithubIds);
        return new ReviewParticipantsDto(authorText, pendingText, List.copyOf(unmappedGithubIds));
    }

    private String formatReviewers(String teamId, List<String> reviewers, List<String> unmappedGithubIds) {
        if (reviewers == null || reviewers.isEmpty()) {
            return "(none)";
        }

        return reviewers.stream()
                .map(ghId -> resolveSlackMention(teamId, ghId, unmappedGithubIds))
                .collect(Collectors.joining(", "));
    }

    private String resolveSlackMention(String teamId, String githubId, List<String> unmappedGithubIds) {
        if (githubId == null || githubId.isBlank()) {
            return "(none)";
        }

        return projectMemberRepository.findByGithubUser(teamId, githubId)
                                      .map(projectMember -> "<@" + projectMember.getSlackUserId() + ">")
                                      .orElseGet(() -> {
                                          unmappedGithubIds.add(githubId);
                                          return githubId;
                                      });
    }
}
