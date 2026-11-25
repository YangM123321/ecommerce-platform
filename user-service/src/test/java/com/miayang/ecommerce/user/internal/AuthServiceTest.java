package com.miayang.ecommerce.user.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.miayang.ecommerce.common.security.JwtProperties;
import com.miayang.ecommerce.common.web.ApiException;
import com.miayang.ecommerce.user.internal.AuthDtos.LoginRequest;
import com.miayang.ecommerce.user.internal.AuthDtos.RegisterRequest;
import com.miayang.ecommerce.user.internal.AuthDtos.TokenResponse;
import com.miayang.ecommerce.user.internal.AuthDtos.UserResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Unit tests: no Spring, no database. Repositories and TokenService are Mockito mocks. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository users;

    @Mock
    RoleRepository roles;

    @Mock
    RefreshTokenRepository refreshTokens;

    @Mock
    TokenService tokenService;

    // Real BCrypt with low cost (4) so the tests stay fast.
    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    JwtProperties jwtProperties = new JwtProperties(
            "x".repeat(32), "test", Duration.ofMinutes(15), Duration.ofDays(7));

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(users, roles, refreshTokens, passwordEncoder, tokenService, jwtProperties);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(users.existsByEmailIgnoreCase("alice@test.local")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Alice@Test.local", "Password123", "Alice")))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);

        verify(users, never()).save(any());
    }

    @Test
    void registerHashesPasswordAndAssignsUserRole() {
        when(users.existsByEmailIgnoreCase("alice@test.local")).thenReturn(false);
        when(roles.findByName(RoleName.USER)).thenReturn(Optional.of(new Role(RoleName.USER)));
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = authService.register(
                new RegisterRequest("Alice@Test.local", "Password123", "  Alice  "));

        assertThat(response.email()).isEqualTo("alice@test.local");
        assertThat(response.fullName()).isEqualTo("Alice");
        assertThat(response.roles()).containsExactly("USER");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("Password123");
        assertThat(passwordEncoder.matches("Password123", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = new User("bob@test.local", passwordEncoder.encode("Password123"), "Bob");
        when(users.findByEmailIgnoreCase("bob@test.local")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("bob@test.local", "wrong-password")))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(tokenService);
    }

    @Test
    void loginReturnsTokensAndStoresOnlyTheRefreshTokenHash() {
        User user = new User("carol@test.local", passwordEncoder.encode("Password123"), "Carol");
        when(users.findByEmailIgnoreCase("carol@test.local")).thenReturn(Optional.of(user));
        when(tokenService.createAccessToken(user)).thenReturn("access-jwt");
        when(tokenService.newRefreshToken()).thenReturn("raw-refresh-token");

        TokenResponse tokens = authService.login(new LoginRequest("carol@test.local", "Password123"));

        assertThat(tokens.accessToken()).isEqualTo("access-jwt");
        assertThat(tokens.refreshToken()).isEqualTo("raw-refresh-token");
        assertThat(tokens.tokenType()).isEqualTo("Bearer");
        assertThat(tokens.expiresIn()).isEqualTo(900);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(saved.capture());
        assertThat(saved.getValue().getTokenHash())
                .isEqualTo(TokenService.sha256("raw-refresh-token"))
                .isNotEqualTo("raw-refresh-token");
    }

    @Test
    void reusingARevokedRefreshTokenRevokesAllSessions() {
        User user = new User("dave@test.local", "hash", "Dave");
        RefreshToken alreadyUsed = new RefreshToken(
                TokenService.sha256("old-token"), user, Instant.now().plusSeconds(3600));
        alreadyUsed.revoke();
        when(refreshTokens.findByTokenHash(TokenService.sha256("old-token"))).thenReturn(Optional.of(alreadyUsed));

        assertThatThrownBy(() -> authService.refresh("old-token"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.UNAUTHORIZED);

        verify(refreshTokens).revokeAllForUser(any());
        verifyNoInteractions(tokenService);
    }
}
