# -*- coding: utf-8 -*-
"""
J-Style 2208A Smart Health Bracelet — PC(Windows/Mac/Linux) BLE 센서 데이터 수집 스크립트

[근거]
  - JCWear2/J-Style 2208A Smart Health Bracelet SDK_v2_20231225/2208a/2208aSdk/
      2208A Android SDK Documentation.doc
  - jcwear_jadx/sources/com/jstyle/blesdk/Util/SingleDealData.java
  - jcwear_jadx/sources/com/jstyle/blesdk/Util/ResolveUtil.java
  - jcwear_jadx/sources/com/jstyle/blesdk/constant/DeviceConst.java
  - jcwear_jadx/sources/com/jstyles/jcwear/ble/BleService.java
       (UUID: SERVICE 0xFFF0, WRITE 0xFFF6, NOTIFY 0xFFF7)

[프로토콜 요약]
  - 모든 명령 패킷 길이 = 16 byte
  - 마지막 byte(idx 15) = CRC = (byte) sum(bArr[0..14]) & 0xFF   (단순 1byte 합)
  - 응답도 동일한 16byte 형식이며 첫 byte(헤더)에 명령 코드가 들어옴

[필요 라이브러리]
  pip install bleak

[사용법]
  python j2208a_sensor_reader.py                       # 자동 스캔 후 J-Style 디바이스에 연결
  python j2208a_sensor_reader.py <MAC주소>             # 특정 MAC 주소에 직접 연결
  python j2208a_sensor_reader.py --scan                # 스캔만 자세히 수행하고 종료
  python j2208a_sensor_reader.py --scan --time 20      # 20초 스캔
"""

from __future__ import annotations

import argparse
import asyncio
import sys
import time as _time
from datetime import datetime
from typing import Optional

from bleak import BleakClient, BleakScanner

# ──────────────────────────────────────────
# j2208a 파이프라인 통합 (per CONTEXT.md D-04 인라인 통합)
# ──────────────────────────────────────────
# 2026-05-19 fix: 실행 패턴 무관하게 repo root 를 sys.path 에 자동 주입.
# `python scripts/j2208a_sensor_reader.py` 실행 시 sys.path[0] 은 scripts/
# 디렉터리. cwd 와 무관. → j2208a/ (repo root 의 sibling) 를 못 찾음.
# 이 스크립트 파일 위치 (scripts/) 의 부모 = repo root 를 강제 prepend.
import os as _os  # noqa: E402
_REPO_ROOT = _os.path.abspath(_os.path.join(_os.path.dirname(__file__), ".."))
if _REPO_ROOT not in sys.path:
    sys.path.insert(0, _REPO_ROOT)
# 이전 권장 패턴 (PYTHONPATH 또는 `python -m scripts.j2208a_sensor_reader`) 도 동작.
# import 실패 시 BLE 데몬은 계속 동작하되 DB write/알림이 비활성화 (개발 fallback).
try:
    from j2208a.runtime import process_sample  # noqa: E402
    _RUNTIME_OK = True
    print(f"[+] j2208a.runtime import 성공 (repo_root={_REPO_ROOT}) — DB write/알림 활성")
except ImportError as _imp_err:
    print(f"[!] j2208a.runtime import 실패 — DB write/알림 비활성화: {_imp_err}")
    print(f"    sys.path[0]={sys.path[0]}, repo_root 주입 시도={_REPO_ROOT}")
    process_sample = None  # type: ignore[assignment]
    _RUNTIME_OK = False

# Supabase env var 진단 — service_role_key 부재 시 insert/heartbeat 모두 silent fail.
# 사용자에게 명확한 안내 출력.
if _RUNTIME_OK:
    _supa_url = _os.environ.get("SUPABASE_URL")
    _supa_key = _os.environ.get("SUPABASE_SERVICE_ROLE_KEY")
    if not _supa_url or not _supa_key:
        print("[!] Supabase env var 누락 — DB 적재가 silent fail 합니다:")
        if not _supa_url:
            print("    - SUPABASE_URL 미설정 (예: https://xbjqxnvemcqubjfflain.supabase.co)")
        if not _supa_key:
            print("    - SUPABASE_SERVICE_ROLE_KEY 미설정 (Dashboard → Settings → API → service_role)")
        print("    PowerShell 예: ")
        print('      $env:SUPABASE_URL="https://xbjqxnvemcqubjfflain.supabase.co"')
        print('      $env:SUPABASE_SERVICE_ROLE_KEY="<service_role JWT>"')
        print('      $env:TEST_DEVICE_ID="1"  # Plan 09-01 seed 정합 (testuser1 워치)')
        print('      $env:TEST_USER_ID="testuser1"')
    else:
        _device_id = _os.environ.get("TEST_DEVICE_ID", "1")
        _user_id = _os.environ.get("TEST_USER_ID", "testuser1")
        print(f"[+] Supabase 적재 활성 (device_id={_device_id}, user_id={_user_id})")

# -------------------------------------------------------------------
# UUID  (출처: BleService.java)
# -------------------------------------------------------------------
SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb"
WRITE_UUID   = "0000fff6-0000-1000-8000-00805f9b34fb"
NOTIFY_UUID  = "0000fff7-0000-1000-8000-00805f9b34fb"

# -------------------------------------------------------------------
# 명령 코드 (출처: DeviceConst.java)
# -------------------------------------------------------------------
CMD_SET_TIME            = 0x01
CMD_SET_USERINFO        = 0x02
CMD_GET_DEVICE_INFO     = 0x04
CMD_REALTIME_STEP       = 0x09  # 실시간 걸음/심박/거리/칼로리
CMD_GET_BATTERY         = 0x13
CMD_RESET               = 0x12
CMD_GET_MAC             = 0x22
CMD_GET_VERSION         = 0x27
CMD_MOTOR_VIBRATION     = 0x36
CMD_MCU_RESET           = 0x2E
CMD_GET_TIME            = 0x41
CMD_GET_USERINFO        = 0x42
CMD_GET_GOAL            = 0x4B
CMD_HRV_BLOOD_PRESSURE  = 0x28  # 1=hrv, 2=heart, 3=spo2
CMD_AUTO_HEART_GET      = 0x2B


# ===================================================================
# 패킷 빌더 / CRC
# ===================================================================
def _crc(packet: bytearray) -> bytearray:
    """SDK ResolveUtil.crcValue() 와 동일한 1byte sum 체크섬."""
    s = 0
    for i in range(len(packet) - 1):
        s = (s + packet[i]) & 0xFF
    packet[-1] = s
    return packet


def build_cmd(cmd: int, *payload: int) -> bytes:
    """16byte 명령 패킷 생성."""
    pkt = bytearray(16)
    pkt[0] = cmd & 0xFF
    for i, b in enumerate(payload, start=1):
        if i >= 15:
            break
        pkt[i] = b & 0xFF
    return bytes(_crc(pkt))


def cmd_realtime_step(enable: bool, temp_enable: bool = False) -> bytes:
    return build_cmd(CMD_REALTIME_STEP, 1 if enable else 0, 1 if temp_enable else 0)


def cmd_set_time(now: Optional[datetime] = None) -> bytes:
    """디바이스 시계 설정 (BCD 형식)."""
    now = now or datetime.now()

    def bcd(v: int) -> int:
        return ((v // 10) << 4) | (v % 10)

    return build_cmd(
        CMD_SET_TIME,
        bcd(now.year - 2000),
        bcd(now.month),
        bcd(now.day),
        bcd(now.hour),
        bcd(now.minute),
        bcd(now.second),
    )


def cmd_get_battery() -> bytes:        return build_cmd(CMD_GET_BATTERY)
def cmd_get_time()    -> bytes:        return build_cmd(CMD_GET_TIME)
def cmd_get_version() -> bytes:        return build_cmd(CMD_GET_VERSION)
def cmd_get_mac()     -> bytes:        return build_cmd(CMD_GET_MAC)
def cmd_mcu_reset()   -> bytes:        return build_cmd(CMD_MCU_RESET)
def cmd_vibrate(n: int = 1) -> bytes:  return build_cmd(CMD_MOTOR_VIBRATION, n & 0xFF)


def cmd_health_measure(measure_type: int, on: bool) -> bytes:
    """type: 1=HRV, 2=HeartRate, 3=SpO2 / on=True 측정 시작."""
    return build_cmd(CMD_HRV_BLOOD_PRESSURE, measure_type, 1 if on else 0)


# ===================================================================
# 응답 파서 (출처: SingleDealData.dealWithSendCmdState + ResolveUtil)
# ===================================================================
def _u8(b: int) -> int:
    return b & 0xFF


def _bcd2int(b: int) -> int:
    return ((b & 0xF0) >> 4) * 10 + (b & 0x0F)


def _le_int(data: bytes, start: int, length: int) -> int:
    """little-endian unsigned int."""
    v = 0
    for i in range(length):
        v += _u8(data[start + i]) * (256 ** i)
    return v


def parse_packet(data: bytes) -> dict:
    """수신 패킷을 의미 있는 dict 로 변환."""
    if not data:
        return {"raw": ""}
    head = _u8(data[0])
    out: dict = {
        "cmd": f"0x{head:02X}",
        "raw": data.hex(" "),
    }

    if head == CMD_GET_BATTERY:                       # 0x13
        out["battery_percent"] = _u8(data[1])

    elif head == CMD_GET_MAC:                         # 0x22
        out["mac"] = ":".join(f"{_u8(b):02X}" for b in data[1:7])

    elif head == CMD_GET_TIME:                        # 0x41
        try:
            t = (
                f"20{_bcd2int(data[1]):02d}-{_bcd2int(data[2]):02d}-{_bcd2int(data[3]):02d} "
                f"{_bcd2int(data[4]):02d}:{_bcd2int(data[5]):02d}:{_bcd2int(data[6]):02d}"
            )
            out["device_time"] = t
        except Exception:
            pass

    elif head == CMD_GET_VERSION:                     # 0x27
        out["version"] = ".".join(f"{_u8(b):X}" for b in data[1:5])

    elif head == CMD_REALTIME_STEP:                   # 0x09
        # 측정 ON 응답이고 길이가 24+ 인 경우 실시간 데이터 페이로드
        if len(data) >= 22:
            steps      = _le_int(data, 1, 4)
            calories   = _le_int(data, 5, 4) / 100.0
            distance   = _le_int(data, 9, 4) / 100.0      # km
            time_sec   = _le_int(data, 13, 4)
            exercise_m = _le_int(data, 17, 4)
            heart_rate = _u8(data[21])
            out.update({
                "steps":          steps,
                "calories_kcal":  round(calories, 2),
                "distance_km":    round(distance, 2),
                "active_minutes": time_sec // 60,
                "exercise_min":   exercise_m,
                "heart_rate":     heart_rate,
            })
            if len(data) > 22:
                temp = (_u8(data[22]) + _u8(data[23]) * 256) * 0.1
                out["temperature_c"] = round(temp, 1)

    elif head == CMD_GET_GOAL:                        # 0x4B
        out["step_goal"] = _le_int(data, 1, 2)

    elif head == CMD_HRV_BLOOD_PRESSURE:              # 0x28 (단발/자동 측정 응답)
        # 실측 패킷 예: 28 02 4e 00 00 00 00 00 72 01 ...
        #              헤더 type HR    ...    온도(LE,*0.1℃)
        m_type = _u8(data[1])
        type_name = {1: "HRV", 2: "HeartRate", 3: "SpO2"}.get(m_type, f"type{m_type}")
        out["measure_type"] = type_name
        if m_type == 2:                               # 심박 단발
            out["heart_rate"] = _u8(data[2])
        elif m_type == 1:                             # HRV
            out["hrv"] = _u8(data[2])
        elif m_type == 3:                             # SpO2
            out["spo2"] = _u8(data[2])
        if len(data) >= 10:
            temp = (_u8(data[8]) + _u8(data[9]) * 256) * 0.1
            if 20 < temp < 50:                        # 사람 손목 체온 범위에서만 출력
                out["temperature_c"] = round(temp, 1)

    return out


# ===================================================================
# 메인 BLE 클라이언트
# ===================================================================
class J2208AClient:
    def __init__(self, address: Optional[str] = None) -> None:
        self.address = address
        self.client: Optional[BleakClient] = None

    # ---------------- 디바이스 검색 ----------------
    async def discover(self, scan_seconds: float = 12.0, verbose: bool = True) -> Optional[str]:
        """
        active scan 으로 광고 데이터를 모아 J-Style 2208A 를 찾습니다.
        식별 우선순위: (1) Service UUID 0xFFF0  (2) 이름 매칭
        """
        print(f"[*] BLE active scan 시작 ({scan_seconds:.0f}초) ...")
        # address -> (BLEDevice, AdvertisementData)
        seen: dict[str, tuple] = {}

        def _cb(device, adv):
            seen[device.address] = (device, adv)

        # active scan + service uuid FFF0 광고만 잡도록 필터하면 가장 빠르지만,
        # 펌웨어에 따라 서비스 UUID를 광고에 안 싣는 경우도 있어 일단 전부 받습니다.
        scanner = BleakScanner(detection_callback=_cb, scanning_mode="active")
        await scanner.start()
        try:
            await asyncio.sleep(scan_seconds)
        finally:
            await scanner.stop()

        # 정렬: RSSI(가까운 순) → 의미있는 이름 우선
        rows = []
        for addr, (device, adv) in seen.items():
            name = (adv.local_name or device.name or "").strip()
            rssi = getattr(adv, "rssi", None)
            svc_uuids = list(getattr(adv, "service_uuids", []) or [])
            mfg = dict(getattr(adv, "manufacturer_data", {}) or {})
            rows.append((addr, name, rssi, svc_uuids, mfg))

        rows.sort(key=lambda r: (-(r[2] if r[2] is not None else -999), -bool(r[1])))

        target = None
        for addr, name, rssi, svc_uuids, mfg in rows:
            has_fff0 = any(u.lower().startswith("0000fff0") for u in svc_uuids)
            tag = ""
            if has_fff0:
                tag = "  <-- service 0xFFF0 (J-Style)"
            elif name and any(t in name.upper() for t in ("J-STYLE", "J2208", "2208", "JCWEAR")):
                tag = "  <-- name match"
            if verbose:
                print(
                    f"    - {addr}  RSSI={rssi}  name={name!r}"
                    f"  services={svc_uuids if svc_uuids else '-'}"
                    f"{tag}"
                )
            if target is None and (has_fff0 or (name and any(
                t in name.upper() for t in ("J-STYLE", "J2208", "2208", "JCWEAR")
            ))):
                target = addr

        if target:
            print(f"[+] J-Style 디바이스 발견: {target}")
            return target

        print("[!] J-Style 2208A 디바이스를 찾지 못했습니다.")
        print("    - 휴대폰 JCWear 앱이 켜져 있으면 종료해 주세요 (BLE 마스터 충돌)")
        print("    - 그래도 안 보이면 한 번 알고 있는 MAC 주소로 직접 시도해 보세요")
        return None

    # ---------------- 연결 ----------------
    async def connect(self, on_disconnect=None) -> bool:
        if not self.address:
            self.address = await self.discover()
            if not self.address:
                print("[!] J-Style 2208A 디바이스를 찾지 못했습니다.")
                return False

        print(f"[*] {self.address} 연결 시도 ...")
        self.client = BleakClient(
            self.address, timeout=20.0, disconnected_callback=on_disconnect
        )
        try:
            await self.client.connect()
        except Exception as e:
            print(f"[!] 연결 실패: {e}")
            return False
        if not self.client.is_connected:
            print("[!] 연결 실패")
            return False
        print(f"[+] 연결 성공 (services 검색 중) ...")
        # bleak >=0.20 은 자동으로 서비스 탐색을 수행
        await self.client.start_notify(NOTIFY_UUID, self._on_notify)
        print(f"[+] notify({NOTIFY_UUID}) 구독 시작")
        return True

    async def disconnect(self) -> None:
        if self.client and self.client.is_connected:
            try:
                await self.client.stop_notify(NOTIFY_UUID)
            except Exception:
                pass
            await self.client.disconnect()
            print("[*] 연결 해제 완료")

    # ---------------- 송수신 ----------------
    async def send(self, packet: bytes, label: str = "") -> None:
        assert self.client and self.client.is_connected
        print(f"[>] 송신 {label or ''}: {packet.hex(' ')}")
        await self.client.write_gatt_char(WRITE_UUID, packet, response=True)

    def _on_notify(self, _sender, data: bytearray) -> None:
        raw = bytes(data)
        info = parse_packet(raw)
        # 한 줄로 보기 쉽게 (기존 진단 출력 보존)
        kv = " ".join(f"{k}={v}" for k, v in info.items() if k not in ("raw",))
        print(f"[<] {kv}    (raw: {info['raw']})")

        # j2208a 파이프라인 진입 (per CONTEXT.md D-04 인라인 통합)
        # raw_events INSERT + S2/S3/S4 + safety_alerts → notifications/watch-alert.
        # 데몬 안전: process_sample 실패해도 BLE notify 루프는 계속 동작.
        if _RUNTIME_OK and process_sample is not None:
            try:
                cmd = raw[0] if raw else 0
                process_sample(info, info.get("raw", ""), cmd, now_ts=_time.time())
            except Exception as e:
                print(f"[!] j2208a.runtime.process_sample 실패: {e}")


# ===================================================================
# 데모 시나리오 (자동 재연결 모드)
# ===================================================================
async def _heartbeat_loop(disconnected: asyncio.Event,
                          interval_sec: float = 10.0) -> None:
    """주기적 j2208a.runtime.tick 호출 — raw 가 없는 동안에도 시간 기반 전이
    (COMMS_LOST 120s 부재, REMOVED 5분 만료) 를 잡아낸다.

    process_sample 단독으로는 COMMS_LOST 발생 불가 (RT-7 contract — derive 가
    호출되는 시점에 last_raw_ts 와 now_ts 가 거의 동일). 이 heartbeat 가
    유일한 시간 기반 전이 트리거 경로.

    disconnected 이벤트가 set 되면 즉시 종료. interval_sec=10 이면 최악의
    경우 COMMS_LOST 검출 지연 = 120s + 10s = 130s.
    """
    if not _RUNTIME_OK:
        return
    try:
        from j2208a.runtime import tick as _tick
    except ImportError:
        return
    while not disconnected.is_set():
        try:
            # asyncio.wait_for 로 disconnected 와 sleep 을 결합 — 끊김 시 즉시 종료.
            try:
                await asyncio.wait_for(disconnected.wait(), timeout=interval_sec)
            except asyncio.TimeoutError:
                pass
            if disconnected.is_set():
                break
            _tick()
        except Exception as e:
            print(f"[!] j2208a.runtime.tick 실패: {e}")


async def _run_session(cli: J2208AClient, disconnected: asyncio.Event) -> None:
    """단일 연결 세션. disconnected.set() 이 되거나 write 가 실패하면 빠져나옵니다.

    heartbeat task 를 spawn 해 COMMS_LOST 검출을 가능하게 하고, finally 에서
    cancel 해 leak 방지 (advisor 가이드).
    """
    # heartbeat 우선 spawn — write 실패해도 cancel 보장 (try/finally).
    hb_task = asyncio.create_task(_heartbeat_loop(disconnected, 10.0))
    try:
        # 1) 시계 동기화 → 이게 안 되면 실시간 데이터의 시각이 어긋납니다.
        await cli.send(cmd_set_time(), "시계 동기화 (0x01)")
        await asyncio.sleep(0.6)

        # 2) 기본 정보 확인
        await cli.send(cmd_get_battery(), "배터리 (0x13)")
        await asyncio.sleep(0.4)
        await cli.send(cmd_get_version(), "펌웨어 버전 (0x27)")
        await asyncio.sleep(0.4)
        await cli.send(cmd_get_mac(),     "MAC 주소 (0x22)")
        await asyncio.sleep(0.4)
        await cli.send(cmd_get_time(),    "디바이스 시계 (0x41)")
        await asyncio.sleep(0.4)

        # 3) 실시간 측정 ON + 심박/체온 측정 ON
        #    - 0x09(실시간 걸음/거리/칼로리) 단독으론 변화가 없으면 거의 안 쏘기 때문에
        #      0x28(심박 측정) 도 같이 켜야 매초 스트림이 흘러나옵니다.
        await cli.send(cmd_realtime_step(True), "실시간 측정 ON (0x09)")
        await asyncio.sleep(0.5)
        await cli.send(cmd_health_measure(2, True), "심박/체온 측정 ON (0x28)")

        # 일부 펌웨어는 0x28 측정을 ~30s 후 자동 종료하므로 20s 마다 재시작.
        # disconnected 이벤트가 set 되면 즉시 종료.
        while not disconnected.is_set():
            try:
                await asyncio.wait_for(disconnected.wait(), timeout=20)
            except asyncio.TimeoutError:
                try:
                    await cli.send(cmd_health_measure(2, True), "심박/체온 재시작")
                except Exception:
                    # write 실패 = 사실상 끊김. 외부에서 재연결 처리.
                    break
    finally:
        hb_task.cancel()
        try:
            await hb_task
        except (asyncio.CancelledError, Exception):
            pass


async def demo(address: Optional[str]) -> None:
    # MAC 미지정 시 1회 스캔으로 알아낸 후, 이후 재연결은 같은 MAC 으로 직접 시도
    if not address:
        scout = J2208AClient()
        address = await scout.discover()
        if not address:
            print("[!] J-Style 2208A 디바이스를 찾지 못했습니다.")
            return

    print(f"[*] 자동 재연결 모드 (target={address}) — Ctrl+C 로 종료")
    backoff = 3
    try:
        while True:
            cli = J2208AClient(address)
            loop = asyncio.get_running_loop()
            disconnected = asyncio.Event()

            def _on_disconnect(_client) -> None:
                if not disconnected.is_set():
                    print("[!] BLE 연결 끊김 — 재연결 대기")
                loop.call_soon_threadsafe(disconnected.set)

            ok = await cli.connect(on_disconnect=_on_disconnect)
            if not ok:
                wait = backoff
                print(f"[*] {wait}s 후 재연결 시도")
                await asyncio.sleep(wait)
                backoff = min(backoff * 2, 30)
                continue
            backoff = 3  # 연결 성공 시 백오프 리셋

            try:
                await _run_session(cli, disconnected)
            except Exception as e:
                print(f"[!] 세션 오류: {e}")
            finally:
                try:
                    if cli.client and cli.client.is_connected:
                        await cli.send(cmd_health_measure(2, False), "심박/체온 측정 OFF")
                        await cli.send(cmd_realtime_step(False), "실시간 측정 OFF")
                except Exception:
                    pass
                try:
                    await cli.disconnect()
                except Exception:
                    pass

            print("[*] 3s 후 재연결 시도")
            await asyncio.sleep(3)
    except KeyboardInterrupt:
        print("\n[*] 사용자 중단")


async def scan_only(scan_seconds: float) -> None:
    cli = J2208AClient()
    await cli.discover(scan_seconds=scan_seconds, verbose=True)


def main() -> None:
    parser = argparse.ArgumentParser(description="J-Style 2208A BLE 센서 데이터 수집기")
    parser.add_argument("address", nargs="?", default=None, help="디바이스 MAC 주소 (생략 시 자동 스캔)")
    parser.add_argument("--scan", action="store_true", help="스캔만 수행하고 종료")
    parser.add_argument("--time", type=float, default=12.0, help="스캔 시간(초), 기본 12")
    args = parser.parse_args()

    try:
        if args.scan:
            asyncio.run(scan_only(args.time))
        else:
            asyncio.run(demo(args.address))
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
