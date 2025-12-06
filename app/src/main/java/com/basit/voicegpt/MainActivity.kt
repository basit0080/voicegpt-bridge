package com.basit.voicegpt

import com.basit.voicegpt.BuildConfig
import com.basit.voicegpt.ApiKeys
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

    // TTS Ready
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                tvAssistant.text = "Language not supported."
            }
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    // Permission
    private fun checkMicPermission() {
        val permission = Manifest.permission.RECORD_AUDIO

        when {
            ContextCompat.checkSelfPermission(this, permission) ==
                    PackageManager.PERMISSION_GRANTED -> startListening()

            shouldShowRequestPermissionRationale(permission) -> {
                tvAssistant.text = "Mic permission is required."
                requestPermissions(arrayOf(permission), 200)
            }

            else -> requestPermissions(arrayOf(permission), 200)
        }
    }

    // Listen to speech
    private fun startListening() {
        tvAssistant.text = "Sun raha hoon..."

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK")

        startActivityForResult(intent, 1001)
    }

    // Speech result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1001 && data != null) {
            val result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val userText = result?.firstOrNull() ?: return

            tvUserSpeech.text = userText
            btnMic.isEnabled = false

            askChatGPT(userText)
        }
    }

    // ================== ChatGPT Call =====================
    private fun askChatGPT(userText: String) {
        val apiKey = BuildConfig.ApiKeys.OPENAI_API_KEY

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
                            put("content", "Tum ek friendly Urdu assistant ho.")
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
                    put("model", BuildConfig.ApiKeys.OPENAI_MODEL)
                    put("messages", messages)
                }

                val body = RequestBody.create(
                    "application/json".toMediaType(),
                    root.toString()
                )

                val req = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(body)
                    .build()

                val response = httpClient.newCall(req).execute()
                val responseText = response.body?.string()

                val reply =
                    if (response.isSuccessful && !responseText.isNullOrBlank()) {
                        JSONObject(responseText)
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim()
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

    // Local fallback
    private fun buildLocalReply(userText: String): String {
        val lower = userText.lowercase(Locale.getDefault())

        return when {
            lower.contains("assalam") ->
                "Wa Alaikum Assalam Basit bhai. Kaise ho?"

            lower.contains("kese ho") ->
                "Alhamdulillah theek hoon Basit bhai."

            else -> "Aap ne kaha: $userText"
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
