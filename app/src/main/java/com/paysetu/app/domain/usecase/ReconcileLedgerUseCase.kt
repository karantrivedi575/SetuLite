package com.paysetu.app.domain.usecase

import com.paysetu.app.data.device.DeviceStateRepository
import com.paysetu.app.data.ledger.LedgerRepository
import com.paysetu.app.data.ledger.entity.TransactionStatus
import com.paysetu.app.domain.model.sync.SyncResponse
import com.paysetu.app.domain.policy.HeartbeatPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReconcileLedgerUseCase(
    private val ledgerRepository: LedgerRepository,
    private val deviceStateRepository: DeviceStateRepository,
    private val heartbeatPolicy: HeartbeatPolicy
) {

    /**
     * Enforces the Backend's authoritative state onto the local offline ledger.
     */
    suspend fun execute(response: SyncResponse) = withContext(Dispatchers.IO) {

        // 1. Process ACCEPTED (Clean chain)
        response.acceptedTxHashes.forEach { hashHex ->
            ledgerRepository.updateStatus(hexToBytes(hashHex), TransactionStatus.ACCEPTED)
        }

        // 2. Process REJECTED (Corrupted/Invalid)
        response.rejectedTxHashes.forEach { hashHex ->
            ledgerRepository.updateStatus(hexToBytes(hashHex), TransactionStatus.REJECTED)
        }

        // 3. Process CONFLICTED (Double Spends detected globally!)
        // These remain in the local database for audit purposes, but their balances
        // are voided, and they visually flag the user as fraudulent.
        response.conflictedTxHashes.forEach { hashHex ->
            ledgerRepository.updateStatus(hexToBytes(hashHex), TransactionStatus.CONFLICTED)
        }

        // 4. Evolve Trust Score based on Server's Risk Engine
        deviceStateRepository.updateTrustScore(response.updatedTrustScore)

        // 5. Reset the 48-Hour Heartbeat Rule
        // Because we successfully talked to the server, the device is allowed
        // to go offline again for another 48 hours.
        deviceStateRepository.updateLastSyncTimestamp(response.serverTimestamp)
    }

    private fun hexToBytes(hexString: String): ByteArray {
        return hexString.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}