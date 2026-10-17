package de.harukadev.attacksharkx11.usb

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbRequest
import de.harukadev.attacksharkx11.protocol.ConnectionMode
import de.harukadev.attacksharkx11.protocol.DEVICE_INTERFACE
import de.harukadev.attacksharkx11.protocol.VENDOR_ID
import java.nio.ByteBuffer

class HidError(message: String) : Exception(message)

/**
 * Spricht per USB-Host-API (control transfers) mit der HID-Feature-Report-
 * Schnittstelle der Maus - das Android-Äquivalent zu HIDIOCSFEATURE/
 * HIDIOCGFEATURE unter Linux. Setzt USB-OTG voraus (Kabel oder Dongle an
 * einem USB-Host-fähigen Tablet/Handy).
 *
 * USB-HID-Spec, Abschnitt "Set_Report"/"Get_Report" (Control-Requests auf
 * Endpoint 0):
 *   SET_REPORT: bmRequestType = 0x21 (Host->Device, Class, Interface)
 *               bRequest = 0x09, wValue = (ReportType<<8)|ReportId
 *   GET_REPORT: bmRequestType = 0xA1 (Device->Host, Class, Interface)
 *               bRequest = 0x01, wValue = (ReportType<<8)|ReportId
 *   ReportType FEATURE = 0x03
 */
object UsbHid {
    private const val REPORT_TYPE_FEATURE = 0x03
    private const val REQUEST_SET_REPORT = 0x09
    private const val REQUEST_GET_REPORT = 0x01
    private const val TIMEOUT_MS = 1000

    fun findDevice(context: Context): UsbDevice? {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return manager.deviceList.values.firstOrNull { d ->
            d.vendorId == VENDOR_ID && ConnectionMode.fromProductId(d.productId) != null
        }
    }

    fun usbManager(context: Context): UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    /** Sucht die Feature-Report-Schnittstelle (bInterfaceNumber 2) des Geräts. */
    private fun findFeatureInterface(device: UsbDevice): UsbInterface {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.id == DEVICE_INTERFACE) return intf
        }
        // Fallback: erstes HID-Interface, falls das Gerät anders aufgebaut ist
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_HID) return intf
        }
        throw HidError("Keine passende HID-Schnittstelle am Gerät gefunden")
    }

    /** Sucht einen Interrupt-IN-Endpoint (für Akkustand-Benachrichtigungen). */
    private fun findInterruptInEndpoint(intf: UsbInterface): UsbEndpoint? {
        for (i in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_INT && ep.direction == UsbConstants.USB_DIR_IN) {
                return ep
            }
        }
        return null
    }
}

class HidConnection(context: Context, val device: UsbDevice) {
    private val manager = UsbHid.usbManager(context)
    private var connection: UsbDeviceConnection? = null
    private var iface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null

    val connectionMode: ConnectionMode =
        ConnectionMode.fromProductId(device.productId) ?: error("Unbekannte Produkt-ID ${device.productId}")

    fun open() {
        if (!manager.hasPermission(device)) {
            throw HidError("Keine USB-Berechtigung für dieses Gerät")
        }
        val conn = manager.openDevice(device) ?: throw HidError("Gerät konnte nicht geöffnet werden")
        connection = conn

        var found: UsbInterface? = null
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.id == DEVICE_INTERFACE) {
                found = intf
                break
            }
        }
        if (found == null) {
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                if (intf.interfaceClass == UsbConstants.USB_CLASS_HID) {
                    found = intf
                    break
                }
            }
        }
        val intf = found ?: throw HidError("Keine passende HID-Schnittstelle am Gerät gefunden")
        iface = intf

        if (!conn.claimInterface(intf, true)) {
            throw HidError("Konnte HID-Schnittstelle nicht belegen (claimInterface)")
        }

        for (i in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_INT && ep.direction == UsbConstants.USB_DIR_IN) {
                inEndpoint = ep
                break
            }
        }
    }

    fun close() {
        try {
            iface?.let { connection?.releaseInterface(it) }
        } catch (_: Exception) {
        }
        connection?.close()
        connection = null
        iface = null
    }

    /** SET_FEATURE: sendet einen kompletten Report (erstes Byte = Report-ID). */
    fun sendFeatureReport(data: ByteArray) {
        val conn = connection ?: throw HidError("Nicht verbunden")
        val intf = iface ?: throw HidError("Nicht verbunden")
        val reportId = data[0].toInt() and 0xFF
        val payload = data // inkl. Report-ID, wie von der USB-HID-Spec für numbered reports verlangt
        val bmRequestType = UsbConstants.USB_TYPE_CLASS or UsbConstants.USB_DIR_OUT or 0x01 // Interface
        val wValue = (0x03 shl 8) or reportId // ReportType FEATURE << 8 | ReportId
        val result = conn.controlTransfer(bmRequestType, 0x09, wValue, intf.id, payload, payload.size, 1000)
        if (result < 0) {
            throw HidError("SET_FEATURE fehlgeschlagen (Report ${"0x%02X".format(reportId)})")
        }
    }

    /** GET_FEATURE: fordert einen Report der Länge [length] (inkl. Report-ID-Byte) an. */
    fun getFeatureReport(reportId: Int, length: Int): ByteArray {
        val conn = connection ?: throw HidError("Nicht verbunden")
        val intf = iface ?: throw HidError("Nicht verbunden")
        val buffer = ByteArray(length)
        val bmRequestType = UsbConstants.USB_TYPE_CLASS or UsbConstants.USB_DIR_IN or 0x01
        val wValue = (0x03 shl 8) or (reportId and 0xFF)
        val result = conn.controlTransfer(bmRequestType, 0x01, wValue, intf.id, buffer, length, 1000)
        if (result < 0) {
            throw HidError("GET_FEATURE fehlgeschlagen (Report ${"0x%02X".format(reportId)})")
        }
        return buffer
    }

    /**
     * Liest asynchron einen eingehenden Interrupt-IN-Report (z.B. Akkustand),
     * mit Timeout. Gibt null zurück, falls innerhalb der Zeit nichts ankam
     * oder kein Interrupt-Endpoint existiert.
     */
    fun readInputReport(timeoutMs: Long = 1000): ByteArray? {
        val conn = connection ?: return null
        val ep = inEndpoint ?: return null

        val request = UsbRequest()
        return try {
            request.initialize(conn, ep)
            val buffer = ByteBuffer.allocate(ep.maxPacketSize.coerceAtLeast(16))
            if (!request.queue(buffer, buffer.capacity())) return null
            val completed = conn.requestWait(timeoutMs)
            if (completed == request) {
                buffer.flip()
                val out = ByteArray(buffer.remaining())
                buffer.get(out)
                out
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            request.close()
        }
    }
}
