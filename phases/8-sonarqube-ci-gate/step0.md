# Step 0: gradle-sonar-plugin

## 읽어야 할 파일

- `/CLAUDE.md` §9 — 품질 게이트 목표 (Service 70%, 중복 3%, Blocker·Critical 0, 시크릿 0)
- `/docs/PRD.md` §9 M6 (SonarQube CI 게이트)
- `/docs/ADR.md` ADR-013 (TDD)
- `/backend/build.gradle` — 현재 의존성·플러그인

## 작업

이 step 의 목적은 **backend Gradle 빌드에 SonarQube 분석 플러그인과 JaCoCo 커버리지 플러그인을 추가하고, `./gradlew sonar` 가 동작 가능한 상태로 만드는 것**이다. 실 SonarQube 서버 연동은 다음 step.

### 1. `backend/build.gradle` 추가

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.0'
    id 'io.spring.dependency-management' version '1.1.6'
    id 'jacoco'                                                  // ← 추가
    id 'org.sonarqube' version '5.1.0.4882'                      // ← 추가
}
/* 기존 group / version / java / repositories / dependencies 그대로 */

jacoco {
    toolVersion = '0.8.12'
}

tasks.test {
    finalizedBy jacocoTestReport
}

tasks.jacocoTestReport {
    dependsOn test
    reports {
        xml.required  = true                                     // SonarQube 입력
        html.required = true                                     // 로컬 확인용
        csv.required  = false
    }
    afterEvaluate {
        classDirectories.setFrom(
            files(classDirectories.files.collect {
                fileTree(dir: it, exclude: [
                    'com/nkia/itg/Application*',
                    'com/nkia/itg/**/package-info*',
                    'com/nkia/itg/**/dto/**',                    // Record DTO 자동 생성 부분
                ])
            })
        )
    }
}

sonar {
    properties {
        property 'sonar.projectKey',  System.getenv('SONAR_PROJECT_KEY') ?: 'polestar-itg-v2'
        property 'sonar.projectName', 'Polestar10 ITG v2'
        property 'sonar.host.url',    System.getenv('SONAR_HOST_URL') ?: 'http://localhost:9000'
        property 'sonar.token',       System.getenv('SONAR_TOKEN')    ?: ''
        property 'sonar.sourceEncoding', 'UTF-8'
        // 커버리지 리포트 경로
        property 'sonar.coverage.jacoco.xmlReportPaths',
                 layout.buildDirectory.dir('reports/jacoco/test/jacocoTestReport.xml').get().asFile
        // 분석 제외 — 단순 DTO·Application·생성 파일
        property 'sonar.exclusions',
                 '**/Application.java,**/package-info.java,**/dto/**,**/_generated/**'
        // 커버리지 제외
        property 'sonar.coverage.exclusions',
                 '**/Application.java,**/package-info.java,**/dto/**,**/config/**'
    }
}
```

### 2. 환경 변수 문서화

`backend/.env.sonar.example` (커밋):
```
# SonarQube 분석용 — 실 값은 .env.sonar (gitignore) 또는 CI secret 로
SONAR_HOST_URL=http://localhost:9000
SONAR_TOKEN=__placeholder__
SONAR_PROJECT_KEY=polestar-itg-v2
```

`backend/.gitignore` 에 `.env.sonar` 추가.

> 토큰 없이도 `./gradlew sonar` 자체는 실행되며, host 연결 실패시 명확한 에러로 종료.

### 3. JaCoCo 동작 검증

`./gradlew test jacocoTestReport` 실행 후:
- `backend/build/reports/jacoco/test/jacocoTestReport.xml` 생성.
- `backend/build/reports/jacoco/test/html/index.html` 생성 (브라우저 열기 가능).

### 4. sonar 태스크 동작 검증 (서버 없이)

`SONAR_HOST_URL=http://invalid-host:9000 ./gradlew sonar` 실행 시:
- 분석 수집 단계는 통과 (gradle 측 properties 계산 성공).
- 호스트 연결에서 명시적 에러 (`ConnectException` 또는 `connection refused`) — 본 step 은 여기까지가 정상.

> CI/서버 실 연동은 step 2·3.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/backend

# 1) build.gradle 에 jacoco + sonarqube
grep -q "id 'jacoco'"      build.gradle
grep -q "id 'org.sonarqube'" build.gradle
grep -q "sonar.coverage.jacoco.xmlReportPaths" build.gradle
grep -q "sonar.exclusions" build.gradle

# 2) .env.sonar.example 존재
test -f .env.sonar.example
grep -q "SONAR_HOST_URL" .env.sonar.example
grep -q "SONAR_TOKEN" .env.sonar.example

# 3) .gitignore 에 .env.sonar
grep -q "\.env\.sonar$\|/\.env\.sonar" .gitignore

# 4) JaCoCo 리포트 생성
./gradlew clean test jacocoTestReport 2>&1 | tail -5
test -f build/reports/jacoco/test/jacocoTestReport.xml
test -f build/reports/jacoco/test/html/index.html

# 5) sonar 태스크가 존재
./gradlew tasks --all | grep -E "^sonar" || true
./gradlew help --task sonar 2>&1 | tail -10

# 6) sonar 실행 시 sourceEncoding·projectKey 인식 (호스트 없으면 connection 에서 실패 OK)
SONAR_HOST_URL=http://invalid.example.com:9000 \
  ./gradlew sonar -Dsonar.scanner.skip=false 2>&1 | head -30 | \
  grep -E "polestar-itg-v2|sonar\.host\.url|invalid\.example\.com" || true
# 위 grep 은 정보 출력 확인 — 분석은 어차피 connection 실패. 실패해도 OK.
```

## 검증 절차

1. AC 통과.
2. 아키텍처 체크:
   - jacoco 의 xml 리포트가 SonarQube 가 읽는 경로(`build/reports/jacoco/test/jacocoTestReport.xml`) 와 일치?
   - DTO·Application·package-info·config 등 분석 제외 패턴이 합리적?
   - 토큰을 build.gradle 에 하드코딩하지 않고 환경변수 참조?
3. step 0 업데이트:
   - 성공 → `"summary": "backend/build.gradle 에 jacoco(0.8.12) + org.sonarqube(5.1) 플러그인 + JaCoCo XML/HTML 리포트(test finalizedBy) + sonar.properties(sourceEncoding/coverage.jacoco.xmlReportPaths/exclusions: Application·package-info·dto·_generated). .env.sonar.example + .gitignore .env.sonar. ./gradlew test jacocoTestReport 통과 + JaCoCo XML 리포트 생성 확인."`

## 금지사항

- SonarQube 토큰을 build.gradle / .env.sonar.example 에 실 값으로 하드코딩 금지.
- `sonar.exclusions` 에 production 코드 디렉토리(service·controller) 를 추가 금지. 이유: 분석 의미 상실.
- JaCoCo 의 `excludes` 에 메타 모듈(`com.nkia.itg.meta.**`) 추가 금지. 이유: 핵심 도메인.
- 다른 phase 의 운영 코드 (frontend·sql·docs 등) 수정 금지.
- 새 의존성(예: SpotBugs·Checkstyle) 본 step 에서 추가 금지. 본 step 은 SonarQube + JaCoCo 만.
- `./gradlew sonar` 가 서버 없이도 무조건 성공하도록 만들지 마라 (이상 동작 — 서버 연결 실패는 명시적이어야).
- 운영 코드 console.log·System.out.println 추가 금지.
