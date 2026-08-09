package com.janus.targetserver

/**
 * Entry point for the Target-side server payload (spec #19, #48).
 *
 * This is NOT launched as a normal Android app. After Janus pushes this
 * module's compiled classes to the Target via ADB (`adb push`), it is
 * started with `app_process`, which invokes this main() function directly
 * under the ADB shell UID — the same trust level Wireless Debugging
 * authorization already grants, with no separate install/permission-grant
 * step.
 *
 * Phase 1 scope: a stub that proves the launch mechanism works end-to-end
 * (prints a version banner, exits cleanly) before any real capture/encode/
 * input/transport logic is wired in. TargetServerLauncher.kt (Phase 5, in
 * the :app module) is what actually performs the push + app_process launch
 * and expects this stub's output as a smoke-test signal that the pushed
 * payload started successfully.
 *
 * Real responsibilities added in later phases:
 *   Phase 5 — parse launch arguments (port, requested resolution), start
 *             the MediaProjection screen-capture consent flow
 *   Phase 6 — H264Encoder + ServerSocketTransport for video
 *   Phase 7 — AudioCaptureSession for synchronized audio
 *   Phase 8 — InputInjector to receive and apply touch/key/text commands
 *             from the Controller
 */
fun main(args: Array<String>) {
    println("Project Janus target server v0.1.0 (Phase 1 stub)")
    println("Launched with args: ${args.joinToString(separator = " ")}")
    println("No capture/transport pipeline implemented yet — exiting.")
}