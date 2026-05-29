package com.nkia.itg.itsm.ticket.service;

import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.itsm.ticket.domain.Priority;
import com.nkia.itg.itsm.ticket.domain.TicketStatus;
import com.nkia.itg.itsm.ticket.dto.TicketAssignRequest;
import com.nkia.itg.itsm.ticket.dto.TicketCreateRequest;
import com.nkia.itg.itsm.ticket.dto.TicketPriorityChangeRequest;
import com.nkia.itg.itsm.ticket.dto.TicketResponse;
import com.nkia.itg.itsm.ticket.dto.TicketStatusChangeRequest;
import com.nkia.itg.itsm.ticket.dto.TicketSummary;
import com.nkia.itg.itsm.ticket.dto.TicketUpdateRequest;
import com.nkia.itg.itsm.ticket.entity.Ticket;
import com.nkia.itg.itsm.ticket.repository.TicketRepository;
import com.nkia.itg.itsm.requesttype.entity.TicketRequestType;
import com.nkia.itg.itsm.requesttype.repository.TicketRequestTypeRepository;
import com.nkia.itg.itsm.workflow.service.WorkflowEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketRequestTypeRepository requestTypeRepository;
    private final WorkflowEngineService workflowEngineService;

    /**
     * 신규 생성. ticket_no 는 ITSM-{id5} 패턴으로 부여 (save 후 dirty checking). 요청 유형에 기본
     * 워크플로우가 지정되어 있으면 WorkflowInstance 를 자동 시작하고 그 id 를 티켓에 연결한다 (ADR-015).
     */
    public TicketResponse create(TicketCreateRequest req) {
        Ticket ticket = Ticket.builder()
                .ticketNo(null)
                .title(req.title())
                .content(req.content())
                .priority(req.priority())
                .status(TicketStatus.OPEN)
                .category(req.category())
                .assigneeId(req.assigneeId())
                .requestTypeCode(req.requestTypeCode())
                .build();
        Ticket saved = ticketRepository.save(ticket);
        saved.assignTicketNo("ITSM-" + String.format("%05d", saved.getId()));
        startWorkflowIfConfigured(saved);
        return TicketResponse.from(saved);
    }

    /**
     * 요청 유형의 default_workflow 가 있으면 워크플로우 인스턴스를 시작한다. 요청 유형이 없거나
     * 기본 워크플로우가 비어 있으면 워크플로우 없이 진행한다 (티켓 생성 자체는 막지 않는다).
     */
    private void startWorkflowIfConfigured(Ticket ticket) {
        String requestTypeCode = ticket.getRequestTypeCode();
        if (requestTypeCode == null || requestTypeCode.isBlank()) {
            return;
        }
        requestTypeRepository.findById(requestTypeCode)
                .map(TicketRequestType::getDefaultWorkflowCode)
                .filter(code -> code != null && !code.isBlank())
                .ifPresent(workflowCode -> workflowEngineService.start(ticket, workflowCode));
    }

    @Transactional(readOnly = true)
    public TicketResponse getById(Long id) {
        return TicketResponse.from(loadOrThrow(id));
    }

    @Transactional(readOnly = true)
    public TicketResponse getByTicketNo(String ticketNo) {
        Ticket ticket = ticketRepository.findByTicketNo(ticketNo)
                .orElseThrow(() -> new ITGException(
                        "TICKET_NOT_FOUND",
                        "티켓을 찾을 수 없습니다: " + ticketNo,
                        HttpStatus.NOT_FOUND));
        return TicketResponse.from(ticket);
    }

    @Transactional(readOnly = true)
    public Page<TicketSummary> search(
            TicketStatus status, Priority priority, String assigneeId,
            int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ticketRepository.search(status, priority, assigneeId, pageable)
                .map(TicketSummary::from);
    }

    /** 본문 수정 (title/content/category 부분). CLOSED 면 도메인 예외. */
    public TicketResponse update(Long id, TicketUpdateRequest req) {
        Ticket ticket = loadOrThrow(id);
        ticket.updateContent(req.title(), req.content(), req.category());
        return TicketResponse.from(ticket);
    }

    /** 상태 전이 (도메인 메서드 호출). 매트릭스 위반 시 IllegalStateException → 400. */
    public TicketResponse changeStatus(Long id, TicketStatusChangeRequest req) {
        Ticket ticket = loadOrThrow(id);
        ticket.changeStatus(req.next());
        return TicketResponse.from(ticket);
    }

    /** 우선순위 변경. CLOSED 면 도메인 예외. */
    public TicketResponse changePriority(Long id, TicketPriorityChangeRequest req) {
        Ticket ticket = loadOrThrow(id);
        ticket.changePriority(req.next());
        return TicketResponse.from(ticket);
    }

    /** 담당자 변경(또는 해제). CLOSED 면 도메인 예외. */
    public TicketResponse assign(Long id, TicketAssignRequest req) {
        Ticket ticket = loadOrThrow(id);
        ticket.assign(req.assigneeId());
        return TicketResponse.from(ticket);
    }

    private Ticket loadOrThrow(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ITGException(
                        "TICKET_NOT_FOUND",
                        "티켓을 찾을 수 없습니다: " + id,
                        HttpStatus.NOT_FOUND));
    }
}
