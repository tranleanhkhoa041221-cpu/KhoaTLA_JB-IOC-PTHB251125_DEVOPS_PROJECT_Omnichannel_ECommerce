package com.quickbite.edu.service.impl;

import com.quickbite.edu.dto.client.UserContactResponse;
import com.quickbite.edu.grpc.IdentityGrpcClient;
import com.quickbite.edu.service.interfaces.UserContactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserContactServiceImpl implements UserContactService {

    private final IdentityGrpcClient identityGrpcClient;

    @Override
    public UserContactResponse resolveContact(Long customerId) {
        return identityGrpcClient.getUserContact(customerId);
    }
}
