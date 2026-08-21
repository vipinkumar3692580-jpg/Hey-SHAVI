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

    private var isServiceRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private var responseCount = 0

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

        isServiceRunning = VoiceAssistantService.isRunning
        updateUI()
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
        window.decorView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.black))
        apiKeyInput.transformationMethod = PasswordTransformationMethod.getInstance()
        apiKeyInput.setHintTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        
        toggleVisibility.setOnClickListener {
            if (apiKeyInput.transformationMethod == PasswordTransformationMethod.getInstance()) {
                apiKeyInput.transformationMethod = null
                toggleVisibility.setImageResource(android.R.drawable.ic_menu_view)
            } else {
                apiKeyInput.transformationMethod = PasswordTransformationMethod.getInstance()
                toggleVisibility.setImageResource(android.R.drawable.ic_menu_manage)
            }
            apiKeyInput.setSelection(apiKeyInput.text.length)
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("shavi_prefs", MODE_PRIVATE)
        val savedKey = prefs.getString("gemini_api_key", "")
        apiKeyInput.setText(savedKey)
        
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
        toggleButton.setOnClickListener {
            if (isServiceRunning) {
                stopService()
            } else {
                startService()
            }
        }

        apiKeyInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                saveApiKey()
            }
        }

        clearHistoryBtn.setOnClickListener {
            commandHistory.text = ""
            commandHistory.visibility = View.GONE
            historyScrollView.visibility = View.GONE
            val prefs = getSharedPreferences("shavi_prefs", MODE_PRIVATE)
            prefs.edit().remove("command_history").apply()
            responseCount = 0
            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
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
        
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "⚠️ Gemini API Key डालें", Toast.LENGTH_LONG).show()
            apiKeyInput.requestFocus()
            apiKeyInput.error = "API Key required"
            return
        }

        if (apiKey.length < 20) {
            Toast.makeText(this, "⚠️ Invalid API Key", Toast.LENGTH_LONG).show()
            apiKeyInput.error = "Invalid API Key"
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
            Toast.makeText(this, "🚀 SHAVi Started!", Toast.LENGTH_SHORT).show()

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
            Toast.makeText(this, "⏹ SHAVi Stopped", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveApiKey() {
        val apiKey = apiKeyInput.text.toString().trim()
        if (apiKey.isNotEmpty()) {
            val prefs = getSharedPreferences("shavi_prefs", MODE_PRIVATE)
            prefs.edit().putString("gemini_api_key", apiKey).apply()
            VoiceAssistantService.apiKey = apiKey
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
                animateStatusIcon(true)
            } else {
                toggleButton.text = "🚀 Start SHAVi"
                toggleButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
                statusText.text = "⭕ INACTIVE"
                statusText.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                statusIcon.visibility = View.GONE
                wakeWordText.text = "⚪ Say 'Hey SHAVi' to activate"
                wakeWordText.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                animateStatusIcon(false)
                loadingProgress.visibility = View.GONE
            }
        }
    }

    private fun updateStatusUI(status: String) {
        runOnUiThread {
            wakeWordText.text = status
            when {
                status.contains("सुन") -> {
                    wakeWordText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
                    statusIcon.visibility = View.VISIBLE
                }
                status.contains("प्रोसेस") -> {
                    wakeWordText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
                    loadingProgress.visibility = View.VISIBLE
                }
                status.contains("Error") -> {
                    wakeWordText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
                    loadingProgress.visibility = View.GONE
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
            addToHistory(response)
            
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({
                responseContainer.visibility = View.GONE
            }, 5000)
        }
    }

    private fun addToHistory(response: String) {
        responseCount++
        val timestamp = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        val historyText = "[$timestamp] ${responseCount}. $response\n"
        commandHistory.append(historyText)
        commandHistory.visibility = View.VISIBLE
        historyScrollView.visibility = View.VISIBLE
        
        val prefs = getSharedPreferences("shavi_prefs", MODE_PRIVATE)
        prefs.edit().putString("command_history", commandHistory.text.toString()).apply()
        
        historyScrollView.post {
            historyScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun showWelcomeMessage() {
        val firstRun = getSharedPreferences("shavi_prefs", MODE_PRIVATE)
            .getBoolean("first_run", true)
        
        if (firstRun) {
            responseContainer.visibility = View.VISIBLE
            responseText.text = "👋 Welcome to SHAVi AI!\n\n1. Enter Gemini API Key\n2. Click Start\n3. Say 'Hey SHAVi'"
            responseText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
            
            getSharedPreferences("shavi_prefs", MODE_PRIVATE)
                .edit().putBoolean("first_run", false).apply()
            
            handler.postDelayed({
                responseContainer.visibility = View.GONE
            }, 6000)
        }
    }

    private fun animateStatusIcon(active: Boolean) {
        if (active) {
            statusIcon.animate()
                .scaleX(1.3f).scaleY(1.3f)
                .setDuration(500)
                .withEndAction {
                    statusIcon.animate().scaleX(1f).scaleY(1f).setDuration(500).start()
                }
                .start()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "✅ All permissions granted", Toast.LENGTH_SHORT).show()
                if (apiKeyInput.text.isNotEmpty()) startService()
            } else {
                Toast.makeText(this, "⚠️ Permissions required", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isServiceRunning = VoiceAssistantService.isRunning
        updateUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver)
        } catch (e: Exception) {}
    }
}
