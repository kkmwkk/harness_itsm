# SonarQube CI / 로컬 분석 가이드 — Polestar10 ITG v2

> backend 모듈 한정. JaCoCo 커버리지 + SonarQube 정적분석 게이트(PRD §9 M6).
> Gradle 셋업은 `backend/build.gradle` 의 `jacoco` + `org.sonarqube` 플러그인, baseline 은
> `backend/SONAR_BASELINE.md` 참고.

## 1. 로컬 SonarQube 실행

운영 DB(`itg-postgres`) 와 격리된 별도 compose 파일로 띄운다 (메모리 부담이 크므로 옵션).

```bash
docker-compose -f docker-compose.sonar.yml up -d
```

- `itg-sonarqube` (`sonarqube:community`) — http://localhost:9000
- `itg-sonar-db` (`postgres:16`) — SonarQube 전용 DB (운영 `itgdb` 와 분리)

기동에 1~2분 소요. `docker-compose -f docker-compose.sonar.yml logs -f sonarqube` 로 `SonarQube is operational` 확인.

## 2. 토큰 발급

1. http://localhost:9000 접속 → 최초 로그인 `admin` / `admin` → 비밀번호 변경.
2. **My Account → Security → Generate Tokens** 로 분석용 토큰 발급.
3. 발급한 값을 `backend/.env.sonar` 에 저장 (`backend/.env.sonar.example` 복사).

```bash
cp backend/.env.sonar.example backend/.env.sonar
# backend/.env.sonar 의 SONAR_TOKEN 을 발급한 값으로 교체
```

> `backend/.env.sonar` 는 `.gitignore` 대상 — 커밋 금지.

## 3. 분석 실행

```bash
cd backend
set -a; source .env.sonar; set +a
./gradlew test jacocoTestReport sonar
```

- `test jacocoTestReport` 로 JaCoCo XML 리포트 생성 후 `sonar` 가 이를 업로드한다.
- `SONAR_HOST_URL` / `SONAR_TOKEN` / `SONAR_PROJECT_KEY` 는 `build.gradle` 의 `sonar { }` 블록이 환경변수에서 읽는다.

## 4. 결과 확인

http://localhost:9000/dashboard?id=polestar-itg-v2

커버리지·중복·취약점·코드스멜을 확인한다 (게이트 기준은 PRD §6 — Service 70% 커버리지, 중복 3% 이하, Blocker/Critical 0건).

## 5. CI 환경 (GitHub Actions)

`.github/workflows/sonarqube.yml` 이 `main` push / PR 에서 동작한다.

- GitHub repo **Settings → Secrets and variables → Actions** 에 등록:
  - `SONAR_TOKEN` (필수 — 미설정 시 워크플로우 job 자동 skip)
  - `SONAR_HOST_URL` (선택 — 미설정 시 `https://sonarcloud.io`)
  - `SONAR_PROJECT_KEY` (선택 — 미설정 시 `polestar-itg-v2`)
- 자체 호스팅 SonarQube 또는 sonarcloud.io 중 택1.
- 워크플로우는 postgres service container 를 띄우고 `sql/init/01_schema.sql` 을 적용한 뒤
  `./gradlew test jacocoTestReport sonar` 를 실행한다.

> 토큰·DB 비밀번호는 워크플로우에 하드코딩하지 않는다. GitHub Secrets 만 사용한다.
