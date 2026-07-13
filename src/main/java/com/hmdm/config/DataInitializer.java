package com.hmdm.config;

import com.hmdm.entity.Customer;
import com.hmdm.entity.User;
import com.hmdm.repository.CustomerRepository;
import com.hmdm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Runs on every startup — creates the default customer and admin user.
 * Default credentials:  Username=Sravan  Password=Sravan@123
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository     userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder    passwordEncoder;

    // ── Default admin credentials ──────────────────────────────
    private static final String DEFAULT_LOGIN    = "Sravan";
    private static final String DEFAULT_PASSWORD = "Sravan@123";
    private static final String DEFAULT_NAME     = "Sravan Admin";
    private static final String DEFAULT_EMAIL    = "admin@mdm.local";
    private static final String DEFAULT_ROLE     = "SUPER_ADMIN";

    @Override
    public void run(String... args) {

        // 1. Ensure default customer exists (id = 1)
        if (customerRepository.count() == 0) {
            Customer c = Customer.builder()
                    .name("Default")
                    .prefix("default")
                    .build();
            customerRepository.save(c);
            log.info("DataInitializer: created default customer");
        }

        // 2. Ensure admin user Sravan / Sravan@123 exists
        if (!userRepository.existsByLogin(DEFAULT_LOGIN)) {
            User admin = User.builder()
                    .login(DEFAULT_LOGIN)
                    .password(passwordEncoder.encode(DEFAULT_PASSWORD))
                    .name(DEFAULT_NAME)
                    .email(DEFAULT_EMAIL)
                    .role(DEFAULT_ROLE)
                    .customerId(1L)
                    .active(true)
                    .build();
            userRepository.save(admin);
            log.info("DataInitializer: created admin user '{}'", DEFAULT_LOGIN);
        } else {
            // If user exists but password has been lost — always keep it correct
            userRepository.findByLogin(DEFAULT_LOGIN).ifPresent(u -> {
                if (!passwordEncoder.matches(DEFAULT_PASSWORD, u.getPassword())) {
                    u.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
                    u.setRole(DEFAULT_ROLE);
                    u.setActive(true);
                    userRepository.save(u);
                    log.info("DataInitializer: reset password for '{}'", DEFAULT_LOGIN);
                }
            });
        }

        log.info("╔══════════════════════════════════════════╗");
        log.info("║          MDM Backend is READY            ║");
        log.info("║  Admin Username : Sravan                 ║");
        log.info("║  Admin Password : Sravan@123             ║");
        log.info("║  API Base URL   : http://localhost:8080  ║");
        log.info("╚══════════════════════════════════════════╝");
    }
}
