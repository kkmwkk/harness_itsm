# 예시 — Claude Code 라벨 다듬기 프롬프트

`generate_meta.py` 산출물(`example-output-itg-ticket.json`)을 Claude Code 에 다듬어달라고 요청하는 정형 프롬프트. `docs/META_GENERATION_GUIDE.md` §4 와 동일하다.

## 프롬프트

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

[JSON 첨부: example-output-itg-ticket.json 내용]
```

## 기대 변화 (예시)

골격의 라벨은 OpenAPI `description`/이름을 그대로 쓴다. 다듬기 후에는 다음과 같이 보강된다.

| 위치 | 변경 전 (골격) | 변경 후 (다듬기) |
|------|----------------|------------------|
| `grid.columns[].label` "내부 ID" | `id` 노출용 라벨 | "번호" 등 화면 친화 라벨 |
| `grid.columns[].label` "담당자 ID" | `assigneeId` | "담당자" |
| `form.fields[priority].options[].label` | `LOW` / `MEDIUM` / `HIGH` / `CRITICAL` | "낮음" / "보통" / "높음" / "긴급" |
| `form.fields[priority].options[].value` | `LOW` / … | **변경 금지** — 영문 코드 그대로 |

## 다듬은 뒤

라벨·options.label 만 바뀌었더라도 다시 dry-run 으로 재검증한다.

```bash
scripts/validate_meta.sh <다듬은-meta.json>
# → "valid": true 확인 후 INSERT(DRAFT) → 검토 → publish
```

> `metaStatus` 는 끝까지 `DRAFT` 다. PUBLISHED 전환은 `PATCH /api/meta/{id}/publish` 로만 수행한다 (라벨 다듬기 단계에서 상태를 바꾸지 않는다).
