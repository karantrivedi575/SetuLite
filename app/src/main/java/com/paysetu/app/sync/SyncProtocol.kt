package com.paysetu.app.sync

import com.paysetu.app.payment.SignedTransaction

/**
 * Sent by the Device when the 48-hour heartbeat expires or internet is restored.
 */
data class SyncRequest(
    val devicePublicKeyHex: String,
    val lastSyncedTxHashHex: String?, // Null if genesis sync
    val newTransactions: List<SignedTransaction> // Only send what the server hasn't seen
)

/**
 * The authoritative truth returned by the Backend Arbiter.
 */
data class SyncResponse(
    val acceptedTxHashes: List<String>,   // Clean, valid transactions
    val rejectedTxHashes: List<String>,   // Failed signature/format
    val conflictedTxHashes: List<String>, // DOUBLE SPENDS. First-seen won, these lost.
    val updatedTrustScore: Float,         // Calculated by backend based on conflicts
    val serverTimestamp: Long             // Used to reset the 48-hour heartbeat
)