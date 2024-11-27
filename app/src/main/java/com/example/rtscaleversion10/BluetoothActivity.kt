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
import android.os.Handler
import android.os.Message
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.ref.WeakReference
import java.util.UUID

class BluetoothActivity : AppCompatActivity() {

    private val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val handler = MyHandler(this)
    companion object {
        private const val TAG = "BluetoothActivity"
        private const val REQUEST_CODE_PERMISSIONS = 1001
        private const val MY_UUID = "00001101-0000-1000-8000-00805F9B34FB"
        private const val MESSAGE_READ = 0
        private const val MESSAGE_WRITE = 1
        private const val MESSAGE_TOAST = 2
    }

    private var connectedThread: ConnectedThread? = null
    private lateinit var weightTextView: TextView

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

    // Handler to Update UI from the Bluetooth Service
    private class MyHandler(activity: BluetoothActivity) : Handler() {
        val activityWeakReference = WeakReference(activity)
        @SuppressLint("SetTextI18n")
        override fun handleMessage(msg: Message) {

            val activity = activityWeakReference.get() ?: return

            when (msg.what) {
                MESSAGE_READ -> {
                    val readBuffer = msg.obj as ByteArray
                    val data = String(readBuffer)
                    // Update the UI with the weight
                    val weight = activity.extractWeight(data)
                    activity.runOnUiThread {
                        activity.weightTextView.text = "Weight: $weight"
                    }
                }
                MESSAGE_WRITE -> {
                    Toast.makeText(activity, "Data Sent", Toast.LENGTH_SHORT).show()
                }
                MESSAGE_TOAST -> {
                    val toastMessage = msg.data.getString("toast")
                    Toast.makeText(activity, toastMessage, Toast.LENGTH_SHORT).show()
                }
            }
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
                            if (it.name == "BT") {
                                val connectThread = ConnectThread(it)
                                connectThread.start()
                            }
                        }
                    } catch (_: SecurityException) {
                        Toast.makeText(this@BluetoothActivity, "Bluetooth Permission Denied", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Connect Thread for Bluetooth Connection
    @SuppressLint("MissingPermission")
    private inner class ConnectThread(device: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy {
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
                    Log.e(TAG, "Connection Failed", e)
                }
            }
        }
    }

    // Manage the Connected Bluetooth Socket
    private fun manageConnectedSocket(socket: BluetoothSocket) {
        val inputStream = socket.inputStream
        val outputStream = socket.outputStream

        connectedThread = ConnectedThread(socket, inputStream, outputStream)
        connectedThread?.start()
    }

    // Extract weight from the received data
    private fun extractWeight(data: String): String {
        return data.trim() // Assuming the scale sends weight as a string
    }

    // ConnectedThread for reading and sending data
    private inner class ConnectedThread(
        private val socket: BluetoothSocket,
        private val inputStream: InputStream,
        private val outputStream: OutputStream
    ) : Thread() {
        private val buffer = ByteArray(1024)

        override fun run() {
            var bytes: Int
            try {
                while (true) {
                    bytes = inputStream.read(buffer)
                    val data = String(buffer, 0, bytes)
                    val msg = handler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                    handler.sendMessage(msg)
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error reading data", e)
            } finally {
                try {
                    socket.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Error closing socket", e)
                }
            }
        }

        // Write data to Bluetooth device
        fun write(data: String) {
            try {
                outputStream.write(data.toByteArray())
                handler.obtainMessage(MESSAGE_WRITE).sendToTarget()
            } catch (e: IOException) {
                Log.e(TAG, "Error writing data", e)
                val errorMsg = handler.obtainMessage(MESSAGE_TOAST)
                val bundle = Bundle()
                bundle.putString("toast", "Failed to send data")
                errorMsg.data = bundle
                handler.sendMessage(errorMsg)
            }
        }
    }

    // Check for Bluetooth permissions
    private fun checkBluetoothPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)
    }

    // Request necessary Bluetooth permissions
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

    // Handle the result of permission request
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
                Toast.makeText(this, "Permissions Not Granted", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    // Query paired Bluetooth devices
    private fun queryPairedDevices() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Bluetooth is Not Available or Not Enabled", Toast.LENGTH_SHORT).show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter.bondedDevices?.forEach { device ->
                val deviceName = device.name
                val deviceAddress = device.address
                Log.d(TAG, "Paired Device: Name: $deviceName, Address: $deviceAddress")
            }
        } else {
            requestBluetoothPermissions()
        }
    }

    // Start Bluetooth discovery
    @SuppressLint("MissingPermission")
    private fun startDiscovery() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Bluetooth is not available or not enabled", Toast.LENGTH_SHORT).show()
            return
        }

        if (checkBluetoothPermissions()) {
            bluetoothAdapter.startDiscovery()
        } else {
            requestBluetoothPermissions()
        }
    }

    // Register Bluetooth discovery receiver
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(receiver, filter)
    }

    // Unregister Bluetooth receiver when the activity is destroyed
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        unregisterReceiver(receiver)
    }

    // onCreate function
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.bluetooth_activity)

        weightTextView = findViewById(R.id.weightTextView)

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothEnableResult.launch(enableBtIntent)
        }

        if (checkBluetoothPermissions()) {
            queryPairedDevices()
            startDiscovery()
        } else {
            requestBluetoothPermissions()
        }
    }
}
