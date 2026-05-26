# Phase 12 — Pre-DROP backup (Amendment P1 cross-review)

`017_tbm_v2_schema.sql` 가 운영 DB 의 4 테이블을 DROP CASCADE 하기 직전에 PostgREST anon SELECT \* 로 export 한 JSON dump.

## Files

생성 시각: 2026-05-26 08:21:22 UTC (TS = `20260526T082122Z`)

| 파일 | row count | 비고 |
|---|---|---|
| `tbm_sessions_20260526T082122Z.json` | 5 | Phase 9 시연 + dev 세션 |
| `tbm_templates_20260526T082122Z.json` | 5 | Phase 9 generic 5종 (fire/electric/height/heavy/general) — 017 가 도금 도메인 3종으로 교체 |
| `tbm_checklists_20260526T082122Z.json` | 28 | session 별 checklist row |
| `tbm_participants_20260526T082122Z.json` | 3 | 서명 참여자 |

## 복원 절차 (rollback)

`017` 적용 후 치명적 결함 발견 시:

1. `018_tbm_v2_rollback.sql` 작성 — 013 schema 로 역전 (DDL).
2. JSON dump 에서 INSERT 문 재구성:
   ```sql
   -- 예: tbm_templates 복원
   INSERT INTO public.tbm_templates
   SELECT * FROM jsonb_populate_recordset(
     null::public.tbm_templates,
     pg_read_file('<path-to-tbm_templates_20260526T082122Z.json>')::jsonb
   );
   ```
   SERIAL PK 값이 충돌할 수 있으므로 `ALTER SEQUENCE` 로 재설정 필요.
3. Realtime publication 4 테이블 ADD 재등록.
4. Storage 의 orphan signature PNG 는 cleanup 불필요 (격리됨).

## Reproduce backup

```bash
set -a; source .env; set +a
BACKUP_DIR=.planning/phases/12-tbm-redesign-kosha-guide/backup
TS=$(date -u +%Y%m%dT%H%M%SZ)
for tbl in tbm_sessions tbm_templates tbm_checklists tbm_participants; do
  curl -sS "$SUPABASE_URL/rest/v1/$tbl?select=*" \
    -H "apikey: $SUPABASE_ANON_KEY" \
    -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    > "$BACKUP_DIR/${tbl}_${TS}.json"
done
```

## 상태

- Created at: 2026-05-26
- 017 push 직전 export 완료
- Phase 9 시연 데이터 (v1.0 milestone SHIPPED, historical) — 손실되어도 service impact 0 추정, 안전망용 보존.
