package com.college.complaint.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "complaint_categories")
public class ComplaintCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "category_id", nullable = false, updatable = false)
    private UUID categoryId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "category_name", nullable = false, unique = true, length = 120)
    private String categoryName;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ComplaintCategory() {
    }

    public ComplaintCategory(UUID departmentId, String categoryName, boolean active) {
        this.departmentId = departmentId;
        this.categoryName = categoryName;
        this.active = active;
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

    public UUID getCategoryId() {
        return categoryId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public boolean isActive() {
        return active;
    }
}
