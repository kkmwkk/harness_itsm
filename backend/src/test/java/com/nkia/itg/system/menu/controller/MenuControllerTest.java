package com.nkia.itg.system.menu.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nkia.itg.auth.service.JwtService;
import com.nkia.itg.common.config.SwaggerConfig;
import com.nkia.itg.common.exception.GlobalExceptionHandler;
import com.nkia.itg.common.security.JwtAuthenticationFilter;
import com.nkia.itg.common.security.SecurityConfig;
import com.nkia.itg.fixture.SystemFixtures;
import com.nkia.itg.system.menu.dto.MenuTreeNode;
import com.nkia.itg.system.menu.service.MenuService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MenuController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, SwaggerConfig.class, JwtAuthenticationFilter.class})
class MenuControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MenuService menuService;

    /** SecurityConfig 가 import 하는 JwtAuthenticationFilter 의 의존성 충족용. */
    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("GET /api/menu — 인증 사용자의 권한 필터된 트리 반환 200")
    @WithMockUser(username = "user", authorities = "TICKET_CREATE")
    void GET_menu_인증_사용자_권한_필터된_트리_반환() throws Exception {
        List<MenuTreeNode> tree = List.of(
                MenuTreeNode.of(SystemFixtures.menu(1L, "MENU-SAMPLE-ITSM", "ITSM", "/itsm", "TICKET_CREATE")));
        given(menuService.getTreeFor(any())).willReturn(tree);

        mockMvc.perform(get("/api/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("MENU-SAMPLE-ITSM"))
                .andExpect(jsonPath("$.data[0].route").value("/itsm"));
    }

    @Test
    @DisplayName("GET /api/menu — 익명 401 AUTH_REQUIRED")
    void GET_menu_익명_401() throws Exception {
        mockMvc.perform(get("/api/menu"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }
}
