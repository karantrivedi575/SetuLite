package com.paysetu.app.ui.payment

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.paysetu.app.data.ledger.entity.TransactionStatus
import com.paysetu.app.ui.PaySetuApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SmsPaymentReceiver : BroadcastReceiver() {

    // 💡 Background scope for the Receiver to do database work safely
    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {

            // 🚀 THE FIX: Grab a WakeLock to prevent the CPU from sleeping while the screen is off
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "PaySetu::SmsProcessingWakeLock"
            )

            // Acquire the lock for a maximum of 15 seconds (failsafe)
            wakeLock.acquire(15000L)

            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                val fullBody = StringBuilder()
                var senderNumber = ""

                for (sms in messages) {
                    if (senderNumber.isEmpty()) senderNumber = sms.originatingAddress ?: ""
                    fullBody.append(sms.displayMessageBody)
                }

                val bodyString = fullBody.toString()

                if (bodyString.startsWith("SETU:")) {
                    Log.i("PaySetu_Back", "Universal Signal from $senderNumber: $bodyString")

                    val payload = bodyString.removePrefix("SETU:")

                    // 💡 Tell Android OS to keep this Receiver alive while we do heavy lifting
                    val pendingResult = goAsync()

                    receiverScope.launch {
                        try {
                            when {
                                payload.startsWith("TX-OFFLINE") -> {
                                    handleIncomingPayment(context, senderNumber, bodyString)
                                }
                                payload.startsWith("ACK-OFFLINE") -> {
                                    handleAcknowledgement(context, senderNumber, bodyString)
                                }
                            }
                        } finally {
                            // Tell Android we are done, it can safely sleep now
                            pendingResult.finish()

                            // 🚀 THE FIX: Safely release the CPU to go back to sleep
                            if (wakeLock.isHeld) {
                                wakeLock.release()
                                Log.d("PaySetu_Back", "CPU WakeLock released.")
                            }
                        }
                    }
                } else {
                    // Not a PaySetu message, release lock immediately
                    if (wakeLock.isHeld) {
                        wakeLock.release()
                    }
                }
            } catch (e: Exception) {
                Log.e("PaySetu_Back", "Text processing crash", e)
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
            }
        }
    }

    private suspend fun handleIncomingPayment(context: Context, sender: String, fullPayload: String) {
        try {
            val payloadWithoutHeader = fullPayload.removePrefix("SETU:")
            val parts = payloadWithoutHeader.split("|")
            if (parts.size < 3) return

            val amount = parts[1].toLongOrNull() ?: 0L
            val txHash = parts[2]

            Log.d("PaySetu_Back", "Processing payment of $amount from $sender")

            // 💎 TRUE BACKGROUND LEDGER UPDATE
            val app = context.applicationContext as PaySetuApp
            val processor = app.transactionProcessor

            // Execute the processor (This evaluates the hash and saves to Room DB)
            val result = processor.processIncomingPayload(fullPayload)

            result.fold(
                onSuccess = {
                    Log.d("PaySetu_Back", "✅ Successfully credited $amount to ledger. Hash: $txHash")

                    // 💎 AUTOMATED ACK (Text SMS back to sender)
                    sendSilentAck(context, sender, txHash)

                    // 🚀 THE FIX: Rich Heads-Up Notification for the Receiver
                    val receiptMessage = "Transaction Hash: ${txHash.take(12).uppercase()}...\n" +
                            "Method: Secure Cellular Protocol\n" +
                            "Status: Verified & Settled"

                    showNotification(context, "Received ₢$amount from $sender", receiptMessage)
                },
                onFailure = { error ->
                    Log.e("PaySetu_Back", "❌ Ledger rejected the transaction: ${error.message}")
                }
            )

        } catch (e: Exception) {
            Log.e("PaySetu_Back", "Failed to process silent payment", e)
        }
    }

    private suspend fun handleAcknowledgement(context: Context, sender: String, fullPayload: String) {
        Log.d("PaySetu_Back", "ACK received from $sender. Updating UI.")

        val payloadWithoutHeader = fullPayload.removePrefix("SETU:")
        val parts = payloadWithoutHeader.split("|")
        val ackHash = parts.getOrNull(1)

        if (ackHash != null) {

            // 1. 💾 Update the Database immediately
            try {
                val app = context.applicationContext as PaySetuApp
                val ledgerRepo = app.ledgerRepository

                val hashByteArray = ackHash.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                ledgerRepo.updateTransactionStatus(hashByteArray, TransactionStatus.ACCEPTED)
                Log.d("PaySetu_Back", "Transaction $ackHash marked as ACCEPTED in ledger.")
            } catch (e: Exception) {
                Log.e("PaySetu_Back", "Failed to update ledger status for ACK", e)
            }

            // 2. 🚀 Fire the Event Bus globally to wake up the active UI
            withContext(Dispatchers.Main) {
                OfflinePaymentEventBus.triggerAck(ackHash)
            }
        }
    }

    private fun sendSilentAck(context: Context, targetNumber: String, originalHash: String) {
        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val ackMessage = "SETU:ACK-OFFLINE|$originalHash"

            smsManager.sendTextMessage(targetNumber, null, ackMessage, null, null)
            Log.i("PaySetu_Back", "Text ACK sent back to $targetNumber")
        } catch (e: Exception) {
            Log.e("PaySetu_Back", "Failed to send Text ACK", e)
        }
    }

    // 🚀 THE FIX: Modern Rich Notification
    private fun showNotification(context: Context, title: String, message: String) {
        val channelId = "payment_alerts"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Attempt to create an intent that opens your app's MainActivity
        val launchIntent = try {
            Intent(context, Class.forName("com.paysetu.app.ui.MainActivity")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        } catch (e: ClassNotFoundException) {
            null // Fallback if MainActivity is named differently
        }

        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Secure Payments", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts for incoming offline and SMS payments"
                enableLights(true)
                lightColor = android.graphics.Color.GREEN
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: Change to R.drawable.your_paysetu_logo
            .setContentTitle(title)
            .setContentText("Tap to view details")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message)) // Makes it expand like a receipt
            .setPriority(NotificationCompat.PRIORITY_MAX) // Forces it to pop up on screen
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setColor(0xFF10B981.toInt()) // Emerald Green styling
            .setAutoCancel(true)

        if (pendingIntent != null) {
            notificationBuilder.setContentIntent(pendingIntent)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}