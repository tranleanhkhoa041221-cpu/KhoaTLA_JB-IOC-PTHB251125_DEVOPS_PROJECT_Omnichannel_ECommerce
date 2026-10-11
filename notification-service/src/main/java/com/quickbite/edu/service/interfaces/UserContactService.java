package com.quickbite.edu.service.interfaces;

import com.quickbite.edu.dto.client.UserContactResponse;

public interface UserContactService {
    UserContactResponse resolveContact(Long customerId);
}
