// File: OfflinePaymentEngine.kt
package com.paysetu.app.payment

import com.paysetu.app.connectivity.HeartbeatPolicy
import com.paysetu.app.connectivity.model.SignedTransaction
import com.paysetu.app.ledger.PersistenceAck
import com.paysetu.app.ledger.ledger.LedgerRepository
import com.paysetu.app.ledger.model.LedgerTransactionEntity
import com.paysetu.app.ledger.model.TransactionDirection
import com.paysetu.app.ledger.model.TransactionStatus
import com.paysetu.app.security.DeviceIntegrityChecker
import com.paysetu.app.security.GenesisHashProvider
import com.paysetu.app.security.KeyProvider
import com.paysetu.app.security.PinAuthorizer
import com.paysetu.app.security.TransactionSigner
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * 🚀 CENTRALIZED OFFLINE PAYMENT ENGINE
 * Replaces SendPaymentUseCase, ReceivePaymentUseCase, and FinalizeOutgoingPaymentUseCase.
 * Acts as the single cryptographic brain for all peer-to-peer ledger mutations.
 */
class OfflinePaymentEngine(
    private val genesisHashProvider: GenesisHashProvider,
    private val pinAuthorizer: PinAuthorizer,
    private val deviceIntegrityChecker: DeviceIntegrityChecker,
    private val heartbeatPolicy: HeartbeatPolicy,
    private val signer: TransactionSigner,
    private val ledgerRepository: LedgerRepository,
    private val keyProvider: KeyProvider
) {

    // ==========================================
    // 📤 OUTGOING PAYMENTS
    // ==========================================
    /**
     * Executes an offline payment. (Formerly SendPaymentUseCase)
     * @param amount The value to send.
     * @param pin The user-provided PIN for authorization.
     */
    suspend fun sendPayment(amount: Long, pin: String): SignedTransaction {
        // 1️⃣ Phase 6: Security & Policy Gates
        require(deviceIntegrityChecker.isDeviceTrusted()) { "Device integrity compromised" }
        heartbeatPolicy.enforce()
        require(pinAuthorizer.authorize(pin)) { "Invalid PIN or authentication required" }

        // 2️⃣ Phase 7: Fetch the Correct Previous Hash (The Chain Link)
        val lastTransaction = ledgerRepository.getLastTransaction()
        val prevHash = lastTransaction?.txHash ?: genesisHashProvider.getGenesisHash()

        // 3️⃣ Phase 2: Formulate Secure Payload
        val timestamp = System.currentTimeMillis()
        val senderId = keyProvider.getDevicePublicKey()

        // Ensure buffer is exactly the right size to avoid padding issues in the hash
        val payloadToSign = ByteBuffer.allocate(8 + 8 + prevHash.size + senderId.size)
            .putLong(amount)
            .putLong(timestamp)
            .put(prevHash)
            .put(senderId)
            .array()

        val txHash = MessageDigest.getInstance("SHA-256").digest(payloadToSign)

        // 🔐 Sign the SHA-256 hash using the hardware-backed signer
        val signature = signer.sign(txHash)

        // 4️⃣ Create the Domain Model for Transport
        val signedTx = SignedTransaction(
            txHash = txHash,
            prevTxHash = prevHash,
            payload = payloadToSign,
            signature = signature
        )

        // 5️⃣ Phase 7 & 9: Atomic Persistence
        val entity = LedgerTransactionEntity(
            id = 0L, // Handled by Room
            txHash = signedTx.txHash,
            prevTxHash = signedTx.prevTxHash,
            senderDeviceId = senderId,
            receiverDeviceId = ByteArray(0), // Placeholder until Bluetooth handshake
            amount = amount,
            timestamp = timestamp,
            signature = signedTx.signature,
            direction = TransactionDirection.OUTGOING,
            status = TransactionStatus.ACCEPTED // Set to ACCEPTED once signed locally
        )

        // DAO performs final chain-integrity and replay checks
        ledgerRepository.appendTransactionAtomically(entity)

        return signedTx
    }

    // ==========================================
    // 📥 INCOMING PAYMENTS
    // ==========================================
    /**
     * Validates and saves an incoming payload. (Formerly ReceivePaymentUseCase)
     */
    suspend fun receivePayment(receivedTx: LedgerTransactionEntity): PersistenceAck {

        // Phase 5: Device trust
        require(deviceIntegrityChecker.isDeviceTrusted()) {
            "Device integrity check failed"
        }

        // Phase 6: Validation
        // 💡 THE BUG FIX: Dynamically checks the last hash instead of strictly demanding the Genesis Hash every time
        val lastTransaction = ledgerRepository.getLastTransaction()
        val expectedPrevHash = lastTransaction?.txHash ?: genesisHashProvider.getGenesisHash()

        require(receivedTx.prevTxHash.contentEquals(expectedPrevHash)) {
            "Invalid previous hash. Chain integrity broken."
        }

        // Phase 7: Normalize + append
        val acceptedTx = receivedTx.copy(
            direction = TransactionDirection.INCOMING,
            status = TransactionStatus.ACCEPTED
        )

        ledgerRepository.appendTransactionAtomically(acceptedTx)

        // Phase 9: ACK returned to transport
        return PersistenceAck(acceptedTx.txHash)
    }

    // ==========================================
    // 🔗 TRANSPORT FINALIZATION
    // ==========================================
    /**
     * Closes out the session. (Formerly FinalizeOutgoingPaymentUseCase)
     */
    // 🚀 THE FIX: Removed BleSession parameter.
    suspend fun finalizeOutgoingPayment(ack: PersistenceAck) {
        // Ledger mutation already happened during appendTransactionAtomically
        // Nearby Connections handles socket closure naturally via the ViewModel.
    }
}