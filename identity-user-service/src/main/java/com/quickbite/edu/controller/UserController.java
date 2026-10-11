package com.quickbite.edu.controller;

import com.quickbite.edu.dto.request.*;
import com.quickbite.edu.dto.response.*;
import com.quickbite.edu.service.interfaces.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 1. Đăng ký tài khoản
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@RequestBody @Valid RegisterRequest request) {
        RegisterResponse response = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Đăng ký tài khoản thành công", response));
    }

    // 2. Đăng nhập (trả về Access Token + Refresh Token)
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody @Valid UserRequest request) {
        AuthResponse response = userService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", response));
    }

    // 3. Cấp mới token bằng Refresh Token
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> refreshToken(@RequestBody @Valid RefreshTokenRequest request) {
        RefreshTokenResponse response = userService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Làm mới token thành công", response));
    }

    // 4. Xác thực token (dành cho API Gateway hoặc Service khác)
    @GetMapping("/validate-token")
    public ResponseEntity<ApiResponse<TokenValidationResponse>> validateToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "token", required = false) String tokenParam
    ) {
        String token = authHeader != null ? authHeader : tokenParam;
        TokenValidationResponse response = userService.validateToken(token);
        return ResponseEntity.ok(ApiResponse.success("Xác thực token thành công", response));
    }

    // 5. Lấy thông tin tài khoản hiện tại
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(Principal principal) {
        UserProfileResponse response = userService.getProfile(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin tài khoản thành công", response));
    }

    // 6. Cập nhật thông tin tài khoản hiện tại
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            Principal principal,
            @RequestBody @Valid UpdateProfileRequest request
    ) {
        UserProfileResponse response = userService.updateProfile(principal.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thông tin thành công", response));
    }

    // 7. Lấy danh sách địa chỉ giao hàng của user
    @GetMapping("/addresses")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getAddresses(Principal principal) {
        List<AddressResponse> response = userService.getAddresses(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách địa chỉ thành công", response));
    }

    // 8. Thêm địa chỉ giao hàng mới
    @PostMapping("/addresses")
    public ResponseEntity<ApiResponse<AddressResponse>> addAddress(
            Principal principal,
            @RequestBody @Valid AddressRequest request
    ) {
        AddressResponse response = userService.addAddress(principal.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Thêm địa chỉ giao hàng thành công", response));
    }

    // 9. Cập nhật địa chỉ giao hàng
    @PutMapping("/addresses/{id}")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
            Principal principal,
            @PathVariable Long id,
            @RequestBody @Valid AddressRequest request
    ) {
        AddressResponse response = userService.updateAddress(principal.getName(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật địa chỉ thành công", response));
    }

    // 10. Xóa địa chỉ giao hàng
    @DeleteMapping("/addresses/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            Principal principal,
            @PathVariable Long id
    ) {
        userService.deleteAddress(principal.getName(), id);
        return ResponseEntity.ok(ApiResponse.success("Xóa địa chỉ giao hàng thành công", null));
    }

    // 11. Đặt địa chỉ làm mặc định
    @PutMapping("/addresses/{id}/default")
    public ResponseEntity<ApiResponse<AddressResponse>> setDefaultAddress(
            Principal principal,
            @PathVariable Long id
    ) {
        AddressResponse response = userService.setDefaultAddress(principal.getName(), id);
        return ResponseEntity.ok(ApiResponse.success("Đặt địa chỉ mặc định thành công", response));
    }

    // 12. Phân quyền người dùng (Chỉ ADMIN mới có quyền thực hiện)
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/role")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateUserRole(
            @PathVariable Long id,
            @RequestParam String role
    ) {
        UserProfileResponse response = userService.updateUserRole(id, role);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật quyền người dùng thành công", response));
    }
}
