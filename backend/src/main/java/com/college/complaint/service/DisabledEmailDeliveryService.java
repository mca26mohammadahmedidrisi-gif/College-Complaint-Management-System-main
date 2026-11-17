package com.college.complaint.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.email.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledEmailDeliveryService implements EmailDeliveryService {
    @Override public boolean enabled() { return false; }
    @Override public void sendVerification(String email, String token) { }
    @Override public void sendPasswordReset(String email, String token) { }
    @Override public void sendNotification(String email, String message) { }
}
