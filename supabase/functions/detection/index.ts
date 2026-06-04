import { createAdminClient } from "../_shared/supabase.ts";
import { ok, err, optionsResponse } from "../_shared/response.ts";

type Admin = ReturnType<typeof createAdminClient>;
type Row = Record<string, unknown>;

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return optionsResponse();
  if (req.method !== "POST") return err("Method not allowed", 405);

  const admin = createAdminClient();

  try {
    const body = await req.json();
    const action = nonEmpty(body.action);

    switch (action) {
      case "events":
        return await handleEvents(admin, body, false);
      case "recent_events":
        return await handleEvents(admin, body, true);
      case "event_detail":
        return await handleEventDetail(admin, body);
      case "event_types":
        return await handleEventTypes(admin);
      case "update_status":
        return await handleUpdateStatus(admin, body);
      case "handle_false_positive":
        return await handleFalsePositive(admin, body);
      default:
        return err(`Unknown action: ${action ?? ""}`);
    }
  } catch (e) {
    console.error("detection error:", e);
    return err(e instanceof Error ? e.message : "Internal server error", 500);
  }
});

async function handleEvents(
  admin: Admin,
  body: Row,
  recentOnly: boolean,
) {
  const userId = nonEmpty(body.user_id);
  if (!userId) return err("user_id is required");

  const groupId = await getProfileGroupId(admin, userId);
  if (!groupId) return ok({ events: [] });

  const { data: cameras, error: cameraErr } = await admin
    .from("cameras")
    .select("camera_id")
    .eq("group_id", groupId);

  if (cameraErr) return err(cameraErr.message, 500);

  const cameraIds = uniqueNumbers((cameras ?? []).map((camera: Row) => camera.camera_id));
  if (cameraIds.length === 0) return ok({ events: [] });

  const { data: eventRows, error: eventErr } = await admin
    .from("detection_events")
    .select("event_id,camera_id,capture_id,type_id,risk_level,install_area,device_name,accuracy,status,detected_at")
    .in("camera_id", cameraIds)
    .order("detected_at", { ascending: false })
    .limit(recentOnly ? 200 : 500);

  if (eventErr) return err(eventErr.message, 500);

  const todayStart = startOfToday();
  const events = ((eventRows ?? []) as Row[]).filter((event) => {
    if (!recentOnly) return true;
    const status = nonEmpty(event.status)?.toUpperCase();
    if (status === "PENDING" || status === "REQUESTED") return true;
    return timestampMs(event.detected_at) >= todayStart.getTime();
  }).sort(compareDetectionEvents);

  if (events.length === 0) return ok({ events: [] });

  const captureMap = await getCapturesById(
    admin,
    uniqueNumbers(events.map((event) => event.capture_id)),
  );
  const eventTypeMap = await getEventTypesById(
    admin,
    uniqueNumbers(events.map((event) => event.type_id)),
  );
  const actionMap = await getLatestActionsForEvents(
    admin,
    uniqueNumbers(events.map((event) => event.event_id)),
  );
  const profileMap = await getProfilesByUserId(
    admin,
    Array.from(actionMap.values())
      .map((action) => nonEmpty(action.worker_id))
      .filter((workerId): workerId is string => Boolean(workerId)),
  );

  return ok({
    events: events.map((event) => {
      const eventId = toNumberOrNull(event.event_id);
      const capture = captureMap.get(toNumberOrNull(event.capture_id) ?? -1);
      const action = eventId === null ? undefined : actionMap.get(eventId);
      const workerId = action ? nonEmpty(action.worker_id) : null;

      return {
        event_id: eventId,
        risk_level: nonEmpty(event.risk_level),
        install_area: nonEmpty(event.install_area),
        event_name: eventTypeMap.get(toNumberOrNull(event.type_id) ?? -1) ?? null,
        detected_at: formatTimestamp(event.detected_at),
        device_name: nonEmpty(event.device_name),
        accuracy: toNumberOrNull(event.accuracy),
        status: nonEmpty(event.status) ?? "PENDING",
        worker_name: workerId ? profileMap.get(workerId) ?? null : null,
        completed_at: action ? formatTimestamp(action.completed_at) : null,
        image_url: capture ? nonEmpty(capture.image_url) : null,
      };
    }),
  });
}

async function handleEventDetail(admin: Admin, body: Row) {
  const eventId = toNumberOrNull(body.event_id);
  if (eventId === null) return err("event_id is required");

  const { data: event, error: eventErr } = await admin
    .from("detection_events")
    .select("event_id,camera_id,capture_id,type_id,risk_level,install_area,installation_address,live_url,device_name,accuracy,status,detected_at")
    .eq("event_id", eventId)
    .maybeSingle();

  if (eventErr) return err(eventErr.message, 500);
  if (!event) return err("Event not found", 404);

  const camera = await getCamera(admin, toNumberOrNull(event.camera_id));
  const eventTypeMap = await getEventTypesById(
    admin,
    uniqueNumbers([event.type_id]),
  );
  const capture = await resolveCaptureForEvent(admin, event);
  const action = await getLatestActionForEvent(admin, eventId);
  const actionImages = action
    ? await getActionImages(admin, toNumberOrNull(action.request_id))
    : [];

  return ok({
    event_id: eventId,
    risk_level: nonEmpty(event.risk_level),
    install_area: firstString(event.install_area, camera?.install_area),
    event_name: eventTypeMap.get(toNumberOrNull(event.type_id) ?? -1) ?? null,
    detected_at: formatTimestamp(event.detected_at),
    device_name: firstString(event.device_name, camera?.device_name),
    accuracy: toNumberOrNull(event.accuracy),
    status: nonEmpty(event.status) ?? "PENDING",
    installation_address: firstString(event.installation_address, camera?.installation_address),
    live_url: firstString(camera?.live_url_detail, event.live_url, camera?.live_url),
    latitude: toNumberOrNull(camera?.latitude),
    longitude: toNumberOrNull(camera?.longitude),
    capture_image_url: capture ? nonEmpty(capture.image_url) : null,
    capture_id: capture ? toNumberOrNull(capture.capture_id) : null,
    request_type: action ? nonEmpty(action.request_type) : null,
    request_title: action ? nonEmpty(action.request_title) : null,
    request_details: action ? nonEmpty(action.request_details) : null,
    action_images: actionImages,
  });
}

async function handleEventTypes(admin: Admin) {
  const { data, error } = await admin
    .from("event_types")
    .select("event_name")
    .order("id", { ascending: true });

  if (error) return err(error.message, 500);
  return ok({
    event_types: (data ?? [])
      .map((row: Row) => nonEmpty(row.event_name))
      .filter((eventName): eventName is string => Boolean(eventName)),
  });
}

async function handleUpdateStatus(admin: Admin, body: Row) {
  const eventId = toNumberOrNull(body.event_id);
  const status = nonEmpty(body.status);
  if (eventId === null || !status) return err("event_id and status are required");

  const { data, error } = await admin
    .from("detection_events")
    .update({ status })
    .eq("event_id", eventId)
    .select("event_id")
    .maybeSingle();

  if (error) return err(error.message, 500);
  if (!data) return err("Event not found", 404);
  return ok({ message: "Event status updated" });
}

async function handleFalsePositive(admin: Admin, body: Row) {
  const eventId = toNumberOrNull(body.event_id);
  const userId = nonEmpty(body.user_id);
  if (eventId === null || !userId) return err("event_id and user_id are required");

  const { data: event, error: eventErr } = await admin
    .from("detection_events")
    .select("event_id")
    .eq("event_id", eventId)
    .maybeSingle();

  if (eventErr) return err(eventErr.message, 500);
  if (!event) return err("Event not found", 404);

  const { error: insertErr } = await admin.from("action_requests").insert({
    event_id: eventId,
    requester_id: userId,
    worker_id: userId,
    request_type: "FALSE_POSITIVE",
    request_title: "False positive",
    request_details: "Marked as false positive",
    completed_at: new Date().toISOString(),
  });

  if (insertErr) return err(insertErr.message, 500);

  const { error: updateErr } = await admin
    .from("detection_events")
    .update({ status: "FALSE_POSITIVE" })
    .eq("event_id", eventId);

  if (updateErr) return err(updateErr.message, 500);
  return ok({ message: "False positive handled" });
}

async function getProfileGroupId(admin: Admin, userId: string) {
  const { data, error } = await admin
    .from("profiles")
    .select("group_id")
    .eq("user_id", userId)
    .maybeSingle();

  if (error) throw new Error(error.message);
  if (data?.group_id) return data.group_id;

  if (!looksLikeUuid(userId)) return null;

  const byAuthId = await admin
    .from("profiles")
    .select("group_id")
    .eq("id", userId)
    .maybeSingle();

  if (byAuthId.error) throw new Error(byAuthId.error.message);
  return byAuthId.data?.group_id ?? null;
}

async function getCamera(admin: Admin, cameraId: number | null) {
  if (cameraId === null) return null;

  const { data, error } = await admin
    .from("cameras")
    .select("camera_id,device_name,install_area,installation_address,live_url,live_url_detail,latitude,longitude")
    .eq("camera_id", cameraId)
    .maybeSingle();

  if (error) throw new Error(error.message);
  return (data ?? null) as Row | null;
}

async function getCapturesById(admin: Admin, captureIds: number[]) {
  const map = new Map<number, Row>();
  if (captureIds.length === 0) return map;

  const { data, error } = await admin
    .from("camera_captures")
    .select("capture_id,image_url,captured_at,event_type")
    .in("capture_id", captureIds);

  if (error) throw new Error(error.message);

  for (const row of (data ?? []) as Row[]) {
    const captureId = toNumberOrNull(row.capture_id);
    if (captureId !== null) map.set(captureId, row);
  }

  return map;
}

async function resolveCaptureForEvent(admin: Admin, event: Row) {
  const captureId = toNumberOrNull(event.capture_id);
  if (captureId !== null) {
    const { data, error } = await admin
      .from("camera_captures")
      .select("capture_id,image_url,captured_at,event_type")
      .eq("capture_id", captureId)
      .maybeSingle();

    if (error) throw new Error(error.message);
    if (data && nonEmpty(data.image_url)) return data as Row;
  }

  const cameraId = toNumberOrNull(event.camera_id);
  if (cameraId === null) return null;

  const { data, error } = await admin
    .from("camera_captures")
    .select("capture_id,image_url,captured_at,event_type")
    .eq("camera_id", cameraId)
    .not("image_url", "is", null)
    .order("captured_at", { ascending: false })
    .limit(50);

  if (error) throw new Error(error.message);

  const candidates = ((data ?? []) as Row[]).filter((capture) => {
    const eventType = nonEmpty(capture.event_type)?.toUpperCase();
    return Boolean(nonEmpty(capture.image_url)) && eventType !== "PERIODIC";
  });

  if (candidates.length === 0) return null;

  const eventMs = timestampMs(event.detected_at);
  if (!Number.isFinite(eventMs)) return candidates[0];

  return candidates.sort((left, right) => {
    return Math.abs(timestampMs(left.captured_at) - eventMs) -
      Math.abs(timestampMs(right.captured_at) - eventMs);
  })[0];
}

async function getEventTypesById(admin: Admin, typeIds: number[]) {
  const map = new Map<number, string>();
  if (typeIds.length === 0) return map;

  const { data, error } = await admin
    .from("event_types")
    .select("id,event_name")
    .in("id", typeIds);

  if (error) throw new Error(error.message);

  for (const row of (data ?? []) as Row[]) {
    const id = toNumberOrNull(row.id);
    const eventName = nonEmpty(row.event_name);
    if (id !== null && eventName) map.set(id, eventName);
  }

  return map;
}

async function getLatestActionsForEvents(admin: Admin, eventIds: number[]) {
  const map = new Map<number, Row>();
  if (eventIds.length === 0) return map;

  const { data, error } = await admin
    .from("action_requests")
    .select("request_id,event_id,worker_id,completed_at,requested_at,request_type,request_title,request_details")
    .in("event_id", eventIds);

  if (error) throw new Error(error.message);

  const rows = ((data ?? []) as Row[]).sort((left, right) => {
    return timestampMs(right.completed_at ?? right.requested_at) -
      timestampMs(left.completed_at ?? left.requested_at);
  });

  for (const row of rows) {
    const eventId = toNumberOrNull(row.event_id);
    if (eventId !== null && !map.has(eventId)) map.set(eventId, row);
  }

  return map;
}

async function getLatestActionForEvent(admin: Admin, eventId: number) {
  const actions = await getLatestActionsForEvents(admin, [eventId]);
  return actions.get(eventId) ?? null;
}

async function getProfilesByUserId(admin: Admin, userIds: string[]) {
  const map = new Map<string, string>();
  const ids = Array.from(new Set(userIds.filter(Boolean)));
  if (ids.length === 0) return map;

  const { data, error } = await admin
    .from("profiles")
    .select("user_id,name")
    .in("user_id", ids);

  if (error) throw new Error(error.message);

  for (const row of (data ?? []) as Row[]) {
    const userId = nonEmpty(row.user_id);
    const name = nonEmpty(row.name);
    if (userId && name) map.set(userId, name);
  }

  return map;
}

async function getActionImages(admin: Admin, requestId: number | null) {
  if (requestId === null) return [];

  const { data, error } = await admin
    .from("action_images")
    .select("image_url")
    .eq("request_id", requestId);

  if (error) throw new Error(error.message);

  return (data ?? [])
    .map((row: Row) => nonEmpty(row.image_url))
    .filter((imageUrl): imageUrl is string => Boolean(imageUrl));
}

function nonEmpty(value: unknown) {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function firstString(...values: unknown[]) {
  for (const value of values) {
    const str = nonEmpty(value);
    if (str) return str;
  }
  return null;
}

function toNumberOrNull(value: unknown) {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim() !== "") {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return null;
}

function uniqueNumbers(values: unknown[]) {
  return Array.from(
    new Set(
      values
        .map((value) => toNumberOrNull(value))
        .filter((value): value is number => value !== null),
    ),
  );
}

function formatTimestamp(value: unknown) {
  const text = nonEmpty(value);
  if (!text) return null;

  const match = text.match(/^(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2}:\d{2})/);
  if (match) return `${match[1]} ${match[2]}`;

  return text;
}

function timestampMs(value: unknown) {
  const text = nonEmpty(value);
  if (!text) return Number.NaN;
  const normalized = text.includes("T") ? text : text.replace(" ", "T");
  return new Date(normalized).getTime();
}

function startOfToday() {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return today;
}

function compareDetectionEvents(left: Row, right: Row) {
  const leftOpen = isOpenDetectionStatus(left.status);
  const rightOpen = isOpenDetectionStatus(right.status);
  if (leftOpen !== rightOpen) return leftOpen ? -1 : 1;
  return sortableTimestampMs(left.detected_at) - sortableTimestampMs(right.detected_at);
}

function isOpenDetectionStatus(value: unknown) {
  const status = nonEmpty(value)?.toUpperCase();
  return status === "PENDING" || status === "REQUESTED";
}

function sortableTimestampMs(value: unknown) {
  const ms = timestampMs(value);
  return Number.isFinite(ms) ? ms : 0;
}

function looksLikeUuid(value: string) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
    .test(value);
}
