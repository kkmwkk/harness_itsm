package com.nkia.itg.itsm.workflow.repository;

import com.nkia.itg.itsm.workflow.entity.WorkflowDefinition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, String> {

    List<WorkflowDefinition> findByActiveTrue();
}
