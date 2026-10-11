package com.quickbite.edu.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * BAT BUOC gia tri secret-key nay phai GIONG HET voi jwt.secret-key cua
 * identity-user-service - day la noi DUY NHAT phat hanh token (Gateway chi
 * doc/xac thuc, khong bao gio tu phat hanh token).
 */
@Component
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {
    private String secretKey;
}
