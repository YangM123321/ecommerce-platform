package com.miayang.ecommerce.gateway;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The gateway alone, with no services registered. So:
 *   rejected at the edge -> 401 / 403 (the gateway said no)
 *   let through          -> 503 (the gateway said yes, but there's no instance to forward to)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewaySecurityTest {

    static final String SECRET = "test-secret-at-least-32-bytes-long-0123456789";

    @Autowired
    WebTestClient web;

    private static String token(String secret, String... roles) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("ecommerce-platform").issuedAt(now).expiresAt(now.plusSeconds(600))
                .subject("42").claim("roles", List.of(roles)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void noTokenIsRejectedAtTheEdge() {
        web.get().uri("/api/orders").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void forgedTokenIsRejectedAtTheEdge() {
        String forged = token("a-completely-different-secret-0123456789abc", "USER");
        web.get().uri("/api/orders").headers(h -> h.setBearerAuth(forged))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void validTokenIsForwarded() {
        web.get().uri("/api/orders").headers(h -> h.setBearerAuth(token(SECRET, "USER")))
                .exchange().expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void normalUserCannotReachAdminRoutes() {
        web.get().uri("/api/admin/users").headers(h -> h.setBearerAuth(token(SECRET, "USER")))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void browsingProductsNeedsNoToken() {
        web.get().uri("/api/products").exchange().expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void loginNeedsNoToken() {
        web.post().uri("/api/auth/login").exchange().expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
