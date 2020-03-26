package com.example.deepnoise

import android.Manifest
import android.R.attr.track
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.deepnoise.api.RTCClient
import com.example.deepnoise.audio.FIRFilter
import com.example.deepnoise.databinding.ActivityCallBinding
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.voiceengine.WebRtcAudioRecord
import org.webrtc.voiceengine.WebRtcAudioUtils
import java.nio.ByteBuffer


class CallActivity : AppCompatActivity() {

    companion object {
        private const val AUDIO_PERMISSION_REQUEST_CODE = 1
        private const val AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO
        private const val BUFFER_SIZE = 16384
        private const val SAMPLE_RATE = 48000  // TODO: need to figure out real rate doe
    }

    private lateinit var binding: ActivityCallBinding
    private lateinit var rtcClient: RTCClient
    private lateinit var audioTrack: AudioTrack
    private var filter = FIRFilter(24000.0 / SAMPLE_RATE, 257)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.callButton.setOnClickListener { rtcClient.call() }
        checkAudioPermission()
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, AUDIO_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestAudioPermission()
        } else {
            onAudioPermissionGranted()
        }
    }

    private fun audioCallback() = JavaAudioDeviceModule.AudioTrackProcessingCallback {
        filter.run(it)
    }

    private fun onAudioPermissionGranted() {
        rtcClient = RTCClient(
            audioCallback(),
            application
        )
    }

    private fun requestAudioPermission(dialogShown: Boolean = false) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, AUDIO_PERMISSION) && !dialogShown) {
            showPermissionRationaleDialog()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(AUDIO_PERMISSION), AUDIO_PERMISSION_REQUEST_CODE)
        }
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
        rtcClient.destroy()
        super.onDestroy()
    }
}
