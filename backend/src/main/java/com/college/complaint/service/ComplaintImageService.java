package com.college.complaint.service;

import com.college.complaint.dto.ComplaintImageResponse;
import com.college.complaint.dto.ComplaintImageUploadedResponse;
import com.college.complaint.exception.ApiException;
import com.college.complaint.model.Complaint;
import com.college.complaint.model.ComplaintImage;
import com.college.complaint.model.UserAccount;
import com.college.complaint.repository.ComplaintImageRepository;
import com.college.complaint.repository.ComplaintOwnershipRepository;
import com.college.complaint.repository.ComplaintRepository;
import com.college.complaint.repository.UserRepository;
import com.college.complaint.storage.SupabaseStorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ComplaintImageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComplaintImageService.class);

    private final ComplaintRepository complaintRepository;
    private final ComplaintImageRepository imageRepository;
    private final ComplaintOwnershipRepository ownershipRepository;
    private final UserRepository userRepository;
    private final SupabaseStorageClient storageClient;
    private final ImageContentValidator imageContentValidator;
    private final Duration signedUrlLifetime;

    public ComplaintImageService(ComplaintRepository complaintRepository,
                                 ComplaintImageRepository imageRepository,
                                 ComplaintOwnershipRepository ownershipRepository,
                                 UserRepository userRepository,
                                 SupabaseStorageClient storageClient,
                                 ImageContentValidator imageContentValidator,
                                 @Value("${supabase.storage.signed-url-ttl-seconds:300}") long signedUrlTtlSeconds) {
        if (signedUrlTtlSeconds < 60 || signedUrlTtlSeconds > 3600) {
            throw new IllegalStateException("Supabase signed URL lifetime must be between 60 and 3600 seconds");
        }
        this.complaintRepository = complaintRepository;
        this.imageRepository = imageRepository;
        this.ownershipRepository = ownershipRepository;
        this.userRepository = userRepository;
        this.storageClient = storageClient;
        this.imageContentValidator = imageContentValidator;
        this.signedUrlLifetime = Duration.ofSeconds(signedUrlTtlSeconds);
    }

    @Transactional
    public ComplaintImageUploadedResponse upload(UUID complaintId, UUID authenticatedUserId, MultipartFile file) {
        Complaint complaint = requireOwnedComplaint(complaintId, authenticatedUserId);
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Image file must not be empty");
        }
        if (file.getSize() > ImageContentValidator.MAX_FILE_SIZE_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Image file must not exceed 5 MB");
        }

        final byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Image file could not be read");
        }
        ImageContentValidator.ValidatedImage validated =
                imageContentValidator.validate(bytes, file.getContentType());

        // No client filename is used: the path consists only of a UUID and fixed extension.
        String objectPath = complaintId + "/" + UUID.randomUUID() + "." + validated.extension();
        storageClient.upload(objectPath, validated.sanitizedBytes(), validated.contentType());

        try {
            ComplaintImage saved = imageRepository.saveAndFlush(new ComplaintImage(
                    complaint, objectPath, validated.contentType(), validated.sizeBytes()));
            return new ComplaintImageUploadedResponse(saved.getImageId(), saved.getContentType(),
                    saved.getFileSizeBytes(), saved.getCreatedAt());
        } catch (RuntimeException exception) {
            removeUploadedObject(objectPath);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Image metadata could not be saved; the upload was rolled back");
        }
    }

    @Transactional(readOnly = true)
    public List<ComplaintImageResponse> getImages(UUID complaintId, UUID authenticatedUserId) {
        UserAccount user = userRepository.findById(authenticatedUserId).filter(UserAccount::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "This account cannot access the complaint"));
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Complaint not found"));
        boolean owner = ownershipRepository.existsByComplaint_ComplaintIdAndUser_UserId(complaintId, user.getUserId());
        boolean handler = user.getRole() == com.college.complaint.model.Role.ADMIN
                || (!complaint.isRemoved() && user.getRole() == com.college.complaint.model.Role.FACULTY
                && user.getDepartmentId() != null && user.getDepartmentId().equals(complaint.getDepartmentId()));
        if (!owner && !handler) throw new ApiException(HttpStatus.NOT_FOUND, "Complaint not found");
        Instant now = Instant.now();
        Instant expiresAt = now.plus(signedUrlLifetime);
        return imageRepository.findAllByComplaint_ComplaintIdOrderByCreatedAtAsc(complaintId).stream()
                .map(image -> new ComplaintImageResponse(
                        image.getImageId(), image.getContentType(), image.getFileSizeBytes(), image.getCreatedAt(),
                        storageClient.createSignedUrl(image.getStoragePath(), signedUrlLifetime), expiresAt))
                .toList();
    }

    private Complaint requireOwnedComplaint(UUID complaintId, UUID authenticatedUserId) {
        UserAccount user = userRepository.findById(authenticatedUserId)
                .filter(UserAccount::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "This account cannot access the complaint"));
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Complaint not found"));
        if (complaint.isRemoved()) {
            throw new ApiException(HttpStatus.CONFLICT, "This complaint has been removed from the active queue");
        }
        if (!ownershipRepository.existsByComplaint_ComplaintIdAndUser_UserId(complaintId, user.getUserId())) {
            // Hide whether another person's complaint exists.
            throw new ApiException(HttpStatus.NOT_FOUND, "Complaint not found");
        }
        return complaint;
    }

    private void removeUploadedObject(String objectPath) {
        try {
            storageClient.delete(objectPath);
        } catch (RuntimeException cleanupException) {
            // Do not replace the safe database error or expose storage details to clients.
            LOGGER.warn("Could not remove uploaded object after metadata persistence failed");
        }
    }
}
