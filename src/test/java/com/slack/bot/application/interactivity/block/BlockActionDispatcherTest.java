package com.slack.bot.application.interactivity.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.api.model.view.View;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interactivity.block.dto.BlockActionCommandDto;
import com.slack.bot.application.interactivity.block.dto.BlockActionOutcomeDto;
import com.slack.bot.application.interactivity.client.NotificationApiClient;
import com.slack.bot.application.interactivity.publisher.ReviewInteractionEvent;
import com.slack.bot.application.interactivity.publisher.ReviewReservationChangeEvent;
import com.slack.bot.application.interactivity.publisher.ReviewReservationRequestEvent;
import com.slack.bot.domain.member.ProjectMember;
import com.slack.bot.domain.member.repository.ProjectMemberRepository;
import com.slack.bot.domain.reservation.ReservationStatus;
import com.slack.bot.domain.reservation.ReviewReservation;
import com.slack.bot.domain.reservation.repository.ReviewReservationRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@RecordApplicationEvents
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BlockActionDispatcherTest {

    @Autowired
    BlockActionDispatcher blockActionDispatcher;

    @Autowired
    ProjectMemberRepository projectMemberRepository;

    @Autowired
    ReviewReservationRepository reviewReservationRepository;

    @Autowired
    NotificationApiClient notificationApiClient;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/notification/project_member_t1.sql",
            "classpath:sql/fixtures/notification/notification_settings_t1_u1_dm_enabled.sql"
    })
    void 클레임_프리픽스_액션이면_깃허브_연동을_수행하고_빈_결과를_반환한다() {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1")).willReturn("D1");
        BlockActionCommandDto command = command(
                objectMapper.createObjectNode(),
                objectMapper.createObjectNode().put("value", "new-github-id"),
                "claim_new-github-id",
                BlockActionType.CLAIM_PREFIX,
                "U1"
        );

        // when
        BlockActionOutcomeDto actual = blockActionDispatcher.dispatch(command);

        // then
        Optional<ProjectMember> mapped = projectMemberRepository.findBySlackUser("T1", "U1");

        assertAll(
                () -> assertThat(actual).isEqualTo(BlockActionOutcomeDto.empty()),
                () -> assertThat(mapped).isPresent(),
                () -> assertThat(mapped.get().getGithubId().getValue()).isEqualTo("new-github-id"),
                () -> verify(notificationApiClient).sendEphemeralMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        eq("U1"),
                        argThat(message -> message.contains("new-github-id"))
                ),
                () -> verify(notificationApiClient).sendMessage(
                        eq("xoxb-test-token"),
                        eq("D1"),
                        argThat(message -> message.contains("new-github-id"))
                )
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/reservation/project_123.sql")
    void 예약_생성_모달_열기_액션이면_예약_요청_이벤트를_발행하고_모달을_연다(ApplicationEvents applicationEvents) {
        // given
        BlockActionCommandDto command = command(
                objectMapper.createObjectNode().put("trigger_id", "TRIGGER_1"),
                objectMapper.createObjectNode().put("value", metaJsonWithProjectId("123")),
                BlockActionType.OPEN_REVIEW_SCHEDULER.value(),
                BlockActionType.OPEN_REVIEW_SCHEDULER,
                "U1"
        );

        // when
        BlockActionOutcomeDto actual = blockActionDispatcher.dispatch(command);

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo(BlockActionOutcomeDto.empty()),
                () -> assertThat(applicationEvents.stream(ReviewReservationRequestEvent.class).toList())
                        .singleElement()
                        .satisfies(event -> assertAll(
                                () -> assertThat(event.teamId()).isEqualTo("T1"),
                                () -> assertThat(event.channelId()).isEqualTo("C1"),
                                () -> assertThat(event.slackUserId()).isEqualTo("U1")
                        )),
                () -> verify(notificationApiClient).openModal(
                        eq("xoxb-test-token"),
                        eq("TRIGGER_1"),
                        any(View.class)
                )
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql")
    void 예약_변경_액션이면_변경_모달을_열고_변경_이벤트를_발행한다(ApplicationEvents applicationEvents) {
        // given
        BlockActionCommandDto command = command(
                objectMapper.createObjectNode().put("trigger_id", "TRIGGER_1"),
                objectMapper.createObjectNode().put("value", "100"),
                BlockActionType.CHANGE_REVIEW_RESERVATION.value(),
                BlockActionType.CHANGE_REVIEW_RESERVATION,
                "U1"
        );

        // when
        BlockActionOutcomeDto actual = blockActionDispatcher.dispatch(command);

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo(BlockActionOutcomeDto.empty()),
                () -> assertThat(applicationEvents.stream(ReviewReservationChangeEvent.class).toList())
                        .singleElement()
                        .satisfies(event -> assertAll(
                                () -> assertThat(event.teamId()).isEqualTo("T1"),
                                () -> assertThat(event.channelId()).isEqualTo("C1"),
                                () -> assertThat(event.slackUserId()).isEqualTo("U1"),
                                () -> assertThat(event.reservationId()).isEqualTo(100L)
                        )),
                () -> verify(notificationApiClient).openModal(
                        eq("xoxb-test-token"),
                        eq("TRIGGER_1"),
                        any(View.class)
                )
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql")
    void 예약_취소_액션이면_예약을_취소하고_취소된_예약을_반환한다() {
        // given
        BlockActionCommandDto command = command(
                objectMapper.createObjectNode(),
                objectMapper.createObjectNode().put("value", "100"),
                BlockActionType.CANCEL_REVIEW_RESERVATION.value(),
                BlockActionType.CANCEL_REVIEW_RESERVATION,
                "U1"
        );

        // when
        BlockActionOutcomeDto actual = blockActionDispatcher.dispatch(command);

        // then
        Optional<ReviewReservation> cancelled = reviewReservationRepository.findById(100L);

        assertAll(
                () -> assertThat(actual.duplicateReservation()).isNull(),
                () -> assertThat(actual.cancelledReservation()).isNotNull(),
                () -> assertThat(actual.cancelledReservation().getId()).isEqualTo(100L),
                () -> assertThat(cancelled).isPresent(),
                () -> assertThat(cancelled.get().getStatus()).isEqualTo(ReservationStatus.CANCELLED)
        );
    }

    @Test
    void 알수없는_액션_타입이면_아무것도_수행하지_않고_빈_결과를_반환한다(ApplicationEvents applicationEvents) {
        // given
        BlockActionCommandDto command = command(
                objectMapper.createObjectNode().put("trigger_id", "TRIGGER_1"),
                objectMapper.createObjectNode().put("value", "100"),
                "unknown-action",
                BlockActionType.UNKNOWN,
                "U1"
        );

        // when
        BlockActionOutcomeDto actual = blockActionDispatcher.dispatch(command);

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo(BlockActionOutcomeDto.empty()),
                () -> assertThat(applicationEvents.stream(ReviewInteractionEvent.class).toList()).isEmpty(),
                () -> verify(notificationApiClient, never()).openModal(any(), any(), any(View.class)),
                () -> verify(notificationApiClient, never()).sendEphemeralMessage(any(), any(), any(), any())
        );
    }

    private BlockActionCommandDto command(
            JsonNode payload,
            JsonNode action,
            String actionId,
            BlockActionType actionType,
            String slackUserId
    ) {
        return new BlockActionCommandDto(
                payload,
                action,
                actionId,
                actionType,
                "T1",
                "C1",
                slackUserId,
                "xoxb-test-token"
        );
    }

    private String metaJsonWithProjectId(String projectId) {
        return objectMapper.createObjectNode()
                           .put("team_id", "T1")
                           .put("channel_id", "C1")
                           .put("pull_request_id", 10L)
                           .put("pull_request_number", 10)
                           .put("pull_request_title", "PR 제목")
                           .put("pull_request_url", "https://github.com/org/repo/pull/10")
                           .put("project_id", projectId)
                           .put("author_github_id", "author-gh")
                           .put("author_slack_id", "U_AUTHOR")
                           .put("reservation_id", "R1")
                           .toString();
    }
}
