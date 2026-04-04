package com.paysetu.app.connectivity


import android.Manifest
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import java.util.UUID

class BleAdvertiser(
    private val advertiser: BluetoothLeAdvertiser,
    private val serviceUuid: UUID
) {

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun startAdvertising(sessionHint: ByteArray, callback: AdvertiseCallback) {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(serviceUuid))
            .addServiceData(ParcelUuid(serviceUuid), sessionHint)
            .build()

        advertiser.startAdvertising(settings, data, callback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun stopAdvertising(callback: AdvertiseCallback) {
        advertiser.stopAdvertising(callback)
    }
}

