package com.college.complaint.dto;

import java.time.Instant;
import java.util.UUID;

/** Confirmation after an image has been stored; private object paths stay server-side. */
public record ComplaintImageUploadedResponse(
        UUID imageId,
        String contentType,
        long fileSizeBytes,
        Instant createdAt
) {
}
