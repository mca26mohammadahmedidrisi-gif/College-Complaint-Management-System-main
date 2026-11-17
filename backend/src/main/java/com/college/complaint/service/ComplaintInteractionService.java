package com.college.complaint.service;

import com.college.complaint.dto.ComplaintCommentRequest;
import com.college.complaint.dto.ComplaintCommentView;
import com.college.complaint.dto.ComplaintEditRequest;
import com.college.complaint.exception.ApiException;
import com.college.complaint.model.Complaint;
import com.college.complaint.model.ComplaintStatus;
import com.college.complaint.model.Role;
import com.college.complaint.model.UserAccount;
import com.college.complaint.repository.ClassroomRepository;
import com.college.complaint.repository.ComplaintCategoryRepository;
import com.college.complaint.repository.ComplaintOwnershipRepository;
import com.college.complaint.repository.ComplaintRepository;
import com.college.complaint.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Service
public class ComplaintInteractionService {
    private final ComplaintRepository complaints;
    private final ComplaintOwnershipRepository ownership;
    private final ComplaintCategoryRepository categories;
    private final ClassroomRepository classrooms;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;

    public ComplaintInteractionService(ComplaintRepository complaints,
                                       ComplaintOwnershipRepository ownership,
                                       ComplaintCategoryRepository categories,
                                       ClassroomRepository classrooms,
                                       UserRepository users,
                                       JdbcTemplate jdbc,
                                       NotificationService notifications) {
        this.complaints = complaints;
        this.ownership = ownership;
        this.categories = categories;
        this.classrooms = classrooms;
        this.users = users;
        this.jdbc = jdbc;
        this.notifications = notifications;
    }

    @Transactional
    public void edit(UUID complaintId, UUID userId, ComplaintEditRequest request) {
        UserAccount actor = activeUser(userId);
        if (actor.getRole() != Role.STUDENT || !ownership.existsByComplaint_ComplaintIdAndUser_UserId(complaintId, userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Complaint not found");
        }
        Complaint complaint = complaints.findByIdForUpdate(complaintId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Complaint not found"));
        if (complaint.isRemoved() || complaint.getStatus() != ComplaintStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "Only active complaints awaiting review can be edited");
        }
        var category = categories.findByCategoryIdAndActiveTrue(request.categoryId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Complaint category not found"));
        var classroom = classrooms.findById(request.classroomId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Classroom not found"));

        jdbc.update("""
                INSERT INTO complaint_revisions
                    (complaint_id, edited_by_user_id, old_title, old_description, old_category_id, old_classroom_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """, complaintId, userId, complaint.getComplaintTitle(), complaint.getDescription(),
                complaint.getCategory().getCategoryId(), complaint.getClassroom().getClassroomId());
        complaint.updateStudentDetails(request.title().trim(), request.description().trim(), category, classroom);
    }

    @Transactional(readOnly = true)
    public List<ComplaintCommentView> listComments(UUID complaintId, UUID userId) {
        UserAccount actor = activeUser(userId);
        Complaint complaint = complaints.findById(complaintId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Complaint not found"));
        authorizeView(complaint, actor);
        return jdbc.query("""
                SELECT cc.comment_id, cc.body, cc.created_at, u.full_name, u.role
                FROM complaint_comments cc JOIN users u ON u.user_id = cc.author_user_id
                WHERE cc.complaint_id = ? ORDER BY cc.created_at, cc.comment_id
                """, (rs, row) -> {
            String role = rs.getString("role");
            String author = "STUDENT".equals(role) ? "Student (anonymous)" : rs.getString("full_name");
            Timestamp timestamp = rs.getTimestamp("created_at");
            return new ComplaintCommentView(rs.getObject("comment_id", UUID.class), author,
                    rs.getString("body"), timestamp == null ? null : timestamp.toInstant());
        }, complaintId);
    }

    @Transactional
    public ComplaintCommentView addComment(UUID complaintId, UUID userId, ComplaintCommentRequest request) {
        UserAccount actor = activeUser(userId);
        Complaint complaint = complaints.findByIdForUpdate(complaintId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Complaint not found"));
        authorizeView(complaint, actor);
        if (complaint.isRemoved()) {
            throw new ApiException(HttpStatus.CONFLICT, "Replies are closed for complaints removed from the active queue");
        }
        String body = request.body().trim();
        UUID commentId = UUID.randomUUID();
        jdbc.update("INSERT INTO complaint_comments (comment_id, complaint_id, author_user_id, body) VALUES (?, ?, ?, ?)",
                commentId, complaintId, userId, body);
        if (actor.getRole() == Role.STUDENT) {
            if (complaint.getAssignedTo() != null) {
                notifications.create(complaint.getAssignedTo().getUserId(), complaintId, "STUDENT_REPLY",
                        "A student replied to complaint " + complaintId + ".");
            } else if (complaint.getDepartmentId() != null) {
                users.findAllByRoleAndDepartmentIdAndActiveTrueOrderByFullNameAsc(Role.FACULTY, complaint.getDepartmentId())
                        .forEach(faculty -> notifications.create(faculty.getUserId(), complaintId, "STUDENT_REPLY",
                                "A student replied to a complaint in your department."));
            }
        } else {
            ownership.findByComplaint_ComplaintId(complaintId).ifPresent(owner ->
                    notifications.create(owner.getUser().getUserId(), complaintId, "STAFF_REPLY",
                            "A staff member replied to your complaint."));
        }
        return new ComplaintCommentView(commentId,
                actor.getRole() == Role.STUDENT ? "Student (anonymous)" : actor.getFullName(), body,
                java.time.Instant.now());
    }

    private UserAccount activeUser(UUID userId) {
        return users.findById(userId).filter(UserAccount::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "The authenticated account could not be verified"));
    }

    private void authorizeView(Complaint complaint, UserAccount actor) {
        if (actor.getRole() == Role.STUDENT) {
            if (!ownership.existsByComplaint_ComplaintIdAndUser_UserId(complaint.getComplaintId(), actor.getUserId())) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Complaint not found");
            }
            return;
        }
        if (actor.getRole() == Role.FACULTY && (actor.getDepartmentId() == null
                || !actor.getDepartmentId().equals(complaint.getDepartmentId())
                || (complaint.getAssignedTo() != null && !actor.getUserId().equals(complaint.getAssignedTo().getUserId())))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Complaint not found");
        }
    }
}
