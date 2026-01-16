package com.slack.bot.docs.snippet.exceptions;

import static com.slack.bot.docs.RestDocsConfiguration.field;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

import com.slack.bot.docs.CustomResponseFieldsSnippet;
import com.slack.bot.docs.snippet.dto.response.CommonDocsResponse;
import com.slack.bot.presentation.CommonControllerSliceTestSupport;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.PayloadSubsectionExtractor;
import org.springframework.test.web.servlet.MvcResult;

public abstract class CommonExceptionControllerTest extends CommonControllerSliceTestSupport {

    protected CustomResponseFieldsSnippet customResponseFields(
            String type,
            PayloadSubsectionExtractor<?> subsectionExtractor,
            Map<String, Object> attributes,
            FieldDescriptor... descriptors
    ) {
        return new CustomResponseFieldsSnippet(
                type,
                subsectionExtractor,
                Arrays.asList(descriptors),
                attributes,
                true
        );
    }

    protected FieldDescriptor[] exceptionConvertFieldDescriptor(Map<String, ExceptionContent> exceptionValues) {
        return exceptionValues.entrySet()
                              .stream()
                              .map(
                                      exceptionValue -> fieldWithPath(exceptionValue.getKey()).description(exceptionValue.getValue().httpStatus().name())
                                                                                              .attributes(
                                                                                                      field("status", String.valueOf(exceptionValue.getValue().httpStatus().value())),
                                                                                                      field("message", exceptionValue.getValue().message())
                                                                                              )
                              ).toArray(FieldDescriptor[]::new);
    }

    protected <T> T findExceptionData(MvcResult result, Class<T> responseType) throws IOException {
        CommonDocsResponse<T> apiResponseDto = objectMapper.readValue(
                result.getResponse().getContentAsByteArray(),
                objectMapper.getTypeFactory().constructParametricType(CommonDocsResponse.class, responseType)
        );

        return apiResponseDto.data();
    }
}
