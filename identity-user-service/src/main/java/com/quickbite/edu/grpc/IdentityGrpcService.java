package com.quickbite.edu.grpc;

import com.quickbite.edu.dto.response.TokenValidationResponse;
import com.quickbite.edu.entity.Address;
import com.quickbite.edu.entity.User;
import com.quickbite.edu.exception.NotFoundException;
import com.quickbite.edu.repository.AddressRepository;
import com.quickbite.edu.repository.UserRepository;
import com.quickbite.edu.service.interfaces.UserService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
@RequiredArgsConstructor
public class IdentityGrpcService extends IdentityServiceGrpc.IdentityServiceImplBase {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final UserService userService;

    @Override
    public void getUser(UserRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new NotFoundException("User not found: " + request.getUsername()));

            UserResponse response = UserResponse.newBuilder()
                    .setId(user.getId())
                    .setUsername(user.getUsername())
                    .setRole(user.getRole().name())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (NotFoundException ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getDefaultAddress(GetDefaultAddressRequest request, StreamObserver<AddressResponse> responseObserver) {
        try {
            if (request.getUserId() <= 0) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("user_id phải lớn hơn 0")
                        .asRuntimeException());
                return;
            }

            Address address = addressRepository.findByUserIdAndIsDefaultTrue(request.getUserId())
                    .orElseThrow(() -> new NotFoundException(
                            "Không tìm thấy địa chỉ mặc định của user ID: " + request.getUserId()));

            AddressResponse response = AddressResponse.newBuilder()
                    .setId(address.getId())
                    .setUserId(request.getUserId())
                    .setRecipientName(address.getRecipientName())
                    .setPhone(address.getPhone())
                    .setProvince(address.getProvince())
                    .setDistrict(address.getDistrict())
                    .setWard(address.getWard())
                    .setAddressDetail(address.getAddressDetail())
                    .setIsDefault(address.isDefault())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (NotFoundException ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getUserContact(GetUserContactRequest request, StreamObserver<UserContactResponse> responseObserver) {
        try {
            if (request.getUserId() <= 0) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("user_id phải lớn hơn 0")
                        .asRuntimeException());
                return;
            }

            com.quickbite.edu.dto.response.UserContactResponse contact =
                    userService.getContactById(request.getUserId());

            com.quickbite.edu.grpc.UserContactResponse response = com.quickbite.edu.grpc.UserContactResponse.newBuilder()
                    .setId(contact.getId() != null ? contact.getId() : 0L)
                    .setUsername(contact.getUsername() != null ? contact.getUsername() : "")
                    .setEmail(contact.getEmail() != null ? contact.getEmail() : "")
                    .setFullName(contact.getFullName() != null ? contact.getFullName() : "")
                    .setPhone(contact.getPhone() != null ? contact.getPhone() : "")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (NotFoundException ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void validateToken(ValidateTokenRequest request, StreamObserver<ValidateTokenResponse> responseObserver) {
        TokenValidationResponse result = userService.validateToken(request.getToken());

        ValidateTokenResponse.Builder builder = ValidateTokenResponse.newBuilder()
                .setValid(result.isValid());

        if (result.isValid()) {
            builder.setUserId(result.getUserId() != null ? result.getUserId() : 0L)
                    .setUsername(result.getUsername() != null ? result.getUsername() : "")
                    .setRole(result.getRole() != null ? result.getRole() : "");
        }

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }
}
