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
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(STRATEGY)
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

    // 2. Start Discovering (Sender Side)
    fun startDiscovering(onEndpointFound: (String, String) -> Unit) {
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(STRATEGY)
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

    // 💡 NEW: Stop Discovery explicitly without killing active connections
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

        connectionsClient.requestConnection(
            "PaySetu_Sender",
            endpointId,
            object : ConnectionLifecycleCallback() {
                override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
                    // 💡 The Sender MUST accept the connection to track the outgoing payload progress
                    connectionsClient.acceptConnection(id, object : PayloadCallback() {
                        override fun onPayloadReceived(endpointId: String, payload: Payload) {
                            // Sender ignores incoming payloads in this flow
                        }

                        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
                            // 🚀 FASTEST SYNC: Fires the exact millisecond the bytes hit Phone B
                            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                                Log.d("PaySetu_P2P", "Hardware Delivery Confirmed!")
                                onDeliveryConfirmed()
                            }
                        }
                    })
                }

                override fun onConnectionResult(id: String, result: ConnectionResolution) {
                    if (result.status.isSuccess) {
                        Log.d("PaySetu_P2P", "Connection Success to $id. Sending Payload...")
                        val payload = Payload.fromBytes(payloadData.toByteArray())
                        connectionsClient.sendPayload(id, payload)
                    } else {
                        Log.e("PaySetu_P2P", "Connection Failed with status: ${result.status.statusCode}")
                    }
                }

                override fun onDisconnected(id: String) {
                    Log.d("PaySetu_P2P", "Disconnected from $id")
                }
            }
        ).addOnFailureListener { e ->
            // Fallback: If we are somehow already connected, fire the payload immediately.
            if (e.message?.contains("8003") == true || e.message?.contains("ALREADY_CONNECTED") == true) {
                Log.d("PaySetu_P2P", "Already connected. Firing payload directly.")
                val payload = Payload.fromBytes(payloadData.toByteArray())
                connectionsClient.sendPayload(endpointId, payload)

                // Manually trigger the callback so the UI doesn't hang
                onDeliveryConfirmed()
            } else {
                Log.e("PaySetu_P2P", "Request Connection Failed", e)
            }
        }
    }

    // --- Helper Callbacks ---

    private fun createConnectionLifecycleCallback(onReceived: (String) -> Unit) =
        object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
                Log.d("PaySetu_P2P", "Incoming connection from ${info.endpointName}. Accepting...")
                connectionsClient.acceptConnection(id, createPayloadCallback(onReceived))
            }

            override fun onConnectionResult(id: String, result: ConnectionResolution) {
                if (result.status.isSuccess) {
                    Log.d("PaySetu_P2P", "Connection successfully established with $id")
                } else {
                    Log.e("PaySetu_P2P", "Failed to establish connection: ${result.status.statusCode}")
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
                    Log.d("PaySetu_P2P", "Raw Payload Received from $id: $dataString")
                    onReceived(dataString)
                }
            }

            override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) {
                // ❌ Auto-disconnect removed. The connection is closed gracefully
                // when the user hits 'Cancel' or 'Return to Dashboard' via stopAll().
            }
        }

    fun stopAll() {
        connectionsClient.stopAllEndpoints()
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        Log.d("PaySetu_P2P", "All P2P radios stopped.")
    }
}