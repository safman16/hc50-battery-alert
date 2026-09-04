package com.zebra.batteryalert

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnStart = findViewById<Button>(R.id.btnStartService)
        val btnStop = findViewById<Button>(R.id.btnStopService)
        val btnTest = findViewById<Button>(R.id.btnTestAlert)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        checkAndRequestPermissions()
        requestBatteryOptimizationExemption()

        btnStart.setOnClickListener {
            val intent = Intent(this, BatteryAlertService::class.java)
            ContextCompat.startForegroundService(this, intent)
            tvStatus.text = "Status: Monitoring Active (10% threshold)"
            Toast.makeText(this, "Battery Guard Started", Toast.LENGTH_SHORT).show()
        }

        btnStop.setOnClickListener {
            val intent = Intent(this, BatteryAlertService::class.java)
            stopService(intent)
            tvStatus.text = "Status: Stopped"
            Toast.makeText(this, "Battery Guard Stopped", Toast.LENGTH_SHORT).show()
        }

        btnTest.setOnClickListener {
            val intent = Intent(this, BatteryAlertService::class.java).apply {
                action = BatteryAlertService.ACTION_TEST_ALERT
            }
            ContextCompat.startForegroundService(this, intent)
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    private fun requestBatteryOptimizationExemption() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }
}
