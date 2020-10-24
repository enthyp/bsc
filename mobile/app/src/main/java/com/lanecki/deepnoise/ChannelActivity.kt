package com.lanecki.deepnoise

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.lanecki.deepnoise.call.*
import com.lanecki.deepnoise.channel.ChannelManager
import com.lanecki.deepnoise.databinding.ActivityCallBinding
import com.lanecki.deepnoise.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


interface ChannelUI {
    fun onConnectionClosed()
    // TODO:
    //  onTextMessage(message: String)
    //  onUserJoined(user: String)
    //  onUserLeft(user: String)
}


// TODO: use some Android config instead of hardcoding!
class ChannelActivity : AppCompatActivity(), ChannelUI,
    CoroutineScope by CoroutineScope(Dispatchers.Default) {

    companion object {
        private const val TAG = "ChannelActivity"
        private const val AUDIO_PERMISSION_REQUEST_CODE = 1
        private const val AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO
    }

    private lateinit var state: CallState
    private lateinit var nick: String
    private lateinit var callee: String
    private var callId: String? = null

    private lateinit var callActionFab: FloatingActionButton
    private lateinit var hangupActionFab: FloatingActionButton

    private lateinit var channelManager: ChannelManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize CallManager.
        val sharedPreferences: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(this)

        val nickKey = resources.getString(R.string.settings_login)
        val serverAddressKey = resources.getString(R.string.settings_server_address)

        nick = sharedPreferences.getString(nickKey, "") ?: ""
        val serverAddress = sharedPreferences.getString(serverAddressKey, "") ?: ""

        state = intent.getSerializableExtra(Constants.INITIAL_STATE_KEY) as CallState
        callee = intent.getSerializableExtra(Constants.CALLEE_KEY) as String
        callId = intent.getSerializableExtra(Constants.CALL_ID_KEY) as String?

        channelManager = ChannelManager(nick, serverAddress, this, application)

        // Setup view
        hangupActionFab = binding.hangup
        hangupActionFab.setOnClickListener(hangupActionFabClickListener());
        callActionFab = binding.call

        if (state == CallState.INCOMING) {
            callActionFab.setOnClickListener(callActionFabClickListener());
        } else {
            callActionFab.visibility = View.GONE
        }

        setupWindow()
        setupAudio()

        checkAudioPermission()
    }

    private fun callActionFabClickListener() = View.OnClickListener {
        callActionFab.visibility = View.GONE
        if (state == CallState.INCOMING) {
            state = CallState.SIGNALLING
            launch { channelManager.send(
                AcceptMsg(
                    nick,
                    callee,
                    callId!!
                )
            ) }
        }
    }

    // TODO: fix the states (some callbacks to change it + mutex)
    private fun hangupActionFabClickListener() = View.OnClickListener {
        when (state) {
            CallState.INCOMING -> launch { channelManager.send(
                RefuseMsg(
                    nick,
                    callee,
                    callId!!
                )
            ) }
            CallState.OUTGOING -> launch { channelManager.send(HangupMsg) }
            CallState.SIGNALLING -> launch { channelManager.send(HangupMsg) }
        }

        state = CallState.CLOSED
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
        launch { channelManager.send(CloseMsg) }
        super.onDestroy()
        Log.d(TAG, "Destroyed!")
    }

    override fun onConnectionClosed() {
        Toast.makeText(this, "Connection was closed...", Toast.LENGTH_LONG).show()
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
        launch { channelManager.run() }
        if (state == CallState.INCOMING) {
            launch { channelManager.send(
                IncomingCallMsg(
                    callee,
                    callId!!
                )
            ) }
        } else {
            launch { channelManager.send(
                OutgoingCallMsg(
                    callee
                )
            ) }
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Audio Permission Required")
            .setMessage("This app need audio permission to function!")
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
