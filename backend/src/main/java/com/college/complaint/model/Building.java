package com.college.complaint.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "buildings")
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "building_id", nullable = false, updatable = false)
    private UUID buildingId;

    @Column(name = "building_name", nullable = false, unique = true, length = 120)
    private String buildingName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Building() {
    }

    public Building(String buildingName) {
        this.buildingName = buildingName;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getBuildingId() {
        return buildingId;
    }

    public String getBuildingName() {
        return buildingName;
    }
}
