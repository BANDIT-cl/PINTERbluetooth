package com.zplprinter.app.image

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64

/**
 * Empaquetado monocromo (1 bit/píxel, negro=1, MSB primero, fila alineada a byte): el formato
 * que espera el comando ZPL ^GFA. Lo comparten la importación de PDF, la importación de fotos
 * y el dibujo libre ("Graffiti"), todos los caminos que terminan insertando un
 * [com.zplprinter.app.data.model.LabelElement.ImageElement].
 */
object MonoBitmap {
    private const val DEFAULT_THRESHOLD = 180

    fun pack(bitmap: Bitmap, threshold: Int = DEFAULT_THRESHOLD): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val bytesPerRow = (width + 7) / 8
        val out = ByteArray(bytesPerRow * height)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            val rowBase = y * width
            val outRowBase = y * bytesPerRow
            for (x in 0 until width) {
                if (luminance(pixels[rowBase + x]) < threshold) {
                    val byteIndex = outRowBase + (x shr 3)
                    out[byteIndex] = (out[byteIndex].toInt() or (0x80 shr (x and 7))).toByte()
                }
            }
        }
        return out
    }

    fun unpack(packed: ByteArray, widthDots: Int, heightDots: Int): Bitmap {
        val bytesPerRow = (widthDots + 7) / 8
        val bitmap = Bitmap.createBitmap(widthDots, heightDots, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(widthDots * heightDots)

        for (y in 0 until heightDots) {
            val rowBase = y * widthDots
            val inRowBase = y * bytesPerRow
            for (x in 0 until widthDots) {
                val byteIndex = inRowBase + (x shr 3)
                val bit = (packed[byteIndex].toInt() ushr (7 - (x and 7))) and 1
                pixels[rowBase + x] = if (bit == 1) Color.BLACK else Color.WHITE
            }
        }
        bitmap.setPixels(pixels, 0, widthDots, 0, 0, widthDots, heightDots)
        return bitmap
    }

    fun toBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun fromBase64(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

    private fun luminance(pixel: Int): Int {
        val alpha = (pixel ushr 24) and 0xFF
        if (alpha == 0) return 255
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }
}
