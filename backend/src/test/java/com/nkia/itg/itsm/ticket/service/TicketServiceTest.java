package com.nkia.itg.itsm.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.itsm.ticket.domain.Priority;
import com.nkia.itg.itsm.ticket.domain.TicketStatus;
import com.nkia.itg.itsm.ticket.dto.TicketAssignRequest;
import com.nkia.itg.itsm.ticket.dto.TicketCreateRequest;
import com.nkia.itg.itsm.ticket.dto.TicketPriorityChangeRequest;
import com.nkia.itg.itsm.ticket.dto.TicketResponse;
import com.nkia.itg.itsm.ticket.dto.TicketStatusChangeRequest;
import com.nkia.itg.itsm.ticket.dto.TicketUpdateRequest;
import com.nkia.itg.itsm.ticket.entity.Ticket;
import com.nkia.itg.itsm.ticket.repository.TicketRepository;
import com.nkia.itg.itsm.requesttype.repository.TicketRequestTypeRepository;
import com.nkia.itg.itsm.workflow.service.WorkflowEngineService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
@DisplayName("TicketService 단위 테스트 (Mockito)")
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketRequestTypeRepository requestTypeRepository;

    @Mock
    private WorkflowEngineService workflowEngineService;

    @Mock
    private com.nkia.itg.system.notification.service.NotificationService notificationService;

    @InjectMocks
    private TicketService ticketService;

    private Ticket ticketWith(Long id, TicketStatus status) {
        return Ticket.builder()
                .id(id)
                .ticketNo("ITSM-" + String.format("%05d", id))
                .title("샘플 티켓")
                .content("샘플 본문")
                .priority(Priority.MEDIUM)
                .status(status)
                .category("BUG")
                .assigneeId("assignee-sample-1")
                .build();
    }

    @Test
    @DisplayName("create — ticket_no 는 ITSM-{id5} 패턴으로 부여")
    void create_ticket_no_는_ITSM_5자리_패턴() {
        // given — save 가 id=42 채워진 (ticket_no 미부여) 영속 객체 반환
        Ticket persisted = spy(Ticket.builder()
                .id(42L)
                .ticketNo(null)
                .title("샘플 티켓 제목")
                .content("샘플 본문")
                .priority(Priority.MEDIUM)
                .status(TicketStatus.OPEN)
                .category("BUG")
                .assigneeId("assignee-sample-1")
                .build());
        when(ticketRepository.save(any(Ticket.class))).thenReturn(persisted);

        TicketCreateRequest req = new TicketCreateRequest(
                "샘플 티켓 제목", "샘플 본문", Priority.MEDIUM, "BUG", "assignee-sample-1", "INCIDENT");

        // when
        TicketResponse result = ticketService.create(req);

        // then
        verify(persisted).assignTicketNo("ITSM-00042");
        assertThat(result.ticketNo()).isEqualTo("ITSM-00042");
    }

    @Test
    @DisplayName("create — status 기본 OPEN, closedAt null")
    void create_status_기본_OPEN_closedAt_null() {
        // given
        Ticket persisted = Ticket.builder()
                .id(1L)
                .ticketNo(null)
                .title("샘플 티켓 제목")
                .priority(Priority.MEDIUM)
                .status(TicketStatus.OPEN)
                .build();
        when(ticketRepository.save(any(Ticket.class))).thenReturn(persisted);
        lenient().when(requestTypeRepository.findById(any())).thenReturn(Optional.empty());

        TicketCreateRequest req = new TicketCreateRequest(
                "샘플 티켓 제목", null, Priority.MEDIUM, null, null, "INCIDENT");

        // when
        TicketResponse result = ticketService.create(req);

        // then
        assertThat(result.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(result.closedAt()).isNull();
    }

    @Test
    @DisplayName("create — 요청 유형에 기본 워크플로우가 있으면 WorkflowEngineService.start 호출")
    void create_요청_유형_기본_워크플로우_있으면_워크플로우_시작() {
        // given
        Ticket persisted = Ticket.builder()
                .id(7L).ticketNo(null).title("샘플").priority(Priority.MEDIUM)
                .status(TicketStatus.OPEN).requestTypeCode("INCIDENT").build();
        when(ticketRepository.save(any(Ticket.class))).thenReturn(persisted);
        when(requestTypeRepository.findById("INCIDENT")).thenReturn(Optional.of(
                com.nkia.itg.itsm.requesttype.entity.TicketRequestType.builder()
                        .code("INCIDENT").label("장애").defaultWorkflowCode("WF_INCIDENT_STD")
                        .active(true).build()));

        TicketCreateRequest req = new TicketCreateRequest(
                "샘플", null, Priority.MEDIUM, null, null, "INCIDENT");

        // when
        ticketService.create(req);

        // then
        verify(workflowEngineService).start(persisted, "WF_INCIDENT_STD");
    }

    @Test
    @DisplayName("create — 요청 유형에 기본 워크플로우가 없으면 워크플로우 미시작")
    void create_요청_유형_기본_워크플로우_없으면_미시작() {
        // given
        Ticket persisted = Ticket.builder()
                .id(8L).ticketNo(null).title("샘플").priority(Priority.MEDIUM)
                .status(TicketStatus.OPEN).requestTypeCode("QNA").build();
        when(ticketRepository.save(any(Ticket.class))).thenReturn(persisted);
        when(requestTypeRepository.findById("QNA")).thenReturn(Optional.of(
                com.nkia.itg.itsm.requesttype.entity.TicketRequestType.builder()
                        .code("QNA").label("QnA").defaultWorkflowCode(null).active(true).build()));

        TicketCreateRequest req = new TicketCreateRequest(
                "샘플", null, Priority.MEDIUM, null, null, "QNA");

        // when
        ticketService.create(req);

        // then
        org.mockito.BDDMockito.then(workflowEngineService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("getById — 없으면 TICKET_NOT_FOUND (404)")
    void getById_없으면_TICKET_NOT_FOUND_404() {
        // given
        when(ticketRepository.findById(99L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ticketService.getById(99L))
                .isInstanceOf(ITGException.class)
                .satisfies(ex -> {
                    ITGException itg = (ITGException) ex;
                    assertThat(itg.getErrorCode()).isEqualTo("TICKET_NOT_FOUND");
                    assertThat(itg.getHttpStatus().value()).isEqualTo(404);
                });
    }

    @Test
    @DisplayName("getById — 정상 경로 TicketResponse 반환")
    void getById_정상_경로_TicketResponse_반환() {
        // given
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticketWith(1L, TicketStatus.OPEN)));

        // when
        TicketResponse result = ticketService.getById(1L);

        // then
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.ticketNo()).isEqualTo("ITSM-00001");
        assertThat(result.status()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    @DisplayName("update — CLOSED 티켓은 IllegalStateException")
    void update_CLOSED_티켓_IllegalStateException() {
        // given
        Ticket closed = ticketWith(1L, TicketStatus.CLOSED);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(closed));

        // when & then
        assertThatThrownBy(() -> ticketService.update(1L,
                new TicketUpdateRequest("새 제목", null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLOSED 티켓은 내용을 수정할 수 없습니다");
    }

    @Test
    @DisplayName("update — 부분 업데이트, null 은 변경 없음")
    void update_부분_업데이트_null_은_변경_없음() {
        // given
        Ticket ticket = ticketWith(1L, TicketStatus.OPEN);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        // when — title 만 변경
        TicketResponse result = ticketService.update(1L,
                new TicketUpdateRequest("변경된 제목", null, null));

        // then
        assertThat(result.title()).isEqualTo("변경된 제목");
        assertThat(result.content()).isEqualTo("샘플 본문");
        assertThat(result.category()).isEqualTo("BUG");
    }

    @Test
    @DisplayName("changeStatus — OPEN → RESOLVED 허용")
    void changeStatus_OPEN_to_RESOLVED_허용() {
        // given
        Ticket ticket = ticketWith(1L, TicketStatus.OPEN);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        // when
        TicketResponse result = ticketService.changeStatus(1L,
                new TicketStatusChangeRequest(TicketStatus.RESOLVED));

        // then
        assertThat(result.status()).isEqualTo(TicketStatus.RESOLVED);
    }

    @Test
    @DisplayName("changeStatus — CLOSED → OPEN IllegalStateException")
    void changeStatus_CLOSED_to_OPEN_IllegalStateException() {
        // given
        Ticket closed = ticketWith(1L, TicketStatus.CLOSED);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(closed));

        // when & then
        assertThatThrownBy(() -> ticketService.changeStatus(1L,
                new TicketStatusChangeRequest(TicketStatus.OPEN)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLOSED 티켓은 상태를 변경할 수 없습니다");
    }

    @Test
    @DisplayName("changeStatus — CLOSED 전이 시 closedAt set")
    void changeStatus_to_CLOSED_시_closedAt_set() {
        // given
        Ticket ticket = ticketWith(1L, TicketStatus.OPEN);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        // when
        TicketResponse result = ticketService.changeStatus(1L,
                new TicketStatusChangeRequest(TicketStatus.CLOSED));

        // then
        assertThat(result.status()).isEqualTo(TicketStatus.CLOSED);
        assertThat(result.closedAt()).isNotNull();
    }

    @Test
    @DisplayName("changePriority — CLOSED 불허")
    void changePriority_CLOSED_불허() {
        // given
        Ticket closed = ticketWith(1L, TicketStatus.CLOSED);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(closed));

        // when & then
        assertThatThrownBy(() -> ticketService.changePriority(1L,
                new TicketPriorityChangeRequest(Priority.HIGH)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLOSED 티켓은 우선순위를 변경할 수 없습니다");
    }

    @Test
    @DisplayName("assign — CLOSED 불허")
    void assign_CLOSED_불허() {
        // given
        Ticket closed = ticketWith(1L, TicketStatus.CLOSED);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(closed));

        // when & then
        assertThatThrownBy(() -> ticketService.assign(1L,
                new TicketAssignRequest("assignee-sample-2")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLOSED 티켓은 담당자 할당이 불가합니다");
    }

    @Test
    @DisplayName("assign — assigneeId null 허용 (담당자 해제, CLOSED 아님)")
    void assign_assigneeId_null_허용_담당자_해제() {
        // given
        Ticket ticket = ticketWith(1L, TicketStatus.OPEN);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        // when
        TicketResponse result = ticketService.assign(1L, new TicketAssignRequest(null));

        // then
        assertThat(result.assigneeId()).isNull();
    }

    @Test
    @DisplayName("search — status/priority/assignee 필터를 repository 에 전달")
    void search_status_priority_assignee_필터_repository_에_전달() {
        // given
        when(ticketRepository.search(
                eq(TicketStatus.OPEN), eq(Priority.HIGH), eq("assignee-sample-1"),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(ticketWith(1L, TicketStatus.OPEN))));

        // when
        Page<?> result = ticketService.search(
                TicketStatus.OPEN, Priority.HIGH, "assignee-sample-1", 0, 20);

        // then
        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(ticketRepository).search(
                eq(TicketStatus.OPEN), eq(Priority.HIGH), eq("assignee-sample-1"),
                any(Pageable.class));
    }

    @Test
    @DisplayName("search — Pageable 생성 시 Sort createdAt DESC")
    void search_pageable_생성_Sort_createdAt_DESC() {
        // given
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        when(ticketRepository.search(any(), any(), any(), captor.capture()))
                .thenReturn(new PageImpl<>(java.util.List.of()));

        // when
        ticketService.search(null, null, null, 2, 50);

        // then
        Pageable used = captor.getValue();
        assertThat(used.getPageNumber()).isEqualTo(2);
        assertThat(used.getPageSize()).isEqualTo(50);
        Sort.Order order = used.getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }
}
