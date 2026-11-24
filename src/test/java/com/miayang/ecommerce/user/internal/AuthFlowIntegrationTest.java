package com.miayang.ecommerce.user.internal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miayang.ecommerce.TestcontainersConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * End-to-end tests of the auth flow: real Spring app, real security filters,
 * real Postgres (Testcontainers). Each test uses its own email so tests don't interfere.
 */
@SpringBootTest(properties = {
        "app.admin.email=admin@test.local",
        "app.admin.password=AdminPass123"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AuthFlowIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void registerLoginAndReadOwnProfile() throws Exception {
        register("alice@test.local", "Password123", "Alice")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("alice@test.local"))
                .andExpect(jsonPath("$.roles[0]").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        String accessToken = login("alice@test.local", "Password123").get("accessToken").asText();

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@test.local"))
                .andExpect(jsonPath("$.fullName").value("Alice"));
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        register("bob@test.local", "Password123", "Bob").andExpect(status().isCreated());

        register("BOB@test.local", "Password123", "Bob again")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email is already registered"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        register("carol@test.local", "Password123", "Carol").andExpect(status().isCreated());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", "carol@test.local", "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() throws Exception {
        register("not-an-email", "short", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.fullName").exists());
    }

    @Test
    void profileRequiresAToken() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointRejectsNormalUser() throws Exception {
        register("dave@test.local", "Password123", "Dave").andExpect(status().isCreated());
        String accessToken = login("dave@test.local", "Password123").get("accessToken").asText();

        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpointAllowsAdmin() throws Exception {
        String accessToken = login("admin@test.local", "AdminPass123").get("accessToken").asText();

        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                // The admin is created at startup, so it has the lowest id and is listed first.
                .andExpect(jsonPath("$[0].email").value("admin@test.local"))
                .andExpect(jsonPath("$[0].roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$[0].roles[1]").value("USER"));
    }

    @Test
    void refreshRotatesTokensAndDetectsReuse() throws Exception {
        register("erin@test.local", "Password123", "Erin").andExpect(status().isCreated());
        String firstRefresh = login("erin@test.local", "Password123").get("refreshToken").asText();

        // 1st use: works and returns a NEW refresh token
        String body = refresh(firstRefresh).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String secondRefresh = json.readTree(body).get("refreshToken").asText();

        // Reusing the old one is rejected...
        refresh(firstRefresh).andExpect(status().isUnauthorized());
        // ...and because reuse looks like theft, the new one was revoked too.
        refresh(secondRefresh).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        register("frank@test.local", "Password123", "Frank").andExpect(status().isCreated());
        String refreshToken = login("frank@test.local", "Password123").get("refreshToken").asText();

        mvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isNoContent());

        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    private ResultActions register(String email, String password, String fullName) throws Exception {
        return mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "email", email, "password", password, "fullName", fullName))));
    }

    private JsonNode login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", refreshToken))));
    }
}
