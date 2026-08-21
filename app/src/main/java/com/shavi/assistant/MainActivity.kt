package com.shavi.assistant

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var statusText: TextView
    private lateinit var statusIcon: View
    private lateinit var wakeWordText: TextView
    private lateinit var apiKeyInput: EditText
    private lateinit var toggleButton: Button
    private lateinit var responseText: TextView
    private lateinit var responseContainer: LinearLayout
    private lateinit var loadingProgress: ProgressBar
    private lateinit var commandHistory: TextView
    private lateinit var toggleVisibility: ImageButton
    private lateinit var clearHistoryBtn: Button
    private lateinit var historyScrollView: ScrollView

    // Variables
    private var isServiceRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private var responseCount = 0

    // Broadcast Receiver for service updates
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "UPDATE_STATUS" -> {
                    val status = intent.getStringExtra("status") ?: ""
                    updateStatusUI(status)
                }
                "UPDATE_RESPONSE" -> {
                    val response = intent.getStringExtra("response") ?: ""
                    showResponse(response)
                }
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        const val ACTION_UPDATE_STATUS = "UPDATE_STATUS"
        const val ACTION_UPDATE_RESPONSE = "UPDATE_RESPONSE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupUI()
        loadSettings()
        checkPermissions()
        setupListeners()
        registerReceivers()

        // Check if service is already running
        isServiceRunning = VoiceAssistantService.Companion.isRunning
        updateUI()

        // Show welcome message
        showWelcomeMessage()
    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        statusIcon = findViewById(R.id.statusIcon)
        wakeWordText = findViewById(R.id.wakeWordText)
        apiKeyInput = findViewById(R.id.apiKeyInput)
        toggleButton = findViewById(R.id.toggleButton)
        responseText = findViewById(R.id.responseText)
        responseContainer = findViewById(R.id.responseContainer)
        loadingProgress = findViewById(R.id.loadingProgress)
        commandHistory = findViewById(R.id.commandHistory)
        toggleVisibility = findViewById(R.id.toggleVisibility)
        clearHistoryBtn = findViewById(R.id.clearHistoryBtn)
        historyScrollView = findViewById(R.id.historyScrollView)
    }

    private fun setupUI() {
        // Dark theme
        window.decorView.apply {
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
        }

        // API Key input with visibility toggle
        apiKeyInput.transformationMethod = PasswordTransformationMethod.getInstance()
        apiKeyInput.setHintTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        
        // Toggle password visibility
        toggleVisibility.setOnClickListener {
            if (apiKeyInput.transformationMethod == PasswordTransformationMethod.getInstance()) {
                apiKeyInput.transformationMethod = null
                toggleVisibility.setImageResource(android.R.drawable.ic_menu_view)
                Toast.makeText(this, "API Key visible", Toast.LENGTH_SHORT).show()
            } else {
                apiKeyInput.transformationMethod = PasswordTransformationMethod.getInstance()
                toggleVisibility.setImageResource(android.R.drawable.ic_menu_manage)
                Toast.makeText(this, "API Key hidden", Toast.LENGTH_SHORT).show()
            }
            apiKeyInput.setSelection(apiKeyInput.text.length)
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("shavi_prefs", MODE_PRIVATE)
        val savedKey = prefs.getString("gemini_api_key", "")
        apiKeyInput.setText(savedKey)
        
        // Load command history
        val history = prefs.getString("command_history", "")
        if (history.isNotEmpty()) {
            commandHistory.text = history
            commandHistory.visibility = View.VISIBLE
            historyScrollView.visibility = View.VISIBLE
        }
    }

    private fun checkPermissions() {
        val permissions = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE
        )

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun setupListeners() {
        // Toggle button - Start/Stop Service
        toggleButton.setOnClickListener {
            if (isServiceRunning) {
                stopService()
            } else {
                startService()
            }
        }

        // Save API key on focus change
        apiKeyInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                saveApiKey()
            }
        }

        // Save API key on text change (with delay)
        apiKeyInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                // Auto-save after typing stops
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({
                    if (apiKeyInput.text.toString().isNotEmpty()) {
                        saveApiKey()
                    }
                }, 1000)
            }
        })

        // Clear history button
        clearHistoryBtn.setOnClickListener {
            commandHistory.text = ""
            commandHistory.visibility = View.GONE
            historyScrollView.visibility = View.GONE
            val prefs = getSharedPreferences("shavi_prefs", MODE_PRIVATE)
            prefs.edit().remove("command_history").apply()
            responseCount = 0
            Toast.makeText(this, "🗑️ History cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(ACTION_UPDATE_STATUS)
            addAction(ACTION_UPDATE_RESPONSE)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(statusReceiver, filter)
    }

    private fun startService() {
        val apiKey = apiKeyInput.text.toString().trim()
        
        // Validate API Key
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "⚠️ कृपया Gemini API Key डालें", Toast.LENGTH_LONG).show()
            apiKeyInput.requestFocus()
            apiKeyInput.error = "API Key required"
            return
        }

        if (apiKey.length < 20) {
            Toast.makeText(this, "⚠️ Invalid API Key (too short)", Toast.LENGTH_LONG).show()
            apiKeyInput.error = "Invalid API Key"
            return
        }

        if (!apiKey.startsWith("AIza")) {
            Toast.makeText(this, "⚠️ Invalid API Key format", Toast.LENGTH_LONG).show()
            apiKeyInput.error = "Must start with 'AIza'"
            return
        }

        saveApiKey()

        try {
            val intent = Intent(this, VoiceAssistantService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            isServiceRunning = true
            updateUI()
            
            Toast.makeText(this, "🚀 SHAVi शुरू हो गई!", Toast.LENGTH_SHORT).show()
            
            // Show startup message
            responseContainer.visibility = View.VISIBLE
            responseText.text = "🔊 SHAVi सुन रही है... 'Hey SHAVi' बोलें"
            responseText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))

        } catch (e: Exception) {
            Toast.makeText(this, "❌ Error: ${e.message}", Toast.LENGTH_LONG).show()
            isServiceRunning = false
            updateUI()
        }
    }

    private fun stopService() {
        try {
            val intent = Intent(this, VoiceAssistantService::class.java)
            stopService(intent)
            
            isServiceRunning = false
            updateUI()
            
            Toast.makeText(this, "⏹ SHAVi बंद हो गई", Toast.LENGTH_SHORT).show()
            
            responseContainer.visibility = View.GONE
            loadingProgress.visibility = View.GONE

        } catch (e: Exception) {
            Toast.makeText(this, "❌ Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveApiKey() {
        val apiKey = apiKeyInput.text.toString().trim()
        if (apiKey.isNotEmpty()) {
            val prefs = getSharedPreferences("shavi_prefs", MODE_PRIVATE)
            prefs.edit().putString("gemini_api_key", apiKey).apply()
            
            // Update VoiceAssistantService's API key
            VoiceAssistantService.Companion.apiKey = apiKey
            
            // Show confirmation only if valid
            if (apiKey.length >= 20 && apiKey.startsWith("AIza")) {
                Toast.makeText(this, "✅ API Key saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI() {
        runOnUiThread {
            if (isServiceRunning) {
                toggleButton.text = "⏹ Stop SHAVi"
                toggleButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
                statusText.text = "🟢 ACTIVE"
                statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
                statusIcon.visibility = View.VISIBLE
                wakeWordText.text = "🔊 Listening for 'Hey SHAVi'..."
                wakeWordText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
                toggleButton.isEnabled = true
                
                // Animate status icon
                animateStatusIcon(true)
                
            } else {
                toggleButton.text = "🚀 Start SHAVi"
                toggleButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
                statusText.text = "⭕ INACTIVE"
                statusText.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                statusIcon.visibility = View.GONE
                wakeWordText.text = "⚪ Say 'Hey SHAVi' to activate"
                wakeWordText.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                toggleButton.isEnabled = true
                
                animateStatusIcon(false)
                loadingProgress.visibility = View.GONE
            }
        }
    }

    private fun updateStatusUI(status: String) {
        runOnUiThread {
            wakeWordText.text = status
            when {
                status.contains("सुन") || status.contains("Listening") -> {
                    wakeWordText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
                    statusIcon.visibility = View.VISIBLE
                    animateStatusIcon(true)
                }
                status.contains("प्रोसेस") || status.contains("Processing") -> {
                    wakeWordText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
                    loadingProgress.visibility = View.VISIBLE
                }
                status.contains("Error") || status.contains("⚠️") || status.contains("restart") -> {
                    wakeWordText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
                    loadingProgress.visibility = View.GONE
                    // Show error in response
                    showResponse("⚠️ $status")
                }
                status.contains("API key") -> {
                    wakeWordText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
                    showResponse("⚠️ $status")
                }
                else -> {
                    wakeWordText.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                    loadingProgress.visibility = View.GONE
                }
            }
        }
    }

    private fun showResponse(response: String) {
        runOnUiThread {
            responseContainer.visibility = View.VISIBLE
            responseText.text = "🗣️ $response"
            responseText.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            loadingProgress.visibility = View.GONE
            
            // Add to history if not error
            if (!response.contains("Error") && !response.contains("⚠️")) {
                addToHistory(response)
            }
            
            // Auto-hide after 5 seconds
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({
                responseContainer.visibility = View.GONE
            }, 6000)
        }
    }

    private fun addToHistory(response: String) {
        responseCount++
        val timestamp = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        val historyText = "[$timestamp] ${responseCount}. $response\n"
        commandHistory.append(historyText)
        commandHistory.visibility = View.VISIBLE
        historyScrollView.visibility = View.VISIBLE
        
        // Save history
        val prefs = getSharedPreferences("shavi_prefs", MODE_PRIVATE)
        prefs.edit().putString("command_history", commandHistory.text.toString()).apply()
        
        // Auto-scroll to bottom
        historyScrollView.post {
            historyScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun showWelcomeMessage() {
        val firstRun = getSharedPreferences("shavi_prefs", MODE_PRIVATE)
            .getBoolean("first_run", true)
        
        if (firstRun) {
            responseContainer.visibility = View.VISIBLE
            responseText.text = "👋 Welcome to SHAVi AI!\n\n1. Enter your Gemini API Key\n2. Click Start SHAVi\n3. Say 'Hey SHAVi' and command"
            responseText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
            responseText.textSize = 16f
            
            getSharedPreferences("shavi_prefs", MODE_PRIVATE)
                .edit().putBoolean("first_run", false).apply()
            
            handler.postDelayed({
                responseContainer.visibility = View.GONE
            }, 8000)
        }
    }

    private fun animateStatusIcon(active: Boolean) {
        if (active) {
            statusIcon.animate()
                .scaleX(1.3f)
                .scaleY(1.3f)
                .setDuration(500)
                .withEndAction {
                    statusIcon.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(500)
                        .start()
                }
                .start()
        } else {
            statusIcon.animate().scaleX(1f).scaleY(1f).setDuration(300).start()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Toast.makeText(this, "✅ All permissions granted", Toast.LENGTH_SHORT).show()
                if (apiKeyInput.text.isNotEmpty()) {
                    startService()
                }
            } else {
                val denied = permissions.filterIndexed { index, _ ->
                    grantResults[index] != PackageManager.PERMISSION_GRANTED
                }
                Toast.makeText(
                    this,
                    "⚠️ Permissions denied: ${denied.joinToString()}\n\nPlease grant permissions from Settings",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if service is still running
        isServiceRunning = VoiceAssistantService.Companion.isRunning
        updateUI()
        
        // Reload API key
        val prefs = getSharedPreferences("shavi_prefs", MODE_PRIVATE)
        val savedKey = prefs.getString("gemini_api_key", "")
        if (savedKey != apiKeyInput.text.toString()) {
            apiKeyInput.setText(savedKey)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver)
        } catch (e: Exception) {
            // Receiver already unregistered
        }
    }
}
