package com.paysetu.app.data.p2p

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*

class P2PTransferManager(private val context: Context) {

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val serviceId = "com.paysetu.app.OFFLINE_PAYMENT"
    private val STRATEGY = Strategy.P2P_POINT_TO_POINT

    // 1. Start Advertising (Receiver Side)
    fun startBroadcasting(userName: String, onPayloadReceived: (String) -> Unit) {
        // 🚀 TURBO: Force high power.
        // We removed setDisallowedMediums to let the OS pick the fastest path automatically.
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(STRATEGY)
            .setLowPower(false)
            .build()

        connectionsClient.startAdvertising(
            userName,
            serviceId,
            createConnectionLifecycleCallback(onPayloadReceived),
            advertisingOptions
        ).addOnSuccessListener {
            Log.d("PaySetu_P2P", "Broadcasting as $userName...")
        }.addOnFailureListener { e ->
            Log.e("PaySetu_P2P", "Broadcasting failed", e)
        }
    }

    // 2. Start Discovery (Sender Side)
    fun startDiscovering(onEndpointFound: (String, String) -> Unit) {
        // 🚀 TURBO: Use high power to scan more frequently (better for Xiaomi/MiUI)
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(STRATEGY)
            .setLowPower(false)
            .build()

        connectionsClient.startDiscovery(
            serviceId,
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    onEndpointFound(endpointId, info.endpointName)
                }
                override fun onEndpointLost(endpointId: String) {}
            },
            discoveryOptions
        ).addOnSuccessListener {
            Log.d("PaySetu_P2P", "Discovery started...")
        }.addOnFailureListener { e ->
            Log.e("PaySetu_P2P", "Discovery failed", e)
        }
    }

    fun stopDiscovery() {
        connectionsClient.stopDiscovery()
        Log.d("PaySetu_P2P", "Discovery stopped.")
    }

    // 3. Connect & Send Payload (Sender Side)
    fun sendTransaction(
        endpointId: String,
        payloadData: String,
        onDeliveryConfirmed: () -> Unit
    ) {
        Log.d("PaySetu_P2P", "Attempting to send payload to $endpointId")

        // 💡 OPTIMIZATION: We removed the explicit ConnectionOptions builder call.
        // On many Android versions, requestConnection automatically uses the
        // discovery strategy. This avoids the "Unresolved reference" compiler error.
        connectionsClient.requestConnection(
            "PaySetu_Sender",
            endpointId,
            object : ConnectionLifecycleCallback() {
                override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
                    connectionsClient.acceptConnection(id, object : PayloadCallback() {
                        override fun onPayloadReceived(id: String, payload: Payload) {}

                        override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) {
                            // 🚀 THE MAGIC: Native hardware confirmation
                            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                                Log.d("PaySetu_P2P", "Hardware Delivery Confirmed!")
                                onDeliveryConfirmed()
                            }
                        }
                    })
                }

                override fun onConnectionResult(id: String, result: ConnectionResolution) {
                    if (result.status.isSuccess) {
                        Log.d("PaySetu_P2P", "Connection Success. Sending Payload...")
                        val payload = Payload.fromBytes(payloadData.toByteArray())
                        connectionsClient.sendPayload(id, payload)
                    }
                }

                override fun onDisconnected(id: String) {
                    Log.d("PaySetu_P2P", "Disconnected from $id")
                }
            }
        ).addOnFailureListener { e ->
            if (e.message?.contains("8003") == true || e.message?.contains("ALREADY_CONNECTED") == true) {
                Log.d("PaySetu_P2P", "Already connected. Firing payload directly.")
                val payload = Payload.fromBytes(payloadData.toByteArray())
                connectionsClient.sendPayload(endpointId, payload)
                onDeliveryConfirmed()
            }
        }
    }

    private fun createConnectionLifecycleCallback(onReceived: (String) -> Unit) =
        object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
                Log.d("PaySetu_P2P", "Incoming connection. Accepting...")
                connectionsClient.acceptConnection(id, createPayloadCallback(onReceived))
            }

            override fun onConnectionResult(id: String, result: ConnectionResolution) {
                if (result.status.isSuccess) {
                    Log.d("PaySetu_P2P", "Connection established with $id")
                }
            }

            override fun onDisconnected(id: String) {
                Log.d("PaySetu_P2P", "Connection lost with $id")
            }
        }

    private fun createPayloadCallback(onReceived: (String) -> Unit) =
        object : PayloadCallback() {
            override fun onPayloadReceived(id: String, payload: Payload) {
                payload.asBytes()?.let {
                    val dataString = String(it)
                    Log.d("PaySetu_P2P", "Raw Payload Received: $dataString")
                    onReceived(dataString)
                }
            }

            override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) {}
        }

    fun stopAll() {
        connectionsClient.stopAllEndpoints()
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        Log.d("PaySetu_P2P", "Radios shut down.")
    }
}