package com.quickbite.edu.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Order & Inventory Service TU XAC THUC JWT doc lap (khong chi dua vao API Gateway) -
 * dung THEO DUNG secret + thuat toan HS256 voi identity-user-service (noi phat hanh token),
 * de dam bao "defense in depth": ke ca khi 1 request vo tinh/co y bo qua Gateway va goi
 * THANG vao service nay (vi du trong moi truong dev port 8084 dang mo), endpoint van duoc
 * bao ve dung muc, khong phu thuoc hoan toan vao Gateway.
 */
@Component
public class JwtService {

    @Value("${jwt.secret-key}")
    private String secretKey;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public boolean isValid(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(getKey()).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private Claims claims(String token) {
        return Jwts.parserBuilder().setSigningKey(getKey()).build().parseClaimsJws(token).getBody();
    }

    public String getUsername(String token) {
        return claims(token).getSubject();
    }

    public String getRole(String token) {
        return claims(token).get("role", String.class);
    }

    public Long getUserId(String token) {
        Object val = claims(token).get("userId");
        return (val instanceof Number number) ? number.longValue() : null;
    }
}
