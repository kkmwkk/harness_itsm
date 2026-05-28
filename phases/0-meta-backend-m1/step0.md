# Step 0: project-skeleton

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/CLAUDE.md` — 기술 스택·절대 규칙·개발 프로세스 (특히 "패키지 구조" 와 "절대 규칙" 섹션)
- `/docs/ARCHITECTURE.md` — 디렉토리 구조 (§2-1, §2-3, §2-4) 와 패키지 명명 규칙
- `/docs/ADR.md` — ADR-002 (Java 21 + Spring Boot 3), ADR-009 (`ApiResponse<T>` 강제), ADR-013 (TDD)

레포 현황:
- 작업 루트: `/Users/mwjeon/Projects/ai-work/harness_framework_ITSM/`
- 현 디렉토리: `docs/`, `phases/`, `scripts/`, `CLAUDE.md` 만 존재. `backend/`·`frontend/`·`sql/`·`docker-compose.yml` 은 **이 phase 에서 처음 생성**한다.

## 작업

이 step 의 목적은 **Spring Boot 3 + Java 21 백엔드 프로젝트의 빈 골격을 만들고 `./gradlew build` 가 통과하게 만드는 것**이다. 이 step 에서는 도메인 코드(`PageMeta`, `MetaService` 등) 와 DB 스키마는 작성하지 않는다.

### 1. 디렉토리 생성

루트에 아래 디렉토리를 만든다:

```
backend/
backend/src/main/java/com/nkia/itg/
backend/src/main/resources/
backend/src/test/java/com/nkia/itg/
```

### 2. Gradle 8.x + Spring Boot 3.x + Java 21 초기화

`backend/` 디렉토리에 Gradle Wrapper 를 포함한 프로젝트를 만든다. Gradle Wrapper 는 `gradle wrapper --gradle-version 8.10` (또는 그 이상 8.x 안정 버전) 으로 생성한다. Gradle 이 시스템에 없으면, `gradle/wrapper/gradle-wrapper.properties` 와 `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` 를 표준 템플릿으로 직접 만들어도 된다.

`backend/build.gradle` (Groovy DSL — 팀 표준은 Groovy 로 통일):

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.4'   // 또는 3.3.x 최신 안정
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'com.nkia.itg'
version = '2.0.0-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories { mavenCentral() }

dependencies {
    // --- Spring Boot ---
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'

    // --- Swagger / OpenAPI ---
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0'

    // --- DB ---
    runtimeOnly  'org.postgresql:postgresql'

    // --- JWT (step 3 에서 사용 예정, 미리 의존성만 추가) ---
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly  'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly  'io.jsonwebtoken:jjwt-jackson:0.12.6'

    // --- Lombok (제한적 사용 — @RequiredArgsConstructor·@Slf4j·@Getter·@Builder 만) ---
    compileOnly      'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    testCompileOnly  'org.projectlombok:lombok'
    testAnnotationProcessor 'org.projectlombok:lombok'

    // --- 테스트 ---
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testImplementation 'org.testcontainers:junit-jupiter:1.20.2'
    testImplementation 'org.testcontainers:postgresql:1.20.2'
    testRuntimeOnly  'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

`backend/settings.gradle`:

```groovy
rootProject.name = 'itg-backend'
```

> **QueryDSL 은 이 step 에서 추가하지 않는다.** Repository step(step 4) 에서 실제로 필요해질 때 도입한다. 미리 끌어다 놓아 빌드 환경을 복잡하게 만들지 않는다.

### 3. 패키지 골격 (빈 디렉토리 + `.gitkeep` 또는 패키지마다 1개 placeholder)

`backend/src/main/java/com/nkia/itg/` 아래에 다음 패키지를 만든다 (Java 는 빈 디렉토리를 추적하지 않으므로 각 패키지에 `package-info.java` 1 개씩 두어도 좋다. 단, 텍스트 주석은 1줄로 짧게):

```
com.nkia.itg
├── Application.java            # @SpringBootApplication 메인 클래스
├── common
│   ├── response                # ApiResponse<T> 위치 (step 3)
│   ├── exception               # ITGException · GlobalExceptionHandler (step 3)
│   ├── security                # JWT, SecurityConfig (step 3)
│   └── config                  # SwaggerConfig, JpaConfig (step 3)
├── meta
│   ├── controller              # MetaController (step 6)
│   ├── service                 # MetaService (step 5)
│   ├── repository              # MetaRepository (step 4)
│   ├── entity                  # PageMeta (step 2)
│   ├── dto                     # PageMetaResponse, ... (step 5)
│   └── domain                  # SystemType, PackageType, MetaStatus (step 2)
├── itsm
├── itam
├── pms
└── system
```

`Application.java`:

```java
package com.nkia.itg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

테스트 패키지에도 같은 골격을 만들고, `backend/src/test/java/com/nkia/itg/ApplicationContextLoadTest.java` 하나만 추가:

```java
package com.nkia.itg;

import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.Test;

@SpringBootTest
class ApplicationContextLoadTest {
    @Test void contextLoads() {}
}
```

> 이 테스트는 step 1 에서 DB 설정이 들어오면 깨질 수 있다. 그래서 이 step 에서는 `application.yml` 의 DataSource auto-configure 를 제외하거나, JPA·DataSource starter 의 클래스 패스 자동 구성을 우회해야 한다. 가장 단순한 방법은 **`backend/src/test/resources/application.yml` 에 `spring.autoconfigure.exclude` 로 DataSource/JPA 자동 구성을 제외**하는 것이다. step 1 이 들어오면 이 제외 설정을 정상화한다.

### 4. 리소스

`backend/src/main/resources/application.yml` (이 step 에서는 거의 비어있음. DB 설정은 step 1):

```yaml
spring:
  application:
    name: itg-backend

server:
  port: 8080
```

### 5. .gitignore 보강

루트 `.gitignore` 에 다음을 추가한다 (이미 있으면 중복 추가하지 마라):

```
# Gradle / Spring
backend/.gradle/
backend/build/
backend/out/
backend/.idea/
backend/*.iml
backend/HELP.md

# 환경 변수
.env.local
```

### 6. 루트 README 보강 (선택, 짧게)

`README.md` 는 한 번 더 만든다. 다음 내용만 1페이지로:
- 프로젝트 한 줄 소개 ("Polestar10 ITG v2 — No-code 플랫폼")
- 기술 스택 한 줄 요약
- 로컬 실행 명령(이 시점에는 backend 만): `cd backend && ./gradlew build`
- 자세한 가이드는 `CLAUDE.md` / `docs/` 참조

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/backend
./gradlew --version           # Java 21 toolchain 확인
./gradlew build               # 빌드 통과 (테스트 포함, 단 contextLoads 만 존재)
./gradlew bootJar             # 실행 jar 생성 (build/libs/*.jar)
```

빌드 산출물(`build/`)은 커밋하지 마라.

## 검증 절차

1. 위 AC 커맨드를 실행한다. `./gradlew build` 가 통과해야 한다.
2. 아키텍처 체크리스트:
   - `docs/ARCHITECTURE.md` §2-3 의 패키지 골격(`common/{response,exception,security,config}`, `meta/{controller,service,repository,entity,dto,domain}`, `itsm/itam/pms/system`)을 빠짐없이 만들었는가?
   - Java 21 toolchain 선언(`languageVersion = JavaLanguageVersion.of(21)`) 이 들어있는가?
   - Spring Boot 3.x 의존성을 사용했는가? (`ADR-002`)
   - `ApiResponse<T>` 는 step 3 에서 만들 예정이므로 **이 step 에서는 만들지 않는다**.
3. 결과에 따라 `phases/0-meta-backend-m1/index.json` 의 step 0 을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "backend/ Gradle 8 + Spring Boot 3.3 + Java 21 스켈레톤, com.nkia.itg.{common,meta,itsm,itam,pms,system} 패키지 골격, Application.java, ApplicationContextLoadTest contextLoads 통과"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "<구체적 에러>"`
   - 사용자 개입 필요 (Java 21 미설치 등) → `"status": "blocked"`, `"blocked_reason": "<구체적 사유>"` 후 중단

## 금지사항

- `frontend/`·`sql/`·`docker-compose.yml` 을 이 step 에서 만들지 마라. 이유: scope 최소화, step 1 의 책임이다.
- `PageMeta` Entity·Enum·`MetaService` 코드를 미리 작성하지 마라. 이유: step 2~5 의 책임이다. 미리 짜면 빈 의존성에 묶여 빌드가 깨진다.
- Kotlin 파일을 만들지 마라. 이유: `ADR-002` — Java 21 전용.
- Lombok 의 `@SneakyThrows`·`@Synchronized`·`@Cleanup` 매직 어노테이션 사용 금지. 이유: 디버깅·정적 분석 방해. 허용: `@RequiredArgsConstructor`·`@Slf4j`·`@Getter`·`@Builder`.
- `application.yml` 에 DB·시크릿·실 운영 정보 넣지 마라. 이유: step 1 에서 `application-local.yml` 분리. 이 step 에선 빈 yml.
- 빌드 산출물(`build/`, `*.jar`) 을 git 에 커밋하지 마라.
- 기존 `docs/`, `CLAUDE.md`, `phases/`, `scripts/` 를 수정하지 마라.
