package io.monohull.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Binds the shipped {@code application.yml} the way Spring Boot does at startup (no web
 * server, JPA or Docker beans) and checks the session timeout the servlet container
 * will be given.
 *
 * <p>Regression pin for the frequent-logout bug: with no {@code server.servlet.session.timeout}
 * Spring Boot falls back to 30 minutes, and an idle page makes no requests, so a tab
 * left open was signed out after half an hour.
 */
class SessionTimeoutConfigTest {

    @Test
    void sessionsLastEightHoursByDefault() throws IOException {
        assertEquals(Duration.ofHours(8), sessionTimeout(Map.of()));
    }

    @Test
    void environmentVariableOverridesTheTimeout() throws IOException {
        assertEquals(Duration.ofMinutes(45), sessionTimeout(Map.of("MONOHULL_SESSION_TIMEOUT", "45m")));
    }

    @Test
    void bareNumberIsSeconds() throws IOException {
        assertEquals(Duration.ofHours(1), sessionTimeout(Map.of("MONOHULL_SESSION_TIMEOUT", "3600")));
    }

    private static Duration sessionTimeout(Map<String, Object> variables) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        // Only the given variables: the machine's own environment must not leak in.
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("variables", variables));
        for (PropertySource<?> source : new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addLast(source);
        }
        ServerProperties server = Binder.get(environment)
            .bind("server", ServerProperties.class)
            .orElseThrow(() -> new AssertionError("application.yml has no server section"));
        return server.getServlet().getSession().getTimeout();
    }
}
