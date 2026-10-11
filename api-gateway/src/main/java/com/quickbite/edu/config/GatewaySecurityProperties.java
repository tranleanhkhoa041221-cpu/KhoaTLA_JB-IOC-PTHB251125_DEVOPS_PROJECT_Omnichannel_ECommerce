package com.quickbite.edu.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Danh sach endpoint duoc phep di qua ma KHONG can Bearer token (binding tu gateway.security.public-endpoints). */
@Component
@ConfigurationProperties(prefix = "gateway.security")
@Getter
@Setter
public class GatewaySecurityProperties {

    private List<PublicEndpoint> publicEndpoints = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PublicEndpoint {
        /** ANY, GET, POST, PUT, DELETE... */
        private String method = "ANY";
        /** Ant-style path pattern, vd "/api/users/login" hoac "/api/catalog/**". */
        private String pattern;
    }
}
