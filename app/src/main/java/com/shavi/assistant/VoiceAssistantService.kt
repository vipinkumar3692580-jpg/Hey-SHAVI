package com.shavi.assistant

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class VoiceAssistantService : Service(), TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var isWakeWordDetected = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    companion object {
        private const val TAG = "SHAVi"
        const val CHANNEL_ID = "shavi_channel"
        const val NOTIF_ID = 101
        val WAKE_PHRASES = listOf(
            "hey shavi", "hey shiv", "he shavi", "hi shavi",
            "हाय शावी", "हे शावी", "शावी", "shavi"
        )
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 Service onCreate")
        
        // ✅ TTS Initialize
        tts = TextToSpeech(this, this)
        
        // ✅ Notification
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("🔄 SHAVi starting..."))
        
        // ✅ Speech Recognition
        startVoiceRecognition()
        
        // ✅ Test TTS after 2 seconds
        serviceScope.launch {
            delay(2000)
            if (!isTtsReady) {
                Log.e(TAG, "❌ TTS not ready after 2 seconds")
            }
        }
    }

    override fun onInit(status: Int) {
        Log.d(TAG, "TTS onInit: $status")
        if (status == TextToSpeech.SUCCESS) {
            tts?.apply {
                language = Locale("hi", "IN")
                setPitch(1.5f)
                setSpeechRate(1.0f)
                isTtsReady = true
                Log.d(TAG, "✅ TTS initialized successfully")
                speak("नमस्ते! मैं SHAVi हूँ")  // ✅ Test speech
                updateNotification("🟢 SHAVi ready")
            }
        } else {
            Log.e(TAG, "❌ TTS initialization failed: $status")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SHAVi Assistant",
                NotificationManager.IMPORTANCE_HIGH  // ✅ HIGH importance
            ).apply {
                description = "SHAVi is running"
                enableVibration(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🤖 SHAVi AI")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification(text))
    }

    private fun startVoiceRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "❌ Speech recognition not available")
            updateNotification("⚠️ Speech recognition unavailable")
            return
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "🎤 Ready for speech")
                    updateNotification("🎤 सुन रही हूँ...")
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "🎤 Beginning of speech")
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "🎤 End of speech")
                    updateNotification("⏳ प्रोसेस कर रही हूँ...")
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val heard = matches?.firstOrNull()?.lowercase(Locale.getDefault())?.trim() ?: ""
                    
                    Log.d(TAG, "🎯 Heard: '$heard'")
                    
                    if (heard.isNotEmpty()) {
                        processCommand(heard)
                    } else {
                        Log.d(TAG, "⚠️ Empty result, restarting")
                        restartListening()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onError(error: Int) {
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission denied"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                        else -> "Error: $error"
                    }
                    Log.e(TAG, "❌ Recognition error: $errorMsg")
                    updateNotification("⚠️ $errorMsg")
                    
                    serviceScope.launch {
                        delay(2000)
                        restartListening()
                    }
                }
            })

            startListening()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing recognition", e)
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
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L)
            }
            
            speechRecognizer?.startListening(intent)
            Log.d(TAG, "👂 Listening started")
            updateNotification("🔊 'Hey SHAVi' बोलें")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting listening", e)
            updateNotification("❌ Start error: ${e.message}")
        }
    }

    private fun restartListening() {
        Log.d(TAG, "🔄 Restarting listening...")
        isWakeWordDetected = false
        
        serviceScope.launch {
            delay(1000)
            startListening()
        }
    }

    private fun processCommand(heard: String) {
        val isWakeWord = WAKE_PHRASES.any { heard.contains(it) }
        
        Log.d(TAG, "Processing: isWakeWord=$isWakeWord, detected=$isWakeWordDetected")
        
        if (!isWakeWord && !isWakeWordDetected) {
            Log.d(TAG, "⏭️ No wake word, ignoring")
            return
        }
        
        if (isWakeWord && !isWakeWordDetected) {
            isWakeWordDetected = true
            speak("जी बोलिए")  // ✅ Voice response
            updateNotification("🎤 कमांड सुन रही हूँ")
            restartListening()
            return
        }
        
        // ✅ Process command
        isWakeWordDetected = false
        updateNotification("💭 सोच रही हूँ...")
        
        // ✅ Get API Key
        val prefs = getSharedPreferences("shavi_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", null)
        
        Log.d(TAG, "API Key: ${apiKey?.take(10)}...")  // Debug log
        
        if (apiKey.isNullOrBlank()) {
            speak("कृपया Gemini API key डालें")
            updateNotification("⚠️ No API key")
            restartListening()
            return
        }
        
        // ✅ Process with Gemini
        serviceScope.launch {
            try {
                val response = processWithGemini(heard, apiKey)
                Log.d(TAG, "✅ Response: $response")
                speak(response)  // ✅ Speak response
                updateNotification("✅ Response given")
                
                delay(3000)
                restartListening()
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error processing", e)
                speak("क्षमा करें, कुछ गड़बड़ हो गई")
                updateNotification("❌ Error: ${e.message}")
                restartListening()
            }
        }
    }

    private suspend fun processWithGemini(command: String, apiKey: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=$apiKey"
                
                val json = JSONObject().apply {
                    put("contents", listOf(
                        JSONObject().apply {
                            put("parts", listOf(
                                JSONObject().apply {
                                    put("text", "You are SHAVi AI assistant. Respond in Hinglish (Hindi+English) mixed, friendly and helpful. Keep response short (max 2 sentences). User: $command")
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
                
                Log.d(TAG, "API Response Code: ${response.code}")
                
                if (!response.isSuccessful) {
                    Log.e(TAG, "API Error: $responseBody")
                    return@withContext getFallbackResponse(command)
                }
                
                val jsonResponse = JSONObject(responseBody ?: "{}")
                val text = jsonResponse
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                
                text
                
            } catch (e: Exception) {
                Log.e(TAG, "API Exception", e)
                getFallbackResponse(command)
            }
        }
    }

    private fun getFallbackResponse(command: String): String {
        return when {
            command.contains("time") || command.contains("समय") -> {
                val time = SimpleDateFormat("hh:mm a", Locale("hi")).format(Date())
                "समय $time बज रहा है"
            }
            command.contains("date") || command.contains("तारीख") -> {
                val date = SimpleDateFormat("dd MMMM yyyy", Locale("hi")).format(Date())
                "आज $date है"
            }
            command.contains("hello") || command.contains("नमस्ते") -> {
                "नमस्ते! मैं SHAVi हूँ"
            }
            else -> {
                "मुझे समझ नहीं आया, कृपया फिर से बोलें"
            }
        }
    }

    private fun speak(text: String) {
        if (isTtsReady) {
            Log.d(TAG, "🗣️ Speaking: $text")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "shavi_utterance")
        } else {
            Log.e(TAG, "❌ TTS not ready, can't speak")
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "🛑 Service Destroyed")
        super.onDestroy()
        speechRecognizer?.destroy()
        tts?.shutdown()
        serviceScope.cancel()
    }
}
