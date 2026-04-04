package com.paysetu.app.ledger.ledger

import android.util.Log
import com.paysetu.app.ledger.LedgerDao
import com.paysetu.app.ledger.ChainVerifier
import com.paysetu.app.ledger.model.LedgerTransactionEntity
import com.paysetu.app.ledger.model.TransactionDirection
import com.paysetu.app.ledger.model.TransactionStatus
import java.nio.ByteBuffer
import java.security.MessageDigest

class TransactionProcessor(
    private val ledgerDao: LedgerDao,
    private val chainVerifier: ChainVerifier
) {
    /**
     * Processes incoming payloads from both Nearby P2P and Universal Text SMS.
     * Returns a Pair containing (TransactionHashHex, Amount)
     */
    suspend fun processIncomingPayload(payload: String): Result<Pair<String, Long>> {
        return try {
            val amount: Long
            val remoteHashHex: String
            val senderTimestamp: Long

            // 1. DUAL-MODE PARSING & SECURITY CHECKS
            when {
                // 📱 CASE A: Universal Text SMS Protocol (With Expiration Check)
                payload.startsWith("SETU:TX-OFFLINE|") -> {
                    val parts = payload.split("|")
                    // Now expecting 4 parts: HEADER | AMOUNT | HASH | TIMESTAMP
                    if (parts.size < 4) return Result.failure(Exception("Malformed SMS payload"))

                    amount = parts[1].toLongOrNull() ?: 0L
                    remoteHashHex = parts[2]
                    senderTimestamp = parts[3].toLongOrNull() ?: 0L

                    // 🛡️ DOUBLE-SPEND PREVENTION (10-Minute Expiration)
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - senderTimestamp > 600_000L) { // 600,000 ms = 10 minutes
                        Log.e("PaySetu_Security", "Transaction Expired! Network delayed SMS by over 10 minutes. Rejecting to prevent double-spend.")
                        return Result.failure(Exception("Transaction Expired: Automatically reversed by sender."))
                    }

                    Log.d("PaySetu_Processor", "Parsing SMS Payload: Amt $amount. Timestamp valid.")
                }

                // 📡 CASE B: Nearby P2P (Radio) Protocol
                payload.startsWith("TX_PAYLOAD:") -> {
                    amount = payload.substringAfter("amount:").substringBefore("}").toLongOrNull() ?: 0L
                    remoteHashHex = payload.substringAfter("hash:").substringBefore(",")
                    senderTimestamp = System.currentTimeMillis() // P2P is instant, use current time
                    Log.d("PaySetu_Processor", "Parsing Nearby Payload: Amt $amount")
                }

                else -> return Result.failure(Exception("Unknown payload format: ${payload.take(15)}..."))
            }

            if (amount <= 0) return Result.failure(Exception("Invalid transaction amount"))

            // 2. Fetch the local tip of the chain
            val lastTx = ledgerDao.getLastTransaction()
            val expectedPrevHash = lastTx?.txHash ?: ByteArray(32) { 0 }

            // 3. Construct the local representation
            val incomingTxTemplate = LedgerTransactionEntity(
                id = 0L,
                txHash = ByteArray(0),
                prevTxHash = expectedPrevHash,
                senderDeviceId = "REMOTE_NODE".toByteArray(),
                receiverDeviceId = "LOCAL_DEVICE".toByteArray(),
                amount = amount,
                timestamp = System.currentTimeMillis(), // We log when WE received it
                signature = ByteArray(64) { 1.toByte() }, // Placeholder for actual sig verification
                direction = TransactionDirection.INCOMING,
                status = TransactionStatus.ACCEPTED
                // nonce is auto-generated, refundedTxHash defaults to null
            )

            // 4. Cryptographic Seal (Chaining)
            val localHash = calculateHash(incomingTxTemplate)
            val finalTx = incomingTxTemplate.copy(txHash = localHash)

            // 5. Commit to the database
            ledgerDao.appendTransactionAtomically(finalTx)

            Log.i("PaySetu_Processor", "✅ Transaction Processed: ₹$amount | Hash: $remoteHashHex")

            Result.success(Pair(remoteHashHex, amount))

        } catch (e: Exception) {
            Log.e("PaySetu_Processor", "Ledger Rejection: ${e.message}")
            Result.failure(e)
        }
    }

    // 💡 SECURITY FIX: Updated to hash the new 'nonce' and 'refundedTxHash' fields
    private fun calculateHash(tx: LedgerTransactionEntity): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        // Increased buffer size to 2048 to prevent Overflow errors with the new UUID string
        val buffer = ByteBuffer.allocate(2048)

        buffer.put(tx.prevTxHash)
        buffer.put(tx.senderDeviceId)
        buffer.put(tx.receiverDeviceId)
        buffer.putLong(tx.amount)
        buffer.putLong(tx.timestamp)
        buffer.put(tx.direction.name.toByteArray())

        // Include new Entity fields
        buffer.put(tx.nonce.toByteArray())
        if (tx.refundedTxHash != null) {
            buffer.put(tx.refundedTxHash)
        }

        val array = ByteArray(buffer.position())
        buffer.flip()
        buffer.get(array)
        return digest.digest(array)
    }
}