package com.slack.bot.infrastructure.auth.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEDecrypter;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.slack.bot.domain.auth.PrivateClaims;
import com.slack.bot.domain.auth.TokenDecoder;
import com.slack.bot.domain.auth.TokenType;
import com.slack.bot.global.config.properties.TokenProperties;
import com.slack.bot.infrastructure.auth.jwt.exception.ExpiredTokenException;
import com.slack.bot.infrastructure.auth.jwt.exception.InvalidTokenException;
import java.text.ParseException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Date;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JwtDecoder implements TokenDecoder {

    private static final String CLAIM_ID = "id";

    private final Clock clock;
    private final JWEDecrypter jweDecrypter;
    private final JwsVerifierFinder jwsVerifierFinder;
    private final TokenProperties tokenProperties;

    @Override
    public PrivateClaims decode(TokenType tokenType, String token) {
        validateToken(token);

        JWTClaimsSet claimsSet = parse(tokenType, token);

        return convert(claimsSet);
    }

    private void validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("토큰이 존재하지 않거나 길이가 부족합니다.");
        }
    }

    private JWTClaimsSet parse(TokenType tokenType, String token) {
        try {
            return extractClaimsSet(tokenType, token);
        } catch (JOSEException e) {
            throw new InvalidTokenException("토큰 디코딩에 실패했습니다", e);
        } catch (ParseException e) {
            throw new InvalidTokenException("유효한 토큰이 아닙니다.", e);
        }
    }

    private JWTClaimsSet extractClaimsSet(
            TokenType tokenType,
            String token
    ) throws ParseException, JOSEException {
        JWTClaimsSet claimsSet = findJWTClaimsSet(tokenType, token);

        validateIssuer(claimsSet);
        validateExpired(claimsSet);

        return claimsSet;
    }

    private void validateExpired(JWTClaimsSet claimsSet) {
        Date expirationTime = claimsSet.getExpirationTime();

        if (expirationTime == null) {
            throw new InvalidTokenException("토큰 만료 시간이 존재하지 않습니다.");
        }
        if (isExpiredToken(expirationTime)) {
            throw new ExpiredTokenException();
        }
    }

    private JWTClaimsSet findJWTClaimsSet(TokenType tokenType, String token) throws ParseException, JOSEException {
        JWEObject jweObject = findJWEObject(token);
        SignedJWT signedJwt = findSignedJWT(jweObject);
        JWSVerifier jwsVerifier = findJWSVerifier(tokenType);

        validateSign(signedJwt, jwsVerifier);

        return signedJwt.getJWTClaimsSet();
    }

    private JWEObject findJWEObject(String token) throws ParseException, JOSEException {
        JWEObject jweObject = JWEObject.parse(token);

        jweObject.decrypt(jweDecrypter);
        return jweObject;
    }

    private SignedJWT findSignedJWT(JWEObject jweObject) {
        SignedJWT signedJWT = jweObject.getPayload()
                                       .toSignedJWT();

        if (signedJWT == null) {
            throw new InvalidTokenException("유효한 JWT 형식이 아닙니다.");
        }

        return signedJWT;
    }

    private JWSVerifier findJWSVerifier(TokenType tokenType) {
        return jwsVerifierFinder.findByTokenType(tokenType);
    }

    private void validateSign(SignedJWT signedJwt, JWSVerifier jwsVerifier) throws JOSEException {
        if (!signedJwt.verify(jwsVerifier)) {
            throw new InvalidTokenException("위변조된 토큰입니다.");
        }
    }

    private boolean isExpiredToken(Date expirationTime) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expirationDate = LocalDateTime.ofInstant(expirationTime.toInstant(), clock.getZone());

        return expirationDate.isBefore(now);
    }

    private void validateIssuer(JWTClaimsSet claimsSet) {
        if (!tokenProperties.issuer().equals(claimsSet.getIssuer())) {
            throw new InvalidTokenException("서비스에서 발급한 토큰이 아닙니다.");
        }
    }

    private PrivateClaims convert(JWTClaimsSet claims) {
        Date issueTime = claims.getIssueTime();

        if (issueTime == null) {
            throw new InvalidTokenException("토큰 발급 시간이 존재하지 않습니다.");
        }
        try {
            Long userId = claims.getLongClaim(CLAIM_ID);

            if (userId == null) {
                throw new InvalidTokenException("사용자 ID가 존재하지 않습니다.");
            }

            return new PrivateClaims(
                    userId,
                    LocalDateTime.ofInstant(issueTime.toInstant(), clock.getZone())
            );
        } catch (ParseException e) {
            throw new InvalidTokenException("유효한 형식의 토큰이 아닙니다.");
        }
    }
}
