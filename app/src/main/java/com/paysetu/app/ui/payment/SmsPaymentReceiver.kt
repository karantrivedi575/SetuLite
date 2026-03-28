package com.paysetu.app.ui.payment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsPaymentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 1. Ensure we are handling the correct action
        if (intent.action == "android.intent.action.DATA_SMS_RECEIVED") {
            val uri = intent.data

            // 2. Check if the message hit our specific payment port
            if (uri?.port == 8901) {
                try {
                    // 3. Extract the messages from the intent
                    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                    val fullPayload = StringBuilder()

                    for (sms in messages) {
                        // Binary data is stored in the userData field for DATA_SMS
                        val binaryData = sms.userData
                        if (binaryData != null) {
                            fullPayload.append(String(binaryData, Charsets.UTF_8))
                        }
                    }

                    val payloadString = fullPayload.toString()

                    // 4. Validate the PaySetu Transaction Prefix
                    if (payloadString.startsWith("SETU-TX-", ignoreCase = true)) {
                        handleIncomingPayment(context, payloadString)
                    } else {
                        Log.w("SmsReceiver", "Unknown data received on port 8901: $payloadString")
                    }
                } catch (e: Exception) {
                    Log.e("SmsReceiver", "Error parsing binary SMS", e)
                }
            }
        }
    }

    private fun handleIncomingPayment(context: Context, payload: String) {
        Log.d("SmsReceiver", "Validated PaySetu SMS received: $payload")

        /**
         * 💡 ARCHITECTURE NOTE:
         * Since BroadcastReceivers are short-lived, we should pass this to a
         * long-lived component or update a shared StateFlow.
         * For now, we will log it. In the next step, we'll connect this to
         * the PaymentViewModel.
         */

        // Example format: SETU-TX-SENDER_ID|AMOUNT|HASH
        val parts = payload.removePrefix("SETU-TX-").split("|")
        if (parts.size >= 2) {
            val senderId = parts[0]
            val amount = parts[1]
            Log.i("SmsReceiver", "Incoming Transfer Request: Sender=$senderId, Amount=$amount")

            // TODO: Trigger a high-priority notification or a UI update
            // via a SharedFlow or a LocalBroadcast.
        }
    }
}