package com.college.complaint.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "departments")
public class Department {

    @Id
    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "department_name", nullable = false, length = 120)
    private String departmentName;

    protected Department() {
    }

    public Department(UUID departmentId, String departmentName) {
        this.departmentId = departmentId;
        this.departmentName = departmentName;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }
}
