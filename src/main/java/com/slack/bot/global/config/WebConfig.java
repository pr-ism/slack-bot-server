package com.slack.bot.global.config;

import com.slack.bot.global.config.properties.CorsProperties;
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

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        CorsRegistration registration = registry.addMapping("/**")
                                                .allowedMethods(corsProperties.allowedMethods().toArray(String[]::new))
                                                .allowedHeaders(corsProperties.allowedHeaders().toArray(String[]::new))
                                                .maxAge(corsProperties.maxAge())
                                                .allowCredentials(true);

        if (corsProperties.hasOriginPatterns()) {
            registration.allowedOriginPatterns(corsProperties.allowedOriginPatterns().toArray(String[]::new));
        } else {
            registration.allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new));
        }

        if (corsProperties.hasExposedHeaders()) {
            registration.exposedHeaders(corsProperties.exposedHeaders().toArray(String[]::new));
        }
    }
}
