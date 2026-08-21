package com.shavi.assistant

import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.*

class VoiceAssistantService : Service(), TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var listeningForCommand = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var commandHandler: CommandHandler

    companion object {
        const val CHANNEL_ID = "shavi_channel"
        const val NOTIF_ID = 101
        const val WAKE_PHRASE = "hey shavi"
    }

    override fun onCreate() {
        super.onCreate()
        commandHandler = CommandHandler(this)
        tts = TextToSpeech(this, this)
        startForeground(NOTIF_ID, buildNotification("SHAVi sun rahi hai..."))
        startListeningLoop()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
            tts?.setPitch(1.6f)
            tts?.setSpeechRate(1.1f)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(text: String): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID, "SHAVi Assistant", NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hey SHAVi")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification(text))
    }

    private fun startListeningLoop() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("Is device par speech recognition available nahi hai")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val heard = matches?.firstOrNull()?.lowercase(Locale.getDefault()) ?: ""

                if (!listeningForCommand) {
                    if (heard.contains(WAKE_PHRASE)) {
                        listeningForCommand = true
                        updateNotification("Ji boliye, main yahi hoon...")
                        speak("Ji boliye, main yahi hoon")
                    }
                    restartListening()
                } else {
                    listeningForCommand = false
                    updateNotification("Sochte hue...")
                    handleCommand(heard)
                }
            }

            override fun onError(error: Int) {
                updateNotification("Error code: $error — restart ho rahi hai")
                restartListening()
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        restartListening()
    }

    private fun restartListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        serviceScope.launch {
            delay(300)
            speechRecognizer?.startListening(intent)
        }
    }

    private fun handleCommand(command: String) {
        val prefs = getSharedPreferences("shavi_prefs", MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", null)

        if (apiKey.isNullOrBlank()) {
            speak("Pehle app mein Gemini API key daaliye")
            updateNotification("API key set nahi hai")
            restartListening()
            return
        }

        serviceScope.launch(Dispatchers.IO) {
            val client = GeminiClient(apiKey)
            val action = client.resolveCommand(command)

            withContext(Dispatchers.Main) {
                executeAction(action)
            }
        }
    }

    private fun executeAction(action: GeminiClient.ShaviAction) {
        val hasCallPermission = ActivityCompat.checkSelfPermission(
            this, android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val hasSmsPermission = ActivityCompat.checkSelfPermission(
            this, android.Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val resultText = when (action.action) {
            "call" -> action.target?.let { commandHandler.callContact(it, hasCallPermission) }
                ?: "Kisko call karna hai, naam nahi mila."
            "time" -> commandHandler.getSpokenTime()
            "open_app" -> action.target?.let { commandHandler.openApp(it) }
                ?: "Kaunsa app kholna hai?"
            "send_message" -> if (action.target != null && action.message != null) {
                commandHandler.sendMessage(action.target, action.message, hasSmsPermission)
            } else "Message ya contact clear nahi hua."
            "scroll" -> commandHandler.scroll(action.target ?: "down")
            else -> action.reply
        }

        speak(resultText)
        updateNotification("SHAVi sun rahi hai...")
        restartListening()
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "shavi_utterance")
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        tts?.shutdown()
        serviceScope.cancel()
    }
}
