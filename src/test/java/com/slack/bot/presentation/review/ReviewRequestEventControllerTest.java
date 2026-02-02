package com.slack.bot.presentation.review;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.slack.bot.application.review.ReviewEventBatch;
import com.slack.bot.application.review.channel.exception.ReviewChannelResolveException;
import com.slack.bot.application.review.client.exception.ReviewSlackApiException;
import com.slack.bot.application.review.dto.request.ReviewRequestEventRequest;
import com.slack.bot.presentation.CommonControllerSliceTestSupport;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewRequestEventControllerTest extends CommonControllerSliceTestSupport {

    @Autowired
    ReviewEventBatch reviewEventBatch;

    @Test
    void 리뷰_요청_이벤트_수신_성공_테스트() throws Exception {
        // given
        ReviewRequestEventRequest body = new ReviewRequestEventRequest(
                "my-repo",
                "PR-1",
                42,
                "Fix bug",
                "https://github.com/pr/1",
                "author-gh",
                List.of("reviewer-gh-1"),
                List.of()
        );

        willDoNothing().given(reviewEventBatch).buffer(any(String.class), any(ReviewRequestEventRequest.class));

        // when & then
        ResultActions resultActions = mockMvc.perform(
                        post("/events/review-request")
                                .header("X-API-Key", "test-api-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body))
                ).andExpect(status().isOk());

        리뷰_요청_이벤트_수신_문서화(resultActions);
    }

    private void 리뷰_요청_이벤트_수신_문서화(ResultActions resultActions) throws Exception {
        resultActions.andDo(
                restDocs.document(
                        requestHeaders(
                                headerWithName("X-API-Key").description("프로젝트 API 키")
                        ),
                        requestFields(
                                fieldWithPath("repositoryName").type(JsonFieldType.STRING).description("GitHub 레포지토리 이름"),
                                fieldWithPath("pullRequestId").type(JsonFieldType.STRING).description("Pull Request 식별자"),
                                fieldWithPath("pullRequestNumber").type(JsonFieldType.NUMBER).description("Pull Request 번호"),
                                fieldWithPath("pullRequestTitle").type(JsonFieldType.STRING).description("Pull Request 제목"),
                                fieldWithPath("pullRequestUrl").type(JsonFieldType.STRING).description("Pull Request URL"),
                                fieldWithPath("authorGithubId").type(JsonFieldType.STRING).description("PR 작성자 GitHub ID"),
                                fieldWithPath("pendingReviewers").type(JsonFieldType.ARRAY).description("리뷰 대기 중인 리뷰어 GitHub ID 목록").optional(),
                                fieldWithPath("reviewedReviewers").type(JsonFieldType.ARRAY).description("리뷰 완료한 리뷰어 GitHub ID 목록").optional()
                        )
                )
        );
    }

    @Test
    void 필수_필드가_누락되면_400을_반환한다() throws Exception {
        // given
        String invalidBody = """
                {
                    "repositoryName": "my-repo"
                }
                """;

        // when & then
        mockMvc.perform(
                post("/events/review-request")
                        .header("X-API-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody)
        ).andExpect(status().isBadRequest());
    }

    @Test
    void 리뷰_채널을_찾을_수_없으면_예외_응답을_반환한다() throws Exception {
        // given
        ReviewRequestEventRequest body = defaultBody();

        willThrow(new ReviewChannelResolveException("채널 정보가 없습니다."))
                .given(reviewEventBatch)
                .buffer(any(String.class), any(ReviewRequestEventRequest.class));

        // when & then
        mockMvc.perform(
                        post("/events/review-request")
                                .header("X-API-Key", "test-api-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("R00"))
                .andExpect(jsonPath("$.message").value("리뷰 채널 정보를 찾을 수 없습니다."));
    }

    @Test
    void 슬랙_API_예외가_발생하면_서버_에러_응답을_반환한다() throws Exception {
        // given
        ReviewRequestEventRequest body = defaultBody();

        willThrow(new ReviewSlackApiException("Slack API 요청 실패"))
                .given(reviewEventBatch)
                .buffer(any(String.class), any(ReviewRequestEventRequest.class));

        // when & then
        mockMvc.perform(
                        post("/events/review-request")
                                .header("X-API-Key", "test-api-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body))
                )
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("R03"))
                .andExpect(jsonPath("$.message").value("리뷰 알림 Slack API 요청 실패"));
    }

    private ReviewRequestEventRequest defaultBody() {
        return new ReviewRequestEventRequest(
                "my-repo",
                "PR-1",
                42,
                "Fix bug",
                "https://github.com/pr/1",
                "author-gh",
                List.of("reviewer-gh-1"),
                List.of()
        );
    }
}
