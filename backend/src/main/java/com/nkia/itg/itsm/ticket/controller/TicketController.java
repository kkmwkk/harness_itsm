package com.nkia.itg.itsm.ticket.controller;

import com.nkia.itg.common.response.ApiResponse;
import com.nkia.itg.common.response.PageResponse;
import com.nkia.itg.itsm.ticket.domain.Priority;
import com.nkia.itg.itsm.ticket.domain.TicketStatus;
import com.nkia.itg.itsm.ticket.dto.TicketAssignRequest;
import com.nkia.itg.itsm.ticket.dto.TicketCreateRequest;
import com.nkia.itg.itsm.ticket.dto.TicketPriorityChangeRequest;
import com.nkia.itg.itsm.ticket.dto.TicketResponse;
import com.nkia.itg.itsm.ticket.dto.TicketStatusChangeRequest;
import com.nkia.itg.itsm.ticket.dto.TicketSummary;
import com.nkia.itg.itsm.ticket.dto.TicketUpdateRequest;
import com.nkia.itg.itsm.ticket.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ITSM Ticket — 티켓 관리",
        description = "ITSM 티켓 접수·조회·상태 전이 API. 물리 삭제는 제공하지 않으며 close 로 종료한다.")
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @Operation(
            summary = "티켓 신규 접수",
            description = "신규 티켓을 생성한다. 상태는 OPEN 으로 시작하고 ticketNo(ITSM-{id5}) 가 자동 부여된다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "접수 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "검증 실패")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<TicketResponse>> create(
            @Valid @RequestBody TicketCreateRequest req
    ) {
        TicketResponse data = ticketService.create(req);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(data, "티켓이 접수되었습니다."));
    }

    @Operation(
            summary = "티켓 목록 검색 (페이지)",
            description = "상태·우선순위·담당자 ID 로 필터링한 티켓 목록을 생성일 내림차순으로 페이지 반환. "
                    + "모든 필터는 선택값이며 미지정 시 전체 대상."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공 (빈 페이지 가능)")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TicketSummary>>> search(
            @Parameter(description = "상태", example = "OPEN")
            @RequestParam(required = false) TicketStatus status,
            @Parameter(description = "우선순위", example = "HIGH")
            @RequestParam(required = false) Priority priority,
            @Parameter(description = "담당자 ID", example = "assignee-sample-1")
            @RequestParam(required = false) String assigneeId,
            @Parameter(description = "페이지 번호 (0부터)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<TicketSummary> result = ticketService.search(status, priority, assigneeId, page, size);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(result)));
    }

    @Operation(
            summary = "티켓 단건 조회 (내부 ID)",
            description = "내부 PK 로 티켓 1건을 조회한다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "티켓 없음")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TicketResponse>> getById(
            @Parameter(description = "티켓 PK", example = "42") @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(ticketService.getById(id)));
    }

    @Operation(
            summary = "티켓 단건 조회 (티켓 번호)",
            description = "표시용 티켓 번호(ITSM-{id5}) 로 티켓 1건을 조회한다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "티켓 없음")
    })
    @GetMapping("/by-no/{ticketNo}")
    public ResponseEntity<ApiResponse<TicketResponse>> getByTicketNo(
            @Parameter(description = "표시용 티켓 번호", example = "ITSM-00042") @PathVariable String ticketNo
    ) {
        return ResponseEntity.ok(ApiResponse.ok(ticketService.getByTicketNo(ticketNo)));
    }

    @Operation(
            summary = "티켓 본문 부분 수정",
            description = "제목·본문·분류만 수정한다. 우선순위·상태·담당자는 별도 엔드포인트로 처리. CLOSED 티켓은 수정 불가."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "CLOSED 티켓 수정 불가 또는 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "티켓 없음")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<TicketResponse>> update(
            @Parameter(description = "티켓 PK", example = "42") @PathVariable Long id,
            @Valid @RequestBody TicketUpdateRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.ok(ticketService.update(id, req)));
    }

    @Operation(
            summary = "티켓 상태 전이",
            description = "허용 전이: OPEN→IN_PROGRESS/RESOLVED/CLOSED, IN_PROGRESS→RESOLVED/CLOSED, "
                    + "RESOLVED→CLOSED/IN_PROGRESS(재오픈), CLOSED→불가."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "전이 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "허용되지 않은 전이"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "티켓 없음")
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<TicketResponse>> changeStatus(
            @Parameter(description = "티켓 PK", example = "42") @PathVariable Long id,
            @Valid @RequestBody TicketStatusChangeRequest req
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(ticketService.changeStatus(id, req), "상태가 변경되었습니다.")
        );
    }

    @Operation(
            summary = "티켓 우선순위 변경",
            description = "우선순위(LOW/MEDIUM/HIGH/CRITICAL) 를 변경한다. CLOSED 티켓은 변경 불가."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "CLOSED 티켓 변경 불가"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "티켓 없음")
    })
    @PatchMapping("/{id}/priority")
    public ResponseEntity<ApiResponse<TicketResponse>> changePriority(
            @Parameter(description = "티켓 PK", example = "42") @PathVariable Long id,
            @Valid @RequestBody TicketPriorityChangeRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.ok(ticketService.changePriority(id, req)));
    }

    @Operation(
            summary = "티켓 담당자 변경/해제",
            description = "담당자 ID 를 변경하거나 null/blank 로 해제한다. CLOSED 티켓은 변경 불가."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "CLOSED 티켓 변경 불가"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "티켓 없음")
    })
    @PatchMapping("/{id}/assign")
    public ResponseEntity<ApiResponse<TicketResponse>> assign(
            @Parameter(description = "티켓 PK", example = "42") @PathVariable Long id,
            @Valid @RequestBody TicketAssignRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.ok(ticketService.assign(id, req)));
    }
}
