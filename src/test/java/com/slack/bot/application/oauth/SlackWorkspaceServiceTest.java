package com.slack.bot.application.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.oauth.dto.response.SlackTokenResponse;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.infrastructure.workspace.persistence.JpaWorkspaceRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackWorkspaceServiceTest {

    @Autowired
    SlackWorkspaceService slackWorkspaceService;

    @Autowired
    JpaWorkspaceRepository jpaWorkspaceRepository;

    @Test
    void 기존_워크스페이스가_있으면_재연결_처리한다() {
        // given
        Workspace existingWorkspace = Workspace.create("T123", "old-token", "old-user");
        jpaWorkspaceRepository.save(existingWorkspace);

        SlackTokenResponse tokenResponse = new SlackTokenResponse(
                true,
                "xoxb-new-token",
                new SlackTokenResponse.Team("T123", "테스트 팀"),
                new SlackTokenResponse.AuthedUser("U456")
        );

        // when
        slackWorkspaceService.registerWorkspace(tokenResponse);

        // then
        Workspace actual = jpaWorkspaceRepository.findByTeamId("T123")
                                                 .orElseThrow();

        assertAll(
                () -> assertThat(jpaWorkspaceRepository.count()).isEqualTo(1),
                () -> assertThat(actual.getAccessToken()).isEqualTo("xoxb-new-token"),
                () -> assertThat(actual.getInstalledBy()).isEqualTo("U456")
        );
    }

    @Test
    void 슬랙_봇을_설치한_워크스페이스를_등록한다() {
        // given
        SlackTokenResponse tokenResponse = new SlackTokenResponse(
                true,
                "xoxb-test-token",
                new SlackTokenResponse.Team("T123", "테스트 팀"),
                new SlackTokenResponse.AuthedUser("U123")
        );

        // when
        slackWorkspaceService.registerWorkspace(tokenResponse);

        // then
        Optional<Workspace> actual = jpaWorkspaceRepository.findByTeamId("T123");

        assertAll(
                () -> assertThat(actual).isPresent(),
                () -> assertThat(actual.get().getAccessToken()).isEqualTo("xoxb-test-token"),
                () -> assertThat(actual.get().getInstalledBy()).isEqualTo("U123")
        );
    }
}
