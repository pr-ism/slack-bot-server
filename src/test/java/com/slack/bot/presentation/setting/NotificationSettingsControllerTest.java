package com.slack.bot.presentation.setting;

import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.slack.bot.application.setting.NotificationSettingsService;
import com.slack.bot.application.setting.dto.request.UpdateNotificationSettingsRequest;
import com.slack.bot.application.setting.dto.response.NotificationSettingsResponse;
import com.slack.bot.domain.setting.DeliverySpace;
import com.slack.bot.presentation.CommonControllerSliceTestSupport;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NotificationSettingsControllerTest extends CommonControllerSliceTestSupport {

    @Autowired
    NotificationSettingsService notificationSettingsService;

    @Test
    void 알림_설정_조회_성공_테스트() throws Exception {
        // given
        Long projectMemberId = 1L;
        NotificationSettingsResponse response = new NotificationSettingsResponse(
                projectMemberId,
                DeliverySpace.DIRECT_MESSAGE,
                true,
                true,
                true,
                true
        );

        given(notificationSettingsService.findSettings(projectMemberId)).willReturn(response);

        // when & then
        ResultActions resultActions = mockMvc.perform(
                                                     get("/notification-settings")
                                                             .queryParam("projectMemberId", String.valueOf(projectMemberId))
                                                             .accept(MediaType.APPLICATION_JSON)
                                             )
                                             .andExpect(status().isOk())
                                             .andExpect(jsonPath("$.projectMemberId").value(projectMemberId))
                                             .andExpect(jsonPath("$.reservationConfirmedSpace").value("DIRECT_MESSAGE"))
                                             .andExpect(jsonPath("$.reservationCanceledConfirmationEnabled").value(true))
                                             .andExpect(jsonPath("$.reviewReminderEnabled").value(true))
                                             .andExpect(jsonPath("$.prMentionEnabled").value(true))
                                             .andExpect(jsonPath("$.reviewCompletedEnabled").value(true));

        알림_설정_조회_문서화(resultActions);
    }

    private void 알림_설정_조회_문서화(ResultActions resultActions) throws Exception {
        resultActions.andDo(
                restDocs.document(
                        queryParameters(
                                parameterWithName("projectMemberId").description("프로젝트 멤버 ID")
                        ),
                        responseFields(
                                fieldWithPath("projectMemberId").type(JsonFieldType.NUMBER).description("프로젝트 멤버 ID"),
                                fieldWithPath("reservationConfirmedSpace").type(JsonFieldType.STRING).description("리뷰 예약 완료 알림 전달 공간"),
                                fieldWithPath("reservationCanceledConfirmationEnabled").type(JsonFieldType.BOOLEAN).description("리뷰 예약 취소 확인 알림 활성화 여부"),
                                fieldWithPath("reviewReminderEnabled").type(JsonFieldType.BOOLEAN).description("리뷰 리마인드 알림 활성화 여부"),
                                fieldWithPath("prMentionEnabled").type(JsonFieldType.BOOLEAN).description("PR 멘션 알림 활성화 여부"),
                                fieldWithPath("reviewCompletedEnabled").type(JsonFieldType.BOOLEAN).description("리뷰 완료 알림 활성화 여부")
                        )
                )
        );
    }

    @Test
    void 알림_설정_수정_성공_테스트() throws Exception {
        // given
        Long projectMemberId = 2L;
        UpdateNotificationSettingsRequest request = new UpdateNotificationSettingsRequest(
                DeliverySpace.TRIGGER_CHANNEL,
                false,
                true,
                false,
                true
        );
        NotificationSettingsResponse response = new NotificationSettingsResponse(
                projectMemberId,
                DeliverySpace.TRIGGER_CHANNEL,
                false,
                true,
                false,
                true
        );

        given(notificationSettingsService.updateSettings(projectMemberId, request)).willReturn(response);

        // when & then
        ResultActions resultActions = mockMvc.perform(
                                                     put("/notification-settings")
                                                             .queryParam("projectMemberId", String.valueOf(projectMemberId))
                                                             .contentType(MediaType.APPLICATION_JSON)
                                                             .content(objectMapper.writeValueAsString(request))
                                                             .accept(MediaType.APPLICATION_JSON)
                                             )
                                             .andExpect(status().isOk())
                                             .andExpect(jsonPath("$.projectMemberId").value(projectMemberId))
                                             .andExpect(jsonPath("$.reservationConfirmedSpace").value("TRIGGER_CHANNEL"))
                                             .andExpect(jsonPath("$.reservationCanceledConfirmationEnabled").value(false))
                                             .andExpect(jsonPath("$.reviewReminderEnabled").value(true))
                                             .andExpect(jsonPath("$.prMentionEnabled").value(false))
                                             .andExpect(jsonPath("$.reviewCompletedEnabled").value(true));

        알림_설정_수정_문서화(resultActions);
    }

    private void 알림_설정_수정_문서화(ResultActions resultActions) throws Exception {
        resultActions.andDo(
                restDocs.document(
                        queryParameters(
                                parameterWithName("projectMemberId").description("프로젝트 멤버 ID")
                        ),
                        requestFields(
                                fieldWithPath("reservationConfirmedSpace").type(JsonFieldType.STRING).description("리뷰 예약 완료 알림 전달 공간"),
                                fieldWithPath("reservationCanceledConfirmationEnabled").type(JsonFieldType.BOOLEAN).description("리뷰 예약 취소 확인 알림 활성화 여부"),
                                fieldWithPath("reviewReminderEnabled").type(JsonFieldType.BOOLEAN).description("리뷰 리마인드 알림 활성화 여부"),
                                fieldWithPath("prMentionEnabled").type(JsonFieldType.BOOLEAN).description("PR 멘션 알림 활성화 여부"),
                                fieldWithPath("reviewCompletedEnabled").type(JsonFieldType.BOOLEAN).description("리뷰 완료 알림 활성화 여부")
                        ),
                        responseFields(
                                fieldWithPath("projectMemberId").type(JsonFieldType.NUMBER).description("프로젝트 멤버 ID"),
                                fieldWithPath("reservationConfirmedSpace").type(JsonFieldType.STRING).description("리뷰 예약 완료 알림 전달 공간"),
                                fieldWithPath("reservationCanceledConfirmationEnabled").type(JsonFieldType.BOOLEAN).description("리뷰 예약 취소 확인 알림 활성화 여부"),
                                fieldWithPath("reviewReminderEnabled").type(JsonFieldType.BOOLEAN).description("리뷰 리마인드 알림 활성화 여부"),
                                fieldWithPath("prMentionEnabled").type(JsonFieldType.BOOLEAN).description("PR 멘션 알림 활성화 여부"),
                                fieldWithPath("reviewCompletedEnabled").type(JsonFieldType.BOOLEAN).description("리뷰 완료 알림 활성화 여부")
                        )
                )
        );
    }
}
