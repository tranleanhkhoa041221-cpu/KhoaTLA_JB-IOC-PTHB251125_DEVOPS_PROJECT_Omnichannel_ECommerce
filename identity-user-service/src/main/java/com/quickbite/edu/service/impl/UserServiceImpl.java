package com.quickbite.edu.service.impl;

import com.quickbite.edu.config.jwt.JwtService;
import com.quickbite.edu.dto.request.*;
import com.quickbite.edu.dto.response.*;
import com.quickbite.edu.entity.Address;
import com.quickbite.edu.entity.RefreshToken;
import com.quickbite.edu.entity.User;
import com.quickbite.edu.entity.enums.Role;
import com.quickbite.edu.exception.BadRequestException;
import com.quickbite.edu.exception.ConflictException;
import com.quickbite.edu.exception.ForbiddenException;
import com.quickbite.edu.exception.NotFoundException;
import com.quickbite.edu.repository.AddressRepository;
import com.quickbite.edu.repository.RefreshTokenRepository;
import com.quickbite.edu.repository.UserRepository;
import com.quickbite.edu.service.interfaces.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Tên đăng nhập đã tồn tại: " + request.getUsername());
        }

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            if (userRepository.existsByEmail(request.getEmail().trim())) {
                throw new ConflictException("Email đã được sử dụng: " + request.getEmail());
            }
        }

        String phone = request.getPhone() == null ? null : request.getPhone().trim();
        if (phone != null && !phone.isBlank()) {
            if (userRepository.existsByPhone(phone)) {
                throw new ConflictException("Số điện thoại đã được sử dụng: " + phone);
            }
        }

        Role userRole = Role.CUSTOMER;
        if (request.getRole() != null && !request.getRole().isBlank()) {
            try {
                userRole = Role.valueOf(request.getRole().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Vai trò không hợp lệ: " + request.getRole() + ". Giá trị hợp lệ: CUSTOMER, SELLER");
            }

            if (userRole == Role.ADMIN) {
                throw new ForbiddenException("Không được phép tự đăng ký tài khoản với quyền Quản trị viên (ADMIN)");
            }
        }

        User user = User.builder()
                .username(request.getUsername().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail() != null ? request.getEmail().trim() : null)
                .fullName(request.getFullName() != null ? request.getFullName().trim() : null)
                .phone(phone)
                .role(userRole)
                .build();

        userRepository.save(user);

        return RegisterResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole().name())
                .build();
    }

    @Override
    @Transactional
    public AuthResponse login(UserRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadRequestException("Tên đăng nhập hoặc mật khẩu không chính xác"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadRequestException("Tên đăng nhập hoặc mật khẩu không chính xác");
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenStr = jwtService.generateRefreshToken();

        // Thu hồi refresh token cũ
        refreshTokenRepository.revokeAllByUserId(user.getId());

        // Lưu refresh token mới (hết hạn sau 7 ngày)
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(refreshTokenStr)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpiresIn())
                .build();
    }

    @Override
    @Transactional
    public RefreshTokenResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken token = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new BadRequestException("Refresh token không tồn tại"));

        if (token.isRevoked() || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Refresh token đã hết hạn hoặc bị thu hồi");
        }

        User user = token.getUser();

        // 1. Thu hồi refresh token cũ (đảm bảo bảo mật tránh bị lạm dụng)
        token.setRevoked(true);
        refreshTokenRepository.save(token);

        // 2. Sinh cặp token hoàn toàn mới
        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshTokenStr = jwtService.generateRefreshToken();

        // 3. Lưu refresh token mới vào DB
        RefreshToken newRefreshToken = RefreshToken.builder()
                .user(user)
                .token(newRefreshTokenStr)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();
        refreshTokenRepository.save(newRefreshToken);

        return RefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshTokenStr) // Trả về refresh token mới luôn
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpiresIn())
                .build();
    }

    @Override
    public TokenValidationResponse validateToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (token == null || !jwtService.validateToken(token)) {
            return TokenValidationResponse.builder()
                    .valid(false)
                    .build();
        }

        return TokenValidationResponse.builder()
                .valid(true)
                .userId(jwtService.getUserIdFromToken(token))
                .username(jwtService.getUsernameFromToken(token))
                .role(jwtService.getRoleFromToken(token))
                .build();
    }

    @Override
    public UserProfileResponse getProfile(String username) {
        User user = getUserByUsername(username);
        List<AddressResponse> addressResponses = user.getAddresses().stream()
                .map(this::mapToAddressResponse)
                .toList();

        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole().name())
                .addresses(addressResponses)
                .createdAt(user.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(String username, UpdateProfileRequest request) {
        User user = getUserByUsername(username);

        if (request.getEmail() != null && !request.getEmail().isBlank()
                && !request.getEmail().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new ConflictException("Email đã được sử dụng: " + request.getEmail());
            }
            user.setEmail(request.getEmail().trim());
        }

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getPhone() != null) {
            String newPhone = request.getPhone().trim();
            if (!newPhone.equals(user.getPhone()) && userRepository.existsByPhoneAndIdNot(newPhone, user.getId())) {
                throw new ConflictException("Số điện thoại đã được sử dụng: " + newPhone);
            }
            user.setPhone(newPhone);
        }

        userRepository.save(user);
        return getProfile(username);
    }

    @Override
    public List<AddressResponse> getAddresses(String username) {
        User user = getUserByUsername(username);
        return addressRepository.findByUserId(user.getId()).stream()
                .map(this::mapToAddressResponse)
                .toList();
    }

    @Override
    @Transactional
    public AddressResponse addAddress(String username, AddressRequest request) {
        User user = getUserByUsername(username);

        List<Address> existing = addressRepository.findByUserId(user.getId());
        boolean shouldBeDefault = request.isDefault() || existing.isEmpty();

        if (shouldBeDefault) {
            for (Address addr : existing) {
                if (addr.isDefault()) {
                    addr.setDefault(false);
                    addressRepository.save(addr);
                }
            }
        }

        Address address = Address.builder()
                .user(user)
                .recipientName(request.getRecipientName().trim())
                .phone(request.getPhone().trim())
                .province(request.getProvince().trim())
                .district(request.getDistrict().trim())
                .ward(request.getWard().trim())
                .addressDetail(request.getAddressDetail().trim())
                .isDefault(shouldBeDefault)
                .build();

        addressRepository.save(address);
        return mapToAddressResponse(address);
    }

    @Override
    @Transactional
    public AddressResponse updateAddress(String username, Long addressId, AddressRequest request) {
        User user = getUserByUsername(username);
        Address address = addressRepository.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy địa chỉ với ID: " + addressId));

        if (request.isDefault() && !address.isDefault()) {
            List<Address> addresses = addressRepository.findByUserId(user.getId());
            for (Address addr : addresses) {
                if (addr.isDefault()) {
                    addr.setDefault(false);
                    addressRepository.save(addr);
                }
            }
            address.setDefault(true);
        }

        address.setRecipientName(request.getRecipientName().trim());
        address.setPhone(request.getPhone().trim());
        address.setProvince(request.getProvince().trim());
        address.setDistrict(request.getDistrict().trim());
        address.setWard(request.getWard().trim());
        address.setAddressDetail(request.getAddressDetail().trim());

        addressRepository.save(address);
        return mapToAddressResponse(address);
    }

    @Override
    @Transactional
    public void deleteAddress(String username, Long addressId) {
        User user = getUserByUsername(username);
        Address address = addressRepository.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy địa chỉ với ID: " + addressId));
        addressRepository.delete(address);
    }

    @Override
    @Transactional
    public AddressResponse setDefaultAddress(String username, Long addressId) {
        User user = getUserByUsername(username);
        Address targetAddress = addressRepository.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy địa chỉ với ID: " + addressId));

        List<Address> addresses = addressRepository.findByUserId(user.getId());
        for (Address addr : addresses) {
            addr.setDefault(addr.getId().equals(addressId));
            addressRepository.save(addr);
        }

        return mapToAddressResponse(targetAddress);
    }

    @Override
    @Transactional(readOnly = true)
    public UserContactResponse getContactById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng với ID: " + userId));
        return UserContactResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .build();
    }

    @Override
    @Transactional
    public UserProfileResponse updateUserRole(Long userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng với ID: " + userId));

        Role newRole;
        try {
            newRole = Role.valueOf(roleName.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Vai trò không hợp lệ: " + roleName + ". Giá trị hợp lệ: CUSTOMER, ADMIN, SELLER");
        }

        user.setRole(newRole);
        userRepository.save(user);

        return getProfile(user.getUsername());
    }

    private User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng: " + username));
    }

    private AddressResponse mapToAddressResponse(Address address) {
        return AddressResponse.builder()
                .id(address.getId())
                .recipientName(address.getRecipientName())
                .phone(address.getPhone())
                .province(address.getProvince())
                .district(address.getDistrict())
                .ward(address.getWard())
                .addressDetail(address.getAddressDetail())
                .isDefault(address.isDefault())
                .createdAt(address.getCreatedAt())
                .build();
    }
}
