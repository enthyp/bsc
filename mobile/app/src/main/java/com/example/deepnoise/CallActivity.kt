package com.example.deepnoise

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.example.deepnoise.api.RTCClient
import com.example.deepnoise.databinding.ActivityCallBinding

class CallActivity : AppCompatActivity() {

    companion object {
        private const val AUDIO_PERMISSION_REQUEST_CODE = 1
        private const val AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO
    }

    private lateinit var binding: ActivityCallBinding
    private lateinit var rtcClient: RTCClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.callButton.setOnClickListener { rtcClient.call() }
        checkCameraPermission()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, AUDIO_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestAudioPermission()
        } else {
            onAudioPermissionGranted()
        }
    }

    private fun onAudioPermissionGranted() {
        rtcClient = RTCClient(
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
