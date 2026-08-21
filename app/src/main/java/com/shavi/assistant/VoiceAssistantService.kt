package com.shavi.assistant

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class VoiceAssistantService : Service(), TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isWakeWordDetected = false
    private var isListening = false
    private var isSpeaking = false
    private lateinit var wakeLock: PowerManager.WakeLock
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    companion object {
        private const val TAG = "SHAViService"
        const val CHANNEL_ID = "shavi_channel"
        const val NOTIF_ID = 101
        var isRunning = false
        
        val WAKE_PHRASES = listOf(
            "hey shavi", "hey shiv", "he shavi", "hi shavi",
            "हाय शावी", "हे शावी", "शावी", "shavi"
        )
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 SHAVi Service Creating...")
        isRunning = true
        
        // Wake Lock for continuous operation
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SHAVi:WakeLock"
        ).apply { acquire() }
        
        // Initialize TTS
        tts = TextToSpeech(this, this)
        
        // Start Foreground
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("🟢 SHAVi सुन रही है..."))
        
        // Start listening
        startVoiceRecognition()
        
        Log.d(TAG, "✅ SHAVi Service Created Successfully")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.apply {
                language = Locale("hi", "IN")
                setPitch(1.5f)
                setSpeechRate(1.0f)
                Log.d(TAG, "✅ TTS Initialized Successfully")
                speak("नमस्ते! मैं SHAVi हूँ, आपकी निजी सहायक")
            }
        } else {
            Log.e(TAG, "❌ TTS Initialization Failed")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SHAVi Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "SHAVi is running in background"
                setShowBadge(false)
                enableLights(true)
                lightColor = Color.GREEN
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🤖 SHAVi AI")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification(text))
    }

    private fun startVoiceRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "❌ Speech Recognition not available")
            updateNotification("⚠️ Speech recognition not available")
            return
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "🎤 Ready for speech")
                    isListening = true
                    updateStatus("🎤 सुन रही हूँ...")
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "🎤 Beginning of speech")
                    updateStatus("🎤 सुन रही हूँ...")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Voice activity indicator - can be used for UI
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "🎤 End of speech")
                    updateStatus("⏳ प्रोसेस कर रही हूँ...")
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val heard = matches?.firstOrNull()?.lowercase(Locale.getDefault())?.trim() ?: ""
                    
                    if (heard.isNotEmpty()) {
                        Log.d(TAG, "🎯 Heard: $heard")
                        processVoiceCommand(heard)
                    } else {
                        Log.d(TAG, "⚠️ No speech detected")
                        restartListening()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    // Can be used for real-time transcription
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onError(error: Int) {
                    isListening = false
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio error - check microphone"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission denied"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error - check internet"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No match found - please speak clearly"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Busy, retrying..."
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout - speak louder"
                        else -> "Error: $error"
                    }
                    
                    Log.e(TAG, "❌ Recognition Error: $errorMsg")
                    updateStatus("⚠️ $errorMsg")
                    
                    serviceScope.launch {
                        delay(1500)
                        restartListening()
                    }
                }
            })

            startListening()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing speech recognition", e)
            updateNotification("❌ Error: ${e.message}")
        }
    }

    private fun startListening() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
            }
            
            speechRecognizer?.startListening(intent)
            isListening = true
            updateStatus("🔊 'Hey SHAVi' बोलें...")
            Log.d(TAG, "👂 Listening started")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting listening", e)
            updateStatus("❌ Error: ${e.message}")
        }
    }

    private fun restartListening() {
        Log.d(TAG, "🔄 Restarting listening...")
        isWakeWordDetected = false
        isListening = false
        
        serviceScope.launch {
            delay(800)
            startListening()
        }
    }

    private fun updateStatus(text: String) {
        updateNotification(text)
        // Send broadcast to update UI
        val intent = Intent(MainActivity.ACTION_UPDATE_STATUS).apply {
            putExtra("status", text)
        }
        sendBroadcast(intent)
    }

    private fun processVoiceCommand(heard: String) {
        // Check if this is a wake word
        val isWakeWord = WAKE_PHRASES.any { heard.contains(it) }
        
        if (!isWakeWord && !isWakeWordDetected) {
            Log.d(TAG, "⏭️ No wake word, ignoring: $heard")
            restartListening()
            return
        }
        
        if (isWakeWord && !isWakeWordDetected) {
            isWakeWordDetected = true
            speak("जी बोलिए, मैं सुन रही हूँ")
            updateStatus("🎤 कमांड सुन रही हूँ...")
            restartListening()
            return
        }
        
        // Process the actual command
        isWakeWordDetected = false
        updateStatus("💭 प्रोसेस कर रही हूँ...")
        
        // Get API key
        val prefs = getSharedPreferences("shavi_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", null)
        
        if (apiKey.isNullOrBlank()) {
            speak("कृपया पहले Gemini API key सेट करें")
            updateStatus("⚠️ No API key found")
            restartListening()
            return
        }
        
        // Process with Gemini
        serviceScope.launch {
            try {
                val response = processWithGemini(heard, apiKey)
                speak(response)
                updateStatus("✅ $response")
                broadcastResponse(response)
                
                serviceScope.launch {
                    delay(3000)
                    restartListening()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error processing command", e)
                speak("क्षमा करें, कुछ गड़बड़ हो गई")
                updateStatus("❌ Error: ${e.message}")
                restartListening()
            }
        }
    }

    private suspend fun processWithGemini(command: String, apiKey: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=$apiKey"
                
                // Create system prompt for J.A.R.V.I.S. style
                val systemPrompt = """You are SHAVi, a J.A.R.V.I.S.-style AI assistant for an Android phone. 
You are smart, witty, and helpful. Keep responses short and conversational in Hindi/English mix.
You can help with:
- Time and date
- Opening apps
- Making calls
- Sending messages
- Web searches
- General questions
- Device controls

Respond naturally in Hinglish (Hindi+English). Be friendly but concise.
User said: $command"""
                
                val json = JSONObject().apply {
                    put("contents", listOf(
                        JSONObject().apply {
                            put("parts", listOf(
                                JSONObject().apply {
                                    put("text", systemPrompt)
                                }
                            ))
                        }
                    ))
                }
                
                val request = Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(MediaType.parse("application/json"), json.toString()))
                    .build()
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                
                if (!response.isSuccessful) {
                    Log.e(TAG, "❌ API Error: ${response.code}")
                    return@withContext getFallbackResponse(command)
                }
                
                val jsonResponse = JSONObject(responseBody ?: "{}")
                val text = jsonResponse.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                
                Log.d(TAG, "✅ Gemini Response: $text")
                text
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Gemini API Error", e)
                getFallbackResponse(command)
            }
        }
    }

    private fun getFallbackResponse(command: String): String {
        return when {
            command.contains("time") || command.contains("समय") -> {
                val time = SimpleDateFormat("hh:mm a", Locale("hi")).format(Date())
                "अभी समय $time बज रहा है"
            }
            command.contains("date") || command.contains("तारीख") -> {
                val date = SimpleDateFormat("dd MMMM yyyy", Locale("hi")).format(Date())
                "आज $date है"
            }
            command.contains("hello") || command.contains("नमस्ते") -> {
                "नमस्ते! मैं SHAVi हूँ, आपकी कैसे मदद कर सकती हूँ?"
            }
            else -> {
                "मुझे समझ नहीं आया। कृपया फिर से बोलें या API key चेक करें।"
            }
        }
    }

    private fun speak(text: String) {
        isSpeaking = true
        Log.d(TAG, "🗣️ Speaking: $text")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "shavi_utterance")
        
        // Small delay to ensure speech completes
        serviceScope.launch {
            delay(text.length * 50L + 1000)
            isSpeaking = false
        }
    }

    private fun broadcastResponse(text: String) {
        val intent = Intent(MainActivity.ACTION_UPDATE_RESPONSE).apply {
            putExtra("response", text)
        }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        Log.d(TAG, "🛑 SHAVi Service Destroying...")
        isRunning = false
        isListening = false
        
        super.onDestroy()
        speechRecognizer?.destroy()
        tts?.shutdown()
        wakeLock.release()
        serviceScope.cancel()
        
        Log.d(TAG, "✅ SHAVi Service Destroyed")
    }
}
