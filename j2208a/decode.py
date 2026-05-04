# -*- coding: utf-8 -*-
"""S1 Decode — J2208A 16-byte BLE packet parser.

Extracted from scripts/j2208a_sensor_reader.py:67-213 per CONTEXT.md D-04.
The original sensor_reader.py is left untouched in this plan; 04-03 will
convert it to import from this module.

Sources (jadx of vendor SDK):
  - jcwear_jadx/sources/com/jstyle/blesdk/Util/SingleDealData.java
  - jcwear_jadx/sources/com/jstyle/blesdk/Util/ResolveUtil.java
  - jcwear_jadx/sources/com/jstyle/blesdk/constant/DeviceConst.java
  - jcwear_jadx/sources/com/jstyles/jcwear/ble/BleService.java

Protocol summary:
  - All command/response packets are 16 bytes.
  - Last byte (idx 15) = CRC = sum(bArr[0..14]) & 0xFF (1-byte unsigned sum).
  - Response header byte (idx 0) carries the command code.
"""

from __future__ import annotations

from datetime import datetime
from typing import Optional


# -------------------------------------------------------------------
# UUID  (BleService.java)
# -------------------------------------------------------------------
SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb"
WRITE_UUID   = "0000fff6-0000-1000-8000-00805f9b34fb"
NOTIFY_UUID  = "0000fff7-0000-1000-8000-00805f9b34fb"


# -------------------------------------------------------------------
# Command codes (DeviceConst.java)
# -------------------------------------------------------------------
CMD_SET_TIME            = 0x01
CMD_SET_USERINFO        = 0x02
CMD_GET_DEVICE_INFO     = 0x04
CMD_REALTIME_STEP       = 0x09  # realtime steps/HR/distance/calories — canonical stream (D-14 keep)
CMD_GET_BATTERY         = 0x13
CMD_RESET               = 0x12
CMD_GET_MAC             = 0x22
CMD_GET_VERSION         = 0x27
CMD_MOTOR_VIBRATION     = 0x36
CMD_MCU_RESET           = 0x2E
CMD_GET_TIME            = 0x41
CMD_GET_USERINFO        = 0x42
CMD_GET_GOAL            = 0x4B
CMD_HRV_BLOOD_PRESSURE  = 0x28  # 1=HRV, 2=Heart, 3=SpO2 — dual-stream duplicate (D-14 silent drop)
CMD_AUTO_HEART_GET      = 0x2B


# ===================================================================
# Packet builders / CRC
# ===================================================================
def _crc(packet: bytearray) -> bytearray:
    """SDK ResolveUtil.crcValue() — 1-byte unsigned sum of bytes [0..len-2]."""
    s = 0
    for i in range(len(packet) - 1):
        s = (s + packet[i]) & 0xFF
    packet[-1] = s
    return packet


def build_cmd(cmd: int, *payload: int) -> bytes:
    """16-byte command packet with CRC."""
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
    """Set device clock (BCD-encoded)."""
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
    """type: 1=HRV, 2=HeartRate, 3=SpO2 / on=True starts measurement."""
    return build_cmd(CMD_HRV_BLOOD_PRESSURE, measure_type, 1 if on else 0)


# ===================================================================
# Response parser (SingleDealData.dealWithSendCmdState + ResolveUtil)
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
    """Decode a received 16-byte packet into a dict of meaningful fields.

    Returns at minimum {"cmd": "0x09", "raw": "..."}; HR/temp/steps are added
    only when the packet carries them. HR=0 / temp absent are NORMAL signals
    (PPG warmup / short 0x09 response) — NOT errors. S2 Validate handles them.
    """
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

    elif head == CMD_REALTIME_STEP:                   # 0x09 — canonical stream (D-14 keep)
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

    elif head == CMD_HRV_BLOOD_PRESSURE:              # 0x28 — dual-stream (D-14 silent drop at writer)
        # Real packet sample: 28 02 4e 00 00 00 00 00 72 01 ...
        #                     hdr type HR    ...    temp(LE,*0.1°C)
        m_type = _u8(data[1])
        type_name = {1: "HRV", 2: "HeartRate", 3: "SpO2"}.get(m_type, f"type{m_type}")
        out["measure_type"] = type_name
        if m_type == 2:                               # HeartRate single-shot
            out["heart_rate"] = _u8(data[2])
        elif m_type == 1:                             # HRV
            out["hrv"] = _u8(data[2])
        elif m_type == 3:                             # SpO2
            out["spo2"] = _u8(data[2])
        if len(data) >= 10:
            temp = (_u8(data[8]) + _u8(data[9]) * 256) * 0.1
            if 20 < temp < 50:                        # only emit plausible wrist-temperature
                out["temperature_c"] = round(temp, 1)

    return out
