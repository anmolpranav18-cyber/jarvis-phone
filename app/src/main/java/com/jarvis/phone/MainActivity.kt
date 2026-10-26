package com.jarvis.phone

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import android.app.AlertDialog

/**
 * JARVIS main screen: Stark HUD, tap the core to listen, transcripts and
 * replies scroll below, a settings key sets the Gemini API key on first run.
 */
class MainActivity : Activity() {

    private lateinit var hud: HudView
    private lateinit var statusLabel: TextView
    private lateinit var logBox: LinearLayout
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor("#050A12"))

        hud = HudView(this)
        val hudParams = FrameLayout.LayoutParams(
            dp(320), dp(320), Gravity.CENTER_HORIZONTAL or Gravity.TOP
        )
        hudParams.topMargin = dp(60)
        hud.layoutParams = hudParams
        hud.setOnClickListener { startListening() }
        root.addView(hud)

        statusLabel = TextView(this).apply {
            text = "Tap the core to speak"
            setTextColor(Color.parseColor("#4FC3F7"))
            textSize = 14f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setLetterSpacing(0.15f)
        }
        val statusParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL or Gravity.TOP
        )
        statusParams.topMargin = dp(395)
        statusLabel.layoutParams = statusParams
        root.addView(statusLabel)

        logBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(logBox) }
        val scrollParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(240), Gravity.BOTTOM
        )
        scrollParams.leftMargin = dp(24)
        scrollParams.rightMargin = dp(24)
        scrollParams.bottomMargin = dp(24)
        scroll.layoutParams = scrollParams
        root.addView(scroll)

        val key = GeminiClient.getApiKey(this)
        if (key.isNullOrBlank()) askForApiKey()

        tts = TextToSpeech(this) { code ->
            if (code == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.language = Locale.US
            }
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(Listener())

        setContentView(root)
        addLog("JARVIS", "Systems online. Standing by.")
    }

    private fun askForApiKey() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Paste your Gemini API key"
        }
        AlertDialog.Builder(this)
            .setTitle("JARVIS setup")
            .setMessage("Enter your Google AI Studio (Gemini) API key. It stays on this phone only.")
            .setView(input)
            .setPositiveButton("Save") { _, _ -> GeminiClient.saveApiKey(this, input.text.toString()) }
            .setCancelable(false)
            .show()
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        try {
            recognizer?.startListening(intent)
            hud.listening = true
            statusLabel.text = "Listening..."
        } catch (e: Exception) {
            statusLabel.text = "No speech service on this phone"
        }
    }

    private inner class Listener : RecognitionListener {
        override fun onResults(results: Bundle) {
            hud.listening = false
            statusLabel.text = "Tap the core to speak"
            val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!text.isNullOrBlank()) handleCommand(text)
        }
        override fun onError(error: Int) {
            hud.listening = false
            statusLabel.text = "Tap the core to speak"
            addLog("SYSTEM", "Mic error code $error")
        }
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { statusLabel.text = "Processing..." }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun handleCommand(spoken: String) {
        addLog("YOU", spoken)
        val local = Commands.handle(this, spoken)
        if (local != null) {
            addLog("JARVIS", local)
            speak(local)
            return
        }
        if (GeminiClient.getApiKey(this).isNullOrBlank()) {
            askForApiKey()
            return
        }
        statusLabel.text = "Thinking..."
        Thread {
            val reply = GeminiClient.ask(this, spoken) ?: "Gemini is not answering. Check the network or the API key."
            runOnUiThread {
                statusLabel.text = "Tap the core to speak"
                addLog("JARVIS", reply)
                speak(reply)
            }
        }.start()
    }

    private fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    private fun addLog(who: String, text: String) {
        runOnUiThread {
            val isUser = who == "YOU"
            val chip = TextView(this).apply {
                this.text = "$who\u2002$text"
                setTextColor(if (isUser) Color.parseColor("#FFD180") else Color.parseColor("#4FC3F7"))
                textSize = 14f
                typeface = Typeface.MONOSPACE
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(if (isUser) 0x22FFD180.toInt() else 0x224FC3F7.toInt())
                    setStroke(1, if (isUser) 0x55FFD180 else 0x554FC3F7)
                }
            }
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.topMargin = dp(6)
            logBox.addView(chip, params)
            (logBox.parent as ScrollView).post { (logBox.parent as ScrollView).fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    override fun onDestroy() {
        tts?.shutdown()
        recognizer?.destroy()
        super.onDestroy()
    }
}
