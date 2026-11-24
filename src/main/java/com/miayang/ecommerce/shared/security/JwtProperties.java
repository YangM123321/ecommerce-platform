package com.miayang.ecommerce.shared.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings from application.yml (prefix "app.jwt").
 *
 * @param secret          HMAC signing key, at least 32 bytes (256 bits) for HS256
 * @param issuer          value of the "iss" claim; tokens from any other issuer are rejected
 * @param accessTokenTtl  lifetime of access tokens (short, e.g. 15m)
 * @param refreshTokenTtl lifetime of refresh tokens (long, e.g. 7d)
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl) {
}
