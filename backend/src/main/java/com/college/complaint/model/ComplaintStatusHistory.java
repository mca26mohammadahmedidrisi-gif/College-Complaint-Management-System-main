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
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "complaint_status_history")
public class ComplaintStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "history_id", nullable = false, updatable = false)
    private UUID historyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private ComplaintStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private ComplaintStatus newStatus;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected ComplaintStatusHistory() {
    }

    public ComplaintStatusHistory(Complaint complaint, ComplaintStatus previousStatus,
                                  ComplaintStatus newStatus, String remarks) {
        this.complaint = complaint;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.remarks = remarks;
    }

    @PrePersist
    void onCreate() {
        if (changedAt == null) {
            changedAt = Instant.now();
        }
    }

    public UUID getHistoryId() {
        return historyId;
    }

    public Complaint getComplaint() {
        return complaint;
    }

    public ComplaintStatus getPreviousStatus() {
        return previousStatus;
    }

    public ComplaintStatus getNewStatus() {
        return newStatus;
    }

    public String getRemarks() {
        return remarks;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
