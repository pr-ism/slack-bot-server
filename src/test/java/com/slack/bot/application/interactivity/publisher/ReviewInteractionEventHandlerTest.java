package com.slack.bot.application.interactivity.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.awaitility.Awaitility.await;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.domain.analysis.metadata.reservation.ReviewReservationInteraction;
import com.slack.bot.domain.analysis.metadata.reservation.repository.ReviewReservationInteractionRepository;
import com.slack.bot.infrastructure.analysis.metadata.reservation.persistence.JpaReviewReservationInteractionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewInteractionEventHandlerTest {

    @Autowired
    ReviewInteractionEventPublisher reviewInteractionEventPublisher;

    @Autowired
    ReviewReservationInteractionRepository reviewReservationInteractionRepository;

    @Autowired
    JpaReviewReservationInteractionRepository jpaReviewReservationInteractionRepository;

    @Test
    void 리뷰_예약_관련_이벤트를_처리하면_슬랙_연동_정보를_저장한다() {
        // given
        String teamId = "T1";
        Long projectId = 123L;
        Long pullRequestId = 10L;
        String reviewerSlackId = "U1";
        Instant reviewScheduledAt = Instant.parse("2099-01-01T10:00:00Z");
        Instant firstNotifiedAt = Instant.parse("2099-01-01T09:30:00Z");
        Instant fulfilledNotifiedAt = Instant.parse("2099-01-01T10:30:00Z");

        // when
        reviewInteractionEventPublisher.publish(new ReviewReservationRequestEvent(
                teamId,
                "C1",
                reviewerSlackId,
                projectId,
                pullRequestId,
                "{\"meta\":\"request\"}"
        ));
        reviewInteractionEventPublisher.publish(new ReviewReservationChangeEvent(
                teamId,
                "C1",
                reviewerSlackId,
                100L,
                projectId,
                pullRequestId
        ));
        reviewInteractionEventPublisher.publish(new ReviewReservationCancelEvent(
                teamId,
                "C1",
                reviewerSlackId,
                100L,
                projectId,
                pullRequestId
        ));
        reviewInteractionEventPublisher.publish(new ReviewReservationScheduledEvent(
                teamId,
                "C1",
                reviewerSlackId,
                100L,
                projectId,
                pullRequestId,
                reviewScheduledAt,
                firstNotifiedAt
        ));
        reviewInteractionEventPublisher.publish(new ReviewReservationFulfilledEvent(
                teamId,
                projectId,
                reviewerSlackId,
                pullRequestId,
                fulfilledNotifiedAt
        ));

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            Optional<ReviewReservationInteraction> actual = reviewReservationInteractionRepository.findByReviewKey(
                    teamId,
                    projectId,
                    pullRequestId,
                    reviewerSlackId
            );

            assertAll(
                    () -> assertThat(actual).isPresent(),
                    () -> assertThat(actual.get().getInteractionTimeline().getReviewTimeSelectedAt()).isNotNull(),
                    () -> assertThat(actual.get().getInteractionTimeline().getReviewScheduledAt()).isEqualTo(reviewScheduledAt),
                    () -> assertThat(actual.get().getInteractionTimeline().getPullRequestNotifiedAt()).isEqualTo(fulfilledNotifiedAt),
                    () -> assertThat(actual.get().getInteractionCount().getScheduleChangeCount()).isEqualTo(1),
                    () -> assertThat(actual.get().getInteractionCount().getScheduleCancelCount()).isEqualTo(1),
                    () -> assertThat(actual.get().isReviewFulfilled()).isTrue()
            );
        });
    }

    @Test
    void 같은_리뷰_키의_이벤트는_기존_엔티티를_재사용한다() {
        // given
        ReviewReservationRequestEvent requestEvent = new ReviewReservationRequestEvent(
                "T1",
                "C1",
                "U1",
                123L,
                10L,
                "{\"meta\":\"request\"}"
        );

        // when
        reviewInteractionEventPublisher.publish(requestEvent);
        reviewInteractionEventPublisher.publish(requestEvent);
        reviewInteractionEventPublisher.publish(new ReviewReservationChangeEvent(
                "T1",
                "C1",
                "U1",
                100L,
                123L,
                10L
        ));

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            Optional<ReviewReservationInteraction> actual = reviewReservationInteractionRepository.findByReviewKey(
                    "T1",
                    123L,
                    10L,
                    "U1"
            );
            long savedCount = jpaReviewReservationInteractionRepository.count();

            assertAll(
                    () -> assertThat(actual).isPresent(),
                    () -> assertThat(savedCount).isEqualTo(1L),
                    () -> assertThat(actual.get().getInteractionCount().getScheduleChangeCount()).isEqualTo(1)
            );
        });
    }

    @Test
    void 리뷰_키가_유효하지_않은_이벤트는_저장하지_않는다() {
        // when
        reviewInteractionEventPublisher.publish(new ReviewReservationFulfilledEvent(
                "T1",
                123L,
                "",
                10L,
                Instant.parse("2099-01-01T10:30:00Z")
        ));

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            long actual = jpaReviewReservationInteractionRepository.count();
            assertThat(actual).isZero();
        });
    }

    @Test
    void 리뷰_키의_상위_필드가_유효하지_않은_이벤트는_저장하지_않는다() {
        // when
        reviewInteractionEventPublisher.publish(new ReviewReservationChangeEvent(
                "",
                "C1",
                "U1",
                100L,
                123L,
                10L
        ));
        reviewInteractionEventPublisher.publish(new ReviewReservationChangeEvent(
                "T1",
                "C1",
                "U1",
                100L,
                null,
                10L
        ));
        reviewInteractionEventPublisher.publish(new ReviewReservationChangeEvent(
                "T1",
                "C1",
                "U1",
                100L,
                123L,
                null
        ));

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            long actual = jpaReviewReservationInteractionRepository.count();
            assertThat(actual).isZero();
        });
    }

    @Test
    void pullRequest_알림_발송_시각이_비어_있으면_현재_시각으로_저장한다() {
        // when
        reviewInteractionEventPublisher.publish(new ReviewReservationFulfilledEvent(
                "T1",
                123L,
                "U1",
                10L,
                null
        ));

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            Optional<ReviewReservationInteraction> actual = reviewReservationInteractionRepository.findByReviewKey(
                    "T1",
                    123L,
                    10L,
                    "U1"
            );

            assertAll(
                    () -> assertThat(actual).isPresent(),
                    () -> assertThat(actual.get().getInteractionTimeline().getPullRequestNotifiedAt()).isNotNull()
            );
        });
    }
}
