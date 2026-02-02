package com.slack.bot.presentation.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.request.RequestDocumentation.formParameters;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.slack.bot.application.command.CommandService;
import com.slack.bot.application.command.dto.request.SlackCommandRequest;
import com.slack.bot.application.command.exception.WorkspaceNotFoundException;
import com.slack.bot.presentation.CommonControllerSliceTestSupport;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CommandControllerTest extends CommonControllerSliceTestSupport {

    @Autowired
    CommandService commandService;

    @Test
    void 커맨드_처리_성공_테스트() throws Exception {
        // given
        given(commandService.handle(any(SlackCommandRequest.class))).willReturn("ok");

        // when & then
        ResultActions resultActions = mockMvc.perform(
                                                     post("/slack/commands")
                                                             .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                                             .param("text", "help")
                                                             .param("userId", "U123")
                                                             .param("teamId", "T456")
                                             )
                                             .andExpect(status().isOk())
                                             .andExpect(content().string("\"ok\""));

        커맨드_처리_성공_문서화(resultActions);
    }

    private void 커맨드_처리_성공_문서화(ResultActions resultActions) throws Exception {
        resultActions.andDo(
                restDocs.document(
                        formParameters(
                                parameterWithName("text").description("슬랙 명령어 텍스트"),
                                parameterWithName("userId").description("슬랙 사용자 ID"),
                                parameterWithName("teamId").description("슬랙 팀(워크스페이스) ID")
                        )
                )
        );
    }

    @Test
    void 워크스페이스가_없으면_커맨드_처리는_실패한다() throws Exception {
        // given
        willThrow(new WorkspaceNotFoundException()).given(commandService).handle(any(SlackCommandRequest.class));

        // when & then
        mockMvc.perform(
                       post("/slack/commands")
                               .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                               .param("text", "help")
                               .param("userId", "U123")
                               .param("teamId", "T456")
               )
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.errorCode").value("C00"))
               .andExpect(jsonPath("$.message").value("워크스페이스 없음"));
    }
}
