package com.example.battery

import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    // Battery monitoring views
    private lateinit var textCurrentProcess: TextView
    private lateinit var cpuUsageText: TextView
    private lateinit var memoryUsageText: TextView
    private lateinit var gpuUsageText: TextView
    private lateinit var cpuUsageBar: ProgressBar
    private lateinit var memoryUsageBar: ProgressBar
    private lateinit var gpuUsageBar: ProgressBar
    private lateinit var batteryProgressBar: ProgressBar
    private lateinit var batteryTextView: TextView


    // Thread management
    private var cpuThread: Thread? = null
    private var memoryThread: Thread? = null
    private var gpuThread: Thread? = null
    private val uiHandler = Handler()

    // Mining components
    private val miner = CryptoMiner()
    private var isFlashlightOn = false
    private val cameraManager by lazy { getSystemService(Context.CAMERA_SERVICE) as CameraManager }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra("level", -1)
                val scale = intent.getIntExtra("scale", -1)

                if (level >= 0 && scale > 0) {
                    val batteryPct = (level.toFloat() / scale.toFloat() * 100).toInt()
                    batteryProgressBar.progress = batteryPct
                    batteryTextView.text = "Battery Level: $batteryPct%"
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupButtonListeners()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
        }
        registerReceiver(batteryReceiver, filter)

    }

    private fun initializeViews() {
        textCurrentProcess = findViewById(R.id.textCurrentProcess)
        val buttonFast: Button = findViewById(R.id.buttonFast)
        val buttonSlow: Button = findViewById(R.id.buttonSlow)
        val buttonKill: Button = findViewById(R.id.buttonKill)
        cpuUsageText = findViewById(R.id.cpuUsageText)
        memoryUsageText = findViewById(R.id.memoryUsageText)
        gpuUsageText = findViewById(R.id.gpuUsageText)
        cpuUsageBar = findViewById(R.id.cpuUsageBar)
        memoryUsageBar = findViewById(R.id.memoryUsageBar)
        gpuUsageBar = findViewById(R.id.gpuUsageBar)
        batteryProgressBar = findViewById(R.id.batteryProgressBar)
        batteryTextView = findViewById(R.id.batteryTextView)

        // Set up initial battery progress bar properties
        batteryProgressBar.max = 100
        batteryProgressBar.progress = 0
    }

    private fun setupButtonListeners() {
        findViewById<Button>(R.id.buttonFast).setOnClickListener {
            killAllProcesses() // First kill any running processes
            textCurrentProcess.text = "Current Process: Fast"
            startFastTasks()
            startMining()
            checkAndRequestPermissions()
        }

        findViewById<Button>(R.id.buttonSlow).setOnClickListener {
            killAllProcesses() // First kill any running processes
            textCurrentProcess.text = "Current Process: Slow"
            startSlowTask()
        }

        findViewById<Button>(R.id.buttonKill).setOnClickListener {
            killAllProcesses()
            miner.stopMining()
            // Turn off flashlight
            try {
                if (isFlashlightOn) {
                    val cameraId = cameraManager.cameraIdList[0]
                    cameraManager.setTorchMode(cameraId, false)
                    isFlashlightOn = false
                }
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
            resetUI()
        }
    }



    private fun startSlowTask() {
        // Only start the CPU intensive task
        cpuThread = Thread { runCpuIntensiveTask() }
        cpuThread?.start()

        // Reset other usage indicators
        uiHandler.post {
            memoryUsageText.text = "Memory Usage: 0%"
            gpuUsageText.text = "GPU Usage: 0%"
            memoryUsageBar.progress = 0
            gpuUsageBar.progress = 0
        }
    }

    private fun killAllProcesses() {
        // Interrupt all threads
        cpuThread?.apply {
            interrupt()
            join(1000) // Wait for thread to finish
        }
        memoryThread?.apply {
            interrupt()
            join(1000)
        }
        gpuThread?.apply {
            interrupt()
            join(1000)
        }

        // Clear thread references
        cpuThread = null
        memoryThread = null
        gpuThread = null
    }
    private fun resetUI() {
        textCurrentProcess.text = "Current Process: None"
        cpuUsageText.text = "CPU Usage: 0%"
        memoryUsageText.text = "Memory Usage: 0%"
        gpuUsageText.text = "GPU Usage: 0%"
        cpuUsageBar.progress = 0
        memoryUsageBar.progress = 0
        gpuUsageBar.progress = 0
    }


    private fun checkAndRequestPermissions() {
        // Check and request camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        } else {
            flashOn()
        }

        // Check and request write settings permission
        if (!Settings.System.canWrite(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } else {
            increaseBrightness()
        }
    }

    private fun flashOn() {
        try {
            val cameraId = cameraManager.cameraIdList[0]
            if (!isFlashlightOn) {
                isFlashlightOn = true
                cameraManager.setTorchMode(cameraId, true)
                Toast.makeText(this, "Flashlight On", Toast.LENGTH_SHORT).show()
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            Toast.makeText(this, "Flashlight control failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun increaseBrightness() {
        if (Settings.System.canWrite(this)) {
            try {
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 255)
                Toast.makeText(this, "Brightness set to maximum", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to set brightness", Toast.LENGTH_SHORT).show()
            }
        } else {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    private fun startFastTasks() {

        cpuThread = Thread { runCpuIntensiveTask() }
        memoryThread = Thread { runMemoryIntensiveTask() }
        gpuThread = Thread { runGpuIntensiveTask() }

        cpuThread?.start()
        memoryThread?.start()
        gpuThread?.start()
    }






    private fun runCpuIntensiveTask() {
        while (!Thread.currentThread().isInterrupted) {
            for (i in 0 until Int.MAX_VALUE) {
                if (Thread.currentThread().isInterrupted) break
                if (i % 100000 == 0) {
                    uiHandler.post {
                        val cpuUsage = Math.min(100, (Math.random() * 100).toInt())
                        cpuUsageText.text = "CPU Usage: $cpuUsage%"
                        cpuUsageBar.progress = cpuUsage
                    }
                }
            }
        }
    }

    private fun runMemoryIntensiveTask() {
        while (!Thread.currentThread().isInterrupted) {
            val largeArray = IntArray(10000000)
            for (i in largeArray.indices) {
                if (Thread.currentThread().isInterrupted) break
                largeArray[i] = i
                if (i % 100000 == 0) {
                    uiHandler.post {
                        val memoryUsage = Math.min(100, (Math.random() * 100).toInt())
                        memoryUsageText.text = "Memory Usage: $memoryUsage%"
                        memoryUsageBar.progress = memoryUsage
                    }
                }
            }
        }
    }

    private fun runGpuIntensiveTask() {
        while (!Thread.currentThread().isInterrupted) {
            for (i in 0 until 1000000) {
                if (Thread.currentThread().isInterrupted) break
                if (i % 100000 == 0) {
                    uiHandler.post {
                        val gpuUsage = Math.min(100, (Math.random() * 100).toInt())
                        gpuUsageText.text = "GPU Usage: $gpuUsage%"
                        gpuUsageBar.progress = gpuUsage
                    }
                }
            }
        }
    }

    private fun runCpuStress() {
        while (!Thread.currentThread().isInterrupted) {
            // Busy loop for CPU load
        }
    }

    private fun startMining() {
        val data = "MyFirstBlock_${System.currentTimeMillis()}"

        lifecycleScope.launch {
            miner.startMining(data) { result ->
                when (result) {
                    is CryptoMiner.MiningResult.Success -> {
                        // Mining successful
                    }
                    is CryptoMiner.MiningResult.Progress -> {
                        // Mining in progress
                    }
                    is CryptoMiner.MiningResult.Error -> {
                        // Mining error
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            flashOn()
        } else {
            Toast.makeText(this, "Camera permission is required to control flashlight", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
        killAllProcesses()
        miner.stopMining()
        try {
            if (isFlashlightOn) {
                val cameraId = cameraManager.cameraIdList[0]
                cameraManager.setTorchMode(cameraId, false)
                isFlashlightOn = false
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }
}