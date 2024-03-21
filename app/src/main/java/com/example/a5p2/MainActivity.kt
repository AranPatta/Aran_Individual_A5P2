package com.example.a5p2

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent.*
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var languageSpinner: Spinner
    private lateinit var inputText: EditText
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var shakeDetector: ShakeDetector? = null

    companion object {
        private const val REQUEST_CODE_SPEECH_INPUT = 100
        private const val REQUEST_CODE_RECORD_AUDIO_PERMISSION = 200
    }

    private val languageData: Map<String, Pair<String, Int>> = mapOf(
        "Spanish (Mexico)" to Pair("geo:19.4326,-99.1332", R.raw.spanish_hello),
        "Spanish (Spain)" to Pair("geo:40.4168,-3.7038", R.raw.spanish_hello),
        "French" to Pair("geo:48.8566,2.3522", R.raw.french_hello),
        "Chinese (Beijing)" to Pair("geo:39.9042,116.4074", R.raw.chinese_hello),
        "Chinese (Shanghai)" to Pair("geo:31.2304,121.4737", R.raw.chinese_hello),
        "Chinese (Guangzhou)" to Pair("geo:23.1291,113.2644", R.raw.chinese_hello)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        languageSpinner = findViewById(R.id.languageSpinner)
        inputText = findViewById(R.id.inputText)
        setupSpinner()
        setupShakeDetector()
    }

    private fun setupSpinner() {
        val languages = listOf("Select a language", "Spanish (Mexico)", "Spanish (Spain)", "French", "Chinese (Beijing)", "Chinese (Shanghai)", "Chinese (Guangzhou)")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = adapter

        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val language = parent.getItemAtPosition(position) as String
                if (position > 0) {
                    promptSpeechInput(language)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun promptSpeechInput(language: String) {
        if (!checkAudioPermission()) {
            return
        }

        val languageCode = when(language) {
            "Spanish (Mexico)" -> "es-ES"
            "Spanish (Spain)" -> "es-ES"
            "French" -> "fr-FR"
            "Chinese (Beijing)" -> "zh-CN"
            "Chinese (Shanghai)" -> "zh-CN"
            "Chinese (Guangzhou)" -> "zh-CN"
            else -> Locale.getDefault().language
        }

        val intent = Intent(ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(EXTRA_LANGUAGE_MODEL, LANGUAGE_MODEL_FREE_FORM)
            putExtra(EXTRA_LANGUAGE, languageCode)
            putExtra(EXTRA_PROMPT, "Speak now...")
        }

        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT)
        } catch (a: ActivityNotFoundException) {
            Toast.makeText(applicationContext, "Speech recognition not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == RESULT_OK && data != null) {
            val result = data.getStringArrayListExtra(EXTRA_RESULTS)
            inputText.setText(result?.get(0) ?: "")
        }
    }

    private fun checkAudioPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_RECORD_AUDIO_PERMISSION)
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun setupShakeDetector() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        shakeDetector = ShakeDetector {
            val selectedLanguage = languageSpinner.selectedItem.toString()
            if (languageData.containsKey(selectedLanguage)) {
                val geoLocation = languageData[selectedLanguage]?.first
                geoLocation?.let {
                    openMap(it)
                }
                val audioResId = languageData[selectedLanguage]?.second
                audioResId?.let {
                    playGreeting(it)
                }
            } else {
                Toast.makeText(this, "Please select a language.", Toast.LENGTH_SHORT).show()
            }
        }
        accelerometer?.also { acc ->
            sensorManager.registerListener(shakeDetector, acc, SensorManager.SENSOR_DELAY_UI)
        }
    }


    override fun onResume() {
        super.onResume()
        accelerometer?.also { acc ->
            sensorManager.registerListener(shakeDetector, acc, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(shakeDetector)
    }

    private fun openMap(geoLocation: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(geoLocation))
        startActivity(intent)
        intent.setPackage("com.google.android.apps.maps")
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "No application found to open map", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playGreeting(audioResId: Int) {
        MediaPlayer.create(this, audioResId).apply {
            start()
            setOnCompletionListener { mp -> mp.release() }
        }
    }

    inner class ShakeDetector(private val onShake: () -> Unit) : SensorEventListener {
        private val shakeThreshold = 1.5f
        private val timeThreshold = 500
        private var lastUpdate: Long = 0
        private var last_x: Float = 0.0f
        private var last_y: Float = 0.0f
        private var last_z: Float = 0.0f

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

        override fun onSensorChanged(event: SensorEvent) {
            val currentTime = System.currentTimeMillis()
            if ((currentTime - lastUpdate) > timeThreshold) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                Log.d("X", x.toString())
                Log.d("Y", y.toString())
                Log.d("Z", z.toString())

                val deltaX = kotlin.math.abs(last_x - x)
                val deltaY = kotlin.math.abs(last_y - y)
                val deltaZ = kotlin.math.abs(last_z - z)

                if ((deltaX > shakeThreshold && deltaY > shakeThreshold) ||
                    (deltaX > shakeThreshold && deltaZ > shakeThreshold) ||
                    (deltaY > shakeThreshold && deltaZ > shakeThreshold)) {
                    Log.d("Shake", "Shaken")
                    onShake()
                }

                lastUpdate = currentTime
                last_x = x
                last_y = y
                last_z = z
            }
        }
    }

}

