package com.college.complaint.dto;

import com.college.complaint.model.ComplaintStatus;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

/** Identity-free complaint projection for an owner or authorized handler. */
public record ComplaintView(
        UUID complaintId,
        String trackingReference,
        String title,
        String description,
        UUID categoryId,
        String category,
        UUID departmentId,
        String department,
        String building,
        String floor,
        UUID classroomId,
        String classroom,
        UUID buildingId,
        UUID floorId,
        ComplaintStatus status,
        Instant submittedAt,
        String remarks,
        boolean removed,
        Instant removedAt,
        String removalReason,
        UUID assignedToUserId,
        String assignedToName,
        List<ComplaintHistoryEntry> history
) { }
