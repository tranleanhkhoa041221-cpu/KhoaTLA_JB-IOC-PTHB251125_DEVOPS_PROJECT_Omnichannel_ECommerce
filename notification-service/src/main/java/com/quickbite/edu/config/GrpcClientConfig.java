package com.quickbite.edu.config;

import com.quickbite.edu.grpc.IdentityServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class GrpcClientConfig {

    @Bean
    public IdentityServiceGrpc.IdentityServiceBlockingStub identityServiceBlockingStub(GrpcChannelFactory channels) {
        return IdentityServiceGrpc.newBlockingStub(channels.createChannel("identity-user-service"));
    }
}
