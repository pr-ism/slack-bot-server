package com.slack.bot.application.round;

import com.slack.bot.application.review.dto.request.ReviewAssignmentRequest;
import com.slack.bot.application.round.dto.ReviewRoundRegistrationResultDto;
import com.slack.bot.domain.round.PullRequestRound;
import com.slack.bot.domain.round.RoundReviewer;
import com.slack.bot.domain.round.repository.PullRequestRoundRepository;
import com.slack.bot.domain.round.repository.RoundReviewerRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReviewRequestRoundCoordinator {

    private final PullRequestRoundRepository pullRequestRoundRepository;
    private final RoundReviewerRepository roundReviewerRepository;

    @Transactional
    public ReviewRoundRegistrationResultDto register(String apiKey, ReviewAssignmentRequest request) {
        validateApiKey(apiKey);
        validateRequest(request);

        String startCommitHash = normalizeStartCommitHash(request.startCommitHash());
        RoundResolution resolution = resolveRound(apiKey, request.githubPullRequestId(), startCommitHash);
        PullRequestRound round = resolution.round();

        if (resolution.previousRound() != null) {
            markReviewedReviewers(resolution.previousRound(), request.reviewedReviewers());
        }

        List<String> pendingReviewers = mergePendingWithCarryoverReviewedReviewers(
                request.pendingReviewers(),
                resolution.previousRound()
        );
        Set<String> reviewersToMention = upsertPendingReviewers(
                round,
                resolution.previousRound(),
                pendingReviewers
        );

        if (resolution.previousRound() == null) {
            markReviewedReviewers(round, request.reviewedReviewers());
        }

        return new ReviewRoundRegistrationResultDto(round.coalescingKey(), new ArrayList<>(reviewersToMention));
    }

    private RoundResolution resolveRound(
            String apiKey,
            Long githubPullRequestId,
            String startCommitHash
    ) {
        return pullRequestRoundRepository.findLatestRound(apiKey, githubPullRequestId)
                                         .map(currentRound -> resolveWithExistingRound(
                                                 apiKey,
                                                 githubPullRequestId,
                                                 startCommitHash,
                                                 currentRound
                                         ))
                                         .orElseGet(() -> new RoundResolution(
                                                 saveRound(apiKey, githubPullRequestId, 1, startCommitHash),
                                                 null
                                         ));
    }

    private RoundResolution resolveWithExistingRound(
            String apiKey,
            Long githubPullRequestId,
            String startCommitHash,
            PullRequestRound currentRound
    ) {
        if (currentRound.hasSameStartCommitHash(startCommitHash)) {
            return new RoundResolution(currentRound, null);
        }

        PullRequestRound nextRound = saveRound(
                apiKey,
                githubPullRequestId,
                currentRound.getRoundNumber() + 1,
                startCommitHash
        );
        return new RoundResolution(nextRound, currentRound);
    }

    private PullRequestRound saveRound(
            String apiKey,
            Long githubPullRequestId,
            int roundNumber,
            String startCommitHash
    ) {
        PullRequestRound round = PullRequestRound.create(
                apiKey,
                githubPullRequestId,
                roundNumber,
                startCommitHash
        );

        try {
            return pullRequestRoundRepository.save(round);
        } catch (DataIntegrityViolationException exception) {
            return pullRequestRoundRepository.findRoundByStartCommitHash(
                    apiKey,
                    githubPullRequestId,
                    startCommitHash
            ).orElseThrow(() -> exception);
        }
    }

    private Set<String> upsertPendingReviewers(
            PullRequestRound round,
            PullRequestRound previousRound,
            List<String> pendingReviewers
    ) {
        Set<String> reviewersToMention = new LinkedHashSet<>();

        for (String reviewerGithubId : normalizeReviewerGithubIds(pendingReviewers)) {
            if (upsertReviewer(round, previousRound, reviewerGithubId)) {
                reviewersToMention.add(reviewerGithubId);
            }
        }

        return reviewersToMention;
    }

    private void markReviewedReviewers(PullRequestRound round, List<String> reviewedReviewers) {
        for (String reviewerGithubId : normalizeReviewerGithubIds(reviewedReviewers)) {
            roundReviewerRepository.findReviewerInRound(round.getId(), reviewerGithubId)
                                   .ifPresentOrElse(
                                           roundReviewer -> {
                                               if (roundReviewer.isReviewed()) {
                                                   return;
                                               }

                                               roundReviewer.markReviewed();
                                               roundReviewerRepository.save(roundReviewer);
                                           },
                                           () -> roundReviewerRepository.save(
                                                   RoundReviewer.reviewed(round.getId(), reviewerGithubId)
                                           )
                                   );
        }
    }

    private List<String> mergePendingWithCarryoverReviewedReviewers(
            List<String> pendingReviewers,
            PullRequestRound previousRound
    ) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(normalizeReviewerGithubIds(pendingReviewers));
        merged.addAll(loadCarryoverReviewedReviewerGithubIds(previousRound));
        return new ArrayList<>(merged);
    }

    private Set<String> loadCarryoverReviewedReviewerGithubIds(PullRequestRound previousRound) {
        if (previousRound == null || previousRound.getId() == null) {
            return Set.of();
        }

        return roundReviewerRepository.findAllInRound(previousRound.getId())
                                      .stream()
                                      .filter(roundReviewer -> roundReviewer.isReviewed())
                                      .map(roundReviewer -> roundReviewer.getReviewerGithubId())
                                      .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean upsertReviewer(
            PullRequestRound round,
            PullRequestRound previousRound,
            String reviewerGithubId
    ) {
        return roundReviewerRepository.findReviewerInRound(round.getId(), reviewerGithubId)
                                      .map(roundReviewer -> markRequestedIfReviewed(roundReviewer))
                                      .orElseGet(() -> createReviewer(round, previousRound, reviewerGithubId));
    }

    private boolean markRequestedIfReviewed(RoundReviewer roundReviewer) {
        if (roundReviewer.isRequested()) {
            return false;
        }

        roundReviewer.markRequested();
        roundReviewerRepository.save(roundReviewer);
        return true;
    }

    private boolean createReviewer(
            PullRequestRound round,
            PullRequestRound previousRound,
            String reviewerGithubId
    ) {
        roundReviewerRepository.save(RoundReviewer.requested(round.getId(), reviewerGithubId));
        return !wasRequestedInPreviousRound(previousRound, reviewerGithubId);
    }

    private boolean wasRequestedInPreviousRound(PullRequestRound previousRound, String reviewerGithubId) {
        if (previousRound == null || previousRound.getId() == null) {
            return false;
        }

        return roundReviewerRepository.findReviewerInRound(previousRound.getId(), reviewerGithubId)
                                      .map(roundReviewer -> roundReviewer.isRequested())
                                      .orElse(false);
    }

    private Set<String> normalizeReviewerGithubIds(List<String> reviewerGithubIds) {
        Set<String> normalized = new LinkedHashSet<>();

        if (reviewerGithubIds == null || reviewerGithubIds.isEmpty()) {
            return normalized;
        }

        for (String reviewerGithubId : reviewerGithubIds) {
            String normalizedReviewerGithubId = normalizeReviewerGithubId(reviewerGithubId);
            if (normalizedReviewerGithubId != null) {
                normalized.add(normalizedReviewerGithubId);
            }
        }

        return normalized;
    }

    private String normalizeReviewerGithubId(String reviewerGithubId) {
        if (reviewerGithubId == null) {
            return null;
        }

        String trimmed = reviewerGithubId.trim();
        if (trimmed.isBlank()) {
            return null;
        }

        return trimmed;
    }

    private void validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey는 비어 있을 수 없습니다.");
        }
    }

    private void validateRequest(ReviewAssignmentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request는 비어 있을 수 없습니다.");
        }
        if (request.githubPullRequestId() == null || request.githubPullRequestId() <= 0) {
            throw new IllegalArgumentException("githubPullRequestId는 비어 있을 수 없습니다.");
        }
        if (normalizeStartCommitHash(request.startCommitHash()) == null) {
            throw new IllegalArgumentException("startCommitHash는 비어 있을 수 없습니다.");
        }
    }

    private String normalizeStartCommitHash(String startCommitHash) {
        if (startCommitHash == null) {
            return null;
        }

        String trimmed = startCommitHash.trim();
        if (trimmed.isBlank()) {
            return null;
        }

        return trimmed;
    }

    private record RoundResolution(PullRequestRound round, PullRequestRound previousRound) {
    }
}
