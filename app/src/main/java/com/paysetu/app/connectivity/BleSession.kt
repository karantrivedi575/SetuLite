package com.paysetu.app.connectivity

import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

class BleSession {

    private var nonceLocal: ByteArray? = null
    private var nonceRemote: ByteArray? = null

    private var _sessionId: ByteArray? = null
    val sessionId: ByteArray
        get() = _sessionId ?: throw IllegalStateException("Session not established")

    private val incomingSequence = AtomicInteger(0)
    private val outgoingSequence = AtomicInteger(0)

    private var closed = false

    // Phase 9: Transport → Domain handoff
    lateinit var incomingPaymentHandler: IncomingPaymentHandler

    fun createLocalNonce(nonce: ByteArray) {
        check(nonceLocal == null) { "Local nonce already set" }
        nonceLocal = nonce
    }

    fun receiveRemoteNonce(nonce: ByteArray) {
        check(nonceRemote == null) { "Remote nonce already set" }
        nonceRemote = nonce
        deriveSessionIfReady()
    }

    private fun deriveSessionIfReady() {
        val local = nonceLocal ?: return
        val remote = nonceRemote ?: return

        val digest = MessageDigest.getInstance("SHA-256")
        _sessionId = digest.digest(local + remote)
    }

    fun isEstablished(): Boolean = _sessionId != null && !closed

    fun nextOutgoingSequence(): Int {
        check(isEstablished()) { "Session not established" }
        return outgoingSequence.getAndIncrement()
    }

    fun validateIncoming(sessionId: ByteArray, sequence: Int): Boolean {
        if (!isEstablished()) return false
        if (!this.sessionId.contentEquals(sessionId)) return false
        if (sequence != incomingSequence.get()) return false

        incomingSequence.incrementAndGet()
        return true
    }

    // Called ONLY after MTU reassembly completes
    fun onPayloadComplete(bytes: ByteArray) {
        check(isEstablished()) { "Session not established" }
        incomingPaymentHandler.handle(bytes)
    }

    fun close() {
        closed = true
    }
}
