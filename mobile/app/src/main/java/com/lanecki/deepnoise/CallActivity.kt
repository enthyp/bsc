package com.lanecki.deepnoise

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.lanecki.deepnoise.call.CallHandler
import com.lanecki.deepnoise.databinding.ActivityCallBinding
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.webrtc.audio.JavaAudioDeviceModule
import java.io.IOException
import java.nio.MappedByteBuffer


class CallActivity : AppCompatActivity() {

    companion object {
        private const val AUDIO_PERMISSION_REQUEST_CODE = 1
        private const val AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO
        private const val BUFFER_SIZE = 441  // number of samples
    }

    private lateinit var binding: ActivityCallBinding
    private lateinit var callHandler: CallHandler
    private lateinit var tfliteModel: MappedByteBuffer
    private lateinit var tflite: Interpreter
    private var inBuffer: FloatArray = FloatArray(BUFFER_SIZE)
    private var outBuffer: Array<FloatArray> = arrayOf(FloatArray(BUFFER_SIZE))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.callButton.setOnClickListener { callHandler.call() }
        checkAudioPermission()

        try{
            tfliteModel = FileUtil.loadMappedFile(this, "identity_model.tflite")
            tflite = Interpreter(tfliteModel)
        } catch (e: IOException){
            Log.e("tfliteSupport", "Error reading model", e);
        }
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, AUDIO_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestAudioPermission()
        } else {
            onAudioPermissionGranted()
        }
    }

    private fun audioCallback() = JavaAudioDeviceModule.AudioTrackProcessingCallback {
        for (i in 0 until BUFFER_SIZE)
            inBuffer[i] = it.getShort(2 * i).toFloat()

        tflite.run(inBuffer, outBuffer)

        for (i in 0 until BUFFER_SIZE)
            it.putShort(2 * i, outBuffer[0][i].toShort())
    }

    private fun onAudioPermissionGranted() {
        callHandler = CallHandler(
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
        super.onDestroy()
        callHandler.shutdown()
    }
}
