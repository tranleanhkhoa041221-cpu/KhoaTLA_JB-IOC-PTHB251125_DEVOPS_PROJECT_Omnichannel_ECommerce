package com.quickbite.edu.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI serviceOpenApi() {
        return new OpenAPI().info(new Info().title("Identity & User Service API").description("Tai khoan, JWT, dia chi, phan quyen RBAC.").version("1.0"));
    }
}
