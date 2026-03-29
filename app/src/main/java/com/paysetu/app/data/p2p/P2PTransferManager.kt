package com.paysetu.app.data.p2p

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*

class P2PTransferManager(private val context: Context) {

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val serviceId = "com.paysetu.app.OFFLINE_PAYMENT"

    // Using P2P_STAR ensures better compatibility across different Android versions
    // and prevents the Wi-Fi Direct socket lockup seen in P2P_POINT_TO_POINT on Xiaomi devices.
    private val STRATEGY = Strategy.P2P_STAR

    fun startBroadcasting(userName: String, onPayloadReceived: (String) -> Unit) {
        // 🛡️ Always clean state before starting
        stopAll()

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

    fun startDiscovering(onEndpointFound: (String, String) -> Unit) {
        // 🛡️ Always clean state before starting
        stopAll()

        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        connectionsClient.startDiscovery(
            serviceId,
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    Log.d("PaySetu_P2P", "Found Receiver: ${info.endpointName}")
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

    fun sendTransaction(
        endpointId: String,
        payloadData: String,
        onDeliveryConfirmed: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        Log.d("PaySetu_P2P", "Attempting to connect to $endpointId")

        connectionsClient.requestConnection(
            "PaySetu_Sender",
            endpointId,
            object : ConnectionLifecycleCallback() {
                override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
                    connectionsClient.acceptConnection(id, object : PayloadCallback() {
                        override fun onPayloadReceived(id: String, payload: Payload) {}

                        override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) {
                            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                                Log.d("PaySetu_P2P", "Hardware Delivery Confirmed!")
                                onDeliveryConfirmed()
                            } else if (update.status == PayloadTransferUpdate.Status.FAILURE) {
                                Log.e("PaySetu_P2P", "Payload transfer failed during upload")
                                onFailure("Radio transfer failed")
                            }
                        }
                    })
                }

                override fun onConnectionResult(id: String, result: ConnectionResolution) {
                    if (result.status.isSuccess) {
                        Log.d("PaySetu_P2P", "Connection Success. Waiting for socket...")
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                val payload = Payload.fromBytes(payloadData.toByteArray(Charsets.UTF_8))
                                connectionsClient.sendPayload(id, payload)
                            } catch (e: Exception) {
                                Log.e("PaySetu_P2P", "Payload conversion failed", e)
                                onFailure("Failed to package transaction")
                            }
                        }, 250)
                    } else {
                        Log.e("PaySetu_P2P", "Connection rejected/failed: ${result.status.statusCode}")
                        onFailure("Connection rejected by receiver")
                    }
                }

                override fun onDisconnected(id: String) {
                    Log.d("PaySetu_P2P", "Disconnected from $id")
                }
            }
        ).addOnFailureListener { e ->
            if (e.message?.contains("8003") == true || e.message?.contains("ALREADY_CONNECTED") == true) {
                Log.d("PaySetu_P2P", "Already connected. Firing payload directly.")
                val payload = Payload.fromBytes(payloadData.toByteArray(Charsets.UTF_8))
                connectionsClient.sendPayload(endpointId, payload)
                onDeliveryConfirmed()
            } else {
                Log.e("PaySetu_P2P", "requestConnection failed", e)
                onFailure(e.message ?: "Connection request failed")
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
                } else {
                    Log.e("PaySetu_P2P", "Receiver connection failed: ${result.status.statusCode}")
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
                    val dataString = String(it, Charsets.UTF_8)
                    Log.d("PaySetu_P2P", "Raw Payload Received: $dataString")
                    onReceived(dataString)
                }
            }

            override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) {}
        }

    // 🛡️ FIX: Hard Reset to clear Zombie Sockets
    fun stopAll() {
        try {
            connectionsClient.stopDiscovery()
            connectionsClient.stopAdvertising()
            connectionsClient.stopAllEndpoints()
            Log.d("PaySetu_P2P", "Radios hard reset.")
        } catch (e: Exception) {
            Log.e("PaySetu_P2P", "Error shutting down radios", e)
        }
    }
}