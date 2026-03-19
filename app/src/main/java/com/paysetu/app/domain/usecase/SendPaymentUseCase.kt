package com.paysetu.app.domain.usecase

import com.paysetu.app.data.ledger.LedgerRepository
import com.paysetu.app.data.ledger.entity.LedgerTransactionEntity
import com.paysetu.app.data.ledger.entity.TransactionDirection
import com.paysetu.app.data.ledger.entity.TransactionStatus
import com.paysetu.app.domain.model.SignedTransaction
import com.paysetu.app.domain.policy.HeartbeatPolicy
import com.paysetu.app.domain.security.DeviceIntegrityChecker
import com.paysetu.app.domain.security.KeyProvider
import com.paysetu.app.domain.security.PinAuthorizer
import com.paysetu.app.domain.security.TransactionSigner
import com.paysetu.app.security.genesis.GenesisHashProvider
import java.nio.ByteBuffer
import java.security.MessageDigest

class SendPaymentUseCase(
    private val genesisHashProvider: GenesisHashProvider,
    private val pinAuthorizer: PinAuthorizer,
    private val deviceIntegrityChecker: DeviceIntegrityChecker,
    private val heartbeatPolicy: HeartbeatPolicy,
    private val signer: TransactionSigner,
    private val ledgerRepository: LedgerRepository,
    private val keyProvider: KeyProvider
) {

    suspend fun execute(amount: Long): SignedTransaction {
        // 1️⃣ Phase 6: Security & Policy Gates
        require(deviceIntegrityChecker.isDeviceTrusted()) { "Device integrity compromised" }
        heartbeatPolicy.enforce()

        // Correcting PinAuthorizer call: It requires a CharArray or no-args depending on implementation
        // Based on your PinGate.kt, it requires a CharArray. For now, we use a placeholder.
        val placeholderPin = CharArray(4) // In real UI, this comes from the user
        require(pinAuthorizer.authorize(placeholderPin)) { "User authentication required" }

        // 2️⃣ Phase 7: Fetch the Correct Previous Hash (The Chain Link)
        val lastTransaction = ledgerRepository.getLastTransaction()
        val prevHash = lastTransaction?.txHash ?: genesisHashProvider.getGenesisHash()

        // 3️⃣ Phase 2: Formulate Secure Payload
        val timestamp = System.currentTimeMillis()
        val senderId = keyProvider.getDevicePublicKey()

        // Using 8 bytes for Longs (Amount + Timestamp)
        val payloadToSign = ByteBuffer.allocate(8 + 8 + prevHash.size + senderId.size)
            .putLong(amount)
            .putLong(timestamp)
            .put(prevHash)
            .put(senderId)
            .array()

        val txHash = MessageDigest.getInstance("SHA-256").digest(payloadToSign)

        // FIX: Your TransactionSigner.sign() returns ByteArray, ensure the import is correct
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
            txHash = signedTx.txHash,
            prevTxHash = signedTx.prevTxHash,
            senderDeviceId = senderId,
            receiverDeviceId = ByteArray(0),
            amount = amount,
            timestamp = timestamp,
            signature = signedTx.signature,
            direction = TransactionDirection.OUTGOING,
            status = TransactionStatus.PENDING
        )

        ledgerRepository.appendTransactionAtomically(entity)

        return signedTx
    }
}