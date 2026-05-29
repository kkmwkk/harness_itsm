package com.nkia.itg.itsm.workflow.repository;

import com.nkia.itg.itsm.workflow.entity.WorkflowInstance;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, Long> {

    Optional<WorkflowInstance> findByTicketId(Long ticketId);
}
