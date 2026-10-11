package com.quickbite.edu.security;

import com.quickbite.edu.config.GatewaySecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

@Component
@RequiredArgsConstructor
public class PublicEndpointMatcher {

    private final GatewaySecurityProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public boolean isPublic(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        return properties.getPublicEndpoints().stream().anyMatch(endpoint -> {
            boolean methodMatches = "ANY".equalsIgnoreCase(endpoint.getMethod())
                    || (method != null && method.name().equalsIgnoreCase(endpoint.getMethod()));
            return methodMatches && pathMatcher.match(endpoint.getPattern(), path);
        });
    }
}
