package com.slack.bot.application.review.box;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

@Component
public class ReviewNotificationIdempotencyKeyGenerator {

    public String generate(ReviewNotificationIdempotencyScope scope, String payloadJson) {
        try {
            String source = scopeValue(scope) + ":" + nullToEmpty(payloadJson);
            byte[] digest = messageDigest().digest(source.getBytes(StandardCharsets.UTF_8));

            return toHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    MessageDigest messageDigest() throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256");
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

    private String scopeValue(ReviewNotificationIdempotencyScope scope) {
        if (scope == null) {
            return "";
        }

        return scope.getValue();
    }
}
