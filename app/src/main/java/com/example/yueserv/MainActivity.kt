package com.example.yueserv

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

private const val TAG = "MainActivity"
private const val REQUEST_CODE_PERMISSION = 1001

class MainActivity : AppCompatActivity() {

    private var httpServiceIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkPermission()
    }

    // region init main view
    private fun initMainView() {
        initStartStopButtons()
        initUpdateMusicDbButton()

        val checkBoxAutoMode = findViewById<CheckBox>(R.id.checkBoxAutoMode)
        if (checkBoxAutoMode.isChecked) {
            val buttonStart = findViewById<Button>(R.id.buttonStart)
            buttonStart.performClick()
        }
    }

    override fun onDestroy() {
        stopHttpService()
        super.onDestroy()
    }

    private fun alert(msg: String?) {
        if (!msg.isNullOrEmpty()) {
            this.runOnUiThread {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
    // endregion

    // region init functions
    private fun getVolumePath(volume: StorageVolume): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return volume.directory?.absolutePath ?: ""
        }
        val getPathMethod = StorageVolume::class.java.getMethod("getPath")
        val path = getPathMethod.invoke(volume) as String
        return path
    }

    private fun getAllVolumePaths(): List<String> {
        val externalPaths = mutableListOf<String>()
        externalPaths.add(Environment.getExternalStorageDirectory().absolutePath)

        val storageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val storageVolumes = storageManager.storageVolumes
        for (volume in storageVolumes) {
            if (!volume.isRemovable || volume.state != Environment.MEDIA_MOUNTED) {
                continue
            }
            try {
                val absPath = getVolumePath(volume)
                if (absPath.isNotEmpty()) {
                    externalPaths.add(absPath)
                }
            } catch (e: Exception) {
                Log.e(TAG, "get volume path error: ${e.message}")
            }
        }
        return externalPaths
    }

    private fun initUpdateMusicDbButton() {
        val that = this
        val buttonRefresh = findViewById<Button>(R.id.buttonRefresh)
        var countDown = 0 // buggy!

        fun tryEnableRefreshButton(){
            countDown --
            if(countDown > 0){
                return
            }
            that.runOnUiThread {
                buttonRefresh.isEnabled = true
            }
        }

        fun scanPath(dir: String) {
            Log.d(TAG, "scan dir: $dir")
            MediaScannerConnection.scanFile(
                that, arrayOf(dir), arrayOf("audio/*")
            ) { path, uri ->
                tryEnableRefreshButton()
                var msg = "${getString(R.string.scan_failed)}$path"
                if (uri != null) {
                    msg = "${getString(R.string.scan_success)}$path"
                }
                Log.d(TAG, msg)
                alert(msg)
            }
        }

        buttonRefresh.setOnClickListener {
            buttonRefresh.isEnabled = false
            countDown = 0
            val paths = getAllVolumePaths()
            for (path in paths) {
                countDown ++
                scanPath(path)
            }
        }
    }

    private fun initStartStopButtons() {
        val that = this

        val editTextPort = findViewById<EditText>(R.id.editTextPort)
        val buttonStart = findViewById<Button>(R.id.buttonStart)
        val buttonStop = findViewById<Button>(R.id.buttonStop)
        val buttonVisit = findViewById<Button>(R.id.buttonVisit)
        val checkBoxAutoMode = findViewById<CheckBox>(R.id.checkBoxAutoMode)

        fun toggleButtons(isOn: Boolean) {
            editTextPort.isEnabled = !isOn
            buttonStop.isEnabled = isOn
            buttonStart.isEnabled = !isOn
            buttonVisit.isEnabled = isOn
        }
        toggleButtons(false)

        val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val savedPort = sharedPreferences.getString("savedPort", "3000")
        val savedAutoMode = sharedPreferences.getString("savedAutoMode", "false")
        editTextPort.setText(savedPort)
        checkBoxAutoMode.isChecked = savedAutoMode == "true"

        checkBoxAutoMode.setOnCheckedChangeListener { _, _ ->
            sharedPreferences.edit {
                putString("savedAutoMode", if (checkBoxAutoMode.isChecked) "true" else "false")
            }
        }

        buttonVisit.setOnClickListener {
            try {
                val webpage = "http://localhost:${editTextPort.text}/".toUri()
                val browserIntent = Intent(Intent.ACTION_VIEW, webpage)
                startActivity(browserIntent)
            } catch (e: Exception) {
                Log.e(TAG, "open browser error: ${e.message}")
                alert(getString(R.string.no_browser_available))
            }
        }

        buttonStop.setOnClickListener {
            stopHttpService()
            toggleButtons(false)
        }

        buttonStart.setOnClickListener {
            try {
                val portText = editTextPort.text.toString()
                val port = portText.toInt()
                httpServiceIntent = Intent(that, HttpService::class.java).apply {
                    putExtra("PORT", port)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(httpServiceIntent)
                } else {
                    startService(httpServiceIntent)
                }

                sharedPreferences.edit {
                    putString("savedPort", portText)
                }
                toggleButtons(true)

                alert(getString(R.string.http_server_starts))
            } catch (e: Exception) {
                alert(e.message)
            }
        }
    }

    private fun stopHttpService() {
        val intent = httpServiceIntent
        httpServiceIntent = null // buggy!
        if (intent != null) {
            stopService(intent)
        }
    }

    // endregion

    // region init
    private fun initMainActivity() {
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initMainView()
    }

    private fun showNoPermitActivity() {
        setContentView(R.layout.activity_no_permit)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.no_permit)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun showIndexActivity(isGranted: Boolean) {
        if (isGranted) {
            initMainActivity()
        } else {
            showNoPermitActivity()
        }
    }

    // endregion

    // region permissions

    private fun isPermit(
        permission: String
    ): Boolean {
        return ContextCompat.checkSelfPermission(
            this, permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasPermission(): Boolean {
        if (!isPermit(Manifest.permission.INTERNET)) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (!isPermit(Manifest.permission.FOREGROUND_SERVICE)) {
                return false
            }
            if (!isPermit(Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC)) {
                return false
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (isPermit(
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            ) {
                return true
            }
        } else {
            if (isPermit(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                return true
            }
        }
        return false
    }

    private fun generatePermitsList(): Array<String> {
        val permits = mutableListOf<String>()

        permits.add(Manifest.permission.INTERNET)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permits.add(Manifest.permission.FOREGROUND_SERVICE)
            permits.add(Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permits.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permits.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        return permits.toTypedArray()
    }

    private fun checkPermission() {
        if (hasPermission()) {
            showIndexActivity(true)
            return
        }

        val permits = generatePermitsList()
        ActivityCompat.requestPermissions(
            this, permits, REQUEST_CODE_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSION) {
            if (grantResults.isEmpty()) {
                showIndexActivity(false)
                return
            }
            for (state in grantResults) {
                if (state != PackageManager.PERMISSION_GRANTED) {
                    showIndexActivity(false)
                    return
                }
            }
            showIndexActivity(true)
        }
    }
    // endregion

}