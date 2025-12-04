package com.voicegpt.bridge

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvRecognizedText: TextView
    private lateinit var tvActionStatus: TextView
    private lateinit var btnMic: Button

    private val REQUEST_CODE_SPEECH = 100
    private val REQUEST_CODE_PERMISSIONS = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvRecognizedText = findViewById(R.id.tvRecognizedText)
        tvActionStatus = findViewById(R.id.tvActionStatus)
        btnMic = findViewById(R.id.btnMic)

        btnMic.setOnClickListener {
            checkAndRequestPermissionsThenStartListening()
        }
    }

    private fun checkAndRequestPermissionsThenStartListening() {
        val neededPermissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.CALL_PHONE)
        }

        if (neededPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                neededPermissions.toTypedArray(),
                REQUEST_CODE_PERMISSIONS
            )
        } else {
            startVoiceInput()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            startVoiceInput()
        }
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your command")
        }

        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Speech recognition not supported", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_SPEECH && resultCode == RESULT_OK && data != null) {
            val result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = result?.getOrNull(0) ?: return

            tvRecognizedText.text = "You said: $spokenText"
            handleSpokenCommand(spokenText.lowercase(Locale.getDefault()))
        }
    }

    private fun handleSpokenCommand(text: String) {
        // Here currently simple rules (v1)
        // Future: send `text` to AI API + parse JSON -> actions

        when {
            // YouTube open
            text.contains("youtube") || text.contains("yt") -> {
                openAppOrPlayStore("com.google.android.youtube")
            }

            // Browser search
            text.contains("search") || text.contains("browser") || text.contains("chrome") -> {
                val query = text
                openWebSearch(query)
            }

            // Call (demo: direct number)
            text.contains("call") || text.contains("phone") -> {
                // Simple demo: fixed number (later: parse name/number)
                val demoNumber = "03001234567"
                startCall(demoNumber)
            }

            else -> {
                tvActionStatus.text = "Action: no rule matched (v1)"
                Toast.makeText(this, "No local rule matched. (AI integration TODO)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openAppOrPlayStore(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                startActivity(intent)
                tvActionStatus.text = "Action: opening app $packageName"
            } else {
                // If app not installed, open Play Store page
                val marketIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=$packageName")
                )
                startActivity(marketIntent)
                tvActionStatus.text = "Action: opening Play Store for $packageName"
            }
        } catch (e: Exception) {
            tvActionStatus.text = "Action: failed to open app"
            Toast.makeText(this, "Cannot open app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWebSearch(query: String) {
        try {
            val uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
            tvActionStatus.text = "Action: web search"
        } catch (e: Exception) {
            tvActionStatus.text = "Action: failed web search"
            Toast.makeText(this, "Cannot open browser", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCall(number: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$number")
            }
            startActivity(intent)
            tvActionStatus.text = "Action: calling $number"
        } catch (e: SecurityException) {
            tvActionStatus.text = "Action: call permission error"
            Toast.makeText(this, "Call permission not granted", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            tvActionStatus.text = "Action: failed to start call"
            Toast.makeText(this, "Cannot start call", Toast.LENGTH_SHORT).show()
        }
    }
}
