package com.miayang.ecommerce.user.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first admin account at startup, if app.admin.email and app.admin.password
 * are set (Docker Compose sets them for local use). Does nothing if they're empty or the
 * account already exists. Normal sign-ups can never become admin.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrap(
            UserRepository users,
            RoleRepository roles,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.email:}") String adminEmail,
            @Value("${app.admin.password:}") String adminPassword) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.info("Admin bootstrap skipped: app.admin.email / app.admin.password not set");
            return;
        }
        String email = AuthService.normalizeEmail(adminEmail);
        if (users.existsByEmailIgnoreCase(email)) {
            return;
        }

        User admin = new User(email, passwordEncoder.encode(adminPassword), "Administrator");
        admin.addRole(roles.findByName(RoleName.USER).orElseThrow());
        admin.addRole(roles.findByName(RoleName.ADMIN).orElseThrow());
        users.save(admin);
        log.info("Created bootstrap admin account {}", email);
    }
}
