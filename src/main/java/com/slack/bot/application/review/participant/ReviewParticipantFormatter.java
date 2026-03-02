package com.slack.bot.application.review.participant;

import com.slack.bot.application.review.dto.ReviewNotificationPayload;
import com.slack.bot.application.review.participant.dto.ReviewParticipantsDto;
import com.slack.bot.domain.member.ProjectMember;
import com.slack.bot.domain.member.repository.ProjectMemberRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewParticipantFormatter {

    private final ProjectMemberRepository projectMemberRepository;

    public ReviewParticipantsDto format(String teamId, ReviewNotificationPayload event) {
        List<String> unmappedGithubIds = new ArrayList<>();
        Set<String> reviewersToMention = normalizeGithubIds(event.reviewersToMention());
        boolean mentionAllReviewers = reviewersToMention.isEmpty();

        String authorText = resolveSlackMention(teamId, event.authorGithubId(), unmappedGithubIds);
        String pendingText = formatReviewers(
                teamId,
                event.pendingReviewers(),
                reviewersToMention,
                mentionAllReviewers,
                unmappedGithubIds
        );
        return new ReviewParticipantsDto(authorText, pendingText, List.copyOf(unmappedGithubIds));
    }

    private String formatReviewers(
            String teamId,
            List<String> reviewers,
            Set<String> reviewersToMention,
            boolean mentionAllReviewers,
            List<String> unmappedGithubIds
    ) {
        if (reviewers == null || reviewers.isEmpty()) {
            return "(none)";
        }

        return reviewers.stream()
                        .map(ghId -> resolveReviewerText(teamId, ghId, reviewersToMention, mentionAllReviewers, unmappedGithubIds))
                        .collect(Collectors.joining(", "));
    }

    private String resolveReviewerText(
            String teamId,
            String githubId,
            Set<String> reviewersToMention,
            boolean mentionAllReviewers,
            List<String> unmappedGithubIds
    ) {
        if (githubId == null || githubId.isBlank()) {
            return "(none)";
        }

        return projectMemberRepository.findByGithubUser(teamId, githubId)
                                      .map(projectMember -> resolveReviewerMappedText(
                                              githubId,
                                              projectMember,
                                              reviewersToMention,
                                              mentionAllReviewers
                                      ))
                                      .orElseGet(() -> {
                                          unmappedGithubIds.add(githubId);
                                          return githubId;
                                      });
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

    private String resolveReviewerMappedText(
            String githubId,
            ProjectMember projectMember,
            Set<String> reviewersToMention,
            boolean mentionAllReviewers
    ) {
        if (mentionAllReviewers || reviewersToMention.contains(githubId)) {
            return "<@" + projectMember.getSlackUserId() + ">";
        }

        return projectMember.getDisplayName();
    }

    private Set<String> normalizeGithubIds(List<String> githubIds) {
        if (githubIds == null || githubIds.isEmpty()) {
            return new LinkedHashSet<>();
        }

        return githubIds.stream()
                        .filter(id -> Objects.nonNull(id))
                        .map(s -> s.trim())
                        .filter(Predicate.not(s1 -> s1.isBlank()))
                        .collect(Collectors.toCollection(() -> new LinkedHashSet<>()));
    }
}
