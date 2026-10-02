package com.miku.ray.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import java.util.EnumMap

object QRCodeDecoder {
    private val hints: Map<DecodeHintType, Any?> = EnumMap<DecodeHintType, Any?>(DecodeHintType::class.java).apply {
        this[DecodeHintType.TRY_HARDER] = true
        this[DecodeHintType.POSSIBLE_FORMATS] = listOf(BarcodeFormat.QR_CODE)
        this[DecodeHintType.CHARACTER_SET] = Charsets.UTF_8.name()
    }

    fun createQRCode(text: String, size: Int = 800): Bitmap? {
        return runCatching {
            val hints = mapOf(EncodeHintType.CHARACTER_SET to Charsets.UTF_8)
            val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
            val pixels = IntArray(size * size) { i ->
                if (bitMatrix.get(i % size, i / size)) 0xff000000.toInt() else 0xffffffff.toInt()
            }
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, size, 0, 0, size, size)
            }
        }.getOrNull()
    }

    fun syncDecodeQRCode(picturePath: String): String? {
        return syncDecodeQRCode(getDecodeAbleBitmap(picturePath))
    }

    fun syncDecodeQRCode(bitmap: Bitmap?): String? {
        if (bitmap == null || bitmap.isRecycled) return null

        // Shared images are sometimes rotated by EXIF or arrive sideways.
        for (rotation in ROTATIONS) {
            val candidate = if (rotation == 0) bitmap else rotate(bitmap, rotation)
            try {
                decode(candidate)?.let { return it }
            } finally {
                if (candidate !== bitmap) candidate.recycle()
            }
        }
        return null
    }

    private fun decode(bitmap: Bitmap): String? {
        return runCatching {
            val pixels = IntArray(bitmap.width * bitmap.height).also { array ->
                bitmap.getPixels(array, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            }
            val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
            val qrReader = QRCodeReader()
            try {
                qrReader.decode(BinaryBitmap(GlobalHistogramBinarizer(source)), hints).text
            } catch (_: NotFoundException) {
                qrReader.decode(BinaryBitmap(GlobalHistogramBinarizer(source.invert())), hints).text
            }
        }.getOrNull()
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            Matrix().apply { postRotate(degrees.toFloat()) },
            true,
        )
    }

    private fun getDecodeAbleBitmap(picturePath: String): Bitmap? {
        return try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(picturePath, options)
            val longestSide = maxOf(options.outWidth, options.outHeight)
            var sampleSize = longestSide / MAX_BITMAP_SIDE
            if (sampleSize <= 0) sampleSize = 1
            options.inSampleSize = sampleSize
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(picturePath, options)
        } catch (e: Exception) {
            null
        }
    }

    private const val MAX_BITMAP_SIDE = 1600
    private val ROTATIONS = intArrayOf(0, 90, 180, 270)
}
