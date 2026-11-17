package com.college.complaint.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "complaints")
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "complaint_id", nullable = false, updatable = false)
    private UUID complaintId;

    @Column(name = "complaint_title", nullable = false, length = 200)
    private String complaintTitle;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ComplaintCategory category;

    // Department routing comes from the selected category. It is not submitter identity.
    @Column(name = "department_id")
    private UUID departmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "classroom_id", nullable = false)
    private Classroom classroom;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ComplaintStatus status = ComplaintStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "removed_by_user_id")
    private UserAccount removedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_user_id")
    private UserAccount assignedTo;

    @Column(name = "removal_reason", columnDefinition = "TEXT")
    private String removalReason;

    protected Complaint() {
    }

    public Complaint(String complaintTitle, String description, ComplaintCategory category,
                     Building building, Floor floor, Classroom classroom) {
        this.complaintTitle = complaintTitle;
        this.description = description;
        this.category = category;
        this.departmentId = category.getDepartmentId();
        this.building = building;
        this.floor = floor;
        this.classroom = classroom;
        this.status = ComplaintStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getComplaintId() {
        return complaintId;
    }

    public String getComplaintTitle() {
        return complaintTitle;
    }

    public String getDescription() {
        return description;
    }

    public ComplaintCategory getCategory() {
        return category;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public Building getBuilding() {
        return building;
    }

    public Floor getFloor() {
        return floor;
    }

    public Classroom getClassroom() {
        return classroom;
    }

    public ComplaintStatus getStatus() {
        return status;
    }

    public void setStatus(ComplaintStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isRemoved() {
        return removedAt != null;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    public String getRemovalReason() {
        return removalReason;
    }

    public UserAccount getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(UserAccount assignedTo) {
        this.assignedTo = assignedTo;
    }

    public void updateStudentDetails(String title, String description, ComplaintCategory category, Classroom classroom) {
        this.complaintTitle = title;
        this.description = description;
        this.category = category;
        this.departmentId = category.getDepartmentId();
        this.classroom = classroom;
        this.floor = classroom.getFloor();
        this.building = classroom.getFloor().getBuilding();
    }

    public void markRemoved(UserAccount user, String reason) {
        if (isRemoved()) {
            throw new IllegalStateException("Complaint has already been removed");
        }
        this.removedAt = Instant.now();
        this.removedBy = user;
        this.removalReason = reason;
        this.status = ComplaintStatus.REJECTED;
    }
}
