import { buildCapturePath, normalizeAccuracy } from "./helpers.ts";

Deno.test("buildCapturePath uses detection bucket prefix convention", () => {
  assertEquals(
    buildCapturePath(5, 1780298670271),
    "detection/5/fire_5_1780298670271.jpg",
  );
});

Deno.test("buildCapturePath preserves prefix and adds optional entropy", () => {
  assertEquals(
    buildCapturePath(5, 1780298670271, "abc123"),
    "detection/5/fire_5_1780298670271_abc123.jpg",
  );
});

Deno.test("normalizeAccuracy clamps to zero through one", () => {
  assertEquals(normalizeAccuracy(-1), 0);
  assertEquals(normalizeAccuracy(0.75), 0.75);
  assertEquals(normalizeAccuracy(2), 1);
  assertEquals(normalizeAccuracy(Infinity), 1);
});

Deno.test("index uses canonical Korean fire event name", async () => {
  const text = await Deno.readTextFile(
    new URL("./index.ts", import.meta.url),
  );

  assertIncludes(text, 'const FIRE_EVENT_NAME = "화재";');
});

Deno.test("index binds authenticated camera visibility to registered device token before upload", async () => {
  const text = await Deno.readTextFile(
    new URL("./index.ts", import.meta.url),
  );

  assertIncludes(text, 'const userId = nonEmptyString(body.user_id);');
  assertIncludes(text, 'if (!userId) return err("user_id is required");');
  assertIncludes(text, 'const fcmToken = nonEmptyString(body.fcm_token);');
  assertIncludes(text, 'if (!fcmToken) return err("fcm_token is required");');
  assertIncludes(text, "fcmToken,");
  assertIncludes(text, 'from("profiles")');
  assertIncludes(text, 'select("group_id,user_role,fcm_token")');
  assertIncludes(text, 'select("camera_id,group_id")');
  assertIncludes(text, "String((profile as Row).fcm_token) !== fcmToken");
  assertIncludes(text, 'return err("Camera not visible for current user", 403)');
  assertBefore(text, "await requireVisibleCamera", "decodeJpegBase64");
  assertBefore(text, "await requireVisibleCamera", ".upload(path");
});

Deno.test("index rejects oversized content length before parsing JSON", async () => {
  const text = await Deno.readTextFile(
    new URL("./index.ts", import.meta.url),
  );

  assertIncludes(text, "rejectOversizedContentLength(req)");
  assertBefore(text, "rejectOversizedContentLength(req)", "await req.json()");
});

Deno.test("index rejects oversized JPEG payloads before decoding", async () => {
  const text = await Deno.readTextFile(
    new URL("./index.ts", import.meta.url),
  );

  assertIncludes(text, "MAX_JPEG_BYTES");
  assertIncludes(text, "MAX_JPEG_BASE64_LENGTH");
  assertIncludes(text, "rejectOversizedJpegBase64");
  assertIncludes(text, "jpeg_base64 payload is too large");
  assertBefore(text, "rejectOversizedJpegBase64", "decodeJpegBase64");
});

function assertEquals(actual: unknown, expected: unknown) {
  if (actual !== expected) {
    throw new Error(`Expected ${String(expected)}, got ${String(actual)}`);
  }
}

function assertIncludes(actual: string, expected: string) {
  if (!actual.includes(expected)) {
    throw new Error(`Expected source to contain ${expected}`);
  }
}

function assertBefore(actual: string, first: string, second: string) {
  const firstIndex = actual.indexOf(first);
  const secondIndex = actual.indexOf(second);
  if (firstIndex < 0 || secondIndex < 0 || firstIndex >= secondIndex) {
    throw new Error(`Expected ${first} to appear before ${second}`);
  }
}
