# Step 5: e2e-no-code-form-editor

## 읽어야 할 파일
- `/docs/PRD.md` §8 성공 지표 "비개발자가 메타 1건 발행 시간 10분 이내"
- `/docs/ADR.md` ADR-016
- `/phases/13-meta-editor-form-ui/step0~4.md` 산출물

## 작업
**비개발 사용자 시나리오** — GUI 만으로 새 메타 그룹을 만들고 발행 → 화면 자동 생성까지의 e2e.

### 시나리오 (Playwright + cURL)

#### Playwright spec — `frontend/e2e/meta-editor.spec.ts`

```
1) admin 로그인 → /system/meta-editor 진입
2) "신규 그룹 만들기" 클릭 → 다이얼로그
3) 입력: id=itg-test-meta-editor-v1-1, title=테스트 메타,
        systemType=COMMON, packageType=PACKAGE, groupId=itg-test-meta-editor, major=1, minor=1
4) 생성 → 편집 페이지로 이동
5) FormFieldEditor: 필드 3개 추가 (title text required·priority radio with 4 options·content textarea span 2)
6) GridColumnEditor: 컬럼 2개 추가 (title text flex 1·priority priority width 110)
7) ActionEditor: create dialog-form 액션 추가
8) 저장 클릭 → 성공 토스트
9) 발행 클릭 → 확인 → 성공 토스트
10) /system/meta?groupId=itg-test-meta-editor 진입 → PUBLISHED 메타 카드 표시
11) 시각 스크린샷 baseline (meta-editor-flow.png)
```

#### cURL 검증
```bash
# 생성된 메타가 DB 에 PUBLISHED
docker exec -i itg-postgres psql -U itg -d itgdb -c \
  "SELECT meta_status FROM page_meta WHERE id='itg-test-meta-editor-v1-1';" | grep -q PUBLISHED

# active 응답
ADMIN=$(...)
curl -fsS -H "Authorization: Bearer $ADMIN" http://localhost:8080/api/meta/active/itg-test-meta-editor \
  | grep -q '"id":"itg-test-meta-editor-v1-1"'
```

### 보고서 — `docs/META_EDITOR_REPORT.md`

섹션:
- 환경
- 시나리오 11 단계 PASS
- 핵심 검증 사실:
  - **비개발 사용자가 JSON 한 줄 안 보고 메타 생성·발행** (ADR-016, PRD §8).
  - DRAFT → dry-run → PUT body → publish 흐름 자동.
  - 발행 즉시 `/system/meta?groupId=...` viewer 에서 메타 노출.
- 한계 (다음 phase):
  - 드래그앤드롭 순서 변경(phase 14).
  - 실 화면 WYSIWYG(phase 15 Stretch).
- 산출물.

## Acceptance Criteria
```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend
test -f e2e/meta-editor.spec.ts
pnpm exec playwright test e2e/meta-editor.spec.ts --update-snapshots 2>&1 | tail -10
test -s docs/META_EDITOR_REPORT.md || test -s ../docs/META_EDITOR_REPORT.md

# 시드 보존
docker exec -i itg-postgres psql -U itg -d itgdb -c \
  "SELECT meta_status FROM page_meta WHERE id='itg-test-meta-editor-v1-1';" | grep -q PUBLISHED
```

## 금지사항
- 운영 코드 수정 금지. 결함 발견 시 step 0~4 로 되돌린다.
- 시나리오 종료 후 시드 메타(`itg-test-meta-editor-*`) 정리 금지.
- 보고서에 실 운영 데이터 금지.
- 새 endpoint·새 권한 추가 금지.
