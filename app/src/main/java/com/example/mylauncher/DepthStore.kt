package com.example.mylauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** 3D depth wallpaper ki saari settings (clock, subject, date). Positions screen ke fractions mein hain: har screen pe same. */
data class DepthConfig(
    val clockX: Float = 0f,
    val clockY: Float = 0.14f,
    val clockScale: Float = 1f,
    val fgX: Float = 0f,
    val fgY: Float = 0f,
    val fgScale: Float = 1f,
    val showDate: Boolean = true
) {
    fun save(p: Prefs) {
        p.depthClockX = clockX
        p.depthClockY = clockY
        p.depthClockScale = clockScale
        p.depthFgX = fgX
        p.depthFgY = fgY
        p.depthFgScale = fgScale
        p.depthShowDate = showDate
    }

    companion object {
        fun from(p: Prefs) = DepthConfig(
            p.depthClockX, p.depthClockY, p.depthClockScale,
            p.depthFgX, p.depthFgY, p.depthFgScale, p.depthShowDate
        )
    }
}

/**
 * Depth wallpaper ke files: sirf app-private storage (filesDir/depth). User ki chuni hui image ka URI yaad nahi rakhte:
 * import ke waqt validate + sample karke apni copy banate hain, isliye permission / missing file ka masla nahi.
 * File ke naam fixed hain, isliye path traversal ka sawal hi nahi.
 */
object DepthStore {
    const val MAX_DIM = 2400

    private fun dir(ctx: Context): File = File(ctx.filesDir, "depth").also { it.mkdirs() }

    fun bgFile(ctx: Context): File = File(dir(ctx), "bg.jpg")
    fun fgFile(ctx: Context): File = File(dir(ctx), "fg.png")

    fun hasBg(ctx: Context): Boolean = bgFile(ctx).let { it.exists() && it.length() > 0 }
    fun hasFg(ctx: Context): Boolean = fgFile(ctx).let { it.exists() && it.length() > 0 }

    /**
     * Uri se image ko sampled decode (lambi side <= [maxDim]); bad / kharab file ya OOM pe null.
     * Background photos ke liye EXIF rotation lagta hai.
     */
    fun decodeUri(ctx: Context, uri: Uri, maxDim: Int = MAX_DIM, applyExif: Boolean = true): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null
            if (!applyExif) return bmp
            val rotation = ctx.contentResolver.openInputStream(uri)?.use { exifRotation(it) } ?: 0
            if (rotation == 0) bmp else rotate(bmp, rotation)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private fun exifRotation(stream: java.io.InputStream): Int = try {
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }

    private fun rotate(src: Bitmap, degrees: Int): Bitmap {
        val m = Matrix()
        m.postRotate(degrees.toFloat())
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (out !== src) src.recycle()
        return out
    }

    /** Background ko JPEG ke roop mein save (pehle temp file, phir rename: beech mein kharab file nahi rehti). */
    fun saveBackground(ctx: Context, bmp: Bitmap): Boolean = writeAtomic(bgFile(ctx)) {
        bmp.compress(Bitmap.CompressFormat.JPEG, 92, it)
    }

    /** Foreground transparent PNG (alpha preserve). */
    fun saveForeground(ctx: Context, bmp: Bitmap): Boolean = writeAtomic(fgFile(ctx)) {
        bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
    }

    fun clearForeground(ctx: Context) {
        fgFile(ctx).delete()
    }

    fun clearAll(ctx: Context) {
        bgFile(ctx).delete()
        fgFile(ctx).delete()
    }

    private fun writeAtomic(target: File, write: (FileOutputStream) -> Boolean): Boolean {
        val tmp = File(target.parentFile, target.name + ".tmp")
        return try {
            val ok = FileOutputStream(tmp).use { write(it) }
            if (ok && tmp.renameTo(target)) {
                true
            } else {
                tmp.delete()
                false
            }
        } catch (e: Exception) {
            tmp.delete()
            false
        }
    }

    /**
     * Home screen pe dikhane ke liye: HARDWARE bitmap (GPU memory mein, Java heap bachti hai). Bahut bada ya kharab ho to null.
     * Ye bitmap pixels padhne ke liye nahi, sirf draw ke liye hai.
     */
    fun loadForDisplay(file: File, maxDim: Int = MAX_DIM): Bitmap? {
        if (!file.exists()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2
            val hw = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.HARDWARE
            }
            BitmapFactory.decodeFile(file.absolutePath, hw) ?: loadSoftware(file, maxDim)
        } catch (e: Exception) {
            loadSoftware(file, maxDim)
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** Editor ke liye (pixels edit / compose hote hain): software bitmap. */
    fun loadSoftware(file: File, maxDim: Int = MAX_DIM): Bitmap? {
        if (!file.exists()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }
}
