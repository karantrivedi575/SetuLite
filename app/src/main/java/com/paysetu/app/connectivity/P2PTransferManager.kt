package com.paysetu.app.connectivity

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy

class P2PTransferManager(private val context: Context) {

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val serviceId = "com.paysetu.app.OFFLINE_PAYMENT"
    private val STRATEGY = Strategy.P2P_STAR

    // 🚀 PROXIMITY CONFIG: -45 is robust for most phones with cases.
    // Touching usually hits -30 to -40.
    private val TAP_RSSI_THRESHOLD = -45

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bleScanner = bluetoothManager?.adapter?.bluetoothLeScanner
    private var bleScanCallback: ScanCallback? = null

    /**
     * RECEIVE MODE: Called when user clicks "Receive".
     * Starts the beacon so others can find this device via QR or Tap.
     */
    fun startBroadcasting(userName: String, onPayloadReceived: (String) -> Unit) {
        stopAll()
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()

        connectionsClient.startAdvertising(
            userName, serviceId, createConnectionLifecycleCallback(onPayloadReceived), advertisingOptions
        ).addOnSuccessListener {
            Log.d("PaySetu_P2P", "Broadcasting Beacon: $userName")
        }.addOnFailureListener { e ->
            Log.e("PaySetu_P2P", "Broadcasting failed", e)
        }
    }

    /**
     * QR PATH: Standard long-range discovery.
     * Only starts the Nearby Radio. BLE Tap Radar remains OFF.
     */
    fun startDiscovering(onEndpointFound: (String, String) -> Unit) {
        stopAll()
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()

        connectionsClient.startDiscovery(
            serviceId,
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    Log.d("PaySetu_P2P", "Nearby Found (Long Range): ${info.endpointName}")
                    onEndpointFound(endpointId, info.endpointName)
                }
                override fun onEndpointLost(endpointId: String) {}
            },
            discoveryOptions
        ).addOnSuccessListener {
            Log.d("PaySetu_P2P", "Standard Discovery Active (BLE Tap Radar is OFF)")
        }
    }

    /**
     * TAP PATH: High-precision proximity discovery.
     * Starts both Nearby Radio and the BLE Tap Radar.
     */
    fun startTapDiscovery(onTapDetected: (String) -> Unit) {
        stopAll()
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()

        // 1. Start Nearby Discovery to resolve names
        connectionsClient.startDiscovery(
            serviceId,
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    val name = info.endpointName
                    // 🚀 HYBRID FIX: If Nearby discovers the node while in "Tap Mode",
                    // treat it as a successful proximity handshake.
                    if (name.startsWith("SETU-")) {
                        Log.d("PaySetu_P2P", "Proximity target resolved via Nearby: $name")

                        // 🛡️ HARDWARE SAFETY: Force kill radios immediately to prevent locking
                        stopAll()

                        onTapDetected(name)
                    }
                }
                override fun onEndpointLost(endpointId: String) {}
            },
            discoveryOptions
        ).addOnSuccessListener {
            // 2. Start the BLE Signal Strength Radar for the 2cm "Thump"
            startBleTapScanner(onTapDetected)
        }
    }

    private fun startBleTapScanner(onTapDetected: (String) -> Unit) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // 🚀 Fast as possible
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        bleScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val rssi = result.rssi
                val deviceName = try {
                    result.scanRecord?.deviceName ?: result.device?.name
                } catch (e: SecurityException) { null }

                if (deviceName?.startsWith("SETU-") == true) {
                    // 💥 PHYSICAL TAP DETECTED
                    if (rssi >= TAP_RSSI_THRESHOLD) {
                        Log.i("PaySetu_P2P", "💥 TAP SUCCESS! RSSI: $rssi | ID: $deviceName")

                        // 🛡️ HARDWARE SAFETY: Force kill radios immediately to prevent locking
                        stopAll()

                        onTapDetected(deviceName)
                    }
                }
            }
        }

        try {
            bleScanner?.startScan(null, settings, bleScanCallback)
            Log.d("PaySetu_P2P", "BLE Radar active (Threshold: $TAP_RSSI_THRESHOLD)")
        } catch (e: Exception) {
            Log.e("PaySetu_P2P", "BLE Scanner failed", e)
        }
    }

    private fun stopBleTapScanner() {
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bleScanCallback?.let { bleScanner?.stopScan(it) }
            }
            bleScanCallback = null
        } catch (e: Exception) {
            Log.e("PaySetu_P2P", "Error stopping BLE scanner", e)
        }
    }

    fun stopDiscovery() {
        connectionsClient.stopDiscovery()
        stopBleTapScanner()
        Log.d("PaySetu_P2P", "Discovery stopped.")
    }

    fun sendTransaction(
        endpointId: String,
        payloadData: String,
        onDeliveryConfirmed: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        Log.d("PaySetu_P2P", "Attempting connection to $endpointId")

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
                                Log.e("PaySetu_P2P", "Payload transfer failed")
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
                        Log.e("PaySetu_P2P", "Connection rejected: ${result.status.statusCode}")
                        onFailure("Connection rejected by receiver")
                    }
                }

                override fun onDisconnected(id: String) {
                    Log.d("PaySetu_P2P", "Disconnected from $id")
                }
            }
        ).addOnFailureListener { e ->
            if (e.message?.contains("8003") == true || e.message?.contains("ALREADY_CONNECTED") == true) {
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
                    val dataString = String(it, Charsets.UTF_8)
                    Log.d("PaySetu_P2P", "Raw Payload Received: $dataString")
                    onReceived(dataString)
                }
            }
            override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) {}
        }

    fun stopAll() {
        try {
            connectionsClient.stopDiscovery()
            connectionsClient.stopAdvertising()
            connectionsClient.stopAllEndpoints()
            stopBleTapScanner() // 🛡️ Ensure BLE shuts down too
            Log.d("PaySetu_P2P", "Radios hard reset.")
        } catch (e: Exception) {
            Log.e("PaySetu_P2P", "Error shutting down radios", e)
        }
    }
}