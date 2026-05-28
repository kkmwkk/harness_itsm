package com.nkia.itg.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nkia.itg.common.exception.helper.ExceptionEmittingController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.hamcrest.Matchers;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ExceptionEmittingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("ITGException 은 지정한 httpStatus 와 errorCode 로 변환된다")
    void itgExceptionMapsToConfiguredStatus() throws Exception {
        mockMvc.perform(post("/__test/exceptions/itg"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("META_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("메타를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("IllegalStateException 은 400 INVALID_REQUEST 로 변환된다")
    void illegalStateMapsToBadRequest() throws Exception {
        mockMvc.perform(post("/__test/exceptions/illegal-state"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("DRAFT only"));
    }

    @Test
    @DisplayName("IllegalArgumentException 도 400 INVALID_REQUEST 로 변환된다")
    void illegalArgumentMapsToBadRequest() throws Exception {
        mockMvc.perform(post("/__test/exceptions/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("DataIntegrityViolationException 은 409 DATA_INTEGRITY 로 변환된다")
    void dataIntegrityMapsToConflict() throws Exception {
        mockMvc.perform(post("/__test/exceptions/data-integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("DATA_INTEGRITY"));
    }

    @Test
    @DisplayName("@Valid 실패는 400 VALIDATION_FAILED 로 변환된다")
    void validationMapsToBadRequest() throws Exception {
        mockMvc.perform(post("/__test/exceptions/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("알 수 없는 예외는 500 INTERNAL_ERROR 이며 stack trace 를 응답에 노출하지 않는다")
    void unknownMapsToInternalError() throws Exception {
        mockMvc.perform(post("/__test/exceptions/unknown"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("내부 오류"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("RuntimeException"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("boom"))));
    }
}
