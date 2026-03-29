package com.paysetu.app.ui.payment

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeGenerator {

    // 💡 Helper to ensure the payload is correctly formatted for the scanner
    fun formatPayload(sessionId: String): String {
        return if (sessionId.startsWith("SETU-", ignoreCase = true)) {
            sessionId
        } else {
            "SETU-$sessionId"
        }
    }

    fun generateQrCode(content: String, size: Int = 512): Bitmap? {
        return try {
            // 🛡️ Enforce the prefix rule before generating
            val formattedContent = formatPayload(content)

            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(formattedContent, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}