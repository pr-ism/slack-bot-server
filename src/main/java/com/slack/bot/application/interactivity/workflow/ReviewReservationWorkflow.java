package com.slack.bot.application.interactivity.workflow;

import com.slack.bot.application.interactivity.dto.ReviewScheduleMetaDto;
import com.slack.bot.application.interactivity.notification.ReviewReservationNotifier;
import com.slack.bot.application.interactivity.reply.InteractivityErrorType;
import com.slack.bot.application.interactivity.reply.SlackActionErrorNotifier;
import com.slack.bot.application.interactivity.reply.dto.response.SlackActionResponse;
import com.slack.bot.application.interactivity.reservation.AuthorResolver;
import com.slack.bot.application.interactivity.reservation.ProjectIdResolver;
import com.slack.bot.application.interactivity.reservation.ReservationType;
import com.slack.bot.application.interactivity.reservation.ReviewReservationCoordinator;
import com.slack.bot.application.interactivity.reservation.dto.ReservationContextDto;
import com.slack.bot.application.interactivity.reservation.exception.ActiveReservationAlreadyExistsException;
import com.slack.bot.application.interactivity.reservation.exception.ReservationScheduleInPastException;
import com.slack.bot.domain.reservation.ReviewReservation;
import com.slack.bot.domain.reservation.vo.ReservationPullRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewReservationWorkflow {

    private static final String TIME_BLOCK_ID = "time_block";

    private final Clock clock;
    private final AuthorResolver authorResolver;
    private final ProjectIdResolver projectIdResolver;
    private final SlackActionErrorNotifier errorNotifier;
    private final ReviewReservationNotifier reservationNotifier;
    private final ReviewReservationCoordinator reviewReservationCoordinator;

    public SlackActionResponse reserveReview(
            ReviewScheduleMetaDto meta,
            String reviewerId,
            String token,
            Instant scheduledAt
    ) {
        return executeSafely(meta, token, reviewerId, () -> {
            ReservationContextDto context = buildContext(meta, reviewerId, token, scheduledAt);

            return checkDuplicate(context)
                    .map(duplicate -> SlackActionResponse.empty())
                    .orElseGet(() -> proceedReservation(context));
        });
    }

    private SlackActionResponse proceedReservation(ReservationContextDto context) {
        ReservationType strategy = ReservationType.resolve(context.reservationId());
        ReviewReservation reservation = strategy.persist(reviewReservationCoordinator, context);

        notifySuccess(strategy, context, reservation);
        return SlackActionResponse.clear();
    }

    private Optional<ReviewReservation> checkDuplicate(ReservationContextDto context) {
        return findActiveReservation(context)
                .filter(active -> ReservationType.resolve(context.reservationId()).isNew())
                .map(active -> {
                    notifyDuplicate(context, active);
                    return active;
                });
    }

    private void notifySuccess(ReservationType strategy, ReservationContextDto context, ReviewReservation reservation) {
        notifyTiming(context);

        if (strategy.isNew()) {
            reservationNotifier.sendReservationBlockToDmAndEphemeral(
                    context.token(),
                    context.meta().teamId(),
                    context.meta().channelId(),
                    context.reviewerId(),
                    reservation,
                    strategy.successMessage()
            );
            return;
        }

        reservationNotifier.sendReservationBlockToDirectMessageOnly(
                context.token(),
                context.reviewerId(),
                reservation,
                strategy.successMessage()
        );
    }

    private void notifyTiming(ReservationContextDto context) {
        if (isImmediate(context)) {
            reservationNotifier.notifyStartNow(
                    context.meta(),
                    context.reviewerId(),
                    context.token(),
                    context.authorSlackId()
            );
            return;
        }

        reservationNotifier.notifyScheduled(
                context.meta(),
                context.reviewerId(),
                context.scheduledAt(),
                context.token(),
                context.authorSlackId()
        );
    }

    private void notifyDuplicate(ReservationContextDto context, ReviewReservation reservation) {
        reservationNotifier.sendDuplicateReservationNoticeToDmAndEphemeral(
                context.token(),
                context.meta().channelId(),
                context.reviewerId(),
                reservation
        );
    }

    private SlackActionResponse executeSafely(
            ReviewScheduleMetaDto meta,
            String token,
            String reviewerId,
            Supplier<SlackActionResponse> action
    ) {
        try {
            return action.get();
        } catch (ReservationScheduleInPastException e) {
            log.info("리뷰 예약 시간 유효성 실패: {}", e.getMessage());

            return SlackActionResponse.errors(Map.of(TIME_BLOCK_ID, e.getMessage()));
        } catch (ActiveReservationAlreadyExistsException e) {
            log.info("리뷰 예약 동시성 중복 발생");

            ReservationContextDto context = buildContext(meta, reviewerId, token, Instant.now(clock));

            findActiveReservation(context).ifPresent(active -> notifyDuplicate(context, active));
            return SlackActionResponse.empty();
        } catch (Exception e) {
            log.error("리뷰 예약 실패", e);

            return errorNotifier.respond(
                    token,
                    meta.channelId(),
                    reviewerId,
                    InteractivityErrorType.RESERVATION_FAILURE
            );
        }
    }

    private Optional<ReviewReservation> findActiveReservation(ReservationContextDto context) {
        return reviewReservationCoordinator.findActive(
                context.meta().teamId(),
                context.projectId(),
                context.reviewerId(),
                context.reservationPullRequest().getPullRequestId()
        );
    }

    private boolean isImmediate(ReservationContextDto context) {
        return !context.scheduledAt().isAfter(Instant.now(clock));
    }

    private ReservationContextDto buildContext(
            ReviewScheduleMetaDto meta,
            String reviewerId,
            String token,
            Instant scheduledAt
    ) {
        Long reservationId = parseReservationId(meta.reservationId());
        boolean isReschedule = reservationId != null;
        ReservationPullRequest reservationPullRequest = ReservationPullRequest.builder()
                                                                          .pullRequestId(meta.pullRequestId())
                                                                          .pullRequestNumber(meta.pullRequestNumber())
                                                                          .pullRequestTitle(meta.pullRequestTitle())
                                                                          .pullRequestUrl(meta.pullRequestUrl())
                                                                          .build();

        return ReservationContextDto.builder()
                                    .meta(meta)
                                    .reviewerId(reviewerId)
                                    .token(token)
                                    .scheduledAt(scheduledAt)
                                    .authorSlackId(authorResolver.resolveAuthorSlackId(meta))
                                    .isReschedule(isReschedule)
                                    .projectId(projectIdResolver.resolve(meta.projectId(), meta.teamId()))
                                    .reservationPullRequest(reservationPullRequest)
                                    .reservationId(reservationId)
                                    .build();
    }

    private Long parseReservationId(String rawId) {
        return Optional.ofNullable(rawId)
                       .filter(value -> !value.isBlank())
                       .map(value -> {
                           try {
                               return Long.parseLong(value);
                           } catch (NumberFormatException e) {
                               log.warn("유효하지 않은 reservationId: {}", value);
                               return null;
                           }
                       })
                       .orElse(null);
    }
}
