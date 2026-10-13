package com.quickbite.edu.config;

import com.quickbite.edu.grpc.IdentityServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kenh gRPC toi Identity tao truc tiep tu IDENTITY_GRPC_HOST/PORT (khong phu thuoc ten property Spring gRPC).
 */
@Configuration
public class GrpcClientConfig {

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel identityChannel(@Value("${IDENTITY_GRPC_HOST:localhost}") String host,
                                          @Value("${IDENTITY_GRPC_PORT:9090}") int port) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        channel.getState(true); // chu dong mo ket noi ngay luc khoi dong -> cuoc goi gRPC dau tien khong phai cho handshake (khoi bi timeout 3s)
        return channel;
    }

    @Bean
    public IdentityServiceGrpc.IdentityServiceBlockingStub identityServiceBlockingStub(
            @Qualifier("identityChannel") ManagedChannel channel) {
        return IdentityServiceGrpc.newBlockingStub(channel);
    }
}