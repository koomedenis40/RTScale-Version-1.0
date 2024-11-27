package com.example.rtscaleversion10

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.UUID

class BluetoothActivity : AppCompatActivity() {

    private val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    companion object {
        private const val TAG = "BluetoothActivity"
        private const val REQUEST_CODE_PERMISSIONS = 1001
        private var MY_UUID = "00001101-0000-1000-8000-00805F9B34FB"
    }

    // Bluetooth Enable Request Contract
    private val bluetoothEnableResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Toast.makeText(this, "Bluetooth Enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Bluetooth not Enabled", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    // Bluetooth Discovery Receiver
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            when (action) {
                BluetoothDevice.ACTION_FOUND -> {
                    try {
                        val device: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                        device?.let {
                            val deviceName = it.name
                            val deviceAddress = it.address
                            Log.d(TAG, "Device Found: Name: $deviceName, Address: $deviceAddress")

                            if (deviceName == "BT"){
                                val connectThread = ConnectThread(it)
                                connectThread.start()
                            }
                        }

                        bluetoothAdapter?.cancelDiscovery()

                    } catch (_: SecurityException) {
                        // Handle Cases Where Bluetooth Permissions are Denied
                        Toast.makeText(this@BluetoothActivity, "Bluetooth Permission Denied", Toast.LENGTH_SHORT).show()
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.d(TAG, "Bluetooth Discovery Started")
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "Bluetooth Discovery Finished")
                }
            }
        }
    }

    // Connect Thread for Bluetooth Connection
    @SuppressLint("MissingPermission")
    private inner class ConnectThread(device: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy {
            MY_UUID = "00001101-0000-1000-8000-00805F9B34FB"
            device.createRfcommSocketToServiceRecord(UUID.fromString(MY_UUID))

        }

        @SuppressLint("MissingPermission")
        override fun run() {
            bluetoothAdapter?.cancelDiscovery()
            mmSocket?.let { socket ->
                try {
                    socket.connect()
                    manageConnectedSocket(socket)
                } catch (e: IOException) {
                    Log.e(TAG, "Connected Failed", e)
                }
                try {
                    socket.close()
                } catch (closeException: IOException) {
                    Log.e(TAG, "Socket Close Failed", closeException)
                }
            }
        }
    }

    fun manageConnectedSocket(socket: BluetoothSocket) {
        val inputStream = socket.inputStream
        val outputStream = socket.outputStream

        // Example: Reading data from the scale
        val buffer = ByteArray(1024)
        var bytes: Int

        try {
            while (true) {
                bytes = inputStream.read(buffer)
                val data = String(buffer, 0, bytes)
                Log.d(TAG, "Received Data: $data")

                // Handle the received data (e.g., extract weight)
                val weight = extractWeight(data)
                runOnUiThread {
                    // Update UI with the weight
                    Toast.makeText(this@BluetoothActivity, "Weight: $weight", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error Reading Data", e)
        } finally {
            try {
                socket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing socket", e)
            }
        }

    }

    fun extractWeight(data: String): String {
        // Example: Parse the data to extract the weight (adjust as per scale protocol)
        // Assuming the scale sends weight as a string (e.g., "70.5kg")
        return data.trim()
    }

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Request Bluetooth Enable if it's NOT Enabled
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothEnableResult.launch(enableBtIntent)
        }

        // Check for Permissions Before Querying Paired Devices or Starting Discovery
        if (checkBluetoothPermissions()) {
            queryPairedDevices()
            startDiscovery()
        } else {
            requestBluetoothPermissions()
        }
    }

    // Helper Function to Check All Required Permissions
    @SuppressLint("InlinedApi")
    private fun checkBluetoothPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)
    }

    // Helper Function to Request Necessary Bluetooth Permissions
    @SuppressLint("InlinedApi")
    private fun requestBluetoothPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.BLUETOOTH_SCAN
            ),
            REQUEST_CODE_PERMISSIONS
        )
    }

    // Handle the Result of Permission Request
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                queryPairedDevices()
                startDiscovery()
            } else {
                Toast.makeText(this,
                    "Permissions Not Granted",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun queryPairedDevices() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Toast.makeText(this,
                "Bluetooth is Not Available or Not Enabled",
                Toast.LENGTH_SHORT).show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter.bondedDevices?.forEach { device ->
                val deviceName = device.name
                val deviceAddress = device.address
                Log.d(TAG, "Paired Device: Name: $deviceName, Address: $deviceAddress")
            }
        } else {
            // Request Permission if not Granted
            requestBluetoothPermissions()
        }
    }

    private fun startDiscovery() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Toast.makeText(this,
                "Bluetooth is not available or not enabled",
                Toast.LENGTH_SHORT).show()
            return
        }

        if (checkBluetoothPermissions()) {
            // Start Bluetooth discovery
            if (!bluetoothAdapter.isEnabled) {
                Toast.makeText(
                    this,
                    "Bluetooth is not available or not enabled",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
                ){
                bluetoothAdapter.startDiscovery()
            }
        } else {
            requestBluetoothPermissions()
        }
    }


    // Register Receiver for Bluetooth Discovery
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(receiver, filter)
    }

    // Unregister Receiver When Activity is Destroyed
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }
}
