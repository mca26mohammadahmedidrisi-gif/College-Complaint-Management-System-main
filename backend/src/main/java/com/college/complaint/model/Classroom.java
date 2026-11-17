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
@Table(name = "classrooms")
public class Classroom {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor;

    @Column(name = "classroom_number", nullable = false, length = 40)
    private String classroomNumber;

    @Column(name = "classroom_name", nullable = false, length = 120)
    private String classroomName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Classroom() {
    }

    public Classroom(Floor floor, String classroomNumber, String classroomName) {
        this.floor = floor;
        this.classroomNumber = classroomNumber;
        this.classroomName = classroomName;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public Floor getFloor() {
        return floor;
    }

    public String getClassroomNumber() {
        return classroomNumber;
    }

    public String getClassroomName() {
        return classroomName;
    }
}
