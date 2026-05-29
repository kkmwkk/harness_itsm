# Step 2: ci-workflow

## 읽어야 할 파일

- `/CLAUDE.md` §9
- `/docs/PRD.md` §9 M6
- `/phases/8-sonarqube-ci-gate/step0.md`·`step1.md` — sonar/jacoco 셋업·baseline

## 작업

이 step 의 목적은 **GitHub Actions 워크플로우로 `./gradlew sonar` 를 자동 실행하는 CI 파이프라인을 정의하고, 로컬 SonarQube 인스턴스 옵션도 함께 제공하는 것**이다. 실 SonarQube 분석 결과 보고는 다음 step.

### 1. GitHub Actions 워크플로우 — `.github/workflows/sonarqube.yml`

```yaml
name: SonarQube Analysis

on:
  push:
    branches: [ "main" ]
  pull_request:
    branches: [ "main" ]

jobs:
  sonarqube:
    name: Backend coverage + SonarQube
    runs-on: ubuntu-latest
    if: ${{ secrets.SONAR_TOKEN != '' }}      # 토큰 미설정 환경에서는 자동 skip

    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_DB: itgdb
          POSTGRES_USER: itg
          POSTGRES_PASSWORD: itg1234
        ports: [ 5432:5432 ]
        options: >-
          --health-cmd "pg_isready -U itg -d itgdb"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

    steps:
      - name: Checkout
        uses: actions/checkout@v4
        with:
          fetch-depth: 0                       # SonarQube blame 분석용

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      - name: Cache Gradle
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: gradle-${{ runner.os }}-${{ hashFiles('backend/**/*.gradle', 'backend/gradle/wrapper/gradle-wrapper.properties') }}

      - name: Apply schema (postgres)
        run: |
          docker exec ${{ job.services.postgres.id }} \
            psql -U itg -d itgdb -f /dev/stdin < sql/init/01_schema.sql

      - name: Test + JaCoCo
        working-directory: backend
        run: ./gradlew test jacocoTestReport --no-daemon

      - name: SonarQube analysis
        working-directory: backend
        env:
          SONAR_TOKEN:      ${{ secrets.SONAR_TOKEN }}
          SONAR_HOST_URL:   ${{ secrets.SONAR_HOST_URL || 'https://sonarcloud.io' }}
          SONAR_PROJECT_KEY: ${{ secrets.SONAR_PROJECT_KEY || 'polestar-itg-v2' }}
        run: ./gradlew sonar --no-daemon
```

### 2. 로컬 SonarQube 옵션 — `docker-compose.sonar.yml`

별도 compose 파일로 분리 (기본 `docker-compose.yml` 의 postgres·pgadmin 과 분리):

```yaml
version: '3.9'

services:
  sonarqube:
    image: sonarqube:community
    container_name: itg-sonarqube
    restart: unless-stopped
    environment:
      SONAR_ES_BOOTSTRAP_CHECKS_DISABLE: "true"
      SONAR_JDBC_URL:      jdbc:postgresql://sonar-db:5432/sonar
      SONAR_JDBC_USERNAME: sonar
      SONAR_JDBC_PASSWORD: sonar1234
    ports:
      - "9000:9000"
    depends_on:
      - sonar-db
    volumes:
      - sonar-data:/opt/sonarqube/data
      - sonar-extensions:/opt/sonarqube/extensions
      - sonar-logs:/opt/sonarqube/logs

  sonar-db:
    image: postgres:16
    container_name: itg-sonar-db
    restart: unless-stopped
    environment:
      POSTGRES_DB: sonar
      POSTGRES_USER: sonar
      POSTGRES_PASSWORD: sonar1234
    volumes:
      - sonar-db-data:/var/lib/postgresql/data

volumes:
  sonar-data:
  sonar-extensions:
  sonar-logs:
  sonar-db-data:
```

> 운영 DB(`itg-postgres`) 와 SonarQube DB(`itg-sonar-db`) 는 분리. 메모리 부담 큰 SonarQube 는 옵션으로 띄움 (`docker-compose -f docker-compose.sonar.yml up -d`).

### 3. 로컬 분석 가이드 — `docs/SONAR_CI_GUIDE.md` (선택, 짧게)

다음 내용 1페이지:
1. 로컬 SonarQube 실행:
   ```
   docker-compose -f docker-compose.sonar.yml up -d
   ```
2. `http://localhost:9000` 접속 → admin/admin → 토큰 발급 → `.env.sonar` 에 저장.
3. 분석:
   ```
   cd backend
   set -a; source ../.env.sonar; set +a
   ./gradlew test jacocoTestReport sonar
   ```
4. 결과 확인: `http://localhost:9000/dashboard?id=polestar-itg-v2`.
5. CI 환경에서는 GitHub Secrets 에 `SONAR_TOKEN`·`SONAR_HOST_URL` 등록 (또는 sonarcloud.io 사용).

### 4. .gitignore

`docker-compose.sonar.yml` 의 볼륨이 호스트에 마운트되지 않도록 named volume 만 사용 (위 yaml 그대로 OK). 추가 gitignore 항목 없음.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM

# 1) GitHub Actions
test -f .github/workflows/sonarqube.yml
grep -q "actions/setup-java@v4" .github/workflows/sonarqube.yml
grep -q "java-version: 21" .github/workflows/sonarqube.yml
grep -q "./gradlew sonar" .github/workflows/sonarqube.yml
grep -q "secrets.SONAR_TOKEN" .github/workflows/sonarqube.yml

# 2) 로컬 SonarQube compose
test -f docker-compose.sonar.yml
grep -q "sonarqube:community" docker-compose.sonar.yml
grep -q "9000:9000" docker-compose.sonar.yml

# 3) 가이드 문서 (선택)
test -s docs/SONAR_CI_GUIDE.md || true

# 4) yaml syntax 검증
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/sonarqube.yml'))"
python3 -c "import yaml; yaml.safe_load(open('docker-compose.sonar.yml'))"

# 5) 기존 docker-compose.yml 영향 없음
docker-compose config -q
```

## 검증 절차

1. AC 통과.
2. 아키텍처 체크:
   - workflow 가 PR + push to main 에서 동작?
   - SONAR_TOKEN 미설정 환경에서는 자동 skip?
   - postgres service container 가 health-check 와 함께 떠 있고, 스키마 적용 단계 포함?
   - 로컬 SonarQube 가 별도 compose 파일에 분리 (운영 postgres 와 격리)?
3. step 2 업데이트:
   - 성공 → `"summary": ".github/workflows/sonarqube.yml (PR/main push, JDK 21 + Gradle cache + postgres service + 스키마 자동 + ./gradlew test jacocoTestReport sonar) + docker-compose.sonar.yml (sonarqube:community + sonar-db postgres:16, named volumes, 9000 port) + docs/SONAR_CI_GUIDE.md (로컬 분석 + CI 시크릿)."`

## 금지사항

- GitHub Actions 워크플로우에 토큰·DB 비밀번호 하드코딩 금지. secrets 만.
- SonarQube 컨테이너의 메모리/JVM 옵션을 임의 튜닝 금지 (기본값 유지).
- docker-compose.sonar.yml 을 기본 docker-compose.yml 에 병합 금지 — 별도 파일 유지.
- workflow 의 trigger 에 `*` 모든 push 추가 금지. PR + main 만.
- frontend/sonar 분석 추가 금지 (본 phase 는 backend 만).
- 외부 도구(SpotBugs·PMD) 추가 금지.
- `sonar` 태스크가 실패해도 workflow 가 통과하도록 만들지 마라 (`continue-on-error: true` 추가 금지).
- 운영 코드 수정 금지.
