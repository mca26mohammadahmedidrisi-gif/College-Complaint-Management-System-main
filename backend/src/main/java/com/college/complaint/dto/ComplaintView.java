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
        String department,
        String building,
        String floor,
        UUID classroomId,
        String classroom,
        ComplaintStatus status,
        Instant submittedAt,
        String remarks,
        List<ComplaintHistoryEntry> history
) { }
