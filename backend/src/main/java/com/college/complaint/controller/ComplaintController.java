package com.college.complaint.controller;

import com.college.complaint.dto.ComplaintCategoryResponse;
import com.college.complaint.dto.ComplaintImageResponse;
import com.college.complaint.dto.ComplaintImageUploadedResponse;
import com.college.complaint.dto.ComplaintSubmissionRequest;
import com.college.complaint.dto.ComplaintSubmissionResponse;
import com.college.complaint.dto.ComplaintStatusUpdateRequest;
import com.college.complaint.dto.ComplaintView;
import com.college.complaint.exception.ApiException;
import com.college.complaint.service.ComplaintService;
import com.college.complaint.service.ComplaintImageService;
import com.college.complaint.service.ComplaintWorkflowService;
import com.college.complaint.security.ComplaintTrackingReferenceService;
import com.college.complaint.utility.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/complaints")
public class ComplaintController {

    private final ComplaintService complaintService;
    private final ComplaintImageService complaintImageService;
    private final ComplaintWorkflowService workflowService;
    private final ComplaintTrackingReferenceService trackingReferences;

    public ComplaintController(ComplaintService complaintService, ComplaintImageService complaintImageService,
                               ComplaintWorkflowService workflowService,
                               ComplaintTrackingReferenceService trackingReferences) {
        this.complaintService = complaintService;
        this.complaintImageService = complaintImageService;
        this.workflowService = workflowService;
        this.trackingReferences = trackingReferences;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ComplaintSubmissionResponse>> submit(
            @Valid @RequestBody ComplaintSubmissionRequest request,
            Authentication authentication) {
        UUID userId;
        try {
            userId = UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "The access token has an invalid subject");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Complaint submitted successfully",
                        complaintService.submit(request, userId)));
    }

    @GetMapping("/categories")
    public ApiResponse<List<ComplaintCategoryResponse>> getCategories() {
        return ApiResponse.success("Complaint categories retrieved successfully", complaintService.getCategories());
    }

    @GetMapping("/mine")
    public ApiResponse<List<ComplaintView>> getMine(Authentication authentication) {
        return ApiResponse.success("Your complaints retrieved successfully",
                workflowService.listMine(authenticatedUserId(authentication)));
    }

    @GetMapping("/mine/{trackingReference}")
    public ApiResponse<ComplaintView> getMineByReference(@PathVariable String trackingReference,
                                                          Authentication authentication) {
        return ApiResponse.success("Complaint retrieved successfully",
                workflowService.getMine(trackingReference, authenticatedUserId(authentication)));
    }

    @GetMapping("/staff")
    public ApiResponse<List<ComplaintView>> getForHandler(Authentication authentication) {
        return ApiResponse.success("Complaints retrieved successfully",
                workflowService.listForHandler(authenticatedUserId(authentication)));
    }

    @PutMapping("/{complaintId}/status")
    public ApiResponse<ComplaintView> updateStatus(@PathVariable UUID complaintId,
                                                    @Valid @RequestBody ComplaintStatusUpdateRequest request,
                                                    Authentication authentication) {
        return ApiResponse.success("Complaint updated successfully",
                workflowService.update(complaintId, authenticatedUserId(authentication), request));
    }

    @PostMapping(value = "/{complaintId}/images", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ComplaintImageUploadedResponse>> uploadImage(
            @PathVariable String complaintId,
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        UUID userId = authenticatedUserId(authentication);
        ComplaintImageUploadedResponse result = complaintImageService.upload(resolveComplaintId(complaintId), userId, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Complaint image uploaded successfully", result));
    }

    @GetMapping("/{complaintId}/images")
    public ResponseEntity<ApiResponse<List<ComplaintImageResponse>>> getImages(
            @PathVariable String complaintId,
            Authentication authentication) {
        UUID userId = authenticatedUserId(authentication);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Complaint images retrieved successfully",
                        complaintImageService.getImages(resolveComplaintId(complaintId), userId)));
    }

    private UUID authenticatedUserId(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "The access token has an invalid subject");
        }
    }

    private UUID resolveComplaintId(String idOrReference) {
        try {
            return UUID.fromString(idOrReference);
        } catch (IllegalArgumentException ignored) {
            return trackingReferences.resolve(idOrReference)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Complaint not found"));
        }
    }
}
