package com.miayang.ecommerce.user.internal;

import java.time.Instant;
import java.util.Locale;

import com.miayang.ecommerce.shared.security.JwtProperties;
import com.miayang.ecommerce.shared.web.ApiException;
import com.miayang.ecommerce.user.internal.AuthDtos.LoginRequest;
import com.miayang.ecommerce.user.internal.AuthDtos.RegisterRequest;
import com.miayang.ecommerce.user.internal.AuthDtos.TokenResponse;
import com.miayang.ecommerce.user.internal.AuthDtos.UserResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration, login, token refresh and logout. */
@Service
@Transactional
public class AuthService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final JwtProperties jwtProperties;

    public AuthService(
            UserRepository users,
            RoleRepository roles,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            JwtProperties jwtProperties) {
        this.users = users;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.jwtProperties = jwtProperties;
    }

    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }

        User user = new User(email, passwordEncoder.encode(request.password()), request.fullName().trim());
        user.addRole(roles.findByName(RoleName.USER)
                .orElseThrow(() -> new IllegalStateException("Role USER missing - check Flyway migration V2")));
        return UserResponse.from(users.save(user));
    }

    public TokenResponse login(LoginRequest request) {
        // Same message whether the email or the password is wrong, so attackers can't
        // find out which emails are registered.
        User user = users.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .filter(User::isEnabled)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return issueTokens(user);
    }

    /**
     * Refresh-token rotation: each refresh token works once and is replaced by a new one.
     * If an already-used token shows up again, it was probably stolen, so every session
     * of that user is revoked. noRollbackFor keeps that revocation even though we then throw.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse refresh(String rawRefreshToken) {
        RefreshToken token = refreshTokens.findByTokenHash(TokenService.sha256(rawRefreshToken))
                .orElseThrow(AuthService::invalidRefreshToken);

        if (token.isRevoked()) {
            refreshTokens.revokeAllForUser(token.getUser().getId());
            throw invalidRefreshToken();
        }
        if (token.isExpired(Instant.now())) {
            throw invalidRefreshToken();
        }

        token.revoke();
        return issueTokens(token.getUser());
    }

    public void logout(String rawRefreshToken) {
        refreshTokens.findByTokenHash(TokenService.sha256(rawRefreshToken))
                .ifPresent(RefreshToken::revoke);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = tokenService.createAccessToken(user);
        String rawRefreshToken = tokenService.newRefreshToken();
        refreshTokens.save(new RefreshToken(
                TokenService.sha256(rawRefreshToken),
                user,
                Instant.now().plus(jwtProperties.refreshTokenTtl())));
        return new TokenResponse(
                accessToken, rawRefreshToken, "Bearer", jwtProperties.accessTokenTtl().toSeconds());
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException invalidRefreshToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
    }
}
