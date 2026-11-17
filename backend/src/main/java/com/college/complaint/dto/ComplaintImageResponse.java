package com.college.complaint.dto;

import java.time.Instant;
import java.util.UUID;

/** Public image information; never contains a storage path or submitter fields. */
public record ComplaintImageResponse(
        UUID imageId,
        String contentType,
        long fileSizeBytes,
        Instant createdAt,
        String signedUrl,
        Instant signedUrlExpiresAt
) {
}
