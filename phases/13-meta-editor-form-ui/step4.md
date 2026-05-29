# Step 4: actions-and-publish-flow

## 읽어야 할 파일
- `/docs/PRD.md` §5-4·`/docs/ARCHITECTURE.md` §16-1
- `/phases/13-meta-editor-form-ui/step0~3.md`
- `/frontend/src/types/meta-body.ts` ActionMeta

## 작업
**액션 편집 + 미리보기 + 발행 흐름** — 메타 편집의 마지막 단계.

### 1. `components/editor/ActionEditor.vue`
- meta.actions 편집 UI.
- 각 action 행: id·label·type(dialog-form/export/navigate/custom)·to(navigate 시).
- 추가/삭제.

### 2. `components/editor/MetaPreview.vue`
- 현재 편집 중인 bodyDraft 를 `<DynamicPage :metaId="..." :rows="mockRows" />` 로 렌더.
- mockRows 는 grid.columns 에서 추론한 가짜 데이터 5건 (각 컬럼 field 별로 "샘플 {label} 1" 등).
- 폼 미리보기: "등록" 버튼 클릭 시 DynamicForm 다이얼로그 열기.

### 3. 발행 흐름
- 발행 버튼 → 확인 다이얼로그 → `PATCH /api/meta/{id}/publish` → 성공 토스트 + 라우터 목록으로 이동.
- 이전 PUBLISHED 가 자동 DEPRECATE 됨을 안내문에 명시 (ADR-006).

### 4. 복사 흐름
- "새 버전 만들기" 버튼 → `POST /api/meta/{id}/copy` → 신규 DRAFT 의 편집 페이지로 이동.

### 5. 통합 — MetaEditorDetailPage
- 3 탭 (폼·그리드·액션) + 미리보기 사이드 패널 또는 하단 슬라이드 + 우상단 발행/복사/보관 버튼 + 좌상단 "←목록".
- 모든 편집은 client state 에서 → "저장" 버튼 누르면 PUT body, "발행" 은 별도.

### 6. 단위 테스트
- `ActionEditor.spec` — action 추가/삭제.
- `MetaPreview.spec` — mockRows 생성 로직.

## Acceptance Criteria
```bash
cd frontend
test -f src/components/editor/ActionEditor.vue
test -f src/components/editor/MetaPreview.vue
grep -q "PATCH.*publish" src/pages/system/MetaEditorDetailPage.vue || \
  grep -q "publish" src/components/editor/*.vue

pnpm type-check
pnpm lint
pnpm build
pnpm test
```

## 금지사항
- 발행 전 dry-run 검증 누락 시 발행 버튼 활성 금지.
- 미리보기 mockRows 에 실 운영 데이터 금지.
- type=navigate 의 `to` 가 외부 URL 인 경우 새 탭 강제 (보안).
- 백엔드 수정 금지.
- 발행 후 즉시 또 다른 발행 가능 (clicking 두 번) — 버튼 disable 로 방지.
