package io.monohull.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test of the real {@link SecurityConfig} filter chain (no JPA/Docker beans):
 * proves the static bearer key works for the FULL API, mutations included.
 *
 * <p>Regression pin for the silent-read-only bug: {@code ApiKeyAuthFilter} runs after
 * Spring's CsrfFilter, so before bearer requests were exempted from CSRF every
 * bearer-authenticated POST/PUT/DELETE died with 403 — the key authenticated but could
 * never mutate. Session-cookie callers must still present a CSRF token.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ApiKeyFullAccessTest.TestApp.class)
@WebAppConfiguration
@TestPropertySource(properties = "monohull.api.key=test-service-key")
class ApiKeyFullAccessTest {

    @EnableWebMvc
    @Configuration
    @Import({SecurityConfig.class, ApiKeyFullAccessTest.ProbeController.class})
    static class TestApp {
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/probe")
        public String read() {
            return "ok";
        }

        @PostMapping("/api/probe")
        public String mutate() {
            return "ok";
        }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();
    }

    @Test
    void bearerKeyReads() throws Exception {
        mvc.perform(get("/api/probe").header("Authorization", "Bearer test-service-key"))
            .andExpect(status().isOk());
    }

    @Test
    void bearerKeyMutatesWithoutCsrfToken() throws Exception {
        mvc.perform(post("/api/probe").header("Authorization", "Bearer test-service-key"))
            .andExpect(status().isOk());
    }

    @Test
    void wrongBearerKeyIsUnauthorized() throws Exception {
        mvc.perform(post("/api/probe").header("Authorization", "Bearer wrong-key"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void missingCredentialsAreUnauthorized() throws Exception {
        mvc.perform(get("/api/probe"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void sessionMutationStillNeedsCsrfToken() throws Exception {
        // A logged-in browser session without the X-XSRF-TOKEN header must keep
        // failing: the bearer exemption must not have loosened cookie-session CSRF.
        mvc.perform(post("/api/probe").with(user("andrej")))
            .andExpect(status().isForbidden());
    }
}
