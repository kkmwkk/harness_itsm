package com.nkia.itg.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SwaggerConfigTest {

    private final OpenAPI openAPI = new SwaggerConfig().openAPI();

    @Test
    @DisplayName("OpenAPI info 의 title 과 version 이 PRD 규격을 따른다")
    void infoMatchesContract() {
        assertThat(openAPI.getInfo().getTitle()).isEqualTo("ITG No-code Platform API");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("v2.0.0");
    }

    @Test
    @DisplayName("contact email 은 example.com 도메인이며 실제 운영 메일이 들어있지 않다")
    void contactEmailUsesExampleDomain() {
        String email = openAPI.getInfo().getContact().getEmail();
        assertThat(email).endsWith("@example.com");
    }

    @Test
    @DisplayName("bearerAuth SecurityScheme 이 등록되어 있다")
    void bearerAuthSchemeRegistered() {
        SecurityScheme bearer = openAPI.getComponents().getSecuritySchemes().get("bearerAuth");
        assertThat(bearer).isNotNull();
        assertThat(bearer.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(bearer.getScheme()).isEqualTo("bearer");
        assertThat(bearer.getBearerFormat()).isEqualTo("JWT");
    }
}
