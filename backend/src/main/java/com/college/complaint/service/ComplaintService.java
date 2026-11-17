package com.college.complaint.service;

import com.college.complaint.dto.ComplaintCategoryResponse;
import com.college.complaint.dto.ComplaintSubmissionRequest;
import com.college.complaint.dto.ComplaintSubmissionResponse;
import com.college.complaint.exception.ApiException;
import com.college.complaint.model.Classroom;
import com.college.complaint.model.Complaint;
import com.college.complaint.model.ComplaintCategory;
import com.college.complaint.model.ComplaintOwnership;
import com.college.complaint.model.ComplaintStatus;
import com.college.complaint.model.ComplaintStatusHistory;
import com.college.complaint.model.UserAccount;
import com.college.complaint.repository.ClassroomRepository;
import com.college.complaint.repository.ComplaintCategoryRepository;
import com.college.complaint.repository.ComplaintOwnershipRepository;
import com.college.complaint.repository.ComplaintRepository;
import com.college.complaint.repository.ComplaintStatusHistoryRepository;
import com.college.complaint.repository.UserRepository;
import com.college.complaint.security.ComplaintTrackingReferenceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final ComplaintCategoryRepository categoryRepository;
    private final ClassroomRepository classroomRepository;
    private final ComplaintOwnershipRepository ownershipRepository;
    private final ComplaintStatusHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final ComplaintTrackingReferenceService trackingReferenceService;

    public ComplaintService(ComplaintRepository complaintRepository,
                            ComplaintCategoryRepository categoryRepository,
                            ClassroomRepository classroomRepository,
                            ComplaintOwnershipRepository ownershipRepository,
                            ComplaintStatusHistoryRepository historyRepository,
                            UserRepository userRepository,
                            ComplaintTrackingReferenceService trackingReferenceService) {
        this.complaintRepository = complaintRepository;
        this.categoryRepository = categoryRepository;
        this.classroomRepository = classroomRepository;
        this.ownershipRepository = ownershipRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.trackingReferenceService = trackingReferenceService;
    }

    @Transactional(readOnly = true)
    public List<ComplaintCategoryResponse> getCategories() {
        return categoryRepository.findAllByActiveTrueOrderByCategoryNameAsc().stream()
                .map(category -> new ComplaintCategoryResponse(category.getCategoryId(), category.getCategoryName()))
                .toList();
    }

    @Transactional
    public ComplaintSubmissionResponse submit(ComplaintSubmissionRequest request, UUID authenticatedUserId) {
        UserAccount user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                        "The authenticated account could not be verified"));
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This account is not active");
        }

        ComplaintCategory category = categoryRepository.findByCategoryIdAndActiveTrue(request.categoryId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Complaint category not found"));
        Classroom classroom = classroomRepository.findById(request.classroomId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Classroom not found"));

        // Derive the location from the selected classroom so clients cannot submit
        // mismatched building/floor/classroom combinations.
        var floor = classroom.getFloor();
        var building = floor.getBuilding();
        Complaint complaint = complaintRepository.save(new Complaint(
                request.title().trim(), request.description().trim(), category, building, floor, classroom));

        // Identity is written only to the private ownership table, never to complaints.
        ownershipRepository.save(new ComplaintOwnership(complaint, user));
        historyRepository.save(new ComplaintStatusHistory(
                complaint, null, ComplaintStatus.PENDING, "Complaint submitted"));

        return new ComplaintSubmissionResponse(
                trackingReferenceService.generate(complaint.getComplaintId()),
                complaint.getStatus(),
                complaint.getCreatedAt());
    }
}
