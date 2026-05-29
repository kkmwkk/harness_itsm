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

    /** 신규 생성. ticket_no 는 ITSM-{id5} 패턴으로 부여 (save 후 dirty checking). */
    public TicketResponse create(TicketCreateRequest req) {
        Ticket ticket = Ticket.builder()
                .ticketNo(null)
                .title(req.title())
                .content(req.content())
                .priority(req.priority())
                .status(TicketStatus.OPEN)
                .category(req.category())
                .assigneeId(req.assigneeId())
                .build();
        Ticket saved = ticketRepository.save(ticket);
        saved.assignTicketNo("ITSM-" + String.format("%05d", saved.getId()));
        return TicketResponse.from(saved);
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
