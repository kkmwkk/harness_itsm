# AI 메타 자동 생성 가이드 — Polestar10 ITG v2

> 개발자가 Claude Code(또는 동등한 AI 에이전트)를 통해 PageMeta 를 자동 생성·검증·배포하는 워크플로우를 정형화한 문서.

## 1. 개요

**"메타 한 건 = 화면 한 개"** — ADR-004 의 핵심 약속을 재확인한다. 신규 화면을 만들 때 Vue 파일을 새로 쓰지 않고, `page_meta` 레코드 한 건만 등록하면 폼·그리드·목록·상세 화면이 동적으로 생성된다.

본 가이드는 Spring Boot `@Entity` 또는 OpenAPI 사양에서 **PageMeta DRAFT** 를 자동 생성하고, **dry-run** 으로 검증한 뒤 **INSERT → publish** 하는 7단계 워크플로우를 다룬다.

핵심 원칙:

- 모든 신규 메타는 `metaStatus = DRAFT` 로 시작한다 (ADR-006). 생성기·다듬기 단계에서 `PUBLISHED` 로 직접 만들지 않는다.
- 필수 3축(`systemType`·`packageType`·버전 그룹)이 누락되면 생성 자체를 거부한다 (ADR-005).
- 자동 생성은 골격까지만 책임진다. 라벨·옵션·UX 다듬기는 사람·AI 협업으로 보강한다 (§1.4, §1.6).
- DB 적용 전 반드시 dry-run 검증과 사람 검토를 거친다. CLI → DB 자동 INSERT 흐름은 의도적으로 만들지 않는다.

도구:

- `scripts/generate_meta.py` — OpenAPI JSON + DTO 이름 → PageMeta DRAFT JSON / INSERT SQL (Step 0).
- `scripts/validate_meta.sh` → `POST /api/meta/dry-run` — DB 변경 없이 형식 검증 (Step 1).

## 2. 사전 준비

1. **Docker Desktop · PostgreSQL 컨테이너 가동**

   ```bash
   docker-compose up -d
   docker-compose ps          # itg-postgres 가 healthy 인지 확인
   ```

2. **Spring Boot bootRun 가동 (8080)**

   ```bash
   cd backend
   SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
   ```

3. **OpenAPI 사양 갱신** — `backend/openapi/itg-api-spec.json` 을 최신 상태로 export.

   ```bash
   curl http://localhost:8080/v3/api-docs > backend/openapi/itg-api-spec.json
   ```

   bootRun 이 떠 있어야 `/v3/api-docs` 가 응답한다. 새 모듈의 DTO 가 OpenAPI 사양의 `components.schemas` 에 노출되어 있어야 generate_meta.py 가 읽을 수 있다.

## 3. 단계별 워크플로우

아래는 `itg-change`(ITSM 변경 관리) 모듈 메타를 새로 만드는 예시다.

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
   # stdout(/tmp/...json): PageMeta JSON 골격 (metaStatus=DRAFT 강제)
   # --output: page_meta INSERT SQL (ON CONFLICT DO NOTHING, meta_status='DRAFT')

3) (선택) Claude Code 로 라벨·옵션 다듬기 — §4 프롬프트 템플릿 참고
   # 생성기는 description/이름을 라벨로 쓰므로, 사용자 친화 한글 라벨·옵션 label 을 보강한다.

4) dry-run 검증
   scripts/validate_meta.sh /tmp/itg-change-v1-1.json
   # → "valid": true 가 아니면 issues 배열의 ERROR/WARNING 을 확인 후 메타 수정.
   # DB INSERT 는 일어나지 않는다 — 형식(필수 3축·id 패턴·grid/form/actions)만 검증.

5) DB 적용 (DRAFT)
   docker exec -i itg-postgres psql -U itg -d itgdb < sql/init/_generated/itg-change-v1-1.sql
   # DRAFT 상태로 INSERT — 아직 사용자 화면에 노출되지 않는다.

6) 검토 후 배포
   curl -X PATCH http://localhost:8080/api/meta/itg-change-v1-1/publish
   # DRAFT → PUBLISHED. 동일 groupId 의 기존 PUBLISHED 는 자동 DEPRECATED (트리거 + Service).

7) 화면 확인
   # - frontend/src/router/index.ts 의 라우트에 meta.groupId 매핑 추가
   #   (itam·itsm 기존 패턴 참조 — Vue Router 등록은 수동, §6 한계 참고)
   # - 또는 /system/meta?groupId=itg-change 로 메타 직접 확인
```

> 4단계(dry-run)와 6단계(publish) 사이의 **사람 검토** 가 이 워크플로우의 핵심 안전망이다. 자동화로 건너뛰지 않는다.

## 4. Claude Code 프롬프트 템플릿

generate_meta.py 는 OpenAPI 의 `description`·property 이름을 라벨로 그대로 쓴다. 사용자 친화 라벨·옵션 다듬기는 다음 정형 프롬프트로 Claude Code 에 요청한다. 보호 규칙을 반드시 함께 전달한다.

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

다듬은 결과는 다시 §3 의 4단계(dry-run)로 돌아가 재검증한다. 라벨·options.label 만 바뀌었어도 dry-run 을 다시 통과시키는 것이 원칙이다.

> 같은 프롬프트의 실행 예시는 `scripts/templates/example-prompt-polish.md` 참고.

## 5. 결함 사례·회피

자동 생성기와 dry-run 이 **잡지 못하는** 결함이 있다. 메타 형식만 검증하므로 백엔드 스키마·보안 설정 결함은 별도로 챙긴다.

- **`ticket_no NOT NULL` + `@GeneratedValue(IDENTITY)` 충돌** (hotfix `8d636a2`): IDENTITY 저장 시 즉시 INSERT 되는데 `ticket_no` 가 NOT NULL 이라 저장이 실패했다. 메타 생성기는 백엔드 DB 스키마 결함을 잡지 않는다. dry-run 도 form/grid 형식만 검증한다.
- **SecurityConfig permitAll 누락** (hotfix `b5f9150`): `/api/tickets/**` 가 화이트리스트에 없어 인증 없이 접근하면 막혔다. 새 모듈 적용 시 `SecurityConfig` 의 `/api/{module}/**` permitAll(또는 인가 규칙) 추가를 잊지 말 것.
- 위 두 결함은 모두 **자가 교정 단위 테스트(Mockito)로는 재현되지 않는다.** Mock 은 실제 DB 제약·시큐리티 필터 체인을 통과하지 않으므로, 이런 결함은 통합·e2e 단계에서야 드러난다.

## 6. 한계 (별도 ADR 후보 — 다음 phase 책임)

generate_meta.py + dry-run 파이프라인이 **현재 다루지 않는** 범위. 다음 phase 에서 보강 대상.

- **자산-도메인-특수 휴리스틱 미지원**: `pageMetaIdAtRegistration`(자산 등록 당시 메타 버전 보존) 같은 메타-관리용 컬럼은 generator 가 자동 인식하지 못한다. 수동 보강 필요.
- **그리드 컬럼 width·flex·pinned 자동 산정 미지원**: 컬럼 폭·고정·플렉스는 사람이 §4 다듬기 단계에서 보강한다.
- **Vue Router 등록 코드 자동 생성 미지원**: `frontend/src/router/index.ts` 에 `groupId` 라우트를 수동으로 추가해야 한다 (§3 의 7단계).
- **백엔드 스키마/보안 설정 검증 미지원**: §5 의 결함 사례처럼 DB 제약·SecurityConfig 는 메타 파이프라인 범위 밖이다.

---

- 도구 설계 근거: [`docs/ADR.md`](./ADR.md) ADR-004 / ADR-005 / ADR-006
- AI 메타 생성 파이프라인 개요: [`docs/ARCHITECTURE.md`](./ARCHITECTURE.md) §10
- 필드 타입 매핑표: [`docs/ARCHITECTURE.md`](./ARCHITECTURE.md) §5
