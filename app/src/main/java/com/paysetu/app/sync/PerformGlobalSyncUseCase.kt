package com.paysetu.app.sync

import com.paysetu.app.ledger.ReconcileLedgerUseCase
import com.paysetu.app.ledger.ledger.LedgerRepository
import com.paysetu.app.payment.SignedTransaction

class PerformGlobalSyncUseCase(
    private val ledgerRepository: LedgerRepository,
    private val deviceStateRepository: `DeviceState.kt`,
    private val reconcileLedgerUseCase: ReconcileLedgerUseCase,
    private val backendApiService: BackendSyncService
) {

    /**
     * Orchestrates the global sync process.
     * 1. Collects unsynced local data.
     * 2. Communicates with the Backend Arbiter.
     * 3. Reconciles local state with server truth.
     */
    suspend fun execute(): Boolean {
        return try {
            // 1. Gather all local transactions the server hasn't seen
            val unsyncedEntities = ledgerRepository.getUnsyncedTransactions()

            // ✅ Fix: Map entities to SignedTransaction domain objects
            val unsyncedDomainTxs = unsyncedEntities.map { entity ->
                SignedTransaction(
                    txHash = entity.txHash,
                    prevTxHash = entity.prevTxHash,
                    payload = entity.amount.toString().toByteArray(), // Standardized payload format
                    signature = entity.signature
                )
            }

            val deviceId = deviceStateRepository.getDevicePublicKeyHex()
            val lastSyncedHash = deviceStateRepository.getLastSyncedHashHex()

            // 2. Build Request
            val request = SyncRequest(
                devicePublicKeyHex = deviceId,
                lastSyncedTxHashHex = lastSyncedHash,
                newTransactions = unsyncedDomainTxs
            )

            // 3. Talk to the Arbiter (The Backend)
            val response = backendApiService.syncLedger(request)

            // 4. Force Server Reality onto Local Device (Resolves double-spends/conflicts)
            reconcileLedgerUseCase.execute(response)

            // 5. Save the anchor (last accepted hash) for the next delta-sync
            if (response.acceptedTxHashes.isNotEmpty()) {
                val lastHashBytes = response.acceptedTxHashes.last().hexToByteArray()
                deviceStateRepository.updateLastSyncedHash(lastHashBytes)
            }

            true // Sync Successful
        } catch (e: Exception) {
            e.printStackTrace()
            false // Sync Failed (Offline or Server down)
        }
    }

    /**
     * Helper to convert Hex strings from the server back into binary for local storage.
     */
    private fun String.hexToByteArray(): ByteArray {
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}