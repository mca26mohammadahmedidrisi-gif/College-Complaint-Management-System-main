package com.college.complaint.repository;

import com.college.complaint.model.ComplaintStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.util.Collection;

public interface ComplaintStatusHistoryRepository extends JpaRepository<ComplaintStatusHistory, UUID> {

    List<ComplaintStatusHistory> findAllByComplaint_ComplaintIdOrderByChangedAtAsc(UUID complaintId);

    List<ComplaintStatusHistory> findAllByComplaint_ComplaintIdInOrderByChangedAtAsc(Collection<UUID> complaintIds);
}
