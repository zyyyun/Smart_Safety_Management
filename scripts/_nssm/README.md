# scripts/_nssm/

`ai_agent_service.ps1` looks here for `nssm.exe` when neither `winget` install
nor a PATH-resolved `nssm` is available.

## Why this directory exists

NSSM (Non-Sucking Service Manager) is the supervisor that runs
`ai_agent/main.py` as a Windows service. Plan reference: A.3.

`ai_agent_service.ps1 -Action Install` resolves NSSM in this order:
1. `Get-Command nssm` (PATH)
2. `scripts/_nssm/nssm.exe` (this folder)
3. `winget install NSSM.NSSM` (Windows 11 — auto attempt)

If the install fails on a machine without winget and you want a fully
offline install, drop `nssm.exe` (the 64-bit `win64\nssm.exe` from
<https://nssm.cc/download>) into this folder.

## Why we don't commit nssm.exe

- NSSM 2.24 is public domain, but binaries don't belong in the
  application repo — `winget`/`choco` are the right distribution channels.
- Avoids platform-specific binaries in the source tree.
- Forces a deliberate install step on each new PC (visible in logs).

`*.exe`, `*.zip`, `*.dll` here are gitignored.
