package com.nkia.itg.meta.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.nkia.itg.meta.domain.MetaStatus;
import com.nkia.itg.meta.domain.PackageType;
import com.nkia.itg.meta.domain.SystemType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * 메타 검증(dry-run) 요청 DTO.
 *
 * <p>generate_meta.py 산출물은 {@code api/grid/form/actions} 본문을 최상위에 평탄화하여 내보낸다
 * (ARCHITECTURE §3-4 예시). 이 평탄화 형태를 그대로 받기 위해 {@link JsonAnySetter} 로
 * 분류 축(id/title/systemType/.../metaStatus) 외의 최상위 키를 {@code metaJson} 으로 수집한다.
 */
@Schema(description = "PageMeta 검증(dry-run) 요청 — DB INSERT 는 일어나지 않는다")
public record PageMetaCreateRequest(
        @Schema(description = "메타 ID ({groupId}-v{major}-{minor})", example = "itg-ticket-v1-1")
        String id,

        @Schema(description = "페이지 타이틀", example = "ITSM 티켓 관리")
        String title,

        @Schema(description = "시스템 모듈 구분", example = "ITSM")
        SystemType systemType,

        @Schema(description = "패키지 구분 (본사 기본 / 고객사 커스터마이징)", example = "PACKAGE")
        PackageType packageType,

        @Schema(description = "페이지 그룹 ID", example = "itg-ticket")
        String groupId,

        @Schema(description = "메이저 버전", example = "1")
        Integer majorVersion,

        @Schema(description = "마이너 버전", example = "1")
        Integer minorVersion,

        @Schema(description = "메타 상태 (dry-run 권장값은 DRAFT)", example = "DRAFT")
        MetaStatus metaStatus,

        @Schema(
                description = "메타 본문 (api / grid / form / detail / actions). "
                        + "최상위 평탄화 형태 또는 metaJson 래퍼 형태 모두 허용.",
                example = "{\"api\":\"/api/tickets\",\"grid\":{\"columns\":[]},"
                        + "\"form\":{\"layout\":\"two-column\",\"fields\":[]}}"
        )
        @JsonAnySetter
        Map<String, Object> metaJson
) {
}
