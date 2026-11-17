package com.college.complaint.dto;

import com.college.complaint.model.ComplaintStatus;

import java.time.Instant;

/** Public confirmation fields only; never contains a user or ownership identifier. */
public record ComplaintSubmissionResponse(
        String trackingReference,
        ComplaintStatus status,
        Instant submittedAt
) {
}
