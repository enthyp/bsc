package com.lanecki.deepnoise

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.lanecki.deepnoise.call.CallManager
import com.lanecki.deepnoise.call.CallState
import com.lanecki.deepnoise.databinding.ActivityCallBinding
import com.lanecki.deepnoise.settings.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// TODO: use some Android config instead of hardcoding!
class CallActivity : AppCompatActivity(), CallUI {

    private lateinit var callManager: CallManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize CallManager.
        val sharedPreferences: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(this)

        val nickKey = resources.getString(R.string.settings_nick)
        val serverAddressKey = resources.getString(R.string.settings_server_address)

        val nick = sharedPreferences.getString(nickKey, "") ?: ""
        val serverAddress = sharedPreferences.getString(serverAddressKey, "") ?: ""

        val initState = intent.getSerializableExtra(INITIAL_STATE_KEY) as CallState
        val callee = intent.getSerializableExtra(CALLEE_KEY) as String

        callManager = CallManager(initState, nick, callee, serverAddress, this, application)

        checkAudioPermission()
    }

    override fun onModelLoadFailure() {
        Toast.makeText(this, "Failed to load speech model!", Toast.LENGTH_LONG).show()
    }

    override fun onCallRefused() {
        Toast.makeText(this, "Call refused!", Toast.LENGTH_LONG).show()
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, AUDIO_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestAudioPermission()
        } else {
            onAudioPermissionGranted()
        }
    }

    private fun requestAudioPermission(dialogShown: Boolean = false) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, AUDIO_PERMISSION) && !dialogShown) {
            showPermissionRationaleDialog()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(AUDIO_PERMISSION), AUDIO_PERMISSION_REQUEST_CODE)
        }
    }

    private fun onAudioPermissionGranted() {
        lifecycleScope.launch { callManager.run() }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Camera Permission Required")
            .setMessage("This app need the camera to function")
            .setPositiveButton("Grant") { dialog, _ ->
                dialog.dismiss()
                requestAudioPermission(true)
            }
            .setNegativeButton("Deny") { dialog, _ ->
                dialog.dismiss()
                onAudioPermissionDenied()
            }
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AUDIO_PERMISSION_REQUEST_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            onAudioPermissionGranted()
        } else {
            onAudioPermissionDenied()
        }
    }

    private fun onAudioPermissionDenied() {
        Toast.makeText(this, "Audio Permission Denied", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        callManager.shutdown()
    }

    companion object {
        const val CALLEE_KEY = "CALLEE"
        const val INITIAL_STATE_KEY = "INITIAL_STATE"

        private const val AUDIO_PERMISSION_REQUEST_CODE = 1
        private const val AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO
    }
}

interface CallUI {
    fun onModelLoadFailure()
    fun onCallRefused()
}