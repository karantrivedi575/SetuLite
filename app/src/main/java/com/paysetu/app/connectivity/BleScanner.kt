package com.paysetu.app.connectivity

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import java.util.UUID

class BleScanner(
    private val context: Context,
    bluetoothAdapter: BluetoothAdapter,
    private val serviceUuid: UUID
) {

    private val scanner: BluetoothLeScanner =
        bluetoothAdapter.bluetoothLeScanner

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan(callback: ScanCallback) {
        if (!hasScanPermission()) {
            // Phase 8 rule: transport fails fast
            return
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(serviceUuid))
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(filters, settings, callback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan(callback: ScanCallback) {
        if (!hasScanPermission()) return
        scanner.stopScan(callback)
    }

    private fun hasScanPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
    }
}
