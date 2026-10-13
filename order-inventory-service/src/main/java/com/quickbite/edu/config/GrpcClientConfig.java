package com.quickbite.edu.config;

import com.quickbite.edu.grpc.IdentityServiceGrpc;
import com.quickbite.edu.grpc.ProductServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kenh gRPC tao TRUC TIEP tu host/port lay tu bien moi truong (IDENTITY_GRPC_HOST/PORT, PRODUCT_GRPC_HOST/PORT).
 * Khong phu thuoc ten property cua Spring gRPC: tren production kenh tao qua GrpcChannelFactory khong nhan
 * cau hinh address va roi ve "identity-user-service:80" (Connection refused). Host trong Docker = ten service.
 */
@Configuration
public class GrpcClientConfig {

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel productChannel(@Value("${PRODUCT_GRPC_HOST:localhost}") String host,
                                         @Value("${PRODUCT_GRPC_PORT:9091}") int port) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        channel.getState(true); // chu dong mo ket noi ngay luc khoi dong -> cuoc goi gRPC dau tien khong phai cho handshake (khoi bi timeout 3s)
        return channel;
    }

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel identityChannel(@Value("${IDENTITY_GRPC_HOST:localhost}") String host,
                                          @Value("${IDENTITY_GRPC_PORT:9090}") int port) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        channel.getState(true); // chu dong mo ket noi ngay luc khoi dong -> cuoc goi gRPC dau tien khong phai cho handshake (khoi bi timeout 3s)
        return channel;
    }

    @Bean
    public ProductServiceGrpc.ProductServiceBlockingStub productServiceBlockingStub(
            @Qualifier("productChannel") ManagedChannel channel) {
        return ProductServiceGrpc.newBlockingStub(channel);
    }

    @Bean
    public IdentityServiceGrpc.IdentityServiceBlockingStub identityServiceBlockingStub(
            @Qualifier("identityChannel") ManagedChannel channel) {
        return IdentityServiceGrpc.newBlockingStub(channel);
    }
}
