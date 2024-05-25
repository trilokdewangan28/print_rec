package com.example.untitled2
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.example.untitled2/printer"
    private val PRINTER_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var bluetoothSocket: BluetoothSocket? = null

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "getUsbDevices" -> {
                    val devices = getUsbDevices()
                    result.success(devices)
                }
                "printReceipt" -> {
                    val text = call.argument<String>("text")
                    val macAddress = call.argument<String>("macAddress")
                    val method = call.argument<String>("method")
                    val vendorId = call.argument<Int>("vendorId")
                    val productId = call.argument<Int>("productId")
                    if (text != null && method != null) {
                        when (method) {
                            "bluetooth" -> if (macAddress != null) printViaBluetooth(text, macAddress)
                            "usb" -> if (vendorId != null && productId != null) printViaUsb(text, vendorId, productId)
                            else -> result.error("INVALID_METHOD", "Invalid method: $method", null)
                        }
                        result.success(null)
                    } else {
                        result.error("INVALID_ARGUMENTS", "Text or Method is null", null)
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun getUsbDevices(): List<Map<String, Any>> {
        val usbManager: UsbManager = getSystemService(USB_SERVICE) as UsbManager
        val deviceList = usbManager.deviceList
        val devices = mutableListOf<Map<String, Any>>()

        for (usbDevice in deviceList.values) {
            val device = mapOf(
                "vendorId" to usbDevice.vendorId,
                "productId" to usbDevice.productId,
                "deviceName" to usbDevice.deviceName
            )
            devices.add(device)
        }
        return devices
    }

    private fun printViaBluetooth(text: String, macAddress: String) {
        val bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(macAddress)

        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(PRINTER_UUID)
            bluetoothSocket?.connect()

            val outputStream: OutputStream = bluetoothSocket!!.outputStream
            outputStream.write(text.toByteArray())
            outputStream.flush()
            outputStream.close()

            bluetoothSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun printViaUsb(text: String, vendorId: Int, productId: Int) {
        val usbManager: UsbManager = getSystemService(USB_SERVICE) as UsbManager
        var connection: UsbDeviceConnection? = null
        var device: UsbDevice? = null

        for (usbDevice in usbManager.deviceList.values) {
            if (usbDevice.vendorId == vendorId && usbDevice.productId == productId) {
                device = usbDevice
                connection = usbManager.openDevice(device)
                break
            }
        }

        if (connection != null && device != null) {
            try {
                val outputStream: OutputStream = UsbPrinterOutputStream(connection, device)
                outputStream.write(text.toByteArray())
                outputStream.flush()
                outputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private class UsbPrinterOutputStream(
        private val connection: UsbDeviceConnection,
        private val device: UsbDevice
    ) : OutputStream() {
        override fun write(b: Int) {
            val buffer = byteArrayOf(b.toByte())
            write(buffer, 0, 1)
        }

        override fun write(buffer: ByteArray, offset: Int, count: Int) {
            connection.bulkTransfer(device.getInterface(0).getEndpoint(1), buffer, offset, count, 0)
        }
    }
}
