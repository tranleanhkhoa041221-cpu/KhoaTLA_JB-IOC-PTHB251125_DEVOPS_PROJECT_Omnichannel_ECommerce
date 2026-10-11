package com.quickbite.edu.dto.client;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserContactResponse {
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String phone;
}
