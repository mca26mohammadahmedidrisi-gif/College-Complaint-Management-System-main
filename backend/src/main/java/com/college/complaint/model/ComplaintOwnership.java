package com.college.complaint.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "complaint_ownership")
public class ComplaintOwnership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ownership_id", nullable = false, updatable = false)
    private UUID ownershipId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false, unique = true)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ComplaintOwnership() {
    }

    public ComplaintOwnership(Complaint complaint, UserAccount user) {
        this.complaint = complaint;
        this.user = user;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getOwnershipId() {
        return ownershipId;
    }

    public Complaint getComplaint() {
        return complaint;
    }

    public UserAccount getUser() {
        return user;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
