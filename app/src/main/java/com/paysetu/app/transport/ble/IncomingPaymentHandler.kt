package com.paysetu.app.transport.ble

interface IncomingPaymentHandler {
    fun handle(bytes: ByteArray)
}
