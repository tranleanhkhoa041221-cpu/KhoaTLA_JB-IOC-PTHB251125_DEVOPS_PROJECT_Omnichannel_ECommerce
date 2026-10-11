package com.quickbite.edu.service.interfaces;

import com.quickbite.edu.dto.request.*;
import com.quickbite.edu.dto.response.*;

import java.util.List;

public interface UserService {

    RegisterResponse register(RegisterRequest request);

    AuthResponse login(UserRequest request);

    RefreshTokenResponse refreshToken(RefreshTokenRequest request);

    TokenValidationResponse validateToken(String token);

    UserProfileResponse getProfile(String username);

    UserProfileResponse updateProfile(String username, UpdateProfileRequest request);

    List<AddressResponse> getAddresses(String username);

    AddressResponse addAddress(String username, AddressRequest request);

    AddressResponse updateAddress(String username, Long addressId, AddressRequest request);

    void deleteAddress(String username, Long addressId);

    AddressResponse setDefaultAddress(String username, Long addressId);

    UserProfileResponse updateUserRole(Long userId, String role);

    UserContactResponse getContactById(Long userId);
}
