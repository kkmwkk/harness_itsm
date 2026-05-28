# Step 3: common-foundation

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/CLAUDE.md` — "절대 규칙" (Backend: `ApiResponse<T>` 공통 래퍼, Swagger 어노테이션, 민감정보 금지)
- `/docs/ARCHITECTURE.md` — §2-3 패키지 구조 (`common/{response,exception,security,config}`), §8 인증·인가
- `/docs/ADR.md` — ADR-008 (JWT 자체 인증), ADR-009 (`ApiResponse<T>` 강제), ADR-011 (Swagger 민감정보 금지)
- `/phases/0-meta-backend-m1/step1.md` — `application.yml` 의 SpringDoc 설정
- `/phases/0-meta-backend-m1/step2.md` — Enum/Entity 위치 (예외 처리 시 참고)

이전 step 에서 만들어진 코드를 꼼꼼히 읽고, 패키지 구조와 import 경로를 정확히 맞춰라.

## 작업

이 step 의 목적은 **백엔드의 횡단 관심사(공통 응답 래퍼·예외·Swagger·보안) 를 한 번에 박아두는 것**이다. 이후 Service·Controller step 은 이 공통 레이어를 그대로 사용한다.

### 1. `ApiResponse<T>` — `com.nkia.itg.common.response.ApiResponse`

Java 21 Record 사용. 정적 팩토리 메서드 3종:

```java
package com.nkia.itg.common.response;

public record ApiResponse<T>(
    boolean success,
    T       data,
    String  message,
    String  errorCode
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }
    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, null);
    }
    public static <T> ApiResponse<T> fail(String errorCode, String message) {
        return new ApiResponse<>(false, null, message, errorCode);
    }
}
```

> 이 래퍼는 모든 REST 응답에 적용된다. Controller 는 `ResponseEntity<ApiResponse<T>>` 반환. 외부 시스템 연동을 위한 어댑터는 별도 (이 phase 범위 아님).

### 2. 도메인 예외 — `com.nkia.itg.common.exception.ITGException`

비검사 예외(`RuntimeException` 상속). 에러 코드 + 메시지 + 선택적 HTTP 상태:

```java
package com.nkia.itg.common.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;

@Getter
public class ITGException extends RuntimeException {
    private final String     errorCode;
    private final HttpStatus httpStatus;

    public ITGException(String errorCode, String message) {
        this(errorCode, message, HttpStatus.BAD_REQUEST);
    }
    public ITGException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode  = errorCode;
        this.httpStatus = httpStatus;
    }
}
```

자주 쓸 정적 헬퍼는 만들지 않는다. 호출부에서 명시적으로 `new ITGException("META_NOT_FOUND", "메타를 찾을 수 없습니다.", HttpStatus.NOT_FOUND)` 처럼 쓴다.

### 3. `GlobalExceptionHandler` — `com.nkia.itg.common.exception`

`@RestControllerAdvice` 로 다음을 변환:

- `ITGException` → 해당 `httpStatus` + `ApiResponse.fail(errorCode, message)`
- `MethodArgumentNotValidException` → 400 + `ApiResponse.fail("VALIDATION_FAILED", <필드별 메시지 결합>)`
- `IllegalStateException`·`IllegalArgumentException` → 400 + `ApiResponse.fail("INVALID_REQUEST", message)` (Entity 도메인 예외용)
- `org.springframework.dao.DataIntegrityViolationException` → 409 + `ApiResponse.fail("DATA_INTEGRITY", message)` (UNIQUE 제약 위반 등)
- `Exception` (fallback) → 500 + `ApiResponse.fail("INTERNAL_ERROR", "내부 오류")` + 로깅(stack trace)

> 응답 본문에는 stack trace 노출 금지. 로그에만 남긴다 (`@Slf4j`).

### 4. `SwaggerConfig` — `com.nkia.itg.common.config.SwaggerConfig`

```java
package com.nkia.itg.common.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("ITG No-code Platform API")
                .description("Polestar10 ITG v2 — ITSM · ITAM · PMS REST API")
                .version("v2.0.0")
                .contact(new Contact()
                    .name("ITG Dev Team")
                    .email("dev-team@example.com")))   // example.com 만 사용 — 실 이메일 금지
            .components(new Components()
                .addSecuritySchemes("bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
```

### 5. `SecurityConfig` — `com.nkia.itg.common.security.SecurityConfig`

이 phase 의 보안 정책:
- 비밀번호 발급·로그인 엔드포인트는 **이번 phase 범위 아님**. JWT 검증 필터 골격만 둔다.
- `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health` 는 `permitAll`.
- `/api/meta/**` 는 이번 phase 동안은 `permitAll` (로컬 개발용). 인증·인가 강제는 다음 phase 에서 별도 ADR/구현.
- CSRF 비활성 (REST API), Session `STATELESS`.
- CORS 는 `localhost:5173` (Vite dev) 만 허용하는 기본 설정.

JWT 검증 필터는 클래스 골격만:

```java
package com.nkia.itg.common.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class JwtAuthenticationFilter implements Filter {
    /** TODO(phase-2): Authorization 헤더에서 토큰 추출 후 검증. 이번 phase 는 패스스루. */
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
        throws IOException, ServletException {
        chain.doFilter(req, res);
    }
}
```

`SecurityFilterChain` 빈은 위 정책을 반영한다. 패키지명·import 는 Spring Security 6.x 기준.

### 6. 단위 테스트

#### 6-1. `ApiResponseTest`
- `ok(data)` 결과의 `success=true`, `errorCode=null`.
- `ok(data, "메시지")` 의 `message` 필드.
- `fail("CODE", "메시지")` 의 `success=false`, `data=null`, `errorCode="CODE"`.

#### 6-2. `GlobalExceptionHandlerTest` (`@WebMvcTest` 또는 plain MockMvc)
- 더미 컨트롤러를 테스트 패키지에 만들어 각 예외를 던지게 하고:
  - `ITGException("META_NOT_FOUND", "msg", HttpStatus.NOT_FOUND)` → status 404, body `success=false`, `errorCode="META_NOT_FOUND"`.
  - `IllegalStateException("DRAFT only")` → status 400, `errorCode="INVALID_REQUEST"`.
  - `MethodArgumentNotValidException` → status 400, `errorCode="VALIDATION_FAILED"`.
  - `DataIntegrityViolationException("dup")` → status 409, `errorCode="DATA_INTEGRITY"`.
  - 알 수 없는 `Exception` → 500, `errorCode="INTERNAL_ERROR"`, 응답에 stack 미노출.

> 더미 컨트롤러는 `src/test/java/.../helper/ExceptionEmittingController.java` 처럼 테스트 트리에만 둔다. 운영 코드와 섞지 마라.

#### 6-3. `SwaggerConfigTest`
- `OpenAPI` 빈이 컨텍스트에 존재.
- `info.title` 이 "ITG No-code Platform API", `info.version` 이 `"v2.0.0"`.
- `components.securitySchemes` 에 `bearerAuth` 가 등록되어 있음.
- `info.contact.email` 이 `example.com` 도메인. 실 도메인 금지.

### 7. 실 부팅 확인

- `bootRun` 으로 Spring Boot 가 뜨면 `/swagger-ui.html` (또는 `/swagger-ui/index.html`) 가 200 으로 응답.
- `/v3/api-docs` JSON 이 받아진다.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/backend
./gradlew test --tests "com.nkia.itg.common.*"
./gradlew build

# Postgres 컨테이너 기동 후 부팅 + Swagger UI 확인
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
docker-compose up -d
cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
sleep 8
curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'
curl -fsS http://localhost:8080/v3/api-docs | grep -q '"openapi"'
curl -fsS -I http://localhost:8080/swagger-ui/index.html | head -1 | grep -q "200"
kill %1
```

## 검증 절차

1. 위 AC 커맨드를 실행한다. 모든 단계가 통과해야 한다.
2. 아키텍처 체크리스트:
   - `ApiResponse<T>` 가 Record 인가? `ok(data)` / `ok(data, message)` / `fail(code, message)` 3종 정적 팩토리가 있는가? (ADR-009)
   - `ITGException` 이 `errorCode` + `httpStatus` 를 보유하는가? `RuntimeException` 상속인가?
   - `GlobalExceptionHandler` 가 응답 본문에 stack trace 를 노출하지 않는가?
   - `SwaggerConfig` 의 `contact.email` 이 `example.com` 도메인이고, 실 운영 이메일이 들어있지 않은가? (ADR-011)
   - `SecurityConfig` 에서 `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health`, `/api/meta/**` 이 `permitAll`, Session 정책이 `STATELESS`, CSRF 가 disabled 인가?
   - JWT 검증 필터가 골격만 있고 실 검증은 다음 phase 로 미루었는가? (코드에 `TODO(phase-2)` 주석으로 표시)
3. 결과에 따라 `phases/0-meta-backend-m1/index.json` 의 step 3 을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "ApiResponse<T> Record + ITGException + GlobalExceptionHandler (5종 예외 매핑) + SwaggerConfig (bearerAuth, example.com) + SecurityConfig (STATELESS, /api/meta/** permitAll, CORS localhost:5173) + JwtAuthenticationFilter 골격. /swagger-ui · /v3/api-docs · /actuator/health 200 확인"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "<구체적 에러>"`
   - 사용자 개입 필요 (Spring Security 6.x 정책 변경 대응 등) → `"status": "blocked"`, `"blocked_reason": "<구체적 사유>"` 후 즉시 중단

## 금지사항

- `@Schema(example = ...)` 에 실제 운영 데이터(이메일·IP·서버명·사번·시리얼)를 넣지 마라. 이유: ADR-011 — git 이력에 남아 회수 불가능. 가상 샘플(`"샘플-"`, `"SAMPLE-"`, `example.com`, RFC 5737 `192.0.2.x`) 만 사용.
- `GlobalExceptionHandler` 에서 stack trace 를 응답 본문에 포함시키지 마라. 이유: 정보 누출. stack 은 로그에만.
- `SecurityConfig` 에서 모든 경로를 `permitAll` 로 두지 마라. 이유: 다음 phase 에 인증 적용 시 누락 위험. 명시적으로 화이트리스트 패턴만 `permitAll`, 나머지는 `authenticated()` 또는 향후 강화를 위해 `permitAll` 이라도 별도 라인으로 명시. `/api/meta/**` 는 phase 한정 `permitAll`.
- JWT 검증 로직을 이번 step 에서 완성하지 마라. 이유: scope. 골격만. 실 검증·발급 엔드포인트는 다음 phase.
- Repository·Service·Controller 코드를 미리 작성하지 마라. 이유: step 4·5·6 의 책임.
- `ApiResponse` 의 정적 메서드를 `class` 로 만들거나 Lombok `@Builder` 로 대체하지 마라. 이유: ADR-009 — Record 명시. 일관성.
- `@Order(Ordered.HIGHEST_PRECEDENCE)` 등 임의 우선순위를 `GlobalExceptionHandler` 에 부여하지 마라. 이유: 추후 다른 `@RestControllerAdvice` 와 충돌 시 디버깅 어려움. 기본 우선순위로 두고 필요 시 ADR 추가.
