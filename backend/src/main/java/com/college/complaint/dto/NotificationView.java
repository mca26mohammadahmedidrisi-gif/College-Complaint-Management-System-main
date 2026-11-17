package com.college.complaint.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationView(UUID notificationId, UUID complaintId, String type,
                               String message, Instant createdAt, Instant readAt) { }
