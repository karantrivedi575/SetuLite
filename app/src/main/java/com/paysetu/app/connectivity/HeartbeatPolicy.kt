package com.paysetu.app.connectivity

import java.util.concurrent.TimeUnit

class HeartbeatPolicy(
    private val deviceStateRepository: `DeviceState.kt`
) {
    companion object {
        // Strict 48-hour window as defined in Phase 1
        private val MAX_OFFLINE_DURATION_MS = TimeUnit.HOURS.toMillis(48)
    }

    /**
     * Enforces the heartbeat rule.
     * Throws an IllegalStateException if the device has been offline too long.
     */
    suspend fun enforce() {
        val lastSync = deviceStateRepository.getLastSyncTimestamp()
        val currentTime = System.currentTimeMillis()

        if (lastSync == 0L) {
            // First time use: System must sync to establish a baseline
            throw IllegalStateException("Initial sync required to enable payments")
        }

        if (currentTime - lastSync > MAX_OFFLINE_DURATION_MS) {
            // Requirement from Phase 1, Rule 6: Block outgoing payments
            throw IllegalStateException("Device offline for > 48h. Sync required to continue.")
        }
    }
}