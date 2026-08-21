package com.shavi.assistant

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private var serviceRunning = false

    private val requiredPermissions = mutableListOf(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.CALL_PHONE,
        android.Manifest.permission.READ_CONTACTS
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        Toast.makeText(
            this,
            if (allGranted) "Sab permissions mil gayi!" else "Kuch permissions abhi bhi baaki hain",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)
        val apiKeyInput = findViewById<EditText>(R.id.apiKeyInput)
        val saveKeyButton = findViewById<Button>(R.id.saveKeyButton)
        val permissionsButton = findViewById<Button>(R.id.permissionsButton)
        val toggleButton = findViewById<Button>(R.id.toggleButton)

        val prefs = getSharedPreferences("shavi_prefs", MODE_PRIVATE)
        apiKeyInput.setText(prefs.getString("gemini_api_key", ""))

        saveKeyButton.setOnClickListener {
            val key = apiKeyInput.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, "Pehle API key daalein", Toast.LENGTH_SHORT).show()
            } else {
                prefs.edit().putString("gemini_api_key", key).apply()
                Toast.makeText(this, "API key save ho gayi", Toast.LENGTH_SHORT).show()
            }
        }

        permissionsButton.setOnClickListener {
            permissionLauncher.launch(requiredPermissions)
        }

        toggleButton.setOnClickListener {
            if (!serviceRunning) {
                if (!allPermissionsGranted()) {
                    Toast.makeText(this, "Pehle permissions allow karein", Toast.LENGTH_SHORT).show()
                    permissionLauncher.launch(requiredPermissions)
                    return@setOnClickListener
                }
                val key = prefs.getString("gemini_api_key", "")
                if (key.isNullOrBlank()) {
                    Toast.ma
