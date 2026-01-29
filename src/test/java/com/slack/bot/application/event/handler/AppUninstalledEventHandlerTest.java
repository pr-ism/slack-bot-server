package com.slack.bot.application.event.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.event.handler.spy.SpyChannelRepository;
import com.slack.bot.application.event.handler.spy.SpyWorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AppUninstalledEventHandlerTest {

    private SpyChannelRepository spyChannelRepository;
    private SpyWorkspaceRepository spyWorkspaceRepository;
    private AppUninstalledEventHandler appUninstalledEventHandler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        spyChannelRepository = new SpyChannelRepository();
        spyWorkspaceRepository = new SpyWorkspaceRepository();

        appUninstalledEventHandler = new AppUninstalledEventHandler(
                spyChannelRepository,
                spyWorkspaceRepository
        );
    }

    @Test
    void 앱이_삭제되면_관련_정보를_삭제한다() throws JsonProcessingException {
        // given
        String teamId = "T_DELETE_TARGET";
        String jsonPayload = """
                {
                    "team_id": "%s",
                    "type": "app_uninstalled"
                }
                """.formatted(teamId);
        JsonNode payload = objectMapper.readTree(jsonPayload);

        // when
        appUninstalledEventHandler.handle(payload);

        // then
        assertAll(
                () -> assertThat(spyChannelRepository.getDeleteByTeamIdCallCount()).isEqualTo(1),
                () -> assertThat(spyChannelRepository.getLastDeletedTeamId()).isEqualTo(teamId),
                () -> assertThat(spyWorkspaceRepository.getDeleteByTeamIdCallCount()).isEqualTo(1),
                () -> assertThat(spyWorkspaceRepository.getLastDeletedTeamId()).isEqualTo(teamId)
        );
    }
}
