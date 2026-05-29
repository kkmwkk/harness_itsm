package com.nkia.itg.system.menu.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "메뉴 정보 수정 요청 (트리 이동은 별도 move API)")
public record MenuUpdateRequest(
        @Schema(description = "라벨", example = "ITSM")
        @Size(max = 100) String label,

        @Schema(description = "lucide 아이콘명", example = "BoxesIcon")
        @Size(max = 60) String icon,

        @Schema(description = "라우트 경로", example = "/itsm")
        @Size(max = 200) String route,

        @Schema(description = "PageMeta group 키 (옵션)", example = "itg-ticket")
        @Size(max = 100) String groupId,

        @Schema(description = "필요 권한 코드 (null 이면 누구나)", example = "TICKET_CREATE")
        @Size(max = 60) String permissionCode
) {}
