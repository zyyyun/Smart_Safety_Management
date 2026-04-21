// System Edge Function
// Purpose: Internal (system-to-system) endpoints called by the external Python agent.
// Auth   : Caller MUST present one of:
//            - Authorization: Bearer <SYSTEM_AGENT_SECRET>   (권장, 커스텀 공유 비밀)
//            - x-system-secret: <SYSTEM_AGENT_SECRET>
//          This function is deployed with --no-verify-jwt so we verify manually.
//          SYSTEM_AGENT_SECRET는 `supabase secrets set`으로 주입.
// Actions:
//   - "camera_capture"  : insert a PERIODIC camera_captures row and enforce
//                         "最近 N장만 유지" retention (default 5). Also deletes
//                         the orphaned Storage objects.
//   - "create_ai_event" : create AI detection event (e.g. 쓰러짐). Delegates to
//                         _shared/ai_events.ts::createAiEvent (same logic as
//                         detection/index.ts so they never drift).

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { createAdminClient } from "../_shared/supabase.ts";
import { ok, err, optionsResponse } from "../_shared/response.ts";
import { createAiEvent, CreateAiEventParams } from "../_shared/ai_events.ts";

const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SYSTEM_AGENT_SECRET = Deno.env.get("SYSTEM_AGENT_SECRET") ?? "";
const CAPTURES_BUCKET = "camera-captures";
const KEEP_COUNT = 5;

// ──────────────────────────────────────────────
// Auth helper
// ──────────────────────────────────────────────
function verifyAgentSecret(req: Request): boolean {
  if (!SYSTEM_AGENT_SECRET) {
    console.error("[system] SYSTEM_AGENT_SECRET is not set");
    return false;
  }

  // 우선 커스텀 헤더 확인 (gateway가 Authorization을 건드려도 통과)
  const custom = req.headers.get("x-system-secret");
  if (custom && custom === SYSTEM_AGENT_SECRET) return true;

  // 폴백 : Authorization: Bearer <secret>
  const auth = req.headers.get("Authorization") ?? "";
  const prefix = "Bearer ";
  if (auth.startsWith(prefix) && auth.slice(prefix.length) === SYSTEM_AGENT_SECRET) {
    return true;
  }
  return false;
}

function adminClient() {
  return createClient(SUPABASE_URL, SERVICE_ROLE_KEY, {
    auth: { persistSession: false },
  });
}

// ──────────────────────────────────────────────
// Dispatcher
// ──────────────────────────────────────────────
Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return optionsResponse();
  if (req.method !== "POST") return err("Method not allowed", 405);

  if (!verifyAgentSecret(req)) {
    return err("Unauthorized: valid SYSTEM_AGENT_SECRET required", 401);
  }

  try {
    const body = await req.json();
    const { action } = body;

    switch (action) {
      case "camera_capture":
        return await handleCameraCapture(body);
      case "create_ai_event":
        return await handleCreateAiEvent(body);
      default:
        return err(`Unknown action: ${action}`);
    }
  } catch (e) {
    console.error("system error:", e);
    return err(e instanceof Error ? e.message : "Internal server error", 500);
  }
});

// ──────────────────────────────────────────────
// create_ai_event — Python agent 가 YOLO 추론 후 호출
// ──────────────────────────────────────────────
async function handleCreateAiEvent(body: Record<string, unknown>) {
  const { camera_id, accuracy, risk_level, event_name, image_url } = body as {
    camera_id?: number;
    accuracy?: number;
    risk_level?: string;
    event_name?: string;
    image_url?: string;
  };

  if (
    typeof camera_id !== "number" ||
    typeof event_name !== "string" ||
    typeof risk_level !== "string"
  ) {
    return err("camera_id(number), event_name, risk_level required");
  }

  const params: CreateAiEventParams = {
    camera_id,
    accuracy: typeof accuracy === "number" ? accuracy : 0.0,
    risk_level,
    event_name,
    image_url,
  };

  const admin = createAdminClient();
  return await createAiEvent(admin, params);
}

// ──────────────────────────────────────────────
// camera_capture — Insert + retention
// ──────────────────────────────────────────────
async function handleCameraCapture(body: Record<string, unknown>) {
  const { camera_id, image_url, storage_path } = body as {
    camera_id: number;
    image_url: string;
    storage_path?: string; // Optional "path/inside/bucket" for reliable Storage deletion
  };

  if (!camera_id || !image_url) {
    return err("camera_id and image_url are required");
  }

  const admin = adminClient();

  // 1. Insert new PERIODIC capture
  const { data: inserted, error: insertErr } = await admin
    .from("camera_captures")
    .insert({
      camera_id,
      image_url,
      event_type: "PERIODIC",
    })
    .select("capture_id, captured_at")
    .single();

  if (insertErr) return err(insertErr.message, 500);

  // 2. Retention: find PERIODIC captures beyond the most-recent KEEP_COUNT
  const { data: oldRows, error: oldErr } = await admin
    .from("camera_captures")
    .select("capture_id, image_url")
    .eq("camera_id", camera_id)
    .eq("event_type", "PERIODIC")
    .order("captured_at", { ascending: false })
    .range(KEEP_COUNT, 9999);

  if (oldErr) {
    // Non-fatal: insert succeeded, retention is best-effort.
    console.error("retention query failed:", oldErr);
    return ok({
      capture_id: inserted.capture_id,
      captured_at: inserted.captured_at,
      retention: { deleted: 0, warning: oldErr.message },
    });
  }

  if (!oldRows || oldRows.length === 0) {
    return ok({
      capture_id: inserted.capture_id,
      captured_at: inserted.captured_at,
      retention: { deleted: 0 },
    });
  }

  // 3. Delete Storage objects (best-effort) then DB rows.
  const pathsToDelete = oldRows
    .map((r) => extractStoragePath(r.image_url as string))
    .filter((p): p is string => p !== null);

  if (pathsToDelete.length > 0) {
    const { error: storageErr } = await admin
      .storage
      .from(CAPTURES_BUCKET)
      .remove(pathsToDelete);
    if (storageErr) {
      console.error("storage delete failed:", storageErr);
    }
  }

  const idsToDelete = oldRows.map((r) => r.capture_id as number);
  const { error: deleteErr } = await admin
    .from("camera_captures")
    .delete()
    .in("capture_id", idsToDelete);

  if (deleteErr) {
    console.error("db delete failed:", deleteErr);
    return ok({
      capture_id: inserted.capture_id,
      captured_at: inserted.captured_at,
      retention: { deleted: 0, warning: deleteErr.message },
    });
  }

  return ok({
    capture_id: inserted.capture_id,
    captured_at: inserted.captured_at,
    retention: { deleted: idsToDelete.length },
  });
}

// Extract the storage object path from a public Supabase Storage URL.
// Example input:
//   https://xbjqxnvemcqubjfflain.supabase.co/storage/v1/object/public/camera-captures/periodic/123/snapshot_123_1714632000.jpg
// Returns:
//   periodic/123/snapshot_123_1714632000.jpg
function extractStoragePath(url: string): string | null {
  const marker = `/object/public/${CAPTURES_BUCKET}/`;
  const idx = url.indexOf(marker);
  if (idx < 0) return null;
  return url.slice(idx + marker.length);
}
