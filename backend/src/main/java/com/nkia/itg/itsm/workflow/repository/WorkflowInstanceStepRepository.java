package com.nkia.itg.itsm.workflow.repository;

import com.nkia.itg.itsm.workflow.entity.WorkflowInstanceStep;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowInstanceStepRepository extends JpaRepository<WorkflowInstanceStep, Long> {

    List<WorkflowInstanceStep> findByInstanceIdOrderByStepIndexAscIdAsc(Long instanceId);

    /** 현재 진행 중(미완료) 단계 row — 액션 종료 대상. */
    Optional<WorkflowInstanceStep> findByInstanceIdAndStepIndexAndCompletedAtIsNull(
            Long instanceId, int stepIndex);

    /** SLA 초과 미완료 단계 (배치·polling 용). */
    List<WorkflowInstanceStep> findByCompletedAtIsNullAndSlaDueAtIsNotNullAndSlaDueAtBefore(
            LocalDateTime now);
}
