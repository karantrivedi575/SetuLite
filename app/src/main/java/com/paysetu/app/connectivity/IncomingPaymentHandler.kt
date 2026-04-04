package com.paysetu.app.connectivity

interface IncomingPaymentHandler {
    fun handle(bytes: ByteArray)
}
