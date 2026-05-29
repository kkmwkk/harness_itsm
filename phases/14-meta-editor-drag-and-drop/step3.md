# Step 3: e2e-drag-meta-edit

## 읽어야 할 파일
- `/phases/13-meta-editor-form-ui/step5.md` (이전 e2e)
- `/phases/14-meta-editor-drag-and-drop/step0~2.md`

## 작업
드래그앤드롭으로 메타 편집·발행 시나리오 e2e.

### Playwright spec — `frontend/e2e/meta-editor-drag.spec.ts`
```
1) admin 로그인 → 기존 itg-test-meta-editor 의 DRAFT 사본 생성 (phase 13 step 5 의 시드)
2) FormFieldEditor: 3개 필드 → 1번째와 3번째 드래그 swap
3) GridColumnEditor: 2개 컬럼 → 1번째 컬럼 width 200 → 150 변경
4) 저장 → 성공 토스트
5) 발행 → 성공
6) 메타 viewer 에서 form.fields 순서가 변경된 것 확인
7) 시각 스크린샷 baseline (meta-editor-drag.png)
```

### 보고서 — `docs/META_EDITOR_DRAG_REPORT.md`

핵심 검증:
- 드래그앤드롭으로 필드·컬럼 순서 변경.
- width 인라인 편집.
- 발행 후 메타 본문에 새 순서 반영.

## Acceptance Criteria
```bash
cd frontend
test -f e2e/meta-editor-drag.spec.ts
pnpm exec playwright test e2e/meta-editor-drag.spec.ts --update-snapshots 2>&1 | tail -10
test -s ../docs/META_EDITOR_DRAG_REPORT.md
```

## 금지사항
- 운영 코드 수정 금지.
- 시드 정리 금지.
- 보고서 실 운영 데이터 금지.
