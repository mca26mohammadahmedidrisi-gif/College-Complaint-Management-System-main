package com.college.complaint.service;

public interface EmailDeliveryService {
    boolean enabled();
    void sendVerification(String email, String token);
    void sendPasswordReset(String email, String token);
    void sendNotification(String email, String message);
}
