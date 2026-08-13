package com.janus.app.viewmodel

import androidx.lifecycle.ViewModel

/**
 * ViewModel for the main remote-control screen (spec #6).
 *
 * RESTORED to an empty stub — a previous version of this file was repurposed
 * as ad-hoc scratch/test code (hardcoded IP/port/pairing-code, calls to
 * AdbConnection/AdbSyncService APIs that didn't match their real signatures)
 * and is not wired into the app anywhere. Real state (active stream,
 * connection status, coordinate mapping) lands here starting Phase 6, once
 * there's an actual video pipeline for this screen to reflect.
 *
 * Phase 4's pairing flow belongs in PairingViewModel, not here — this
 * screen's job is displaying an already-connected session, not driving the
 * pairing handshake itself.
 */
class RemoteScreenViewModel : ViewModel()