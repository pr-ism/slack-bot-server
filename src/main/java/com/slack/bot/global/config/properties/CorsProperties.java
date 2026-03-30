package com.slack.bot.global.config.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cors")
public record CorsProperties(
        List<String> allowedOrigins,
        List<String> allowedOriginPatterns,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        List<String> exposedHeaders,
        Long maxAge
) {

    public CorsProperties {
        if (allowedOrigins != null && allowedOrigins.contains("*")
                && (allowedOriginPatterns == null || allowedOriginPatterns.isEmpty())) {
            throw new IllegalArgumentException(
                    "Wildcard origins require 'cors.allowed-origin-patterns' to be configured."
            );
        }

        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            allowedOrigins = List.of("http://localhost:3000");
        } else {
            allowedOrigins = List.copyOf(allowedOrigins);
        }

        if (allowedOriginPatterns == null) {
            allowedOriginPatterns = List.of();
        } else {
            allowedOriginPatterns = List.copyOf(allowedOriginPatterns);
        }

        if (allowedMethods == null || allowedMethods.isEmpty()) {
            allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        } else {
            allowedMethods = List.copyOf(allowedMethods);
        }

        if (allowedHeaders == null || allowedHeaders.isEmpty()) {
            allowedHeaders = List.of(
                    "Authorization",
                    "Content-Type",
                    "Accept",
                    "Cache-Control"
            );
        } else {
            allowedHeaders = List.copyOf(allowedHeaders);
        }

        if (exposedHeaders == null) {
            exposedHeaders = List.of();
        } else {
            exposedHeaders = List.copyOf(exposedHeaders);
        }

        if (maxAge == null) {
            maxAge = 3600L;
        }
    }

    public boolean hasOriginPatterns() {
        return !allowedOriginPatterns.isEmpty();
    }

    public boolean hasExposedHeaders() {
        return !exposedHeaders.isEmpty();
    }

    public boolean hasWildcardOrigins() {
        return allowedOrigins.contains("*");
    }
}
