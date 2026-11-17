package com.college.complaint.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "complaint_images")
public class ComplaintImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "image_id", nullable = false, updatable = false)
    private UUID imageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @Column(name = "storage_path", nullable = false, unique = true, length = 500)
    private String storagePath;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ComplaintImage() {
    }

    public ComplaintImage(Complaint complaint, String storagePath, String contentType, long fileSizeBytes) {
        this.complaint = complaint;
        this.storagePath = storagePath;
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getImageId() {
        return imageId;
    }

    public Complaint getComplaint() {
        return complaint;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
