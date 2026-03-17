package com.paysetu.app.domain.security

interface TransactionSigner {
    fun sign(payloadHash: ByteArray): ByteArray
}