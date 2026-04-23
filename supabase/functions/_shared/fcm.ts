// _shared/fcm.ts
// FCM HTTP v1 API 래퍼 (Deno / Edge Function 전용).
//
// Firebase Admin SDK는 Node 런타임 전용이라 Deno 에서 사용 불가.
// 대신 서비스 계정 JSON 으로 RS256 JWT 를 self-sign → OAuth2 access_token 교환
// → FCM v1 /messages:send 엔드포인트에 직접 POST.
//
// 설계 원칙:
//   1. FCM_SERVICE_ACCOUNT 시크릿 없으면 전부 no-op (warn 1회). 기존 DB 흐름 파괴 금지.
//   2. 실패 어떤 경우에도 throw 하지 않음 — caller 의 .catch 는 안전장치일 뿐.
//   3. Access token 은 모듈 캐시 + 동시 race 방지 (pending Promise 패턴).
//   4. UNREGISTERED 토큰은 profiles.fcm_token null 로 자동 정리.
//
// Android 측 대응: 알림 채널 ID 는 아래 ANDROID_CHANNEL_ID 상수가
// app/src/main/java/com/example/smart_safety_management/MyFirebaseMessagingService.kt:48 과
// 정확히 일치해야 함. 변경 시 양쪽 동시에 수정.

import { createAdminClient } from "./supabase.ts";

type Admin = ReturnType<typeof createAdminClient>;

// Android foreground/background 수신 시 사용되는 알림 채널 ID.
// MyFirebaseMessagingService.kt:48 과 동일해야 중요도·진동 설정이 일치.
const ANDROID_CHANNEL_ID = "fcm_default_channel";

export type FcmPayload = {
  title: string;
  body: string;
  data?: Record<string, string>;
};

export type FcmSendResult = {
  sent: number;
  failed: number;
  skipped: number;
};

type ServiceAccount = {
  client_email: string;
  private_key: string;
  project_id: string;
  token_uri?: string;
};

// ──────────────────────────────────────────────
// 서비스 계정 로딩
// ──────────────────────────────────────────────
let saWarned = false;
function getServiceAccount(): ServiceAccount | null {
  const raw = Deno.env.get("FCM_SERVICE_ACCOUNT");
  if (!raw) {
    if (!saWarned) {
      console.warn(
        "[FCM] FCM_SERVICE_ACCOUNT not configured — push notifications disabled (DB inserts still work)",
      );
      saWarned = true;
    }
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as ServiceAccount;
    // Secrets 저장 시 \n 이 리터럴 \\n 으로 이스케이프되는 경우 복원.
    parsed.private_key = parsed.private_key.replace(/\\n/g, "\n");
    if (!parsed.client_email || !parsed.private_key || !parsed.project_id) {
      throw new Error("service account JSON missing required fields");
    }
    return parsed;
  } catch (e) {
    if (!saWarned) {
      console.error("[FCM] service account JSON parse failed:", e);
      saWarned = true;
    }
    return null;
  }
}

// ──────────────────────────────────────────────
// Base64 / PEM 유틸
// ──────────────────────────────────────────────
function bytesToB64Url(bytes: Uint8Array): string {
  let binary = "";
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function strToB64Url(s: string): string {
  return bytesToB64Url(new TextEncoder().encode(s));
}

function pemToDer(pem: string): Uint8Array {
  const b64 = pem
    .replace(/-----BEGIN [^-]+-----/g, "")
    .replace(/-----END [^-]+-----/g, "")
    .replace(/\s+/g, "");
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}

// ──────────────────────────────────────────────
// OAuth2 access token (캐시 + race guard)
// ──────────────────────────────────────────────
let cached: { token: string; expiresAt: number } | null = null;
let pending: Promise<string> | null = null;

async function fetchAccessToken(sa: ServiceAccount): Promise<string> {
  const header = { alg: "RS256", typ: "JWT" };
  const iat = Math.floor(Date.now() / 1000);
  const claim = {
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: sa.token_uri ?? "https://oauth2.googleapis.com/token",
    iat,
    exp: iat + 3600,
  };

  const unsigned = `${strToB64Url(JSON.stringify(header))}.${
    strToB64Url(JSON.stringify(claim))
  }`;

  const keyDer = pemToDer(sa.private_key);
  const key = await crypto.subtle.importKey(
    "pkcs8",
    keyDer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sigBuf = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsigned),
  );
  const jwt = `${unsigned}.${bytesToB64Url(new Uint8Array(sigBuf))}`;

  const resp = await fetch(sa.token_uri ?? "https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }).toString(),
  });

  if (!resp.ok) {
    const errText = await resp.text().catch(() => "");
    throw new Error(`oauth token request failed: ${resp.status} ${errText}`);
  }
  const json = await resp.json() as { access_token: string; expires_in: number };
  cached = {
    token: json.access_token,
    expiresAt: Date.now() + (json.expires_in - 60) * 1000, // 60초 버퍼
  };
  return json.access_token;
}

async function getAccessToken(sa: ServiceAccount): Promise<string> {
  if (cached && cached.expiresAt > Date.now()) return cached.token;
  if (pending) return pending;
  pending = fetchAccessToken(sa).finally(() => {
    pending = null;
  });
  return pending;
}

// ──────────────────────────────────────────────
// 단건 FCM send
// ──────────────────────────────────────────────
async function fcmSendOne(
  admin: Admin,
  userId: string,
  fcmToken: string,
  accessToken: string,
  projectId: string,
  payload: FcmPayload,
): Promise<boolean> {
  const body = {
    message: {
      token: fcmToken,
      notification: { title: payload.title, body: payload.body },
      ...(payload.data ? { data: payload.data } : {}),
      android: {
        notification: { channel_id: ANDROID_CHANNEL_ID },
        priority: "HIGH",
      },
    },
  };

  const url =
    `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;
  const resp = await fetch(url, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${accessToken}`,
      "Content-Type": "application/json; charset=utf-8",
    },
    body: JSON.stringify(body),
  });

  if (resp.ok) return true;

  const errJson = await resp.json().catch(() => ({} as Record<string, unknown>));
  const details =
    (errJson as { error?: { details?: Array<Record<string, unknown>> } })
      .error?.details ?? [];
  const errCode = details
    .map((d) => d.errorCode as string | undefined)
    .find((c) => !!c);

  // 앱 삭제/재설치 등으로 무효화된 토큰 → profiles.fcm_token 정리
  if (
    errCode === "UNREGISTERED" ||
    errCode === "INVALID_ARGUMENT" ||
    resp.status === 404
  ) {
    try {
      await admin
        .from("profiles")
        .update({ fcm_token: null })
        .eq("user_id", userId);
    } catch (e) {
      console.error("[FCM] failed to clear stale fcm_token", e);
    }
  }
  console.error(
    `[FCM] send failed user=${userId} status=${resp.status} errorCode=${
      errCode ?? "unknown"
    }`,
  );
  return false;
}

// ──────────────────────────────────────────────
// Public API
// ──────────────────────────────────────────────
export async function sendPushToUsers(
  admin: Admin,
  userIds: string[],
  payload: FcmPayload,
): Promise<FcmSendResult> {
  const result: FcmSendResult = { sent: 0, failed: 0, skipped: 0 };
  const sa = getServiceAccount();
  if (!sa) {
    result.skipped = userIds.length;
    return result;
  }
  if (!userIds.length) return result;

  try {
    const unique = [...new Set(userIds)];
    const { data: rows, error } = await admin
      .from("profiles")
      .select("user_id, fcm_token")
      .in("user_id", unique);
    if (error) {
      console.error("[FCM] profiles lookup failed:", error.message);
      result.failed = unique.length;
      return result;
    }
    const entries = (rows ?? []).filter((r) =>
      typeof r.fcm_token === "string" && r.fcm_token.length > 0
    ) as Array<{ user_id: string; fcm_token: string }>;
    result.skipped = unique.length - entries.length;
    if (!entries.length) return result;

    const accessToken = await getAccessToken(sa);
    const projectId = Deno.env.get("FCM_PROJECT_ID") ?? sa.project_id;

    const outcomes = await Promise.allSettled(
      entries.map((e) =>
        fcmSendOne(admin, e.user_id, e.fcm_token, accessToken, projectId, payload)
      ),
    );
    for (const o of outcomes) {
      if (o.status === "fulfilled" && o.value) result.sent++;
      else result.failed++;
    }
  } catch (e) {
    console.error("[FCM] sendPushToUsers fatal:", e);
    result.failed += userIds.length;
  }
  return result;
}

export async function sendPushToUser(
  admin: Admin,
  userId: string,
  payload: FcmPayload,
): Promise<boolean> {
  const r = await sendPushToUsers(admin, [userId], payload);
  return r.sent > 0;
}
