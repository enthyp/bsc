package com.example.deepnoise

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.deepnoise.databinding.ActivityCallBinding

class CallActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val calleeKey = resources.getString(R.string.callee)
        binding.callee.text = intent.getStringExtra(calleeKey)
    }
}
