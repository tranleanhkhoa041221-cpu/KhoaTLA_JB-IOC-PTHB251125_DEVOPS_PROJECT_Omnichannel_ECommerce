package com.quickbite.edu.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI serviceOpenApi() {
        return new OpenAPI().info(new Info().title("Order & Inventory Service API").description("Don hang, ton kho, Saga, Outbox.").version("1.0"));
    }
}
