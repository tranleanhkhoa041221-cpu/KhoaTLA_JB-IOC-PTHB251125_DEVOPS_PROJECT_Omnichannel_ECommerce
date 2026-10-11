package com.quickbite.edu.security;

import com.quickbite.edu.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Gateway KHONG phat hanh token (do la viec cua identity-user-service), chi can XAC THUC
 * chu ky va DOC claims. Dung cung thu vien (jjwt 0.11.5) va cung API (parserBuilder/SignatureAlgorithm
 * HS256) voi JwtService ben identity-user-service de dam bao tuong thich 100%.
 */
@Component
@RequiredArgsConstructor
public class JwtTokenValidator {

    private final JwtProperties jwtProperties;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecretKey().getBytes(StandardCharsets.UTF_8));
    }

    /** true neu token con hop le (chu ky dung + chua het han). */
    public boolean isValid(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(getKey()).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public Claims getClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(getKey()).build().parseClaimsJws(token).getBody();
    }

    public String getUsername(String token) {
        return getClaims(token).getSubject();
    }

    public String getRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    public Long getUserId(String token) {
        Object val = getClaims(token).get("userId");
        return (val instanceof Number number) ? number.longValue() : null;
    }
}
