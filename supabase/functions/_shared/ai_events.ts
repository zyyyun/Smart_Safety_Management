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

  if (!camera_id || !event_name || !risk_level) {
    return err("camera_id, event_name, risk_level are required");
  }

  // 1. Camera info
  const { data: camera, error: camErr } = await admin
    .from("cameras")
    .select(
      "camera_id, device_name, install_area, installation_address, live_url, group_id",
    )
    .eq("camera_id", camera_id)
    .single();
  if (camErr || !camera) return err("Camera not found", 404);

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

  // 3. camera_captures row
  const { data: capture, error: capErr } = await admin
    .from("camera_captures")
    .insert({
      camera_id,
      image_url: image_url ?? "",
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
      live_url: camera.live_url,
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
    }
  }

  return ok(
    {
      event_id: detEvent.event_id,
      capture_id: capture.capture_id,
    },
    201,
  );
}
