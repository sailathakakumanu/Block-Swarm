package com.blockstore.config;

import com.blockstore.model.User;
import com.blockstore.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Auto-creates the admin account on startup if it doesn't already exist.
 */
@Configuration
public class AdminInitializer {

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.password}")
    private String adminPassword;

    @Bean
    public CommandLineRunner initAdmin(UserRepository userRepository) {
        return args -> {
            if (!userRepository.existsByUsername(adminUsername)) {
                BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
                User admin = new User(adminUsername, encoder.encode(adminPassword), "ADMIN");
                userRepository.save(admin);
                System.out.println("🔑 Admin account created: " + adminUsername);
            } else {
                System.out.println("🔑 Admin account already exists: " + adminUsername);
            }
        };
    }
}
