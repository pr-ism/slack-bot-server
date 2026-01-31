package com.slack.bot.global.config;

import com.slack.bot.global.config.properties.CorsProperties;
import com.slack.bot.global.resolver.ProjectMemberIdArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(CorsProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;
    private final ProjectMemberIdArgumentResolver projectMemberIdArgumentResolver;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        CorsRegistration registration = registry.addMapping("/**")
                                                .allowedMethods(corsProperties.allowedMethods().toArray(new String[0]))
                                                .allowedHeaders(corsProperties.allowedHeaders().toArray(new String[0]))
                                                .maxAge(corsProperties.maxAge())
                                                .allowCredentials(true);

        if (corsProperties.hasOriginPatterns()) {
            registration.allowedOriginPatterns(corsProperties.allowedOriginPatterns().toArray(new String[0]));
        } else {
            registration.allowedOrigins(corsProperties.allowedOrigins().toArray(new String[0]));
        }

        if (corsProperties.hasExposedHeaders()) {
            registration.exposedHeaders(corsProperties.exposedHeaders().toArray(new String[0]));
        }
    }

    @Override
    public void addArgumentResolvers(java.util.List<org.springframework.web.method.support.HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(projectMemberIdArgumentResolver);
    }
}
