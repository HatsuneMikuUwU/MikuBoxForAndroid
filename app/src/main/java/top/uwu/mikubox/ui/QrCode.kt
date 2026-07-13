package top.uwu.mikubox.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.ImageView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Renders share links as QR codes, equivalent to UwU's share bottom sheet. */
object QrCode {

    private fun encode(text: String, size: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            val offset = y * size
            for (x in 0 until size) {
                pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, size, 0, 0, size, size)
        }
    }

    fun show(context: Context, title: String, content: String) {
        val size = (context.resources.displayMetrics.density * 260).toInt()
        val image = ImageView(context).apply {
            val pad = (context.resources.displayMetrics.density * 24).toInt()
            setPadding(pad, pad, pad, pad)
            setImageBitmap(encode(content, size))
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(image)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
