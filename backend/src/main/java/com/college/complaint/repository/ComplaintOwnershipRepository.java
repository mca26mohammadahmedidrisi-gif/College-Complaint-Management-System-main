package com.college.complaint.repository;

import com.college.complaint.model.ComplaintOwnership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface ComplaintOwnershipRepository extends JpaRepository<ComplaintOwnership, UUID> {

    Optional<ComplaintOwnership> findByComplaint_ComplaintId(UUID complaintId);

    boolean existsByComplaint_ComplaintIdAndUser_UserId(UUID complaintId, UUID userId);

    @EntityGraph(attributePaths = {"complaint.category", "complaint.building", "complaint.floor", "complaint.classroom"})
    List<ComplaintOwnership> findAllByUser_UserIdOrderByCreatedAtDesc(UUID userId);
}
