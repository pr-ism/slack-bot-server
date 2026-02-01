package com.slack.bot.application.interactivity.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.slack.bot.application.interactivity.client.ReviewReminderSlackDirectMessageClient;
import com.slack.bot.application.interactivity.client.exception.SlackDmException;
import com.slack.bot.domain.reservation.ReviewReminder;
import com.slack.bot.domain.reservation.repository.ReviewReminderRepository;
import com.slack.bot.domain.reservation.vo.ReminderDestination;
import com.slack.bot.domain.reservation.vo.ReminderParticipants;
import com.slack.bot.domain.reservation.vo.ReminderPullRequest;
import com.slack.bot.domain.setting.NotificationSettings;
import com.slack.bot.domain.setting.repository.NotificationSettingsRepository;
import com.slack.bot.domain.setting.vo.OptionalNotifications;
import com.slack.bot.domain.setting.vo.ReservationConfirmed;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import com.slack.bot.global.config.properties.ReviewReminderMessageProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class ReviewReminderDispatcherTest {

    private static final String TEAM_ID = "T1";
    private static final String REVIEWER_ID = "U-REVIEWER";
    private static final String AUTHOR_ID = "U-AUTHOR";
    private static final String TOKEN = "xoxb-token";
    private static final Instant FIXED_NOW = Instant.parse("2024-01-01T00:00:00Z");

    @Mock
    WorkspaceRepository workspaceRepository;

    @Mock
    ReviewReminderRepository reviewReminderRepository;

    @Mock
    NotificationSettingsRepository notificationSettingsRepository;

    @Mock
    ReviewReminderSlackDirectMessageClient reviewReminderSlackDirectMessageClient;

    private ReviewReminderDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        ReviewReminderMessageProperties messageProperties = new ReviewReminderMessageProperties(
                "리뷰어 %s %s",
                "PR: %s (%s)"
        );

        dispatcher = new ReviewReminderDispatcher(
                clock,
                workspaceRepository,
                reviewReminderRepository,
                reviewReminderSlackDirectMessageClient,
                messageProperties,
                notificationSettingsRepository
        );

        given(reviewReminderRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void 알림_설정이_꺼져_있으면_DM을_보내지_않는다() {
        // given
        ReviewReminder reminder = createReminder(AUTHOR_ID, REVIEWER_ID);
        Workspace workspace = Workspace.builder()
                                       .teamId(TEAM_ID)
                                       .accessToken(TOKEN)
                                       .botUserId("B1")
                                       .userId(1L)
                                       .build();
        OptionalNotifications disabled = OptionalNotifications.defaults()
                                                             .updateReviewReminder(false);
        NotificationSettings notificationSettings = NotificationSettings.create(
                1L,
                ReservationConfirmed.defaults(),
                disabled
        );

        given(workspaceRepository.findByTeamId(TEAM_ID)).willReturn(Optional.of(workspace));
        given(notificationSettingsRepository.findBySlackUser(TEAM_ID, REVIEWER_ID))
                .willReturn(Optional.of(notificationSettings));

        // when
        dispatcher.send(reminder);

        // then
        ArgumentCaptor<ReviewReminder> savedReminderCaptor = ArgumentCaptor.forClass(ReviewReminder.class);
        verify(reviewReminderRepository).save(savedReminderCaptor.capture());

        assertThat(savedReminderCaptor.getValue().isFired()).isTrue();
        verify(reviewReminderSlackDirectMessageClient, never()).send(any(), any(), any());
    }

    @Test
    void 워크스페이스_토큰을_찾지_못하면_발사로만_처리한다() {
        // given
        ReviewReminder reminder = createReminder(AUTHOR_ID, REVIEWER_ID);
        given(workspaceRepository.findByTeamId(TEAM_ID)).willReturn(Optional.empty());

        // when
        dispatcher.send(reminder);

        // then
        verify(reviewReminderRepository).save(any());
        verifyNoInteractions(notificationSettingsRepository, reviewReminderSlackDirectMessageClient);
    }

    @Test
    void 리뷰어와_작성자에게_DM을_보내고_발사로_표시한다() {
        // given
        ReviewReminder reminder = createReminder(AUTHOR_ID, REVIEWER_ID);
        Workspace workspace = Workspace.builder()
                                       .teamId(TEAM_ID)
                                       .accessToken(TOKEN)
                                       .botUserId("B1")
                                       .userId(1L)
                                       .build();
        NotificationSettings notificationSettings = NotificationSettings.defaults(1L);

        given(workspaceRepository.findByTeamId(TEAM_ID)).willReturn(Optional.of(workspace));
        given(notificationSettingsRepository.findBySlackUser(TEAM_ID, REVIEWER_ID))
                .willReturn(Optional.of(notificationSettings));

        // when
        dispatcher.send(reminder);

        // then
        String expectedAuthorMessage = "리뷰어 <@U-REVIEWER> <https://github.com/org/repo/pull/1|Great PR>";
        String expectedReviewerMessage = "PR: Great PR (https://github.com/org/repo/pull/1)";

        ArgumentCaptor<ReviewReminder> savedReminderCaptor = ArgumentCaptor.forClass(ReviewReminder.class);
        assertAll(
                () -> verify(reviewReminderSlackDirectMessageClient).send(TOKEN, AUTHOR_ID, expectedAuthorMessage),
                () -> verify(reviewReminderSlackDirectMessageClient).send(TOKEN, REVIEWER_ID, expectedReviewerMessage),
                () -> verify(reviewReminderRepository).save(savedReminderCaptor.capture()),
                () -> assertThat(savedReminderCaptor.getValue().isFired()).isTrue()
        );
    }

    @Test
    void 슬랙_ID가_비어_있으면_DM_전송을_건너뛴다() {
        // given
        ReviewReminder reminder = createReminder("", REVIEWER_ID);
        Workspace workspace = Workspace.builder()
                                       .teamId(TEAM_ID)
                                       .accessToken(TOKEN)
                                       .botUserId("B1")
                                       .userId(1L)
                                       .build();
        NotificationSettings notificationSettings = NotificationSettings.defaults(1L);

        given(workspaceRepository.findByTeamId(TEAM_ID)).willReturn(Optional.of(workspace));
        given(notificationSettingsRepository.findBySlackUser(TEAM_ID, REVIEWER_ID))
                .willReturn(Optional.of(notificationSettings));

        // when
        dispatcher.send(reminder);

        // then
        assertAll(
                () -> verify(reviewReminderSlackDirectMessageClient, never()).send(eq(TOKEN), eq(""), anyString()),
                () -> verify(reviewReminderSlackDirectMessageClient).send(
                        TOKEN,
                        REVIEWER_ID,
                        "PR: Great PR (https://github.com/org/repo/pull/1)"
                )
        );
    }

    @Test
    void PR_링크가_없으면_제목만_전달한다() {
        // given
        ReviewReminder reminder = ReviewReminder.builder()
                                               .reservationId(200L)
                                               .scheduledAt(FIXED_NOW.plusSeconds(60))
                                               .destination(ReminderDestination.builder()
                                                                               .teamId(TEAM_ID)
                                                                               .channelId("C1")
                                                                               .build())
                                               .participants(ReminderParticipants.builder()
                                                                                  .pullRequestAuthorSlackId(AUTHOR_ID)
                                                                                  .reviewerSlackId(REVIEWER_ID)
                                                                                  .build())
                                               .pullRequest(ReminderPullRequest.builder()
                                                                               .pullRequestUrl(null)
                                                                               .pullRequestTitle("OnlyTitle")
                                                                               .build())
                                               .build();
        Workspace workspace = Workspace.builder()
                                       .teamId(TEAM_ID)
                                       .accessToken(TOKEN)
                                       .botUserId("B1")
                                       .userId(1L)
                                       .build();
        NotificationSettings notificationSettings = NotificationSettings.defaults(1L);

        given(workspaceRepository.findByTeamId(TEAM_ID)).willReturn(Optional.of(workspace));
        given(notificationSettingsRepository.findBySlackUser(TEAM_ID, REVIEWER_ID))
                .willReturn(Optional.of(notificationSettings));

        // when
        dispatcher.send(reminder);

        // then
        verify(reviewReminderSlackDirectMessageClient).send(TOKEN, AUTHOR_ID, "리뷰어 <@U-REVIEWER> OnlyTitle");
    }

    @Test
    void PR_링크와_제목이_모두_없으면_빈값을_전달한다() {
        // given
        ReviewReminder reminder = ReviewReminder.builder()
                                               .reservationId(201L)
                                               .scheduledAt(FIXED_NOW.plusSeconds(60))
                                               .destination(ReminderDestination.builder()
                                                                               .teamId(TEAM_ID)
                                                                               .channelId("C1")
                                                                               .build())
                                               .participants(ReminderParticipants.builder()
                                                                                  .pullRequestAuthorSlackId(AUTHOR_ID)
                                                                                  .reviewerSlackId(REVIEWER_ID)
                                                                                  .build())
                                               .pullRequest(ReminderPullRequest.builder()
                                                                               .pullRequestUrl(null)
                                                                               .pullRequestTitle(null)
                                                                               .build())
                                               .build();
        Workspace workspace = Workspace.builder()
                                       .teamId(TEAM_ID)
                                       .accessToken(TOKEN)
                                       .botUserId("B1")
                                       .userId(1L)
                                       .build();
        NotificationSettings notificationSettings = NotificationSettings.defaults(1L);

        given(workspaceRepository.findByTeamId(TEAM_ID)).willReturn(Optional.of(workspace));
        given(notificationSettingsRepository.findBySlackUser(TEAM_ID, REVIEWER_ID))
                .willReturn(Optional.of(notificationSettings));

        // when
        dispatcher.send(reminder);

        // then
        verify(reviewReminderSlackDirectMessageClient).send(TOKEN, AUTHOR_ID, "리뷰어 <@U-REVIEWER> ");
    }

    @Test
    void 리뷰어_ID가_null이면_멘션을_생략한다() {
        // given
        ReviewReminder reminder = ReviewReminder.builder()
                                               .reservationId(300L)
                                               .scheduledAt(FIXED_NOW.plusSeconds(60))
                                               .destination(ReminderDestination.builder()
                                                                               .teamId(TEAM_ID)
                                                                               .channelId("C1")
                                                                               .build())
                                               .participants(ReminderParticipants.builder()
                                                                                  .pullRequestAuthorSlackId(AUTHOR_ID)
                                                                                  .reviewerSlackId(null)
                                                                                  .build())
                                               .pullRequest(ReminderPullRequest.builder()
                                                                               .pullRequestUrl("https://github.com/org/repo/pull/1")
                                                                               .pullRequestTitle("Great PR")
                                                                               .build())
                                               .build();
        Workspace workspace = Workspace.builder()
                                       .teamId(TEAM_ID)
                                       .accessToken(TOKEN)
                                       .botUserId("B1")
                                       .userId(1L)
                                       .build();
        NotificationSettings notificationSettings = NotificationSettings.defaults(1L);

        given(workspaceRepository.findByTeamId(TEAM_ID)).willReturn(Optional.of(workspace));
        given(notificationSettingsRepository.findBySlackUser(TEAM_ID, null))
                .willReturn(Optional.of(notificationSettings));

        // when
        dispatcher.send(reminder);

        // then
        verify(reviewReminderSlackDirectMessageClient).send(TOKEN, AUTHOR_ID, "리뷰어  <https://github.com/org/repo/pull/1|Great PR>");
    }

    @Test
    void 리뷰어_ID가_빈문자면_멘션을_생략한다() {
        // given
        ReviewReminder reminder = ReviewReminder.builder()
                                               .reservationId(310L)
                                               .scheduledAt(FIXED_NOW.plusSeconds(60))
                                               .destination(ReminderDestination.builder()
                                                                               .teamId(TEAM_ID)
                                                                               .channelId("C1")
                                                                               .build())
                                               .participants(ReminderParticipants.builder()
                                                                                  .pullRequestAuthorSlackId(AUTHOR_ID)
                                                                                  .reviewerSlackId("")
                                                                                  .build())
                                               .pullRequest(ReminderPullRequest.builder()
                                                                               .pullRequestUrl("https://github.com/org/repo/pull/1")
                                                                               .pullRequestTitle("Great PR")
                                                                               .build())
                                               .build();
        Workspace workspace = Workspace.builder()
                                       .teamId(TEAM_ID)
                                       .accessToken(TOKEN)
                                       .botUserId("B1")
                                       .userId(1L)
                                       .build();
        NotificationSettings notificationSettings = NotificationSettings.defaults(1L);

        given(workspaceRepository.findByTeamId(TEAM_ID)).willReturn(Optional.of(workspace));
        given(notificationSettingsRepository.findBySlackUser(TEAM_ID, "")).willReturn(Optional.of(notificationSettings));

        // when
        dispatcher.send(reminder);

        // then
        verify(reviewReminderSlackDirectMessageClient).send(TOKEN, AUTHOR_ID, "리뷰어  <https://github.com/org/repo/pull/1|Great PR>");
    }

    @Test
    void 제목이_없고_URL만_있으면_템플릿을_유지한다() {
        // given
        ReviewReminder reminder = ReviewReminder.builder()
                                               .reservationId(320L)
                                               .scheduledAt(FIXED_NOW.plusSeconds(60))
                                               .destination(ReminderDestination.builder()
                                                                               .teamId(TEAM_ID)
                                                                               .channelId("C1")
                                                                               .build())
                                               .participants(ReminderParticipants.builder()
                                                                                  .pullRequestAuthorSlackId(AUTHOR_ID)
                                                                                  .reviewerSlackId(REVIEWER_ID)
                                                                                  .build())
                                               .pullRequest(ReminderPullRequest.builder()
                                                                               .pullRequestUrl("https://github.com/org/repo/pull/1")
                                                                               .pullRequestTitle("")
                                                                               .build())
                                               .build();
        Workspace workspace = Workspace.builder()
                                       .teamId(TEAM_ID)
                                       .accessToken(TOKEN)
                                       .botUserId("B1")
                                       .userId(1L)
                                       .build();
        NotificationSettings notificationSettings = NotificationSettings.defaults(1L);

        given(workspaceRepository.findByTeamId(TEAM_ID)).willReturn(Optional.of(workspace));
        given(notificationSettingsRepository.findBySlackUser(TEAM_ID, REVIEWER_ID))
                .willReturn(Optional.of(notificationSettings));

        // when
        dispatcher.send(reminder);

        // then
        verify(reviewReminderSlackDirectMessageClient).send(TOKEN, REVIEWER_ID, "PR:  (https://github.com/org/repo/pull/1)");
    }

    @Test
    void DM_전송_실패시에도_fired로_저장한다() {
        // given
        ReviewReminder reminder = createReminder(AUTHOR_ID, REVIEWER_ID);
        Workspace workspace = Workspace.builder()
                                       .teamId(TEAM_ID)
                                       .accessToken(TOKEN)
                                       .botUserId("B1")
                                       .userId(1L)
                                       .build();
        NotificationSettings notificationSettings = NotificationSettings.defaults(1L);

        given(workspaceRepository.findByTeamId(TEAM_ID)).willReturn(Optional.of(workspace));
        given(notificationSettingsRepository.findBySlackUser(TEAM_ID, REVIEWER_ID))
                .willReturn(Optional.of(notificationSettings));
        willThrow(new SlackDmException("전송 실패"))
                .given(reviewReminderSlackDirectMessageClient)
                .send(TOKEN, AUTHOR_ID, "리뷰어 <@U-REVIEWER> <https://github.com/org/repo/pull/1|Great PR>");

        // when
        dispatcher.send(reminder);

        // then
        ArgumentCaptor<ReviewReminder> savedReminderCaptor = ArgumentCaptor.forClass(ReviewReminder.class);

        assertAll(
                () -> verify(reviewReminderRepository).save(savedReminderCaptor.capture()),
                () -> assertThat(savedReminderCaptor.getValue().isFired()).isTrue()
        );
    }

    private ReviewReminder createReminder(String authorId, String reviewerId) {
        return ReviewReminder.builder()
                             .reservationId(100L)
                             .scheduledAt(FIXED_NOW.plusSeconds(60))
                             .destination(ReminderDestination.builder()
                                                             .teamId(TEAM_ID)
                                                             .channelId("C1")
                                                             .build())
                             .participants(ReminderParticipants.builder()
                                                                .pullRequestAuthorSlackId(authorId)
                                                                .reviewerSlackId(reviewerId)
                                                                .build())
                             .pullRequest(ReminderPullRequest.builder()
                                                             .pullRequestUrl("https://github.com/org/repo/pull/1")
                                                             .pullRequestTitle("Great PR")
                                                             .build())
                             .build();
    }
}
