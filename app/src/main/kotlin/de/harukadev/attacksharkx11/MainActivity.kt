package de.harukadev.attacksharkx11

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.harukadev.attacksharkx11.device.AttackSharkX11
import de.harukadev.attacksharkx11.device.DeviceNotFoundError
import de.harukadev.attacksharkx11.protocol.*
import de.harukadev.attacksharkx11.ui.theme.AttackSharkTheme
import de.harukadev.attacksharkx11.ui.theme.SharkOrange
import de.harukadev.attacksharkx11.usb.HidError
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

private const val ACTION_USB_PERMISSION = "de.harukadev.attacksharkx11.USB_PERMISSION"

// -- Stabile, von Compose beobachtbare Zustandsklassen ----------------------

class StageUi(index: Int, dpi: Int, color: Color, active: Boolean) {
    val index = index
    var dpi by mutableStateOf(dpi)
    var color by mutableStateOf(color)
    var active by mutableStateOf(active)
}

class ButtonUi(val slot: ButtonSlot, val label: String, action: FirmwareAction) {
    var action by mutableStateOf(action)
}

fun Rgb.toComposeColor() = Color(r / 255f, g / 255f, b / 255f)
fun Color.toRgb() = Rgb((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

class MainActivity : ComponentActivity() {

    private var device: AttackSharkX11? by mutableStateOf(null)
    private var statusText by mutableStateOf("Suche Gerät...")
    private var batteryText by mutableStateOf("")
    private var snackbarMessage by mutableStateOf<String?>(null)

    private val stages = (0 until 6).map { i ->
        StageUi(i, DEFAULT_DPI_VALUES[i], DEFAULT_DPI_COLORS[i].toComposeColor(), i + 1 == 2)
    }
    private val buttonRows = PHYSICAL_BUTTONS.map { slot ->
        ButtonUi(slot, slot.label, DEFAULT_BUTTON_MAPPING[slot]?.action ?: FirmwareAction.DISABLE_BUTTON)
    }

    private var angleSnap by mutableStateOf(false)
    private var rippleControl by mutableStateOf(true)
    private var motionSync by mutableStateOf(false)
    private var liftOffDistance by mutableStateOf(0)

    private var lightMode by mutableStateOf(LightMode.OFF)
    private var lightColor by mutableStateOf(Rgb(0x00, 0xFF, 0x00).toComposeColor())
    private var ledSpeed by mutableStateOf(3f)
    private var brightness by mutableStateOf(8f)
    private var sleepMinutes by mutableStateOf(0.5f)
    private var deepSleepMinutes by mutableStateOf(10f)
    private var keyResponseMs by mutableStateOf(8f)

    private var selectedRate by mutableStateOf(Rate.ESPORTS)

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    val usbDevice: UsbDevice? = intent.getUsbDeviceExtra()
                    if (granted && usbDevice != null) {
                        connectTo(usbDevice)
                    } else {
                        statusText = "USB-Berechtigung abgelehnt"
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> refreshDeviceSearch()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    device?.close()
                    device = null
                    statusText = "Maus getrennt"
                    batteryText = ""
                }
            }
        }
    }

    private fun Intent.getUsbDeviceExtra(): UsbDevice? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        else @Suppress("DEPRECATION") getParcelableExtra(UsbManager.EXTRA_DEVICE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(usbReceiver, filter)
        }

        refreshDeviceSearch()

        setContent {
            AttackSharkTheme {
                AppScreen()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(usbReceiver) } catch (_: Exception) {}
        device?.close()
    }

    // -- Verbindung -----------------------------------------------------

    private fun refreshDeviceSearch() {
        val found = UsbHidFindOrNull(this)
        if (found == null) {
            statusText = "Keine Attack Shark X11/X11SE gefunden. Per OTG anschließen."
            return
        }
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        if (manager.hasPermission(found)) {
            connectTo(found)
        } else {
            statusText = "Warte auf USB-Berechtigung..."
            val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val permissionIntent = Intent(ACTION_USB_PERMISSION).setPackage(packageName)
            val pi = PendingIntent.getBroadcast(this, 0, permissionIntent, flags)
            manager.requestPermission(found, pi)
        }
    }

    private fun connectTo(usbDevice: UsbDevice) {
        try {
            val d = AttackSharkX11(this, usbDevice)
            d.open()
            device = d
            val modeLabel = if (d.connectionMode == ConnectionMode.WIRED) "Kabel" else "Funk (2,4 GHz)"
            statusText = "Verbunden · $modeLabel"
            if (d.connectionMode == ConnectionMode.WIRELESS) {
                startBatteryPolling(d)
            }
        } catch (e: DeviceNotFoundError) {
            statusText = "Verbindung fehlgeschlagen: ${e.message}"
        } catch (e: HidError) {
            statusText = "Verbindung fehlgeschlagen: ${e.message}"
        }
    }

    private fun startBatteryPolling(d: AttackSharkX11) {
        lifecycleScope.launch {
            while (device === d) {
                val result = runCatching { d.pollBattery(1500) }.getOrNull()
                if (result != null) {
                    val (status, pct) = result
                    val suffix = when (status) {
                        BatteryStatus.CHARGING -> " (lädt)"
                        BatteryStatus.FULLY_CHARGED -> " (voll)"
                        else -> ""
                    }
                    batteryText = "🔋 $pct%$suffix"
                } else {
                    kotlinx.coroutines.delay(300) // Schutz vor Busy-Loop, falls kein Interrupt-Endpoint existiert
                }
            }
        }
    }

    // -- Aktionen, von der UI aufgerufen ---------------------------------

    private fun collectDpiSettings(): DpiSettings {
        var activeMask = 0
        var currentStage = 1
        stages.forEach { s ->
            if (s.active) {
                activeMask = activeMask or (1 shl s.index)
                currentStage = s.index + 1
            }
        }
        return DpiSettings(
            dpiValues = stages.map { it.dpi } + listOf(0, 0),
            dpiColors = stages.map { it.color.toRgb() } + listOf(Rgb(255, 255, 255), Rgb(255, 255, 255)),
            angleSnap = angleSnap,
            rippleControl = rippleControl,
            motionSync = motionSync,
            liftOffDistance = liftOffDistance,
            activeStages = if (activeMask != 0) activeMask else 0x3F,
            currentStage = currentStage,
        )
    }

    private fun collectLightingSettings() = LightingSettings(
        lightMode = lightMode,
        rgb = lightColor.toRgb(),
        ledSpeed = ledSpeed.toInt(),
        brightnessLevel = brightness.toInt(),
        sleepTimeMinutes = (Math.round(sleepMinutes * 2) / 2.0),
        deepSleepMinutes = deepSleepMinutes.toInt(),
        keyResponseMs = keyResponseMs.toInt(),
    )

    private fun collectButtonMapping(): Map<ButtonSlot, SlotButton> =
        DEFAULT_BUTTON_MAPPING + buttonRows.associate { it.slot to SlotButton(it.action) }

    private fun applyAll(scopeLaunch: (suspend () -> Unit) -> Unit) {
        val d = device
        if (d == null) {
            snackbarMessage = "Kein Gerät verbunden"
            return
        }
        scopeLaunch {
            try {
                d.setDpi(collectDpiSettings())
                d.setLighting(collectLightingSettings())
                d.setPollingRate(selectedRate)
                d.setButtonMapping(collectButtonMapping())
                snackbarMessage = "Einstellungen übertragen"
            } catch (e: Exception) {
                snackbarMessage = "Übertragung fehlgeschlagen: ${e.message}"
            }
        }
    }

    private fun loadFromMouse(scopeLaunch: (suspend () -> Unit) -> Unit) {
        val d = device
        if (d == null) {
            snackbarMessage = "Kein Gerät verbunden"
            return
        }
        scopeLaunch {
            try {
                val dpi = d.getDpi()
                stages.forEachIndexed { i, s ->
                    s.dpi = dpi.dpiValues.getOrElse(i) { 0 }
                    s.color = dpi.dpiColors.getOrElse(i) { Rgb() }.toComposeColor()
                    s.active = (i + 1 == dpi.currentStage)
                }
                angleSnap = dpi.angleSnap
                rippleControl = dpi.rippleControl
                motionSync = dpi.motionSync

                val light = d.getLighting()
                lightMode = light.lightMode
                lightColor = light.rgb.toComposeColor()
                ledSpeed = light.ledSpeed.toFloat()
                brightness = light.brightnessLevel.toFloat()
                sleepMinutes = light.sleepTimeMinutes.toFloat()
                deepSleepMinutes = light.deepSleepMinutes.toFloat()
                keyResponseMs = light.keyResponseMs.toFloat()

                selectedRate = d.getPollingRate()

                val mapping = d.getButtonMapping()
                buttonRows.forEach { row -> mapping[row.slot]?.let { row.action = it.action } }

                snackbarMessage = "Einstellungen von der Maus geladen"
            } catch (e: Exception) {
                snackbarMessage = "Laden fehlgeschlagen: ${e.message}"
            }
        }
    }

    private fun resetDefaults(scopeLaunch: (suspend () -> Unit) -> Unit) {
        val d = device
        if (d == null) {
            snackbarMessage = "Kein Gerät verbunden"
            return
        }
        scopeLaunch {
            try {
                d.resetToDefaults()
                stages.forEachIndexed { i, s ->
                    s.dpi = DEFAULT_DPI_VALUES[i]
                    s.color = DEFAULT_DPI_COLORS[i].toComposeColor()
                    s.active = (i + 1 == 2)
                }
                angleSnap = false; rippleControl = true; motionSync = false; liftOffDistance = 0
                lightMode = LightMode.OFF; lightColor = Rgb(0, 255, 0).toComposeColor()
                ledSpeed = 3f; brightness = 8f; sleepMinutes = 0.5f; deepSleepMinutes = 10f; keyResponseMs = 8f
                selectedRate = Rate.ESPORTS
                buttonRows.forEach { row -> row.action = DEFAULT_BUTTON_MAPPING[row.slot]?.action ?: FirmwareAction.DISABLE_BUTTON }
                snackbarMessage = "Werkseinstellungen wiederhergestellt"
            } catch (e: Exception) {
                snackbarMessage = "Zurücksetzen fehlgeschlagen: ${e.message}"
            }
        }
    }

    // -- UI ----------------------------------------------------------------

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AppScreen() {
        val scope = rememberCoroutineScope()
        val snackbarHost = remember { SnackbarHostState() }
        fun launchIo(block: suspend () -> Unit) {
            scope.launch { block() }
        }

        LaunchedEffect(snackbarMessage) {
            snackbarMessage?.let {
                snackbarHost.showSnackbar(it)
                snackbarMessage = null
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHost) },
            topBar = {
                TopAppBar(
                    title = { Text("Attack Shark X11 Control", fontWeight = FontWeight.Bold) },
                    actions = {
                        if (batteryText.isNotEmpty()) {
                            Text(batteryText, modifier = Modifier.padding(end = 12.dp), fontSize = 14.sp)
                        }
                        IconButton(onClick = { refreshDeviceSearch() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren")
                        }
                    }
                )
            },
            bottomBar = {
                BottomAppBar {
                    OutlinedButton(
                        onClick = { loadFromMouse(::launchIo) },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    ) { Text("Von Maus laden") }
                    Button(
                        onClick = { applyAll(::launchIo) },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    ) { Text("Übernehmen") }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { DeviceCard(scope = ::launchIo) }
                item { DpiCard() }
                item { LightingCard() }
                item { PollingCard() }
                item { ButtonsCard() }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    @Composable
    private fun SectionCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (subtitle != null) {
                    Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }

    @Composable
    private fun DeviceCard(scope: (suspend () -> Unit) -> Unit) {
        SectionCard(title = "Gerät") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Mouse, contentDescription = null, tint = SharkOrange)
                Spacer(Modifier.width(8.dp))
                Text(statusText)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { resetDefaults(scope) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Werkseinstellungen wiederherstellen") }
        }
    }

    @Composable
    private fun DpiCard() {
        SectionCard(title = "DPI-Stufen", subtitle = "Bis zu 6 Sensor-Stufen, per DPI-Taste umschaltbar") {
            stages.forEach { stage ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = stage.active, onCheckedChange = { stage.active = it })
                    Text("Stufe ${stage.index + 1}", modifier = Modifier.width(72.dp))
                    Slider(
                        value = stage.dpi.toFloat(),
                        onValueChange = { stage.dpi = it.toInt() },
                        valueRange = 50f..26000f,
                        steps = 0,
                        modifier = Modifier.weight(1f)
                    )
                    Text("${stage.dpi}", modifier = Modifier.width(56.dp), fontSize = 12.sp)
                    ColorSwatch(color = stage.color, onColorChange = { stage.color = it })
                }
            }
            Spacer(Modifier.height(8.dp))
            SwitchRow("Winkel-Ausgleich (Angle Snap)", angleSnap) { angleSnap = it }
            SwitchRow("Ripple Control", rippleControl) { rippleControl = it }
            SwitchRow("Motion Sync", motionSync) { motionSync = it }
        }
    }

    @Composable
    private fun LightingCard() {
        SectionCard(title = "Beleuchtung") {
            DropdownRow(
                label = "Modus",
                options = LightMode.values().toList(),
                selected = lightMode,
                optionLabel = { it.label },
                onSelect = { lightMode = it }
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                Text("Farbe", modifier = Modifier.weight(1f))
                ColorSwatch(color = lightColor, onColorChange = { lightColor = it })
            }
            SliderRow("LED-Geschwindigkeit", ledSpeed, 1f, 5f) { ledSpeed = it }
            SliderRow("Helligkeit", brightness, 1f, 8f) { brightness = it }
            SliderRow("Ruhemodus nach (Min.)", sleepMinutes, 0.5f, 30f) { sleepMinutes = it }
            SliderRow("Tiefschlaf nach (Min.)", deepSleepMinutes, 1f, 60f) { deepSleepMinutes = it }
            SliderRow("Tastenanschlagzeit (ms)", keyResponseMs, 4f, 50f) { keyResponseMs = (it / 2).toInt().let { v -> (v * 2).toFloat() } }
        }
    }

    @Composable
    private fun PollingCard() {
        SectionCard(title = "Abtastrate (Polling Rate)") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Rate.POWER_SAVING, Rate.OFFICE, Rate.GAMING, Rate.ESPORTS).forEach { rate ->
                    FilterChip(
                        selected = selectedRate == rate,
                        onClick = { selectedRate = rate },
                        label = { Text("${rate.hz} Hz") }
                    )
                }
            }
        }
    }

    @Composable
    private fun ButtonsCard() {
        SectionCard(title = "Tastenbelegung", subtitle = "Physische Tasten der X11(SE) umbelegen") {
            buttonRows.forEach { row ->
                DropdownRow(
                    label = row.label,
                    options = ButtonMappingActions,
                    selected = row.action,
                    optionLabel = { it.label },
                    onSelect = { row.action = it }
                )
            }
        }
    }
}

private val ButtonMappingActions = listOf(
    FirmwareAction.LEFT_CLICK, FirmwareAction.RIGHT_CLICK, FirmwareAction.MIDDLE_CLICK,
    FirmwareAction.FORWARD, FirmwareAction.BACKWARD, FirmwareAction.DOUBLE_CLICK,
    FirmwareAction.GLOBAL_DPI_CYCLE, FirmwareAction.GLOBAL_DPI_PLUS, FirmwareAction.GLOBAL_DPI_MINUS,
    FirmwareAction.SCROLL_UP, FirmwareAction.SCROLL_DOWN,
    FirmwareAction.PLAY_PAUSE, FirmwareAction.NEXT_TRACK, FirmwareAction.PREVIOUS_TRACK,
    FirmwareAction.VOL_PLUS, FirmwareAction.VOL_MINUS, FirmwareAction.MUTE,
    FirmwareAction.BROWSER_HOME, FirmwareAction.BROWSER_BACKWARD, FirmwareAction.BROWSER_FORWARD,
    FirmwareAction.MY_COMPUTER, FirmwareAction.CALCULATOR, FirmwareAction.DISABLE_BUTTON,
)

private fun UsbHidFindOrNull(activity: MainActivity): UsbDevice? =
    de.harukadev.attacksharkx11.usb.UsbHid.findDevice(activity)

// -- Wiederverwendbare kleine Composables -----------------------------------

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderRow(label: String, value: Float, min: Float, max: Float, onValueChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp)
            Text(
                if (value % 1f == 0f) "${value.toInt()}" else "%.1f".format(value),
                fontSize = 13.sp,
                color = SharkOrange
            )
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = min..max)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownRow(label: String, options: List<T>, selected: T, optionLabel: (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.menuAnchor()) {
                Text(optionLabel(selected), fontSize = 12.sp)
            }
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = { onSelect(option); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Color, onColorChange: (Color) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    Surface(
        color = color,
        shape = CircleShape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
        onClick = { showDialog = true },
        modifier = Modifier.padding(start = 8.dp).size(32.dp)
    ) {}
    if (showDialog) {
        RgbPickerDialog(initial = color, onDismiss = { showDialog = false }, onConfirm = {
            onColorChange(it); showDialog = false
        })
    }
}

@Composable
private fun RgbPickerDialog(initial: Color, onDismiss: () -> Unit, onConfirm: (Color) -> Unit) {
    var r by remember { mutableStateOf(initial.red * 255f) }
    var g by remember { mutableStateOf(initial.green * 255f) }
    var b by remember { mutableStateOf(initial.blue * 255f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Farbe wählen") },
        text = {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(r / 255f, g / 255f, b / 255f))
                )
                Spacer(Modifier.height(16.dp))
                Text("Rot: ${r.toInt()}", fontSize = 12.sp)
                Slider(value = r, onValueChange = { r = it }, valueRange = 0f..255f)
                Text("Grün: ${g.toInt()}", fontSize = 12.sp)
                Slider(value = g, onValueChange = { g = it }, valueRange = 0f..255f)
                Text("Blau: ${b.toInt()}", fontSize = 12.sp)
                Slider(value = b, onValueChange = { b = it }, valueRange = 0f..255f)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(Color(r / 255f, g / 255f, b / 255f)) }) { Text("Übernehmen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
