package de.harukadev.attacksharkx11.protocol

data class DpiBytes(val xByte: Int, val yByte: Int, val isDouble: Boolean, val isTriple: Boolean)

/** Wandelt einen gewünschten DPI-Wert (50-26000) in X/Y-Bytes + Flags um. */
fun convertDpiToBytes(dpi: Int): DpiBytes {
    val clamped = dpi.coerceIn(50, 26000)

    if (clamped == 20100) {
        return DpiBytes(xByte = 0xEB, yByte = 1, isDouble = true, isTriple = false)
    }

    var isDouble = false
    val isTriple = false // Die X11 nutzt keinen dreifachen Multiplikator
    var target = clamped

    if (clamped > 10000) {
        target = Math.round(clamped / 2.0).toInt()
        isDouble = true
    }

    val xByte: Int
    val yByte: Int
    if (target > 10000) {
        val extraIndex = (target - 10100) / 100
        val combined = 199 + extraIndex
        xByte = combined and 0xFF
        yByte = (combined shr 8) and 0xFF
    } else if (target > 5000 && target % 100 == 0) {
        val index = (target / 100) - 1
        xByte = if (index in DPI_3311.indices) DPI_3311[index] else 0
        yByte = 1
    } else {
        val index = (target - 50) / 50
        xByte = if (index in DPI_3311.indices) DPI_3311[index] else 0
        yByte = 0
    }

    return DpiBytes(xByte, yByte, isDouble, isTriple)
}

/** Rückrichtung: Rohbytes -> DPI-Wert (zum Auslesen der aktuellen Einstellungen). */
fun convertBytesToDpi(xByte: Int, yByte: Int, isDouble: Boolean, @Suppress("UNUSED_PARAMETER") isTriple: Boolean): Int {
    if (xByte == 0 && yByte == 0) return 0

    var dpiValue: Int
    when (yByte) {
        0 -> {
            val index = DPI_3311.indexOf(xByte)
            dpiValue = if (index >= 0) index * 50 + 50 else 50
        }
        1 -> {
            val index = DPI_3311.indexOf(xByte)
            dpiValue = if (index >= 0) index * 100 + 100 else 100
        }
        else -> {
            val combined = (yByte shl 8) or xByte
            val extraIndex = combined - 199
            dpiValue = 10100 + extraIndex * 100
        }
    }

    if (isDouble) {
        if (xByte == 235) return 20100 // 0xEB Sonderfall
        dpiValue *= 2
    }

    return minOf(dpiValue, 26000)
}
