package com.slack.bot.application.interactivity.box;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

@Component
public class SlackInteractionIdempotencyKeyGenerator {

    public String generate(SlackInteractionIdempotencyScope scope, String payloadJson) {
        String source = scopeValue(scope) + ":" + nullToEmpty(payloadJson);
        byte[] digest = sha256(source.getBytes(StandardCharsets.UTF_8));

        return toHex(digest);
    }

    private byte[] sha256(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return digest.digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 찾을 수 없습니다.", exception);
        }
    }

    private String toHex(byte[] value) {
        StringBuilder builder = new StringBuilder(value.length * 2);

        for (byte b : value) {
            int unsigned = b & 0xff;
            String hex = Integer.toHexString(unsigned);
            if (hex.length() == 1) {
                builder.append('0');
            }
            builder.append(hex);
        }

        return builder.toString();
    }

    private String nullToEmpty(String value) {
        if (value == null) {
            return "";
        }

        return value;
    }

    private String scopeValue(SlackInteractionIdempotencyScope scope) {
        if (scope == null) {
            return "";
        }

        return scope.getValue();
    }
}
