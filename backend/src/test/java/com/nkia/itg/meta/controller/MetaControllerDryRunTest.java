package com.nkia.itg.meta.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nkia.itg.auth.service.JwtService;
import com.nkia.itg.common.config.SwaggerConfig;
import com.nkia.itg.common.exception.GlobalExceptionHandler;
import com.nkia.itg.common.security.JwtAuthenticationFilter;
import com.nkia.itg.common.security.SecurityConfig;
import com.nkia.itg.meta.service.MetaService;
import com.nkia.itg.meta.service.MetaValidationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MetaController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, SwaggerConfig.class,
        JwtAuthenticationFilter.class, MetaValidationService.class})
@WithMockUser
class MetaControllerDryRunTest {

    @Autowired
    private MockMvc mockMvc;

    /** dry-run 은 MetaService 를 사용하지 않지만, 컨트롤러 의존성 충족을 위해 mock 으로 주입. */
    @MockBean
    private MetaService metaService;

    /** SecurityConfig 가 import 하는 JwtAuthenticationFilter 의 의존성 충족용. */
    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("POST /api/meta/dry-run — 정상(평탄화) 메타 200 valid=true")
    void POST_dry_run_정상_200_valid_true() throws Exception {
        // generate_meta.py 산출물과 동일하게 api/grid/form/actions 최상위 평탄화 형태
        String body = """
                {
                  "id": "itg-ticket-v1-1",
                  "title": "ITSM 티켓 관리",
                  "systemType": "ITSM",
                  "packageType": "PACKAGE",
                  "groupId": "itg-ticket",
                  "majorVersion": 1,
                  "minorVersion": 1,
                  "metaStatus": "DRAFT",
                  "api": "/api/tickets",
                  "grid": { "columns": [ { "field": "title", "label": "제목", "type": "text" } ] },
                  "form": { "layout": "two-column",
                            "fields": [ { "name": "title", "label": "제목", "type": "text" } ] },
                  "actions": [ { "id": "create", "label": "등록", "type": "dialog-form" } ]
                }
                """;

        mockMvc.perform(post("/api/meta/dry-run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.errorCode").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/meta/dry-run — systemType 누락 200 valid=false issues 표시")
    void POST_dry_run_invalid_200_valid_false_issues_표시() throws Exception {
        String body = """
                {
                  "id": "x-v1-1",
                  "title": "x",
                  "groupId": "x",
                  "majorVersion": 1,
                  "minorVersion": 1,
                  "metaStatus": "DRAFT",
                  "metaJson": { "api": "/api/x",
                                "grid": { "columns": [] },
                                "form": { "layout": "two-column", "fields": [] } }
                }
                """;

        mockMvc.perform(post("/api/meta/dry-run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.issues").isArray())
                .andExpect(jsonPath("$.data.issues[?(@.code=='INVALID_SYSTEM_TYPE')]").exists());
    }
}
