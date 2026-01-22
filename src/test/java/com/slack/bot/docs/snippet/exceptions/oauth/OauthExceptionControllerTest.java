package com.slack.bot.docs.snippet.exceptions.oauth;

import static org.springframework.restdocs.payload.PayloadDocumentation.beneathPath;
import static org.springframework.restdocs.snippet.Attributes.attributes;
import static org.springframework.restdocs.snippet.Attributes.key;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.slack.bot.docs.snippet.exceptions.CommonExceptionControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class OauthExceptionControllerTest extends CommonExceptionControllerTest {

    @Test
    void exceptions() throws Exception {
        ResultActions resultActions = mockMvc.perform(
                get("/test/oauth/exceptions").contentType(MediaType.APPLICATION_JSON)
        );
        MvcResult mvcResult = resultActions.andReturn();
        OauthExceptionDocs data = findExceptionData(mvcResult, OauthExceptionDocs.class);

        resultActions.andExpect(status().isOk())
                .andDo(
                        restDocs.document(
                                customResponseFields(
                                        "exception-response",
                                        beneathPath("data.installException").withSubsectionId("installException"),
                                        attributes(key("title").value("`GET /install` 예외")),
                                        exceptionConvertFieldDescriptor(data.installException())
                                ),
                                customResponseFields(
                                        "exception-response",
                                        beneathPath("data.callbackException").withSubsectionId("callbackException"),
                                        attributes(key("title").value("`GET /callback` 예외")),
                                        exceptionConvertFieldDescriptor(data.callbackException())
                                )
                        )
                );
    }
}
