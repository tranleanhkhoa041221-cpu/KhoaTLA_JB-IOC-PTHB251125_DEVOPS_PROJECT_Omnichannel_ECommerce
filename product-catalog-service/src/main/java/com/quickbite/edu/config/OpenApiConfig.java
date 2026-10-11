package com.quickbite.edu.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI productCatalogOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Product Catalog Service API")
                        .description("Quan ly san pham, danh muc, thuong hieu, SKU. gRPC phuc vu Order Service (gia + trang thai ban).")
                        .version("1.0"));
    }
}
