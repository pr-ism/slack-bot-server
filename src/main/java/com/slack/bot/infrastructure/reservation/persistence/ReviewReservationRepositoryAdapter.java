package com.slack.bot.infrastructure.reservation.persistence;

import static com.slack.bot.domain.reservation.QReviewReservation.reviewReservation;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.domain.reservation.ReservationStatus;
import com.slack.bot.domain.reservation.ReviewReservation;
import com.slack.bot.domain.reservation.repository.ReviewReservationRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ReviewReservationRepositoryAdapter implements ReviewReservationRepository {

    private final JPAQueryFactory queryFactory;
    private final JpaReviewReservationRepository jpaReservationRepository;

    @Override
    @Transactional
    public ReviewReservation save(ReviewReservation reservation) {
        return jpaReservationRepository.save(reservation);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReviewReservation> findById(Long reservationId) {
        return jpaReservationRepository.findById(reservationId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReviewReservation> findActive(
            String teamId,
            Long projectId,
            String reviewerSlackId
    ) {
        ReviewReservation result = queryFactory
                .selectFrom(reviewReservation)
                .where(
                        reviewReservation.teamId.eq(teamId),
                        reviewReservation.projectId.eq(projectId),
                        reviewReservation.reviewerSlackId.eq(reviewerSlackId),
                        reviewReservation.status.eq(ReservationStatus.ACTIVE)
                )
                .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReviewReservation> findActive(
            String teamId,
            Long projectId,
            String reviewerSlackId,
            Long pullRequestId
    ) {
        ReviewReservation result = queryFactory
                .selectFrom(reviewReservation)
                .where(
                        reviewReservation.teamId.eq(teamId),
                        reviewReservation.projectId.eq(projectId),
                        reviewReservation.reviewerSlackId.eq(reviewerSlackId),
                        reviewReservation.reservationPullRequest.pullRequestId.eq(pullRequestId),
                        reviewReservation.status.eq(ReservationStatus.ACTIVE)
                )
                .fetchOne();

        return Optional.ofNullable(result);
    }
}
