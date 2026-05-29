package com.nkia.itg.itsm.workflow.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nkia.itg.auth.service.JwtService;
import com.nkia.itg.common.config.SwaggerConfig;
import com.nkia.itg.common.exception.GlobalExceptionHandler;
import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.common.security.JwtAuthenticationFilter;
import com.nkia.itg.common.security.SecurityConfig;
import com.nkia.itg.itsm.requesttype.domain.StepAction;
import com.nkia.itg.itsm.workflow.dto.WorkflowActionRequest;
import com.nkia.itg.itsm.workflow.dto.WorkflowInstanceResponse;
import com.nkia.itg.itsm.workflow.domain.WorkflowStatus;
import com.nkia.itg.itsm.workflow.service.WorkflowEngineService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SecurityConfig 를 import 하여 인증·권한 분기가 시큐리티 필터 체인을 거치는지 검증한다.
 * 단계 액션의 역할 검증은 WorkflowEngineService 안에서 수행되므로, 여기서는 인증 통과 + 전이 시나리오
 * (정상/전이 불가)와 익명 차단을 확인한다.
 */
@WebMvcTest(WorkflowInstanceController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, SwaggerConfig.class, JwtAuthenticationFilter.class})
class WorkflowInstanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WorkflowEngineService workflowEngineService;

    /** SecurityConfig 가 import 하는 JwtAuthenticationFilter 의 의존성 충족용. */
    @MockBean
    private JwtService jwtService;

    private WorkflowInstanceResponse sampleInstance(int currentStep, WorkflowStatus status) {
        return new WorkflowInstanceResponse(
                100L, "WF_INCIDENT_STD", 42L, currentStep, status,
                LocalDateTime.now(), null, List.of());
    }

    @Test
    @DisplayName("GET /api/workflow-instances/by-ticket/{ticketId} — 인증 사용자 200")
    @WithMockUser(username = "it-support", authorities = "ROLE_IT_SUPPORT")
    void GET_by_ticket_인증_200() throws Exception {
        given(workflowEngineService.getByTicket(42L)).willReturn(sampleInstance(0, WorkflowStatus.RUNNING));

        mockMvc.perform(get("/api/workflow-instances/by-ticket/{ticketId}", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.ticketId").value(42))
                .andExpect(jsonPath("$.data.status").value("RUNNING"));
    }

    @Test
    @DisplayName("GET /api/workflow-instances/by-ticket/{ticketId} — 익명 401 AUTH_REQUIRED")
    void GET_by_ticket_익명_401() throws Exception {
        mockMvc.perform(get("/api/workflow-instances/by-ticket/{ticketId}", 42L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    @DisplayName("POST /{id}/step/{idx}/action — 담당 역할 보유 사용자 액션 200")
    @WithMockUser(username = "team-lead", authorities = "ROLE_TEAM_LEAD")
    void POST_action_담당_역할_200() throws Exception {
        given(workflowEngineService.executeActionAndLoad(eq(100L), eq(0), eq(StepAction.APPROVE), any(), any(), any()))
                .willReturn(sampleInstance(1, WorkflowStatus.RUNNING));

        mockMvc.perform(post("/api/workflow-instances/{id}/step/{idx}/action", 100L, 0)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkflowActionRequest(StepAction.APPROVE, "승인"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("단계 액션이 처리되었습니다."))
                .andExpect(jsonPath("$.data.currentStepIndex").value(1));
    }

    @Test
    @DisplayName("POST /{id}/step/{idx}/action — 역할 미보유·전이 불가는 400 INVALID_WORKFLOW_ACTION")
    @WithMockUser(username = "user", authorities = "ROLE_USER")
    void POST_action_역할_미보유_400() throws Exception {
        given(workflowEngineService.executeActionAndLoad(anyLong(), anyInt(), any(), any(), any(), any()))
                .willThrow(new ITGException("INVALID_WORKFLOW_ACTION",
                        "단계 담당 역할(ROLE_TEAM_LEAD)이 없어 액션할 수 없습니다.", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/workflow-instances/{id}/step/{idx}/action", 100L, 0)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkflowActionRequest(StepAction.APPROVE, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_WORKFLOW_ACTION"));
    }

    @Test
    @DisplayName("POST /{id}/step/{idx}/action — action 누락 시 400 VALIDATION_FAILED")
    @WithMockUser(username = "team-lead", authorities = "ROLE_TEAM_LEAD")
    void POST_action_누락_400_VALIDATION_FAILED() throws Exception {
        mockMvc.perform(post("/api/workflow-instances/{id}/step/{idx}/action", 100L, 0)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"코멘트만\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }
}
