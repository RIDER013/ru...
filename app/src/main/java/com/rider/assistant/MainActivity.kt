package com.rider.assistant

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var selectedAssistant = "RIDER"
    private var isListeningServiceActive = false
    private var currentTab = "home" // "home" or "bluetooth"

    // UI Containers
    private lateinit var rootContainer: FrameLayout
    private lateinit var homeDashboard: LinearLayout
    private lateinit var bluetoothDashboard: LinearLayout

    // Home UI
    private lateinit var riderButton: Button
    private lateinit var buddyButton: Button
    private lateinit var micStatusText: TextView
    private lateinit var micToggleButton: Button
    private lateinit var accessibilityStatusBadge: TextView

    // Bluetooth UI
    private lateinit var bluetoothStatusLayout: LinearLayout
    private val bluetoothHelper by lazy { BluetoothHelper(this) }
    private var earbudsState: EarbudsState = EarbudsState(false, "", 0, 0, 0, "")

    override fun onCreate(savedInstanceState: Bundle?) {
        // Crash prevention: handle any uncaught runtime exceptions safely
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread ${thread.name}: ", throwable)
        }

        super.onCreate(savedInstanceState)

        try {
            buildUi()
            requestRequiredPermissions()
            updateAccessibilityBadge()
            refreshBluetoothData()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            updateAccessibilityBadge()
            refreshBluetoothData()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onResume: ${e.message}", e)
        }
    }

    private fun requestRequiredPermissions() {
        try {
            val permissions = mutableListOf<String>()

            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.RECORD_AUDIO)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            if (permissions.isNotEmpty()) {
                requestPermissions(permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting permissions: ${e.message}")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // When permissions are answered, safely refresh Bluetooth
            refreshBluetoothData()
        }
    }

    private fun buildUi() {
        rootContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F19"))
        }

        val mainVertical = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Top Header Bar
        val header = createHeader()
        mainVertical.addView(header)

        // Middle Content Area
        val contentArea = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        // 1. Home Dashboard
        homeDashboard = createHomeDashboard()
        contentArea.addView(homeDashboard)

        // 2. Bluetooth Dashboard
        bluetoothDashboard = createBluetoothDashboard()
        contentArea.addView(bluetoothDashboard)

        mainVertical.addView(contentArea)

        // Bottom Navigation Bar
        val navBar = createBottomNav()
        mainVertical.addView(navBar)

        rootContainer.addView(mainVertical)
        setContentView(rootContainer)

        switchTab("home")
    }

    private fun createHeader(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(40, 40, 40, 20)
            gravity = Gravity.CENTER_VERTICAL

            // App Icon Image View
            val iconView = ImageView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(96, 96).apply {
                    marginEnd = 24
                }
                setImageResource(R.drawable.ic_launcher)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = createRoundedDrawable("#1E293B", "#38BDF8", 24f)
                clipToOutline = true
            }
            addView(iconView)

            val titleLayout = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val title = TextView(this@MainActivity).apply {
                text = "RIDER ASSISTANT"
                textSize = 20f
                setTextColor(Color.parseColor("#38BDF8"))
                setTypeface(null, Typeface.BOLD)
            }
            titleLayout.addView(title)

            val subtitle = TextView(this@MainActivity).apply {
                text = "VOICE GESTURE • BLUETOOTH"
                textSize = 10f
                setTextColor(Color.parseColor("#94A3B8"))
                setPadding(0, 4, 0, 0)
            }
            titleLayout.addView(subtitle)

            addView(titleLayout)

            // Accessibility permission badge
            accessibilityStatusBadge = TextView(this@MainActivity).apply {
                textSize = 11f
                setPadding(20, 10, 20, 10)
                background = createRoundedDrawable("#1E293B", "#334155", 20f)
                setOnClickListener {
                    openAccessibilitySettings()
                }
            }
            addView(accessibilityStatusBadge)
        }
    }

    private fun createHomeDashboard(): LinearLayout {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 32)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Section Title: Select Assistant Name
        val selectorTitle = TextView(this).apply {
            text = "SELECT ASSISTANT NAME"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 14)
        }
        layout.addView(selectorTitle)

        // Assistant Buttons: 1. RIDER, 2. BUDDY
        val selectorLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        riderButton = Button(this).apply {
            text = "RIDER\n(Swipe Up)"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 16
            }
            setOnClickListener {
                selectAssistant("RIDER")
            }
        }
        selectorLayout.addView(riderButton)

        buddyButton = Button(this).apply {
            text = "BUDDY\n(Swipe Up)"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                selectAssistant("BUDDY")
            }
        }
        selectorLayout.addView(buddyButton)
        layout.addView(selectorLayout)

        updateAssistantButtonStyles()

        // Center Space / Mic Toggle Button
        val centerSpacing = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        micToggleButton = Button(this).apply {
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(320, 320).apply {
                gravity = Gravity.CENTER
                topMargin = 32
                bottomMargin = 20
            }
            setOnClickListener {
                toggleBackgroundVoiceService()
            }
        }
        centerSpacing.addView(micToggleButton)

        micStatusText = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#FFFFFF"))
            gravity = Gravity.CENTER
            text = "Background Voice: Disabled"
            setPadding(0, 6, 0, 6)
        }
        centerSpacing.addView(micStatusText)

        val wakeDescription = TextView(this).apply {
            text = "Say 'Rider' or 'Buddy' anytime (even when other apps like Instagram/Shorts are open) to swipe up at the screen center."
            textSize = 11f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(20, 6, 20, 0)
        }
        centerSpacing.addView(wakeDescription)

        layout.addView(centerSpacing)

        // Instant Test Swipe Button
        val testSwipeBtn = Button(this).apply {
            text = "Test Center Swipe Up"
            background = createRoundedDrawable("#1E293B", "#38BDF8", 16f)
            setTextColor(Color.parseColor("#38BDF8"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
            }
            setOnClickListener {
                triggerManualSwipe()
            }
        }
        layout.addView(testSwipeBtn)

        scroll.addView(layout)
        return layout
    }

    private fun createBluetoothDashboard(): LinearLayout {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        bluetoothStatusLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 32)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        renderBluetoothContent()

        scroll.addView(bluetoothStatusLayout)
        return bluetoothStatusLayout
    }

    private fun renderBluetoothContent() {
        bluetoothStatusLayout.removeAllViews()

        if (!earbudsState.connected) {
            // NOT CONNECTED VIEW
            val centerWrap = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }

            // Case Icon representation
            val caseIcon = TextView(this).apply {
                text = "🔋\n[CASE]"
                textSize = 34f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#94A3B8"))
                setPadding(36, 36, 36, 36)
                background = createRoundedDrawable("#101827", "#334155", 32f)
                layoutParams = LinearLayout.LayoutParams(240, 240).apply {
                    gravity = Gravity.CENTER
                }
            }
            centerWrap.addView(caseIcon)

            // Small "Not Connected" text
            val notConnectedText = TextView(this).apply {
                text = "Not Connected"
                textSize = 15f
                setTextColor(Color.parseColor("#94A3B8"))
                setPadding(0, 20, 0, 8)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            centerWrap.addView(notConnectedText)

            val scanBtn = Button(this).apply {
                text = "Scan / Connect Earbuds"
                background = createRoundedDrawable("#0284C7", "#38BDF8", 16f)
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 20
                }
                setPadding(44, 20, 44, 20)
                setOnClickListener {
                    simulateBluetoothConnection()
                }
            }
            centerWrap.addView(scanBtn)

            bluetoothStatusLayout.addView(centerWrap)

        } else {
            // CONNECTED VIEW
            val devName = TextView(this).apply {
                text = earbudsState.name
                textSize = 22f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, 14, 0, 4)
            }
            bluetoothStatusLayout.addView(devName)

            val devAddress = TextView(this).apply {
                text = "Connected (${earbudsState.address})"
                textSize = 11f
                setTextColor(Color.parseColor("#38BDF8"))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 28)
            }
            bluetoothStatusLayout.addView(devAddress)

            // 3 Columns: Left Earbud, Case, Right Earbud
            val earbudsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            // 1. LEFT EARBUD
            val leftCard = createEarbudCard("LEFT (L)", "🎧", "${earbudsState.leftBattery}%")
            earbudsRow.addView(leftCard)

            // 2. CASE
            val caseCard = createEarbudCard("CASE", "🔋", "${earbudsState.caseBattery}%")
            earbudsRow.addView(caseCard)

            // 3. RIGHT EARBUD
            val rightCard = createEarbudCard("RIGHT (R)", "🎧", "${earbudsState.rightBattery}%")
            earbudsRow.addView(rightCard)

            bluetoothStatusLayout.addView(earbudsRow)

            // Refresh & Disconnect Buttons
            val btnRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 40
                }
            }

            val refreshBtn = Button(this).apply {
                text = "Refresh Battery"
                background = createRoundedDrawable("#1E293B", "#38BDF8", 16f)
                setTextColor(Color.parseColor("#38BDF8"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = 16
                }
                setOnClickListener {
                    refreshBluetoothData()
                    Toast.makeText(this@MainActivity, "Battery levels updated", Toast.LENGTH_SHORT).show()
                }
            }
            btnRow.addView(refreshBtn)

            val disconnectBtn = Button(this).apply {
                text = "Disconnect"
                background = createRoundedDrawable("#3B1D28", "#F43F5E", 16f)
                setTextColor(Color.parseColor("#F43F5E"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    earbudsState = EarbudsState(false, "", 0, 0, 0, "")
                    renderBluetoothContent()
                    Toast.makeText(this@MainActivity, "Earbuds disconnected", Toast.LENGTH_SHORT).show()
                }
            }
            btnRow.addView(disconnectBtn)

            bluetoothStatusLayout.addView(btnRow)
        }
    }

    private fun createEarbudCard(label: String, iconStr: String, batteryStr: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(12, 20, 12, 20)
            background = createRoundedDrawable("#101827", "#334155", 20f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 6
                marginEnd = 6
            }

            val lbl = TextView(this@MainActivity).apply {
                text = label
                textSize = 10f
                setTextColor(Color.parseColor("#38BDF8"))
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            addView(lbl)

            val icon = TextView(this@MainActivity).apply {
                text = iconStr
                textSize = 28f
                gravity = Gravity.CENTER
                setPadding(0, 12, 0, 12)
            }
            addView(icon)

            val bat = TextView(this@MainActivity).apply {
                text = batteryStr
                textSize = 15f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            addView(bat)
        }
    }

    private fun createBottomNav(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(28, 14, 28, 20)
            background = createRoundedDrawable("#0F172A", "#1E293B", 0f)

            val homeTab = Button(this@MainActivity).apply {
                text = "ASSISTANT"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = 16
                }
                setOnClickListener { switchTab("home") }
            }
            addView(homeTab)

            val btTab = Button(this@MainActivity).apply {
                text = "BLUETOOTH"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { switchTab("bluetooth") }
            }
            addView(btTab)
        }
    }

    private fun switchTab(tab: String) {
        currentTab = tab
        if (tab == "home") {
            homeDashboard.visibility = View.VISIBLE
            bluetoothDashboard.visibility = View.GONE
        } else {
            homeDashboard.visibility = View.GONE
            bluetoothDashboard.visibility = View.VISIBLE
            renderBluetoothContent()
        }
    }

    private fun selectAssistant(name: String) {
        selectedAssistant = name
        updateAssistantButtonStyles()

        if (!RiderAccessibilityService.isServiceRunning) {
            Toast.makeText(this, "Enable Accessibility Permission to allow swipe gestures", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
        } else {
            Toast.makeText(this, "Assistant set to $name", Toast.LENGTH_SHORT).show()
        }

        if (isListeningServiceActive) {
            VoiceWakeWordService.start(this, selectedAssistant)
        }
    }

    private fun updateAssistantButtonStyles() {
        if (selectedAssistant == "RIDER") {
            riderButton.background = createRoundedDrawable("#0284C7", "#38BDF8", 16f)
            riderButton.setTextColor(Color.WHITE)
            buddyButton.background = createRoundedDrawable("#1E293B", "#334155", 16f)
            buddyButton.setTextColor(Color.parseColor("#94A3B8"))
        } else {
            buddyButton.background = createRoundedDrawable("#0284C7", "#38BDF8", 16f)
            buddyButton.setTextColor(Color.WHITE)
            riderButton.background = createRoundedDrawable("#1E293B", "#334155", 16f)
            riderButton.setTextColor(Color.parseColor("#94A3B8"))
        }
    }

    private fun toggleBackgroundVoiceService() {
        if (!RiderAccessibilityService.isServiceRunning) {
            Toast.makeText(this, "First enable RIDER in Accessibility Settings", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_LONG).show()
            requestRequiredPermissions()
            return
        }

        if (isListeningServiceActive) {
            VoiceWakeWordService.stop(this)
            isListeningServiceActive = false
            micStatusText.text = "Background Voice: Disabled"
            micToggleButton.text = "START\nLISTENING"
            micToggleButton.background = createRoundedDrawable("#1E293B", "#334155", 160f)
            micToggleButton.setTextColor(Color.WHITE)
            Toast.makeText(this, "Voice wake-word paused", Toast.LENGTH_SHORT).show()
        } else {
            VoiceWakeWordService.start(this, selectedAssistant)
            isListeningServiceActive = true
            micStatusText.text = "Listening for '$selectedAssistant' in background..."
            micToggleButton.text = "LISTENING\nACTIVE"
            micToggleButton.background = createRoundedDrawable("#0284C7", "#38BDF8", 160f)
            micToggleButton.setTextColor(Color.WHITE)
            Toast.makeText(this, "Listening for '$selectedAssistant' in background", Toast.LENGTH_SHORT).show()
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
            accessibilityStatusBadge.text = "Accessibility: ON"
            accessibilityStatusBadge.setTextColor(Color.parseColor("#34D399"))
            accessibilityStatusBadge.background = createRoundedDrawable("#064E3B", "#059669", 20f)
        } else {
            accessibilityStatusBadge.text = "Accessibility: OFF"
            accessibilityStatusBadge.setTextColor(Color.parseColor("#FBBF24"))
            accessibilityStatusBadge.background = createRoundedDrawable("#78350F", "#D97706", 20f)
        }
    }

    private fun refreshBluetoothData() {
        try {
            val st = bluetoothHelper.getEarbudsState()
            if (st.connected) {
                earbudsState = st
            }
            if (currentTab == "bluetooth") {
                renderBluetoothContent()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh bluetooth data: ${e.message}")
        }
    }

    private fun simulateBluetoothConnection() {
        earbudsState = EarbudsState(
            connected = true,
            name = "Rider AirBuds Pro",
            leftBattery = 85,
            caseBattery = 92,
            rightBattery = 88,
            address = "4A:26:9F:8B:12:00"
        )
        renderBluetoothContent()
        Toast.makeText(this, "Connected to Rider AirBuds Pro", Toast.LENGTH_SHORT).show()
    }

    private fun createRoundedDrawable(fillHex: String, strokeHex: String, cornerRadius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(fillHex))
            setStroke(3, Color.parseColor(strokeHex))
            this.cornerRadius = cornerRadius
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 1001
    }
}
