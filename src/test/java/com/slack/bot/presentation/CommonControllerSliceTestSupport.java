package com.slack.bot.presentation;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.context.ControllerMockInjectionSupport;
import com.slack.bot.context.ResetMockTestExecutionListener;
import com.slack.bot.docs.RestDocsConfiguration;
import com.slack.bot.global.resolver.ProjectMemberIdArgumentResolver;
import com.slack.bot.global.exception.GlobalExceptionHandler;
import com.slack.bot.global.resolver.SlackCommandRequestArgumentResolver;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation;
import org.springframework.restdocs.mockmvc.RestDocumentationResultHandler;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

@Import(RestDocsConfiguration.class)
@ExtendWith(SpringExtension.class)
@ContextConfiguration(initializers = ControllerMockInjectionSupport.class)
@TestExecutionListeners(value = ResetMockTestExecutionListener.class, mergeMode = MergeMode.MERGE_WITH_DEFAULTS)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public abstract class CommonControllerSliceTestSupport {

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected RestDocumentationResultHandler restDocs;

    @Autowired
    protected RestDocumentationContextProvider provider;

    @Autowired
    ApplicationContext applicationContext;

    protected MockMvc mockMvc;

    @BeforeEach
    void beforeEach() {
        StandaloneMockMvcBuilder standaloneMockMvcBuilder = MockMvcBuilders.standaloneSetup(findRestControllers());
        ProjectMemberIdArgumentResolver projectMemberIdArgumentResolver = new ProjectMemberIdArgumentResolver();
        SlackCommandRequestArgumentResolver slackCommandRequestArgumentResolver = new SlackCommandRequestArgumentResolver();

        this.mockMvc = new FixedStandaloneMockMvcBuilder(standaloneMockMvcBuilder)
                .configureMessageConverters()
                .configureArgumentResolvers(projectMemberIdArgumentResolver, slackCommandRequestArgumentResolver)
                .configureInterceptors()
                .configureControllerAdvice()
                .configureRestDocs()
                .configureFilters()
                .build();
    }

    private Object[] findRestControllers() {
        return applicationContext.getBeansWithAnnotation(RestController.class)
                                 .values()
                                 .toArray();
    }

    private class FixedStandaloneMockMvcBuilder {

        StandaloneMockMvcBuilder builder;

        public FixedStandaloneMockMvcBuilder(StandaloneMockMvcBuilder builder) {
            this.builder = builder;
        }

        MockMvc build() {
            return builder.build();
        }

        FixedStandaloneMockMvcBuilder configureMessageConverters() {
            MappingJackson2HttpMessageConverter jacksonMessageConverter =
                    new MappingJackson2HttpMessageConverter(objectMapper);
            ResourceHttpMessageConverter resourceMessageConverter = new ResourceHttpMessageConverter();
            resourceMessageConverter.setSupportedMediaTypes(
                    List.of(
                            MediaType.IMAGE_PNG,
                            MediaType.IMAGE_JPEG,
                            MediaType.IMAGE_GIF
                    )
            );

            builder.setMessageConverters(jacksonMessageConverter, resourceMessageConverter);
            return this;
        }

        FixedStandaloneMockMvcBuilder configureArgumentResolvers(HandlerMethodArgumentResolver... resolvers) {
            builder.setCustomArgumentResolvers(resolvers);
            return this;
        }

        FixedStandaloneMockMvcBuilder configureInterceptors() {
            return this;
        }

        FixedStandaloneMockMvcBuilder configureControllerAdvice() {
            builder.setControllerAdvice(new GlobalExceptionHandler());
            return this;
        }

        FixedStandaloneMockMvcBuilder configureRestDocs() {
            builder.apply(MockMvcRestDocumentation.documentationConfiguration(provider))
                   .alwaysDo(print())
                   .alwaysDo(restDocs);
            return this;
        }

        FixedStandaloneMockMvcBuilder configureFilters() {
            builder.addFilters(new CharacterEncodingFilter("UTF-8", true));
            return this;
        }
    }
}
