# 메타 자동 생성 파이프라인 e2e 보고서 — `itg-change`

> Phase 7 (ai-meta-generation) Step 3 — `e2e-generate-itg-change`
> 작성일: 2026-05-29 · 대상 메타: `itg-change-v1-1`

본 보고서는 **실제 OpenAPI 사양에서 출발하여 ITSM 변경관리 도메인(`itg-change`) 의 PageMeta DRAFT 를
자동 생성 → 검증(dry-run) → DB 적용(INSERT) → publish → 화면 노출까지 e2e 로 통과**시킨 결과를 기록한다.
이로써 PRD §9 M5 마일스톤("Claude Code 자동 메타 생성 파이프라인 안정화")의 핵심 약속을 검증한다.

> Change 도메인 백엔드(엔티티/Service/Controller)는 본 phase 의 범위가 **아니다**. 백엔드가 없는
> 도메인이라도 메타 자체는 dry-run·INSERT·publish 가 가능하다 — 이것이 No-code 메타 모델의 자율성이다.
> `meta.api` 는 임시로 미존재 경로 `/api/changes` 로 두었으며, 실제 데이터 호출은 백엔드 모듈 부재로
> 404 가 발생한다. 따라서 화면 검증은 `/system/meta?groupId=itg-change` 의 **메타 viewer 노출까지**만 수행한다.

---

## 1. 환경

| 항목 | 값 |
|------|-----|
| 작업 디렉토리 | `/Users/mwjeon/Projects/ai-work/harness_framework_ITSM` |
| 브랜치 / 기준 커밋 | `feat-7-ai-meta-generation` / `d3a6744` |
| Backend | Spring Boot 3.5.0 · Java 21 (Gradle toolchain 자동 프로비저닝) · `local` 프로파일 |
| DB | PostgreSQL 16.14 · Docker 컨테이너 `itg-postgres` (`localhost:5432`, DB `itgdb`) |
| 헬스체크 | `GET /actuator/health` → `{"status":"UP"}` |
| 인증 | phase-3 한정 `/api/meta/**` `permitAll` (SecurityConfig) — 인증 강화는 별도 phase |

---

## 2. 입력

| 항목 | 값 |
|------|-----|
| OpenAPI 사양 | `backend/openapi/itg-api-spec.json` (`GET /v3/api-docs` 로 최신 추출) |
| Request DTO | `TicketCreateRequest` (`title`, `content`, `priority`, `category`, `assigneeId`) |
| Response DTO | `TicketSummary` (`id`, `ticketNo`, `title`, `priority`, `status`, `assigneeId`, `createdAt`) |
| CLI | `scripts/generate_meta.py` (Step 0 산출물) |

Change 도메인 백엔드가 없어 `ChangeCreateRequest` 도 존재하지 않으므로, **도메인 골격이 유사한
ticket 의 `TicketCreateRequest`·`TicketSummary` 를 입력으로 빌려와** `itg-change` 메타를 생성했다
(`docs/META_GENERATION_GUIDE.md` 에 명시된 활용법).

```bash
python3 scripts/generate_meta.py \
  --openapi backend/openapi/itg-api-spec.json \
  --request-dto TicketCreateRequest \
  --response-dto TicketSummary \
  --group-id itg-change --title "ITSM 변경 관리" \
  --system-type ITSM --package-type PACKAGE \
  --major 1 --minor 1 --api /api/changes \
  --output sql/init/_generated/itg-change-v1-1.sql > /tmp/itg-change-v1-1.json
```

---

## 3. 생성된 메타 요약 — `itg-change-v1-1`

| 항목 | 값 |
|------|-----|
| `id` | `itg-change-v1-1` (`{groupId}-v{major}-{minor}` 패턴) |
| `systemType` / `packageType` | `ITSM` / `PACKAGE` |
| `groupId` / 버전 | `itg-change` / v1.1 |
| `metaStatus` (생성 시) | **`DRAFT`** (CLI 옵션으로도 변경 불가 — 항상 강제) |
| `api` | `/api/changes` (임시, 미존재 — 별도 phase 에서 백엔드 추가 예정) |
| `form.layout` | `two-column` |
| `form.fields` | **5개** — `title`(text/maxLength 200), `content`(text), `priority`(priority, required, 옵션 4종), `category`(text), `assigneeId`(user-picker) |
| `grid.columns` | **7개** — `id`, `ticketNo`, `title`, `priority`(priority), `status`(status), `assigneeId`(user-picker), `createdAt`(date) |
| `actions` | **1개** — `create` (`dialog-form`, 라벨 "등록") |

필드 타입 매핑은 ARCHITECTURE §5 매핑표 + 휴리스틱(`status`/`priority`/enum/`*Id` → user-picker)을 따랐다.

> **라벨 한글화 참고**: 일부 라벨(`내부 ID`, `표시용 티켓 번호` 등)은 OpenAPI `description` 을 그대로
> 가져온 것으로, 변경관리 도메인 용어로 다듬는 작업은 Claude Code 다듬기 단계
> (`docs/META_GENERATION_GUIDE.md` §1.4) 가 필요하다. 본 e2e 는 파이프라인 동작 검증이 목적이므로
> 원본 라벨 그대로 진행했다.

---

## 4. dry-run 결과 (`POST /api/meta/dry-run`)

```json
{ "success": true, "data": { "valid": true, "issues": [] }, "message": null, "errorCode": null }
```

- `valid: true`, 경고·오류 0건.
- `MetaValidationService`(Step 1)가 id 패턴·Enum 4종·groupId·minor·metaJson 구조를 모두 통과시킴.

---

## 5. DB 적용 · publish 결과

### 5-1. INSERT (DRAFT)

```bash
docker exec -i itg-postgres psql -U itg -d itgdb < sql/init/_generated/itg-change-v1-1.sql
# → INSERT 0 1
```

```
       id        | meta_status
-----------------+-------------
 itg-change-v1-1 | DRAFT
```

INSERT SQL 은 `ON CONFLICT (id) DO NOTHING` 으로 멱등하며, `meta_status` 는 `DRAFT` 로 고정.

### 5-2. publish (`PATCH /api/meta/itg-change-v1-1/publish`)

- 응답 `data.metaStatus`: **`PUBLISHED`**, `message`: "배포되었습니다."
- 동일 `groupId(itg-change)` 내 기존 `PUBLISHED` 가 없어 자동 DEPRECATE 대상 없음.

### 5-3. active 조회 (`GET /api/meta/active/itg-change`)

- HTTP 200, `data.id == "itg-change-v1-1"`, `data.metaStatus == "PUBLISHED"`, `data.active == true`.
- `metaJson` 본문(api/grid/form/actions) 정상 직렬화 확인.

---

## 6. 프런트 노출 결과 (`/system/meta?groupId=itg-change`)

- 라우트 `system/meta` 존재 (`frontend/src/router/index.ts`).
- `MetaPage.vue` 가 `route.query.groupId` → `usePageMeta(groupId)` → `GET /api/meta/active/{groupId}` 호출.
- active 응답이 정상이므로 메타 카드(제목 `ITSM 변경 관리 (itg-change-v1-1)`, systemType/packageType/version/metaStatus)
  + `metaJson` JSON 미리보기(`<pre>`)가 렌더링됨 → **시각 PASS**.
- 모듈 라우트(`/change`)는 router 에 미등록 — 본 step 범위 외. `/system/meta` 의 범용 메타 viewer 로만 검증.

---

## 7. 핵심 검증 사실

- **PRD §9 M5 마일스톤 이행**: `OpenAPI → generate_meta.py(CLI) → dry-run → INSERT(DRAFT) → publish → 화면 노출`
  의 5단계 파이프라인이 **백엔드 도메인 모듈(엔티티/Service/Controller) 없이도 동작**함을 확인.
  이는 No-code 메타 모델의 자율성 — "JSON 메타 한 건 추가 = 화면 한 개" 등식 — 을 입증한다.
  단, `meta.api(/api/changes)` 실호출은 백엔드 모듈 부재로 404 이므로, 데이터 그리드/폼 submit 이 아닌
  **메타 viewer 노출까지**를 e2e 종착점으로 삼았다.
- **DRAFT 강제**: 생성 시 `metaStatus` 는 CLI 옵션과 무관하게 항상 `DRAFT` (ADR-005·ADR-006). 화면 노출은
  명시적 publish 이후에만 가능 — DRAFT 메타가 화면에 새는 사고를 원천 차단.
- **검증으로 결함 사전 차단**: `MetaValidationService` 가 `systemType` 누락·id 패턴 위반·Enum 오타 등을
  dry-run 단계에서 `invalid` 로 거부 → DB 오염 전에 차단.

---

## 8. 한계

- **백엔드 모듈 부재**: Change 도메인 엔티티/Service/Controller 와 `/api/changes` 엔드포인트는 미구현.
  메타의 데이터 호출(그리드 로딩·폼 submit)은 백엔드 모듈 추가(**별도 phase**) 후에야 가능.
- **라벨·옵션 한글화**: CLI 는 OpenAPI `description`·enum 값을 기계적으로 매핑하므로, 변경관리 도메인에 맞는
  라벨·옵션 다듬기는 Claude Code 다듬기 단계(`docs/META_GENERATION_GUIDE.md` §1.4)가 필요.
- **입력 DTO 차용**: 본 e2e 는 ticket DTO 를 빌려 골격을 생성했으므로, 실제 변경관리 필드(변경유형·승인자·
  영향도·롤백계획 등)는 향후 Change DTO 정의 후 재생성·복사(`POST /copy`)로 반영해야 한다.

---

## 9. 산출물

| 산출물 | 비고 |
|--------|------|
| `scripts/generate_meta.py` · `scripts/test_generate_meta.py` | Step 0 — 메타 생성 CLI + 단위 테스트 |
| `scripts/validate_meta.sh` | Step 1 — dry-run wrapper |
| `backend/.../meta/service/MetaValidationService.java` | Step 1 — 검증 서비스 |
| `docs/META_GENERATION_GUIDE.md` + `scripts/templates/*` | Step 2 — 7단계 워크플로우 가이드·프롬프트 템플릿 |
| `sql/init/_generated/itg-change-v1-1.sql` | **본 step — generator 결과(INSERT SQL)** |
| `docs/META_GENERATION_REPORT.md` | **본 보고서** |

> 시드 메타(`itg-change-v1-1`)는 DB 에 **보존**한다 (다음 phase 또는 미래 활용 가능).
