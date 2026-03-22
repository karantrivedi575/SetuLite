package com.paysetu.app.data.ledger

import android.util.Log
import com.paysetu.app.data.ledger.dao.LedgerDao
import com.paysetu.app.data.ledger.entity.LedgerTransactionEntity
import com.paysetu.app.data.ledger.entity.TransactionDirection
import com.paysetu.app.data.ledger.entity.TransactionStatus
import java.nio.ByteBuffer
import java.security.MessageDigest

class TransactionProcessor(
    private val ledgerDao: LedgerDao,
    private val chainVerifier: ChainVerifier
) {
    /**
     * Processes incoming P2P payloads.
     * Returns a Pair containing (TransactionHashHex, Amount)
     */
    suspend fun processIncomingPayload(payload: String): Result<Pair<String, Long>> {
        return try {
            // 1. Basic Parsing
            if (!payload.startsWith("TX_PAYLOAD:")) {
                return Result.failure(Exception("Invalid payload format"))
            }

            val amount = payload.substringAfter("amount:").substringBefore("}").toLongOrNull() ?: 0L
            val remoteHashHex = payload.substringAfter("hash:").substringBefore(",")

            // 2. Fetch the local tip of the chain to link this new block
            val lastTx = ledgerDao.getLastTransaction()
            val expectedPrevHash = lastTx?.txHash ?: ByteArray(32) { 0 }

            // 3. Construct the local representation of this incoming payment
            val incomingTxTemplate = LedgerTransactionEntity(
                id = 0L,
                txHash = ByteArray(0),
                prevTxHash = expectedPrevHash,
                senderDeviceId = "REMOTE_SENDER".toByteArray(),
                receiverDeviceId = "LOCAL_DEVICE".toByteArray(),
                amount = amount,
                timestamp = System.currentTimeMillis(),
                signature = ByteArray(64) { 1.toByte() },
                direction = TransactionDirection.INCOMING,
                status = TransactionStatus.ACCEPTED
            )

            // 4. Cryptographic Seal
            val localHash = calculateHash(incomingTxTemplate)
            val finalTx = incomingTxTemplate.copy(txHash = localHash)

            // 5. Commit to the database
            ledgerDao.appendTransactionAtomically(finalTx)

            Log.d("PaySetu_Processor", "Successfully processed incoming payment of ₹$amount")

            // 💡 SUCCESS: Return both the hash and the amount to the ViewModel
            Result.success(Pair(remoteHashHex, amount))

        } catch (e: Exception) {
            Log.e("PaySetu_Processor", "Ledger Rejection: ${e.message}")
            Result.failure(e)
        }
    }

    private fun calculateHash(tx: LedgerTransactionEntity): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteBuffer.allocate(1024)

        buffer.put(tx.prevTxHash)
        buffer.put(tx.senderDeviceId)
        buffer.put(tx.receiverDeviceId)
        buffer.putLong(tx.amount)
        buffer.putLong(tx.timestamp)
        buffer.put(tx.direction.name.toByteArray())

        val bytesToHash = buffer.array().take(buffer.position()).toByteArray()
        return digest.digest(bytesToHash)
    }
}