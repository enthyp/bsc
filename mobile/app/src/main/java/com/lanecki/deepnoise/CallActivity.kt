package com.lanecki.deepnoise

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.lanecki.deepnoise.call.CallManager
import com.lanecki.deepnoise.call.CallState
import com.lanecki.deepnoise.databinding.ActivityCallBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch


// TODO: use some Android config instead of hardcoding!
class CallActivity : AppCompatActivity(), CallUI,
    CoroutineScope by CoroutineScope(Dispatchers.Main) {

    companion object {
        private const val AUDIO_PERMISSION_REQUEST_CODE = 1
        private const val AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO
    }

    private lateinit var callActionFab: FloatingActionButton
    private lateinit var hangupActionFab: FloatingActionButton

    private lateinit var callManager: CallManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        callActionFab = binding.call
        hangupActionFab = binding.hangup
        callActionFab.setOnClickListener(callActionFabClickListener());
        hangupActionFab.setOnClickListener(hangupActionFabClickListener());

        setupWindow()
        setupAudio()

        // Initialize CallManager.
        val sharedPreferences: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(this)

        val nickKey = resources.getString(R.string.settings_nick)
        val serverAddressKey = resources.getString(R.string.settings_server_address)

        val nick = sharedPreferences.getString(nickKey, "") ?: ""
        val serverAddress = sharedPreferences.getString(serverAddressKey, "") ?: ""

        val initState = intent.getSerializableExtra(Constant.INITIAL_STATE_KEY) as CallState
        val callee = intent.getSerializableExtra(Constant.CALLEE_KEY) as String
        val callId: String? = intent.getSerializableExtra(Constant.CALL_ID_KEY) as String?

        callManager = CallManager(initState, nick, callee, callId, serverAddress, this, application)

        checkAudioPermission()
    }

    private fun callActionFabClickListener() = View.OnClickListener {
        // TODO: change the state
        // TODO: this should disappear when we're calling someone too!
        callActionFab.visibility = View.GONE
    }

    private fun hangupActionFabClickListener() = View.OnClickListener {
        // TODO: send refusal
        finish()
    }

    private fun setupWindow() {
        // These flags ensure that the activity can be launched when the screen is locked.
        val window: Window = window
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun setupAudio() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val isWiredHeadsetOn = if (Build.VERSION.SDK_INT >= 23) {
            audioManager.getDevices(AudioManager.GET_DEVICES_ALL)
                .map { d -> d.type }
                .contains(TYPE_WIRED_HEADSET)
        } else {
            audioManager.isWiredHeadsetOn
        }

        audioManager.mode =
            if (isWiredHeadsetOn) AudioManager.MODE_IN_CALL else AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = !isWiredHeadsetOn
    }

    override fun onDestroy() {
        super.onDestroy()
        callManager.shutdown()
    }

    override fun onModelLoadFailure() {
        Toast.makeText(this, "Failed to load speech model!", Toast.LENGTH_LONG).show()
    }

    override fun onCallRefused() {
        Toast.makeText(this, "Call refused!", Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onCallEnd() {
        finish()
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                AUDIO_PERMISSION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestAudioPermission()
        } else {
            onAudioPermissionGranted()
        }
    }

    private fun requestAudioPermission(dialogShown: Boolean = false) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                AUDIO_PERMISSION
            ) && !dialogShown
        ) {
            showPermissionRationaleDialog()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(AUDIO_PERMISSION),
                AUDIO_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun onAudioPermissionGranted() {
        launch { callManager.run() }
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
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
}

interface CallUI {
    fun onModelLoadFailure()
    fun onCallRefused()
    fun onCallEnd()
}