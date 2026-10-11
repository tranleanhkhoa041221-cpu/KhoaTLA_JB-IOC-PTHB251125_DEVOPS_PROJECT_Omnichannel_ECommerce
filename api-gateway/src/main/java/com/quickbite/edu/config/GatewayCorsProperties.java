package com.quickbite.edu.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gateway.cors")
@Getter
@Setter
public class GatewayCorsProperties {
    /** "*" hoac danh sach domain cach nhau boi dau phay, vd "https://shop.com,https://admin.shop.com". */
    private String allowedOrigins = "*";
}
