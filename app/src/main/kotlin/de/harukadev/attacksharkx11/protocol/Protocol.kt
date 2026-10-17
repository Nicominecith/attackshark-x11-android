package de.harukadev.attacksharkx11.protocol

/**
 * Byte-genauer Port der Reverse-Engineering-Ergebnisse aus
 * `attack-shark-x11-driver` (TypeScript) nach Kotlin - identisch zur
 * Python/Linux-Variante dieses Projekts, damit beide Clients exakt
 * dieselben Reports erzeugen.
 */

const val VENDOR_ID = 0x1D57
const val DEVICE_INTERFACE = 2 // bInterfaceNumber der Feature-Report-Schnittstelle

enum class ConnectionMode(val value: Int) {
    WIRELESS(0xFA60),
    WIRED(0xFA55);

    companion object {
        fun fromProductId(id: Int): ConnectionMode? = values().find { it.value == id }
    }
}

object ReportId {
    const val DPI = 0x04
    const val POLLING_RATE = 0x06
    const val LIGHTING_SETTINGS = 0x05
    const val BUTTON_MAPPING = 0x08
    const val READ_REPORT_ID = 0xA0
    const val CHANGE_PROFILE = 0x0C
}

object PacketLength {
    const val DPI = 0x38
    const val POLLING_RATE = 0x09
    const val LIGHTING_SETTINGS = 0x0F
    const val BUTTON_MAPPING = 0x3B
}

object MessageType {
    const val BATTERY = 0x40
    const val BATTERY1 = 0x41
}

enum class BatteryStatus(val value: Int) {
    NORMAL(0x01), FULLY_CHARGED(0x02), CHARGING(0x03);

    companion object {
        fun fromValue(v: Int) = values().find { it.value == v } ?: NORMAL
    }
}

enum class Rate(val hz: Int, val hex: Int) {
    POWER_SAVING(125, 0x08),
    OFFICE(250, 0x04),
    GAMING(500, 0x02),
    ESPORTS(1000, 0x01);

    companion object {
        fun fromHex(v: Int) = values().find { it.hex == v }
    }
}

enum class LightMode(val value: Int, val label: String) {
    OFF(0x0, "Aus"),
    STATIC(0x1, "Statisch"),
    BREATHING(0x2, "Atmend"),
    NEON(0x3, "Neon (Regenbogen)"),
    COLOR_BREATHING(0x4, "Farbwechsel atmend"),
    STATIC_DPI(0x5, "Statisch (DPI-Farbe)"),
    BREATHING_DPI(0x6, "Atmend (DPI-Farbe)");

    companion object {
        fun fromValue(v: Int) = values().find { it.value == v } ?: OFF
    }
}

enum class FirmwareAction(val value: Int, val label: String) {
    DISABLE_BUTTON(0x01, "Deaktiviert"),
    LEFT_CLICK(0x02, "Linksklick"),
    RIGHT_CLICK(0x03, "Rechtsklick"),
    MIDDLE_CLICK(0x04, "Mittelklick (Mausrad)"),
    BACKWARD(0x05, "Zurück (Seitentaste)"),
    FORWARD(0x06, "Vorwärts (Seitentaste)"),
    DOUBLE_CLICK(0x07, "Doppelklick"),
    SCROLL_UP(0x09, "Scrollrad hoch"),
    SCROLL_DOWN(0x0A, "Scrollrad runter"),
    GLOBAL_DPI_CYCLE(0x0D, "DPI-Stufe wechseln"),
    GLOBAL_DPI_PLUS(0x0E, "DPI +"),
    GLOBAL_DPI_MINUS(0x0F, "DPI -"),
    PLAY_PAUSE(0x18, "Play/Pause"),
    NEXT_TRACK(0x17, "Nächster Titel"),
    PREVIOUS_TRACK(0x16, "Vorheriger Titel"),
    VOL_PLUS(0x1B, "Lauter"),
    VOL_MINUS(0x1C, "Leiser"),
    MUTE(0x1A, "Stummschalten"),
    BROWSER_HOME(0x25, "Browser-Startseite"),
    BROWSER_BACKWARD(0x21, "Browser zurück"),
    BROWSER_FORWARD(0x20, "Browser vorwärts"),
    MY_COMPUTER(0x23, "Arbeitsplatz öffnen"),
    CALCULATOR(0x1D, "Taschenrechner");

    companion object {
        fun fromValue(v: Int) = values().find { it.value == v } ?: DISABLE_BUTTON
    }
}

enum class ButtonSlot(val offset: Int, val label: String) {
    LEFT(3, "Linke Taste"),
    RIGHT(6, "Rechte Taste"),
    MIDDLE(9, "Mausrad-Klick"),
    SLOT4(12, "Slot 4"),
    SLOT5(15, "Slot 5"),
    DPI_CYCLE(18, "DPI-Taste"),
    FORWARD(21, "Seitentaste vor"),
    BACKWARD(24, "Seitentaste zurück"),
    SLOT9(27, "Slot 9"),
    SLOT10(30, "Slot 10"),
    SLOT11(33, "Slot 11"),
    SLOT12(36, "Slot 12"),
    SLOT13(39, "Slot 13"),
    SLOT14(42, "Slot 14"),
    SLOT15(45, "Slot 15"),
    SLOT16(48, "Slot 16"),
    SCROLL_UP(51, "Scrollrad hoch"),
    SCROLL_DOWN(54, "Scrollrad runter"),
}

/** Die physischen Tasten, die die X11(SE) wirklich besitzt - Reihenfolge für die UI. */
val PHYSICAL_BUTTONS = listOf(
    ButtonSlot.LEFT, ButtonSlot.RIGHT, ButtonSlot.MIDDLE,
    ButtonSlot.FORWARD, ButtonSlot.BACKWARD, ButtonSlot.DPI_CYCLE,
)

data class Rgb(val r: Int = 0, val g: Int = 0, val b: Int = 0) {
    fun clamp() = Rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
}

val DEFAULT_DPI_VALUES = listOf(800, 1600, 2400, 3200, 5000, 22000, 0, 0)
val DEFAULT_DPI_COLORS = listOf(
    Rgb(0xFF, 0x00, 0x00), Rgb(0x00, 0xFF, 0x00), Rgb(0x00, 0x00, 0xFF), Rgb(0xFF, 0xFF, 0x00),
    Rgb(0x00, 0xFF, 0xFF), Rgb(0xFF, 0x00, 0xFF), Rgb(0xFF, 0x40, 0x00), Rgb(0xFF, 0xFF, 0xFF),
)

data class SlotButton(val action: FirmwareAction = FirmwareAction.DISABLE_BUTTON)

val DEFAULT_BUTTON_MAPPING: Map<ButtonSlot, SlotButton> = mapOf(
    ButtonSlot.LEFT to SlotButton(FirmwareAction.LEFT_CLICK),
    ButtonSlot.RIGHT to SlotButton(FirmwareAction.RIGHT_CLICK),
    ButtonSlot.MIDDLE to SlotButton(FirmwareAction.MIDDLE_CLICK),
    ButtonSlot.SLOT4 to SlotButton(FirmwareAction.DISABLE_BUTTON),
    ButtonSlot.SLOT5 to SlotButton(FirmwareAction.DISABLE_BUTTON),
    ButtonSlot.DPI_CYCLE to SlotButton(FirmwareAction.GLOBAL_DPI_CYCLE),
    ButtonSlot.FORWARD to SlotButton(FirmwareAction.FORWARD),
    ButtonSlot.BACKWARD to SlotButton(FirmwareAction.BACKWARD),
    ButtonSlot.SLOT9 to SlotButton(FirmwareAction.DISABLE_BUTTON),
    ButtonSlot.SLOT10 to SlotButton(FirmwareAction.DISABLE_BUTTON),
    ButtonSlot.SLOT11 to SlotButton(FirmwareAction.DISABLE_BUTTON),
    ButtonSlot.SLOT12 to SlotButton(FirmwareAction.DISABLE_BUTTON),
    ButtonSlot.SLOT13 to SlotButton(FirmwareAction.DISABLE_BUTTON),
    ButtonSlot.SLOT14 to SlotButton(FirmwareAction.DISABLE_BUTTON),
    ButtonSlot.SLOT15 to SlotButton(FirmwareAction.DISABLE_BUTTON),
    ButtonSlot.SLOT16 to SlotButton(FirmwareAction.DISABLE_BUTTON),
    ButtonSlot.SCROLL_UP to SlotButton(FirmwareAction.SCROLL_UP),
    ButtonSlot.SCROLL_DOWN to SlotButton(FirmwareAction.SCROLL_DOWN),
)

// ---------------------------------------------------------------------
// DPI (Report 0x04, wValue 0x0304)
// ---------------------------------------------------------------------

data class DpiSettings(
    val profileId: Int = 1,
    val angleSnap: Boolean = false,
    val rippleControl: Boolean = true,
    val motionSync: Boolean = false,
    val liftOffDistance: Int = 0,
    val activeStages: Int = 0x3F,
    val currentStage: Int = 2,
    val dpiValues: List<Int> = DEFAULT_DPI_VALUES,
    val dpiColors: List<Rgb> = DEFAULT_DPI_COLORS,
)

fun buildDpiReport(s: DpiSettings, mode: ConnectionMode): ByteArray {
    val buf = IntArray(56)
    buf[0] = ReportId.DPI
    buf[1] = PacketLength.DPI
    buf[2] = s.profileId and 0xFF

    buf[3] = ((s.liftOffDistance and 0x0F) shl 4) or (if (s.angleSnap) 0x01 else 0x00)
    buf[4] = ((if (s.motionSync) 0x01 else 0x00) shl 4) or (if (s.rippleControl) 0x01 else 0x00)
    buf[5] = s.activeStages and 0xFF

    var doubleFlags = 0
    var tripleFlags = 0
    for (i in 0 until 8) {
        val dpi = s.dpiValues.getOrElse(i) { 0 }
        val (xByte, yByte) = if (dpi > 0) {
            val conv = convertDpiToBytes(dpi)
            if (conv.isDouble) doubleFlags = doubleFlags or (1 shl i)
            if (conv.isTriple) tripleFlags = tripleFlags or (1 shl i)
            conv.xByte to conv.yByte
        } else 0 to 0
        buf[8 + i] = xByte
        buf[16 + i] = yByte
    }
    buf[6] = doubleFlags
    buf[7] = tripleFlags

    buf[24] = s.currentStage and 0xFF

    for (i in 0 until 8) {
        val c = s.dpiColors.getOrElse(i) { Rgb() }
        buf[25 + i * 3] = c.r and 0xFF
        buf[26 + i * 3] = c.g and 0xFF
        buf[27 + i * 3] = c.b and 0xFF
    }

    buf[49] = 0x02

    var checksum = 0
    for (i in 3..49) checksum += buf[i]
    checksum = checksum and 0xFFFF
    buf[50] = (checksum shr 8) and 0xFF
    buf[51] = checksum and 0xFF

    val bytes = ByteArray(buf.size) { buf[it].toByte() }
    return if (mode == ConnectionMode.WIRED) bytes.copyOfRange(0, 51) else bytes
}

fun parseDpiReport(buf: ByteArray): DpiSettings {
    val u = buf.map { it.toInt() and 0xFF }
    require(u.size >= 50) { "DPI-Antwort zu kurz: ${u.size} Bytes" }

    val lodAndSnap = u[3]
    val syncAndRipple = u[4]

    val doubleFlags = u[6]
    val tripleFlags = u[7]
    val values = (0 until 8).map { i ->
        convertBytesToDpi(u[8 + i], u[16 + i], (doubleFlags and (1 shl i)) != 0, (tripleFlags and (1 shl i)) != 0)
    }
    val colors = (0 until 8).map { i -> Rgb(u[25 + i * 3], u[26 + i * 3], u[27 + i * 3]) }

    return DpiSettings(
        profileId = u[2],
        liftOffDistance = (lodAndSnap and 0xF0) shr 4,
        angleSnap = (lodAndSnap and 0x0F) == 0x01,
        motionSync = ((syncAndRipple and 0xF0) shr 4) == 0x01,
        rippleControl = (syncAndRipple and 0x0F) == 0x01,
        activeStages = u[5],
        currentStage = u[24],
        dpiValues = values,
        dpiColors = colors,
    )
}

// ---------------------------------------------------------------------
// Abtastrate (Report 0x06, wValue 0x0306)
// ---------------------------------------------------------------------

fun buildPollingRateReport(rate: Rate): ByteArray {
    val buf = IntArray(9)
    buf[0] = 0x06; buf[1] = 0x09; buf[2] = 0x01
    buf[3] = rate.hex
    buf[4] = 0xFF - buf[3]
    return ByteArray(buf.size) { buf[it].toByte() }
}

fun parsePollingRateReport(buf: ByteArray): Rate {
    val u = buf.map { it.toInt() and 0xFF }
    require(u.size >= 4) { "Polling-Rate-Antwort zu kurz" }
    return Rate.fromHex(u[3]) ?: error("Unbekannter Polling-Rate-Wert: ${u[3]}")
}

// ---------------------------------------------------------------------
// RGB-Beleuchtung (Report 0x05, wValue 0x0305)
// ---------------------------------------------------------------------

data class LightingSettings(
    val profileId: Int = 1,
    val lightMode: LightMode = LightMode.OFF,
    val rgb: Rgb = Rgb(0x00, 0xFF, 0x00),
    val ledSpeed: Int = 3,
    val sleepTimeMinutes: Double = 0.5,
    val deepSleepMinutes: Int = 10,
    val keyResponseMs: Int = 8,
    val brightnessLevel: Int = 8,
)

private val SLEEP_TIME_LUT = (1..60).map { it * 0.5 }

fun buildLightingReport(s: LightingSettings, mode: ConnectionMode): ByteArray {
    require(s.keyResponseMs in 4..50 && s.keyResponseMs % 2 == 0) {
        "keyResponseMs muss zwischen 4 und 50 (gerade) liegen"
    }

    val buf = IntArray(15)
    buf[0] = 0x05; buf[1] = 0x0F
    buf[2] = s.profileId and 0xFF
    buf[3] = (s.lightMode.value and 0x0F) shl 4

    val deepSleep = s.deepSleepMinutes.coerceIn(1, 60)
    val iSleepH = deepSleep and 0xF0
    val iSleepL = deepSleep and 0x0F
    buf[4] = iSleepH or (s.ledSpeed and 0x0F)
    buf[5] = (iSleepL shl 4) or (s.brightnessLevel and 0x0F)

    val c = s.rgb.clamp()
    buf[6] = c.r; buf[7] = c.g; buf[8] = c.b

    buf[9] = Math.round(s.sleepTimeMinutes * 2).toInt() and 0xFF
    buf[10] = s.keyResponseMs / 2

    var checksum = 0
    for (i in 3..10) checksum += buf[i]
    checksum = checksum and 0xFFFF
    buf[11] = (checksum shr 8) and 0xFF
    buf[12] = checksum and 0xFF

    val bytes = ByteArray(buf.size) { buf[it].toByte() }
    return if (mode == ConnectionMode.WIRED) bytes.copyOfRange(0, 13) else bytes
}

fun parseLightingReport(buf: ByteArray): LightingSettings {
    val u = buf.map { it.toInt() and 0xFF }
    require(u.size >= 13) { "Beleuchtungs-Antwort zu kurz" }

    val deepSleepHl = u[4]
    val deepSleepLl = u[5]
    val iSleepH = (deepSleepHl and 0xF0) shr 4
    val ledSpeed = deepSleepHl and 0x0F
    val iSleepL = (deepSleepLl and 0xF0) shr 4
    val brightness = deepSleepLl and 0x0F

    val keyResponseByte = u[10]
    val sleepTimeByte = u[9]

    return LightingSettings(
        profileId = u[2],
        lightMode = LightMode.fromValue((u[3] shr 4) and 0x0F),
        rgb = Rgb(u[6], u[7], u[8]),
        ledSpeed = ledSpeed,
        deepSleepMinutes = (iSleepH shl 4) or iSleepL,
        brightnessLevel = brightness,
        sleepTimeMinutes = if (sleepTimeByte in 1..SLEEP_TIME_LUT.size) SLEEP_TIME_LUT[sleepTimeByte - 1] else 0.5,
        keyResponseMs = keyResponseByte * 2,
    )
}

// ---------------------------------------------------------------------
// Tastenbelegung (Report 0x08, wValue 0x0308)
// ---------------------------------------------------------------------

fun buildButtonMappingReport(mapping: Map<ButtonSlot, SlotButton>, profileId: Int = 1): ByteArray {
    val buf = IntArray(PacketLength.BUTTON_MAPPING)
    buf[0] = 0x08; buf[1] = 0x3B
    buf[2] = profileId and 0xFF

    val full = DEFAULT_BUTTON_MAPPING + mapping
    for (slot in ButtonSlot.values()) {
        val btn = full[slot] ?: SlotButton()
        buf[slot.offset] = btn.action.value and 0xFF
        buf[slot.offset + 1] = 0x00
        buf[slot.offset + 2] = 0x00
    }

    var checksum = 0
    for (i in 2 until buf.size - 1) checksum = (checksum + buf[i]) and 0xFF
    buf[58] = (checksum - 1) and 0xFF

    return ByteArray(buf.size) { buf[it].toByte() }
}

fun parseButtonMappingReport(buf: ByteArray): Map<ButtonSlot, SlotButton> {
    val u = buf.map { it.toInt() and 0xFF }
    require(u.size >= PacketLength.BUTTON_MAPPING) { "Tastenbelegungs-Antwort zu kurz" }
    return ButtonSlot.values().associateWith { slot ->
        SlotButton(action = FirmwareAction.fromValue(u[slot.offset]))
    }
}

// ---------------------------------------------------------------------
// Interner Reset / Profilwechsel (Report 0x0C, wValue 0x030C)
// ---------------------------------------------------------------------

fun buildChangeProfileReport(mode: ConnectionMode, profileId: Int = 1, vibration: Boolean = true): ByteArray {
    val v = if (vibration) 0x01 else 0x00
    val buf = intArrayOf(
        0x0C, 0x0A, profileId and 0xFF, (profileId xor 0xFF) and 0xFF,
        v, (v xor 0xFF) and 0xFF, 0x00, 0x00, 0x00, 0x00,
    )
    val bytes = ByteArray(buf.size) { buf[it].toByte() }
    return if (mode == ConnectionMode.WIRED) bytes.copyOfRange(0, 6) else bytes
}
