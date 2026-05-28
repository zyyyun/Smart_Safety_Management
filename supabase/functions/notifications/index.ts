import { createAdminClient } from "../_shared/supabase.ts";
import { ok, err, optionsResponse } from "../_shared/response.ts";
import { sendPushToUser, sendPushToUsers } from "../_shared/fcm.ts";

const CONTROL_LEVELS = new Set(["eliminate", "substitute", "control", "remove", "replace"]);
const TARGET_DETECTORS = new Set(["fire", "helmet", "forklift", "person", "fall"]);

function cleanId(value: unknown, fallback: string): string {
  const text = String(value ?? fallback).trim().slice(0, 40);
  return text || fallback;
}

function cleanText(value: unknown, max = 200): string {
  return String(value ?? "").trim().slice(0, max);
}

function cleanOpsSource(item: Record<string, unknown>): Record<string, unknown> {
  const rawTemplateId = item.ops_template_id;
  const opsTemplateId = typeof rawTemplateId === "number" && Number.isInteger(rawTemplateId)
    ? rawTemplateId
    : undefined;
  const opsTitle = typeof item.ops_title === "string"
    ? cleanText(item.ops_title, 80)
    : undefined;
  return {
    ...(opsTemplateId !== undefined ? { ops_template_id: opsTemplateId } : {}),
    ...(opsTitle ? { ops_title: opsTitle } : {}),
  };
}

function cleanHazards(input: unknown): Array<Record<string, unknown>> {
  if (!Array.isArray(input)) return [];
  return input
    .slice(0, 20)
    .map((h, idx) => {
      const item = h as Record<string, unknown>;
      return {
        id: cleanId(item?.id, `h${idx + 1}`),
        text: cleanText(item?.text),
        ...cleanOpsSource(item),
      };
    })
    .filter((h) => h.text.length > 0);
}

function cleanControls(input: unknown): Array<Record<string, unknown>> {
  if (!Array.isArray(input)) return [];
  return input
    .slice(0, 30)
    .map((c, idx) => {
      const item = c as Record<string, unknown>;
      const rawLevel = cleanText(item?.level, 40);
      const level = CONTROL_LEVELS.has(rawLevel) ? rawLevel : "control";
      return {
        id: cleanId(item?.id, `c${idx + 1}`),
        hazard_id: cleanId(item?.hazard_id, ""),
        level,
        text: cleanText(item?.text),
        ...cleanOpsSource(item),
      };
    })
    .filter((c) => c.text.length > 0);
}

function cleanStringArray(input: unknown, maxItems = 20, maxChars = 180): string[] {
  if (!Array.isArray(input)) return [];
  return input
    .slice(0, maxItems)
    .map((value) => cleanText(value, maxChars))
    .filter((value) => value.length > 0);
}

/**
 * key_actions JSON whitelist: `[{id, text, is_custom?}]`.
 * Fix 2026-05-26: schema seed stores object array, Kotlin model expects same shape.
 * Previously cleanStringArray was used which created the inconsistency caught by /gsd-verify-work.
 */
function cleanActions(input: unknown): Array<Record<string, unknown>> {
  if (!Array.isArray(input)) return [];
  return input
    .slice(0, 10)
    .map((value, idx) => ({
      id: cleanId((value as Record<string, unknown>)?.id, `a${idx + 1}`),
      text: cleanText((value as Record<string, unknown>)?.text),
    }))
    .filter((a) => a.text.length > 0);
}

async function requireManager(supabase: any, userId: unknown): Promise<Response | null> {
  if (!userId) return err("user_id is required", 401);
  const { data: prof, error } = await supabase
    .from("profiles")
    .select("user_role")
    .eq("user_id", userId)
    .maybeSingle();
  if (error) return err(error.message, 500);
  if (!prof || !["manager", "general_manager"].includes(prof.user_role)) {
    return err("manager only", 403);
  }
  return null;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return optionsResponse();

  try {
    const { action, ...body } = await req.json();
    const supabase = createAdminClient();

    switch (action) {
      // ????????????????????????????????????????????????????????????????????????????????????
      // LIST
      // ????????????????????????????????????????????????????????????????????????????????????
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

      // ????????????????????????????????????????????????????????????????????????????????????
      // MARK_READ (deletes notification(s))
      // ????????????????????????????????????????????????????????????????????????????????????
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

      // ????????????????????????????????????????????????????????????????????????????????????
      // SEND_GROUP
      // ????????????????????????????????????????????????????????????????????????????????????
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

      // ????????????????????????????????????????????????????????????????????????????????????
      // SEND_INDIVIDUAL
      // ????????????????????????????????????????????????????????????????????????????????????
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

      // ????????????????????????????????????????????????????????????????????????????????????
      // (comment redacted: non-ASCII)
      // ????????????????????????????????????????????????????????????????????????????????????
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
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

        // (comment redacted: non-ASCII)
        const { error: insErr } = await supabase
          .from("notifications")
          .insert({ user_id, title, content: alertBody });
        if (insErr) {
          console.error("[watch-alert] notifications insert failed:", insErr.message);
        }

        // (comment redacted: non-ASCII)
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

      // ????????????????????????????????????????????????????????????????????????????????????
      // WATCH-ACK ??Phase 7 BRIDGE-02 (per 07-CONTEXT.md D-03)
      // ????????????????????????????????????????????????????????????????????????????????????
      // (comment redacted: non-ASCII)
      // SQL: UPDATE safety_alerts SET ack_at=now() WHERE alert_id=$1
      //      AND device_id IN (SELECT device_id FROM devices WHERE user_id=$2)
      //      AND ack_at IS NULL
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
      case "watch-ack": {
        const { user_id, alert_id } = body;
        if (!user_id || !alert_id) {
          return err("user_id, alert_id are required");
        }

        // ownership check (T-7-02): user can only ack alerts of their own devices.
        const { data: ownDevices, error: devErr } = await supabase
          .from("devices")
          .select("device_id")
          .eq("user_id", user_id);
        if (devErr) return err(devErr.message, 500);
        const ownDeviceIds = (ownDevices ?? []).map((d) => d.device_id);
        if (ownDeviceIds.length === 0) {
          return err("user has no devices", 404);
        }

        // ack_at uses server-side toISOString() + idempotency check.
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

      // ????????????????????????????????????????????????????????????????????????????????????
      // WATCH-PAIR ??Phase 7 BRIDGE-03 (per 07-CONTEXT.md D-04b)
      // ????????????????????????????????????????????????????????????????????????????????????
      // (comment redacted: non-ASCII)
      // op='pair'   : payload {action, op, user_id, mac_address}
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
      // op='unpair' : payload {action, op, user_id}
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
      case "watch-pair": {
        const { user_id, mac_address, op } = body;
        if (!user_id || !op) {
          return err("user_id, op are required");
        }
        if (op !== "pair" && op !== "unpair") {
          return err(`invalid op: ${op} (expected "pair" or "unpair")`);
        }

        if (op === "pair") {
          // (comment redacted: non-ASCII)
          if (!mac_address || typeof mac_address !== "string") {
            return err("mac_address is required for op=pair");
          }
          const MAC_REGEX = /^([0-9A-F]{2}:){5}[0-9A-F]{2}$/;
          const macUpper = mac_address.toUpperCase();
          if (!MAC_REGEX.test(macUpper)) {
            return err(`invalid MAC format: ${mac_address}`, 400);
          }

          // (comment redacted: non-ASCII)
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
            // (comment redacted: non-ASCII)
            const { error: updErr } = await supabase
              .from("devices")
              .update({ user_id })
              .eq("device_id", existing.device_id);
            if (updErr) return err(updErr.message, 500);
            return ok({ ok: true, device_id: existing.device_id, mac_address: macUpper, op: "pair" });
          }

          // (comment redacted: non-ASCII)
          // (comment redacted: non-ASCII)
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
            // (comment redacted: non-ASCII)
            const { error: updErr } = await supabase
              .from("devices")
              .update({ mac_address: macUpper, user_id })
              .eq("device_id", bySerial.device_id);
            if (updErr) return err(updErr.message, 500);
            return ok({ ok: true, device_id: bySerial.device_id, mac_address: macUpper, op: "pair" });
          }

          // (comment redacted: non-ASCII)
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
          // (comment redacted: non-ASCII)
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

      // ????????????????????????????????????????????????????????????????????????????????????
      // (comment redacted: non-ASCII)
      // ????????????????????????????????????????????????????????????????????????????????????
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
      //
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
      //
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
      case "camera-down": {
        const { camera_id, group_id, last_frame_at } = body;
        if (!camera_id || group_id === undefined || group_id === null) {
          return err("camera_id and group_id are required");
        }

        // (comment redacted: non-ASCII)
        const { data: cam, error: camErr } = await supabase
          .from("cameras")
          .select("camera_id, device_name, install_area")
          .eq("camera_id", camera_id)
          .maybeSingle();
        if (camErr) return err(camErr.message, 500);

        // (comment redacted: non-ASCII)
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
          title: "카메라 영상 중단",
          body: `${camName} (${camArea}) 영상 프레임이 들어오지 않습니다`,
          data: {
            type: "camera_alert",
            camera_id: String(camera_id),
            severity: "WARNING",
            last_frame_at: String(last_frame_at ?? ""),
          },
        });
        return ok({ ok: true, sent: r.sent, failed: r.failed, skipped: r.skipped });
      }

      // ????????????????????????????????????????????????????????????????????????????????????
      // (comment redacted: non-ASCII)
      // ????????????????????????????????????????????????????????????????????????????????????
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
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
          title: "카메라 영상 복구",
          body: `${camName} (${camArea}) 영상이 다시 들어옵니다`,
          data: {
            type: "camera_alert",
            camera_id: String(camera_id),
            severity: "NORMAL",
          },
        });
        return ok({ ok: true, sent: r.sent, failed: r.failed, skipped: r.skipped });
      }

      // ????????????????????????????????????????????????????????????????????????????????????
      // (comment redacted: non-ASCII)
      // ????????????????????????????????????????????????????????????????????????????????????
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
      //
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
      // (comment redacted: non-ASCII)
      case "tbm-start": {
        const {
          leader_user_id,
          group_id,
          work_type,
          work_scope,
          expected_end_at,
          location,
          notes,
          hazards,
          controls,
          template_ids,
          ops_titles,
          checks,
        } = body;
        if (!leader_user_id || !group_id || !work_type || !expected_end_at) {
          return err("leader_user_id, group_id, work_type, expected_end_at are required", 400);
        }
        if (!work_scope || typeof work_scope !== "string" || work_scope.trim().length === 0) {
          return err("work_scope is required", 400);
        }
        if (work_scope.length > 80) return err("work_scope must be <= 80 chars", 400);

        const { data: tmpl, error: tErr } = await supabase
          .from("tbm_templates")
          .select("checks, title, hazards, controls, is_active")
          .eq("work_type", work_type)
          .maybeSingle();
        if (tErr) return err(tErr.message, 500);
        if (!tmpl) return err(`unknown work_type: ${work_type}`, 400);
        if (tmpl.is_active === false) return err("OPS is inactive", 400);

        const safeHazards = hazards === undefined ? cleanHazards(tmpl.hazards) : cleanHazards(hazards);
        const safeControls = controls === undefined ? cleanControls(tmpl.controls) : cleanControls(controls);
        const hasClientChecks = Array.isArray(checks) && checks.length > 0;
        const safeChecks = hasClientChecks
          ? cleanStringArray(
              checks
                .map((c) => typeof c === "string" ? c : (c as Record<string, unknown>)?.text)
                .filter(Boolean),
              30,
              180,
            )
          : cleanStringArray(tmpl.checks, 30, 180);
        const safeOpsTitles = cleanStringArray(ops_titles, 20, 80);
        if (safeHazards.length === 0) return err("hazards are required", 400);
        if (safeControls.length === 0) return err("controls are required", 400);

        const today = new Date().toISOString().slice(0, 10);
        const { data: session, error: sErr } = await supabase
          .from("tbm_sessions")
          .insert({
            group_id,
            session_date: today,
            work_scope: work_scope.trim(),
            expected_end_at,
            leader_user_id,
            work_type,
            location: location ?? null,
            notes: notes ?? null,
            hazards_snapshot: safeHazards,
            controls_snapshot: safeControls,
          })
          .select()
          .maybeSingle();
        if (sErr) {
          if (sErr.code === "23505") return err(`already has today's ${work_scope} session`, 409);
          return err(sErr.message, 500);
        }
        if (!session) return err("session insert returned null", 500);

        const items = safeChecks.map((text, idx) => ({
          session_id: session.session_id,
          item_idx: idx,
          item_text: text,
          note: null,
        }));
        const { error: cErr } = await supabase.from("tbm_checklists").insert(items);
        if (cErr) return err(cErr.message, 500);

        const { data: workers, error: wErr } = await supabase
          .from("profiles")
          .select("user_id")
          .eq("group_id", group_id)
          .in("user_role", ["worker", "general_manager"])
          .neq("user_id", leader_user_id);
        if (wErr) return err(wErr.message, 500);

        const workerIds = (workers ?? []).map((w: { user_id: string }) => w.user_id);
        const r = await sendPushToUsers(supabase, workerIds, {
          title: "TBM 세션 시작",
          body: `${work_scope} - ${safeOpsTitles.length > 0 ? safeOpsTitles.join(", ") : tmpl.title}`,
          data: {
            type: "tbm_alert",
            action_in_app: "tbm-started",
            session_id: String(session.session_id),
            work_type,
            work_scope: work_scope.trim(),
            template_ids: Array.isArray(template_ids) ? template_ids.join(",") : "",
          },
        });
        return ok({
          ok: true,
          session_id: session.session_id,
          work_scope: work_scope.trim(),
          checklist_count: items.length,
          notified_count: r.sent,
        });
      }

      case "tbm-checkin": {
        const { session_id, user_id, signature_url } = body;
        if (!session_id || !user_id) return err("session_id, user_id are required", 400);

        const { data: session, error: sErr } = await supabase
          .from("tbm_sessions")
          .select("session_id, group_id, ended_at")
          .eq("session_id", session_id)
          .maybeSingle();
        if (sErr) return err(sErr.message, 500);
        if (!session) return err("session not found", 404);
        if (session.ended_at) return err("session already ended", 410);

        const { data: profile, error: pErr } = await supabase
          .from("profiles")
          .select("group_id")
          .eq("user_id", user_id)
          .maybeSingle();
        if (pErr) return err(pErr.message, 500);
        if (!profile) return err("user not found", 404);
        if (profile.group_id !== session.group_id) return err("user not in session group", 403);

        const { data: p, error: insErr } = await supabase
          .from("tbm_participants")
          .insert({ session_id, user_id, signature_url: signature_url ?? null, method: "signature" })
          .select()
          .maybeSingle();
        if (insErr) {
          if (insErr.code === "23505") {
            const { data: existing } = await supabase
              .from("tbm_participants")
              .select("participant_id, signed_at")
              .eq("session_id", session_id)
              .eq("user_id", user_id)
              .maybeSingle();
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

      case "tbm-end": {
        const { session_id, leader_user_id, key_hazard_id, feedback_notes } = body;
        if (!session_id || !leader_user_id) {
          return err("session_id, leader_user_id are required", 400);
        }

        const patch: Record<string, unknown> = { ended_at: new Date().toISOString() };
        if (key_hazard_id !== undefined) patch.key_hazard_id = cleanText(key_hazard_id, 40) || null;
        if (feedback_notes !== undefined) patch.feedback_notes = cleanText(feedback_notes, 2000) || null;

        const { data, error } = await supabase
          .from("tbm_sessions")
          .update(patch)
          .eq("session_id", session_id)
          .eq("leader_user_id", leader_user_id)
          .is("ended_at", null)
          .select()
          .maybeSingle();
        if (error) return err(error.message, 500);
        if (!data) return err("session not found or already ended or not led by user", 404);

        const { count } = await supabase
          .from("tbm_participants")
          .select("*", { count: "exact", head: true })
          .eq("session_id", session_id);
        return ok({ ok: true, ended_at: data.ended_at, participant_count: count ?? 0 });
      }

      case "tbm-missed": {
        const { session_id, group_id, leader_user_id, work_scope } = body;
        if (!session_id || group_id === undefined || group_id === null || !leader_user_id) {
          return err("session_id, group_id, leader_user_id are required", 400);
        }

        const { data: session } = await supabase
          .from("tbm_sessions")
          .select("session_id, ended_at, work_scope")
          .eq("session_id", session_id)
          .maybeSingle();
        if (!session) return err("session not found", 404);
        const scopeText = String(work_scope ?? session.work_scope ?? "");

        const { data: groupWorkers, error: gErr } = await supabase
          .from("profiles")
          .select("user_id, name")
          .eq("group_id", group_id)
          .in("user_role", ["worker", "general_manager"])
          .neq("user_id", leader_user_id);
        if (gErr) return err(gErr.message, 500);

        const { data: joined } = await supabase
          .from("tbm_participants")
          .select("user_id")
          .eq("session_id", session_id);
        const joinedSet = new Set((joined ?? []).map((j: { user_id: string }) => j.user_id));
        const missedIds = (groupWorkers ?? [])
          .map((w: { user_id: string }) => w.user_id)
          .filter((uid: string) => !joinedSet.has(uid));

        const recipientIds = [...missedIds, leader_user_id];
        const r = await sendPushToUsers(supabase, recipientIds, {
          title: "TBM 미참여 알림",
          body: `${scopeText || "TBM"} 미참여 ${missedIds.length}명`,
          data: {
            type: "tbm_alert",
            action_in_app: "tbm-missed",
            session_id: String(session_id),
            work_scope: scopeText,
            missed_count: String(missedIds.length),
          },
        });
        return ok({ ok: true, missed_count: missedIds.length, notified_count: r.sent });
      }

      case "ops-create": {
        const guard = await requireManager(supabase, body.user_id);
        if (guard) return guard;
        const workType = cleanText(body.work_type, 40).toLowerCase();
        const title = cleanText(body.title, 120);
        const safeHazards = cleanHazards(body.hazards);
        const safeControls = cleanControls(body.controls);
        if (!workType || !title) return err("work_type and title are required", 400);
        if (safeHazards.length === 0 || safeControls.length === 0) {
          return err("hazards and controls are required", 400);
        }
        const target = cleanText(body.target_detector, 20);
        const { data, error } = await supabase
          .from("tbm_templates")
          .insert({
            work_type: workType,
            title,
            description: cleanText(body.description, 500) || null,
            hazards: safeHazards,
            controls: safeControls,
            key_actions: cleanActions(body.key_actions),
            checks: cleanStringArray(body.checks),
            target_detector: TARGET_DETECTORS.has(target) ? target : null,
            is_active: body.is_active === undefined ? true : Boolean(body.is_active),
            is_custom: true,
          })
          .select("template_id, work_type, is_custom")
          .maybeSingle();
        if (error) {
          if (error.code === "23505") return err("work_type already exists", 400);
          return err(error.message, 500);
        }
        return ok({ ok: true, ...data });
      }

      case "ops-update": {
        const guard = await requireManager(supabase, body.user_id);
        if (guard) return guard;
        const templateId = Number(body.template_id);
        if (!Number.isFinite(templateId)) return err("template_id is required", 400);

        const patch: Record<string, unknown> = {};
        if (body.title !== undefined) patch.title = cleanText(body.title, 120);
        if (body.description !== undefined) patch.description = cleanText(body.description, 500) || null;
        if (body.hazards !== undefined) {
          const safeHazards = cleanHazards(body.hazards);
          if (safeHazards.length === 0) return err("hazards are required", 400);
          patch.hazards = safeHazards;
        }
        if (body.controls !== undefined) {
          const safeControls = cleanControls(body.controls);
          if (safeControls.length === 0) return err("controls are required", 400);
          patch.controls = safeControls;
        }
        if (body.key_actions !== undefined) patch.key_actions = cleanActions(body.key_actions);
        if (body.checks !== undefined) patch.checks = cleanStringArray(body.checks);
        if (body.target_detector !== undefined) {
          const target = cleanText(body.target_detector, 20);
          patch.target_detector = TARGET_DETECTORS.has(target) ? target : null;
        }
        if (body.is_active !== undefined) patch.is_active = Boolean(body.is_active);
        if (Object.keys(patch).length === 0) return err("no fields to update", 400);

        const { data, error } = await supabase
          .from("tbm_templates")
          .update(patch)
          .eq("template_id", templateId)
          .select("template_id")
          .maybeSingle();
        if (error) return err(error.message, 500);
        if (!data) return err("template not found", 404);
        return ok({ ok: true, template_id: data.template_id });
      }

      case "ops-toggle": {
        const guard = await requireManager(supabase, body.user_id);
        if (guard) return guard;
        const templateId = Number(body.template_id);
        if (!Number.isFinite(templateId)) return err("template_id is required", 400);
        if (typeof body.is_active !== "boolean") return err("is_active is required", 400);
        const { data, error } = await supabase
          .from("tbm_templates")
          .update({ is_active: body.is_active })
          .eq("template_id", templateId)
          .select("template_id, is_active")
          .maybeSingle();
        if (error) return err(error.message, 500);
        if (!data) return err("template not found", 404);
        return ok({ ok: true, template_id: data.template_id, is_active: data.is_active });
      }
      default:
        return err(`Unknown action: ${action}`);
    }
  } catch (e) {
    return err(e.message, 500);
  }
});
