package com.rider.assistant

import android.Manifest
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
    private var remoteMode = "BOTH" // BOTH, LEFT, RIGHT
    private var audioOutputMode = "STEREO" // STEREO, LEFT, RIGHT
    private var currentTab = "home"

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
    private lateinit var testSwipeBtn: Button
    private lateinit var accessibilityStatusBadge: TextView

    // Bottom Navigation Buttons
    private lateinit var homeNavButton: Button
    private lateinit var bluetoothNavButton: Button

    // Bluetooth Helper
    private val bluetoothHelper by lazy { BluetoothHelper(this) }
    private var earbudsState: EarbudsState = EarbudsState(true, "Rider AirBuds Pro", 85, 95, 90, "4A:26:9F:8B:12:00")

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

        // 1. Clean Header (Ribbon border removed)
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

            // App Character Icon
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

            // Title: "RIDER" (no extra subtitles here)
            val title = TextView(this@MainActivity).apply {
                text = "RIDER"
                textSize = 22f
                setTextColor(Color.parseColor("#38BDF8"))
                setTypeface(null, Typeface.BOLD)
                letterSpacing = 0.08f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(title)

            // Accessibility Status Badge Button
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
            setPadding(40, 20, 40, 20)
            gravity = Gravity.CENTER
        }

        // 1. Small Round Play Start Button (No bulky text)
        // "lets start te fkt ek lahans button asel tyavar kahi text nko"
        playStartBtn = Button(this).apply {
            text = "▶"
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#020617"))
            background = createRoundedDrawable("#38BDF8", "#7DD3FC", 120f)
            layoutParams = LinearLayout.LayoutParams(220, 220).apply {
                gravity = Gravity.CENTER
                topMargin = 40
                bottomMargin = 48
            }
            setOnClickListener { handleLetsStart() }
        }
        layout.addView(playStartBtn)

        // 2. Single Combined Button for "Say Rider" / "Say Buddy"
        // "fkt he button naavach aahe na te asu de tyat asel say riderr aani budy tyavar click kel ki donhi madheun change karaych option yeil oky"
        toggleWakeWordBtn = Button(this).apply {
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = createRoundedDrawable("#101827", "#38BDF8", 20f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
            setPadding(32, 28, 32, 28)
            setOnClickListener {
                toggleWakeWord()
            }
        }
        layout.addView(toggleWakeWordBtn)
        updateWakeWordButtonText()

        // 3. Simple Test Swipe Trigger
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
            setPadding(32, 16, 32, 16)
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

        // Start background service
        VoiceWakeWordService.start(this, selectedAssistant)
        Toast.makeText(this, "RIDER Active! Say \"$selectedAssistant\" to swipe.", Toast.LENGTH_LONG).show()

        // Move to home screen / background so user can use other apps immediately
        // "tyavar click kel ki app chya baher kadhun deil oky kalal na"
        moveTaskToBack(true)
    }

    private fun toggleWakeWord() {
        selectedAssistant = if (selectedAssistant == "RIDER") "BUDDY" else "RIDER"
        updateWakeWordButtonText()
        updateTestButtonText()
        Toast.makeText(this, "Switched to Say $selectedAssistant", Toast.LENGTH_SHORT).show()

        if (VoiceWakeWordService.isServiceRunning) {
            VoiceWakeWordService.start(this, selectedAssistant)
        }
    }

    private fun updateWakeWordButtonText() {
        val alt = if (selectedAssistant == "RIDER") "Buddy" else "Rider"
        toggleWakeWordBtn.text = "Say $selectedAssistant  (Tap to switch to $alt)"
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
            text = earbudsState.name
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 2)
        }
        container.addView(devTitle)

        val devSub = TextView(this).apply {
            text = "Earbuds Connected • Touch Sensors Active"
            textSize = 10f
            setTextColor(Color.parseColor("#38BDF8"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        container.addView(devSub)

        // Earbuds Touch Sensors Row
        // "tyache sensores aahet na tyavar click kel ki swip whav oky"
        val earbudsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }

        // Left Earbud Sensor Card
        val leftCard = createEarbudSensorCard("LEFT (L)", "🎧", "${earbudsState.leftBattery}%") {
            triggerManualSwipe()
            Toast.makeText(this@MainActivity, "Left Earbud Touch: Swiped Up!", Toast.LENGTH_SHORT).show()
        }
        earbudsRow.addView(leftCard)

        // Case Card
        val caseCard = createEarbudCaseCard("CASE", "🔋", "${earbudsState.caseBattery}%")
        earbudsRow.addView(caseCard)

        // Right Earbud Sensor Card
        val rightCard = createEarbudSensorCard("RIGHT (R)", "🎧", "${earbudsState.rightBattery}%") {
            triggerManualSwipe()
            Toast.makeText(this@MainActivity, "Right Earbud Touch: Swiped Up!", Toast.LENGTH_SHORT).show()
        }
        earbudsRow.addView(rightCard)

        container.addView(earbudsRow)

        // Configuration 1: Gesture Remote Earbud Role ("konta remote sarkh kaam karaav")
        val remoteCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            background = createRoundedDrawable("#101827", "#1E293B", 14f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12
            }
        }

        val remoteTitle = TextView(this).apply {
            text = "EARBUD GESTURE REMOTE"
            textSize = 9f
            setTextColor(Color.parseColor("#94A3B8"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }
        remoteCard.addView(remoteTitle)

        val remoteBtnsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        listOf("BOTH" to "Both Buds", "LEFT" to "Left Only", "RIGHT" to "Right Only").forEach { (key, label) ->
            val btn = Button(this).apply {
                text = label
                textSize = 9f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = 6
                }
                background = if (remoteMode == key) {
                    createRoundedDrawable("#0284C7", "#38BDF8", 10f)
                } else {
                    createRoundedDrawable("#1E293B", "#334155", 10f)
                }
                setTextColor(if (remoteMode == key) Color.WHITE else Color.parseColor("#94A3B8"))
                setOnClickListener {
                    remoteMode = key
                    renderBluetoothContent()
                    Toast.makeText(this@MainActivity, "Remote mode: $label", Toast.LENGTH_SHORT).show()
                }
            }
            remoteBtnsRow.addView(btn)
        }
        remoteCard.addView(remoteBtnsRow)
        container.addView(remoteCard)

        // Configuration 2: Audio Routing ("kontya birds madhe aavaj yaava")
        val audioCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            background = createRoundedDrawable("#101827", "#1E293B", 14f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val audioTitle = TextView(this).apply {
            text = "AUDIO & PROMPT OUTPUT"
            textSize = 9f
            setTextColor(Color.parseColor("#94A3B8"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }
        audioCard.addView(audioTitle)

        val audioBtnsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        listOf("STEREO" to "Stereo", "LEFT" to "Left Earbud", "RIGHT" to "Right Earbud").forEach { (key, label) ->
            val btn = Button(this).apply {
                text = label
                textSize = 9f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = 6
                }
                background = if (audioOutputMode == key) {
                    createRoundedDrawable("#0284C7", "#38BDF8", 10f)
                } else {
                    createRoundedDrawable("#1E293B", "#334155", 10f)
                }
                setTextColor(if (audioOutputMode == key) Color.WHITE else Color.parseColor("#94A3B8"))
                setOnClickListener {
                    audioOutputMode = key
                    renderBluetoothContent()
                    Toast.makeText(this@MainActivity, "Audio output: $label", Toast.LENGTH_SHORT).show()
                }
            }
            audioBtnsRow.addView(btn)
        }
        audioCard.addView(audioBtnsRow)
        container.addView(audioCard)
    }

    private fun createEarbudSensorCard(label: String, iconStr: String, batteryStr: String, onClickSensor: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(6, 10, 6, 10)
            background = createRoundedDrawable("#101827", "#334155", 14f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 3
                marginEnd = 3
            }

            val lbl = TextView(this@MainActivity).apply {
                text = label
                textSize = 8.5f
                setTextColor(Color.parseColor("#38BDF8"))
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            addView(lbl)

            val sensorBtn = Button(this@MainActivity).apply {
                text = "Sensor"
                textSize = 8.5f
                setTextColor(Color.WHITE)
                background = createRoundedDrawable("#0284C7", "#38BDF8", 8f)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 6
                    bottomMargin = 6
                }
                setPadding(12, 6, 12, 6)
                setOnClickListener { onClickSensor() }
            }
            addView(sensorBtn)

            val bat = TextView(this@MainActivity).apply {
                text = batteryStr
                textSize = 12f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            addView(bat)
        }
    }

    private fun createEarbudCaseCard(label: String, iconStr: String, batteryStr: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(6, 10, 6, 10)
            background = createRoundedDrawable("#101827", "#1E293B", 14f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 3
                marginEnd = 3
            }

            val lbl = TextView(this@MainActivity).apply {
                text = label
                textSize = 8.5f
                setTextColor(Color.parseColor("#94A3B8"))
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            addView(lbl)

            val icon = TextView(this@MainActivity).apply {
                text = iconStr
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(0, 8, 0, 8)
            }
            addView(icon)

            val bat = TextView(this@MainActivity).apply {
                text = batteryStr
                textSize = 12f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
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

            // Reduced height ribbon buttons
            // "khlchi rebbon thodi lahan kar"
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

            // "created by rider" at the very bottom
            // "hw naav sarvat khali"
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
            if (st.connected) {
                earbudsState = st
            }
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
