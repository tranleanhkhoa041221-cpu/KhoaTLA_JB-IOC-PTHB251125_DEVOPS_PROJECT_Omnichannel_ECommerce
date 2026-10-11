package com.quickbite.edu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate don gian dung de goi cac API server-to-server cua VNPay
 * (querydr/refund). Khong dung WebClient/WebFlux de tranh phai them
 * dependency spring-boot-starter-webflux khong can thiet cho 1 service
 * thuan Servlet (MVC).
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(15_000);
        return new RestTemplate(factory);
    }
}
