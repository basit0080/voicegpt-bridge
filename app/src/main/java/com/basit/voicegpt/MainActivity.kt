package com.basit.voicegpt

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tvAssistant: TextView
    private lateinit var tvUserSpeech: TextView
    private lateinit var btnMic: View

    private var tts: TextToSpeech? = null
    private val httpClient = OkHttpClient()

    private val REQUEST_CODE_SPEECH = 1001
    private val REQUEST_CODE_MIC_PERMISSION = 2001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvAssistant = findViewById(R.id.tvAssistant)
        tvUserSpeech = findViewById(R.id.tvUserSpeech)
        btnMic = findViewById(R.id.btnMic)

        tts = TextToSpeech(this, this)

        btnMic.setOnClickListener {
            checkMicPermission()
        }
    }

    // =================== TTS Init ===================
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("ur", "PK"))
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                tvAssistant.text = "Language not supported."
            }
        } else {
            tvAssistant.text = "TTS init failed."
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voicegpt-utterance")
    }

    // =================== Permissions ===================
    private fun checkMicPermission() {
        val permission = Manifest.permission.RECORD_AUDIO
        val granted = ContextCompat.checkSelfPermission(this, permission)

        if (granted == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            requestPermissions(arrayOf(permission), REQUEST_CODE_MIC_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_MIC_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening()
            } else {
                Toast.makeText(this, "Mic permission denied.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // =================== Speech Input ===================
    private fun startListening() {
        tvAssistant.text = "Sun raha hoon..."

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("ur", "PK"))
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Bolna shuru karein...")
        }

        startActivityForResult(intent, REQUEST_CODE_SPEECH)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_SPEECH && data != null) {
            val results =
                data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) ?: ArrayList()
            val userText = results.firstOrNull().orEmpty()

            if (userText.isNotBlank()) {
                tvUserSpeech.text = userText
                btnMic.isEnabled = false
                askChatGPT(userText)
            }
        }
    }

    // =================== ChatGPT Call ===================
    private fun askChatGPT(userText: String) {
        val model = ApiKeys.OPENAI_MODEL
        val apiKey = ApiKeys.OPENAI_API_KEY

        if (apiKey.isBlank()) {
            val fallback = buildLocalReply(userText)
            tvAssistant.text = fallback
            speak(fallback)
            btnMic.isEnabled = true
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val url = "https://api.openai.com/v1/chat/completions"

                val messages = JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("role", "system")
                            put(
                                "content",
                                "Tum ek friendly Urdu AI assistant ho jo Basit bhai se normal baat karta hai."
                            )
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", userText)
                        }
                    )
                }

                val root = JSONObject().apply {
                    put("model", model)
                    put("messages", messages)
                }

                val mediaType = "application/json".toMediaType()
                val body = RequestBody.create(mediaType, root.toString())

                val req = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                val response = httpClient.newCall(req).execute()
                val responseText = response.body?.string().orEmpty()

                val reply = if (response.isSuccessful && responseText.isNotBlank()) {
                    try {
                        val json = JSONObject(responseText)
                        json.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim()
                    } catch (e: Exception) {
                        buildLocalReply(userText)
                    }
                } else {
                    buildLocalReply(userText)
                }

                runOnUiThread {
                    tvAssistant.text = reply
                    speak(reply)
                    btnMic.isEnabled = true
                }

            } catch (e: Exception) {
                e.printStackTrace()
                val fallback = buildLocalReply(userText)
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Network ya API error!",
                        Toast.LENGTH_SHORT
                    ).show()
                    tvAssistant.text = fallback
                    speak(fallback)
                    btnMic.isEnabled = true
                }
            }
        }
    }

    // =================== Local Fallback ===================
    private fun buildLocalReply(userText: String): String {
        val lower = userText.lowercase(Locale.getDefault())

        return when {
            lower.contains("assalam") ->
                "Wa Alaikum Assalam Basit bhai."

            lower.contains("kese ho") || lower.contains("kaise ho") ->
                "Alhamdulillah theek hoon Basit bhai."

            else ->
                "Aap ne kaha: $userText"
        }
    }

    // =================== Cleanup ===================
    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
