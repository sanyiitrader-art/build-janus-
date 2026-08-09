# Project Janus — Protocol Notes

This document tracks the two protocols Janus implements: Android's ADB
wire protocol (for pairing/connecting/shell) and Janus's own Target-server
transport protocol (for video/audio/input once a session is established).
It is updated as each piece is implemented — Phase 1 has implemented
neither yet, so this is currently a specification, not a description of
working code.

## 1. ADB protocol (Phases 4–5)

Janus implements the ADB wire protocol as a client directly in Kotlin —
it does not shell out to a bundled `adb` binary (see spec #48).

### 1.1 Wireless Debugging pairing

Android's pairing flow (Settings → Developer Options → Wireless Debugging
→ "Pair device with pairing code") opens a **separate** TLS-secured pairing
service, distinct from the main ADB-over-TCP port. The pairing handshake
uses SPAKE2 (a password-authenticated key exchange) seeded by the 6-digit
pairing code shown on the Target, over a TLS connection to the IP/port
shown alongside it. On success, the Target's ADB daemon adds the
Controller's public key to its list of authorized keys — this authorization
persists across the pairing session (it does not need to be repeated for a
subsequent plain `connect`).

Planned implementation: `AdbPairingClient.kt` + `SpakeHandshake.kt` +
`PairingTlsSocket.kt`.

### 1.2 Connection & authentication

A normal ADB-over-TCP connection (typically port 5555, or the ephemeral
Wireless Debugging connect port shown alongside the pairing port) begins
with a `CNXN` (connect) message exchange. If the Controller's RSA public
key is not yet in the Target's authorized-keys list, the Target's ADB
daemon responds with `AUTH` and shows the user an on-screen authorization
prompt — Janus polls/waits for this to resolve rather than forcing it (spec
#18). Once authorized, the connection proceeds to `OPEN`/`WRTE`/`OKAY`
framed streams for shell sessions and file push.

Planned implementation: `AdbConnection.kt` + `AdbProtocol.kt` +
`AdbMessage.kt` + `AdbStream.kt`, with `AdbKeystoreManager.kt` +
`AdbRsaKeyPair.kt` managing the Controller's persistent RSA identity
(stored in Android Keystore where practical, excluded from backups — see
`backup_rules.xml` / `data_extraction_rules.xml`).

### 1.3 Shell & file push

Standard ADB shell (`OPEN` with a `shell:<command>` service string) and
sync/push (for transferring the target-server payload) run over the same
authenticated connection. No new authorization step is needed once `CNXN`
has completed.

Planned implementation: `AdbShellSession.kt`, `TargetServerLauncher.kt`.

## 2. Target-server transport protocol (Phases 5–8)

Once the target-server payload is pushed and launched via `app_process`
(see `ARCHITECTURE.md`), it opens its own local TCP socket(s) for video,
audio, and input — separate from the ADB connection itself, forwarded to
the Controller over the same local network path.

This section will be filled in with the exact framing format (frame
headers, timestamps, packet types) once `ServerSocketTransport.kt` and
`FrameFramer.kt` are implemented in Phase 6.

### Design constraints already fixed by the spec (Phase 6+ implementation
must honor these):

- Video: H.264 baseline, MediaCodec hardware encode/decode, target 60 FPS,
  latest-frame-wins under backpressure (no unbounded queue growth).
- Input: sent over a path independent of the video stream so input latency
  is never gated on decoder throughput.
- Audio: timestamped for explicit sync against video, not played
  immediately on arrival.

## Status

| Component | Status |
|---|---|
| ADB pairing (SPAKE2/TLS) | Not yet implemented (Phase 4) |
| ADB connect/auth | Not yet implemented (Phase 4) |
| ADB shell/push | Not yet implemented (Phase 4) |
| Target-server launch | Stub only (Phase 1 — `Main.kt` prints a banner) |
| Video transport | Not yet implemented (Phase 6) |
| Audio transport | Not yet implemented (Phase 7) |
| Input transport | Not yet implemented (Phase 8) |