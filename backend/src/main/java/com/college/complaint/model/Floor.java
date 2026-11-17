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
@Table(name = "floors")
public class Floor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "floor_id", nullable = false, updatable = false)
    private UUID floorId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @Column(name = "floor_number", nullable = false)
    private Integer floorNumber;

    @Column(name = "floor_name", nullable = false, length = 80)
    private String floorName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Floor() {
    }

    public Floor(Building building, Integer floorNumber, String floorName) {
        this.building = building;
        this.floorNumber = floorNumber;
        this.floorName = floorName;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getFloorId() {
        return floorId;
    }

    public Building getBuilding() {
        return building;
    }

    public Integer getFloorNumber() {
        return floorNumber;
    }

    public String getFloorName() {
        return floorName;
    }
}
