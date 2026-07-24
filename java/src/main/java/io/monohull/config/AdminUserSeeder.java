package io.monohull.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import io.monohull.entity.AppUserEntity;
import io.monohull.repository.AppUserRepository;

import java.util.Set;

/**
 * Seeds the initial admin login on first boot (when {@code app_user} is empty),
 * using {@code MONOHULL_ADMIN_USERNAME} / {@code MONOHULL_ADMIN_PASSWORD}. If no password
 * is configured a weak default is used and a loud warning is logged; seeding a
 * well-known password (e.g. the dev compose's 'admin' fallback) also warns.
 */
@Component
public class AdminUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);
    private static final String DEFAULT_PASSWORD = "changeme";
    private static final Set<String> WELL_KNOWN_PASSWORDS =
            Set.of("admin", "changeme", "password", "monohull");

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public AdminUserSeeder(AppUserRepository users,
                           PasswordEncoder passwordEncoder,
                           @Value("${monohull.admin.username:admin}") String adminUsername,
                           @Value("${monohull.admin.password:}") String adminPassword) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }

        String password = adminPassword;
        if (password == null || password.isBlank()) {
            password = DEFAULT_PASSWORD;
            log.warn("=================================================================");
            log.warn("MONOHULL_ADMIN_PASSWORD is not set. Seeding admin user '{}' with the",
                    adminUsername);
            log.warn("default password '{}'. CHANGE THIS IMMEDIATELY.", DEFAULT_PASSWORD);
            log.warn("=================================================================");
        } else if (WELL_KNOWN_PASSWORDS.contains(password)) {
            log.warn("=================================================================");
            log.warn("Seeding admin user '{}' with the well-known password '{}'.",
                    adminUsername, password);
            log.warn("The dev docker-compose.yml falls back to 'admin' when");
            log.warn("MONOHULL_ADMIN_PASSWORD is unset. Fine for a throwaway dev");
            log.warn("instance — change it for anything reachable by others.");
            log.warn("=================================================================");
        }

        AppUserEntity admin = new AppUserEntity();
        admin.setUsername(adminUsername);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole("ADMIN");
        admin.setEnabled(true);
        users.save(admin);

        log.info("Seeded initial admin login '{}'.", adminUsername);
    }
}
