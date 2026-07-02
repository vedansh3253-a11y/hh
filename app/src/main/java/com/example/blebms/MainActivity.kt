package com.example.blebms

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.UUID

/**
 * Generic BLE explorer + controller.
 *
 * Workflow:
 *  1. Scan -> tap a device to connect.
 *  2. Once connected, all discovered services/characteristics are listed.
 *  3. Tap a characteristic to select it as the write target; its
 *     value changes (notify) and reads are logged in hex at the bottom.
 *  4. Type raw hex bytes and hit Send, or use the ON/OFF preset buttons
 *     (edit CommandPresets.kt once you know your device's real command
 *     bytes, captured from the manufacturer app via HCI snoop log).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var scanButton: Button
    private lateinit var deviceList: RecyclerView
    private lateinit var charList: RecyclerView
    private lateinit var selectedCharText: TextView
    private lateinit var hexInput: EditText
    private lateinit var sendButton: Button
    private lateinit var onButton: Button
    private lateinit var offButton: Button
    private lateinit var logText: TextView
    private lateinit var commandRow: View
    private lateinit var presetRow: View

    private lateinit var deviceAdapter: SimpleListAdapter
    private lateinit var charAdapter: SimpleListAdapter

    private val bluetoothManager by lazy { getSystemService(BluetoothManager::class.java) }
    private val bluetoothAdapter by lazy { bluetoothManager.adapter }
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null

    private val foundDevices = mutableListOf<BluetoothDevice>()
    private val allCharacteristics = mutableListOf<BluetoothGattCharacteristic>()
    private var selectedCharacteristic: BluetoothGattCharacteristic? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            startScan()
        } else {
            log("Permissions denied — cannot scan.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        scanButton = findViewById(R.id.scanButton)
        deviceList = findViewById(R.id.deviceList)
        charList = findViewById(R.id.charList)
        selectedCharText = findViewById(R.id.selectedCharText)
        hexInput = findViewById(R.id.hexInput)
        sendButton = findViewById(R.id.sendButton)
        onButton = findViewById(R.id.onButton)
        offButton = findViewById(R.id.offButton)
        logText = findViewById(R.id.logText)
        commandRow = findViewById(R.id.commandRow)
        presetRow = findViewById(R.id.presetRow)

        deviceAdapter = SimpleListAdapter(onClick = { index -> connectTo(foundDevices[index]) })
        deviceList.layoutManager = LinearLayoutManager(this)
        deviceList.adapter = deviceAdapter

        charAdapter = SimpleListAdapter(
            onClick = { index -> selectCharacteristic(allCharacteristics[index]) },
            onLongClick = { index -> readCharacteristic(allCharacteristics[index]) }
        )
        charList.layoutManager = LinearLayoutManager(this)
        charList.adapter = charAdapter

        scanButton.setOnClickListener { toggleScan() }
        sendButton.setOnClickListener { sendHexToSelected(hexInput.text.toString()) }
        onButton.setOnClickListener { sendHexToSelected(CommandPresets.ON_HEX) }
        offButton.setOnClickListener { sendHexToSelected(CommandPresets.OFF_HEX) }
    }

    // ---------- Scanning ----------

    private fun toggleScan() {
        if (isScanning) {
            stopScan()
            return
        }
        if (requiredPermissions.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }) {
            permissionLauncher.launch(requiredPermissions)
            return
        }
        startScan()
    }

    private fun startScan() {
        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Enable Bluetooth first", Toast.LENGTH_SHORT).show()
            return
        }
        foundDevices.clear()
        deviceAdapter.setItems(emptyList())
        scanner = bluetoothAdapter.bluetoothLeScanner
        log("Scanning...")
        statusText.text = "Scanning..."
        isScanning = true
        scanButton.text = "Stop scan"
        try {
            scanner?.startScan(scanCallback)
        } catch (e: SecurityException) {
            log("Missing permission: ${e.message}")
        }
        handler.postDelayed({ if (isScanning) stopScan() }, 15000)
    }

    private fun stopScan() {
        isScanning = false
        scanButton.text = "Scan for devices"
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            // ignore
        }
        statusText.text = "Scan stopped — ${foundDevices.size} device(s) found"
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (foundDevices.none { it.address == device.address }) {
                foundDevices.add(device)
                val name = try {
                    device.name ?: "(unnamed)"
                } catch (e: SecurityException) {
                    "(unnamed)"
                }
                deviceAdapter.addOrUpdate("$name  [${device.address}]  rssi=${result.rssi}")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            log("Scan failed: code $errorCode")
        }
    }

    // ---------- Connection ----------

    private fun connectTo(device: BluetoothDevice) {
        stopScan()
        log("Connecting to ${device.address}...")
        statusText.text = "Connecting to ${device.address}..."
        try {
            gatt = device.connectGatt(this, false, gattCallback)
        } catch (e: SecurityException) {
            log("Missing BLUETOOTH_CONNECT permission")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            runOnUiThread {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    statusText.text = "Connected — discovering services..."
                    log("Connected. Discovering services...")
                    try {
                        g.discoverServices()
                    } catch (e: SecurityException) {
                        log("Missing permission to discover services")
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    statusText.text = "Disconnected"
                    log("Disconnected (status=$status)")
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            runOnUiThread {
                allCharacteristics.clear()
                val labels = mutableListOf<String>()
                for (service in g.services) {
                    for (ch in service.characteristics) {
                        allCharacteristics.add(ch)
                        labels.add(formatCharLabel(service.uuid, ch))
                    }
                }
                charAdapter.setItems(labels)
                deviceList.visibility = View.GONE
                charList.visibility = View.VISIBLE
                selectedCharText.visibility = View.VISIBLE
                commandRow.visibility = View.VISIBLE
                presetRow.visibility = View.VISIBLE
                statusText.text = "Connected — ${allCharacteristics.size} characteristic(s) found"
                log("Found ${g.services.size} service(s), ${allCharacteristics.size} characteristic(s).")
                log("Tap a characteristic to select it for writing; long-press to read its value.")

                // Subscribe to everything that supports notify, so incoming
                // data (e.g. status updates) shows up in the log automatically.
                for (ch in allCharacteristics) {
                    if (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                        enableNotify(g, ch)
                    }
                }
            }
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            runOnUiThread {
                log("READ ${characteristic.uuid}: ${bytesToHex(value)}")
            }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            runOnUiThread {
                log("NOTIFY ${characteristic.uuid}: ${bytesToHex(value)}")
            }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            runOnUiThread {
                val ok = status == BluetoothGatt.GATT_SUCCESS
                log("WRITE ${characteristic.uuid}: ${if (ok) "OK" else "FAILED (status=$status)"}")
            }
        }
    }

    private fun enableNotify(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
        try {
            g.setCharacteristicNotification(ch, true)
            val cccd = ch.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            if (cccd != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(cccd)
                }
            }
        } catch (e: SecurityException) {
            log("Missing permission to enable notify on ${ch.uuid}")
        }
    }

    // ---------- Characteristic selection / read / write ----------

    private fun selectCharacteristic(ch: BluetoothGattCharacteristic) {
        selectedCharacteristic = ch
        selectedCharText.text = "Selected: ${ch.uuid}"
        log("Selected characteristic ${ch.uuid} for writing.")
    }

    private fun readCharacteristic(ch: BluetoothGattCharacteristic) {
        val g = gatt ?: return
        try {
            g.readCharacteristic(ch)
        } catch (e: SecurityException) {
            log("Missing permission to read")
        }
    }

    private fun sendHexToSelected(hex: String) {
        val ch = selectedCharacteristic
        if (ch == null) {
            Toast.makeText(this, "Long-press or tap a characteristic first to select it", Toast.LENGTH_SHORT).show()
            return
        }
        val bytes = hexToBytes(hex)
        if (bytes == null) {
            Toast.makeText(this, "Invalid hex string", Toast.LENGTH_SHORT).show()
            return
        }
        val g = gatt ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(ch, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                ch.value = bytes
                @Suppress("DEPRECATION")
                ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                g.writeCharacteristic(ch)
            }
            log("Sending to ${ch.uuid}: ${bytesToHex(bytes)}")
        } catch (e: SecurityException) {
            log("Missing permission to write")
        }
    }

    // ---------- Helpers ----------

    private fun formatCharLabel(serviceUuid: UUID, ch: BluetoothGattCharacteristic): String {
        val props = mutableListOf<String>()
        if (ch.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) props.add("READ")
        if (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) props.add("WRITE")
        if (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) props.add("WRITE_NR")
        if (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) props.add("NOTIFY")
        return "svc ${serviceUuid.toString().take(8)}...  char ${ch.uuid}  [${props.joinToString(",")}]"
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString(" ") { String.format("%02X", it) }

    private fun hexToBytes(hex: String): ByteArray? {
        val clean = hex.replace(" ", "").replace("0x", "").replace("0X", "")
        if (clean.isEmpty() || clean.length % 2 != 0) return null
        return try {
            ByteArray(clean.length / 2) { i ->
                clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun log(msg: String) {
        Log.d("BleBms", msg)
        logText.append("$msg\n")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            gatt?.close()
        } catch (e: SecurityException) {
            // ignore
        }
    }
}
