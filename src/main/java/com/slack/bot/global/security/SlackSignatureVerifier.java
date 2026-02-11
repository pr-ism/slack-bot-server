package com.slack.bot.global.security;

import com.slack.bot.global.config.properties.SlackProperties;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class SlackSignatureVerifier {

    private static final long MAX_TIMESTAMP_DRIFT_SECONDS = 60L * 5L;

    private final String signingSecret;

    public SlackSignatureVerifier(SlackProperties slackProperties) {
        this.signingSecret = slackProperties.signingSecret();
    }

    public boolean verify(String timestamp, String signature, String rawBody) {
        long ts = Long.parseLong(timestamp);
        long now = System.currentTimeMillis() / 1_000L;

        if (Math.abs(now - ts) > MAX_TIMESTAMP_DRIFT_SECONDS) {
            return false;
        }

        String base = "v0:" + timestamp + ":" + rawBody;
        String computed = "v0=" + hmacSha256(base, signingSecret);

        return constantTimeEquals(computed, signature);
    }

    private String hmacSha256(String data, String secret) {
        Mac mac = createHmacSha256Mac(secret);
        byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

        return toHexString(digest);
    }

    private Mac createHmacSha256Mac(String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");

            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 Mac 초기화에 실패했습니다.", e);
        }
    }

    private String toHexString(byte[] digest) {
        StringBuilder hexStringBuilder = new StringBuilder();

        for (byte b : digest) {
            hexStringBuilder.append(String.format("%02x", b));
        }

        return hexStringBuilder.toString();
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }

        int differenceAccumulator = 0;

        for (int i = 0; i < a.length(); i++) {
            differenceAccumulator |= a.charAt(i) ^ b.charAt(i);
        }

        return differenceAccumulator == 0;
    }
}
