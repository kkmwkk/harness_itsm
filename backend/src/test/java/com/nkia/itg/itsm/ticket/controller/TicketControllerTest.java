package com.nkia.itg.itsm.ticket.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nkia.itg.common.exception.GlobalExceptionHandler;
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
import com.nkia.itg.itsm.ticket.service.TicketService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TicketController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class TicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TicketService ticketService;

    /** @Component 인 JwtAuthenticationFilter 가 슬라이스에 로드되므로 그 의존성(JwtService) 충족용. */
    @MockBean
    private com.nkia.itg.auth.service.JwtService jwtService;

    private TicketResponse sampleResponse(Long id, TicketStatus status, Priority priority, String assigneeId) {
        return new TicketResponse(
                id,
                "ITSM-%05d".formatted(id),
                "샘플 티켓 제목",
                "샘플 본문",
                priority,
                status,
                "BUG",
                assigneeId,
                LocalDateTime.now(),
                LocalDateTime.now(),
                null
        );
    }

    @Test
    @DisplayName("POST /api/tickets — 201 과 TicketResponse 반환")
    void POST_create_201_과_TicketResponse_반환() throws Exception {
        TicketCreateRequest req = new TicketCreateRequest(
                "샘플 티켓 제목", "샘플 본문", Priority.MEDIUM, "BUG", "assignee-sample-1");
        given(ticketService.create(any())).willReturn(sampleResponse(42L, TicketStatus.OPEN, Priority.MEDIUM, "assignee-sample-1"));

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.ticketNo").value("ITSM-00042"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.errorCode").doesNotExist());

        then(ticketService).should().create(any());
    }

    @Test
    @DisplayName("POST /api/tickets — title 누락 시 400 VALIDATION_FAILED")
    void POST_create_title_누락_400_VALIDATION_FAILED() throws Exception {
        TicketCreateRequest req = new TicketCreateRequest(
                "  ", "샘플 본문", Priority.MEDIUM, "BUG", "assignee-sample-1");

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        then(ticketService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("GET /api/tickets/{id} — 200 정상")
    void GET_by_id_200_정상() throws Exception {
        given(ticketService.getById(42L)).willReturn(sampleResponse(42L, TicketStatus.OPEN, Priority.MEDIUM, "assignee-sample-1"));

        mockMvc.perform(get("/api/tickets/{id}", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.ticketNo").value("ITSM-00042"));

        then(ticketService).should().getById(42L);
    }

    @Test
    @DisplayName("GET /api/tickets/{id} — 없으면 404 TICKET_NOT_FOUND")
    void GET_by_id_없으면_404_TICKET_NOT_FOUND() throws Exception {
        given(ticketService.getById(999L))
                .willThrow(new ITGException("TICKET_NOT_FOUND", "티켓을 찾을 수 없습니다: 999", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/tickets/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("TICKET_NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/tickets — status·priority·assignee 파라미터가 Service 에 전달")
    void GET_search_파라미터_Service_에_전달() throws Exception {
        Page<TicketSummary> empty = new PageImpl<>(List.of(), PageRequest.of(2, 50), 0);
        given(ticketService.search(any(), any(), any(), eq(2), eq(50))).willReturn(empty);

        mockMvc.perform(get("/api/tickets")
                        .param("status", "OPEN")
                        .param("priority", "HIGH")
                        .param("assigneeId", "assignee-sample-1")
                        .param("page", "2")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.number").value(2))
                .andExpect(jsonPath("$.data.size").value(50));

        ArgumentCaptor<TicketStatus> statusCaptor = ArgumentCaptor.forClass(TicketStatus.class);
        ArgumentCaptor<Priority> priorityCaptor = ArgumentCaptor.forClass(Priority.class);
        ArgumentCaptor<String> assigneeCaptor = ArgumentCaptor.forClass(String.class);
        then(ticketService).should()
                .search(statusCaptor.capture(), priorityCaptor.capture(), assigneeCaptor.capture(), eq(2), eq(50));
        assertThat(statusCaptor.getValue()).isEqualTo(TicketStatus.OPEN);
        assertThat(priorityCaptor.getValue()).isEqualTo(Priority.HIGH);
        assertThat(assigneeCaptor.getValue()).isEqualTo("assignee-sample-1");
    }

    @Test
    @DisplayName("GET /api/tickets — 파라미터 없으면 기본 page 0 size 20")
    void GET_search_기본_page_0_size_20() throws Exception {
        Page<TicketSummary> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        given(ticketService.search(isNull(), isNull(), isNull(), eq(0), eq(20))).willReturn(empty);

        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.size").value(20));

        then(ticketService).should().search(isNull(), isNull(), isNull(), eq(0), eq(20));
    }

    @Test
    @DisplayName("PATCH /api/tickets/{id}/status — 200 과 메시지 '상태가 변경되었습니다.'")
    void PATCH_status_200_과_메시지_상태가_변경되었습니다() throws Exception {
        given(ticketService.changeStatus(eq(42L), any()))
                .willReturn(sampleResponse(42L, TicketStatus.IN_PROGRESS, Priority.MEDIUM, "assignee-sample-1"));

        mockMvc.perform(patch("/api/tickets/{id}/status", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TicketStatusChangeRequest(TicketStatus.IN_PROGRESS))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("상태가 변경되었습니다."))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        then(ticketService).should().changeStatus(eq(42L), any());
    }

    @Test
    @DisplayName("PATCH /api/tickets/{id}/status — 허용되지 않은 전이는 400 INVALID_REQUEST")
    void PATCH_status_허용되지_않은_전이_400_INVALID_REQUEST() throws Exception {
        given(ticketService.changeStatus(eq(42L), any()))
                .willThrow(new IllegalStateException("허용되지 않은 상태 전이입니다: CLOSED → OPEN"));

        mockMvc.perform(patch("/api/tickets/{id}/status", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TicketStatusChangeRequest(TicketStatus.OPEN))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("PATCH /api/tickets/{id}/priority — CLOSED 티켓 변경은 400")
    void PATCH_priority_CLOSED_400() throws Exception {
        given(ticketService.changePriority(eq(42L), any()))
                .willThrow(new IllegalStateException("CLOSED 티켓은 변경할 수 없습니다."));

        mockMvc.perform(patch("/api/tickets/{id}/priority", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TicketPriorityChangeRequest(Priority.HIGH))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("PATCH /api/tickets/{id}/assign — assignee null 허용 (해제)")
    void PATCH_assign_assignee_null_허용() throws Exception {
        given(ticketService.assign(eq(42L), any()))
                .willReturn(sampleResponse(42L, TicketStatus.OPEN, Priority.MEDIUM, null));

        mockMvc.perform(patch("/api/tickets/{id}/assign", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TicketAssignRequest(null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assigneeId").doesNotExist());

        then(ticketService).should().assign(eq(42L), any());
    }

    @Test
    @DisplayName("PATCH /api/tickets/{id} — 본문 부분 업데이트 200")
    void PATCH_update_부분_업데이트_200() throws Exception {
        TicketResponse updated = new TicketResponse(
                42L, "ITSM-00042", "수정된 샘플 제목", "수정된 샘플 본문",
                Priority.MEDIUM, TicketStatus.OPEN, "REQ", "assignee-sample-1",
                LocalDateTime.now(), LocalDateTime.now(), null);
        given(ticketService.update(eq(42L), any())).willReturn(updated);

        mockMvc.perform(patch("/api/tickets/{id}", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TicketUpdateRequest("수정된 샘플 제목", "수정된 샘플 본문", "REQ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("수정된 샘플 제목"))
                .andExpect(jsonPath("$.data.category").value("REQ"));

        then(ticketService).should().update(eq(42L), any());
    }

    @Test
    @DisplayName("GET /api/tickets/by-no/{ticketNo} — 200")
    void GET_by_no_200() throws Exception {
        given(ticketService.getByTicketNo("ITSM-00042"))
                .willReturn(sampleResponse(42L, TicketStatus.OPEN, Priority.MEDIUM, "assignee-sample-1"));

        mockMvc.perform(get("/api/tickets/by-no/{ticketNo}", "ITSM-00042"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ticketNo").value("ITSM-00042"));

        then(ticketService).should().getByTicketNo("ITSM-00042");
    }
}
