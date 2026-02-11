package com.slack.bot.global.security;

import com.slack.bot.global.config.properties.SlackProperties;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SlackSignatureVerifier {

    private static final long MAX_TIMESTAMP_DRIFT_SECONDS = 60L * 5L;

    private final String signingSecret;

    public SlackSignatureVerifier(SlackProperties slackProperties) {
        this.signingSecret = slackProperties.signingSecret();
    }

    public boolean verify(String timestamp, String signature, String rawBody) {
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            log.warn("슬랙 시그니처 검증 실패: timestamp 파싱 실패. timestamp={}", timestamp);
            return false;
        }
        long now = System.currentTimeMillis() / 1_000L;

        if (Math.abs(now - ts) > MAX_TIMESTAMP_DRIFT_SECONDS) {
            log.warn("슬랙 시그니처 검증 실패: timestamp 허용 오차 초과. now={}, request={}, drift={}", now, ts, Math.abs(now - ts));
            return false;
        }

        String base = "v0:" + timestamp + ":" + rawBody;
        String computed = "v0=" + hmacSha256(base, signingSecret);
        boolean verified = constantTimeEquals(computed, signature);

        if (!verified) {
            log.warn("슬랙 시그니처 검증 실패: 서명 불일치. requestSignaturePrefix={}", signaturePrefix(signature));
        }

        return verified;
    }

    private String signaturePrefix(String signature) {
        if (signature == null || signature.isBlank()) {
            return "empty";
        }
        int endIndex = Math.min(signature.length(), 12);
        return signature.substring(0, endIndex);
    }

    private String hmacSha256(String data, String secret) {
        Mac mac = createHmacSha256Mac(secret);
        byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
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

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }
}
