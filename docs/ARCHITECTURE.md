# Project Janus — Architecture

## Overview

Project Janus is a native Android application that lets one Android phone
(the **Controller**) remotely view and control another Android phone (the
**Target**) over a local Wi-Fi network, using Android's Wireless Debugging /
ADB protocol as the underlying transport and trust mechanism. No PC, no
Termux, and no internet connection are required for the core remote-control
feature set.

## High-level flow

```
Controller Phone                          Target Phone
┌─────────────────────┐                   ┌─────────────────────┐
│ Embedded ADB client  │──── local Wi-Fi ─▶│ ADB daemon           │
│ (pairing, connect,   │                   │ (Wireless Debugging) │
│  shell)               │                  │                       │
│                       │──── push+launch ─▶│ Target server        │
│                       │                   │ (temporary process,  │
│ H.264 decoder         │◀─── video/audio ──│  screen+audio capture│
│ Coordinate mapper      │                   │  + H.264 encode)     │
│ Input encoder          │──── input cmds ──▶│ Input injector        │
└─────────────────────┘                   └─────────────────────┘
```

## Module boundaries

- **`:app`** — the Controller application. Compose UI, ADB client, video/
  audio decode, coordinate mapping consumer, input capture, persistence,
  settings, diagnostics.
- **`:coordmapping`** — pure Kotlin/JVM module with zero Android
  dependencies. Contains the touch-to-Target-pixel coordinate transform
  math (the most safety-critical logic in the app — see spec #29). Isolated
  here so it can be unit-tested on a plain JVM, including in CI, without an
  Android SDK or emulator.
- **`:targetserver`** — the payload pushed to the Target device via ADB and
  executed there via `app_process`. Never installed as a normal app. Handles
  screen/audio capture (MediaProjection), H.264 encoding (MediaCodec), and
  input injection on the Target side.

## Key architectural decisions

- **No native (C/C++/NDK) code.** ADB protocol, H.264 decode/encode, and
  persistence are all reachable from the Android platform SDK in pure
  Kotlin/Java. This keeps the APK smaller and avoids NDK/CMake toolchain
  complexity, per the "avoid unnecessary dependencies" requirement.
- **No DI framework.** A single manually-constructed `AppModule` object
  graph replaces Hilt/Dagger — small enough at this app's scope to stay
  explicit and easy to trace.
- **Coordinate mapping is never `touchX * scale`.** It subtracts the
  actual rendered content rectangle's offset (accounting for letterbox/
  pillarbox from aspect-ratio mismatches) before scaling by the ratio of
  Target resolution to *drawn content size* — not surface size. See
  `:coordmapping`'s `CoordinateTransform.kt` and its test suite.
- **Android's security model is never bypassed.** Wireless Debugging
  authorization, ADB RSA authorization, and MediaProjection consent all
  require the Target owner's manual approval — Janus detects successful
  authorization and continues automatically, but never automates the
  approval itself.
- **Device identity is not IP-based.** IPs change; the app tracks known
  devices by a stable identity (ADB device serial / RSA key fingerprint,
  finalized in the ADB subsystem phase) so a device isn't "lost" or
  duplicated when its IP changes on the LAN.

## Build & CI

The project builds and tests exclusively via GitHub Actions
(`.github/workflows/build.yml`) — no local Android Studio installation is
assumed. CI generates the Gradle wrapper itself on each run (avoiding the
need to commit a binary `gradle-wrapper.jar`), runs `:coordmapping`'s unit
tests, then builds and uploads a debug APK artifact.

See `PROTOCOL.md` for details on the ADB pairing/connection protocol and
the Target-server wire protocol.
```

