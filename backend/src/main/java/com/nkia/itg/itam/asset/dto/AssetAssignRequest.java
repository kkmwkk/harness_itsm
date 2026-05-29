package com.nkia.itg.itam.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "자산 담당자 할당(또는 해제) 요청")
public record AssetAssignRequest(
        @Schema(description = "담당자 ID (null 이면 해제)", example = "assignee-sample-2")
        String assigneeId
) {}
