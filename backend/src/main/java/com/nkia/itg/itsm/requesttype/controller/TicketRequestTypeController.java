package com.nkia.itg.itsm.requesttype.controller;

import com.nkia.itg.common.response.ApiResponse;
import com.nkia.itg.itsm.requesttype.dto.TicketRequestTypeCreateRequest;
import com.nkia.itg.itsm.requesttype.dto.TicketRequestTypeResponse;
import com.nkia.itg.itsm.requesttype.dto.TicketRequestTypeUpdateRequest;
import com.nkia.itg.itsm.requesttype.service.TicketRequestTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ITSM RequestType — 요청 유형",
        description = "티켓 요청 유형(장애·서비스요청·변경·문제·QnA) 관리. 조회는 인증만, 생성·수정은 ROLE_ADMIN.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/ticket-request-types")
@RequiredArgsConstructor
public class TicketRequestTypeController {

    private final TicketRequestTypeService requestTypeService;

    @Operation(summary = "활성 요청 유형 목록", description = "활성(active=true) 요청 유형을 코드순으로 반환한다. 인증만 필요.")
    @GetMapping
    public ApiResponse<List<TicketRequestTypeResponse>> findActive() {
        return ApiResponse.ok(requestTypeService.findActive());
    }

    @Operation(summary = "요청 유형 단건 조회", description = "code 로 요청 유형 1건을 조회한다. 인증만 필요.")
    @GetMapping("/{code}")
    public ApiResponse<TicketRequestTypeResponse> getByCode(
            @Parameter(description = "요청 유형 코드", example = "INCIDENT") @PathVariable String code) {
        return ApiResponse.ok(requestTypeService.getByCode(code));
    }

    @Operation(summary = "요청 유형 생성 (관리자)", description = "ROLE_ADMIN 필요. code 중복 시 REQUEST_TYPE_DUPLICATE.")
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<TicketRequestTypeResponse>> create(
            @Valid @RequestBody TicketRequestTypeCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(requestTypeService.create(req), "요청 유형이 생성되었습니다."));
    }

    @Operation(summary = "요청 유형 수정 (관리자)", description = "ROLE_ADMIN 필요. code 는 불변.")
    @PatchMapping("/{code}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ApiResponse<TicketRequestTypeResponse> update(
            @Parameter(description = "요청 유형 코드", example = "INCIDENT") @PathVariable String code,
            @Valid @RequestBody TicketRequestTypeUpdateRequest req) {
        return ApiResponse.ok(requestTypeService.update(code, req));
    }
}
