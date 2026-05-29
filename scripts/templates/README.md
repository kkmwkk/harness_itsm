# 메타 생성 입출력 예시

`generate_meta.py` 와 함께 사용하는 입출력 예시를 모은다. 새 모듈 메타를 만들 때 어떤 입력이 어떤 산출물로 변환되는지 참고용.

- `example-input-ticket.json` — OpenAPI 사양의 `components.schemas` 부분 발췌 (`TicketCreateRequest` / `TicketSummary`). generate_meta.py 의 `--request-dto` / `--response-dto` 가 읽는 입력 형태.
- `example-output-itg-ticket.json` — 그 결과로 생성된 PageMeta JSON (`metaStatus=DRAFT`). 아직 라벨 다듬기 전 골격 상태.
- `example-prompt-polish.md` — Claude Code 라벨 다듬기 프롬프트 (보호 규칙 포함).

## 재현 방법

```bash
# example-output-itg-ticket.json 을 다시 생성하려면:
python3 scripts/generate_meta.py \
  --openapi backend/openapi/itg-api-spec.json \
  --request-dto TicketCreateRequest \
  --response-dto TicketSummary \
  --group-id itg-ticket --title "ITSM 티켓 관리" \
  --system-type ITSM --package-type PACKAGE \
  --major 1 --minor 1 --api /api/tickets
```

전체 워크플로우는 [`docs/META_GENERATION_GUIDE.md`](../../docs/META_GENERATION_GUIDE.md) 참고.
