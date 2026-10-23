package com.rider.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), SensorEventListener {

    private var selectedAssistant = "RIDER" // Only "RIDER" and "NEXT"
    private var proximitySensorEnabled = true
    private var voiceControlEnabled = true
    private var bluetoothControlEnabled = true
    private var currentTab = "home"

    // Proximity Sensor
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var lastProximityTriggerTime = 0L

    // UI Structure
    private lateinit var mainLayout: LinearLayout
    private lateinit var contentContainer: FrameLayout
    private lateinit var homeScrollView: ScrollView
    private lateinit var bluetoothScrollView: ScrollView
    private lateinit var homeLayout: LinearLayout
    private lateinit var bluetoothStatusLayout: LinearLayout

    // Home Interactive Components
    private lateinit var playStartBtn: Button
    private lateinit var toggleWakeWordBtn: Button
    private lateinit var wakeWordIndicator: TextView
    private lateinit var proximityToggleBtn: Button
    private lateinit var testSwipeBtn: Button
    private lateinit var tutorialBtn: Button
    private lateinit var accessibilityStatusBadge: TextView

    // Bottom Navigation Buttons
    private lateinit var homeNavButton: Button
    private lateinit var bluetoothNavButton: Button

    // Bluetooth Helper
    private val bluetoothHelper by lazy { BluetoothHelper(this) }
    private var earbudsState: EarbudsState = EarbudsState(false, "", -1, -1, -1, "")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread ${thread.name}:", throwable)
        }

        try {
            buildUi()
            requestRequiredPermissions()
            updateAccessibilityBadge()
            refreshBluetoothData()

            // Initialize SensorManager and Proximity Sensor
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            updateAccessibilityBadge()
            refreshBluetoothData()

            // Register proximity sensor listener
            if (proximitySensor != null && proximitySensorEnabled) {
                sensorManager?.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_UI)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onResume: ${e.message}", e)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            sensorManager?.unregisterListener(this)
        } catch (_: Exception) {}
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!proximitySensorEnabled || event == null) return
        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            val distance = event.values[0]
            val maxRange = proximitySensor?.maximumRange ?: 5f
            if (distance < maxRange || distance <= 2.5f) {
                val now = System.currentTimeMillis()
                if (now - lastProximityTriggerTime > 1000) {
                    lastProximityTriggerTime = now
                    Log.d(TAG, "MainActivity Proximity wave detected! Triggering center swipe...")
                    triggerManualSwipe()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun requestRequiredPermissions() {
        try {
            val needed = mutableListOf<String>()

            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.RECORD_AUDIO)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            if (needed.isNotEmpty()) {
                requestPermissions(needed.toTypedArray(), PERMISSION_REQUEST_CODE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Permissions error: ${e.message}")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            refreshBluetoothData()
        }
    }

    private fun buildUi() {
        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0F19"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 1. Clean Header
        val header = createHeader()
        mainLayout.addView(header)

        // 2. Content Container
        contentContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        // Home View
        homeScrollView = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        homeLayout = buildHomeLayout()
        homeScrollView.addView(homeLayout)
        contentContainer.addView(homeScrollView)

        // Bluetooth View
        bluetoothScrollView = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }
        bluetoothStatusLayout = buildBluetoothLayout()
        bluetoothScrollView.addView(bluetoothStatusLayout)
        contentContainer.addView(bluetoothScrollView)

        mainLayout.addView(contentContainer)

        // 3. Compact Bottom Navigation with "created by rider" at very bottom
        val navBar = createBottomNav()
        mainLayout.addView(navBar)

        setContentView(mainLayout)
        switchTab("home")
    }

    private fun createHeader(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(36, 44, 36, 12)
            gravity = Gravity.CENTER_VERTICAL

            val iconView = ImageView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(80, 80).apply {
                    marginEnd = 16
                }
                setImageResource(R.mipmap.ic_launcher)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = createRoundedDrawable("#1E293B", "#EF4444", 16f)
                clipToOutline = true
            }
            addView(iconView)

            val title = TextView(this@MainActivity).apply {
                text = "RIDER"
                textSize = 22f
                setTextColor(Color.parseColor("#38BDF8"))
                setTypeface(null, Typeface.BOLD)
                letterSpacing = 0.08f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(title)

            accessibilityStatusBadge = TextView(this@MainActivity).apply {
                textSize = 10f
                setPadding(16, 6, 16, 6)
                background = createRoundedDrawable("#1E293B", "#334155", 16f)
                setOnClickListener { openAccessibilitySettings() }
            }
            addView(accessibilityStatusBadge)
        }
    }

    private fun buildHomeLayout(): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 16, 36, 20)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Wake-word indicator badge at top of home
        wakeWordIndicator = TextView(this).apply {
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(20, 10, 20, 10)
            background = createRoundedDrawable("#101827", "#38BDF8", 16f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24
            }
        }
        layout.addView(wakeWordIndicator)
        updateWakeWordIndicator()

        // Small Round Play Start Button -> Opens Configuration Dialog & Starts Assistant
        playStartBtn = Button(this).apply {
            text = "▶"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#020617"))
            background = createRoundedDrawable("#38BDF8", "#7DD3FC", 100f)
            layoutParams = LinearLayout.LayoutParams(180, 180).apply {
                gravity = Gravity.CENTER
                bottomMargin = 24
            }
            setOnClickListener { handleLetsStart() }
        }
        layout.addView(playStartBtn)

        // Interactive Tutorial & Test Activity Button
        tutorialBtn = Button(this).apply {
            text = "🎬 Open Live Test & Tutorial"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = createRoundedDrawable("#7C3AED", "#A78BFA", 18f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 14
            }
            setPadding(28, 22, 28, 22)
            setOnClickListener {
                val intent = Intent(this@MainActivity, TutorialActivity::class.java)
                startActivity(intent)
            }
        }
        layout.addView(tutorialBtn)

        // Single Combined Button for Wake Word Toggle ("Rider" / "Next")
        toggleWakeWordBtn = Button(this).apply {
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = createRoundedDrawable("#101827", "#38BDF8", 18f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 14
            }
            setPadding(28, 24, 28, 24)
            setOnClickListener { toggleWakeWord() }
        }
        layout.addView(toggleWakeWordBtn)
        updateWakeWordButtonText()

        // Proximity Sensor Toggle Switch
        proximityToggleBtn = Button(this).apply {
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 14
            }
            setPadding(24, 20, 24, 20)
            setOnClickListener { toggleProximitySensor() }
        }
        layout.addView(proximityToggleBtn)
        updateProximityButtonState()

        // Simple Test Swipe Trigger
        testSwipeBtn = Button(this).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#38BDF8"))
            background = createRoundedDrawable("#0F172A", "#1E293B", 14f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            setPadding(28, 14, 28, 14)
            setOnClickListener { triggerManualSwipe() }
        }
        layout.addView(testSwipeBtn)
        updateTestButtonText()

        return layout
    }

    private fun handleLetsStart() {
        if (!RiderAccessibilityService.isServiceRunning) {
            Toast.makeText(this, "Enable RIDER in Accessibility settings first", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
            requestRequiredPermissions()
            return
        }

        // Show configuration dialog with Proximity, Voice, and Bluetooth switches
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 30, 40, 20)
            setBackgroundColor(Color.parseColor("#101827"))
        }

        val dialogTitle = TextView(this).apply {
            text = "RIDER Start Configuration"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 0, 0, 20)
        }
        dialogView.addView(dialogTitle)

        // 1. Proximity Sensor Switch
        val switchProx = Switch(this).apply {
            text = "Proximity Sensor Wave Control"
            textSize = 14f
            setTextColor(Color.WHITE)
            isChecked = proximitySensorEnabled
            setPadding(0, 10, 0, 15)
        }
        dialogView.addView(switchProx)

        // 2. Voice Control Switch
        val switchVoice = Switch(this).apply {
            text = "Voice Control ($selectedAssistant / Next)"
            textSize = 14f
            setTextColor(Color.WHITE)
            isChecked = voiceControlEnabled
            setPadding(0, 10, 0, 15)
        }
        dialogView.addView(switchVoice)

        // 3. Bluetooth Earbuds Switch
        val switchBluetooth = Switch(this).apply {
            text = "Bluetooth Earbuds Tap Control"
            textSize = 14f
            setTextColor(Color.WHITE)
            isChecked = bluetoothControlEnabled
            setPadding(0, 10, 0, 20)
        }
        dialogView.addView(switchBluetooth)

        val dialogDesc = TextView(this).apply {
            text = "When you click 'Done', RIDER will run in the background with your chosen settings."
            textSize = 11f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 0, 0, 20)
        }
        dialogView.addView(dialogDesc)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Done") { _, _ ->
                proximitySensorEnabled = switchProx.isChecked
                voiceControlEnabled = switchVoice.isChecked
                bluetoothControlEnabled = switchBluetooth.isChecked

                VoiceWakeWordService.isProximityEnabled = proximitySensorEnabled
                VoiceWakeWordService.isBluetoothEnabled = bluetoothControlEnabled
                updateProximityButtonState()

                if (proximitySensorEnabled && proximitySensor != null) {
                    sensorManager?.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_UI)
                } else {
                    sensorManager?.unregisterListener(this)
                }

                if (voiceControlEnabled || bluetoothControlEnabled) {
                    VoiceWakeWordService.start(this, selectedAssistant)
                    Toast.makeText(this, "RIDER Active! Settings configured.", Toast.LENGTH_LONG).show()
                } else {
                    VoiceWakeWordService.stop(this)
                    Toast.makeText(this, "RIDER Active! Proximity sensor only.", Toast.LENGTH_LONG).show()
                }

                moveTaskToBack(true)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleWakeWord() {
        selectedAssistant = if (selectedAssistant == "RIDER") "NEXT" else "RIDER"
        updateWakeWordIndicator()
        updateWakeWordButtonText()
        updateTestButtonText()
        Toast.makeText(this, "Switched to Say $selectedAssistant", Toast.LENGTH_SHORT).show()

        if (VoiceWakeWordService.isServiceRunning) {
            VoiceWakeWordService.start(this, selectedAssistant)
        }
    }

    private fun toggleProximitySensor() {
        proximitySensorEnabled = !proximitySensorEnabled
        VoiceWakeWordService.isProximityEnabled = proximitySensorEnabled
        updateProximityButtonState()
        
        if (proximitySensorEnabled && proximitySensor != null) {
            sensorManager?.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            sensorManager?.unregisterListener(this)
        }

        val status = if (proximitySensorEnabled) "Enabled" else "Disabled"
        Toast.makeText(this, "Proximity Sensor $status", Toast.LENGTH_SHORT).show()
    }

    private fun updateWakeWordIndicator() {
        wakeWordIndicator.text = "Active Wake-Word: $selectedAssistant"
        wakeWordIndicator.setTextColor(Color.parseColor("#38BDF8"))
    }

    private fun updateWakeWordButtonText() {
        val nextOption = if (selectedAssistant == "RIDER") "Next" else "Rider"
        toggleWakeWordBtn.text = "Say $selectedAssistant  (Tap for $nextOption)"
    }

    private fun updateProximityButtonState() {
        if (proximitySensorEnabled) {
            proximityToggleBtn.text = "Proximity Sensor: ON (Tap to Disable)"
            proximityToggleBtn.setBackgroundColor(Color.parseColor("#064E3B"))
            proximityToggleBtn.setTextColor(Color.parseColor("#34D399"))
            proximityToggleBtn.background = createRoundedDrawable("#064E3B", "#059669", 18f)
        } else {
            proximityToggleBtn.text = "Proximity Sensor: OFF (Tap to Enable)"
            proximityToggleBtn.setBackgroundColor(Color.parseColor("#78350F"))
            proximityToggleBtn.setTextColor(Color.parseColor("#FBBF24"))
            proximityToggleBtn.background = createRoundedDrawable("#78350F", "#D97706", 18f)
        }
    }

    private fun updateTestButtonText() {
        testSwipeBtn.text = "Test \"Say $selectedAssistant\" (Swipe Up)"
    }

    private fun buildBluetoothLayout(): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 16, 36, 20)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        renderBluetoothContent(layout)
        return layout
    }

    private fun renderBluetoothContent(container: LinearLayout = bluetoothStatusLayout) {
        container.removeAllViews()

        // Device Title
        val devTitle = TextView(this).apply {
            text = if (earbudsState.connected) earbudsState.name else "Rider AirBuds Pro"
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 2)
        }
        container.addView(devTitle)

        val devSub = TextView(this).apply {
            text = if (earbudsState.connected) "Connected • Touch Sensors Active" else "Not Connected"
            textSize = 10f
            setTextColor(Color.parseColor("#38BDF8"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        container.addView(devSub)

        val earbudsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }

        // 1. LEFT CARD
        val leftCard = createEarbudCard("LEFT", "Sensor Active", "N/A") {
            triggerManualSwipe()
            Toast.makeText(this@MainActivity, "LEFT Earbud Sensor: Swiped Up!", Toast.LENGTH_SHORT).show()
        }
        earbudsRow.addView(leftCard)

        // 2. CASE CARD
        val caseCard = createEarbudCard("CASE", "Charging Case", if (earbudsState.caseBattery >= 0) "${earbudsState.caseBattery}%" else "N/A") {
            Toast.makeText(this@MainActivity, "Charging Case Connected", Toast.LENGTH_SHORT).show()
        }
        earbudsRow.addView(caseCard)

        // 3. RIGHT CARD
        val rightCard = createEarbudCard("RIGHT", "Sensor Active", "N/A") {
            triggerManualSwipe()
            Toast.makeText(this@MainActivity, "RIGHT Earbud Sensor: Swiped Up!", Toast.LENGTH_SHORT).show()
        }
        earbudsRow.addView(rightCard)

        container.addView(earbudsRow)

        val refreshBtn = Button(this).apply {
            text = "Refresh Earbuds Status"
            textSize = 11f
            setTextColor(Color.parseColor("#38BDF8"))
            background = createRoundedDrawable("#1E293B", "#38BDF8", 12f)
            setPadding(28, 14, 28, 14)
            setOnClickListener {
                refreshBluetoothData()
                Toast.makeText(this@MainActivity, "Status refreshed", Toast.LENGTH_SHORT).show()
            }
        }
        container.addView(refreshBtn)
    }

    private fun createEarbudCard(label: String, sensorStatus: String, batteryStr: String, onClickCard: (() -> Unit)? = null): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(8, 16, 8, 16)
            background = createRoundedDrawable("#101827", "#334155", 16f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 4
                marginEnd = 4
            }
            if (onClickCard != null) {
                setOnClickListener { onClickCard() }
            }

            val lbl = TextView(this@MainActivity).apply {
                text = label
                textSize = 10f
                setTextColor(Color.parseColor("#38BDF8"))
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            addView(lbl)

            val sensorBadge = TextView(this@MainActivity).apply {
                text = sensorStatus
                textSize = 9f
                setTextColor(Color.parseColor("#34D399"))
                background = createRoundedDrawable("#064E3B", "#059669", 10f)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 8
                    bottomMargin = 8
                }
                setPadding(12, 6, 12, 6)
            }
            addView(sensorBadge)

            val bat = TextView(this@MainActivity).apply {
                text = "Battery: $batteryStr"
                textSize = 10f
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
            }
            addView(bat)
        }
    }

    private fun createBottomNav(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 8, 20, 10)
            setBackgroundColor(Color.parseColor("#080C14"))

            val btnsRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            homeNavButton = Button(this@MainActivity).apply {
                text = "RIDER"
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = 8
                }
                setPadding(0, 14, 0, 14)
                setOnClickListener { switchTab("home") }
            }
            btnsRow.addView(homeNavButton)

            bluetoothNavButton = Button(this@MainActivity).apply {
                text = "EARBUDS"
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(0, 14, 0, 14)
                setOnClickListener { switchTab("bluetooth") }
            }
            btnsRow.addView(bluetoothNavButton)

            addView(btnsRow)

            val footerText = TextView(this@MainActivity).apply {
                text = "created by rider"
                textSize = 9f
                setTextColor(Color.parseColor("#64748B"))
                gravity = Gravity.CENTER
                setPadding(0, 8, 0, 0)
                letterSpacing = 0.08f
            }
            addView(footerText)
        }
    }

    private fun switchTab(tab: String) {
        currentTab = tab
        if (tab == "home") {
            homeScrollView.visibility = View.VISIBLE
            bluetoothScrollView.visibility = View.GONE
            homeNavButton.background = createRoundedDrawable("#0284C7", "#38BDF8", 12f)
            homeNavButton.setTextColor(Color.WHITE)
            bluetoothNavButton.background = createRoundedDrawable("#1E293B", "#334155", 12f)
            bluetoothNavButton.setTextColor(Color.parseColor("#94A3B8"))
        } else {
            homeScrollView.visibility = View.GONE
            bluetoothScrollView.visibility = View.VISIBLE
            renderBluetoothContent()
            bluetoothNavButton.background = createRoundedDrawable("#0284C7", "#38BDF8", 12f)
            bluetoothNavButton.setTextColor(Color.WHITE)
            homeNavButton.background = createRoundedDrawable("#1E293B", "#334155", 12f)
            homeNavButton.setTextColor(Color.parseColor("#94A3B8"))
        }
    }

    private fun triggerManualSwipe() {
        if (!RiderAccessibilityService.isServiceRunning) {
            Toast.makeText(this, "Enable RIDER in Accessibility settings first", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }

        val success = RiderAccessibilityService.instance?.swipeUp() ?: false
        if (success) {
            Toast.makeText(this, "Center Swipe-up gesture dispatched!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Swipe failed. Verify service is running.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open Accessibility settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateAccessibilityBadge() {
        if (RiderAccessibilityService.isServiceRunning) {
            accessibilityStatusBadge.text = "Active"
            accessibilityStatusBadge.setTextColor(Color.parseColor("#34D399"))
            accessibilityStatusBadge.background = createRoundedDrawable("#064E3B", "#059669", 16f)
        } else {
            accessibilityStatusBadge.text = "Permission"
            accessibilityStatusBadge.setTextColor(Color.parseColor("#FBBF24"))
            accessibilityStatusBadge.background = createRoundedDrawable("#78350F", "#D97706", 16f)
        }
    }

    private fun refreshBluetoothData() {
        try {
            val st = bluetoothHelper.getEarbudsState()
            earbudsState = st
            if (currentTab == "bluetooth") {
                renderBluetoothContent()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth refresh error: ${e.message}")
        }
    }

    private fun createRoundedDrawable(fillHex: String, strokeHex: String, cornerRadius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(fillHex))
            setStroke(2, Color.parseColor(strokeHex))
            this.cornerRadius = cornerRadius
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 1001
    }
}