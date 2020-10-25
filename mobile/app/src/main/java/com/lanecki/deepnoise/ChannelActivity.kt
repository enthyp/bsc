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
import com.lanecki.deepnoise.channel.ChannelManager
import com.lanecki.deepnoise.channel.JoinMsg
import com.lanecki.deepnoise.channel.LeaveMsg
import com.lanecki.deepnoise.databinding.ActivityChannelBinding
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

    enum class State {
        INIT,
        SIGNALLING,
        CLOSING
    }

    private var state: State = State.INIT
    private var channelId: String? = null

    private lateinit var leaveActionFab: FloatingActionButton

    private lateinit var channelManager: ChannelManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityChannelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize CallManager.
        val sharedPreferences: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(this)

        val serverAddressKey = resources.getString(R.string.settings_server_address)
        val serverAddress = sharedPreferences.getString(serverAddressKey, "") ?: ""

        // TODO:
        //  - null channel ID
        //  - incorrect channel ID (no such channel)
        channelId = intent.getSerializableExtra(Constants.CHANNEL_ID_KEY) as String?
        channelManager = ChannelManager(channelId!!, serverAddress, this, application)

        // Setup view
        leaveActionFab = binding.leave
        leaveActionFab.setOnClickListener(leaveActionFabClickListener());

        setupWindow()
        setupAudio()

        checkAudioPermission()
    }

    // TODO: fix the states (some callbacks to change it + mutex)
    private fun leaveActionFabClickListener() = View.OnClickListener {
        when (state) {
            State.INIT, State.SIGNALLING -> launch { channelManager.send(LeaveMsg) }
            else -> {
                // TODO: Toast("Leaving already, calm down!")?
            }
        }

        state = State.CLOSING
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
        launch { channelManager.send(LeaveMsg) }
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
        when (state) {
            State.INIT, State.SIGNALLING -> launch {
                channelManager.send(JoinMsg(channelId?: ""))
            }
            State.CLOSING -> Log.d(TAG, "Audio permission granted in state CLOSING.")
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
