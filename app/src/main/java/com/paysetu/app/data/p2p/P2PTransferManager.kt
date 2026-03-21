package com.paysetu.app.data.p2p

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import android.util.Log

class P2PTransferManager(private val context: Context) {

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val serviceId = "com.paysetu.app.OFFLINE_PAYMENT"

    // 1. Start Advertising (Receiver Side)
    fun startBroadcasting(userName: String, onPayloadReceived: (String) -> Unit) {
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_STAR)
            .build()

        connectionsClient.startAdvertising(
            userName, serviceId,
            createConnectionLifecycleCallback(onPayloadReceived),
            advertisingOptions
        ).addOnSuccessListener {
            Log.d("P2P", "Broadcasting as $userName...")
        }.addOnFailureListener {
            Log.e("P2P", "Broadcasting failed", it)
        }
    }

    // 2. Start Discovering (Sender Side)
    fun startDiscovering(onEndpointFound: (String, String) -> Unit) {
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_STAR)
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
        )
    }

    // 3. Connect & Send Payload (Sender Side)
    fun sendTransaction(endpointId: String, payloadData: String) {
        connectionsClient.requestConnection("Sender", endpointId, object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
                // Auto-accept the connection for a seamless payment experience
                connectionsClient.acceptConnection(id, createPayloadCallback { })
            }

            override fun onConnectionResult(id: String, result: ConnectionResolution) {
                if (result.status.isSuccess) {
                    val payload = Payload.fromBytes(payloadData.toByteArray())
                    connectionsClient.sendPayload(id, payload)
                }
            }

            override fun onDisconnected(id: String) {}
        })
    }

    private fun createConnectionLifecycleCallback(onReceived: (String) -> Unit) =
        object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
                connectionsClient.acceptConnection(id, createPayloadCallback(onReceived))
            }
            override fun onConnectionResult(id: String, result: ConnectionResolution) {}
            override fun onDisconnected(id: String) {}
        }

    private fun createPayloadCallback(onReceived: (String) -> Unit) =
        object : PayloadCallback() {
            override fun onPayloadReceived(id: String, payload: Payload) {
                payload.asBytes()?.let {
                    onReceived(String(it))
                }
            }
            override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) {}
        }

    fun stopAll() {
        connectionsClient.stopAllEndpoints()
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
    }
}