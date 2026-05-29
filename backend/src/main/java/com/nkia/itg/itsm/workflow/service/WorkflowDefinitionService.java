package com.nkia.itg.itsm.workflow.service;

import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.itsm.requesttype.domain.StepAction;
import com.nkia.itg.itsm.workflow.dto.WorkflowDefinitionCreateRequest;
import com.nkia.itg.itsm.workflow.dto.WorkflowDefinitionResponse;
import com.nkia.itg.itsm.workflow.dto.WorkflowDefinitionUpdateRequest;
import com.nkia.itg.itsm.workflow.entity.WorkflowDefinition;
import com.nkia.itg.itsm.workflow.repository.WorkflowDefinitionRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 워크플로우 정의 CRUD (ADR-015). code 가 PK 이며 불변. steps JSONB 는 저장 전 형식 검증한다 —
 * 각 단계는 name·allowed_actions 를 가져야 하고, allowed_actions 의 값은 {@link StepAction} Enum 이어야 한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class WorkflowDefinitionService {

    private static final String STEP_NAME = "name";
    private static final String STEP_ALLOWED_ACTIONS = "allowed_actions";

    private final WorkflowDefinitionRepository definitionRepository;

    @Transactional(readOnly = true)
    public List<WorkflowDefinitionResponse> findAll() {
        return definitionRepository.findAll().stream()
                .map(WorkflowDefinitionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkflowDefinitionResponse getByCode(String code) {
        return WorkflowDefinitionResponse.from(loadOrThrow(code));
    }

    public WorkflowDefinitionResponse create(WorkflowDefinitionCreateRequest req) {
        if (definitionRepository.existsById(req.code())) {
            throw new ITGException("WORKFLOW_DEF_DUPLICATE",
                    "이미 존재하는 워크플로우 정의 코드입니다: " + req.code());
        }
        validateSteps(req.steps());
        WorkflowDefinition saved = definitionRepository.save(WorkflowDefinition.builder()
                .code(req.code())
                .name(req.name())
                .version(req.version())
                .steps(req.steps())
                .active(true)
                .build());
        return WorkflowDefinitionResponse.from(saved);
    }

    public WorkflowDefinitionResponse update(String code, WorkflowDefinitionUpdateRequest req) {
        WorkflowDefinition entity = loadOrThrow(code);
        validateSteps(req.steps());
        entity.update(req.name(), req.version(), req.steps(), req.active());
        return WorkflowDefinitionResponse.from(entity);
    }

    /** steps JSONB 형식 검증. 비거나 단계에 name·유효 allowed_actions 가 없으면 400. */
    private void validateSteps(List<Map<String, Object>> steps) {
        if (steps == null || steps.isEmpty()) {
            throw invalidDefinition("워크플로우 단계가 비어 있습니다.");
        }
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            Object name = step.get(STEP_NAME);
            if (name == null || name.toString().isBlank()) {
                throw invalidDefinition("단계 " + i + " 의 name 이 비어 있습니다.");
            }
            Object actions = step.get(STEP_ALLOWED_ACTIONS);
            if (!(actions instanceof List<?> list) || list.isEmpty()) {
                throw invalidDefinition("단계 " + i + " 의 allowed_actions 가 비어 있습니다.");
            }
            for (Object action : list) {
                assertValidAction(i, action);
            }
        }
    }

    private void assertValidAction(int stepIndex, Object action) {
        try {
            StepAction.valueOf(String.valueOf(action));
        } catch (IllegalArgumentException ex) {
            throw invalidDefinition("단계 " + stepIndex + " 의 허용되지 않은 액션입니다: " + action);
        }
    }

    private ITGException invalidDefinition(String message) {
        return new ITGException("INVALID_WORKFLOW_DEFINITION", message, HttpStatus.BAD_REQUEST);
    }

    private WorkflowDefinition loadOrThrow(String code) {
        return definitionRepository.findById(code)
                .orElseThrow(() -> new ITGException("WORKFLOW_DEF_NOT_FOUND",
                        "워크플로우 정의를 찾을 수 없습니다: " + code, HttpStatus.NOT_FOUND));
    }
}
