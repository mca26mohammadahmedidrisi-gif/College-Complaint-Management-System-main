package com.college.complaint.dto;

import com.college.complaint.model.ComplaintStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ComplaintStatusUpdateRequest(
        @NotNull(message = "Complaint status is required") ComplaintStatus status,
        @Size(max = 4000, message = "Remarks must be at most 4000 characters") String remarks
) { }
