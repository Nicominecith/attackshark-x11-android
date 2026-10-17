package de.harukadev.attacksharkx11.device

import android.content.Context
import android.hardware.usb.UsbDevice
import de.harukadev.attacksharkx11.protocol.BatteryStatus
import de.harukadev.attacksharkx11.protocol.ButtonSlot
import de.harukadev.attacksharkx11.protocol.ConnectionMode
import de.harukadev.attacksharkx11.protocol.DEFAULT_BUTTON_MAPPING
import de.harukadev.attacksharkx11.protocol.DpiSettings
import de.harukadev.attacksharkx11.protocol.LightingSettings
import de.harukadev.attacksharkx11.protocol.MessageType
import de.harukadev.attacksharkx11.protocol.PacketLength
import de.harukadev.attacksharkx11.protocol.Rate
import de.harukadev.attacksharkx11.protocol.ReportId
import de.harukadev.attacksharkx11.protocol.SlotButton
import de.harukadev.attacksharkx11.protocol.buildButtonMappingReport
import de.harukadev.attacksharkx11.protocol.buildChangeProfileReport
import de.harukadev.attacksharkx11.protocol.buildDpiReport
import de.harukadev.attacksharkx11.protocol.buildLightingReport
import de.harukadev.attacksharkx11.protocol.buildPollingRateReport
import de.harukadev.attacksharkx11.protocol.parseButtonMappingReport
import de.harukadev.attacksharkx11.protocol.parseDpiReport
import de.harukadev.attacksharkx11.protocol.parseLightingReport
import de.harukadev.attacksharkx11.protocol.parsePollingRateReport
import de.harukadev.attacksharkx11.usb.HidConnection
import de.harukadev.attacksharkx11.usb.HidError
import kotlinx.coroutines.delay

class DeviceNotFoundError(message: String) : Exception(message)

/** Verbindung zu genau einer angeschlossenen Attack Shark X11(SE) über USB-OTG. */
class AttackSharkX11(private val context: Context, val usbDevice: UsbDevice) {
    private val hid = HidConnection(context, usbDevice)
    val connectionMode: ConnectionMode get() = hid.connectionMode

    private val delayMs = 180L

    fun open() {
        try {
            hid.open()
        } catch (e: HidError) {
            throw DeviceNotFoundError(e.message ?: "Verbindung fehlgeschlagen")
        }
    }

    fun close() = hid.close()

    // -- Schreiben --------------------------------------------------------

    suspend fun setDpi(settings: DpiSettings) {
        hid.sendFeatureReport(buildDpiReport(settings, connectionMode))
        delay(delayMs)
    }

    suspend fun setPollingRate(rate: Rate) {
        hid.sendFeatureReport(buildPollingRateReport(rate))
        delay(delayMs)
    }

    suspend fun setLighting(settings: LightingSettings) {
        hid.sendFeatureReport(buildLightingReport(settings, connectionMode))
        delay(delayMs)
    }

    suspend fun setButtonMapping(mapping: Map<ButtonSlot, SlotButton>) {
        hid.sendFeatureReport(buildButtonMappingReport(mapping))
        delay(delayMs)
    }

    suspend fun resetToDefaults() {
        hid.sendFeatureReport(buildChangeProfileReport(connectionMode))
        delay(delayMs)
        setDpi(DpiSettings())
        setLighting(LightingSettings())
        setPollingRate(Rate.ESPORTS)
        setButtonMapping(DEFAULT_BUTTON_MAPPING)
    }

    // -- Lesen --------------------------------------------------------------
    //
    // Firmware-Ablauf laut Upstream-Reverse-Engineering: zunächst per Report
    // 0xA0 "Lesen von <reportId> freischalten" anfordern, kurz warten, dann
    // den eigentlichen Report per GET_FEATURE(reportId, länge) abholen.

    private suspend fun unlockAndRead(reportId: Int, length: Int): ByteArray {
        hid.sendFeatureReport(byteArrayOf(0xA0.toByte(), reportId.toByte(), length.toByte(), 0x00, 0x01, 0x00, 0x00, 0x00))
        delay(delayMs)
        val status = hid.getFeatureReport(0xA0, 8)
        if (status.size > 1 && status[1].toInt() != 0x01) {
            throw HidError("Firmware hat Lesen von Report ${"0x%02X".format(reportId)} nicht freigegeben")
        }
        return hid.getFeatureReport(reportId, length)
    }

    suspend fun getDpi(): DpiSettings = parseDpiReport(unlockAndRead(ReportId.DPI, PacketLength.DPI))
    suspend fun getPollingRate(): Rate = parsePollingRateReport(unlockAndRead(ReportId.POLLING_RATE, PacketLength.POLLING_RATE))
    suspend fun getLighting(): LightingSettings = parseLightingReport(unlockAndRead(ReportId.LIGHTING_SETTINGS, PacketLength.LIGHTING_SETTINGS))
    suspend fun getButtonMapping(): Map<ButtonSlot, SlotButton> =
        parseButtonMappingReport(unlockAndRead(ReportId.BUTTON_MAPPING, PacketLength.BUTTON_MAPPING))

    // -- Akkustand (nur Wireless) --------------------------------------------

    /** Blockierender Aufruf (für einen Hintergrund-Coroutine-Loop gedacht). */
    fun pollBattery(timeoutMs: Long = 1000): Pair<BatteryStatus, Int>? {
        if (connectionMode != ConnectionMode.WIRELESS) return null
        val data = hid.readInputReport(timeoutMs) ?: return null
        if (data.size < 5) return null
        val msgType = data[2].toInt() and 0xFF
        if (msgType == MessageType.BATTERY || msgType == MessageType.BATTERY1) {
            val status = BatteryStatus.fromValue(data[3].toInt() and 0xFF)
            val percentage = data[4].toInt() and 0xFF
            return status to percentage
        }
        return null
    }
}
