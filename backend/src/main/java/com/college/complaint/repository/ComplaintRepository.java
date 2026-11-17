package com.college.complaint.repository;

import com.college.complaint.model.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, UUID> {

    @EntityGraph(attributePaths = {"category", "building", "floor", "classroom"})
    List<Complaint> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"category", "building", "floor", "classroom"})
    List<Complaint> findAllByRemovedAtIsNullOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"category", "building", "floor", "classroom"})
    List<Complaint> findAllByDepartmentIdOrderByCreatedAtDesc(UUID departmentId);

    @EntityGraph(attributePaths = {"category", "building", "floor", "classroom"})
    List<Complaint> findAllByDepartmentIdAndRemovedAtIsNullOrderByCreatedAtDesc(UUID departmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Complaint c where c.complaintId = :complaintId")
    Optional<Complaint> findByIdForUpdate(@Param("complaintId") UUID complaintId);
}
