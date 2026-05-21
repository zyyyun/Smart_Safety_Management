# VLM 기반 위험상황 인식·탐지 AI 모델
# 파인튜닝용 데이터 구조 정의서

> **Predictive AI-based Safety System (P.A.S.S)**
> 작업자 웨어러블 디바이스를 활용한 위험상황관리 PASS 개발
>
> 주관기관 : 주식회사 애나 (ANNA Inc.) │ 버전 : v2.0 │ 발행일 : 2026-05-15

---

## 제1장. 개요

### 1.1 문서 목적 및 범위

본 문서는 P.A.S.S(Predictive AI-based Safety System) 과제에서 개발하는 VLM(Vision Language Model) 기반 위험상황 인식·탐지 AI 모델의 파인튜닝(Fine-tuning)을 위한 학습 데이터 구조를 체계적으로 정의하며 구체적으로 아래 내용을 포함한다.

- 5종 센서(스마트안전모 카메라·스마트워치·현장 CCTV·통합기상센서·**가스센서**) 입력 데이터의 포맷 및 구조
- 8개 이상상황 클래스(AC-001~AC-008) 각각의 상세 정의·트리거 조건·임계값
- 위험구역 무단 진입은 별도 클래스로 두지 않고 AC-005(지게차 가동 zone), AC-008(가스·밀폐공간 zone) 의 트리거에 통합
- 각 클래스별 파인튜닝용 입력 프롬프트 및 출력 응답 예시 (JSON 포맷)
- 학습 데이터셋 파일 구조, 규모 계획, 품질 기준, 검수 방안
- 정상 상황(Negative Sample) 데이터 구성 방안
- 비전 라벨(이미지 annotation) 표준 및 시계열 메트릭 산출 규약
- 베이스 모델 선정, 평가 지표·수용 기준, 외주 라벨링 발주 단계 게이트

### 1.2 적용 대상 시스템

| 항목 | 내용 |
|---|---|
| 과제명 | 작업자 웨어러블 디바이스를 활용한 위험상황관리 PASS 개발 |
| 주관기관 | 주식회사 애나 (ANNA Inc.) |
| 적용 모델 | VLM (Vision Language Model) 기반 sVLM — 7B 파라미터급 경량화 모델 (베이스 모델은 PoC 검증 후 확정, 후보 비교는 제10장 참조) |
| 대상 환경 | 중소 제조업 산업 현장 (철골·금속·화학·물류 등) |
| 관련 법령 | 중대재해처벌법, 산업안전보건법, KOSHA 안전 지침, 개인정보보호법 |

### 1.3 VLM 파인튜닝 데이터 구조 개요

본 시스템의 VLM 모델은 다음 구조로 파인튜닝된다.

**▶ 핵심 입출력 구조**

```
[ 입력 (Input) ]
  ① 시스템 프롬프트 : 모델 역할 정의 및 출력 형식 고정
  ② 사용자 프롬프트 : 5종 센서 실시간 데이터 + 분석 지시

[ 출력 (Output) ]
  JSON 포맷 이상상황 판단 결과
  → primary_code / secondary_codes / severity / confidence / location
     / evidence / recommended_action / class_extension 포함
```

**▶ 멀티모달 처리 방식**

영상 데이터(S-01 헬멧캠, S-03 CCTV)는 VLM이 직접 시각 처리하며, 수치 데이터(S-02 스마트워치, S-04 기상센서, S-05 가스센서)는 구조화된 텍스트로 변환하여 프롬프트에 포함한다. 모든 센서 데이터는 타임스탬프 기준 ±2초 내 데이터를 동기화하여 단일 추론 요청으로 처리한다.

**▶ Cascade 추론 파이프라인**

본 시스템은 단일 end-to-end VLM이 아닌 **다단 결합(Cascade)** 구조로 운영된다. 산업현장 주요 유독가스(H₂S, CO, CH₄, O₂ 결핍, VOC 등) 대부분이 무색·무취여서 VLM이 raw 영상에서 화학물질을 직접 검출하는 것이 원리적으로 불가능하고, 자세 변화율·충격 가속도·심박 패턴·고도 변화 등 시계열 정량 메트릭 또한 단일 VLM 프레임 추론으로 산출 불가하기 때문이다. 따라서 다음 4단 구조를 갖는다.

```
┌─ Tier-1 : 객체 검출 (YOLO detector — 운영 중) ──────────────────────────────┐
│   · person / fire / smoke / fall / no_helmet / forklift 검출 + bbox          │
│   · S-01 helmet_camera, S-03 CCTV 영상에서 실시간 동작                        │
└──────────────────────────────────────────────────────────────────────────────┘
┌─ Tier-2 : 시계열 메트릭 산출 (Pose / IMU / GPS 처리기) ─────────────────────┐
│   · S-01 자세 변화율, 시점 회전 속도 (Pose estimation)                         │
│   · S-02 충격 가속도, 가속도 정지 구간, GPS 고도 변화율 (IMU/GPS 처리기)        │
│   · Bbox 종횡비 변화, 인체-기계 IoU (CCTV 분석기)                             │
└──────────────────────────────────────────────────────────────────────────────┘
┌─ Tier-3 : 센서 정량 측정 ───────────────────────────────────────────────────┐
│   · S-02 심박·체온·SpO₂, S-04 WBGT·풍속, S-05 가스 ppm·LEL% 등                │
└──────────────────────────────────────────────────────────────────────────────┘
                                       ↓ (모든 Tier 결과를 프롬프트로 융합)
┌─ Tier-4 : VLM 융합 추론 (7B sVLM — 본 문서 학습 대상) ──────────────────────┐
│   · 입력: Tier-1 검출 결과 + Tier-2 시계열 메트릭 + Tier-3 센서 수치 + 키프레임 │
│   · 출력: primary_code + severity + confidence + evidence + recommended_action │
│   · 역할: 다중 신호 융합 판단 + JSON 형식 응답 + 권고 조치 생성                  │
└──────────────────────────────────────────────────────────────────────────────┘
```

본 cascade 구조에 따라 학습 데이터의 `inputs` 필드에는 Tier-1 `detected_objects`와 Tier-2 시계열 메트릭이 사전 산출 결과로 포함된다. VLM 자체는 instruction following + JSON 정합성 + 다중 신호 융합 reasoning을 학습한다.

**▶ 추론 지연 목표**

- Tier-1 + Tier-2 + Tier-3 (병렬 처리) : ≤ 0.5초
- Tier-4 VLM 추론 : ≤ 1.0초 (7B 모델 + INT8 양자화 기준)
- 이상상황 발령(FCM 알림 포함) 총 지연 : ≤ 3.0초

---

## 제2장. 5종 센서 데이터 상세 정의

### 2.1 센서 구성 개요

PASS 시스템의 VLM 파인튜닝에 사용되는 5종 센서의 전체 구성은 아래와 같다.

| ID | 장치명 | 데이터 유형 | 전송 방식 | 샘플링 주기 | 주요 활용 이상상황 |
|---|---|---|---|---|---|
| S-01 | 스마트안전모 카메라 | 영상 (Image/Video) | Wi-Fi (RTSP) | 16 FPS | AC-001~005, 008 |
| S-02 | 스마트워치 (JCWear) | 수치 신호 (Numeric) | **BLE (JSON over GATT)** | 1초 간격 | AC-001~008 전체 |
| S-03 | 현장 CCTV | 영상 (Image/Video) | NVR → 서버 (RTSP) | 25 FPS | AC-001~006, 008 |
| S-04 | 통합기상센서 | 수치 신호 (Numeric) | Wi-Fi (JSON) | 10초 간격 | AC-001, 003, 007, 008 |
| **S-05** | **가스센서 (다중 채널)** | 수치 신호 (Numeric) | Modbus / Wi-Fi (JSON) | 1초 간격 | AC-008 (필수), AC-001 (보조) |

### 2.2 S-01 : 스마트안전모 카메라

| 항목 | 내용 |
|---|---|
| 장치 스펙 | 카메라 해상도 1080P (4MP) / 촬영 속도 16 FPS / 통신 Wi-Fi (RTSP 스트리밍) / 무게 97g / 크기 25cm × 10cm / 배터리 1160mAh (지속 6시간) |
| VLM 입력 형태 | 프레임 이미지 (JPEG, 1280×720) — 정상 모드 1초당 3프레임 키프레임 추출, 이상 의심 시 10 FPS 격상 |
| 주요 탐지 정보 | 화염·연기 Bounding Box / 자세 변화율 / 시점 방향(하향·상향·정면) / Wi-Fi 연결 상태 |
| 프롬프트 텍스트 변환 예시 | `탐지 객체: fire (Bbox: x=320, y=180, w=210, h=155, confidence=0.94)` / `자세 변화율: 1.8% (4.7초 고착)` / `카메라 방향: 하향 45도` / `Wi-Fi 연결 상태: 정상 연결` |
| 데이터 동기화 | 타임스탬프 기준 S-02·S-03·S-04·**S-05**와 ±2초 내 동기화 |

### 2.3 S-02 : 스마트워치 (JCWear)

| 항목 | 내용 |
|---|---|
| 장치 스펙 | 심박/산소포화도 센서, 체온 센서, 가속도센서, GPS 센서 / 무게 48g / 배터리 95mAh (지속 10일) / 통신 **BLE (JSON over GATT custom service)** |
| VLM 입력 형태 | 구조화 텍스트 (JSON → 자연어 변환하여 프롬프트 삽입) + 시계열 raw 별도 파일 (`sensor_logs/*.jsonl`) |
| 주요 측정 항목 및 단위 | 심박수 (Heart Rate): bpm, 측정 주기 1초 / 체온 (Body Temperature): °C, 측정 주기 1분 / 산소포화도 (SpO₂): %, 측정 주기 30초 / 가속도 (Accelerometer): G, 3축(X/Y/Z), 측정 주기 0.1초 / GPS: 위도/경도/고도, 측정 주기 1초 |
| 정상 범위 정의 | 심박수 60~100 bpm (안정 시) / 100~140 bpm (작업 중 허용) / 체온 36.0~37.5°C / 산소포화도 ≥ 95% / 가속도 일반 작업 범위 0.5~3.0G |

### 2.4 S-03 : 현장 CCTV

| 항목 | 내용 |
|---|---|
| 장치 스펙 | Full HD 이상 / PTZ 기능 / 광각 렌즈 / 방진·방수 설계 / NVR 연동 |
| VLM 입력 형태 | 키프레임 이미지 (JPEG, 1280×720) — 이상상황 의심 시 프레임 추출 |
| 주요 탐지 정보 | 화염·연기·작업자·지게차·안전모·충돌 Bounding Box / 객체 간 거리 / 이동 경로 |
| 커버리지 | 구역별 평균 2~3대 설치 / 사각지대 최소화 / 3인칭 광역 탐지 |
| Calibration | 카메라별 GPS 좌표·바라보는 방향·FoV(°)·픽셀-거리 환산표 사전 등록 (zone polygon 정의) |

### 2.5 S-04 : 통합기상센서

| 항목 | 내용 |
|---|---|
| 측정 항목 | 온도(°C), 습도(%), 풍속(m/s), 풍향, 기압(hPa), WBGT 지수 |
| VLM 입력 형태 | 구조화 텍스트 (JSON → 자연어 변환하여 프롬프트 삽입) |
| WBGT 위험 등급 | WBGT < 21 정상 / 21~25 주의 (작업 중 수분 섭취 권장) / 25~28 경고 (고강도 작업 제한) / ≥ 28 위험 (실외 고강도 작업 금지, 열사병 고위험) |
| 연관 이상상황 | AC-001 (화재: 온도 급등), AC-003 (낙상: 열사병), AC-007 (건강: WBGT 복합), **AC-008 (가스: 확산 풍향)** |

### 2.6 S-05 : 가스센서

산업안전보건법 시행규칙 별표 26 밀폐공간 작업 규정 및 화학물질관리법상 필수 설치 대상. 본 시스템에서 AC-008(가스누출/질식)의 1차 트리거 역할을 담당한다.

| 채널 | 측정 대상 | 센서 방식 | 측정 범위 | 경보 임계 (TWA / STEL) |
|---|---|---|---|---|
| CH-1 | H₂S (황화수소) | 전기화학식 | 0~100 ppm | TWA 10 ppm / STEL 15 ppm |
| CH-2 | CO (일산화탄소) | 전기화학식 | 0~500 ppm | TWA 30 ppm / STEL 200 ppm |
| CH-3 | LEL (가연성가스) | 접촉연소식 / NDIR | 0~100% LEL | 경고 10% LEL / 위험 25% LEL |
| CH-4 | O₂ (산소) | 전기화학식 | 0~25% | 저산소 19.5% / 과산소 23.5% |
| CH-5 | VOC (휘발성유기화합물) | PID (광이온화) | 0~50 ppm | TWA 100 ppm |
| CH-6 | PM (분진) | 광산란식 | 0~1000 μg/m³ | STEL 5 mg/m³ (호흡성) |

**▶ 데이터 포맷 예시**

```json
{ "sensor_id": "GAS-A01", "timestamp": "2026-04-22T11:05:30+09:00",
  "zone": "A동 밀폐탱크 #3",
  "readings": {
     "h2s_ppm": 18.4, "co_ppm": 22.0, "lel_percent": 8.0,
     "o2_percent": 19.2, "voc_ppm": 12.5, "pm_ug_m3": 320 },
  "alarm_state": "warning", "alarm_channels": ["h2s","o2"] }
```

### 2.7 데이터 동기화 및 융합 방식

5종 센서 데이터는 다음 방식으로 동기화·융합하여 단일 VLM 추론 요청으로 처리한다.

**[데이터 동기화 파이프라인]**

```
1. 타임스탬프 기준 동기화
   - S-01 (16 FPS) → 3 FPS 키프레임 추출 (이상 의심 시 10 FPS)
   - S-02 (1초)    → 타임스탬프 일치 데이터 선택
   - S-03 (25 FPS) → 3 FPS 키프레임 추출
   - S-04 (10초)   → 최근 값 사용 (±10초 허용)
   - S-05 (1초)    → 타임스탬프 일치 데이터 선택 (가스 알람 활성 시 우선)

2. 멀티모달 프롬프트 조합
   - S-01, S-03 이미지 → VLM 비전 인코더 직접 입력 (multi-image 형식, 베이스 모델 호환)
   - S-02, S-04, S-05 수치 → 자연어 텍스트 변환 후 프롬프트 삽입
   - 시계열 raw (S-02 가속도 0.1초 간격 등) → sensor_logs/ 별도 파일로 저장,
     prompt에는 Tier-2에서 산출된 메트릭만 포함

3. 추론 지연 목표
   - 단일 프레임 기준 추론 시간 : ≤ 1.0초 (7B 모델 + INT8 양자화 기준)
   - 이상상황 발령까지 총 지연 : ≤ 3.0초
```

---

## 제3장. 시스템 프롬프트 및 출력 스키마 정의

### 3.1 시스템 프롬프트(System Prompt) 전문

VLM 파인튜닝 시 모델의 역할·행동 기준·출력 형식을 고정하는 시스템 프롬프트이다. 모든 학습 샘플에 동일하게 적용된다.

```
[SYSTEM PROMPT — 전문]

당신은 중소 제조업 산업 현장의 안전 관리 전문 AI입니다.
아래 5종 센서 데이터를 실시간으로 수신하여 이상상황 여부를 판단합니다.

■ 입력 센서 정의
  S-01: 스마트안전모 카메라 (1인칭 영상 — 화염·자세·시점 변화 포함)
  S-02: 스마트워치 (심박수·체온·산소포화도·가속도·GPS)
  S-03: 현장 CCTV (3인칭 영상 — 구역 전체 탐지)
  S-04: 통합기상센서 (온도·습도·풍속·기압·WBGT)
  S-05: 가스센서 (H₂S·CO·LEL·O₂·VOC·PM)

■ 이상상황 클래스 (8종)
  AC-001: 화재/연기              AC-002: 작업자 끼임
  AC-003: 낙상/쓰러짐 (지면)     AC-004: 작업자 추락 (고소)
  AC-005: 지게차 충돌 / 가동구역 진입
  AC-006: 안전모 미착용
  AC-007: 작업자 건강 이상
  AC-008: 가스누출 / 질식 / 위험구역 진입 (밀폐공간·화학물질)

■ 위험도 등급
  Critical : 즉각적 인명 위험 → 자동 경보 + 119 연동
  Warning  : 사고 가능성 높음 → 관리자 즉시 알림
  Notice   : 규정 위반·경미한 위험 → 지도·기록

■ 출력 규칙
  1. 반드시 부록 C의 JSON Schema(vlm_response.schema.json)를 엄격히 준수하여 출력
     (설명 텍스트 금지, JSON 객체 한 개만 출력)
  2. 스키마 외 임의 필드 추가 금지
     (클래스별 확장 필드는 class_extension 의 oneOf 정의를 따른다)
  3. 이상상황 미감지 시 : anomaly_detected=false, 나머지 null
  4. confidence : 0.0~1.0 범위 (0.70 미만 시 Warning 으로 하향)
  5. evidence : 탐지 근거 센서 최소 2개 이상 기재 (sensor enum 준수)
  6. recommended_action : action enum 에서 최소 3개 이상 선택 (자유 텍스트 금지)
  7. 다중 이상상황 동시 발생 시 : 부록 A 결정 트리에 따라
     primary_code 1개 결정, 나머지는 secondary_codes 배열에 기재
  8. suspected_condition (AC-007), suspected_cause (AC-003) 등은 enum 에서만 선택

■ 출력 JSON 스키마 (요약, 상세는 부록 C)
{
  "anomaly_detected":   boolean,
  "primary_code":       "AC-001"|...|"AC-008"|null,
  "secondary_codes":    [ "AC-***", ... ],
  "anomaly_type":       string | null,
  "severity":           "Critical" | "Warning" | "Notice" | null,
  "confidence":         float (0.0~1.0),
  "detected_at":        ISO8601 with +09:00,
  "location": {
    "zone":            string,
    "camera_id":       string | null,
    "gps":             { "lat", "lng", "altitude_m" } | null,
    "zone_polygon_id": string | null
  } | null,
  "affected_workers": [
    { "worker_id": string, "distance_m": float | null, "status": string }
  ],
  "evidence":           [ { "sensor": <enum>, "finding": string } ],
  "recommended_action": [ <action_enum>, ... ],
  "class_extension":    { ... }   // 클래스별 확장 필드 (oneOf)
}
```

### 3.2 사용자 프롬프트 템플릿(User Prompt Template)

각 학습 샘플의 사용자 입력은 아래 템플릿 구조를 따른다. 이상상황 유형에 무관하게 동일한 템플릿을 사용하며, Tier-1 사전 검출 결과·Tier-2 시계열 메트릭·Tier-3 센서 정량 측정이 명시적으로 구분되어 들어간다.

```
[USER PROMPT TEMPLATE]

[시스템 역할]
당신은 산업 현장 안전 관리 전문 AI입니다. 아래 5종 센서 데이터를 종합 분석하여
이상상황 발생 여부를 판단하고, 지정된 JSON 스키마로 결과를 출력하시오.

[Tier-1 사전 검출 결과]
■ S-01 스마트안전모 카메라 (작업자 ID: {worker_id}, 타임스탬프: {ISO8601})
  - detected_objects: [ {class:"fire", bbox:[...], confidence:...}, ... ]
  - {기타 helmet_camera findings ...}

■ S-03 현장 CCTV (카메라 ID: {camera_id}, 구역: {zone}, 타임스탬프: {ISO8601})
  - detected_objects: [ ... ]
  - nearby_workers: [ ... ]

[Tier-2 시계열 메트릭]
  S-01 : pose_change_rate={x}%, view_rotation={y}°/s, view_direction={dir}
  S-02 : impact_peak_g={x}G, stop_duration_s={y}s,
         hr_sustained={hr}bpm × {dt}s, gps_altitude_change={Δh}m / {Δt}s
  S-03 : bbox_aspect_ratio={x}, person_machine_iou={y}, collision_eta_s={z}

[Tier-3 센서 정량 측정]
■ S-02 스마트워치 (작업자 ID: {worker_id}, 타임스탬프: {ISO8601})
  - 심박수: {heart_rate} bpm ({status})
  - 체온: {temperature}°C
  - 산소포화도: {spo2}%
  - 가속도: X={ax}, Y={ay}, Z={az}
  - GPS: lat={lat}, lng={lng}, altitude={alt}m

■ S-04 통합기상센서 (센서 ID: {sensor_id}, 위치: {location}, 타임스탬프: {ISO8601})
  - 온도: {temp}°C, 습도: {humidity}%
  - 풍속: {wind}m/s, 기압: {pressure}hPa
  - WBGT: {wbgt} ({risk_level})

■ S-05 가스센서 (센서 ID: {sensor_id}, 구역: {zone}, 타임스탬프: {ISO8601})
  - H₂S: {h2s}ppm, CO: {co}ppm, LEL: {lel}%
  - O₂: {o2}%, VOC: {voc}ppm, PM: {pm}μg/m³
  - alarm_state: {none|warning|critical}

[키프레임 이미지]
  (image: S-01 keyframe)
  (image: S-03 keyframe)

[분석 지시]
위 5종 센서 데이터를 종합하여 이상상황 여부를 판단하고,
시스템 프롬프트의 JSON 스키마에 맞게 결과를 출력하시오.
JSON 외 다른 텍스트는 출력하지 마시오.
```

### 3.3 다중 이상상황 동시 발생 처리

**▶ 다중 이상상황 규칙**

동일 시각·동일 구역에서 2개 이상 이상상황이 동시 감지될 경우 (예: 화재+낙상, 추락+안전모 미착용, 가스누출+위험구역 진입):

1. **primary_code 결정** : 부록 A. 클래스 우선순위 결정 트리에 따라 단일 코드를 선정한다.
2. **secondary_codes** : 함께 활성화된 나머지 코드를 0~3개 배열로 기재한다.
3. **anomaly_type** : `primary_code`의 한국어 명칭만 기재한다. `secondary_codes`의 명칭은 병기하지 않는다.
4. **evidence** : `primary` + `secondary` 모두의 근거를 통합하여 기재 (sensor enum 준수).
5. **recommended_action** : `primary`의 액션 우선 + `secondary`의 액션 추가 (중복 제거).
6. **class_extension** : `primary_code`에 해당하는 필드만 기재한다. `secondary_codes`의 확장 필드는 기재하지 않는다.

### 3.4 출력 JSON Schema 강제

상세 schema 파일은 **부록 C** 참조. 라벨링·학습·평가 단계에서 다음 규칙이 강제된다.

- 모든 학습 샘플의 `response` 필드는 Ajv strict mode(`additionalProperties=false`)로 검증한다.
- `primary_code` 값에 따라 `class_extension` 의 구조가 결정된다(`if/then`).
- `detected_at` 은 ISO8601 + `+09:00` 타임존을 강제한다.
- GPS 좌표는 WGS84 기준.
- 라벨러는 `class_extension` 의 확장 필드를 임의로 추가할 수 없으며, 부록 B enum 목록에 정의된 값만 사용한다.

---

## 제4장. 이상상황 클래스별 상세 정의 및 프롬프트 예시

■ 본 장에서는 8개 이상상황 클래스 각각에 대해 상세 정의, 5종 센서별 역할, 트리거 조건, 임계값, 다중 센서 판단 로직, 오탐 방지 방안, 그리고 파인튜닝용 예시 프롬프트(입력/출력)를 기술한다. 모든 클래스의 출력 응답은 `class_extension` 객체를 통해 클래스별 확장 필드를 표현한다. 위험구역 무단 진입은 별도 클래스로 두지 않고 다음과 같이 통합한다.

- 지게차 가동 zone 무단 진입 → **AC-005** (지게차 충돌)에 통합
- 가스 zone·밀폐공간·화학물질 저장소 무단 진입 → **AC-008** (가스누출/질식)에 통합

---

### AC-001 | 화재 / 연기 (Fire / Smoke Detection)
**위험도 : Critical (위험)**

#### 4.1.1 클래스 개요

작업 현장 내에서 화재 또는 연기가 발생하는 상황으로, 스마트안전모 카메라(1인칭 영상), CCTV(3인칭 영상), 기상센서(온도·습도)의 복합 데이터로 탐지한다. 화염 픽셀의 색상 분포(RGB 임계치), 연기의 확산 패턴, 온도 급등이 교차 검증되어야 한다.

#### 4.1.2 5종 센서별 역할

| ID | 센서명 | 역할 | 핵심 판단 지표 |
|---|---|---|---|
| S-01 | 스마트안전모 카메라 | 1인칭 화염·연기 객체 탐지 (Bounding Box) | 화염 픽셀 면적 ≥ 이미지 면적 2%, 연기 색조 임계치 초과 |
| S-02 | 스마트워치 | 심박수 급등 (열 스트레스 또는 패닉 반응) | 심박수 ≥ 120bpm 지속 30초 이상 |
| S-03 | 현장 CCTV | 3인칭 광역 화재·연기 확산 범위 탐지 | 연기 Blob 면적 급증, 화염 RGB 임계 초과 픽셀군 |
| S-04 | 통합기상센서 | 주변 온도·습도 급변 감지 | 온도 ≥ 40°C 또는 정상 대비 +15°C 이상, 습도 ≤ 20% |
| S-05 | 가스센서 | LEL·VOC 보조 — 화학 화재 시 가연성가스 동반 | LEL ≥ 10% (보조 증거) |

#### 4.1.3 트리거 조건

- S-01 or S-03 : 화염 Bounding Box 신뢰도 ≥ 0.70 탐지
- S-01 or S-03 : 연기 영역(Blob) 이미지 면적 대비 5% 이상 차지
- S-04 : 온도 40°C 초과 또는 기준 온도 대비 +15°C 이상 급등
- S-04 : 습도 20% 이하(건조한 연소 환경)
- S-02 : 인근 작업자 심박수 120bpm 이상(2개 이상 작업자 동시)

#### 4.1.4 다중 센서 판단 로직

**▶ 판단 로직**

단일 센서 오탐 방지를 위해 (S-01 OR S-03)의 영상 탐지와 S-04의 온도 이상이 동시 충족 시 발령. S-02 심박 이상이 추가될 경우 신뢰도 가산. S-05 LEL ≥ 10% 동반 시 화학 화재로 판정하여 AC-008 secondary 부착.

#### 4.1.5 임계값 정의

| 측정 지표 | 임계값 | 단위 | 조치 |
|---|---|---|---|
| 화염 픽셀 비율 (S-01/S-03) | ≥ 2% (이미지 면적) | % | Warning 발령 |
| 화염 Bbox 신뢰도 | ≥ 0.70 | 0~1 | 탐지 확정 |
| 연기 Blob 면적 | ≥ 5% (이미지 면적) | % | Critical 발령 |
| 센서 온도 (S-04) | ≥ 40°C 또는 기준 +15°C | °C | Critical 발령 |
| 주변 습도 (S-04) | ≤ 20% | % | 위험 가중치 상승 |
| 심박수 (S-02) | ≥ 120 bpm × 30초 | bpm | 신뢰도 가산 |

#### 4.1.6 오탐(False Positive) 방지 방안

- 용접 스파크 : 화염 크기 소규모, 위치 고정, 온도 이상 미발생 → 무시
- 수증기·증기 : 연기 색조 임계치 미달(흰색 계열 필터링)
- 석양·조명 반사 : CCTV 각도 보정으로 색상 왜곡 제거

#### 4.1.7 파인튜닝용 예시 프롬프트

**▶ 입력 프롬프트 (User Input)**

```
[시스템 역할]
당신은 산업 현장 안전 관리 전문 AI입니다. 아래 5종 센서 데이터를 종합 분석하여
이상상황 발생 여부를 판단하고, 지정된 JSON 스키마로 결과를 출력하시오.

[Tier-1 사전 검출 결과]
■ S-01 스마트안전모 카메라 (작업자 ID: W-042, 타임스탬프: 2026-04-15T14:23:10+09:00)
  - 탐지 객체: fire (Bbox: x=320, y=180, w=210, h=155, confidence=0.94)
  - 탐지 객체: smoke (Bbox: x=280, y=100, w=380, h=240, confidence=0.89)
  - 카메라 방향: 정면 작업대 → 우측 45도 (급격한 시점 전환 감지)

■ S-03 현장 CCTV (카메라 ID: CAM-03, 구역: B동 2구역, 타임스탬프: 2026-04-15T14:23:09+09:00)
  - 탐지 객체: fire (Bbox: x=600, y=300, w=320, h=280, confidence=0.91)
  - 인근 작업자 탐지: W-042 (1.8m), W-031 (4.2m), W-057 (7.1m)

[Tier-2 시계열 메트릭]
  S-01 : fire_pixel_ratio=0.068, view_rotation_dps=35, view_direction="right_45"
  S-02 : hr_sustained=134bpm × 52s
  S-03 : smoke_blob_ratio=0.183, smoke_direction="NE", smoke_speed_ms=0.8

[Tier-3 센서 정량 측정]
■ S-02 스마트워치 (작업자 ID: W-042, 타임스탬프: 2026-04-15T14:23:08+09:00)
  - 심박수: 134 bpm (정상 범위 60~100 bpm 초과, 지속 52초)
  - 체온: 37.4°C (정상 범위)
  - 산소포화도: 95% (미세 저하)
  - 가속도: X=2.1, Y=1.8, Z=9.4 (빠른 이동 패턴)
  - GPS: lat=37.4561, lng=126.7324, altitude=4.2m

■ S-04 통합기상센서 (센서 ID: WS-B02, 위치: B동 내부, 타임스탬프: 2026-04-15T14:23:05+09:00)
  - 온도: 47.8°C (기준 온도 24°C 대비 +23.8°C 상승)
  - 습도: 14% (정상 범위 40~60% 대비 급감)
  - 풍속: 0.2 m/s (실내 순환 최소)
  - 기압: 1010.2 hPa (정상)
  - WBGT: 41.2 (고위험 임계치 28 초과)

■ S-05 가스센서 (센서 ID: GAS-B01, 구역: B동 2구역, 타임스탬프: 2026-04-15T14:23:06+09:00)
  - H₂S: 0.0 ppm, CO: 15.0 ppm, LEL: 0.0%
  - O₂: 20.8%, VOC: 2.1 ppm, PM: 680 μg/m³
  - alarm_state: none

[키프레임 이미지]
  (image: S-01 keyframe), (image: S-03 keyframe)

[분석 지시]
위 5종 센서 데이터를 종합하여 이상상황 여부를 판단하고,
시스템 프롬프트의 JSON 스키마에 맞게 결과를 출력하시오. JSON 외 다른 텍스트는 출력하지 마시오.
```

**▶ 출력 응답 (Expected Model Output)**

```json
{
  "anomaly_detected": true,
  "primary_code": "AC-001",
  "secondary_codes": [],
  "anomaly_type": "화재/연기",
  "severity": "Critical",
  "confidence": 0.96,
  "detected_at": "2026-04-15T14:23:10+09:00",
  "location": {
    "zone": "B동 2구역",
    "camera_id": "CAM-03",
    "gps": { "lat": 37.4561, "lng": 126.7324, "altitude_m": 4.2 },
    "zone_polygon_id": "ZONE-B-002"
  },
  "affected_workers": [
    { "worker_id": "W-042", "distance_m": 1.8, "status": "이동 중 (HR 134bpm 상승)" },
    { "worker_id": "W-031", "distance_m": 4.2, "status": "위험 근접 구역" },
    { "worker_id": "W-057", "distance_m": 7.1, "status": "모니터링 대상" }
  ],
  "evidence": [
    { "sensor": "S-01_helmet_camera", "finding": "화염 Bbox 신뢰도 0.94, 픽셀 비율 6.8%" },
    { "sensor": "S-03_cctv",          "finding": "연기 Blob 면적 18.3%, 확산 NE 0.8m/s" },
    { "sensor": "S-02_smartwatch",    "finding": "심박 134bpm, 52초 지속 (패닉/열 스트레스)" },
    { "sensor": "S-04_weather",       "finding": "온도 47.8°C (+23.8°C), WBGT 41.2" }
  ],
  "recommended_action": [
    "evacuate_all_immediate",
    "trigger_fire_suppression_local",
    "call_119",
    "isolate_zone_downwind",
    "alert_nearby_workers",
    "call_safety_manager",
    "call_executive"
  ],
  "class_extension": {
    "fire_pixel_ratio":   0.068,
    "smoke_blob_ratio":   0.183,
    "spread_direction":   "NE",
    "spread_speed_ms":    0.8,
    "estimated_area_m2":  4.2
  }
}
```

---

### AC-002 | 작업자 끼임 (Worker Entrapment — Machine / Structure)
**위험도 : Critical (위험)**

#### 4.2.1 클래스 개요

작업자의 신체 일부(손·팔·다리 등)가 기계·롤러·프레스·컨베이어 등에 끼이는 상황. 1인칭 카메라의 자세 고착, 스마트워치의 가속도 급정지·심박 급등, CCTV의 신체 고착 영상이 복합적으로 탐지된다. 끼임은 수초 내 심각한 신체 손상을 유발하므로 즉시 설비 정지가 필수적이다.

#### 4.2.2 5종 센서별 역할

| ID | 센서명 | 역할 | 핵심 판단 지표 |
|---|---|---|---|
| S-01 | 스마트안전모 카메라 | 1인칭 신체 자세 고착 감지, 팔/손 방향 추적 | 자세 변화율 ≤ 3% (연속 3초 이상), 특정 방향 고착 |
| S-02 | 스마트워치 | 가속도 급정지 및 심박 급등 감지 | 가속도 변화 급정지 (`|ΔA|` ≤ 0.05G × 2초), 심박 ≥ 125bpm |
| S-03 | 현장 CCTV | 3인칭 신체-기계 방향 고착 확인 | 인체 Bbox 위치 고착 + 기계 Bbox 접촉 감지 |
| S-04 | 통합기상센서 | 참고 데이터 (끼임 직접 연관 낮음) | N/A (보조 데이터) |
| S-05 | 가스센서 | 참고 데이터 | N/A |

#### 4.2.3 트리거 조건

- S-01 : 1인칭 자세 변화율 ≤ 3% 연속 3초 이상 (자발적 정지가 아닌 비정상 고착)
- S-02 : 손목 가속도 `|ΔA|` ≤ 0.05G 연속 2초 이상 (전신 정지) + 심박 ≥ 125bpm
- S-03 : 인체 Bbox가 기계 Bbox와 겹치거나 접촉 상태 3초 이상 유지
- S-01 + S-02 동시 만족 : 자세 고착 + 가속도 정지 복합 (신뢰도 자동 가산)

#### 4.2.4 다중 센서 판단 로직

**▶ 판단 로직**

S-01 자세 고착과 S-02 가속도 급정지가 동시 충족 시 끼임 의심 발령. S-03 영상으로 기계 접촉 확인 시 Critical 확정. 단순 휴식 동작과의 구분을 위해 심박수 ≥ 125bpm을 필수 조건으로 추가.

#### 4.2.5 임계값 정의

| 측정 지표 | 임계값 | 단위 | 조치 |
|---|---|---|---|
| 자세 변화율 (S-01) | ≤ 3% × 3초 이상 | % | 의심 발령 |
| 가속도 정지 (S-02) | `|ΔA|` ≤ 0.05G × 2초 | G | 의심 발령 |
| 심박수 (S-02) | ≥ 125 bpm | bpm | 끼임 가중 판단 |
| 인체-기계 Bbox 겹침 (S-03) | IoU ≥ 0.15 × 3초 | IoU | Critical 확정 |
| 끼임 지속 시간 | ≥ 5초 | sec | 설비 자동 정지 트리거 |

#### 4.2.6 오탐(False Positive) 방지 방안

- 작업자 자발적 정지(휴식) : 심박수 정상 범위 (60~100 bpm) → 끼임 미발령
- 공구 조작 중 손 정지 : 가속도 정지 시간 ≤ 1.5초 → 임계치 미충족
- 의도적 고정 작업 (볼트 조임 등) : S-03 기계 접촉 없음 → 무시

#### 4.2.7 파인튜닝용 예시 프롬프트

**▶ 입력 프롬프트 (User Input)**

```
[시스템 역할]
당신은 산업 현장 안전 관리 전문 AI입니다. 아래 5종 센서 데이터를 종합 분석하여
이상상황 발생 여부를 판단하고, 지정된 JSON 스키마로 결과를 출력하시오.

[Tier-1 사전 검출 결과]
■ S-01 스마트안전모 카메라 (작업자 ID: W-017, 타임스탬프: 2026-04-16T09:41:33+09:00)
  - 탐지 객체: right_arm_region (Bbox 위치 고착, 프레스 방향)
  - 시선 방향: 하향 45도 고착 (우측 프레스 기계 방향)
  - 머리 움직임: 좌우 진동 (통증 반응 패턴 의심, 진폭 8°, 주기 0.5초)

■ S-03 현장 CCTV (카메라 ID: CAM-07, 구역: A동 프레스 라인 7번, 타임스탬프: 2026-04-16T09:41:31+09:00)
  - 작업자 W-017 인체 Bbox: (x=410, y=200, w=80, h=180) — 위치 고착
  - 프레스 기계 Bbox: (x=430, y=260, w=200, h=120) — 인체와 IoU=0.22 (접촉 판정)
  - 인근 작업자: W-008 (3.1m), W-045 (5.8m)

[Tier-2 시계열 메트릭]
  S-01 : pose_change_rate=1.8% (4.7초 고착), view_direction="down_45"
  S-02 : accel_stop_duration_s=4.1, hr_sustained=147bpm × 38s, impact_peak_g=null
  S-03 : person_machine_iou=0.22, 접촉 지속 4.3초

[Tier-3 센서 정량 측정]
■ S-02 스마트워치 (작업자 ID: W-017, 타임스탬프: 2026-04-16T09:41:30+09:00)
  - 손목 가속도: X=0.02, Y=0.01, Z=9.80 (정지 상태, 4.1초 지속)
  - 심박수: 147 bpm (정상 범위 대비 극심한 상승, 지속 38초)
  - 체온: 36.8°C (정상)
  - 산소포화도: 97%
  - GPS: lat=37.4560, lng=126.7319, altitude=1.8m

■ S-04 통합기상센서 (센서 ID: WS-A01, 위치: A동 내부, 타임스탬프: 2026-04-16T09:41:00+09:00)
  - 온도: 26.2°C (정상), 습도: 52% (정상)
  - 풍속: 0.0 m/s (실내), 기압: 1012.1 hPa (정상)
  - WBGT: 22.4 (정상)

■ S-05 가스센서 (센서 ID: GAS-A01, 구역: A동 프레스 라인 7번, 타임스탬프: 2026-04-16T09:41:30+09:00)
  - H₂S: 0.0 ppm, CO: 8.0 ppm, LEL: 0.0%
  - O₂: 20.9%, VOC: 1.2 ppm, PM: 95 μg/m³
  - alarm_state: none

[키프레임 이미지]
  (image: S-01 keyframe), (image: S-03 keyframe)

[분석 지시]
위 5종 센서 데이터를 종합하여 이상상황 여부를 판단하고,
시스템 프롬프트의 JSON 스키마에 맞게 결과를 출력하시오. JSON 외 다른 텍스트는 출력하지 마시오.
```

**▶ 출력 응답 (Expected Model Output)**

```json
{
  "anomaly_detected": true,
  "primary_code": "AC-002",
  "secondary_codes": [],
  "anomaly_type": "작업자 끼임",
  "severity": "Critical",
  "confidence": 0.95,
  "detected_at": "2026-04-16T09:41:33+09:00",
  "location": {
    "zone": "A동 프레스 라인 7번",
    "camera_id": "CAM-07",
    "gps": { "lat": 37.4560, "lng": 126.7319, "altitude_m": 1.8 },
    "zone_polygon_id": "ZONE-A-PRESS-07"
  },
  "affected_workers": [
    { "worker_id": "W-017", "distance_m": 0.0, "status": "끼임 당사자 (즉시 구조 필요)" },
    { "worker_id": "W-008", "distance_m": 3.1, "status": "구조 지원 가능 인원" }
  ],
  "evidence": [
    { "sensor": "S-01_helmet_camera", "finding": "자세 변화율 1.8%, 4.7초 고착 (기계 방향)" },
    { "sensor": "S-02_smartwatch",    "finding": "가속도 정지 4.1초 + 심박 147bpm × 38초" },
    { "sensor": "S-03_cctv",          "finding": "인체-프레스 IoU=0.22, 접촉 4.3초 확인" }
  ],
  "recommended_action": [
    "stop_machine_emergency",
    "shutoff_power_local",
    "request_rescue_team",
    "call_119",
    "request_first_aid",
    "call_safety_manager"
  ],
  "class_extension": {
    "body_part_suspected":   "right_arm",
    "entrapment_duration_sec": 4.7,
    "machine_id":            "PRESS-07",
    "machine_class":         "press"
  }
}
```

---

### AC-003 | 작업자 낙상 / 쓰러짐 (Worker Fall / Collapse — Ground Level)
**위험도 : Critical (위험)**

#### 4.3.1 클래스 개요

작업자가 바닥 수평면에서 실신·기절·급격한 자세 변화로 쓰러지는 상황. 높이에서의 추락(AC-004)과 구분되며, GPS 고도 변화 없이 충격 가속도와 1인칭 카메라 시점 급변이 복합 탐지된다. 열사병·저혈당·과로 등 내인성 원인이 많아 체온·심박 생체신호가 중요 판단 기준이다.

> 라벨링 시 주의 — 쓰러짐이 발생한 사건은 원인이 무엇이든 `primary_code = AC-003`, 원인은 `class_extension.suspected_cause`로 표기하고, 생체신호 이상이 동반된 경우 AC-007 을 `secondary_codes` 에 추가한다. 분류 기준은 부록 A 결정 트리 단계 3 참조.

#### 4.3.2 5종 센서별 역할

| ID | 센서명 | 역할 | 핵심 판단 지표 |
|---|---|---|---|
| S-01 | 스마트안전모 카메라 | 1인칭 시점 급격 회전 후 지면 방향 고착 탐지 | 회전 속도 ≥ 45°/초 후 지면 방향 고착 (2초 이상) |
| S-02 | 스마트워치 | 충격 가속도 + 심박·체온 이상 복합 감지 | 충격 ≥ 2.5G + 심박 ≤ 45bpm(서맥) or ≥ 140bpm, 체온 이상 |
| S-03 | 현장 CCTV | 3인칭 수평 쓰러짐 자세 확인 | 인체 Bbox 종횡비 역전 (세로→가로), 위치 고착 3초 |
| S-04 | 통합기상센서 | 열환경 (WBGT) 및 고온 조건 파악 | WBGT ≥ 28, 온도 ≥ 33°C (열사병 위험 환경) |
| S-05 | 가스센서 | 산소 결핍 동반 여부 — 질식성 쓰러짐 변별 | O₂ ≤ 19.5% 동반 시 AC-008 secondary 부착 |

#### 4.3.3 트리거 조건

- S-01 : 카메라 시점 회전 ≥ 45°/초 후 지면 방향 2초 이상 고착
- S-02 : 충격 가속도 ≥ 2.5G 순간 발생 후 정지
- S-02 : 심박수 ≤ 45 bpm (서맥) 또는 ≥ 140 bpm (빈맥) 지속
- S-03 : 인체 Bbox 종횡비 역전 (수직→수평) + 위치 3초 이상 고착
- S-04 : WBGT ≥ 28 (열환경 위험) → 열사병 낙상 확률 가중

#### 4.3.4 다중 센서 판단 로직

**▶ 판단 로직**

S-01 시점 급변과 S-02 충격 가속도 동시 감지 시 낙상 의심 발령. S-03으로 수평 자세 확인 시 Critical 확정. S-04 고온 환경이면 `class_extension.suspected_cause = "heat_stroke"`, 서맥 단독이면 `"syncope"` 로 표기. 추락(AC-004)과 구분 : GPS 고도 변화 ≤ 0.3m.

#### 4.3.5 임계값 정의

| 측정 지표 | 임계값 | 단위 | 조치 |
|---|---|---|---|
| 카메라 시점 회전 (S-01) | ≥ 45°/초 | °/sec | 낙상 의심 |
| 지면 방향 고착 (S-01) | ≥ 2초 | sec | 낙상 확정 조건 |
| 충격 가속도 (S-02) | ≥ 2.5G | G | 낙상 의심 |
| 서맥 심박 (S-02) | ≤ 45 bpm × 30초 | bpm | 실신 의심 |
| Bbox 종횡비 역전 (S-03) | 가로/세로 ≥ 2.0 × 3초 | ratio | Critical 확정 |
| GPS 고도 변화 (S-02) | ≤ 0.3m (추락 구분) | m | 낙상 분류 유지 |

#### 4.3.6 오탐(False Positive) 방지 방안

- 작업자 자발적 웅크림/앉음 : 심박 정상, 이후 자세 복귀 → 미발령
- 도구 줍는 행동 : 시점 회전 ≤ 30°, 복귀 속도 빠름 → 임계치 미충족
- 의도적 바닥 작업 (배관 등) : S-03 자세 변화 단계적, 충격 없음 → 무시

#### 4.3.7 파인튜닝용 예시 프롬프트

**▶ 입력 프롬프트 (User Input)**

```
[시스템 역할]
당신은 산업 현장 안전 관리 전문 AI입니다. 아래 5종 센서 데이터를 종합 분석하여
이상상황 발생 여부를 판단하고, 지정된 JSON 스키마로 결과를 출력하시오.

[Tier-1 사전 검출 결과]
■ S-01 스마트안전모 카메라 (작업자 ID: W-033, 타임스탬프: 2026-04-17T13:52:44+09:00)
  - 1인칭 영상 내 지면 픽셀 비율: 71% (정상 시 ≤ 20%)
  - 안전모 착용 확인: 정상

■ S-03 현장 CCTV (카메라 ID: CAM-02, 구역: C동 조립 라인 구역, 타임스탬프: 2026-04-17T13:52:43+09:00)
  - 작업자 W-033 인체 Bbox: 위치 고착, 종횡비 2.8 (수직→수평 역전 확인)
  - 쓰러짐 방향: 좌측 (왼쪽 옆으로)
  - 인근 작업자: W-019 (2.3m), W-062 (6.4m)

[Tier-2 시계열 메트릭]
  S-01 : view_rotation_dps=82, 지면 방향 고착 5.1초
  S-02 : impact_peak_g=3.4, accel_stop_duration_s=5.0, altitude_change_m=0.1,
         hr_sustained=38bpm × 30s (서맥)
  S-03 : bbox_aspect_ratio=2.8, 위치 고착 5.1초

[Tier-3 센서 정량 측정]
■ S-02 스마트워치 (작업자 ID: W-033, 타임스탬프: 2026-04-17T13:52:42+09:00)
  - 심박수: 38 bpm (서맥, 30초 이상 지속 — 실신 의심)
  - 체온: 38.7°C (경미한 발열)
  - 산소포화도: 94% (경계값)
  - 가속도 (현재): X=0.01, Y=0.00, Z=9.81 (완전 정지)
  - GPS: lat=37.4563, lng=126.7321, altitude=1.6m (고도 변화 Δ=0.1m)

■ S-04 통합기상센서 (센서 ID: WS-C01, 위치: C동 내부, 타임스탬프: 2026-04-17T13:52:00+09:00)
  - 온도: 36.4°C (고온 환경), 습도: 71% (고습도)
  - 풍속: 0.1 m/s (환기 거의 없음), 기압: 1011.5 hPa
  - WBGT: 34.8 (열사병 고위험 — 임계치 28 초과)

■ S-05 가스센서 (센서 ID: GAS-C01, 구역: C동 조립 라인, 타임스탬프: 2026-04-17T13:52:42+09:00)
  - H₂S: 0.0 ppm, CO: 12.0 ppm, LEL: 0.0%
  - O₂: 20.8%, VOC: 1.8 ppm, PM: 220 μg/m³
  - alarm_state: none

[키프레임 이미지]
  (image: S-01 keyframe), (image: S-03 keyframe)

[분석 지시]
위 5종 센서 데이터를 종합하여 이상상황 여부를 판단하고,
시스템 프롬프트의 JSON 스키마에 맞게 결과를 출력하시오. JSON 외 다른 텍스트는 출력하지 마시오.
```

**▶ 출력 응답 (Expected Model Output)**

```json
{
  "anomaly_detected": true,
  "primary_code": "AC-003",
  "secondary_codes": ["AC-007"],
  "anomaly_type": "작업자 낙상/쓰러짐",
  "severity": "Critical",
  "confidence": 0.97,
  "detected_at": "2026-04-17T13:52:44+09:00",
  "location": {
    "zone": "C동 조립 라인 구역",
    "camera_id": "CAM-02",
    "gps": { "lat": 37.4563, "lng": 126.7321, "altitude_m": 1.6 },
    "zone_polygon_id": "ZONE-C-ASSY-01"
  },
  "affected_workers": [
    { "worker_id": "W-033", "distance_m": 0.0, "status": "실신 의심 (심박 38bpm, 체온 38.7°C)" },
    { "worker_id": "W-019", "distance_m": 2.3, "status": "응급처치 지원 가능" }
  ],
  "evidence": [
    { "sensor": "S-01_helmet_camera", "finding": "시점 82°/초 급회전 후 지면 5.1초 고착" },
    { "sensor": "S-02_smartwatch",    "finding": "충격 3.4G + 심박 38bpm 서맥 + 체온 38.7°C" },
    { "sensor": "S-03_cctv",          "finding": "Bbox 종횡비 2.8 역전, 5.1초 고착" },
    { "sensor": "S-04_weather",       "finding": "WBGT 34.8 (열사병 고위험), 온도 36.4°C" }
  ],
  "recommended_action": [
    "call_119",
    "request_first_aid",
    "deploy_cooling_pad",
    "increase_ventilation",
    "evacuate_to_safe_zone",
    "call_safety_manager",
    "call_executive"
  ],
  "class_extension": {
    "suspected_cause":     "heat_stroke",
    "altitude_change_m":   0.1,
    "fall_direction":      "left"
  }
}
```

---

### AC-004 | 작업자 추락 (Worker Fall from Height)
**위험도 : Critical (위험)**

#### 4.4.1 클래스 개요

작업자가 발판·사다리·지붕·고소작업대 등 고소 위치에서 낙하하는 상황. AC-003(낙상)과 달리 GPS 고도가 급격히 하강하고 대형 충격 가속도가 수반된다. 낙하 높이에 따라 사망 위험이 극히 높으므로 발생 즉시 119 신고 및 접근 통제가 우선된다.

#### 4.4.2 5종 센서별 역할

| ID | 센서명 | 역할 | 핵심 판단 지표 |
|---|---|---|---|
| S-01 | 스마트안전모 카메라 | 1인칭 하강 궤적 및 지상 충돌 순간 감지 | 카메라 하향 이동 속도 ≥ 1.0m/s, 충격 직전 영상 블러 |
| S-02 | 스마트워치 | GPS 고도 급강하 + 대형 충격 가속도 복합 감지 | GPS 고도 변화 ≥ 1.5m (Δh/Δt ≥ 2.0m/s), 충격 ≥ 4.0G |
| S-03 | 현장 CCTV | 3인칭 고소 위치 낙하 영상 포착 | 객체 낙하 궤적 추적, 착지 충격 프레임 감지 |
| S-04 | 통합기상센서 | 강풍 등 추락 유발 환경 조건 파악 | 풍속 ≥ 10m/s (고소작업 위험 조건) |
| S-05 | 가스센서 | 참고 데이터 | N/A |

#### 4.4.3 트리거 조건

- S-02 : GPS 고도 변화 Δh ≥ 1.5m (Δt ≤ 2초, 낙하 속도 ≥ 0.75 m/s 이상)
- S-02 : 충격 가속도 ≥ 4.0G (고소 낙하 착지 충격)
- S-01 : 카메라 하향 급속 이동 + 충격 순간 프레임 블러
- S-03 : 고소 위치 객체 낙하 궤적 탐지 (낙하 방향 Bbox 이동)
- S-04 : 풍속 ≥ 10m/s (추락 유발 환경 플래그)

#### 4.4.4 다중 센서 판단 로직

**▶ 판단 로직**

S-02 GPS 고도 급강하 + 충격 가속도가 기본 트리거. S-01·S-03 영상으로 낙하 방향 확인 시 Critical 확정. AC-003과 구분 : GPS Δh ≥ 1.5m 또는 낙하 속도 ≥ 0.75 m/s. 안전대 미착용(`safety_harness_worn=false`) 동시 시 secondary `AC-006` 부착.

#### 4.4.5 임계값 정의

| 측정 지표 | 임계값 | 단위 | 조치 |
|---|---|---|---|
| GPS 고도 변화 속도 (S-02) | ≥ 2.0 m/s (Δh/Δt) | m/s | 추락 의심 |
| 최소 낙하 높이 (S-02) | Δh ≥ 1.5m | m | 추락 확정 조건 |
| 착지 충격 (S-02) | ≥ 4.0G | G | Critical 발령 |
| 낙하 속도 (S-01) | ≥ 1.0 m/s 하향 | m/s | 추락 의심 |
| 낙하 고도 (S-03) | ≥ 1.5m (착지 지점 대비) | m | 중증도 산정 |

#### 4.4.6 오탐(False Positive) 방지 방안

- 엘리베이터·리프트 하강 : 가속도 완만 (≤ 1.5G), GPS 고도 완만 하강 → 무시
- 계단 하강 : 고도 변화 점진적, 충격 ≤ 2.0G → 임계치 미충족
- 화물 낙하 (인체 미해당) : S-03 인체 Bbox 없음 → 인체 추락 미판정

#### 4.4.7 파인튜닝용 예시 프롬프트

**▶ 입력 프롬프트 (User Input)**

```
[시스템 역할]
당신은 산업 현장 안전 관리 전문 AI입니다. 아래 5종 센서 데이터를 종합 분석하여
이상상황 발생 여부를 판단하고, 지정된 JSON 스키마로 결과를 출력하시오.

[Tier-1 사전 검출 결과]
■ S-01 스마트안전모 카메라 (작업자 ID: W-055, 타임스탬프: 2026-04-18T10:17:28+09:00)
  - 낙하 구간 영상: 0.82초 동안 하강 (발판 → 지면)
  - 착지 프레임: 영상 블러 발생 (충격 순간)
  - 낙하 전 마지막 자세: 발판 위 서 있는 자세 (정상)
  - 안전대 착용 확인: 미착용 (사전 등록 PPE 분류 결과)

■ S-03 현장 CCTV (카메라 ID: CAM-11, 구역: D동 외벽 발판 구역, 타임스탬프: 2026-04-18T10:17:28+09:00)
  - 작업자 W-055 낙하 궤적 전체 포착 (발판 5.2m → 지면)
  - 낙하 방향: 수직 낙하 (바람에 의한 편향 없음)
  - 착지 지점: CAM-11 화면 중앙 좌측
  - 인근 작업자: W-039 (4.1m), W-082 (8.9m)

[Tier-2 시계열 메트릭]
  S-01 : 카메라 하향 이동 속도=3.1m/s, 낙하 0.82초, 충격 직전 블러
  S-02 : impact_peak_g=6.2, altitude_change_m=-3.8 / 0.82s,
         fall_velocity_ms=4.63
  S-03 : 낙하 궤적 추적, 착지 지점 확정

[Tier-3 센서 정량 측정]
■ S-02 스마트워치 (작업자 ID: W-055, 타임스탬프: 2026-04-18T10:17:27+09:00)
  - 충격 가속도: 6.2G (최대값, 착지 순간)
  - GPS 고도 변화: -3.8m / 0.82초 = 4.63m/s 하강
  - 낙하 전 고도: 5.2m (발판 위치)
  - 착지 후 고도: 1.4m (지면 위치)
  - 착지 후 심박: 58 bpm → 측정 불안정 (충격 후)
  - 착지 후 가속도: X=0.0, Y=0.0, Z=9.81 (완전 정지)

■ S-04 통합기상센서 (센서 ID: WS-D01, 위치: D동 외부, 타임스탬프: 2026-04-18T10:16:00+09:00)
  - 온도: 18.3°C (정상), 습도: 62%
  - 풍속: 7.2 m/s (주의 수준 — 임계치 10m/s 미만)
  - 풍향: 북서 (NW), 기압: 1008.4 hPa (정상)
  - WBGT: 17.1 (정상)

■ S-05 가스센서 (센서 ID: GAS-D01, 구역: D동 외부, 타임스탬프: 2026-04-18T10:17:00+09:00)
  - H₂S: 0.0 ppm, CO: 2.0 ppm, LEL: 0.0%
  - O₂: 20.9%, VOC: 0.4 ppm, PM: 45 μg/m³
  - alarm_state: none

[키프레임 이미지]
  (image: S-01 keyframe), (image: S-03 keyframe)

[분석 지시]
위 5종 센서 데이터를 종합하여 이상상황 여부를 판단하고,
시스템 프롬프트의 JSON 스키마에 맞게 결과를 출력하시오. JSON 외 다른 텍스트는 출력하지 마시오.
```

**▶ 출력 응답 (Expected Model Output)**

```json
{
  "anomaly_detected": true,
  "primary_code": "AC-004",
  "secondary_codes": ["AC-006"],
  "anomaly_type": "작업자 추락",
  "severity": "Critical",
  "confidence": 0.99,
  "detected_at": "2026-04-18T10:17:28+09:00",
  "location": {
    "zone": "D동 외벽 발판 구역",
    "camera_id": "CAM-11",
    "gps": { "lat": 37.4558, "lng": 126.7330, "altitude_m": 1.4 },
    "zone_polygon_id": "ZONE-D-EXT-SCAFF"
  },
  "affected_workers": [
    { "worker_id": "W-055", "distance_m": 0.0, "status": "추락 당사자 (즉시 구조 필요)" },
    { "worker_id": "W-039", "distance_m": 4.1, "status": "구조 지원 가능" }
  ],
  "evidence": [
    { "sensor": "S-01_helmet_camera", "finding": "하강 속도 3.1m/s, 0.82초 낙하, 착지 블러" },
    { "sensor": "S-02_smartwatch",    "finding": "GPS 고도 -3.8m / 0.82초, 충격 6.2G" },
    { "sensor": "S-03_cctv",          "finding": "발판→지면 낙하 궤적 전체 포착" },
    { "sensor": "S-04_weather",       "finding": "풍속 7.2m/s (참고, 임계치 미초과)" }
  ],
  "recommended_action": [
    "call_119",
    "block_zone_entry",
    "request_rescue_team",
    "request_first_aid",
    "restrict_high_altitude_work",
    "call_safety_manager",
    "call_executive",
    "preserve_scene_for_investigation"
  ],
  "class_extension": {
    "fall_height_m":         3.8,
    "fall_velocity_ms":      4.63,
    "impact_g":              6.2,
    "start_altitude_m":      5.2,
    "landing_altitude_m":    1.4,
    "safety_harness_worn":   false
  }
}
```

---

### AC-005 | 지게차 충돌 위험 / 가동구역 진입 (Forklift Collision & Zone Entry)
**위험도 : Warning ~ Critical**

#### 4.5.1 클래스 개요

지게차가 작업자 또는 시설물과 충돌할 위험 상황, 그리고 지게차 가동 zone (사전 등록된 zone polygon) 에 작업자가 무단 진입한 상황을 통합 탐지한다. CCTV와 1인칭 카메라에서 지게차 객체와 작업자 간 거리가 위험 임계치에 근접하거나, 이동 경로가 교차하는 상황을 예측·탐지하며, 별도 트리거로 GPS 또는 CCTV polygon 기반 가동 zone 무단 진입도 본 클래스에 포함된다. 경고(≤3m 근접 또는 zone 단순 진입)와 위험(≤1.5m 또는 가동 forklift 활성 zone 진입)으로 단계를 구분하여 단계적 대응을 지원한다.

#### 4.5.2 5종 센서별 역할

| ID | 센서명 | 역할 | 핵심 판단 지표 |
|---|---|---|---|
| S-01 | 스마트안전모 카메라 | 1인칭 지게차 근접 감지 (Bbox 크기 급증) | 지게차 Bbox 면적 전체 이미지의 10% 이상 → 근접 판정 |
| S-02 | 스마트워치 | GPS 기반 지게차 이동 경로와 작업자 위치 교차 예측 | GPS 좌표 기준 지게차 이동 경로와의 교차 예측 거리 ≤ 3m |
| S-03 | 현장 CCTV | 지게차·작업자 간 거리 측정 및 경로 예측 | 지게차-작업자 거리 ≤ 3m (Warning), ≤ 1.5m (Critical) |
| S-04 | 통합기상센서 | 가시거리 영향 환경 조건 (안개·먼지) | 가시거리 저하 시 탐지 신뢰도 보정 |
| S-05 | 가스센서 | 참고 데이터 | N/A |

#### 4.5.3 트리거 조건

**▶ 충돌 트리거 (근접 기반)**

- S-03 : 지게차-작업자 거리 ≤ 3m (Warning) 또는 ≤ 1.5m (Critical)
- S-03 : 지게차 이동 방향과 작업자 위치의 경로 교차 예측 (충돌 예상 ≤ 3초)
- S-01 : 지게차 Bbox 면적 ≥ 10% (급격한 근접 접근)
- S-02 : GPS 좌표 기준 지게차-작업자 거리 ≤ 3m
- S-03 + S-02 교차 검증 : 두 센서 모두 ≤ 2m 확인 시 Critical

**▶ 가동구역 진입 트리거 (zone polygon 기반)**

- S-02 : GPS 좌표가 사전 등록된 `zone_polygon (forklift_active)` 내 ≥ 5초 체류
- S-03 : person bbox 가 사전 등록된 CCTV zone polygon 내 (GPS 미수신 시 대체 신호)
- 사전 작업허가서(`entry_authorized=true`) 미등록 시 무단 진입 확정 → Warning
- 해당 zone 에 forklift 가동 중(속도 ≥ 0.5 m/s) 동시 시 → Critical 격상

#### 4.5.4 다중 센서 판단 로직

**▶ 판단 로직**

S-03 CCTV 거리 기반 1차 판단, S-01 근접 Bbox 2차 확인, S-02 GPS 경로 예측 3차 보정. 3개 중 2개 이상 충족 시 Critical 발령. 단계적 경고 : 3m Warning → 1.5m Critical.

#### 4.5.5 임계값 정의

| 측정 지표 | 임계값 | 단위 | 조치 |
|---|---|---|---|
| 지게차-작업자 거리 (S-03) | ≤ 3m | m | Warning 발령 |
| 지게차-작업자 거리 (S-03) | ≤ 1.5m | m | Critical 발령 |
| 충돌 예측 시간 (S-03) | ≤ 3초 | sec | Critical 발령 |
| 지게차 Bbox 면적 (S-01) | ≥ 10% 이미지 면적 | % | 근접 확인 |
| GPS 거리 (S-02) | ≤ 3m | m | Warning 보강 |
| 가동 zone polygon 체류 (S-02) | ≥ 5초, `entry_authorized=false` | sec | Warning 발령 |
| 가동 zone + forklift 가동 중 | 동시 | - | Critical 격상 |

#### 4.5.6 오탐(False Positive) 방지 방안

- 지게차가 작업자 구역 우회 통과 : 방향 벡터가 작업자 미교차 → 무시
- 정지 중인 지게차 근접 : 지게차 속도 ≤ 0.2m/s → 충돌 예측 없음
- 유리벽·칸막이로 분리된 경우 : 3D GIS 구조물 데이터로 차단 판정
- 작업허가서 등록 인원의 zone 체류 : `entry_authorized=true` → 발령 무시
- zone 경계 통과만 발생 (체류 < 5초) : 발령 무시

#### 4.5.7 파인튜닝용 예시 프롬프트

**▶ 입력 프롬프트 (User Input)**

```
[시스템 역할]
당신은 산업 현장 안전 관리 전문 AI입니다. 아래 5종 센서 데이터를 종합 분석하여
이상상황 발생 여부를 판단하고, 지정된 JSON 스키마로 결과를 출력하시오.

[Tier-1 사전 검출 결과]
■ S-01 스마트안전모 카메라 (작업자 ID: W-028, 타임스탬프: 2026-04-19T15:33:12+09:00)
  - 지게차 FLT-03 Bbox: (x=120, y=80, w=310, h=400) — 이미지 면적의 23.8% (급격 증가)
  - 지게차 방향: 정면에서 작업자 방향 직진
  - 작업자 움직임: 우측 이동 중 (지게차 경로 진입)

■ S-03 현장 CCTV (카메라 ID: CAM-05, 구역: E동 물류 통로 5구역, 타임스탬프: 2026-04-19T15:33:11+09:00)
  - 지게차 FLT-03 Bbox: 속도 4.2 km/h (1.17m/s), 방향 남동 (SE, 135°)
  - 작업자 W-028과의 현재 거리: 1.3m
  - 지게차 FLT-03 GPS: lat=37.4566, lng=126.7327
  - 인근 작업자: W-091 (6.2m), W-044 (11.3m)

[Tier-2 시계열 메트릭]
  S-01 : 지게차 Bbox 면적=23.8%, 확장률=8.2%/초 (근접 가속 중)
  S-02 : GPS 이동 방향=NE(45°), 이동 속도=0.8m/s
  S-03 : current_distance_m=1.3, collision_eta_s=1.1, 경로 교차 확정

[Tier-3 센서 정량 측정]
■ S-02 스마트워치 (작업자 ID: W-028, 타임스탬프: 2026-04-19T15:33:10+09:00)
  - 심박수: 82 bpm (정상 — 위험 인지 전)
  - 체온: 36.6°C (정상)
  - 산소포화도: 98% (정상)
  - 가속도: X=0.3, Y=0.1, Z=9.78 (보행 중)
  - GPS: lat=37.4565, lng=126.7328, altitude=1.5m

■ S-04 통합기상센서 (센서 ID: WS-E01, 위치: E동 내부, 타임스탬프: 2026-04-19T15:33:00+09:00)
  - 온도: 22.1°C (정상), 습도: 48% (정상)
  - 풍속: 0.2 m/s (실내), 기압: 1013.8 hPa (정상)
  - 가시거리: 양호 (실내, 분진 없음)
  - WBGT: 20.5 (정상)

■ S-05 가스센서 (센서 ID: GAS-E01, 구역: E동 물류 통로, 타임스탬프: 2026-04-19T15:33:10+09:00)
  - H₂S: 0.0 ppm, CO: 5.0 ppm, LEL: 0.0%
  - O₂: 20.9%, VOC: 0.8 ppm, PM: 120 μg/m³
  - alarm_state: none

[키프레임 이미지]
  (image: S-01 keyframe), (image: S-03 keyframe)

[분석 지시]
위 5종 센서 데이터를 종합하여 이상상황 여부를 판단하고,
시스템 프롬프트의 JSON 스키마에 맞게 결과를 출력하시오. JSON 외 다른 텍스트는 출력하지 마시오.
```

**▶ 출력 응답 (Expected Model Output)**

```json
{
  "anomaly_detected": true,
  "primary_code": "AC-005",
  "secondary_codes": [],
  "anomaly_type": "지게차 충돌 위험",
  "severity": "Critical",
  "confidence": 0.97,
  "detected_at": "2026-04-19T15:33:12+09:00",
  "location": {
    "zone": "E동 물류 통로 5구역",
    "camera_id": "CAM-05",
    "gps": { "lat": 37.4565, "lng": 126.7328, "altitude_m": 1.5 },
    "zone_polygon_id": "ZONE-E-LOG-05"
  },
  "affected_workers": [
    { "worker_id": "W-028", "distance_m": 1.3, "status": "충돌 위험 (1.1초 예측)" }
  ],
  "evidence": [
    { "sensor": "S-01_helmet_camera", "finding": "지게차 Bbox 23.8%, 8.2%/초 확장 중" },
    { "sensor": "S-02_smartwatch",    "finding": "GPS 거리 교차 경로 진입" },
    { "sensor": "S-03_cctv",          "finding": "거리 1.3m, 충돌 예측 1.1초" }
  ],
  "recommended_action": [
    "stop_forklift_emergency",
    "alert_worker_tts",
    "alert_worker_buzzer",
    "block_zone_entry",
    "call_safety_manager"
  ],
  "class_extension": {
    "trigger_type":            "collision",
    "forklift_id":             "FLT-03",
    "current_distance_m":      1.3,
    "estimated_collision_sec": 1.1,
    "worker_gps":              { "lat": 37.4565, "lng": 126.7328 },
    "forklift_gps":            { "lat": 37.4566, "lng": 126.7327 },
    "zone_polygon_id":         "ZONE-E-LOG-05",
    "entry_authorized":        true
  }
}
```

> `trigger_type` 은 `"collision"` (근접 충돌) 또는 `"zone_entry"` (가동구역 무단 진입) 또는 `"collision+zone_entry"` (동시) 중 하나로 enum 강제.

---

### AC-006 | 안전모 미착용 (Safety Helmet Non-compliance)
**위험도 : Notice (조건부 Warning)**

#### 4.6.1 클래스 개요

작업자가 안전모를 착용하지 않은 상태로 보호구 착용 의무 구역에 진입하거나 작업하는 상황. CCTV 영상의 머리 영역 분류 모델과 스마트안전모 Wi-Fi 연결 여부를 복합 판단하여 오탐률을 최소화한다. 반복 위반 시 자동 이력 누적 및 작업 제한 조치가 연동된다. 기본 severity 는 `Notice` 이며, 의무 구역 + 고소작업 또는 의무 구역 + 활성 화재·가스 동시 발생 시 `Warning` 으로 격상된다(§4.6.5 참조).

#### 4.6.2 5종 센서별 역할

| ID | 센서명 | 역할 | 핵심 판단 지표 |
|---|---|---|---|
| S-01 | 스마트안전모 카메라 | 디바이스 Wi-Fi 연결 여부 확인 (미연결 = 미착용 의심) | Wi-Fi 연결 미확인 (타임아웃 ≥ 30초) |
| S-02 | 스마트워치 | GPS 기반 작업자 위치가 의무 착용 구역 내 확인 | GPS가 보호구 의무 구역 내 위치 확인 |
| S-03 | 현장 CCTV | 머리 부위 안전모 착용 여부 분류 (주요 탐지) | 헬멧 미착용 분류 신뢰도 ≥ 0.80 |
| S-04 | 통합기상센서 | 참고 데이터 | N/A |
| S-05 | 가스센서 | 참고 데이터 (가스 알람 + 미착용 시 severity 격상) | LEL/H₂S/O₂ 임계 동반 시 Warning 격상 |

#### 4.6.3 트리거 조건

- S-03 : 헬멧 미착용 분류 신뢰도 ≥ 0.80 (1차 트리거)
- S-01 : 스마트안전모 Wi-Fi 연결 미확인 ≥ 30초 (2차 보강)
- S-02 : 작업자 GPS가 의무 착용 구역 내 위치 확인 (필수 조건)
- S-03 + S-01 동시 충족 : 오탐률 최소화 → 최종 판정

#### 4.6.4 다중 센서 판단 로직

**▶ 판단 로직**

S-03 CCTV 분류가 1차 트리거. S-01 안전모 미연결이 보강 증거. S-02 GPS로 의무 구역 내임을 확인한 후 최종 Notice 발령. S-03만 단독으로 ≥ 0.85 신뢰도일 때는 즉시 발령 가능.

#### 4.6.5 임계값 정의 및 Severity 격상 규칙

| 측정 지표 | 임계값 | 단위 | 조치 |
|---|---|---|---|
| 헬멧 미착용 분류 신뢰도 (S-03) | ≥ 0.80 | 0~1 | Notice 발령 |
| 안전모 Wi-Fi 미연결 (S-01) | ≥ 30초 | sec | 보강 증거 |
| 의무 착용 구역 내 위치 (S-02) | GPS 반경 일치 | m | 필수 전제 조건 |
| 반복 위반 횟수 | ≥ 3회 (당일) | 회 | Warning 격상 + 작업 제한 |
| 의무 구역 + 고소작업 (altitude ≥ 2m) | 동시 충족 | - | Warning 격상 (AC-004 위험과 결합) |
| 의무 구역 + 활성 AC-001 / AC-008 | 동시 충족 | - | Warning 격상 |

#### 4.6.6 오탐(False Positive) 방지 방안

- 안전모 위에 다른 장비 착용 : 색상 분류기 보정 (안전모 색상 다양성 학습)
- 모자·두건 착용 : 분류 신뢰도 ≤ 0.80 → 미발령
- 의무 구역 외 일반 구역 이동 : GPS 구역 확인으로 필터링
- **Wi-Fi 음영·배터리 방전·AP 다운 등 디바이스 측 미연결** : §6.3 hard-negative 슬롯으로 학습 (실제 미착용 아님)

#### 4.6.7 파인튜닝용 예시 프롬프트

**▶ 입력 프롬프트 (User Input)**

```
[시스템 역할]
당신은 산업 현장 안전 관리 전문 AI입니다. 아래 5종 센서 데이터를 종합 분석하여
이상상황 발생 여부를 판단하고, 지정된 JSON 스키마로 결과를 출력하시오.

[Tier-1 사전 검출 결과]
■ S-01 스마트안전모 카메라 (작업자 ID: W-061, 타임스탬프: 2026-04-20T08:14:22+09:00)
  - Wi-Fi 연결 상태: 미연결 (타임아웃 47초)
  - 최종 연결 시각: 2026-04-20T08:13:35+09:00 (47초 전)
  - 배터리 잔량: N/A (미연결로 확인 불가)

■ S-03 현장 CCTV (카메라 ID: CAM-09, 구역: A동 Zone-A 입구, 타임스탬프: 2026-04-20T08:14:21+09:00)
  - 작업자 머리 부위 Bbox: (x=440, y=60, w=85, h=95)
  - 헬멧 착용 분류 결과: No Helmet (신뢰도 0.92)
  - 작업자 신체 전체 탐지: 정상 (Bbox 확인)
  - 안전 조끼 착용: 확인 (Orange)

[Tier-2 시계열 메트릭]
  S-01 : wifi_disconnected_sec=47
  S-02 : 이동 속도=0.3m/s (도보)
  S-03 : 헬멧 미착용 분류 신뢰도=0.92, 의무 구역 내 person 검출

[Tier-3 센서 정량 측정]
■ S-02 스마트워치 (작업자 ID: W-061, 타임스탬프: 2026-04-20T08:14:20+09:00)
  - GPS: lat=37.4568, lng=126.7332, altitude=1.2m
            (zone_polygon_id=ZONE-A-MANDATORY 내 위치 확인)
  - 심박수: 76 bpm (정상)
  - 체온: 36.5°C (정상), 산소포화도: 98% (정상)
  - 가속도: X=0.2, Y=0.1, Z=9.79 (보행 중)
  - 당일 안전모 미착용 위반 이력: 1회 (07:52, 별도 zone)

■ S-04 통합기상센서 (센서 ID: WS-A01, 위치: A동 내부, 타임스탬프: 2026-04-20T08:14:00+09:00)
  - 온도: 21.4°C (정상), 습도: 58% (정상)
  - 풍속: 0.0 m/s (실내), 기압: 1013.2 hPa (정상)
  - WBGT: 19.8 (정상)

■ S-05 가스센서 (센서 ID: GAS-A02, 구역: A동 Zone-A, 타임스탬프: 2026-04-20T08:14:20+09:00)
  - H₂S: 0.0 ppm, CO: 3.0 ppm, LEL: 0.0%
  - O₂: 20.9%, VOC: 0.5 ppm, PM: 75 μg/m³
  - alarm_state: none

[키프레임 이미지]
  (image: S-01 keyframe — 미연결로 null 가능), (image: S-03 keyframe)

[분석 지시]
위 5종 센서 데이터를 종합하여 이상상황 여부를 판단하고,
시스템 프롬프트의 JSON 스키마에 맞게 결과를 출력하시오. JSON 외 다른 텍스트는 출력하지 마시오.
```

**▶ 출력 응답 (Expected Model Output)**

```json
{
  "anomaly_detected": true,
  "primary_code": "AC-006",
  "secondary_codes": [],
  "anomaly_type": "안전모 미착용",
  "severity": "Notice",
  "confidence": 0.92,
  "detected_at": "2026-04-20T08:14:22+09:00",
  "location": {
    "zone": "A동 Zone-A (보호구 의무 착용 구역)",
    "camera_id": "CAM-09",
    "gps": { "lat": 37.4568, "lng": 126.7332, "altitude_m": 1.2 },
    "zone_polygon_id": "ZONE-A-MANDATORY"
  },
  "affected_workers": [
    { "worker_id": "W-061", "distance_m": null, "status": "당일 2회 위반 (작업 제한 임박)" }
  ],
  "evidence": [
    { "sensor": "S-01_helmet_camera", "finding": "Wi-Fi 미연결 47초 (미착용 의심)" },
    { "sensor": "S-02_smartwatch",    "finding": "GPS Zone-A 의무 구역 내 위치 확인" },
    { "sensor": "S-03_cctv",          "finding": "헬멧 미착용 분류 신뢰도 0.92" }
  ],
  "recommended_action": [
    "alert_worker_tts",
    "log_violation",
    "call_safety_manager"
  ],
  "class_extension": {
    "violation_count_today":  2,
    "zone_polygon_id":        "ZONE-A-MANDATORY",
    "wifi_disconnected_sec":  47
  }
}
```

---

### AC-007 | 작업자 건강 이상 (Worker Health Anomaly — Bio-signal)
**위험도 : Warning ~ Critical**

#### 4.7.1 클래스 개요

스마트워치의 생체신호(심박수·체온·산소포화도)가 임계치를 초과하여 열사병·저체온·심장 이상·과호흡 등이 의심되는 상황. 다른 클래스와 달리 영상 기반이 아닌 생체신호 중심의 판단이 이루어지며, 기상센서의 환경 조건과 복합 분석하여 원인을 추정한다.

> 라벨링 시 주의 — `suspected_condition` 필드는 enum 7개(`heat_stroke / hypothermia / cardiac / hyperventilation / hypoxia / syncope / unknown`) 중에서만 선택하며 자유 텍스트를 기재해서는 안 된다. 쓰러짐을 동반한 경우는 부록 A 결정 트리에 따라 `primary_code = AC-003` 이 되고 본 클래스는 `secondary_codes` 에 들어간다.

#### 4.7.2 5종 센서별 역할

| ID | 센서명 | 역할 | 핵심 판단 지표 |
|---|---|---|---|
| S-01 | 스마트안전모 카메라 | 낙상 동반 여부 확인 (복합 이상 판단) | 쓰러짐 여부 확인 (AC-003과의 연계) |
| S-02 | 스마트워치 | 주요 탐지 센서 : 심박·체온·산소포화도 이상 감지 | 심박 ≥ 140 bpm or ≤ 45 bpm, 체온 ≥ 38.5°C or ≤ 35.0°C, SpO₂ ≤ 93% |
| S-03 | 현장 CCTV | 비정상 행동 (비틀거림·속도 저하) 영상 확인 | 이동 패턴 비정상, 비틀거림 감지 |
| S-04 | 통합기상센서 | 열사병·저체온 유발 환경 조건 분석 | WBGT ≥ 28 (열사병), 온도 ≤ 5°C (저체온) |
| S-05 | 가스센서 | 저산소·VOC 노출 등 환경성 원인 변별 | O₂ ≤ 19.5% 동반 시 `hypoxia` 분류 가중 |

#### 4.7.3 트리거 조건

- S-02 : 심박수 ≥ 140 bpm 지속 ≥ 60초 (과부하 빈맥)
- S-02 : 심박수 ≤ 45 bpm 지속 ≥ 30초 (서맥·실신 전조)
- S-02 : 체온 ≥ 38.5°C (발열·열사병 의심) 또는 ≤ 35.0°C (저체온)
- S-02 : 산소포화도 ≤ 93% (저산소증·과호흡 의심)
- S-04 : WBGT ≥ 28 (고온 환경) + S-02 체온 ≥ 38°C (열사병 복합 조건)
- S-03 : 비틀거림 또는 이동 속도 급감 (정상 대비 ≤ 30%)

#### 4.7.4 다중 센서 판단 로직

**▶ 판단 로직**

S-02 생체신호 1개 이상 임계치 초과 시 Warning. 2개 이상 동시 초과 시 Critical. S-04 고온 환경 복합 시 `class_extension.suspected_condition = "heat_stroke"`. S-05 O₂ 저하 시 `"hypoxia"`. S-03 비틀거림 감지 시 즉각 Critical 격상.

#### 4.7.5 임계값 정의

| 측정 지표 | 임계값 | 단위 | 조치 |
|---|---|---|---|
| 심박수 빈맥 (S-02) | ≥ 140 bpm × 60초 | bpm | Warning |
| 심박수 서맥 (S-02) | ≤ 45 bpm × 30초 | bpm | Critical |
| 체온 발열 (S-02) | ≥ 38.5°C | °C | Warning |
| 산소포화도 (S-02) | ≤ 93% | % | Critical |
| WBGT 복합 (S-04+S-02) | ≥ 28 + 체온 ≥ 38°C | - | 열사병 Critical |
| 비틀거림 감지 (S-03) | 이동 속도 ≤ 정상 30% | % | Critical 격상 |
| O₂ 저하 동반 (S-05) | ≤ 19.5% | % | hypoxia 분류 |

#### 4.7.6 오탐(False Positive) 방지 방안

- 격렬한 작업 후 일시적 심박 상승 : 60초 미만 → 미발령
- 체온계 접촉 불량 : 지속적 이상 패턴 없을 시 필터링
- 고강도 운동 작업 : 심박 ≥ 140 bpm but 체온·SpO₂ 정상 → Warning 유지

#### 4.7.7 파인튜닝용 예시 프롬프트

**▶ 입력 프롬프트 (User Input)**

```
[시스템 역할]
당신은 산업 현장 안전 관리 전문 AI입니다. 아래 5종 센서 데이터를 종합 분석하여
이상상황 발생 여부를 판단하고, 지정된 JSON 스키마로 결과를 출력하시오.

[Tier-1 사전 검출 결과]
■ S-01 스마트안전모 카메라 (작업자 ID: W-072, 타임스탬프: 2026-04-21T14:08:55+09:00)
  - 1인칭 영상 상태: 연결 중
  - 쓰러짐 여부: 미발생 (지면 고착 없음)

■ S-03 현장 CCTV (카메라 ID: CAM-14, 구역: F동 조립 라인, 타임스탬프: 2026-04-21T14:08:52+09:00)
  - 작업자 W-072 이동 패턴 추적
  - 쓰러짐 여부: 미발생
  - 인근 작업자: W-068 (3.7m), W-095 (8.2m)

[Tier-2 시계열 메트릭]
  S-01 : 비틀거림 패턴 ±12° (수평 진동, 주기 0.3초)
  S-02 : hr_sustained=152bpm × 78s, impact_peak_g=null
  S-03 : 이동 속도=0.2m/s (정상 대비 25% — 임계치 30% 미달),
         사행 패턴 좌우 편차 ±0.4m

[Tier-3 센서 정량 측정]
■ S-02 스마트워치 (작업자 ID: W-072, 타임스탬프: 2026-04-21T14:08:50+09:00)
  - 심박수: 152 bpm (지속 78초 — 임계치 140bpm × 60초 초과)
  - 체온: 39.2°C (발열, 정상 36.0~37.5°C 초과)
  - 산소포화도: 94% (경계, 정상 ≥ 95%)
  - 가속도: X=0.9, Y=1.1, Z=9.3 (보행 중 비틀거림 패턴)
  - GPS: lat=37.4569, lng=126.7335, altitude=2.1m

■ S-04 통합기상센서 (센서 ID: WS-F01, 위치: F동 내부, 타임스탬프: 2026-04-21T14:08:00+09:00)
  - 온도: 38.5°C (고온 작업 환경), 습도: 74%
  - 풍속: 0.1 m/s (환기 거의 없음), 기압: 1009.7 hPa
  - WBGT: 35.3 (열사병 고위험 — 임계치 28 초과)

■ S-05 가스센서 (센서 ID: GAS-F01, 구역: F동 조립 라인, 타임스탬프: 2026-04-21T14:08:50+09:00)
  - H₂S: 0.0 ppm, CO: 10.0 ppm, LEL: 0.0%
  - O₂: 20.7%, VOC: 1.4 ppm, PM: 180 μg/m³
  - alarm_state: none

[키프레임 이미지]
  (image: S-01 keyframe), (image: S-03 keyframe)

[분석 지시]
위 5종 센서 데이터를 종합하여 이상상황 여부를 판단하고,
시스템 프롬프트의 JSON 스키마에 맞게 결과를 출력하시오. JSON 외 다른 텍스트는 출력하지 마시오.
```

**▶ 출력 응답 (Expected Model Output)** — 쓰러짐 미동반 사례 (쓰러짐 동반 시 `primary_code = AC-003`, `secondary_codes = ["AC-007"]` 이 됨).

```json
{
  "anomaly_detected": true,
  "primary_code": "AC-007",
  "secondary_codes": [],
  "anomaly_type": "작업자 건강 이상 — 열사병 의심",
  "severity": "Critical",
  "confidence": 0.93,
  "detected_at": "2026-04-21T14:08:55+09:00",
  "location": {
    "zone": "F동 조립 라인",
    "camera_id": "CAM-14",
    "gps": { "lat": 37.4569, "lng": 126.7335, "altitude_m": 2.1 },
    "zone_polygon_id": "ZONE-F-ASSY-02"
  },
  "affected_workers": [
    { "worker_id": "W-072", "distance_m": 0.0, "status": "열사병 의심 — 즉시 조치 필요" }
  ],
  "evidence": [
    { "sensor": "S-01_helmet_camera", "finding": "비틀거림 패턴 ±12°/0.3초 진동" },
    { "sensor": "S-02_smartwatch",    "finding": "심박 152bpm × 78초, 체온 39.2°C, SpO₂ 94%" },
    { "sensor": "S-03_cctv",          "finding": "이동 속도 25% (30% 임계치 근접), 사행 ±0.4m" },
    { "sensor": "S-04_weather",       "finding": "WBGT 35.3 (고위험), 온도 38.5°C, 환기 없음" }
  ],
  "recommended_action": [
    "evacuate_to_safe_zone",
    "deploy_cooling_pad",
    "increase_ventilation",
    "call_119",
    "request_first_aid",
    "call_safety_manager"
  ],
  "class_extension": {
    "suspected_condition":   "heat_stroke",
    "vitals":                { "hr_bpm": 152, "temp_c": 39.2, "spo2_percent": 94 },
    "environmental_trigger": "wbgt_high"
  }
}
```

---

### AC-008 | 가스누출 / 질식 / 위험구역 진입 (Gas Leak, Asphyxiation, Hazardous Zone Entry)
**위험도 : Critical (조건부 Warning)**

#### 4.8.1 클래스 개요

밀폐공간 또는 화학물질 취급 구역에서 (1) 유독가스 누출·산소 결핍이 발생하거나, (2) 작업자가 사전 등록된 가스·밀폐공간·화학물질 저장 구역 polygon 에 무단 진입한 상황을 통합 탐지한다. 산업현장 주요 유독가스(H₂S, CO, CH₄, O₂ 결핍, VOC 등) 대부분이 무색·무취이므로 VLM 이 raw 이미지에서 직접 검출하는 것은 원리적으로 불가능하다. 따라서 **S-05 가스센서가 1차 트리거**, **YOLO person 검출이 2차 신호**, **VLM 시각 단서가 3차 보강**이 되는 cascade 구조로 탐지한다. 가스 알람 없이 zone 진입만 발생한 경우 Warning, 가스 알람 + person 동시 시 Critical 로 분리된다.

#### 4.8.2 5종 센서별 역할

| ID | 센서명 | 역할 | 핵심 판단 지표 |
|---|---|---|---|
| S-01 | 스마트안전모 카메라 | 호흡보호구 착용 여부, 액체 spill·드럼 파손 등 시각 단서 보강 | 호흡보호구 미착용, 액체 누출, GHS 픽토그램 |
| S-02 | 스마트워치 | SpO₂ 급감, 심박 급변 (질식 전조) | SpO₂ ≤ 90%, 심박 ≥ 130bpm × 30초 |
| S-03 | 현장 CCTV | 해당 zone 내 person 검출, 색이 있는 plume 확인 | person bbox 존재, plume Bbox |
| S-04 | 통합기상센서 | 확산 방향 (풍향) — 인접 작업자 영향 추정 | wind_direction (N..NW) |
| S-05 | 가스센서 | **1차 트리거** — 채널별 임계 초과 | H₂S ≥ 10ppm, CO ≥ 30ppm, LEL ≥ 10%, O₂ ≤ 19.5%, VOC ≥ TWA, PM ≥ 5mg/m³ |

#### 4.8.3 트리거 조건

**▶ 가스누출/질식 트리거 (Cascade)**

- **1차 (필수)** S-05 : H₂S ≥ 10ppm OR CO ≥ 30ppm OR LEL ≥ 10% OR O₂ ≤ 19.5% OR VOC ≥ TWA OR PM ≥ 5mg/m³ — 1개 이상 채널 활성
- **2차 (필수)** S-01/S-03 : 해당 zone 에 person 검출 (Bbox confidence ≥ 0.70)
- **3차 (보강)** VLM 시각 단서 : 액체 spill / 드럼 파손 / 호흡보호구 미착용 / GHS 픽토그램 / 색이 있는 plume
- **4차 (보강)** S-02 : SpO₂ ≤ 90% 또는 심박 ≥ 130bpm × 30초 (질식 전조)
- **5차 (보강)** S-04 : 풍향 → 인접 작업자 노출 zone 추정

**▶ 위험구역 진입 트리거 (zone polygon 기반)**

- S-02 : GPS 좌표가 사전 등록된 `zone_polygon (gas / confined_space / chemical_storage)` 내 ≥ 5초 체류
- S-03 : person bbox 가 사전 등록된 CCTV zone polygon 내 (GPS 미수신 시 대체)
- 사전 작업허가서(`entry_authorized=true`) 미등록 시 무단 진입 확정 → Warning
- 가스 알람(위 1차) 동시 활성 시 → Critical 격상 (질식 위험)

#### 4.8.4 다중 센서 판단 로직

**▶ 판단 로직**

S-05 가스센서 알람이 발령된 zone 에 S-01/S-03 의 person 이 검출되면 즉시 AC-008 Critical 발령. VLM 은 raw 이미지에서 가스를 "검출"하지 않고, 시각 단서를 추가 evidence 로 부착하여 상황을 설명·판단한다. 위험구역 무단 진입 단독(가스 알람 없음)은 Warning 으로 분리하여 동일 클래스 내에서 `trigger_type` 으로 구분한다.

#### 4.8.5 임계값 정의

| 측정 지표 | 임계값 | 단위 | 조치 |
|---|---|---|---|
| H₂S (S-05) | ≥ 10 ppm | ppm | Critical (즉각 대피) |
| CO (S-05) | ≥ 30 ppm | ppm | Critical |
| LEL (S-05) | ≥ 10% | % LEL | Warning / ≥ 25% Critical |
| O₂ (S-05) | ≤ 19.5% | % | Critical (저산소) |
| VOC (S-05) | ≥ TWA | ppm | Warning |
| PM (S-05, 호흡성) | ≥ 5 mg/m³ | mg/m³ | Warning |
| SpO₂ (S-02) | ≤ 90% | % | 질식 전조 — 신뢰도 가산 |
| Zone polygon 체류 (S-02) | ≥ 5초, `entry_authorized=false` | sec | Warning 발령 (가스 알람 없을 때) |
| Zone polygon + 가스 알람 동시 | 동시 | - | Critical 격상 |

#### 4.8.6 오탐(False Positive) 방지 방안

- 센서 calibration drift : 24시간 무알람 baseline 대비 +N×σ 이상일 때만 발령
- 용접·도장 작업 정상 VOC : 작업허가서(`entry_authorized=true`) 등록된 구역은 임계 ×1.5 상향
- 가스센서 단독 알람 (person 미검출) : Alert only (작업자 영향 없음으로 처리)
- 작업허가서 등록 인원의 zone 체류 : `entry_authorized=true` → 진입 단독 발령 무시 (가스 알람은 여전히 발령)
- zone 경계 통과만 발생 (체류 < 5초) : 진입 발령 무시

#### 4.8.7 파인튜닝용 예시 프롬프트

**▶ 입력 프롬프트 (User Input)**

```
[시스템 역할]
당신은 산업 현장 안전 관리 전문 AI입니다. 아래 5종 센서 데이터를 종합 분석하여
이상상황 발생 여부를 판단하고, 지정된 JSON 스키마로 결과를 출력하시오.

[Tier-1 사전 검출 결과]
■ S-01 스마트안전모 카메라 (작업자 ID: W-088, 타임스탬프: 2026-04-22T11:05:32+09:00)
  - 시각 단서 탐지: 액체 spill (탱크 하부), 호흡보호구 미착용
  - 작업자 자세: 탱크 내부 굽힘 자세
  - 카메라 방향: 하향 (탱크 바닥)

■ S-03 현장 CCTV (카메라 ID: CAM-A12, 구역: A동 밀폐탱크 #3, 타임스탬프: 2026-04-22T11:05:31+09:00)
  - 탱크 내부 person W-088 검출 (Bbox confidence 0.91)
  - 탱크 입구 person W-091 검출 (Bbox confidence 0.94, 보조 역할)
  - 인근 작업자: W-091 (2.1m)

[Tier-2 시계열 메트릭]
  S-01 : visual_cues=[liquid_spill, missing_respirator]
  S-02 : SpO₂ 시계열 하강 추세 (98% → 94%, 30초 구간)
  S-03 : person W-088 zone_polygon ZONE-A-CONFINED-03 내 체류 4분

[Tier-3 센서 정량 측정]
■ S-02 스마트워치 (작업자 ID: W-088, 타임스탬프: 2026-04-22T11:05:30+09:00)
  - 심박수: 118 bpm (작업 중 허용 범위 상한)
  - 체온: 37.1°C (정상)
  - 산소포화도: 94% (경계 — 하강 추세)
  - GPS: lat=37.4570, lng=126.7340, altitude=1.0m
            (zone_polygon_id=ZONE-A-CONFINED-03 내 위치, entry_authorized=true)
  - 가속도: X=0.2, Y=0.1, Z=9.78

■ S-04 통합기상센서 (센서 ID: WS-A02, 위치: A동 외부, 타임스탬프: 2026-04-22T11:05:00+09:00)
  - 온도: 24.6°C (정상), 습도: 55%
  - 풍속: 1.8 m/s, 풍향: 북동 (NE)
  - 기압: 1011.4 hPa (정상), WBGT: 22.7 (정상)

■ S-05 가스센서 (센서 ID: GAS-A01, 구역: A동 밀폐탱크 #3, 타임스탬프: 2026-04-22T11:05:30+09:00)
  - H₂S: 18.4 ppm (TWA 10ppm × 1.84, 임계 초과)
  - CO: 22.0 ppm, LEL: 8.0%
  - O₂: 19.2% (저산소 — 19.5% 미만)
  - VOC: 12.5 ppm, PM: 320 μg/m³
  - alarm_state: warning, alarm_channels: ["h2s", "o2"]

[키프레임 이미지]
  (image: S-01 keyframe), (image: S-03 keyframe)

[분석 지시]
위 5종 센서 데이터를 종합하여 이상상황 여부를 판단하고,
시스템 프롬프트의 JSON 스키마에 맞게 결과를 출력하시오. JSON 외 다른 텍스트는 출력하지 마시오.
```

**▶ 출력 응답 (Expected Model Output)**

```json
{
  "anomaly_detected": true,
  "primary_code": "AC-008",
  "secondary_codes": [],
  "anomaly_type": "가스누출/질식 — H₂S + O₂ 결핍 (밀폐공간)",
  "severity": "Critical",
  "confidence": 0.97,
  "detected_at": "2026-04-22T11:05:32+09:00",
  "location": {
    "zone": "A동 밀폐탱크 #3",
    "camera_id": "CAM-A12",
    "gps": { "lat": 37.4570, "lng": 126.7340, "altitude_m": 1.0 },
    "zone_polygon_id": "ZONE-A-CONFINED-03"
  },
  "affected_workers": [
    { "worker_id": "W-088", "distance_m": 0.5, "status": "탱크 내부 — 즉시 구조" },
    { "worker_id": "W-091", "distance_m": 2.1, "status": "탱크 입구 보조" }
  ],
  "evidence": [
    { "sensor": "S-05_gas",           "finding": "H₂S 18.4ppm (TWA×1.8), O₂ 19.2% (저산소)" },
    { "sensor": "S-01_helmet_camera", "finding": "호흡보호구 미착용, 액체 spill 확인" },
    { "sensor": "S-03_cctv",          "finding": "탱크 내부 person W-088 검출" },
    { "sensor": "S-04_weather",       "finding": "풍향 NE — 외부 확산 가능성 낮음" }
  ],
  "recommended_action": [
    "evacuate_zone",
    "shutoff_gas_supply",
    "increase_ventilation",
    "deploy_respirator",
    "request_rescue_team",
    "call_119",
    "call_safety_manager",
    "call_executive"
  ],
  "class_extension": {
    "trigger_type":      "gas_leak+zone_entry",
    "gas_readings":      { "h2s_ppm": 18.4, "co_ppm": 22.0, "lel_percent": 8.0,
                           "o2_percent": 19.2, "voc_ppm": 12.5, "pm_ug_m3": 320 },
    "exceeded_channels": ["h2s", "o2"],
    "visual_cues":       ["missing_respirator", "liquid_spill"],
    "wind_direction":    "NE",
    "respirator_worn":   false,
    "zone_polygon_id":   "ZONE-A-CONFINED-03",
    "zone_type":         "confined_space",
    "entry_authorized":  true
  }
}
```

> `trigger_type` 은 `"gas_leak"` (가스누출 단독) / `"zone_entry"` (위험구역 진입 단독, Warning) / `"gas_leak+zone_entry"` (동시, Critical) 중 하나로 enum 강제.

---


## 제5장. 학습 데이터셋 구조 및 JSON 포맷 정의

### 5.1 디렉토리 구조

```
/vlm_finetune_dataset/
├── train/                              # 학습 데이터 (전체의 70%)
│   ├── AC-001_fire_smoke/
│   │   ├── images/                     # 헬멧캠·CCTV 키프레임 (JPEG)
│   │   │   ├── helmet/                 # S-01 스마트안전모 카메라
│   │   │   └── cctv/                   # S-03 현장 CCTV
│   │   ├── coco/                       # 비전 라벨 (COCO format) — 제7장
│   │   │   ├── helmet_annotations.json
│   │   │   └── cctv_annotations.json
│   │   ├── sensor_logs/                # S-02·S-04·S-05 시계열 raw (JSONL)
│   │   │   ├── {sample_id}_smartwatch.jsonl
│   │   │   ├── {sample_id}_weather.jsonl
│   │   │   └── {sample_id}_gas.jsonl
│   │   ├── annotations/                # 프롬프트-응답 쌍 (JSONL)
│   │   │   └── annotations.jsonl       # 1줄 = 1 샘플
│   │   └── _checksum.txt               # 무결성 해시
│   ├── AC-002_entrapment/
│   ├── AC-003_fall_collapse/
│   ├── AC-004_fall_height/
│   ├── AC-005_forklift_collision/
│   ├── AC-006_no_helmet/
│   ├── AC-007_health_anomaly/
│   ├── AC-008_gas_leak/                # 가스누출 + 위험구역 진입 통합
│   ├── AC-000_normal/                  # 정상 상황 (Negative Sample)
│   └── AC-MULTI_combo/                 # 다중 라벨 샘플 (primary/secondary)
├── val/                                # 검증 데이터 (15%)
│   └── [동일 구조]
├── test/                               # 테스트 데이터 (15%)
│   └── [동일 구조]
└── _meta/
    ├── zone_polygons.geojson           # zone_polygon_id 정의
    ├── camera_calibration.json         # CCTV calibration 정보
    ├── machine_registry.json           # 기계 ID 등록부
    ├── enum_definitions.json           # 부록 B enum 전체
    ├── vlm_response.schema.json        # 부록 C schema
    └── DATA_SHEET.md                   # Datasheets for Datasets 형식
```

### 5.2 개별 학습 샘플 JSON 포맷

각 학습 샘플은 JSONL(JSON Lines) 포맷으로 저장되며, 한 줄이 하나의 학습 샘플(프롬프트-응답 쌍)을 구성한다. 단일 샘플은 다음 7개 영역을 포함한다 — 메타 정보, `inputs` (5종 센서별 Tier-1/2/3 결과), `prompt` (system/user), `response` (`primary_code`/`secondary_codes`/`class_extension` 포함), 그리고 시계열 raw 는 `sensor_logs/` 외부 파일로 분리되어 `raw_log_path` 로 참조된다.

```json
{
  "sample_id":        "AC001-TRAIN-000001",
  "primary_code":     "AC-001",
  "secondary_codes":  [],
  "anomaly_type":     "화재/연기",
  "severity":         "Critical",
  "split":            "train",
  "created_at":       "2026-01-15T09:00:00+09:00",
  "labeler_id":       "LBL-003",
  "review_status":    "approved",
  "irr_score":        0.83,
  "data_origin":      "real",
  "site_id":          "GEOMDAN-01",
  "shift":            "day",
  "weather_cond":     "clear",
  "inputs": {
    "helmet_camera": {
      "worker_id":          "W-042",
      "timestamp":          "2026-04-15T14:23:10+09:00",
      "image_path":         "train/AC-001_fire_smoke/images/helmet/AC001-TRAIN-000001_H.jpg",
      "image_resolution":   "1280x720",
      "tier1_detections": [
        { "class":"fire",  "bbox":[320,180,210,155], "confidence":0.94 },
        { "class":"smoke", "bbox":[280,100,380,240], "confidence":0.89 }
      ],
      "tier2_metrics": {
        "fire_pixel_ratio":  0.068,
        "pose_change_rate":  null,
        "view_rotation_dps": 35,
        "view_direction":    "right_45"
      },
      "wifi_connected":     true,
      "coco_annotation_id": 17
    },
    "smartwatch": {
      "worker_id":     "W-042",
      "timestamp":     "2026-04-15T14:23:08+09:00",
      "tier3_vitals":  { "hr_bpm":134, "temp_c":37.4, "spo2_percent":95 },
      "tier3_gps":     { "lat":37.4561, "lng":126.7324, "altitude_m":4.2 },
      "tier2_metrics": {
        "hr_sustained":          { "bpm":134, "duration_s":52 },
        "impact_peak_g":         null,
        "accel_stop_duration_s": null,
        "altitude_change_m":     0.0
      },
      "raw_log_path":  "train/AC-001_fire_smoke/sensor_logs/AC001-TRAIN-000001_smartwatch.jsonl"
    },
    "cctv": {
      "camera_id":     "CAM-03",
      "zone":          "B동 2구역",
      "timestamp":     "2026-04-15T14:23:09+09:00",
      "image_path":    "train/AC-001_fire_smoke/images/cctv/AC001-TRAIN-000001_C.jpg",
      "tier1_detections": [
        { "class":"fire",   "bbox":[600,300,320,280], "confidence":0.91 },
        { "class":"person", "bbox":[420,310, 50, 90], "confidence":0.96, "worker_id":"W-042" }
      ],
      "tier2_metrics": {
        "smoke_blob_ratio":   0.183,
        "smoke_direction":    "NE",
        "smoke_speed_ms":     0.8,
        "person_machine_iou": null,
        "collision_eta_s":    null
      },
      "nearby_workers": [
        { "worker_id":"W-042", "distance_m":1.8 },
        { "worker_id":"W-031", "distance_m":4.2 }
      ],
      "coco_annotation_id": 42
    },
    "weather_sensor": {
      "sensor_id":     "WS-B02",
      "timestamp":     "2026-04-15T14:23:05+09:00",
      "readings":      { "temp_c":47.8, "rh_percent":14, "wind_speed_ms":0.2,
                          "wind_direction":"NE", "pressure_hpa":1010.2, "wbgt":41.2 },
      "raw_log_path":  "train/AC-001_fire_smoke/sensor_logs/AC001-TRAIN-000001_weather.jsonl"
    },
    "gas_sensor": {
      "sensor_id":     "GAS-B01",
      "timestamp":     "2026-04-15T14:23:06+09:00",
      "readings":      { "h2s_ppm":0.0, "co_ppm":15.0, "lel_percent":0.0,
                          "o2_percent":20.8, "voc_ppm":2.1, "pm_ug_m3":680 },
      "alarm_state":   "none"
    }
  },
  "prompt": {
    "system":  "[SYSTEM PROMPT 전문 — 제3장 3.1절 참조]",
    "user":    "[USER PROMPT — 센서 데이터 자연어 변환 + 분석 지시, 제3장 3.2절 템플릿]"
  },
  "response": {
    "anomaly_detected": true,
    "primary_code":     "AC-001",
    "secondary_codes":  [],
    "anomaly_type":     "화재/연기",
    "severity":         "Critical",
    "confidence":       0.96,
    "detected_at":      "2026-04-15T14:23:10+09:00",
    "location": {
      "zone":            "B동 2구역",
      "camera_id":       "CAM-03",
      "gps":             { "lat":37.4561, "lng":126.7324, "altitude_m":4.2 },
      "zone_polygon_id": "ZONE-B-002"
    },
    "affected_workers": [
      { "worker_id":"W-042", "distance_m":1.8, "status":"이동 중 (HR 134bpm)" },
      { "worker_id":"W-031", "distance_m":4.2, "status":"위험 근접" }
    ],
    "evidence": [
      { "sensor":"S-01_helmet_camera", "finding":"화염 Bbox 0.94, 픽셀 비율 6.8%" },
      { "sensor":"S-03_cctv",          "finding":"연기 Blob 면적 18.3%, NE 0.8m/s" },
      { "sensor":"S-04_weather",       "finding":"온도 47.8°C (+23.8°C), WBGT 41.2" }
    ],
    "recommended_action": [
      "evacuate_all_immediate",
      "trigger_fire_suppression_local",
      "call_119",
      "isolate_zone_downwind",
      "alert_nearby_workers"
    ],
    "class_extension": {
      "fire_pixel_ratio":  0.068,
      "smoke_blob_ratio":  0.183,
      "spread_direction":  "NE",
      "spread_speed_ms":   0.8,
      "estimated_area_m2": 4.2
    }
  }
}
```

### 5.3 시계열 raw 데이터 별도 파일 구조

S-02 가속도(0.1초 간격), 심박(1초), S-04(10초), S-05(1초) 등 raw 시계열은 `inputs` 안에 포함하지 않고 `sensor_logs/*.jsonl` 별도 파일에 저장한다. 학습 시 raw 시계열은 prompt 에 들어가지 않으나, Tier-2 메트릭의 ground-truth 재현·검증·재라벨에 사용된다.

```
# AC001-TRAIN-000001_smartwatch.jsonl (예시 — 1초 1줄)
{"ts":"2026-04-15T14:22:50+09:00", "hr":98,  "temp":37.1, "spo2":97, "ax":0.1, "ay":0.0, "az":9.81, "lat":37.4560, "lng":126.7324, "alt":4.2}
{"ts":"2026-04-15T14:22:51+09:00", "hr":102, "temp":37.1, ...}
```

### 5.4 다중 라벨 샘플 (AC-MULTI)

동시 발생 사건은 `train/AC-MULTI_combo/` 디렉토리에 저장하며, `primary_code` 는 부록 A 결정 트리로 단일 결정, `secondary_codes` 는 활성 코드 배열. annotation 파일 구조는 5.2와 동일.

```
# 예: 추락(AC-004) + 안전대 미착용
"primary_code": "AC-004",
"secondary_codes": ["AC-006"],
"class_extension": { "fall_height_m":3.8, "safety_harness_worn":false, ... }
```

### 5.5 Negative Sample (AC-000)

정상 상황 샘플. `anomaly_detected=false`, `primary_code=null`, response 의 나머지 필드도 null. `evidence` 는 빈 배열이 아닌 "정상" 근거를 명시(예: `"모든 센서 정상 범위"`). 제6장 hard-negative 정책에 따라 환경 매칭·난이도 stratification 적용. 자세한 비율 정책은 §6.3 참조.

---

## 제6장. 학습 데이터셋 규모 및 품질 계획

### 6.1 per-class 목표 샘플 수

Cascade 구조(§1.3) 전제. VLM 이 reasoning + JSON formatting 을 학습하는 task 이므로 end-to-end VLM 대비 per-class 요구치는 낮으나, JSON schema 준수와 long-tail 클래스 generalization 을 위해 다음을 기준으로 한다.

| 클래스 | 최소 | 목표 | 비고 |
|---|---|---|---|
| AC-001 화재/연기 | 400 | 800 | 시각적 명확. 합성 데이터(시뮬레이션·뉴스) 비율 ≤ 40% |
| AC-002 끼임 | 300 | 600 | long-tail. 실사고 영상 입수 한계 → 합성 비율 ≤ 60% 허용 |
| AC-003 낙상/쓰러짐 | 400 | 800 | 실연·재현 가능. `suspected_cause` 별 균등 |
| AC-004 추락 | 300 | 600 | long-tail. 합성 ≤ 60%, 안전대 착용/미착용 균등 |
| AC-005 지게차 충돌 / 가동구역 진입 | 500 | 1,000 | 충돌 근접 + 가동 zone 진입 통합. `trigger_type` 별 균등 |
| AC-006 안전모 미착용 | 500 | 1,000 | 흔함. 의무 구역 외 hard-negative 충분 수집 |
| AC-007 건강 이상 | 400 | 800 | `suspected_condition` enum 7개 별 균등 (각 ≥ 100) |
| AC-008 가스누출 / 위험구역 진입 | 500 | 1,000 | 가스 알람·시각 단서·zone 진입 통합. `trigger_type` 별 균등 |
| AC-MULTI 다중 라벨 | 300 | 600 | 주요 조합 위주 |
| AC-000 Negative | 3,500 | 7,000 | §6.3 비율 정책 |
| **합계** | **약 7,000** | **약 14,000** | **본 문서 발주 기준** |

### 6.2 Train / Val / Test 분할 정책

- 비율 : Train 70% / Val 15% / Test 15%
- 분할 단위 : `sample_id` 가 아닌 `site_id × worker_id × incident_id` 그룹 단위로 분할 (data leakage 방지)
- 각 split 내 클래스 비율은 전체 비율과 ±5% 이내 일치
- Test set 은 사이트별로 1개 사이트를 통째로 hold-out (out-of-distribution generalization 평가)

### 6.3 Negative Sample 비율 정책

- 전체 비율 : Positive : Negative = 1 : 1.5 (FP 통제 목적)
- Negative 구성 : (a) 환경 매칭 정상 50% + (b) hard-negative 35% + (c) 일반 정상 15%
- 환경 매칭 정상 : 같은 사이트·시간대·조명에서 정상 작업 중인 샘플 (각 Positive 1개당 1개 페어)
- hard-negative : 클래스별 4.x.6 오탐 방지 절에 기술된 케이스를 슬롯화

| 클래스 | 본문 기술 hard-negative 사례 |
|---|---|
| AC-001 화재 | 용접 스파크, 수증기·증기, 석양·조명 반사 |
| AC-002 끼임 | 자발적 휴식 정지, 공구 조작 중 손 정지, 의도적 고정 작업(볼트 조임) |
| AC-003 낙상 | 자발적 웅크림·앉음, 도구 줍기, 의도적 바닥 작업(배관) |
| AC-004 추락 | 엘리베이터·리프트 하강, 계단 하강, 화물 낙하 |
| AC-005 충돌 | 작업자 구역 우회, 정지 중인 지게차, 유리벽 분리 |
| AC-006 안전모 | 모자·두건, 의무 구역 외 이동, 안전모 위 다른 장비, **Wi-Fi 음영·배터리 방전·AP 다운에 의한 미연결** |
| AC-007 건강 | 격렬한 작업 후 일시적 심박 상승, 체온계 접촉 불량 |
| AC-008 가스 / 위험구역 | 용접·도장 작업 정상 VOC, 가스센서 calibration drift, 작업허가서 등록 인원의 zone 체류, zone 경계 통과만 발생 (< 5초) |

### 6.4 Stratification 매트릭스

각 클래스의 Train 샘플은 다음 4개 축에서 균형 잡힌 분포를 가져야 한다.

| 축 | 범주 | 권장 분포 |
|---|---|---|
| 사이트 | 사이트 A / 사이트 B / (합성) | 40% / 40% / 20% |
| 시간대 | 주간 / 야간 / 교대 전환 | 60% / 30% / 10% |
| 조도 | 주광 / 형광 / 저조도 / 야간 인공조명 | 40% / 30% / 15% / 15% |
| 계절·기상 | 봄·가을 / 여름(고온) / 겨울(저온) / 우천 | 40% / 30% / 20% / 10% |

> 클래스 특수 요건 — AC-003 heat_stroke 케이스는 여름·고온 stratum 우선. AC-007 hypothermia 는 겨울·저온 stratum 우선. AC-004 추락은 풍속 강한 우천 일부 포함.

### 6.5 합성 데이터 vs 실데이터 비율

- AC-001, 003, 005, 006, 007, 009 : 합성 ≤ 40%
- AC-002, 004 : 합성 ≤ 60% 허용
- AC-008 : 합성 + 센서 시뮬레이션 ≤ 70%
- 합성 출처 : Unity/Unreal 시뮬레이션, 산업안전 교육 영상 라이선스, 산업안전공단 공개 자료
- Sim-to-real gap 평가 : Test set 합성 비율 ≤ 10%

### 6.6 클래스 불균형 처리 정책

- Long-tail 클래스(AC-002, AC-004, AC-008) : 학습 시 weighted sampling (가중치 = `sqrt(N_max / N_class)`)
- Hard-example mining : Val recall 하위 10% 샘플을 다음 epoch 에서 2× sampling
- Focal loss (γ=2) 적용 — multi-label binary cross-entropy 의 음성/양성 불균형 보정

---

## 제7장. 비전 라벨 표준 및 시계열 메트릭 산출 규약

본 장은 라벨러가 (1) 학습 셋의 이미지에 그리는 비전 라벨 포맷, (2) 본 문서 곳곳에 등장하는 시계열 메트릭(자세 변화율·충격 가속도·고도 변화 등)의 산출 출처와 검증 방식을 정의한다.

### 7.1 라벨 표준 — COCO 채택

비전 라벨은 COCO format (instances / keypoints / segmentation 통합)을 채택한다. MMDetection·Detectron2·Ultralytics 등 학습 프레임워크 지원과 CVAT·Label Studio 등 라벨링 도구 export 표준이 모두 COCO 기반이기 때문이다.

### 7.2 Bounding Box 좌표 규약

- 좌표계 : 절대 픽셀 (이미지 원본 1280×720 기준)
- 형식 : `[x_min, y_min, width, height]` — COCO 표준 (top-left 기준)
- 정규화 사용 금지 (annotation 단계). 학습 직전 정규화는 학습 파이프라인에서 처리
- Bbox 최소 크기 : 16×16 픽셀 (그 이하는 ignore 라벨)

### 7.3 세그멘테이션 마스크

- 대상 클래스 : fire, smoke, liquid_spill, plume
- 형식 : COCO polygon (RLE 는 export 시 자동 생성)
- 이유 : `fire_pixel_ratio`, `smoke_blob_ratio` 등 면적 비율 메트릭이 학습 라벨로 사용되므로 polygon mask 필수
- 정확도 : 마스크 IoU ≥ 0.85 (이중 라벨링 시 합의 기준)

### 7.4 Keypoint / Pose 라벨

- 대상 : person (작업자) — COCO 17 keypoint 표준
- 용도 : Tier-2 메트릭(자세 변화율, 시점 회전, Bbox 종횡비)의 ground-truth 산출 검증
- 필수 케이스 : AC-002, AC-003, AC-004, AC-007 의 모든 Positive 샘플 (Negative 는 일부)

### 7.5 Multi-Object Tracking (MOT)

- 대상 : forklift (AC-005), person (AC-005 zone_entry, AC-008 zone_entry)
- 형식 : COCO video annotation extension — `track_id` 부여, 1초 내 동일 객체 추적
- `forklift_id`, `worker_id` 는 `track_id` 와 1:1 매핑

### 7.6 시계열 메트릭 ground-truth 산출 규약

본 문서 곳곳에 등장하는 "자세 변화율 1.8%", "시점 회전 82°/초", "충격 6.2G" 등의 메트릭은 라벨러가 직접 수치를 입력하는 것이 아니라, 다음 자동 산출 파이프라인을 통해 산출되며 라벨러는 이를 **검수·승인**하는 역할만 담당한다.

| 메트릭 | 산출 도구·모델 | 검증 방식 |
|---|---|---|
| `pose_change_rate` (%) | MediaPipe Pose / OpenPose — 키포인트 17점의 frame-to-frame Euclidean distance 평균 | 라벨러가 keypoint 라벨 직접 확인 후 자동 산출 결과 승인 |
| `view_rotation_dps` (°/s) | IMU(스마트안전모 내장) 자이로 데이터 적분 — fallback: optical flow | 키프레임 5장 sampling, 라벨러 시각 확인 |
| `view_direction` | IMU pitch/yaw 임계 분류 (-30° / +30°) | 자동 분류 + 라벨러 spot check 10% |
| `impact_peak_g` (G) | S-02 가속도 raw 시계열의 ±2초 윈도우 max(\|a\|) | 시계열 raw log 자동 산출, hash 검증 |
| `accel_stop_duration_s` | S-02 가속도 \|ΔA\| ≤ 0.05G 연속 구간 길이 | 자동 산출 |
| `hr_sustained` | S-02 심박 시계열에서 임계 초과 연속 구간 | 자동 산출 |
| `altitude_change_m` / m/s | S-02 GPS altitude 시계열의 ±1초 윈도우 Δh | 자동 산출 + Tier-1 영상 cross-check |
| `bbox_aspect_ratio` | CCTV person bbox 의 width/height | COCO bbox로부터 자동 계산 |
| `person_machine_iou` | CCTV person bbox 와 사전 등록 machine bbox 의 IoU | 자동 계산 |
| `collision_eta_s` | forklift 와 person 의 속도 벡터 (Kalman filter) | 자동 계산 + 라벨러 spot check |
| `smoke_blob_ratio` / `fire_pixel_ratio` | 세그멘테이션 polygon 면적 / 이미지 면적 | 라벨러가 polygon 라벨 확인 |

각 메트릭의 산출 결과는 `inputs.{sensor}.tier2_metrics` 필드에 저장된다. 라벨러는 자동 산출 결과를 검수·승인하는 역할이며, 직접 수치 입력은 금지한다.

### 7.7 라벨링 도구 표준

- 영상·이미지 : CVAT (오픈소스, COCO export) — bbox, polygon, keypoint, MOT 모두 지원
- 시계열 메트릭 검수 : Tier-2 자동 산출 결과를 영상과 동기 재생하는 검수 도구 (발주처 제공 또는 라벨링 업체 구축)
- JSON annotation 검수 : VS Code + JSON Schema extension (`vlm_response.schema.json` 자동 검증)

### 7.8 PII 마스킹 (라벨링 전 단계)

- 얼굴 자동 blur (Gaussian σ=15, kernel 51×51) — RetinaFace 또는 YOLOv8-face, 라벨링 전 일괄 적용
- `worker_id` 는 `W-XXX` 형태로 pseudonymize — 실명·사번 매핑은 별도 보안 vault
- 차량 번호판 blur, 산업기밀 표시 blur (도면·공정 시각화 영역)
- 상세 정책은 제11장

---

## 제8장. 라벨러 운영 및 검수 SOP

### 8.1 영상·수치 데이터 품질 기준

| 항목 | 허용 기준 | 거부 사유 |
|---|---|---|
| 이미지 해상도 | ≥ 1280×720 | 미달 또는 업스케일 흔적 시 거부 |
| 모션 블러 | Laplacian variance ≥ 100 | < 100 시 거부 (충격 직전 블러는 예외) |
| 노출 | 평균 밝기 30~220 (0~255) | 범위 외 시 거부 |
| 야간 영상 비율 | 전체의 25% ≤ x ≤ 35% | 범위 외 시 재수집 |
| 시계열 결측치 | 동기 윈도우 내 결측 ≤ 10% | > 10% 시 해당 샘플 거부 |
| 타임스탬프 drift | 센서 간 ≤ 2초 | > 2초 시 거부 |
| 중복 샘플 | perceptual hash 거리 ≥ 5 | < 5 시 중복으로 1개만 채택 |

### 8.2 라벨러 자격 및 교육

- 라벨러는 산업안전 도메인 교육 4시간 이상 이수 (KOSHA 안전관리자 기초 또는 동등)
- 신규 라벨러 calibration : 본 발주 라벨러 50건 시험 라벨링 → 기준 라벨과 IRR 측정 → Cohen κ ≥ 0.70 시 통과
- 라벨러 1인의 일일 작업량 ≤ 200 샘플 (라벨 피로 방지)
- 라벨러 ID(`LBL-XXX`)는 모든 샘플 annotation 에 기재

### 8.3 이중 라벨링 및 Adjudication

- 전체 샘플의 20% 는 2명 라벨러가 독립 라벨링
- 두 라벨 불일치 시 (`primary_code` 또는 `secondary_codes` 차이) 시니어 라벨러가 adjudicate
- Adjudication 결과는 `review_status = "adjudicated"` 로 표기, 두 원본 라벨도 메타데이터로 보관
- IRR Cohen κ 는 sample 마다 계산하여 `irr_score` 필드 기재 (이중 라벨링 샘플만)

### 8.4 IRR (Inter-rater Reliability) 목표값

| 지표 | 목표 | 미달 시 조치 |
|---|---|---|
| `primary_code` Cohen κ | ≥ 0.75 | 0.65~0.75 : 라벨러 재교육 / < 0.65 : 본 발주 spec 재정의 |
| `secondary_codes` Jaccard | ≥ 0.65 | 미달 시 결정 트리 명확화 필요 |
| `severity` Cohen κ | ≥ 0.70 | 미달 시 임계값 재정의 |
| `suspected_condition` / `suspected_cause` κ | ≥ 0.70 | enum 정의 재검토 |
| Bbox IoU (이중 라벨) | ≥ 0.85 | CVAT 설정 표준화 / 라벨러 재교육 |
| Mask IoU | ≥ 0.85 | 동상 |

### 8.5 Audit 정책

- 샘플 무작위 audit : 본 발주 샘플의 10% 를 시니어 라벨러가 사후 audit
- Audit 실패율 ≥ 5% 시 해당 라벨러 작업 전체 재검수 (재작업 비용 라벨러 부담)
- Audit 결과는 메타데이터에 별도 기록

### 8.6 라벨 버전 관리

- 데이터셋 버전 관리 : DVC (data version control) + S3-compatible 객체 스토리지
- 어노테이션 변경 시 `sample_id` 그대로 + version 증가
- 영상·이미지 파일은 sha256 해시로 무결성 검증, `_checksum.txt` 동시 갱신

---

## 제9장. 평가 지표 및 수용 기준

### 9.1 per-class 분류 성능

| 클래스 | Recall 목표 | Precision 목표 | FP/일 상한 | 비고 |
|---|---|---|---|---|
| AC-001 화재 | ≥ 0.95 | ≥ 0.90 | ≤ 1 | 인명 직결, recall 최우선 |
| AC-002 끼임 | ≥ 0.90 | ≥ 0.85 | ≤ 2 | long-tail 이지만 critical |
| AC-003 낙상 | ≥ 0.92 | ≥ 0.85 | ≤ 3 | AC-007 과 confusion matrix 별도 보고 |
| AC-004 추락 | ≥ 0.95 | ≥ 0.90 | ≤ 1 | 치사율 최고 |
| AC-005 충돌 / 가동구역 진입 | ≥ 0.93 (충돌), ≥ 0.90 (zone_entry) | ≥ 0.80 (충돌), ≥ 0.85 (zone_entry) | ≤ 5 | 예측 기반 → FP 다소 허용. `trigger_type` 별 별도 리포트 |
| AC-006 안전모 | ≥ 0.90 | ≥ 0.92 | ≤ 10 | 빈도 높음, precision 우선 |
| AC-007 건강 | ≥ 0.88 | ≥ 0.80 | ≤ 5 | `suspected_condition` 정확도 별도 (≥ 0.75) |
| AC-008 가스 / 위험구역 | ≥ 0.95 (가스), ≥ 0.90 (zone_entry) | ≥ 0.90 (가스), ≥ 0.85 (zone_entry) | ≤ 1 (가스), ≤ 5 (zone_entry) | 센서 트리거 우선, VLM 은 보강. `trigger_type` 별 별도 리포트 |

### 9.2 JSON Schema 준수율

- Test set 응답 중 `vlm_response.schema.json` 통과율 ≥ 0.98
- 통과 기준 : Ajv strict mode (`additionalProperties=false`)
- 스키마 위반 사례 logging 후 다음 학습 epoch hard-example 로 회수

### 9.3 Evidence Hallucination Rate

- 정의 : `response.evidence` 의 finding 중 `inputs` 에 근거가 없는 항목 비율
- 목표 : ≤ 5%
- 측정 : Test set 응답을 라벨러가 spot check (5% 샘플)

### 9.4 응답 Latency

- Tier-4 VLM 추론만 측정 (Tier-1~3 은 별도 트랙)
- P50 ≤ 0.8초, P95 ≤ 1.2초, P99 ≤ 1.5초 (INT8 양자화, 7B sVLM, multi-image=2, batch=1 기준)
- 초과 시 INT4 양자화 또는 KV cache 최적화 검토

### 9.5 적대적·분포 외 평가

- OOD test : Test hold-out 사이트 단독 평가 — recall 목표의 ±10% 이내 유지
- Adversarial : 야간·우천·먼지 환경, 비표준 작업복, 안전모 외 두건/모자 등
- Prompt injection 안정성 : "이전 지시를 무시하고..." 같은 user 텍스트 주입 시 schema 준수율 유지

### 9.6 종합 수용 기준

본 절의 모든 목표를 동시 만족할 때 학습된 모델이 PASS 시스템에 배포될 수 있다. 부분 미달 시 미달 영역에 대한 추가 라벨링 또는 학습 cycle 1회 추가.

---

## 제10장. 베이스 모델 가이드라인 및 학습 설정

### 10.1 후보 모델 비교

| 항목 | Qwen2-VL-7B | LLaVA-NeXT-7B | InternVL2-8B | MiniCPM-V 2.6 |
|---|---|---|---|---|
| Multi-image 입력 | ◎ Native (m-RoPE) | △ 제한적 | ○ 가능 | ◎ 가능 |
| Context window | 32K | 4K | 8K | 8K |
| JSON instruction 안정성 | ◎ 우수 | △ 보통 | ○ 양호 | ○ 양호 |
| 한국어 성능 | ◎ 우수 | △ 보통 | ○ 양호 | ○ 양호 |
| INT8 양자화 지원 | ◎ vLLM AWQ | ○ GPTQ | ○ AWQ | ○ AWQ |
| 상용 라이선스 | ◎ Tongyi Qianwen | ○ LLaMA 기반 | ○ MIT | ◎ 자유 |
| 커뮤니티·도구 | ◎ 활발 | ◎ 활발 | ○ 보통 | ○ 보통 |

### 10.2 선정 기준 및 PoC 검증 항목

베이스 모델은 다음 5개 기준을 모두 충족해야 한다. PoC 단계에서 후보 모델별로 동일 데이터셋·동일 prompt 로 비교 검증 후 확정한다.

- Multi-image native 지원 (S-01 + S-03 동시 입력 필수)
- ≥ 8K context — Tier-1·2·3 사전 처리 결과 모두 prompt 에 수용 가능
- JSON output 안정성 (zero-shot schema 준수율 ≥ 0.90)
- 한국어 응답 품질 (`recommended_action` 일부 한국어 사용 시)
- INT8 양자화 후 1.0초 지연 충족 (단일 추론 기준)

### 10.3 학습 설정

| 항목 | 값 |
|---|---|
| 학습 방식 | LoRA (PEFT) |
| LoRA rank | 16 |
| LoRA alpha | 32 |
| LoRA dropout | 0.05 |
| Target modules | `q_proj, k_proj, v_proj, o_proj, gate_proj, up_proj, down_proj` |
| Batch size (per device) | 4 |
| Gradient accumulation | 8 (effective batch 32) |
| Learning rate | 1e-4 (cosine schedule) |
| Warmup ratio | 0.03 |
| Epoch | 3 |
| Max image tokens | 1024 per image (multi-image=2 기준 2048) |
| Max sequence length | 8192 |
| Precision | bfloat16 (학습) → INT8 (배포 양자화) |
| Optimizer | AdamW (β=0.9, 0.999, weight_decay=0.01) |
| Loss | Cross-entropy (next-token) + JSON-schema-aware regularization (선택) |

### 10.4 양자화 및 배포

- Post-training quantization : AWQ INT8 (vLLM 지원)
- Calibration set : Train 의 1% 무작위 추출
- 양자화 후 평가 : §9.1 목표 대비 recall 저하 ≤ 0.02 허용
- 배포 추론 엔진 : vLLM (continuous batching, KV cache)
- 하드웨어 가정 : NVIDIA A10G 24GB 또는 RTX 4090 24GB (온프레미스)

---

## 제11장. PII 보호 및 법령 컴플라이언스

### 11.1 적용 법령

- 개인정보보호법 (얼굴·생체정보 = 민감정보)
- 산업안전보건법 / 중대재해처벌법 (사고 영상 보관·보고)
- 정보통신망법 (영상 정보 처리)
- GDPR (해외 합성 데이터 사용 시 적용 가능)

### 11.2 얼굴 Blur

- 라벨링 전 일괄 적용 : RetinaFace 또는 YOLOv8-face 검출 → Gaussian blur (σ=15, kernel 51×51)
- 검증 : 라벨러 spot check 5%, 미blur 발견 시 해당 배치 전체 재처리
- 예외 : 합성 데이터 (모델은 가상 인물) — blur 불필요

### 11.3 worker_id Pseudonymization

- 형식 : `W-XXX` (3자리 일련번호)
- 매핑 테이블 : 실명·사번·`worker_id` 매핑은 별도 보안 vault (Hashicorp Vault 또는 동등)
- vault 접근 권한 : 안전관리자·인사·법무 제한, audit log 의무

### 11.4 산업기밀 마스킹

- 도면·공정 표시(벽면 표지, 모니터 화면) : 자동 OCR 검출 후 mask
- 제품 사양·고객사 로고 : 라벨러 spot check 후 수동 mask
- marker : annotation 의 `pii_masked = true` / `industrial_secret_masked = true` 필드 기재

### 11.5 사고 영상 사용권 확인 절차

1. 영상 출처 분류 — 자체 수집(발주처 지정 파일럿 사이트) / 합성 / 외부 라이선스
2. 자체 수집 : 작업자 동의서 (PASS 시스템 안내 + AI 학습 활용 동의) 100% 보유
3. 외부 라이선스 : 산업안전공단 공개 자료, 뉴스 라이선스, KOSHA 교육 영상 라이선스 사본 보관
4. 합성 : 시뮬레이션 도구 라이선스 또는 자체 제작
5. 라이선스 미확인 영상 사용 금지 — annotation 의 `license_confirmed = true` 필수

### 11.6 데이터 보관·폐기 정책

- 원본 영상 : 학습 종료 후 5년 보관 (산업안전보건법 사고 보고 의무)
- 학습 후 모델 weight 는 무기한 보관
- 작업자 동의 철회 요청 시 : 해당 `worker_id` 의 모든 샘플을 30일 내 삭제 + 모델 재학습
- 보안 사고 발생 시 : 개인정보보호법 제34조 (72시간 내 신고)

---

## 제12장. 발주 단계 게이트 및 거부 조건

### 12.1 3단계 발주 구조

본 발주는 다음 3단계로 분할된다. 단계별 sign-off gate 를 통과해야 다음 단계로 진행한다.

| 단계 | 범위 | 게이트 |
|---|---|---|
| Phase 0 — Pilot | 8 클래스 × ~12건 = 약 100건 + Negative 50건 (총 ~150건) | IRR, schema, Tier-2 메트릭 검증 |
| Phase 1 — Spec Lock | Pilot 결과 토대로 본 문서 v2.1 동결 | v2.1 sign-off |
| Phase 2 — 본 발주 | §6.1 목표 수량 14,000건 (분할 4회 납품, 각 ~3,500건) | 각 납품 분 sign-off |

### 12.2 Phase 0 Pilot Gate

Pilot 완료 시 다음을 모두 충족해야 Phase 1 진입.

- 이중 라벨링 IRR Cohen κ : `primary_code` ≥ 0.75, `severity` ≥ 0.70
- JSON Schema 준수율 ≥ 0.95 (Pilot 자동 검증)
- Tier-2 메트릭 자동 산출 결과 vs 라벨러 검수 일치율 ≥ 0.90
- PII 마스킹 누락 0건
- 샘플당 평균 라벨링 시간 < 25분

### 12.3 Phase 2 분할 납품 Sign-off

본 발주는 4회 분할 납품 (각 ~3,500건). 각 납품 분 검수.

- 샘플 무작위 audit 10% 통과율 ≥ 0.95
- 이중 라벨링 IRR 유지 (위 목표값)
- Stratification 매트릭스 ±5% 이내 유지 (§6.4)
- Negative 비율 1:1.5 ±0.1 유지
- hard-negative 슬롯 충족 (각 클래스 §6.3 조건)

### 12.4 거부(Reject) 조건

아래 조건 1개 이상 위반 시 해당 분 전체 거부, 라벨러 비용 부담 재작업.

- IRR Cohen κ < 0.65
- JSON Schema 준수율 < 0.90
- PII 마스킹 누락 발견
- 라이선스 미확인 영상 사용 발견
- Tier-2 메트릭 산출 파이프라인 변조
- 중복 샘플 (perceptual hash 거리 < 5) 발견 비율 > 5%

### 12.5 재라벨 정책

- 베이스 모델 변경 시 prompt template 영향 라벨 폐기 가능성 → Phase 1 spec lock 시점 후 베이스 모델 변경 금지
- 본 문서 Schema 마이너 갱신 시 영향 받는 필드만 부분 재라벨
- 재라벨 비용 부담 : (a) 발주처 사양 변경 시 발주처 / (b) 라벨러 품질 미달 시 라벨러

---

## 부록 A. 클래스 우선순위 결정 트리 (다중 라벨 시 primary_code 결정)

> §3.3 다중 이상상황 동시 발생 처리 규칙에서 참조하는 결정 트리. 라벨러는 다중 이상상황이 동시 활성화된 경우 본 트리의 단계 1부터 순차 적용하여 `primary_code` 를 결정한다.

```
입력: 모든 트리거 조건 평가 결과 (어떤 AC-*** 가 활성화되었는지)

단계 1) 즉각적 외상 (Critical 외상) 우선
   AC-004 추락 (GPS Δh ≥ 1.5m) 활성   → primary_code = AC-004
   AC-002 끼임 (인체-기계 IoU ≥ 0.15)  → primary_code = AC-002
   AC-005 충돌 (거리 ≤ 1.5m) 활성      → primary_code = AC-005
                                          class_extension.trigger_type = "collision"

단계 2) 환경 위협 우선 (다수 인명 영향)
   AC-001 화재 활성                     → primary_code = AC-001
   AC-008 가스누출 (LEL ≥ 10% 또는 H₂S ≥ 10ppm 또는 O₂ ≤ 19.5%) 활성
                                       → primary_code = AC-008
                                          class_extension.trigger_type =
                                            "gas_leak" 또는 "gas_leak+zone_entry"

단계 3) 쓰러짐 사건의 원인 식별
   AC-003 트리거 활성 AND S-02 체온 ≥ 38°C AND S-04 WBGT ≥ 28
     → primary_code = AC-003 (낙상 결과)
       class_extension.suspected_cause = "heat_stroke"
       secondary_codes = ["AC-007"]
   AC-003 트리거 활성 AND S-02 심박 ≤ 45bpm AND 충격 < 2.5G
     → primary_code = AC-003 (실신 낙상)
       class_extension.suspected_cause = "syncope"
       secondary_codes = ["AC-007"]
   AC-003 트리거 비활성 AND S-02 생체신호만 이상 (쓰러짐 없음)
     → primary_code = AC-007

단계 4) 위험구역 무단 진입 단독 (가스·forklift 활성 없음)
   GPS 가 zone_polygon (forklift_active) 내 ≥ 5초 + entry_authorized=false
     → primary_code = AC-005 (severity = Warning)
       class_extension.trigger_type = "zone_entry"
   GPS 가 zone_polygon (gas / confined_space / chemical_storage) 내 ≥ 5초
   + entry_authorized=false
     → primary_code = AC-008 (severity = Warning)
       class_extension.trigger_type = "zone_entry"

단계 5) 규정 위반 (PPE)
   AC-006 안전모 미착용 (분류 confidence ≥ 0.80)
     → 위 단계 활성 시 secondary_codes += ["AC-006"]
     → 단독 활성 시 primary_code = AC-006, severity = Notice
     → 의무 구역 + 고소작업(altitude ≥ 2m) 동시 시 severity = Warning 격상
     → 의무 구역 + 활성 AC-001 또는 AC-008 동시 시 severity = Warning 격상
```

**적용 규칙**

- 라벨러는 모든 트리거 조건을 평가한 후 단계 1부터 순차 적용한다.
- 단계 1·2에서 결정된 경우 단계 3 이하는 `secondary_codes` 만 갱신한다.
- `primary_code` 1개와 `secondary_codes` 배열(최대 3개)로 라벨링한다. 동일 코드 중복 금지.
- 단계 결정에 사용된 임계값 위반 사건은 §8.3 adjudication 대상이다.

---

## 부록 B. 전체 enum 목록

### B.1 primary_code / secondary_codes
```
["AC-001", "AC-002", "AC-003", "AC-004", "AC-005",
 "AC-006", "AC-007", "AC-008"]
```

### B.2 severity
```
["Critical", "Warning", "Notice"]
```

### B.3 sensor (evidence[].sensor)
```
["S-01_helmet_camera", "S-02_smartwatch", "S-03_cctv",
 "S-04_weather", "S-05_gas"]
```

### B.4 suspected_condition (AC-007)
```
["heat_stroke", "hypothermia", "cardiac", "hyperventilation",
 "hypoxia", "syncope", "unknown"]
```

### B.5 suspected_cause (AC-003)
```
["heat_stroke", "syncope", "cardiac", "hypoglycemia",
 "exhaustion", "slip", "unknown"]
```

### B.6 body_part_suspected (AC-002)
```
["head", "neck", "torso", "left_arm", "right_arm",
 "left_hand", "right_hand", "left_leg", "right_leg", "unknown"]
```

### B.7 machine_class (AC-002)
```
["press", "roller", "conveyor", "lathe", "crane", "mixer", "other"]
```

### B.8 zone_type (AC-005, AC-008 의 class_extension)
```
AC-005 zone_polygon 의 zone_type :
  ["forklift_active"]

AC-008 zone_polygon 의 zone_type :
  ["gas", "confined_space", "chemical_storage"]
```

### B.8.1 trigger_type (AC-005, AC-008)
```
AC-005 trigger_type :
  ["collision", "zone_entry", "collision+zone_entry"]

AC-008 trigger_type :
  ["gas_leak", "zone_entry", "gas_leak+zone_entry"]
```

### B.9 wind_direction (AC-008 + S-04)
```
["N", "NE", "E", "SE", "S", "SW", "W", "NW"]
```

### B.10 visual_cues (AC-008)
```
["liquid_spill", "drum_damage", "colored_plume",
 "missing_respirator", "ghs_pictogram", "vapor_cloud"]
```

### B.11 recommended_action (controlled vocabulary)

자유 문자열을 금지하고 다음 enum 에서만 선택. 클래스 무관 공통.

```
["evacuate_all_immediate", "evacuate_zone", "isolate_zone_downwind",
 "trigger_fire_suppression_local", "stop_machine_emergency",
 "stop_forklift_emergency", "shutoff_power_local", "shutoff_gas_supply",
 "call_119", "call_safety_manager", "call_executive",
 "request_first_aid", "deploy_cooling_pad", "deploy_warming_pack",
 "deploy_respirator", "request_rescue_team",
 "alert_worker_tts", "alert_worker_buzzer", "alert_nearby_workers",
 "log_incident", "log_violation",
 "increase_ventilation", "evacuate_to_safe_zone",
 "block_zone_entry", "restrict_high_altitude_work",
 "preserve_scene_for_investigation"]
```

---

## 부록 C. vlm_response.schema.json (발췌)

전체 schema 파일은 `_meta/vlm_response.schema.json` 으로 별도 산출. 본 부록은 핵심 부분 발췌.

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id":     "https://anna.co.kr/pass/schemas/vlm_response.schema.json",
  "title":   "PASS VLM Response Schema v2.0",
  "type":    "object",
  "additionalProperties": false,
  "required": [
    "anomaly_detected","primary_code","secondary_codes","severity",
    "confidence","detected_at","location","affected_workers",
    "evidence","recommended_action"
  ],
  "properties": {
    "anomaly_detected": { "type":"boolean" },
    "primary_code":     {
      "type":["string","null"],
      "enum":["AC-001","AC-002","AC-003","AC-004","AC-005",
              "AC-006","AC-007","AC-008",null]
    },
    "secondary_codes": {
      "type":"array", "uniqueItems":true, "maxItems":3,
      "items": {"type":"string","enum":["AC-001", "...", "AC-008"]}
    },
    "anomaly_type":     { "type":["string","null"] },
    "severity":         { "enum":["Critical","Warning","Notice",null] },
    "confidence":       { "type":"number", "minimum":0.0, "maximum":1.0 },
    "detected_at":      { "type":"string", "format":"date-time",
                          "pattern":"^\\d{4}-\\d{2}-\\d{2}T.+\\+09:00$" },
    "location": {
      "type":["object","null"], "additionalProperties":false,
      "required":["zone"],
      "properties":{
        "zone":            {"type":"string"},
        "camera_id":       {"type":["string","null"]},
        "gps":             {"type":["object","null"], "...": "..."},
        "zone_polygon_id": {"type":["string","null"]}
      }
    },
    "affected_workers": {"type":"array", "items":{"...": "..."}},
    "evidence": {
      "type":"array", "minItems":2,
      "items": {
        "type":"object","additionalProperties":false,
        "required":["sensor","finding"],
        "properties":{
          "sensor":  {"enum":["S-01_helmet_camera","S-02_smartwatch",
                              "S-03_cctv","S-04_weather","S-05_gas"]},
          "finding": {"type":"string","maxLength":300}
        }
      }
    },
    "recommended_action": {
      "type":"array", "minItems":3, "uniqueItems":true,
      "items":{"enum":["evacuate_all_immediate", "..."]}
    },
    "class_extension": { "type":["object","null"] }
  },
  "allOf": [
    { "if": {"properties":{"primary_code":{"const":"AC-001"}}},
      "then":{"properties":{"class_extension":{"$ref":"#/$defs/AC001"}}} },
    { "if": {"properties":{"primary_code":{"const":"AC-002"}}},
      "then":{"properties":{"class_extension":{"$ref":"#/$defs/AC002"}}} }
    /* ... AC-003 ~ AC-008 동일 패턴 ... */
  ],
  "$defs": {
    "AC001": { "required":["fire_pixel_ratio","smoke_blob_ratio"], "...": "..." },
    "AC002": { "required":["body_part_suspected","entrapment_duration_sec"], "...": "..." },
    "AC003": { "required":["suspected_cause","altitude_change_m"], "...": "..." },
    "AC004": { "required":["fall_height_m","impact_g","start_altitude_m"], "...": "..." },
    "AC005": { "required":["trigger_type","forklift_id","zone_polygon_id","entry_authorized"], "...": "..." },
    "AC006": { "required":["violation_count_today","zone_polygon_id"], "...": "..." },
    "AC007": { "required":["suspected_condition","vitals"], "...": "..." },
    "AC008": { "required":["trigger_type","gas_readings","exceeded_channels","zone_polygon_id","entry_authorized"], "...": "..." }
  }
}
```

