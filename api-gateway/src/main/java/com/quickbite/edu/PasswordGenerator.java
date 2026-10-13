package com.quickbite.edu;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        System.out.println("Admin:   " + encoder.encode("Test@1234"));
        System.out.println("Mentor:  " + encoder.encode("Test@1234"));
        System.out.println("Student: " + encoder.encode("Test@1234"));
    }
}