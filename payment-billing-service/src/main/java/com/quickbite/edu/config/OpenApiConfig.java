package com.quickbite.edu.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentBillingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Payment & Billing Service API")
                .description("Tich hop cong thanh toan VNPay Sandbox - tao giao dich, webhook IPN, hoan tien, doi soat")
                .version("v1.0")
                .contact(new Contact().name("Omnichannel E-Commerce Platform")));
    }
}
