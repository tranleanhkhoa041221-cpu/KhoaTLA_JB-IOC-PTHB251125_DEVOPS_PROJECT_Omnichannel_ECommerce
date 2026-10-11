package com.quickbite.edu.config;

import com.quickbite.edu.entity.User;
import com.quickbite.edu.entity.enums.Role;
import com.quickbite.edu.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.default.password}")
    private String defaultAdminPassword;

    @Override
    public void run(String... args) {
        if (!userRepository.existsByRole(Role.ADMIN)) {
            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode(defaultAdminPassword))
                    .email("admin@ecommerce.com")
                    .fullName("System Administrator")
                    .phone("0900000000")
                    .role(Role.ADMIN)
                    .build();

            userRepository.save(admin);
            log.info(">>> Khoi tao tai khoan Quan tri vien (ADMIN) mac dinh.");
        }
    }
}