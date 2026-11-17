package com.college.complaint.repository;

import com.college.complaint.model.ComplaintStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.UUID;
import java.util.Collection;

public interface ComplaintStatusHistoryRepository extends JpaRepository<ComplaintStatusHistory, UUID> {

    @EntityGraph(attributePaths = {"changedBy"})
    List<ComplaintStatusHistory> findAllByComplaint_ComplaintIdOrderByChangedAtAsc(UUID complaintId);

    @EntityGraph(attributePaths = {"changedBy"})
    List<ComplaintStatusHistory> findAllByComplaint_ComplaintIdInOrderByChangedAtAsc(Collection<UUID> complaintIds);
}
