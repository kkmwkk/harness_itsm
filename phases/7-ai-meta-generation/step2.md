# Step 2: prompt-templates-and-guide

## 읽어야 할 파일

- `/CLAUDE.md` — 핵심 설계 사상·절대 규칙
- `/docs/ARCHITECTURE.md` — §10 AI 메타 자동 생성 파이프라인
- `/docs/PRD.md` — §5-4 AI 메타 자동 생성
- `/docs/ADR.md` — ADR-004·ADR-005·ADR-006
- `/phases/7-ai-meta-generation/step0~1.md` — generate_meta.py, dry-run
- `/sql/init/03_itg_ticket_meta.sql`·`06_itg_asset_meta.sql` — 메타 시드 예시

## 작업

이 step 의 목적은 **개발자가 Claude Code (또는 동등한 AI 에이전트) 를 통해 메타를 자동 생성·검증·배포하는 워크플로우를 정형화한 문서와 프롬프트 템플릿을 만드는 것**이다. 운영 코드 수정 없음.

### 1. `docs/META_GENERATION_GUIDE.md` — 사용자 가이드

다음 섹션을 포함한 마크다운 문서:

#### 1.1 개요
- "메타 한 건 = 화면 한 개" 약속 (ADR-004) 재확인.
- 본 가이드는 Spring Boot `@Entity` 또는 OpenAPI 사양에서 PageMeta DRAFT 를 자동 생성하고, dry-run 으로 검증한 뒤 INSERT → publish 하는 워크플로우를 다룬다.

#### 1.2 사전 준비
- Docker Desktop · Postgres 컨테이너 · Spring Boot bootRun 가동 (8080).
- `backend/openapi/itg-api-spec.json` 갱신 (`backend && curl http://localhost:8080/v3/api-docs > backend/openapi/itg-api-spec.json` 또는 `./gradlew bootRun` 후 export).

#### 1.3 단계별 워크플로우

```
1) OpenAPI 사양 추출
   curl http://localhost:8080/v3/api-docs > backend/openapi/itg-api-spec.json

2) PageMeta 골격 생성 (DRAFT)
   python3 scripts/generate_meta.py \
     --openapi backend/openapi/itg-api-spec.json \
     --request-dto ChangeCreateRequest \
     --response-dto ChangeSummary \
     --group-id itg-change --title "ITSM 변경 관리" \
     --system-type ITSM --package-type PACKAGE \
     --major 1 --minor 1 --api /api/changes \
     --output sql/init/_generated/itg-change-v1-1.sql > /tmp/itg-change-v1-1.json

3) (선택) Claude Code 로 라벨·옵션 다듬기 — 프롬프트 템플릿 §1.4 참고

4) dry-run 검증
   scripts/validate_meta.sh /tmp/itg-change-v1-1.json
   # → valid:true 가 아니면 issues 확인 후 메타 수정

5) DB 적용 (DRAFT)
   docker exec -i itg-postgres psql -U itg -d itgdb < sql/init/_generated/itg-change-v1-1.sql

6) 검토 후 배포
   curl -X PATCH http://localhost:8080/api/meta/itg-change-v1-1/publish

7) 화면 확인
   - router/index.ts 의 라우트에 meta.groupId 매핑 추가 (예: itam·itsm 패턴 참조)
   - 또는 /system/meta?groupId=itg-change 로 메타 직접 확인
```

#### 1.4 Claude Code 프롬프트 템플릿

라벨·옵션·UX 개선 시 다음 정형 프롬프트:

```
다음 PageMeta JSON 의 form.fields 와 grid.columns 의 'label' 을 사용자 친화 한글
라벨로 다듬어줘. 'options' 가 있는 select·radio 필드는 한글 라벨도 추가해줘.

규칙:
- metaStatus 는 절대 변경 금지 (DRAFT 유지).
- systemType / packageType / groupId / major / minor / id 변경 금지.
- form.fields 의 name·type 변경 금지 (라벨·options.label 만).
- grid.columns 의 field·type 변경 금지 (label·width 조정 허용).
- ARCHITECTURE.md §5 의 FieldType 12 종 외의 값 사용 금지.
- options 의 value 는 영문 코드 그대로, label 만 한글.

[JSON 첨부]
```

#### 1.5 결함 사례·회피

- ticket phase 3 의 `ticket_no NOT NULL` + `@GeneratedValue(IDENTITY)` 충돌 (hotfix 8d636a2) — 메타 생성기는 백엔드 스키마 결함을 잡지 않음. dry-run 도 form/grid 형식만 검증.
- 자가 교정 단위 테스트로는 잡히지 않는 결함 → 통합·e2e 단계에서 발견 가능성 (Mockito 한계).
- 새 모듈 적용 시 SecurityConfig 의 `/api/{module}/**` permitAll 추가 잊지 말 것 (hotfix b5f9150 의 교훈).

#### 1.6 한계 (별도 ADR 후보)
- 자산-도메인-특수 휴리스틱(예: `pageMetaIdAtRegistration` 같은 메타-관리용 컬럼) 은 generator 가 자동 인식 못 함. 수동 보강 필요.
- 그리드 컬럼 width·flex·pinned 의 자동 산정은 미지원.
- Vue Router 등록 코드 자동 생성은 미지원 (수동 추가).

### 2. `scripts/templates/` 디렉토리

`scripts/templates/README.md`:
```
# 메타 생성 입출력 예시

generate_meta.py 와 함께 사용하는 입출력 예시를 모은다.

- `example-input-ticket.json` — OpenAPI 사양의 components.schemas 부분 발췌
- `example-output-itg-ticket.json` — 그 결과로 생성된 PageMeta JSON
- `example-prompt-polish.md` — Claude Code 라벨 다듬기 프롬프트
```

위 3 파일을 만들고, 실 산출물을 담는다 (예: `backend/openapi/itg-api-spec.json` 의 일부 + `generate_meta.py` 실행 결과).

> 본 step 은 문서/템플릿 정착이 목적이므로, 산출물 파일 자체는 README 와 함께 보존.

### 3. 루트 README 갱신 (선택)

`/README.md` 에 한 줄 추가:
> 신규 모듈 메타 생성은 `docs/META_GENERATION_GUIDE.md` 참고.

### 4. ADR 추가 (선택)

`docs/ADR.md` 에 ADR-014: AI 메타 자동 생성 워크플로우. 다음 내용 한 페이지:
- 결정: Python CLI (`generate_meta.py`) + Spring Boot dry-run + Claude Code 다듬기 의 3단계 파이프라인.
- 이유: 완전 자동화는 도메인 휴리스틱 한계로 위험. dry-run 검증을 끼워 결함 차단.
- 트레이드오프: 라벨·옵션·width 같은 사용자-친화 다듬기는 여전히 사람·AI 협업 필요.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM

# 1) 문서
test -s docs/META_GENERATION_GUIDE.md
grep -q "generate_meta.py" docs/META_GENERATION_GUIDE.md
grep -q "dry-run" docs/META_GENERATION_GUIDE.md
grep -q "metaStatus" docs/META_GENERATION_GUIDE.md
grep -q "ADR-004" docs/META_GENERATION_GUIDE.md

# 2) 템플릿
test -d scripts/templates
test -f scripts/templates/README.md
test -f scripts/templates/example-prompt-polish.md
test -f scripts/templates/example-input-ticket.json
test -f scripts/templates/example-output-itg-ticket.json

# 3) (선택) ADR-014 추가
grep -q "ADR-014\|AI 메타 자동 생성 워크플로우" docs/ADR.md || true

# 4) 회귀: backend / frontend 영향 없음
cd backend && ./gradlew build -x test 2>&1 | tail -3
```

## 검증 절차

1. AC 통과.
2. 아키텍처 체크:
   - GUIDE 문서가 7단계 워크플로우 명시?
   - 프롬프트 템플릿이 metaStatus·systemType·groupId 보호 명시?
   - 결함 사례 (phase 3 hotfix 2건) 명시?
   - 한계 섹션이 다음 phase 책임 명확?
3. step 2 업데이트:
   - 성공 → `"summary": "docs/META_GENERATION_GUIDE.md (7단계 워크플로우 + Claude Code 라벨 다듬기 프롬프트 템플릿 + 결함 사례·한계) + scripts/templates/ (README + 입출력·프롬프트 예시 3 파일) + (선택) ADR-014. backend/frontend 회귀 없음."`

## 금지사항

- 운영 코드(backend·frontend) 수정 금지. 본 step 은 문서·템플릿.
- GUIDE 에 실 운영 데이터 (사용자명·이메일·서버명) 포함 금지. 가상 샘플.
- 프롬프트 템플릿이 metaStatus 를 PUBLISHED 로 강제하지 않게 — DRAFT 유지 명시.
- 자동 INSERT 흐름 (CLI → DB) 을 만들지 마라. 검토 단계가 워크플로우의 핵심.
- 사용자에게 LLM API 키 환경변수를 요구하지 마라 — Claude Code 가 이미 가지고 있는 것을 가정.
- 외부 도구(예: Notion·Slack) 의존성 도입 금지.
- 새 라우트·엔드포인트 추가 금지.
