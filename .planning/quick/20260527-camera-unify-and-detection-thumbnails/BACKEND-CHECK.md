---
quick_task: camera-unify-and-detection-thumbnails
sub_task: Task 5 — Backend image_url 반환 검증
status: follow-up-needed
date: 2026-05-27
---

# Backend 검증 보고 — `get_recent_detection_events` 가 `image_url` 반환?

## 결론

**아니오. follow-up 필요.**

Client (DTO+EventData+EventItem Composable) 의 wiring 은 Task 1~4 에서 완료됐으나,
**backend SELECT 절에 `image_url` 이 포함되지 않아 list 응답에 thumbnail 데이터가 비어 있음**.
사용자가 보고한 "AI 감지 list 가 감지 이미지 표시 안 함" 의 root cause 가 client 가 아닌
backend 측에 있음이 확인됨.

## 조사 결과

### 1. Client routing (RetrofitClient.kt:101)

```
"/get_recent_detection_events" → Edge function `detection`, action `recent_events`
```

BASE_URL = `https://xbjqxnvemcqubjfflain.supabase.co/functions/v1/`

→ `POST /functions/v1/detection` body `{"action": "recent_events", "user_id": ...}` 로 변환.

### 2. Supabase Edge Function 상태

- `supabase/functions/` 디렉터리에 **`detection` 폴더 자체가 부재**.
  존재하는 폴더: `_shared/`, `cameras/`, `notifications/`, `system/`.
- 즉 로컬 source tree 에 `detection` Edge function source 가 없음 → deploy 된 함수의 SQL/SELECT 절 검증 불가.
- 가능성:
  (a) 원격 supabase 에 별도로 deploy 된 미커밋 함수가 있음 (가장 가능성 높음 — production 가 동작 중이라면).
  (b) Routing 이 endpoint 부재로 404 떨어지고 어딘가에서 fallback (확률 낮음).

### 3. Legacy Express backend (server/get_recent_detection_events.js)

```sql
SELECT
    de.event_id, de.risk_level, de.install_area, de.device_name, de.accuracy, de.status,
    to_char(de.detected_at, 'YYYY-MM-DD HH24:MI:SS') as detected_at,
    et.event_name,
    u.name as worker_name,
    to_char(ar.completed_at, 'YYYY-MM-DD HH24:MI:SS') as completed_at
FROM detection_events de
JOIN cameras c ON de.camera_id = c.camera_id
LEFT JOIN event_types et ON de.type_id = et.id
LEFT JOIN action_requests ar ON de.event_id = ar.event_id
LEFT JOIN users u ON ar.worker_id = u.user_id
WHERE c.group_id = $1
AND (de.status IN ('PENDING', 'REQUESTED') OR de.detected_at >= CURRENT_DATE)
```

→ **SELECT 절에 `image_url` 부재**. 이 Express 서버가 hit 되더라도 image_url 못 받음.

### 4. detection_events schema (supabase/migrations/002_tables.sql:104-118)

```sql
CREATE TABLE IF NOT EXISTS public.detection_events (
    event_id              SERIAL PRIMARY KEY,
    camera_id             INTEGER REFERENCES public.cameras(camera_id),
    device_name           VARCHAR(100),
    install_area          VARCHAR(100),
    installation_address  VARCHAR(255),
    live_url              TEXT,
    accuracy              DOUBLE PRECISION,
    status                VARCHAR(20) DEFAULT 'PENDING',
    detected_at           TIMESTAMP DEFAULT now(),
    risk_level            VARCHAR(20),
    type_id               INTEGER REFERENCES public.event_types(id) ON DELETE SET NULL,
    capture_id            INTEGER REFERENCES public.camera_captures(capture_id) ON DELETE SET NULL
);
```

→ **`detection_events` 테이블에 `image_url` 칼럼 자체가 없음**.

### 5. camera_captures 테이블 (002_tables.sql:95-101)

```sql
-- ─── 8. camera_captures ───
... 
    camera_id   INTEGER REFERENCES public.cameras(camera_id),
    image_url   TEXT NOT NULL,
    captured_at TIMESTAMP DEFAULT now(),
...
```

→ image_url 은 **`camera_captures`** 에만 존재. `detection_events.capture_id` FK 로 join 해야 함.

### 6. ai_events.ts 의 detection_events INSERT (supabase/functions/_shared/ai_events.ts:195-199)

```typescript
...(captureImageUrl
    ? {
      capture_image_url: captureImageUrl,
      image_url: captureImageUrl,
    }
    : {}),
```

→ Edge function 이 INSERT 시 `image_url` field 를 detection_events 에 넣으려 하지만,
schema 에 칼럼 부재이므로 **silently ignored 또는 INSERT 자체가 fail**.
또한 `capture_image_url` 도 schema 에 없음.

`DetectionEventDetailResponse` (단건 상세) 에는 `capture_image_url` 필드가 있으므로,
어딘가에서 join 으로 채워지고 있을 가능성 (별도 detail 함수 안에서).

## Follow-up Action Items

1. **(필수) 원격 supabase 에 deploy 된 `detection` Edge function source 추적**
   - Supabase Dashboard → Functions → `detection` 의 source code 확인
   - `recent_events` action 의 SELECT 절에 `cc.image_url` 또는 `de.capture_image_url` 추가
   - join: `LEFT JOIN camera_captures cc ON de.capture_id = cc.capture_id`
   - 응답 JSON 의 각 event 에 `image_url: cc.image_url` 형태로 alias

2. **(권장) `detection_events` schema 마이그레이션**
   - `migrations/018_detection_events_image_url.sql` 신규 생성
   - `ALTER TABLE public.detection_events ADD COLUMN IF NOT EXISTS image_url TEXT;`
   - ai_events.ts 의 INSERT 가 이미 image_url 을 보내므로 INSERT 자체 정합성 확보

3. **(선택) Legacy `server/get_recent_detection_events.js` 도 동시 수정**
   - 현재 운영에서 Express 가 hit 되는지 불명확하지만 안전 차원에서 SELECT 절에 추가:
     ```sql
     LEFT JOIN camera_captures cc ON de.capture_id = cc.capture_id
     ...,
     cc.image_url as image_url
     ```

## 본 quick task 범위 결정

위 follow-up 은 **별도 backend plan** 으로 분리 (검증 필요한 supabase 원격 source + DB 마이그레이션은 본 quick task scope 밖).
본 quick task 는 client wiring 만 완료 → backend 가 image_url 을 반환하기 시작하면 **즉시 thumbnail 표시됨** (재배포 불필요, 기존 graceful null fallback 으로 안전).

**Client side 변경 0 lines: 이미 모든 wiring (DTO/EventData/Composable) 완료.**

## 추후 backend 수정 우선순위

`v1.1 Phase 13 (데이터 신뢰성 + 정보구조)` 가 backend 정합성 관련 phase 이므로 거기에 흡수 권장.
또는 검단·포천 6월 설치 전 별도 quick task 로 처리 (deploy 단순 — 단건 Edge function update).
