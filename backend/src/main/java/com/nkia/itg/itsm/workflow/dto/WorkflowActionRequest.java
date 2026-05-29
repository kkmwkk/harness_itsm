package com.nkia.itg.itsm.workflow.dto;

import com.nkia.itg.itsm.requesttype.domain.StepAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "워크플로우 단계 액션 요청")
public record WorkflowActionRequest(
        @Schema(description = "액션 (APPROVE/REJECT/FORWARD/COMPLETE/CONFIRM/REOPEN)", example = "APPROVE")
        @NotNull StepAction action,

        @Schema(description = "액션 코멘트 (옵션)", example = "승인합니다")
        String comment
) {}
