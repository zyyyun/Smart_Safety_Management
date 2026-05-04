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

      default:
        return err(`Unknown action: ${action}`);
    }
  } catch (e) {
    return err(e.message, 500);
  }
});
