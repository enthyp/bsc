package com.example.deepnoise.audio

import android.util.Log
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.sin

class FIRFilter(private val fc: Double, private val kernel_size: Int) {

    private val kernel: DoubleArray = DoubleArray(kernel_size)
    private val sampleBuffer: ShortArray = ShortArray(kernel_size)

    init {
        var sum = 0.0

        for (i in 0 until kernel_size) {
            if (i == (kernel_size / 2)) {
                kernel[i] = 2 * Math.PI * fc
            } else {
                kernel[i] = sin(2 * Math.PI * fc * (i - kernel_size / 2)) / (i - kernel_size / 2)
                kernel[i] *= (0.42 - 0.5 * cos(2 * Math.PI * i / kernel_size) + 0.08 * cos(4 * Math.PI * i / kernel_size))
            }
            sum += kernel[i]
        }

        for (i in 0 until kernel_size) {
            kernel[i] /= sum
        }
    }

    fun run(buffer: ByteBuffer) {
        val position = buffer.position()
        for (i in 0 until buffer.remaining() step 2) {
            var tmp: Short
            // Shift buffer.
            var prev = sampleBuffer[0]
            for (j in 1 until kernel_size) {
                tmp = sampleBuffer[j]
                sampleBuffer[j] = prev
                prev = tmp
            }
            sampleBuffer[0] = buffer.getShort(position + i)

            // Convolve.
            var out = 0.0
            for (j in 0 until kernel_size)
                out += kernel[j] * sampleBuffer[j]
            buffer.putShort(position + i, out.toShort())
        }
    }
}