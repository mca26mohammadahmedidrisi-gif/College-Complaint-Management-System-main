package com.college.complaint.service;

import com.college.complaint.dto.ComplaintStatusUpdateRequest;
import com.college.complaint.dto.ComplaintRemovalRequest;
import com.college.complaint.dto.ComplaintHistoryEntry;
import com.college.complaint.dto.ComplaintView;
import com.college.complaint.dto.ComplaintAssignmentRequest;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final NotificationService notifications;
    private final JdbcTemplate jdbc;

    public ComplaintWorkflowService(ComplaintRepository complaints,
                                    ComplaintOwnershipRepository ownership,
                                    ComplaintStatusHistoryRepository history,
                                    UserRepository users,
                                    DepartmentRepository departments,
                                    ComplaintTrackingReferenceService references,
                                    NotificationService notifications,
                                    JdbcTemplate jdbc) {
        this.complaints = complaints;
        this.ownership = ownership;
        this.history = history;
        this.users = users;
        this.departments = departments;
        this.references = references;
        this.notifications = notifications;
        this.jdbc = jdbc;
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
    public ComplaintView getMineById(UUID complaintId, UUID userId) {
        requireActiveUser(userId);
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
                : complaints.findAllByDepartmentIdAndRemovedAtIsNullOrderByCreatedAtDesc(user.getDepartmentId());
        if (user.getRole() == Role.FACULTY) {
            visible = visible.stream().filter(complaint -> complaint.getAssignedTo() == null
                    || userId.equals(complaint.getAssignedTo().getUserId())).toList();
        }
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
        requireHandlerAccess(user, complaint);
        if (complaint.isRemoved()) {
            throw new ApiException(HttpStatus.CONFLICT, "This complaint has been removed from the active queue");
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
                remarks == null || remarks.isBlank() ? null : remarks, user));
        if (next != previous || (remarks != null && !remarks.isBlank())) {
            notifyOwner(complaint, "STATUS_UPDATE", "Your complaint status is now " + next.name().replace('_', ' ') + ".");
        }
        return view(complaint);
    }

    @Transactional
    public ComplaintView remove(UUID complaintId, UUID userId, ComplaintRemovalRequest request) {
        UserAccount user = requireActiveUser(userId);
        if (user.getRole() == Role.STUDENT) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only complaint handlers can remove complaints");
        }
        Complaint complaint = complaints.findByIdForUpdate(complaintId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Complaint not found"));
        requireHandlerAccess(user, complaint);
        if (complaint.isRemoved()) {
            throw new ApiException(HttpStatus.CONFLICT, "This complaint has already been removed");
        }

        String reason = request.reason().trim();
        ComplaintStatus previous = complaint.getStatus();
        complaint.markRemoved(user, reason);
        history.save(new ComplaintStatusHistory(complaint, previous, ComplaintStatus.REJECTED,
                "Removed from the active queue: " + reason, user));
        notifyOwner(complaint, "COMPLAINT_REMOVED", "Your complaint was removed from the active faculty queue. Reason: " + reason);
        return view(complaint);
    }

    @Transactional
    public ComplaintView assign(UUID complaintId, UUID actorId, ComplaintAssignmentRequest request) {
        UserAccount actor = requireActiveUser(actorId);
        if (actor.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only an administrator can assign complaints");
        }
        Complaint complaint = complaints.findByIdForUpdate(complaintId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Complaint not found"));
        if (complaint.isRemoved()) throw new ApiException(HttpStatus.CONFLICT, "A removed complaint cannot be assigned");

        UserAccount previousAssignee = complaint.getAssignedTo();
        UserAccount nextAssignee = request.facultyUserId() == null ? null : users.findById(request.facultyUserId())
                .filter(UserAccount::isActive)
                .filter(candidate -> candidate.getRole() == Role.FACULTY)
                .filter(candidate -> complaint.getDepartmentId() != null
                        && complaint.getDepartmentId().equals(candidate.getDepartmentId()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "Choose an active faculty account in the complaint's department"));
        if (previousAssignee != null && nextAssignee != null
                && previousAssignee.getUserId().equals(nextAssignee.getUserId())) return view(complaint);
        if (previousAssignee == null && nextAssignee == null) return view(complaint);

        jdbc.update("""
                INSERT INTO complaint_assignment_history
                    (complaint_id, previous_assignee_user_id, new_assignee_user_id, changed_by_user_id)
                VALUES (?, ?, ?, ?)
                """, complaintId, previousAssignee == null ? null : previousAssignee.getUserId(),
                nextAssignee == null ? null : nextAssignee.getUserId(), actorId);
        complaint.setAssignedTo(nextAssignee);
        if (nextAssignee != null && complaint.getStatus() == ComplaintStatus.PENDING) {
            complaint.setStatus(ComplaintStatus.ASSIGNED);
            history.save(new ComplaintStatusHistory(complaint, ComplaintStatus.PENDING, ComplaintStatus.ASSIGNED,
                    "Assigned to " + nextAssignee.getFullName(), actor));
        } else if (nextAssignee == null && complaint.getStatus() == ComplaintStatus.ASSIGNED) {
            complaint.setStatus(ComplaintStatus.PENDING);
            history.save(new ComplaintStatusHistory(complaint, ComplaintStatus.ASSIGNED, ComplaintStatus.PENDING,
                    "Assignment cleared", actor));
        }
        if (nextAssignee != null) notifications.create(nextAssignee.getUserId(), complaintId, "ASSIGNMENT",
                "A complaint has been assigned to you.");
        notifyOwner(complaint, "ASSIGNMENT", nextAssignee == null
                ? "The staff assignment for your complaint was updated."
                : "A faculty member has been assigned to your complaint.");
        return view(complaint);
    }

    private void requireHandlerAccess(UserAccount user, Complaint complaint) {
        if (user.getRole() == Role.FACULTY && (user.getDepartmentId() == null
                || !user.getDepartmentId().equals(complaint.getDepartmentId())
                || (complaint.getAssignedTo() != null && !user.getUserId().equals(complaint.getAssignedTo().getUserId())))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Complaint not found");
        }
    }

    private void notifyOwner(Complaint complaint, String type, String message) {
        ownership.findByComplaint_ComplaintId(complaint.getComplaintId()).ifPresent(owner ->
                notifications.create(owner.getUser().getUserId(), complaint.getComplaintId(), type, message));
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
                    complaint.getCategory().getCategoryName(), complaint.getDepartmentId(), departmentNames.get(complaint.getDepartmentId()),
                    complaint.getBuilding().getBuildingName(), complaint.getFloor().getFloorName(),
                    complaint.getClassroom().getClassroomId(), complaint.getClassroom().getClassroomName(),
                    complaint.getBuilding().getBuildingId(), complaint.getFloor().getFloorId(),
                    complaint.getStatus(), complaint.getCreatedAt(), latestRemarks,
                    complaint.isRemoved(), complaint.getRemovedAt(), complaint.getRemovalReason(),
                    complaint.getAssignedTo() == null ? null : complaint.getAssignedTo().getUserId(),
                    complaint.getAssignedTo() == null ? null : complaint.getAssignedTo().getFullName(),
                    events.stream().map(event -> new ComplaintHistoryEntry(event.getPreviousStatus(), event.getNewStatus(),
                            event.getRemarks(), event.getChangedAt(), event.getChangedBy() == null ? "Staff" :
                            event.getChangedBy().getRole() == Role.STUDENT ? "Student" :
                                    event.getChangedBy().getFullName())).toList());
        }).toList();
    }
}
