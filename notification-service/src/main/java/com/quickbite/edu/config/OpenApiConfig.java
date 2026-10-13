package com.quickbite.edu.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${GATEWAY_BASE_URL:http://localhost:8080}")
    private String gatewayBaseUrl;

    @Bean
    public OpenAPI notificationOpenApi() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Dan accessToken tu POST /api/users/login (KHONG go chu 'Bearer', Swagger tu them).")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .info(new Info()
                        .title("Notification Service API")
                        .description("Tra cuu nhat ky thong bao (Email/SMS/Push) da gui tu Kafka events")
                        .version("1.0"))
                .servers(List.of(
                        new Server()
                                .url(gatewayBaseUrl)
                                .description("API Gateway (" + gatewayBaseUrl + ")"),
                        new Server()
                                .url("http://localhost:8086")
                                .description("Direct — Notification Service (SSH tunnel / local)")
                ));
    }
}
