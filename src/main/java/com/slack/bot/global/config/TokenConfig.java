package com.slack.bot.global.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEDecrypter;
import com.nimbusds.jose.JWEEncrypter;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.crypto.AESDecrypter;
import com.nimbusds.jose.crypto.AESEncrypter;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.slack.bot.domain.auth.TokenDecoder;
import com.slack.bot.global.config.properties.TokenProperties;
import com.slack.bot.infrastructure.auth.jwt.JwsSignerFinder;
import com.slack.bot.infrastructure.auth.jwt.JwsVerifierFinder;
import com.slack.bot.infrastructure.auth.jwt.JwtDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(TokenProperties.class)
public class TokenConfig {

    private static final int KEY_LENGTH = 32;
    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final String AES = "AES";

    private final Clock clock;
    private final TokenProperties tokenProperties;

    @Bean
    public TokenDecoder tokenDecoder(JWEDecrypter jweDecrypter, JwsVerifierFinder jwsVerifierFinder) {
        return new JwtDecoder(clock, jweDecrypter, jwsVerifierFinder, tokenProperties);
    }

    @Bean
    public JwsVerifierFinder jwsVerifierFinder(
            SecretKey accessTokenSecretKey,
            SecretKey refreshTokenSecretKey
    ) throws JOSEException {
        JWSVerifier accessTokenJwsVerifier = new MACVerifier(accessTokenSecretKey);
        JWSVerifier refreshTokenJwsVerifier = new MACVerifier(refreshTokenSecretKey);

        return new JwsVerifierFinder(accessTokenJwsVerifier, refreshTokenJwsVerifier);
    }

    @Bean
    public JwsSignerFinder jwsSignerFinder(
            SecretKey accessTokenSecretKey,
            SecretKey refreshTokenSecretKey
    ) throws KeyLengthException {
        MACSigner accessTokenSigner = new MACSigner(accessTokenSecretKey);
        MACSigner refreshTokenSigner = new MACSigner(refreshTokenSecretKey);

        return new JwsSignerFinder(accessTokenSigner, refreshTokenSigner);
    }

    @Bean
    public SecretKey accessTokenSecretKey() {
        byte[] accessTokenKeyBytes = tokenProperties.accessKey().getBytes(StandardCharsets.UTF_8);

        return new SecretKeySpec(accessTokenKeyBytes, HMAC_SHA_256);
    }

    @Bean
    public SecretKey refreshTokenSecretKey() {
        byte[] refreshTokenKeyBytes = tokenProperties.refreshKey().getBytes(StandardCharsets.UTF_8);

        return new SecretKeySpec(refreshTokenKeyBytes, HMAC_SHA_256);
    }

    @Bean
    public JWEDecrypter jweDecrypter(SecretKey aesSecretKey) throws KeyLengthException {
        return new AESDecrypter(aesSecretKey);
    }

    @Bean
    public JWEEncrypter jweEncrypter(SecretKey aesSecretKey) throws KeyLengthException {
        return new AESEncrypter(aesSecretKey);
    }

    @Bean
    public SecretKeySpec aesSecretKey() {
        byte[] keyBytes = decodeKey();

        if (keyBytes.length != KEY_LENGTH) {
            throw new IllegalStateException("암호화 키 길이가 올바르지 않습니다.");
        }

        return new SecretKeySpec(keyBytes, AES);
    }

    private byte[] decodeKey() {
        String encryptionKey = tokenProperties.encryptionKey();
        byte[] utf8Bytes = encryptionKey.getBytes(StandardCharsets.UTF_8);

        if (utf8Bytes.length == KEY_LENGTH) {
            return utf8Bytes;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(encryptionKey);

            if (decoded.length == KEY_LENGTH) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // Base64 형식이 아닌 경우 무시하고, 아래에서 길이 불일치 예외를 던지도록 진행
        }

        throw new IllegalStateException("암호화 키 길이가 올바르지 않습니다.");
    }
}
