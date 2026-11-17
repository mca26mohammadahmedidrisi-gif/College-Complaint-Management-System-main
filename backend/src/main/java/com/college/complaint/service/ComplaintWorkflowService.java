package com.college.complaint.service;

import com.college.complaint.dto.ComplaintStatusUpdateRequest;
import com.college.complaint.dto.ComplaintHistoryEntry;
import com.college.complaint.dto.ComplaintView;
import com.college.complaint.exception.ApiException;
import com.college.complaint.model.Complaint;
import com.college.complaint.model.ComplaintOwnership;
import com.college.complaint.model.ComplaintStatus;
import com.college.complaint.model.ComplaintStatusHistory;
import com.college.complaint.model.Role;
import com.college.complaint.model.UserAccount;
import com.college.complaint.repository.ComplaintOwnershipRepository;
import com.college.complaint.repository.ComplaintRepository;
import com.college.complaint.repository.ComplaintStatusHistoryRepository;
import com.college.complaint.repository.DepartmentRepository;
import com.college.complaint.repository.UserRepository;
import com.college.complaint.security.ComplaintTrackingReferenceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ComplaintWorkflowService {
    private final ComplaintRepository complaints;
    private final ComplaintOwnershipRepository ownership;
    private final ComplaintStatusHistoryRepository history;
    private final UserRepository users;
    private final DepartmentRepository departments;
    private final ComplaintTrackingReferenceService references;

    public ComplaintWorkflowService(ComplaintRepository complaints,
                                    ComplaintOwnershipRepository ownership,
                                    ComplaintStatusHistoryRepository history,
                                    UserRepository users,
                                    DepartmentRepository departments,
                                    ComplaintTrackingReferenceService references) {
        this.complaints = complaints;
        this.ownership = ownership;
        this.history = history;
        this.users = users;
        this.departments = departments;
        this.references = references;
    }

    @Transactional(readOnly = true)
    public List<ComplaintView> listMine(UUID userId) {
        requireActiveUser(userId);
        List<Complaint> rows = ownership.findAllByUser_UserIdOrderByCreatedAtDesc(userId).stream()
                .map(ComplaintOwnership::getComplaint).toList();
        return views(rows);
    }

    @Transactional(readOnly = true)
    public ComplaintView getMine(String trackingReference, UUID userId) {
        requireActiveUser(userId);
        UUID complaintId = references.resolve(trackingReference)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Complaint not found"));
        if (!ownership.existsByComplaint_ComplaintIdAndUser_UserId(complaintId, userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Complaint not found");
        }
        return view(complaints.findById(complaintId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Complaint not found")));
    }

    @Transactional(readOnly = true)
    public List<ComplaintView> listForHandler(UUID userId) {
        UserAccount user = requireActiveUser(userId);
        if (user.getRole() == Role.STUDENT) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only complaint handlers can access this resource");
        }
        List<Complaint> visible = user.getRole() == Role.ADMIN
                ? complaints.findAllByOrderByCreatedAtDesc()
                : user.getDepartmentId() == null ? List.of()
                : complaints.findAllByDepartmentIdOrderByCreatedAtDesc(user.getDepartmentId());
        return views(visible);
    }

    @Transactional
    public ComplaintView update(UUID complaintId, UUID userId, ComplaintStatusUpdateRequest request) {
        UserAccount user = requireActiveUser(userId);
        if (user.getRole() == Role.STUDENT) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only complaint handlers can update complaints");
        }
        Complaint complaint = complaints.findByIdForUpdate(complaintId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Complaint not found"));
        if (user.getRole() == Role.FACULTY
                && (user.getDepartmentId() == null || !user.getDepartmentId().equals(complaint.getDepartmentId()))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Complaint not found");
        }

        ComplaintStatus previous = complaint.getStatus();
        ComplaintStatus next = request.status();
        String remarks = request.remarks() == null ? null : request.remarks().trim();
        if (next != previous && !allowedTransitions(previous).contains(next)) {
            throw new ApiException(HttpStatus.CONFLICT, "This complaint cannot move from " + previous + " to " + next);
        }
        if (next == previous && (remarks == null || remarks.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Add remarks when saving without changing the status");
        }
        complaint.setStatus(next);
        history.save(new ComplaintStatusHistory(complaint, previous, next,
                remarks == null || remarks.isBlank() ? null : remarks));
        return view(complaint);
    }

    private Set<ComplaintStatus> allowedTransitions(ComplaintStatus status) {
        return switch (status) {
            case PENDING -> Set.of(ComplaintStatus.ASSIGNED, ComplaintStatus.IN_PROGRESS, ComplaintStatus.REJECTED);
            case ASSIGNED -> Set.of(ComplaintStatus.IN_PROGRESS, ComplaintStatus.REJECTED);
            case IN_PROGRESS -> Set.of(ComplaintStatus.RESOLVED, ComplaintStatus.REJECTED);
            case RESOLVED, REJECTED -> Set.of();
        };
    }

    private UserAccount requireActiveUser(UUID userId) {
        return users.findById(userId).filter(UserAccount::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "The authenticated account could not be verified"));
    }

    private ComplaintView view(Complaint complaint) {
        return views(List.of(complaint)).get(0);
    }

    private List<ComplaintView> views(List<Complaint> complaintsToMap) {
        if (complaintsToMap.isEmpty()) return List.of();
        List<UUID> complaintIds = complaintsToMap.stream().map(Complaint::getComplaintId).toList();
        Map<UUID, List<ComplaintStatusHistory>> historyByComplaint = history
                .findAllByComplaint_ComplaintIdInOrderByChangedAtAsc(complaintIds).stream()
                .collect(Collectors.groupingBy(event -> event.getComplaint().getComplaintId()));
        Set<UUID> departmentIds = complaintsToMap.stream().map(Complaint::getDepartmentId)
                .filter(id -> id != null).collect(Collectors.toSet());
        Map<UUID, String> departmentNames = departments.findAllById(departmentIds).stream()
                .collect(Collectors.toMap(department -> department.getDepartmentId(), department -> department.getDepartmentName()));
        return complaintsToMap.stream().map(complaint -> {
            List<ComplaintStatusHistory> events = historyByComplaint.getOrDefault(complaint.getComplaintId(), List.of());
            String latestRemarks = events.stream().filter(event -> event.getPreviousStatus() != null)
                    .map(ComplaintStatusHistory::getRemarks).filter(value -> value != null && !value.isBlank())
                    .reduce((first, second) -> second).orElse(null);
            return new ComplaintView(complaint.getComplaintId(), references.generate(complaint.getComplaintId()),
                    complaint.getComplaintTitle(), complaint.getDescription(), complaint.getCategory().getCategoryId(),
                    complaint.getCategory().getCategoryName(), departmentNames.get(complaint.getDepartmentId()),
                    complaint.getBuilding().getBuildingName(), complaint.getFloor().getFloorName(),
                    complaint.getClassroom().getClassroomId(), complaint.getClassroom().getClassroomName(),
                    complaint.getStatus(), complaint.getCreatedAt(), latestRemarks,
                    events.stream().map(event -> new ComplaintHistoryEntry(event.getPreviousStatus(), event.getNewStatus(),
                            event.getRemarks(), event.getChangedAt())).toList());
        }).toList();
    }
}
