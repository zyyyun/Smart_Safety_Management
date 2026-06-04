// _shared/ai_events.ts
// AI 감지 이벤트 생성 로직 공통 모듈.
//
// 사용처 :
//   - detection/index.ts::handleCreateAiEvent (사용자 호출)
//   - system/index.ts    ::handleCreateAiEvent (Python agent 호출)
//
// 두 곳의 로직이 drift 하지 않도록 한 함수로 통합. 인증 정책만 각 엔트리 포인트에서 달리 적용.

import { createAdminClient } from "./supabase.ts";
import { ok, err } from "./response.ts";
import { sendPushToUsers } from "./fcm.ts";

export type CreateAiEventParams = {
  camera_id: number;
  accuracy: number;
  risk_level: string;
  event_name: string;
  image_url?: string;
};

type Admin = ReturnType<typeof createAdminClient>;

/**
 * AI 감지 이벤트를 생성한다.
 *
 * 1. cameras 조회 → 메타데이터 수집
 * 2. event_types get-or-create
 * 3. camera_captures insert (event_type=event_name)
 * 4. detection_events insert (status=PENDING)
 * 5. 그룹 내 관리자에게 notifications insert
 * 6. risk_level === 'DANGER' 인 경우 general_manager 전체에도 추가 notifications
 */
export async function createAiEvent(
  admin: Admin,
  params: CreateAiEventParams,
): Promise<Response> {
  const { camera_id, accuracy, risk_level, event_name, image_url } = params;
  const captureImageUrl = typeof image_url === "string" ? image_url.trim() : "";

  if (!camera_id || !event_name || !risk_level) {
    return err("camera_id, event_name, risk_level are required");
  }

  // 1. Camera info
  const { data: camera, error: camErr } = await admin
    .from("cameras")
    .select(
      "camera_id, device_name, install_area, installation_address, live_url, live_url_detail, group_id",
    )
    .eq("camera_id", camera_id)
    .single();
  if (camErr || !camera) return err("Camera not found", 404);
  const eventLiveUrl =
    (typeof camera.live_url_detail === "string" && camera.live_url_detail.trim()) ||
    (typeof camera.live_url === "string" && camera.live_url.trim()) ||
    null;

  // 2. Event type get-or-create
  let eventTypeId: number;
  const { data: existingType } = await admin
    .from("event_types")
    .select("id")
    .eq("event_name", event_name)
    .maybeSingle();

  if (existingType) {
    eventTypeId = existingType.id as number;
  } else {
    const { data: newType, error: typeErr } = await admin
      .from("event_types")
      .insert({ event_name })
      .select("id")
      .single();
    if (typeErr || !newType) return err("Failed to create event type", 500);
    eventTypeId = newType.id as number;
  }

  // 2.5. Server-side dedup (Sprint B.1, 2026-05-21)
  // 같은 (camera_id, type_id) 의 직전 N분 row 가 있으면 INSERT 도, capture 생성도,
  // FCM push 도 모두 skip. agent 메모리 cooldown 이 재시작 시 reset 되는 문제와
  // 다중 detector 가 같은 frame 에서 fire 5회 연속 trigger 하는 push 폭주를
  // 서버 측에서 한 곳에서 차단. 인덱스: idx_detection_events_camera_id 이용.
  // env DUP_WINDOW_MIN: 시연 단축 = "2", 운영 기본 = "10".
  const dupWindowMin = parseInt(Deno.env.get("DUP_WINDOW_MIN") ?? "10", 10);
  if (dupWindowMin > 0) {
    const sinceIso = new Date(Date.now() - dupWindowMin * 60_000).toISOString();
    const { data: recent, error: dupErr } = await admin
      .from("detection_events")
      .select("event_id")
      .eq("camera_id", camera_id)
      .eq("type_id", eventTypeId)
      .in("status", ["PENDING", "REQUESTED"])
      .gte("detected_at", sinceIso)
      .limit(1);
    // dup 조회 실패는 INSERT 흐름 막지 않음 — 보수적으로 진행.
    if (!dupErr && recent && recent.length > 0) {
      let duplicateCaptureId: number | null = null;
      if (captureImageUrl) {
        const { data: duplicateCapture, error: duplicateCapErr } = await admin
          .from("camera_captures")
          .insert({
            camera_id,
            image_url: captureImageUrl,
            event_type: event_name,
          })
          .select("capture_id")
          .single();

        if (!duplicateCapErr && duplicateCapture) {
          duplicateCaptureId = duplicateCapture.capture_id as number;
        }
      }

      const duplicateUpdate: Record<string, unknown> = {
        live_url: eventLiveUrl,
        accuracy,
        risk_level,
        detected_at: new Date().toISOString(),
      };
      if (duplicateCaptureId !== null) {
        duplicateUpdate.capture_id = duplicateCaptureId;
      }
      await admin
        .from("detection_events")
        .update(duplicateUpdate)
        .eq("event_id", recent[0].event_id);

      return ok({
        ok: true,
        skipped: true,
        reason: "dup_window",
        dup_window_min: dupWindowMin,
        existing_event_id: recent[0].event_id,
        capture_id: duplicateCaptureId,
        capture_image_url: captureImageUrl,
      }, 200);
    }
  }

  // 3. camera_captures row
  const { data: capture, error: capErr } = await admin
    .from("camera_captures")
    .insert({
      camera_id,
      image_url: captureImageUrl,
      event_type: event_name,
    })
    .select("capture_id")
    .single();
  if (capErr || !capture) return err("Failed to create capture", 500);

  // 4. detection_events row
  const { data: detEvent, error: deErr } = await admin
    .from("detection_events")
    .insert({
      camera_id,
      device_name: camera.device_name,
      install_area: camera.install_area,
      installation_address: camera.installation_address,
      live_url: eventLiveUrl,
      accuracy,
      risk_level,
      type_id: eventTypeId,
      capture_id: capture.capture_id,
      status: "PENDING",
    })
    .select("event_id")
    .single();
  if (deErr || !detEvent) return err("Failed to create detection event", 500);

  // 5. 그룹 관리자 알림
  const groupId = camera.group_id;
  if (groupId) {
    const { data: managers } = await admin
      .from("profiles")
      .select("user_id")
      .eq("group_id", groupId)
      .eq("user_role", "manager");

    const notifications = (managers ?? []).map(
      (m: Record<string, unknown>) => ({
        user_id: m.user_id as string,
        title: `[${risk_level}] ${event_name} 감지`,
        content: `${
          camera.install_area ?? camera.device_name
        }에서 ${event_name}이(가) 감지되었습니다. (정확도: ${
          Math.round(accuracy * 100)
        }%)`,
      }),
    );

    // 6. DANGER 수준 이벤트는 general_manager 에도 통보
    if (risk_level === "DANGER") {
      const { data: generalManagers } = await admin
        .from("profiles")
        .select("user_id")
        .eq("user_role", "general_manager");

      for (const gm of generalManagers ?? []) {
        notifications.push({
          user_id: gm.user_id as string,
          title: `[DANGER] ${event_name} 감지`,
          content: `${
            camera.install_area ?? camera.device_name
          }에서 위험 이벤트 ${event_name}이(가) 감지되었습니다. (정확도: ${
            Math.round(accuracy * 100)
          }%)`,
        });
      }
    }

    if (notifications.length > 0) {
      await admin.from("notifications").insert(notifications);

      // FCM push (fire-and-forget — DB insert 흐름 보호)
      const uniqueUserIds = [
        ...new Set(notifications.map((n) => n.user_id)),
      ];
      sendPushToUsers(admin, uniqueUserIds, {
        title: `[${risk_level}] ${event_name} 감지`,
        body: `${
          camera.install_area ?? camera.device_name
        }에서 ${event_name}이(가) 감지되었습니다. (정확도: ${
          Math.round(accuracy * 100)
        }%)`,
        imageUrl: captureImageUrl || undefined,
        data: {
          type: "ai_event",
          event_id: String(detEvent.event_id),
          risk_level,
          camera_id: String(camera_id),
          ...(captureImageUrl
            ? {
              capture_image_url: captureImageUrl,
              image_url: captureImageUrl,
            }
            : {}),
        },
      }).catch((e) => console.error("[FCM] ai_events push failed:", e));
    }
  }

  return ok(
    {
      event_id: detEvent.event_id,
      capture_id: capture.capture_id,
      capture_image_url: captureImageUrl,
    },
    201,
  );
}
