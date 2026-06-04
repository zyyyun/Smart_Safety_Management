import { createAdminClient, createUserClient } from "../_shared/supabase.ts";
import { err, optionsResponse } from "../_shared/response.ts";
import { createAiEvent } from "../_shared/ai_events.ts";
import { buildCapturePath, normalizeAccuracy } from "./helpers.ts";

type Body = Record<string, unknown>;
type Admin = ReturnType<typeof createAdminClient>;
type UserClient = ReturnType<typeof createUserClient>;

const CAPTURE_BUCKET = "camera-captures";
const MIN_JPEG_BYTES = 1024;
const FIRE_EVENT_NAME = "화재";

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return optionsResponse();
  if (req.method !== "POST") return err("Method not allowed", 405);

  try {
    const authError = authenticateRequest(req);
    if (authError) return authError;

    const body = await req.json() as Body;
    if (body.action !== "create_mobile_fire_event") {
      return err(`Unknown action: ${String(body.action ?? "")}`);
    }

    const cameraId = positiveInteger(body.camera_id);
    if (cameraId === null) return err("camera_id must be a positive integer");

    const jpegBase64 = nonEmptyString(body.jpeg_base64);
    if (!jpegBase64) return err("jpeg_base64 is required");

    const jpegBytes = decodeJpegBase64(jpegBase64);
    if (!isValidJpegPayload(jpegBytes)) {
      return err("jpeg_base64 must contain a valid JPEG image", 400);
    }

    const user = createUserClient(req);
    const visibilityError = await requireVisibleCamera(user, cameraId);
    if (visibilityError) return visibilityError;

    const accuracy = normalizeAccuracy(body.accuracy);
    const admin = createAdminClient();
    const timestampMs = Date.now();
    const path = buildCapturePath(cameraId, timestampMs);

    const { error: uploadError } = await admin.storage
      .from(CAPTURE_BUCKET)
      .upload(path, exactArrayBuffer(jpegBytes), {
        contentType: "image/jpeg",
        upsert: false,
      });

    if (uploadError) return err(uploadError.message, 500);

    await updateCameraLastFrameAt(admin, cameraId);

    const publicUrl = `${Deno.env.get("SUPABASE_URL")}/storage/v1/object/public/${CAPTURE_BUCKET}/${path}`;
    return await createAiEvent(admin, {
      camera_id: cameraId,
      accuracy,
      risk_level: "DANGER",
      event_name: FIRE_EVENT_NAME,
      image_url: publicUrl,
    });
  } catch (e) {
    console.error("mobile-ai-event error:", e);
    return err(e instanceof Error ? e.message : "Internal server error", 500);
  }
});

function authenticateRequest(req: Request) {
  const authHeader = req.headers.get("Authorization")?.trim();
  if (!authHeader || !authHeader.toLowerCase().startsWith("bearer ")) {
    return err("Authorization bearer token is required", 401);
  }
  return null;
}

function positiveInteger(value: unknown) {
  if (typeof value === "number" && Number.isInteger(value) && value > 0) {
    return value;
  }
  if (typeof value === "string" && /^\d+$/.test(value)) {
    const parsed = Number(value);
    if (Number.isSafeInteger(parsed) && parsed > 0) return parsed;
  }
  return null;
}

function nonEmptyString(value: unknown) {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function decodeJpegBase64(value: string) {
  try {
    const normalized = value.replace(/^data:image\/jpe?g;base64,/i, "");
    const binary = atob(normalized);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i += 1) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
  } catch {
    return new Uint8Array();
  }
}

function isValidJpegPayload(bytes: Uint8Array) {
  if (bytes.length < MIN_JPEG_BYTES) return false;
  return bytes[0] === 0xff &&
    bytes[1] === 0xd8 &&
    bytes[bytes.length - 2] === 0xff &&
    bytes[bytes.length - 1] === 0xd9;
}

function exactArrayBuffer(bytes: Uint8Array) {
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
}

async function requireVisibleCamera(user: UserClient, cameraId: number) {
  const { data, error } = await user
    .from("cameras")
    .select("camera_id")
    .eq("camera_id", cameraId)
    .maybeSingle();

  if (error || !data) {
    return err("Camera not visible for current user", 403);
  }
  return null;
}

async function updateCameraLastFrameAt(admin: Admin, cameraId: number) {
  await admin
    .from("cameras")
    .update({ last_frame_at: new Date().toISOString() })
    .eq("camera_id", cameraId);
}
