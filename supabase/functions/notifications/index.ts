import { createAdminClient } from "../_shared/supabase.ts";
import { ok, err, optionsResponse } from "../_shared/response.ts";
import { sendPushToUser, sendPushToUsers } from "../_shared/fcm.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return optionsResponse();

  try {
    const { action, ...body } = await req.json();
    const supabase = createAdminClient();

    switch (action) {
      // ──────────────────────────────────────────
      // LIST
      // ──────────────────────────────────────────
      case "list": {
        const { user_id } = body;
        if (!user_id) return err("user_id is required");

        const { data, error } = await supabase
          .from("notifications")
          .select("*")
          .eq("user_id", user_id)
          .order("created_at", { ascending: false });

        if (error) return err(error.message, 500);
        return ok({ notifications: data });
      }

      // ──────────────────────────────────────────
      // MARK_READ (deletes notification(s))
      // ──────────────────────────────────────────
      case "mark_read": {
        const { user_id, notification_id } = body;
        if (!user_id) return err("user_id is required");

        if (notification_id) {
          const { error } = await supabase
            .from("notifications")
            .delete()
            .eq("id", notification_id)
            .eq("user_id", user_id);
          if (error) return err(error.message, 500);
        } else {
          const { error } = await supabase
            .from("notifications")
            .delete()
            .eq("user_id", user_id);
          if (error) return err(error.message, 500);
        }

        return ok({ success: true });
      }

      // ──────────────────────────────────────────
      // SEND_GROUP
      // ──────────────────────────────────────────
      case "send_group": {
        const { sender_id, title, content } = body;
        if (!sender_id || !title || !content) {
          return err("sender_id, title, content are required");
        }

        // Get sender's group_id
        const { data: sender, error: sErr } = await supabase
          .from("profiles")
          .select("group_id")
          .eq("user_id", sender_id)
          .single();

        if (sErr || !sender) return err("Sender profile not found", 404);

        // Get all workers in that group
        const { data: members, error: mErr } = await supabase
          .from("profiles")
          .select("user_id")
          .eq("group_id", sender.group_id);

        if (mErr) return err(mErr.message, 500);
        if (!members || members.length === 0) {
          return ok({ success: true, count: 0 });
        }

        const rows = members.map((m) => ({
          user_id: m.user_id,
          title,
          content,
        }));

        const { error: insErr } = await supabase
          .from("notifications")
          .insert(rows);

        if (insErr) return err(insErr.message, 500);

        sendPushToUsers(supabase, members.map((m) => m.user_id), {
          title,
          body: content,
          data: { type: "group_message", sender_id },
        }).catch((e) => console.error("[FCM] send_group push failed:", e));

        return ok({ success: true, count: rows.length });
      }

      // ──────────────────────────────────────────
      // SEND_INDIVIDUAL
      // ──────────────────────────────────────────
      case "send_individual": {
        const { user_id, title, content } = body;
        if (!user_id || !title || !content) {
          return err("user_id, title, content are required");
        }

        const { error } = await supabase
          .from("notifications")
          .insert({ user_id, title, content });

        if (error) return err(error.message, 500);

        sendPushToUser(supabase, user_id, {
          title,
          body: content,
          data: { type: "individual_message" },
        }).catch((e) =>
          console.error("[FCM] send_individual push failed:", e)
        );

        return ok({ success: true });
      }

      // ──────────────────────────────────────────
      // WATCH_ALERT — J2208A 워치 위험 알림 (per CONTEXT.md D-11·D-12)
      // ──────────────────────────────────────────
      // BLE 클라이언트 (j2208a/supabase_writer.py:call_watch_alert_edge_function)
      // 가 service_role 로 호출. payload 계약은 그쪽 함수와 1:1 매칭.
      // alert_type / severity 화이트리스트는 010_watch_pipeline.sql 의
      // safety_alerts CHECK constraint 와 동일 — Edge Function 도 한 번 더 검증
      // (T-04-12 STRIDE: 신뢰 경계 위 tampering 차단).
      // 'NORMAL' severity 는 resolution 알림 전용 — DB 인서트는 안 가지만
      // FCM payload data 필드로 전달되어 클라이언트가 색상/아이콘 분기 가능.
      case "watch-alert": {
        const { user_id, alert_type, severity, alert_id, title, body: alertBody } = body;
        if (!user_id || !alert_type || !severity || !title || !alertBody) {
          return err("user_id, alert_type, severity, title, body are required");
        }

        const ALLOWED_TYPES = new Set(["TACHY", "REMOVED", "COMMS_LOST"]);
        const ALLOWED_SEV = new Set(["CAUTION", "WARNING", "DANGER", "NORMAL"]);
        if (!ALLOWED_TYPES.has(alert_type)) {
          return err(`Invalid alert_type: ${alert_type}`);
        }
        if (!ALLOWED_SEV.has(severity)) {
          return err(`Invalid severity: ${severity}`);
        }

        // 알림 트레이용 DB 인서트 (UI 알림 목록 표시 — 기존 send_individual 패턴과 동일)
        const { error: insErr } = await supabase
          .from("notifications")
          .insert({ user_id, title, content: alertBody });
        if (insErr) {
          console.error("[watch-alert] notifications insert failed:", insErr.message);
        }

        // FCM 푸시 — _shared/fcm.ts 재사용 (D-11)
        sendPushToUser(supabase, user_id, {
          title,
          body: alertBody,
          data: {
            type: "watch_alert",
            alert_type,
            severity,
            alert_id: String(alert_id ?? ""),
          },
        }).catch((e) =>
          console.error("[FCM] watch-alert push failed:", e)
        );

        return ok({ success: true });
      }

      // ──────────────────────────────────────────
      // WATCH-ACK — Phase 7 BRIDGE-02 (per 07-CONTEXT.md D-03)
      // ──────────────────────────────────────────
      // SafetyAlertsActivity 의 acknowledge 버튼 → 본 Edge Function 호출.
      // SQL: UPDATE safety_alerts SET ack_at=now() WHERE alert_id=$1
      //      AND device_id IN (SELECT device_id FROM devices WHERE user_id=$2)
      //      AND ack_at IS NULL
      // T-7-02 mitigation (cross-worker tampering): ownership 검증은 device_id IN (...)
      // T-7-05 mitigation (clock spoofing): 서버측 new Date().toISOString() — client 시계 무시
      // D-09 알림 전이 원칙: ack 자체는 새 알림 발생 X (notifications insert / FCM 발송 안 함)
      // idempotency: .is('ack_at', null) 가드 — 두 번째 ack 는 0 rows + 404 "already acknowledged"
      // 컬럼명: ack_at (010_watch_pipeline.sql) — REQUIREMENTS.md §7 의 acknowledg* 표기는 오기 (Pitfall 5)
      case "watch-ack": {
        const { user_id, alert_id } = body;
        if (!user_id || !alert_id) {
          return err("user_id, alert_id are required");
        }

        // ownership 검증 (T-7-02): 본 user_id 가 보유한 devices 의 alert 만 ack 가능
        const { data: ownDevices, error: devErr } = await supabase
          .from("devices")
          .select("device_id")
          .eq("user_id", user_id);
        if (devErr) return err(devErr.message, 500);
        const ownDeviceIds = (ownDevices ?? []).map((d) => d.device_id);
        if (ownDeviceIds.length === 0) {
          return err("user has no devices", 404);
        }

        // ack_at 갱신 — server-side toISOString() + idempotency 가드
        const nowIso = new Date().toISOString();
        const { data, error } = await supabase
          .from("safety_alerts")
          .update({ ack_at: nowIso })
          .eq("alert_id", alert_id)
          .in("device_id", ownDeviceIds)
          .is("ack_at", null)
          .select();

        if (error) return err(error.message, 500);
        if (!data || data.length === 0) {
          return err("alert not found or already acknowledged or not owned by user", 404);
        }
        return ok({ ok: true, ack_at: data[0].ack_at, alert_id });
      }

      // ──────────────────────────────────────────
      // WATCH-PAIR — Phase 7 BRIDGE-03 (per 07-CONTEXT.md D-04b)
      // ──────────────────────────────────────────
      // SettingDeviceManagementActivity 의 J2208A 워치 섹션 → 본 Edge Function 호출.
      // op='pair'   : payload {action, op, user_id, mac_address}
      //   - MAC 형식 재검증 (T-7-03 client validation 우회 차단)
      //   - 기존 device row 의 user_id 가 NULL 이거나 본 user_id 일 때만 허용
      //   - 다른 user 가 paired 한 워치 → 409 (워치 가로채기 차단)
      //   - 같은 user 의 같은 mac 재등록 → idempotent UPDATE
      //   - 시드 외 MAC → INSERT (device_type='WATCH', serial_number='J2208A-{MAC}')
      // op='unpair' : payload {action, op, user_id}
      //   - 본인 user_id + device_type='WATCH' 의 mac_address NULL 화
      //
      // T-7-03 mitigation: WHERE (user_id IS NULL OR user_id=$2) — 다른 worker 가
      // 이미 paired 한 워치는 selectErr 후 409 응답. MAC 정규식 (대문자 정규화)
      // 으로 client validation 우회 시도 차단.
      case "watch-pair": {
        const { user_id, mac_address, op } = body;
        if (!user_id || !op) {
          return err("user_id, op are required");
        }
        if (op !== "pair" && op !== "unpair") {
          return err(`invalid op: ${op} (expected "pair" or "unpair")`);
        }

        if (op === "pair") {
          // MAC 재검증 (T-7-03 — 클라이언트 정규식 우회 차단)
          if (!mac_address || typeof mac_address !== "string") {
            return err("mac_address is required for op=pair");
          }
          const MAC_REGEX = /^([0-9A-F]{2}:){5}[0-9A-F]{2}$/;
          const macUpper = mac_address.toUpperCase();
          if (!MAC_REGEX.test(macUpper)) {
            return err(`invalid MAC format: ${mac_address}`, 400);
          }

          // ownership 검증 (T-7-03 spoofing 차단): mac 의 device 가 (a) 미할당 or (b) 본인 보유
          const { data: existing, error: selErr } = await supabase
            .from("devices")
            .select("device_id, user_id, mac_address")
            .eq("mac_address", macUpper)
            .eq("device_type", "WATCH")
            .maybeSingle();
          if (selErr) return err(selErr.message, 500);

          if (existing && existing.user_id && existing.user_id !== user_id) {
            return err("watch already paired to another user", 409);
          }

          if (existing) {
            // idempotent UPDATE (같은 user 의 같은 mac 재등록 OK)
            const { error: updErr } = await supabase
              .from("devices")
              .update({ user_id })
              .eq("device_id", existing.device_id);
            if (updErr) return err(updErr.message, 500);
            return ok({ ok: true, device_id: existing.device_id, mac_address: macUpper, op: "pair" });
          }

          // unpair 후 re-pair 케이스: serial_number 는 유지되지만 mac 은 NULL.
          // serial_number = `J2208A-{MAC}` 로 재조회. 다른 user 가 보유 시 409.
          const expectedSerial = `J2208A-${macUpper}`;
          const { data: bySerial, error: serialErr } = await supabase
            .from("devices")
            .select("device_id, user_id")
            .eq("serial_number", expectedSerial)
            .eq("device_type", "WATCH")
            .maybeSingle();
          if (serialErr) return err(serialErr.message, 500);

          if (bySerial) {
            if (bySerial.user_id && bySerial.user_id !== user_id) {
              return err("watch already paired to another user", 409);
            }
            // mac_address + user_id 둘 다 복구 (unpair 가 mac 만 NULL 했어도, 또는 다른 변형)
            const { error: updErr } = await supabase
              .from("devices")
              .update({ mac_address: macUpper, user_id })
              .eq("device_id", bySerial.device_id);
            if (updErr) return err(updErr.message, 500);
            return ok({ ok: true, device_id: bySerial.device_id, mac_address: macUpper, op: "pair" });
          }

          // 신규 device INSERT (010 시드 외 MAC, 처음 등록)
          const { data, error: insErr } = await supabase
            .from("devices")
            .insert({
              device_type: "WATCH",
              serial_number: expectedSerial,
              mac_address: macUpper,
              user_id,
            })
            .select()
            .single();
          if (insErr) return err(insErr.message, 500);
          return ok({ ok: true, device_id: data.device_id, mac_address: macUpper, op: "pair" });
        } else {
          // op === "unpair" — 본인 워치만 mac_address NULL
          const { data, error } = await supabase
            .from("devices")
            .update({ mac_address: null })
            .eq("user_id", user_id)
            .eq("device_type", "WATCH")
            .select();
          if (error) return err(error.message, 500);
          if (!data || data.length === 0) {
            return err("no paired watch found for user", 404);
          }
          return ok({ ok: true, count: data.length, op: "unpair" });
        }
      }

      // ──────────────────────────────────────────
      // CAMERA-DOWN — Phase 8 RTSP-03 (per 08-CONTEXT.md D-03 / 08-RESEARCH §Pattern 4)
      // ──────────────────────────────────────────
      // pg_cron (012_cameras_health.sql cameras_healthcheck()) 가 5분 무수신 카메라마다
      // service_role JWT 로 호출. cron 자체가 30분 cooldown + last_alert_at + 상태 전이
      // 책임 (D-09 알림 전이 원칙) — 본 케이스는 push-only. notifications insert 없음
      // (회귀 가드: 본 case 호출로 notifications row 증가 0).
      //
      // C5 정정 (RESEARCH 정정 #5): manager 권한 사용자 N명 → sendPushToUsers (plural).
      // Phase 4 watch-alert (single-recipient) 패턴 복사 금지.
      //
      // C4 정정 (RESEARCH 정정 #4 / Pitfall 3): Option B (deadline 우선) — Android
      // channel_id 명시 X. fcm_default_channel 재사용 (Android 코드 변경 0). v1.1
      // 에서 'camera_alerts' 채널 분리.
      case "camera-down": {
        const { camera_id, group_id, last_frame_at } = body;
        if (!camera_id || group_id === undefined || group_id === null) {
          return err("camera_id and group_id are required");
        }

        // 카메라 메타 (알림 본문용 — install_area 등)
        const { data: cam, error: camErr } = await supabase
          .from("cameras")
          .select("camera_id, device_name, install_area")
          .eq("camera_id", camera_id)
          .maybeSingle();
        if (camErr) return err(camErr.message, 500);

        // manager 권한 사용자 N명 (정정 #5 — plural)
        const { data: managers, error: mgrErr } = await supabase
          .from("profiles")
          .select("user_id")
          .eq("group_id", group_id)
          .in("user_role", ["manager", "general_manager"]);
        if (mgrErr) return err(mgrErr.message, 500);

        const userIds = (managers ?? []).map((m: { user_id: string }) => m.user_id);
        if (userIds.length === 0) {
          return ok({ ok: true, sent: 0, failed: 0, skipped: 0, reason: "no managers in group" });
        }

        const camName = cam?.device_name ?? `camera-${camera_id}`;
        const camArea = cam?.install_area ?? "";
        const r = await sendPushToUsers(supabase, userIds, {
          title: "카메라 통신두절",
          body: `${camName} (${camArea}) 5분 이상 frame 무수신`,
          data: {
            type: "camera_alert",
            camera_id: String(camera_id),
            severity: "WARNING",
            last_frame_at: String(last_frame_at ?? ""),
          },
        });
        return ok({ ok: true, sent: r.sent, failed: r.failed, skipped: r.skipped });
      }

      // ──────────────────────────────────────────
      // CAMERA-RECOVERED — Phase 8 RTSP-03 회복 알림 (D-09 종료 알림 패턴)
      // ──────────────────────────────────────────
      // down→ok 전이 시점에 cron 함수가 1회 호출. camera-down 사본 — 텍스트만 변경.
      // last_frame_at 은 회복 알림에 불필요 (방금 frame 수신했음 = recover trigger).
      case "camera-recovered": {
        const { camera_id, group_id } = body;
        if (!camera_id || group_id === undefined || group_id === null) {
          return err("camera_id and group_id are required");
        }

        const { data: cam } = await supabase
          .from("cameras")
          .select("camera_id, device_name, install_area")
          .eq("camera_id", camera_id)
          .maybeSingle();

        const { data: managers, error: mgrErr } = await supabase
          .from("profiles")
          .select("user_id")
          .eq("group_id", group_id)
          .in("user_role", ["manager", "general_manager"]);
        if (mgrErr) return err(mgrErr.message, 500);

        const userIds = (managers ?? []).map((m: { user_id: string }) => m.user_id);
        if (userIds.length === 0) {
          return ok({ ok: true, sent: 0, failed: 0, skipped: 0, reason: "no managers in group" });
        }

        const camName = cam?.device_name ?? `camera-${camera_id}`;
        const camArea = cam?.install_area ?? "";
        const r = await sendPushToUsers(supabase, userIds, {
          title: "카메라 회복",
          body: `${camName} (${camArea}) frame 수신 재개`,
          data: {
            type: "camera_alert",
            camera_id: String(camera_id),
            severity: "NORMAL",
          },
        });
        return ok({ ok: true, sent: r.sent, failed: r.failed, skipped: r.skipped });
      }

      // ──────────────────────────────────────────
      // TBM-START — Phase 9 TBM-03 (per 09-CONTEXT.md D-08 / 09-RESEARCH §Pattern 4)
      // ──────────────────────────────────────────
      // 관리자가 오늘 TBM 세션을 시작. 4 단계:
      //  (1) tbm_templates 로부터 work_type 별 checklist 조회
      //  (2) tbm_sessions INSERT — UNIQUE (group_id, session_date) 충돌 시 23505 → 409
      //      (T-9-02 mitigation, Pitfall 5 회피 — DB-level UNIQUE 가 신뢰 경계)
      //  (3) tbm_checklists 에 template.checklist 의 각 항목 snapshot bulk INSERT
      //      (JSONB array order 보장 — Pitfall 11)
      //  (4) group 의 worker N명 (leader 제외) 에게 sendPushToUsers (plural) — Phase 8 패턴
      //
      // D-09 회귀 가드: notifications 테이블 insert 없음 (push-only). 상태 전이 책임은
      // tbm_sessions row 자체 + tbm_participants insert (worker checkin) + missed_alert_at
      // (cron) 이 가짐. 본 case 호출로 public.notifications row 증가 0.
      case "tbm-start": {
        const { leader_user_id, group_id, work_type, expected_end_at, location, notes } = body;
        if (!leader_user_id || !group_id || !work_type || !expected_end_at) {
          return err("leader_user_id, group_id, work_type, expected_end_at are required", 400);
        }
        // 1. work_type 검증 + checklist 조회
        const { data: tmpl, error: tErr } = await supabase
          .from("tbm_templates").select("checklist, title")
          .eq("work_type", work_type).maybeSingle();
        if (tErr) return err(tErr.message, 500);
        if (!tmpl) return err(`unknown work_type: ${work_type}`, 400);

        // 2. session insert (UNIQUE 23505 → 409, Pitfall 5)
        const today = new Date().toISOString().slice(0, 10);
        const { data: session, error: sErr } = await supabase
          .from("tbm_sessions").insert({
            group_id, session_date: today,
            expected_end_at, leader_user_id, work_type,
            location: location ?? null, notes: notes ?? null,
          }).select().maybeSingle();
        if (sErr) {
          if (sErr.code === "23505") return err("이미 오늘 세션이 존재합니다", 409);
          return err(sErr.message, 500);
        }
        if (!session) return err("session insert returned null", 500);

        // 3. checklists bulk insert (JSONB array order 보장 — Pitfall 11)
        const items = (tmpl.checklist as string[]).map((text, idx) => ({
          session_id: session.session_id, item_idx: idx, item_text: text,
        }));
        const { error: cErr } = await supabase.from("tbm_checklists").insert(items);
        if (cErr) return err(cErr.message, 500);

        // 4. group worker 전원 SELECT (leader 제외)
        const { data: workers, error: wErr } = await supabase
          .from("profiles").select("user_id")
          .eq("group_id", group_id)
          .in("user_role", ["worker", "general_manager"])
          .neq("user_id", leader_user_id);
        if (wErr) return err(wErr.message, 500);

        const workerIds = (workers ?? []).map((w: { user_id: string }) => w.user_id);
        const r = await sendPushToUsers(supabase, workerIds, {
          title: "TBM 세션 시작",
          body: `${tmpl.title} — ${workerIds.length}명 대상`,
          data: {
            type: "tbm_alert", action_in_app: "tbm-started",
            session_id: String(session.session_id), work_type,
          },
        });
        return ok({
          ok: true,
          session_id: session.session_id,
          checklist_count: items.length,
          notified_count: r.sent,
        });
      }

      // ──────────────────────────────────────────
      // TBM-CHECKIN — Phase 9 TBM-02 (per 09-CONTEXT.md D-08)
      // ──────────────────────────────────────────
      // 작업자가 자신의 폰에서 체크인 (수기 서명 후). T-9-03 (cross-group spoofing) 차단:
      // profiles.group_id 가 session.group_id 와 일치해야 함. Phase 7 watch-pair 의
      // T-7-03 mitigation 패턴 1:1 미러.
      //
      // 멱등성 — UNIQUE (session_id, user_id) 23505 충돌 시 200 idempotent 응답
      // (Pitfall 5 — 사용자 재시도 안전). existing row 의 participant_id + signed_at 을
      // DB 에서 SELECT 후 반환 (T-9-10 응답 fabrication accept — 거짓 응답 0 가능).
      case "tbm-checkin": {
        const { session_id, user_id, signature_url } = body;
        if (!session_id || !user_id) return err("session_id, user_id are required", 400);

        // 1. session 유효성 (ended_at NULL 확인)
        const { data: session, error: sErr } = await supabase
          .from("tbm_sessions").select("session_id, group_id, ended_at")
          .eq("session_id", session_id).maybeSingle();
        if (sErr) return err(sErr.message, 500);
        if (!session) return err("session not found", 404);
        if (session.ended_at) return err("session already ended", 410);

        // 2. ownership 검증 (T-9-03 spoofing 차단)
        const { data: profile, error: pErr } = await supabase
          .from("profiles").select("group_id").eq("user_id", user_id).maybeSingle();
        if (pErr) return err(pErr.message, 500);
        if (!profile) return err("user not found", 404);
        if (profile.group_id !== session.group_id) {
          return err("user not in session group", 403);
        }

        // 3. participant insert (UNIQUE 23505 → 200 idempotent)
        const { data: p, error: insErr } = await supabase
          .from("tbm_participants").insert({
            session_id, user_id,
            signature_url: signature_url ?? null,
            method: "signature",
          }).select().maybeSingle();
        if (insErr) {
          if (insErr.code === "23505") {
            const { data: existing } = await supabase
              .from("tbm_participants")
              .select("participant_id, signed_at")
              .eq("session_id", session_id).eq("user_id", user_id).maybeSingle();
            return ok({
              ok: true,
              participant_id: existing?.participant_id,
              signed_at: existing?.signed_at,
              idempotent: true,
            });
          }
          return err(insErr.message, 500);
        }
        return ok({ ok: true, participant_id: p!.participant_id, signed_at: p!.signed_at });
      }

      // ──────────────────────────────────────────
      // TBM-END — Phase 9 TBM-02 (per 09-CONTEXT.md D-08)
      // ──────────────────────────────────────────
      // 관리자가 세션 종료. T-9-04 (다른 manager 가 종료 시도) 차단:
      //   .eq("leader_user_id", leader_user_id) + .is("ended_at", null)
      // 의 조합으로 매치 0 rows → 404 응답. 정상 1 row 매치 시 ended_at = now() UPDATE.
      //
      // 클라이언트 시계 무시 — 서버측 new Date().toISOString() (T-7-05 clock spoofing 차단).
      case "tbm-end": {
        const { session_id, leader_user_id } = body;
        if (!session_id || !leader_user_id) {
          return err("session_id, leader_user_id are required", 400);
        }

        const { data, error } = await supabase
          .from("tbm_sessions")
          .update({ ended_at: new Date().toISOString() })
          .eq("session_id", session_id)
          .eq("leader_user_id", leader_user_id)
          .is("ended_at", null)
          .select().maybeSingle();
        if (error) return err(error.message, 500);
        if (!data) return err("session not found or already ended or not led by user", 404);

        const { count } = await supabase
          .from("tbm_participants").select("*", { count: "exact", head: true })
          .eq("session_id", session_id);
        return ok({ ok: true, ended_at: data.ended_at, participant_count: count ?? 0 });
      }

      // ──────────────────────────────────────────
      // TBM-MISSED — Phase 9 TBM-03 (per 09-CONTEXT.md D-05 / D-08)
      // ──────────────────────────────────────────
      // pg_cron (013_tbm_schema.sql tbm_missed_attendance_check) 이 expected_end_at + 30분
      // 경과 + missed_alert_at NULL 인 세션마다 service_role JWT 로 호출. cron 자체가
      // missed_alert_at 업데이트 + 알림 전이 1회 발사 dedup 책임 (Phase 4 D-09 / Phase 8
      // last_alert_at 패턴 1:1 미러).
      //
      // D-04 미참여 worker 정의: group_id == session.group_id AND user_role IN
      //   ('worker','general_manager') AND user_id NOT IN tbm_participants AND
      //   user_id != leader_user_id. (출근 시스템 v1.0 부재 → 그룹 worker 전원 단순화.)
      //
      // Pitfall 9 dedup: recipients = missedIds + leader_user_id. sendPushToUsers 내부
      // [...new Set(userIds)] (fcm.ts:253) 가 자연 dedup — D-04 SQL 의 leader 제외와
      // 합치면 leader 가 missedIds 에 들어갈 일은 0 이지만 방어적 dedup.
      case "tbm-missed": {
        const { session_id, group_id, leader_user_id } = body;
        if (!session_id || group_id === undefined || group_id === null || !leader_user_id) {
          return err("session_id, group_id, leader_user_id are required", 400);
        }

        // 1. session 존재 확인 (defensive — cron 이 이미 ended_at NULL 필터)
        const { data: session } = await supabase
          .from("tbm_sessions").select("session_id, ended_at")
          .eq("session_id", session_id).maybeSingle();
        if (!session) return err("session not found", 404);

        // 2. missed worker 계산 (D-04 SQL — RPC 미생성, 직접 query 채택)
        //    profiles 컬럼명: name (user_name 아님 — Plan 09-01 검증 schema)
        const { data: groupWorkers, error: gErr } = await supabase
          .from("profiles").select("user_id, name")
          .eq("group_id", group_id)
          .in("user_role", ["worker", "general_manager"])
          .neq("user_id", leader_user_id);
        if (gErr) return err(gErr.message, 500);

        const { data: joined } = await supabase
          .from("tbm_participants").select("user_id")
          .eq("session_id", session_id);
        const joinedSet = new Set((joined ?? []).map((j: { user_id: string }) => j.user_id));
        const missedIds = (groupWorkers ?? [])
          .map((w: { user_id: string }) => w.user_id)
          .filter((uid: string) => !joinedSet.has(uid));

        // 3. recipients = missedIds + leader (sendPushToUsers Set dedup — Pitfall 9)
        const recipientIds = [...missedIds, leader_user_id];
        const r = await sendPushToUsers(supabase, recipientIds, {
          title: "TBM 미참여 작업자 알림",
          body: `예정 종료 + 30분 — ${missedIds.length}명 미참여`,
          data: {
            type: "tbm_alert", action_in_app: "tbm-missed",
            session_id: String(session_id),
            missed_count: String(missedIds.length),
          },
        });
        return ok({ ok: true, missed_count: missedIds.length, notified_count: r.sent });
      }

      default:
        return err(`Unknown action: ${action}`);
    }
  } catch (e) {
    return err(e.message, 500);
  }
});
