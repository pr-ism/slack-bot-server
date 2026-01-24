package com.slack.bot.application.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.oauth.dto.response.SlackTokenResponse;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.infrastructure.workspace.persistence.JpaWorkspaceRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RegisterWorkspaceServiceTest {

    @Autowired
    RegisterWorkspaceService registerWorkspaceService;

    @Autowired
    JpaWorkspaceRepository jpaWorkspaceRepository;

    @Test
    void 기존_워크스페이스가_있으면_재연결_처리한다() {
        // given
        Long userId = 1L;
        Workspace existingWorkspace = Workspace.builder()
                                               .teamId("T123")
                                               .accessToken("old-token")
                                               .botUserId("B001")
                                               .userId(userId)
                                               .build();
        jpaWorkspaceRepository.save(existingWorkspace);

        SlackTokenResponse tokenResponse = new SlackTokenResponse(
                true,
                "xoxb-new-token",
                "B002",
                new SlackTokenResponse.Team("T123", "테스트 팀")
        );

        // when
        registerWorkspaceService.registerWorkspace(tokenResponse, userId);

        // then
        Workspace actual = jpaWorkspaceRepository.findByTeamId("T123")
                                                 .orElseThrow();

        assertAll(
                () -> assertThat(jpaWorkspaceRepository.count()).isEqualTo(1),
                () -> assertThat(actual.getAccessToken()).isEqualTo("xoxb-new-token"),
                () -> assertThat(actual.getUserId()).isEqualTo(userId)
        );
    }

    @Test
    void 슬랙_봇을_설치한_워크스페이스를_등록한다() {
        // given
        Long installerId = 2L;
        SlackTokenResponse tokenResponse = new SlackTokenResponse(
                true,
                "xoxb-test-token",
                "B003",
                new SlackTokenResponse.Team("T123", "테스트 팀")
        );

        // when
        registerWorkspaceService.registerWorkspace(tokenResponse, installerId);

        // then
        Optional<Workspace> actual = jpaWorkspaceRepository.findByTeamId("T123");

        assertAll(
                () -> assertThat(actual).isPresent(),
                () -> assertThat(actual.get().getAccessToken()).isEqualTo("xoxb-test-token"),
                () -> assertThat(actual.get().getUserId()).isEqualTo(installerId),
                () -> assertThat(actual.get().getBotUserId()).isEqualTo("B003")
        );
    }
}
