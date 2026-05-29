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
 * 분류 축(id/title/systemType/.../metaStatus) 외의 최상위 키를 {@code metaBody} 버킷으로 수집한다.
 *
 * <p>버킷 컴포넌트 이름을 {@code metaBody} 로 둔 것은 의도적이다 — 컴포넌트 이름을 {@code metaJson}
 * 으로 두면 No-code 편집기가 보내는 {@code {"metaJson": {...}}} 래퍼 형태의 {@code metaJson} 키가
 * any-setter 의 컴포넌트 이름과 충돌해 Jackson 이 키를 버린다. {@code metaBody} 로 두면 평탄화 형태와
 * 래퍼 형태가 모두 버킷으로 수집되고, {@link #resolvedMetaJson()} 가 두 형태를 동일 본문으로 정규화한다.
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
        Map<String, Object> metaBody
) {

    /**
     * 검증·저장에 사용할 metaJson 본문을 형태에 무관하게 정규화한다.
     *
     * <p>평탄화 형태({@code {"api":..,"grid":..}})는 그대로 사용하고,
     * {@code metaJson} 래퍼 형태({@code {"metaJson": {...}}})로 들어온 경우 한 겹 벗긴다.
     * {@link JsonAnySetter} 가 최상위 키를 수집하는 방식이 Jackson 버전·바인딩에 따라
     * 한 겹 더 감싸질 수 있어, 두 경우 모두 안전하게 같은 본문을 돌려준다.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> resolvedMetaJson() {
        if (metaBody == null) {
            return null;
        }
        if (metaBody.size() == 1 && metaBody.get("metaJson") instanceof Map<?, ?> inner) {
            return (Map<String, Object>) inner;
        }
        return metaBody;
    }
}
