# RTSP PoC 결과 — `feature_rtps_test` branch (v1)

**일자**: 2026-05-21 ~ 2026-05-22
**연관 design doc**: `.planning/explorations/2026-05-21_rtsp_mobile_relay_architecture.md`
**plan**: `~/.claude/plans/feature-rtps-test-shimmering-fiddle.md` (v3.1 + R3a)
**브랜치**: `feature_rtps_test`

---

## TL;DR — 두 가지 핵심 결론

### 결론 1. **모바일 frame sampler 는 가능하다**

design doc 의 **Approach 5 (frame sampler at scheduler cadence)** 가 실기기에서 동작 검증됨.

- 모바일 (Android 10, 실기기) 이 RTSP 영상을 받아 JPEG 1장 / 5초 cycle 로 추출 → Supabase Storage (`rtsp-poc/cam1/`) upload
- 본부 PC scheduler 가 outbound HTTPS polling 으로 download → YOLO person detector → `detection_events` INSERT + FCM 알림
- design doc 의 Approach 2 (Supabase chunk upload) 가 "dead" 였던 이유 (P4 latency 1-3초 strict) 자체를 challenge 하는 evidence
- Tailscale 같은 외부 앱 의존 0. 우리 앱 + Supabase + 본부 PC 만으로 동작

### 결론 2. **v1 PoC 는 화면 ON 의존 — 24/7 운영 후보 자격은 없음**

R3a (LibVLC + TextureView, HomeActivity bound) 의 fundamental trade-off:

- `vlcVout.setVideoView(TextureView)` 가 attached view 필요 → HomeActivity 가 foreground + 화면 ON 인 동안만 capture
- HomeActivity 종료·다른 Activity 진입·screen OFF·Doze mode 진입 → SurfaceTexture detach → frame 수신 중단
- 산업안전 시스템의 핵심 가치 = 24/7 무인 감지. **v1 은 시연 / PoC 검증용**, 6월 검단·포천 설치엔 미흡

이 한계가 production path 의 분기점:
- v2 production 후보 자격은 R3a 의 Activity-bound 한계를 푼 path 만 — **별도 검증 사이클 필요**
- 또는 design doc 의 Approach 4 (현장 PC / Jetson) 로 의사결정 이동 — V1 검증 결과 의존

---

## 1. 기술 경로 (R1 → R3 → R3a)

PoC 진행 중 두 번 fail 거쳐 R3a 로 정착. 각 fail 이 architecture 결정 evidence:

| 시도 | 라이브러리 | 결과 | 사유 |
|------|------------|------|------|
| **R1** | Media3 ExoPlayer + ImageReader (RGBA_8888) | ❌ FAIL | Android 10 의 MediaCodec output buffer (0x7fa30c06, hardware-private) 가 ImageReader RGBA_8888 (0x1) 와 mismatch. `first frame rendered` 로그까지 났지만 `acquireLatestImage()` 항상 null. **코덱스 진단**. |
| **R3** | LibVLC + MediaPlayer.takeSnapshot | ❌ COMPILE FAIL | LibVLC Android 3.6.0 의 `org.videolan.libvlc.MediaPlayer` 가 `takeSnapshot` 미노출. VLC core C API (`libvlc_video_take_snapshot`) 는 있지만 Java wrapper 가 안 띄움. iOS·desktop LibVLC 에만. **javap 직접 검증**. |
| **R3a** | LibVLC + invisible TextureView + `getBitmap(w, h)` | ✅ 동작 | `vlcVout.setVideoView(TextureView)` 가 attached view 필요 → HomeActivity 의 1×1dp invisible TextureView 안 SurfaceTexture 에 render → `textureView.getBitmap(1280, 720)` 로 frame 추출 → JPEG → Supabase upload |

### R3a 의 trade-off (= 결론 2 의 근거)

| 항목 | R3a (v1) |
|------|----------|
| Service-bound | ❌ Activity-bound (HomeActivity) |
| 24/7 운영 | ❌ 화면 ON 유지 필수 |
| OEM Doze 영향 | 큼 |
| 의존성 추가 | LibVLC AAR ~25-30MB |
| Drift X3 RTSP 호환 | ✅ (TCP 강제, hwdec=any) |
| PoC 동작 검증용 | ✅ 충분 |

---

## 2. 측정 (S6 검증 후 fill-in)

> **TBD** — 사용자가 5분 운영 + python rtsp_poc_pull.py --camera 1 --watch 검증 후 채움.

### 2.1 Phase 8 baseline vs PoC

| 항목 | Phase 8 RTSP-02 (commit `48f09ac`) | PoC (R3a) |
|------|-------------------------------------|-----------|
| Architecture | 본부 PC 가 RTSP 직접 풀링 (same LAN) | 모바일 → Supabase Storage → 본부 PC polling |
| 라이브러리 | ai_agent/snapshot.py cv2.VideoCapture | Android: LibVLC + TextureView<br>본부 PC: supabase-py storage list/download |
| 라벨 | person | person |
| Accuracy | **0.92** | **TBD** |
| Latency (capture → DB INSERT) | **3.16s** | **TBD** (예상 5-12s) |
| 외부 의존 | 0 (same LAN) | Supabase Storage (cloud relay) |

### 2.2 운영 지표 (5분 운영 측정 TBD)

- 모바일 5분 운영 동안 upload 시도 수: TBD (예상 ~60개)
- Storage `rtsp-poc/cam1/` 의 실제 누적 JPEG: TBD
- 본부 PC `rtsp_poc_pull.py --watch` 의 cycle 당 처리량: TBD
- `register_ai_event` 의 server-side dedup (`skipped`) 발동 횟수: TBD (DUP_WINDOW_MIN 기본 10분)
- 모바일 배터리 소모율 (5분, %): TBD
- 평균 JPEG 사이즈 (1280×720 q=85): TBD (예상 100-200KB)
- 본부 PC YOLO 추론 평균 (ms): TBD (Phase 8 = ~500ms, 동일 모델)

### 2.3 PASS / FAIL 판정

- [ ] `label = 'person'` row ≥ 1 in `detection_events`
- [ ] `accuracy ≥ 0.30` (DETECTOR_CONFIGS['person'].conf_thres)
- [ ] `latency_s ≤ 15` (plan v3.1 의 PoC budget)
- [ ] `--delete-after` 적용 — `rtsp-poc/cam1/` 가 polling 후 비어있음
- [ ] `.rtsp_poc_state.json` 의 `cam1` last_processed_key 가 최신 timestamp

**총평**: TBD (PASS / DONE_WITH_CONCERNS / FAIL)

---

## 3. design doc evidence 갱신

본 PoC 가 design doc 의 어느 premise 를 update 하는지:

### 3.1 Approach 5 (frame sampler) — production 후보 자격 일부 확보

- design doc 의 다크호스 (advisor 가 짚음) 가 실기기 동작 검증
- 단 v1 의 Activity-bound 한계로 **production-ready 아님** — 별도 production-path 검증 필요

### 3.2 P4 (latency 1-3초 strict) — 재검증 evidence (TBD measurement 따라)

- design doc 의 P4 답이 "1-3초 엄격" 으로 잠겼었음
- PoC latency 가 5-12초 측정되어도 person 검출 자체는 PASS = "10초도 안전 가치 손실 0" 의 사내 의사결정자 evidence
- 쓰러짐 골든타임 4분 기준 5-15초 latency 도 99% 안전 (이전 design doc 의 P4 challenge 표 참조)

### 3.3 P1 (현장 PC 가능 여부) — still binding

- PoC 가 PASS 해도 V1 검증 (검단·포천 담당자에게 "작은 박스 한 대 설치 가능?" 메시지) 결과가 architecture 결정에 여전히 binding
- V1 답이 "정책상 못 둠" 이면 → Approach 5 v2 production-ready 까지 진척 필요
- V1 답이 "비용·공간 trivial" 이면 → Approach 4 (Jetson Orin Nano) 가 더 robust path

---

## 4. v2 production path 후보

R3a 의 화면 ON 의존 한계를 풀기 위해 다음 옵션이 별 PoC 또는 spike 단계로:

### 4.1 LibVLC vout decoder-only 모드
- SurfaceTexture 의 detach·attach 라이프사이클 외에 LibVLC 가 decoded frame 을 view-attach 없이 buffer 로 받을 수 있는지
- `IVLCVout.addCallback(IVLCVout.Callback)` 의 raw callback API 검토
- 검증 부담: medium

### 4.2 R4 — RootEncoder (pedroSG94/RootEncoder)
- 이전 reject 이유 = Drift X3 RTSP 프로토콜 호환 미검증
- 현재 LibVLC 가 Drift X3 동작 확인됐으므로 RootEncoder 도 같은 카메라로 별도 PoC 가능
- Service-bound (Foreground) 가능 + frame callback 직접 노출

### 4.3 Approach 4 (현장 PC) 회귀
- V1 답이 "비용·공간 OK" 면 Jetson Orin Nano (~₩40만) 한 대 설치로 코드 변경 0
- design doc 의 가장 robust path. v2 production 가장 빠른 정착.

### 4.4 hybrid — 모바일 = thin frame uploader (R3a) + 본부 PC = current scheduler
- v1 의 PoC 도구로 demo + V1 답 대기. V1 = 안 됨 면 v2 production 으로 후속.
- v1 자체가 시연용으로 충분.

---

## 5. 다음 step

- [ ] **S6 측정**: 사용자 5분 운영 + 본부 PC pull → 2.2 의 빈칸 채우기
- [ ] **S7 fill-in**: 본 doc 의 TBD 자리 + PASS/FAIL 판정 update
- [ ] **commit**: `docs(rtsp-poc): S7 결과 — Approach 5 PASS, v1 화면 ON 의존 한계` (또는 FAIL)
- [ ] **V1 검증**: 검단·포천 담당자에게 "작은 박스 설치 가능?" 한 줄 메시지 (design doc 의 The Assignment)
- [ ] **V1 답에 따라**: Approach 4 (현장 PC) 또는 Approach 5 v2 production 별 spike
- [ ] **P4 재검증**: latency 1-3초 strict 사내 재논의 (PoC 측정값을 evidence 로)

---

## 6. 학습 / 의외성

- **R1 fail (Android 10)** — ExoPlayer + ImageReader 의 buffer format mismatch 는 documented quirk 가 아니라 실기기 검증으로만 surface. modern Android 만 검증한 R1 plan 의 premise 가 6월 검단·포천 의 현장 폰 spec 까지 가정해야 함.
- **R3 fail (LibVLC takeSnapshot)** — VLC 공식 docs 에는 takeSnapshot 가 핵심 feature 로 명시. Android Java wrapper 가 일부 API 만 노출하는 패턴 (iOS·desktop 과 차이). plan 시 "라이브러리 docs 가 platform 별 method coverage" 같은 항목 검증 필요.
- **R3a 의 Activity-bound 가 plan v3.1 의 risk #5 (화면 ON 유지) 가정과 우연 align** — risk 가 mitigation 으로 받아들여진 사례. risk 분류가 architecture 의 본질적 한계인지 일시적 가정인지 구분 중요.
- **codex 의 두 변경** (rtsp_poc_pull.py 의 skipped 처리, RtspPocService.kt 의 graceful stop) 이 PoC fail 시점에서 architecture 결정을 차분하게 만든 가치. 외부 reviewer (codex / advisor) 의 점진 보정이 큰 architecture 분기에 도움.
