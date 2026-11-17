package com.college.complaint.dto;

import com.college.complaint.model.ComplaintStatus;
import java.time.Instant;

public record ComplaintHistoryEntry(
        ComplaintStatus previousStatus,
        ComplaintStatus newStatus,
        String remarks,
        Instant changedAt
) { }
