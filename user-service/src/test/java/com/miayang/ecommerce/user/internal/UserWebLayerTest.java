package com.miayang.ecommerce.user.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.crypto.spec.SecretKeySpec;

import com.miayang.ecommerce.common.security.JwtProperties;
import com.miayang.ecommerce.common.security.JwtSecurityConfig;
import com.miayang.ecommerce.common.web.ApiException;
import com.miayang.ecommerce.user.config.SecurityConfig;
import com.miayang.ecommerce.user.internal.AuthDtos.TokenResponse;
import com.miayang.ecommerce.user.internal.AuthDtos.UserResponse;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer "slice" tests: only controllers, JSON handling, validation, the error handler
 * and the real security rules are loaded. No database - AuthService and UserRepository are mocks.
 * Fast (no Docker), and they check exactly what the HTTP layer is responsible for.
 */
@WebMvcTest(controllers = {AuthController.class, UserController.class, AdminController.class})
@Import({SecurityConfig.class, JwtSecurityConfig.class})
class UserWebLayerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    JwtProperties jwtProperties;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    UserRepository users;

    // ---------- validation and error format ----------

    @Test
    void registerReturns201WithTheCreatedUser() throws Exception {
        when(authService.register(any())).thenReturn(new UserResponse(
                7L, "alice@test.local", "Alice", List.of("USER"), Instant.parse("2026-01-01T00:00:00Z")));

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@test.local","password":"Password123","fullName":"Alice"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    @Test
    void invalidRegisterBodyReturns400AndNeverReachesTheService() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"short","fullName":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").value("must be 8-72 characters"))
                .andExpect(jsonPath("$.errors.fullName").exists());

        verifyNoInteractions(authService);
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed JSON request body"));
    }

    @Test
    void apiExceptionFromServiceBecomesProblemDetail() throws Exception {
        when(authService.register(any()))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "Email is already registered"));

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"bob@test.local","password":"Password123","fullName":"Bob"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Email is already registered"));
    }

    @Test
    void loginIsPublicAndReturnsTokens() throws Exception {
        when(authService.login(any())).thenReturn(new TokenResponse("access", "refresh", "Bearer", 900));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"carol@test.local","password":"Password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    // ---------- authentication: which tokens are accepted ----------

    @Test
    void profileWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void profileWithValidTokenReturns200() throws Exception {
        User alice = new User("alice@test.local", "hash", "Alice");
        alice.addRole(new Role(RoleName.USER));
        when(users.findById(1L)).thenReturn(Optional.of(alice));

        mvc.perform(get("/api/users/me").header("Authorization", bearer(token("1", List.of("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@test.local"));
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        Instant anHourAgo = Instant.now().minusSeconds(3600);
        String expired = sign(jwtEncoder, jwtProperties.issuer(), "1", List.of("USER"), anHourAgo);

        mvc.perform(get("/api/users/me").header("Authorization", bearer(expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherKeyReturns401() throws Exception {
        JwtEncoder attackerEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec("an-attacker-key-that-is-32-bytes!!".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        String forged = sign(attackerEncoder, jwtProperties.issuer(), "1", List.of("ADMIN"),
                Instant.now().plusSeconds(900));

        mvc.perform(get("/api/users/me").header("Authorization", bearer(forged)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFromAnotherIssuerReturns401() throws Exception {
        String otherIssuer = sign(jwtEncoder, "some-other-app", "1", List.of("USER"),
                Instant.now().plusSeconds(900));

        mvc.perform(get("/api/users/me").header("Authorization", bearer(otherIssuer)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- authorization: roles ----------

    @Test
    void adminEndpointWithUserRoleReturns403AndNeverQueriesUsers() throws Exception {
        mvc.perform(get("/api/admin/users").header("Authorization", bearer(token("2", List.of("USER")))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(users);
    }

    @Test
    void adminEndpointWithAdminRoleReturns200() throws Exception {
        mvc.perform(get("/api/admin/users").header("Authorization", bearer(token("1", List.of("ADMIN", "USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ---------- helpers ----------

    /** A valid token signed with the app's real key, like TokenService would create. */
    private String token(String userId, List<String> roles) {
        return sign(jwtEncoder, jwtProperties.issuer(), userId, roles, Instant.now().plusSeconds(900));
    }

    private static String sign(JwtEncoder encoder, String issuer, String userId, List<String> roles, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(expiresAt.minusSeconds(900))
                .expiresAt(expiresAt)
                .subject(userId)
                .claim("roles", roles)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
