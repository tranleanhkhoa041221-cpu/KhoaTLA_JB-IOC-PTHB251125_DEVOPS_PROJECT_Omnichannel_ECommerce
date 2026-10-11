package com.quickbite.edu.grpc;

import com.quickbite.edu.dto.response.CheckStockResult;
import com.quickbite.edu.dto.response.ProductVariantResponse;
import com.quickbite.edu.exception.BadRequestException;
import com.quickbite.edu.exception.NotFoundException;
import com.quickbite.edu.service.interfaces.ProductService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class ProductGrpcService extends ProductServiceGrpc.ProductServiceImplBase {

    private final ProductService productService;

    @Override
    public void getProductById(ProductByIdRequest request, StreamObserver<ProductResponse> responseObserver) {
        try {
            var product = productService.getById(request.getId());

            ProductResponse response = ProductResponse.newBuilder()
                    .setId(product.getId())
                    .setName(product.getName())
                    .setDescription(product.getDescription() != null ? product.getDescription() : "")
                    .setBasePrice(product.getBasePrice().doubleValue())
                    .setStatus(product.getStatus().name())
                    .setCategoryId(product.getCategoryId() != null ? product.getCategoryId() : 0)
                    .setBrandId(product.getBrandId() != null ? product.getBrandId() : 0)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (NotFoundException ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (Exception ex) {
            log.error("gRPC getProductById failed", ex);
            responseObserver.onError(Status.INTERNAL
                    .withDescription(ex.getClass().getSimpleName() + ": " + ex.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getVariantBySku(VariantBySkuRequest request, StreamObserver<VariantResponse> responseObserver) {
        try {
            ProductVariantResponse variant = productService.getVariantBySku(request.getSku());

            VariantResponse response = VariantResponse.newBuilder()
                    .setId(variant.getId())
                    .setProductId(variant.getProductId())
                    .setProductName(variant.getProductName() != null ? variant.getProductName() : "")
                    .setSku(variant.getSku())
                    .setPrice(variant.getPrice().doubleValue())
                    .setStockQuantity(variant.getStockQuantity())
                    .setAttributes(variant.getAttributes() != null ? variant.getAttributes() : "")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (NotFoundException ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (Exception ex) {
            log.error("gRPC getVariantBySku failed", ex);
            responseObserver.onError(Status.INTERNAL
                    .withDescription(ex.getClass().getSimpleName() + ": " + ex.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void checkStockAndPrice(CheckStockRequest request, StreamObserver<CheckStockResponse> responseObserver) {
        try {
            CheckStockResult result = productService.checkStockAndPrice(request.getSku(), request.getQuantity());

            CheckStockResponse response = CheckStockResponse.newBuilder()
                    .setAvailable(result.isAvailable())
                    .setPrice(result.getPrice().doubleValue())
                    .setProductName(result.getProductName())
                    .setMessage(result.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (BadRequestException ex) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(ex.getMessage()).asRuntimeException());
        } catch (NotFoundException ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (Exception ex) {
            log.error("gRPC checkStockAndPrice failed", ex);
            responseObserver.onError(Status.INTERNAL
                    .withDescription(ex.getClass().getSimpleName() + ": " + ex.getMessage())
                    .asRuntimeException());
        }
    }
}
