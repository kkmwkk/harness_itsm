package com.nkia.itg.system.menu.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "메뉴 신규 생성 요청")
public record MenuCreateRequest(
        @Schema(description = "메뉴 코드", example = "MENU-SAMPLE-ITSM")
        @NotBlank @Size(max = 60) String code,

        @Schema(description = "라벨", example = "ITSM")
        @NotBlank @Size(max = 100) String label,

        @Schema(description = "상위 메뉴 ID (루트면 null)", example = "1")
        Long parentId,

        @Schema(description = "lucide 아이콘명", example = "BoxesIcon")
        @Size(max = 60) String icon,

        @Schema(description = "정렬 순서", example = "0")
        int sortOrder,

        @Schema(description = "라우트 경로", example = "/itsm")
        @Size(max = 200) String route,

        @Schema(description = "PageMeta group 키 (옵션)", example = "itg-ticket")
        @Size(max = 100) String groupId,

        @Schema(description = "필요 권한 코드 (null 이면 누구나)", example = "TICKET_CREATE")
        @Size(max = 60) String permissionCode
) {}
