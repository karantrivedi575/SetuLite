package com.paysetu.app.security

interface TransactionSigner {
    fun sign(payloadHash: ByteArray): ByteArray
}