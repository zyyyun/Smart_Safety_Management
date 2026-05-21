import { createAdminClient } from "../_shared/supabase.ts";
import { ok, err, optionsResponse } from "../_shared/response.ts";

type Admin = ReturnType<typeof createAdminClient>;

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return optionsResponse();
  if (req.method !== "POST") return err("Method not allowed", 405);

  const admin = createAdminClient();

  try {
    const body = await req.json();
    const { action } = body;

    switch (action) {
      case "list":
        return await handleList(admin, body);
      case "detail":
        return await handleDetail(admin, body);
      case "stream_info":
        return await handleStreamInfo(admin, body);
      case "delete":
        return await handleDelete(admin, body);
      case "register":
        return await handleRegister(admin, body);
      case "captures":
        return await handleCaptures(admin, body);
      default:
        return err(`Unknown action: ${action}`);
    }
  } catch (e) {
    console.error("cameras error:", e);
    return err(e instanceof Error ? e.message : "Internal server error", 500);
  }
});

// ──────────────────────────────────────────────
// list
// ──────────────────────────────────────────────
async function handleList(admin: Admin, body: Record<string, unknown>) {
  const { user_id, area, events } = body as {
    user_id: string;
    area?: string;
    events?: string[];
  };

  // 1. Get user's group_id
  const { data: profile, error: profileErr } = await admin
    .from("profiles")
    .select("group_id")
    .eq("user_id", user_id)
    .single();
  if (profileErr || !profile) return err("User not found", 404);

  const groupId = profile.group_id;
  if (!groupId) return ok({ cctv_list: [] });

  // 2. Build camera query
  let query = admin
    .from("cameras")
    .select("*")
    .eq("group_id", groupId);

  if (area) {
    query = query.ilike("install_area", `%${area}%`);
  }

  const { data: cameras, error: camErr } = await query;
  if (camErr) return err(camErr.message, 500);
  if (!cameras || cameras.length === 0) return ok({ cctv_list: [] });

  // 3. Fetch events for all cameras
  const cameraIds = cameras.map((c: Record<string, unknown>) => c.camera_id);
  const { data: cameraEvents, error: ceErr } = await admin
    .from("camera_events")
    .select("camera_id, event_type_id, event_types(id, event_name)")
    .in("camera_id", cameraIds);
  if (ceErr) return err(ceErr.message, 500);

  // 4. Group events by camera_id
  const eventsByCamera: Record<number, string[]> = {};
  for (const ce of (cameraEvents ?? [])) {
    const cid = ce.camera_id as number;
    const eventName = (ce.event_types as Record<string, unknown>)?.event_name as string;
    if (!eventsByCamera[cid]) eventsByCamera[cid] = [];
    if (eventName) eventsByCamera[cid].push(eventName);
  }

  // 5. Attach events to cameras and filter if needed
  let result = cameras.map((cam: Record<string, unknown>) => ({
    ...cam,
    events: eventsByCamera[cam.camera_id as number] ?? [],
  }));

  // 6. If events filter: only cameras that have ALL specified events
  if (events && events.length > 0) {
    result = result.filter((cam: Record<string, unknown>) => {
      const camEvents = cam.events as string[];
      return events.every((ev: string) => camEvents.includes(ev));
    });
  }

  return ok({ cctv_list: result });
}

// ──────────────────────────────────────────────
// detail
// ──────────────────────────────────────────────
async function handleDetail(admin: Admin, body: Record<string, unknown>) {
  const { camera_id } = body as { camera_id: number };

  const { data: camera, error: camErr } = await admin
    .from("cameras")
    .select("*")
    .eq("camera_id", camera_id)
    .single();
  if (camErr || !camera) return err("Camera not found", 404);

  // Get events via LEFT JOIN equivalent
  const { data: cameraEvents } = await admin
    .from("camera_events")
    .select("event_type_id, event_types(id, event_name)")
    .eq("camera_id", camera_id);

  const events = (cameraEvents ?? []).map(
    (ce: Record<string, unknown>) => (ce.event_types as Record<string, unknown>)?.event_name as string
  ).filter(Boolean);

  return ok({ ...camera, events });
}

// ──────────────────────────────────────────────
// stream_info
// ──────────────────────────────────────────────
async function handleStreamInfo(admin: Admin, body: Record<string, unknown>) {
  const { camera_id } = body as { camera_id: number };

  const { data, error } = await admin
    .from("cameras")
    .select("camera_id, live_url, live_url_detail, install_area, installation_address")
    .eq("camera_id", camera_id)
    .single();

  if (error || !data) return err("Camera not found", 404);

  // Android ExoPlayer는 imageUrl이 .mp4/.m3u8 확장자를 포함하면 즉시 재생
  // 시도를 한다. 로컬 파일 경로(예: D:/.../*.mp4)가 전달되면 네트워크 리졸브
  // 단계에서 무한 블록되어 ANR이 발생한다. 실제로 재생 가능한(http/https/rtsp)
  // URL만 클라이언트에 노출시키고, 그 외는 null 처리한다.
  const sanitized = {
    ...data,
    live_url: sanitizeStreamUrl(data.live_url as string | null),
    live_url_detail: sanitizeStreamUrl(data.live_url_detail as string | null),
  };

  return ok(sanitized);
}

function sanitizeStreamUrl(url: string | null | undefined): string | null {
  if (!url) return null;
  const trimmed = url.trim();
  if (!trimmed) return null;
  // 허용 스킴 : http(s), rtsp(s), hls/dash 계열
  return /^(https?|rtsps?):\/\//i.test(trimmed) ? trimmed : null;
}

// ──────────────────────────────────────────────
// delete
// ──────────────────────────────────────────────
async function handleDelete(admin: Admin, body: Record<string, unknown>) {
  const { camera_ids } = body as { camera_ids: number[] };

  if (!camera_ids || camera_ids.length === 0) {
    return err("camera_ids is required");
  }

  const { error } = await admin
    .from("cameras")
    .delete()
    .in("camera_id", camera_ids);

  if (error) return err(error.message, 500);
  return ok({ message: `${camera_ids.length} camera(s) deleted` });
}

// ──────────────────────────────────────────────
// register
// ──────────────────────────────────────────────
async function handleRegister(admin: Admin, body: Record<string, unknown>) {
  const {
    device_name, device_code, install_area, group_id,
    live_url, live_url_detail, installation_address,
    status, shooting_interval, environment_type,
    latitude, longitude,
  } = body as {
    device_name: string;
    device_code: string;
    install_area: string;
    group_id: number;
    live_url: string;
    live_url_detail: string;
    installation_address: string;
    status?: string;
    shooting_interval?: number;
    environment_type?: string;
    latitude?: number;
    longitude?: number;
  };

  if (!device_name || !device_code || !install_area || !group_id || !installation_address) {
    return err("device_name, device_code, install_area, group_id, installation_address are required");
  }

  const normalizedLiveUrl = normalizeRtspUrl(live_url);
  const normalizedLiveUrlDetail = normalizeRtspUrl(live_url_detail);
  if (!normalizedLiveUrl || !normalizedLiveUrlDetail) {
    return err("live_url and live_url_detail must be rtsp:// or rtsps:// URLs");
  }

  const interval = Number.isFinite(shooting_interval)
    ? Math.max(1, Math.floor(Number(shooting_interval)))
    : 1;

  const { data, error } = await admin
    .from("cameras")
    .upsert({
      device_name,
      device_code,
      install_area,
      group_id,
      live_url: normalizedLiveUrl,
      live_url_detail: normalizedLiveUrlDetail,
      installation_address,
      status: status ?? "정상",
      shooting_interval: interval,
      environment_type: environment_type ?? "실외",
      latitude: latitude ?? null,
      longitude: longitude ?? null,
      last_comm_date: new Date().toISOString(),
    }, {
      onConflict: "device_code",
    })
    .select("camera_id, live_url_detail")
    .single();

  if (error) return err(error.message, 500);
  return ok({
    camera_id: data.camera_id,
    live_url_detail: data.live_url_detail,
    yolo_agent_pickup: "live_url_detail_rtsp",
  }, 201);
}

function normalizeRtspUrl(url: string | null | undefined): string | null {
  if (!url) return null;
  const trimmed = url.trim();
  if (!/^(rtsps?):\/\/[^/\s]+\/[^\s]+$/i.test(trimmed)) return null;
  return trimmed;
}

// ──────────────────────────────────────────────
// captures
// ──────────────────────────────────────────────
async function handleCaptures(admin: Admin, body: Record<string, unknown>) {
  const { camera_id } = body as { camera_id: number };

  const { data, error } = await admin
    .from("camera_captures")
    .select("*")
    .eq("camera_id", camera_id)
    .eq("event_type", "PERIODIC")
    .order("captured_at", { ascending: false })
    .limit(3);

  if (error) return err(error.message, 500);
  return ok({ captures: data ?? [] });
}
