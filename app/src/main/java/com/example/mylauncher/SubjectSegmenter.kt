package com.example.mylauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * On-device subject cutout, bina kisi model / network ke. Ye AI segmentation NAHI hai: ye ek classical algorithm hai:
 *
 *  1. Image ko chhota karo (~192 px) aur upar ki patti + upar ke hisse ke side kinaron ke colors ko "background" maano.
 *  2. Un colors ke 3 clusters (k-means) bana ke background region ko kinaron se andar ki taraf grow karo, jab tak
 *     colors cluster ke paas aur aas-paas ke pixels se milte-julte hain (sky, deewar jaisi smooth background ke liye).
 *  3. Jo bacha wahi subject: speckle saaf, chhote tukde hatao, andar ke ched bharo, edge 1px andar (halo na aaye) + feather.
 *
 * Achha kaam karta hai jab subject background se saaf alag ho (pahad vs aasmaan, plain deewar ke saamne insaan).
 * Complex / busy background pe galat ho sakta hai: tab result ko brush se theek karo. Fail ho to message ke saath mask null.
 * Mask ALPHA_8 hai (alpha = subject).
 */
object SubjectSegmenter {

    class Result(val mask: Bitmap?, val message: String)

    private const val ANALYSIS = 192

    fun segment(src: Bitmap): Result {
        if (src.width < 16 || src.height < 16) return Result(null, "Image bahut chhoti hai")
        var small: Bitmap? = null
        try {
            val scale = ANALYSIS.toFloat() / max(src.width, src.height)
            val w = max(8, (src.width * scale).toInt())
            val h = max(8, (src.height * scale).toInt())
            small = Bitmap.createScaledBitmap(src, w, h, true)
            val px = IntArray(w * h)
            small.getPixels(px, 0, w, 0, 0, w, h)
            return run(px, w, h)
        } catch (e: OutOfMemoryError) {
            return Result(null, "Memory kam padi")
        } catch (e: Exception) {
            return Result(null, "Auto cutout fail ho gaya")
        } finally {
            if (small != null && small !== src) small.recycle()
        }
    }

    private fun run(px: IntArray, w: Int, h: Int): Result {
        val n = w * h
        val r = IntArray(n)
        val g = IntArray(n)
        val b = IntArray(n)
        for (i in 0 until n) {
            r[i] = (px[i] shr 16) and 0xFF
            g[i] = (px[i] shr 8) and 0xFF
            b[i] = px[i] and 0xFF
        }

        // 1) Seeds: upar ki row + upar ke 40% ke left / right kinare
        val seeds = ArrayList<Int>()
        for (x in 0 until w) seeds.add(x)
        val sideRows = (h * 0.4f).toInt().coerceAtLeast(1)
        for (y in 1 until sideRows) {
            seeds.add(y * w)
            seeds.add(y * w + (w - 1))
        }

        // 2) k-means (k = 3) seed colors pe
        val k = 3
        val mr = FloatArray(k)
        val mg = FloatArray(k)
        val mb = FloatArray(k)
        for (c in 0 until k) {
            val s = seeds[(seeds.size - 1) * c / (k - 1)]
            mr[c] = r[s].toFloat()
            mg[c] = g[s].toFloat()
            mb[c] = b[s].toFloat()
        }
        val assign = IntArray(seeds.size)
        for (iter in 0 until 8) {
            for (si in seeds.indices) {
                val p = seeds[si]
                var best = 0
                var bestD = Float.MAX_VALUE
                for (c in 0 until k) {
                    val d = dist(r[p], g[p], b[p], mr[c], mg[c], mb[c])
                    if (d < bestD) {
                        bestD = d
                        best = c
                    }
                }
                assign[si] = best
            }
            val sr = FloatArray(k)
            val sg = FloatArray(k)
            val sb = FloatArray(k)
            val cnt = IntArray(k)
            for (si in seeds.indices) {
                val p = seeds[si]
                val c = assign[si]
                sr[c] += r[p].toFloat()
                sg[c] += g[p].toFloat()
                sb[c] += b[p].toFloat()
                cnt[c]++
            }
            for (c in 0 until k) {
                if (cnt[c] > 0) {
                    mr[c] = sr[c] / cnt[c]
                    mg[c] = sg[c] / cnt[c]
                    mb[c] = sb[c] / cnt[c]
                }
            }
        }
        var spread = 0f
        for (si in seeds.indices) {
            val p = seeds[si]
            val c = assign[si]
            spread += dist(r[p], g[p], b[p], mr[c], mg[c], mb[c])
        }
        spread /= max(seeds.size, 1)
        val clusterLimit = (spread * 2.2f + 14f).coerceIn(30f, 70f)
        val stepLimit = 16f

        fun minCluster(p: Int): Float {
            var best = Float.MAX_VALUE
            for (c in 0 until k) best = min(best, dist(r[p], g[p], b[p], mr[c], mg[c], mb[c]))
            return best
        }

        // 3) Background region grow (BFS): kinaron se andar, jab tak colors milte-julte hain
        val bg = BooleanArray(n)
        val queue = IntArray(n)
        var head = 0
        var tail = 0

        fun grow(p: Int, q: Int) {
            if (bg[q]) return
            if (dist(r[p], g[p], b[p], r[q].toFloat(), g[q].toFloat(), b[q].toFloat()) > stepLimit) return
            if (minCluster(q) > clusterLimit * 1.35f) return
            bg[q] = true
            queue[tail++] = q
        }

        for (s in seeds) {
            if (!bg[s] && minCluster(s) <= clusterLimit) {
                bg[s] = true
                queue[tail++] = s
            }
        }
        while (head < tail) {
            val p = queue[head++]
            val x = p % w
            val y = p / w
            if (x > 0) grow(p, p - 1)
            if (x < w - 1) grow(p, p + 1)
            if (y > 0) grow(p, p - w)
            if (y < h - 1) grow(p, p + w)
        }

        // 4) Subject = background nahi
        var fg = BooleanArray(n) { !bg[it] }
        fg = open(fg, w, h)
        fg = close(fg, w, h)
        fg = keepBigComponents(fg, w, h, (n * 0.015f).toInt().coerceAtLeast(20))
        fg = fillHoles(fg, w, h)
        fg = erode(fg, w, h) // edge 1px andar: background ka halo na aaye

        var area = 0
        for (v in fg) if (v) area++
        val ratio = area.toFloat() / n
        if (ratio < 0.02f) return Result(null, "Subject nahi mila (background se alag nahi dikha). Brush se banao ya PNG lagao")
        if (ratio > 0.85f) return Result(null, "Poori image subject lag rahi hai. Brush se banao ya PNG lagao")

        // 5) Feather + ALPHA_8 mask
        var alpha = IntArray(n) { if (fg[it]) 255 else 0 }
        alpha = boxBlur(alpha, w, h)
        alpha = boxBlur(alpha, w, h)
        val bytes = ByteArray(n) { alpha[it].toByte() }
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        mask.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(bytes))
        return Result(mask, "Auto cutout ban gaya (${(ratio * 100).toInt()}% subject). Zarurat ho to brush se theek karo")
    }

    private fun dist(r1: Int, g1: Int, b1: Int, r2: Float, g2: Float, b2: Float): Float {
        val dr = r1 - r2
        val dg = g1 - g2
        val db = b1 - b2
        return sqrt(2f * dr * dr + 4f * dg * dg + 3f * db * db) / 3f
    }

    // ---- morphology (3x3) ----
    private fun erode(s: BooleanArray, w: Int, h: Int): BooleanArray {
        val o = BooleanArray(s.size)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            o[i] = s[i] && s[i - 1] && s[i + 1] && s[i - w] && s[i + w]
        }
        return o
    }

    private fun dilate(s: BooleanArray, w: Int, h: Int): BooleanArray {
        val o = BooleanArray(s.size)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            o[i] = s[i] || (x > 0 && s[i - 1]) || (x < w - 1 && s[i + 1]) || (y > 0 && s[i - w]) || (y < h - 1 && s[i + w])
        }
        return o
    }

    private fun open(s: BooleanArray, w: Int, h: Int) = dilate(erode(s, w, h), w, h)
    private fun close(s: BooleanArray, w: Int, h: Int) = erode(dilate(s, w, h), w, h)

    /** Chhote tukde (noise) hatao: sirf [minArea] se bade connected components rakho. */
    private fun keepBigComponents(s: BooleanArray, w: Int, h: Int, minArea: Int): BooleanArray {
        val n = s.size
        val label = IntArray(n)
        val out = BooleanArray(n)
        val stack = IntArray(n)
        var next = 1
        for (start in 0 until n) {
            if (!s[start] || label[start] != 0) continue
            var sp = 0
            stack[sp++] = start
            label[start] = next
            val members = ArrayList<Int>()
            while (sp > 0) {
                val p = stack[--sp]
                members.add(p)
                val x = p % w
                val y = p / w
                if (x > 0 && s[p - 1] && label[p - 1] == 0) { label[p - 1] = next; stack[sp++] = p - 1 }
                if (x < w - 1 && s[p + 1] && label[p + 1] == 0) { label[p + 1] = next; stack[sp++] = p + 1 }
                if (y > 0 && s[p - w] && label[p - w] == 0) { label[p - w] = next; stack[sp++] = p - w }
                if (y < h - 1 && s[p + w] && label[p + w] == 0) { label[p + w] = next; stack[sp++] = p + w }
            }
            if (members.size >= minArea) for (m in members) out[m] = true
            next++
        }
        return out
    }

    /** Subject ke andar bache background ke ched bharo (kinare se na judne wale non-subject pixels). */
    private fun fillHoles(s: BooleanArray, w: Int, h: Int): BooleanArray {
        val n = s.size
        val outside = BooleanArray(n)
        val stack = IntArray(n)
        var sp = 0
        fun push(i: Int) {
            if (!s[i] && !outside[i]) {
                outside[i] = true
                stack[sp++] = i
            }
        }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (sp > 0) {
            val p = stack[--sp]
            val x = p % w
            val y = p / w
            if (x > 0) push(p - 1)
            if (x < w - 1) push(p + 1)
            if (y > 0) push(p - w)
            if (y < h - 1) push(p + w)
        }
        return BooleanArray(n) { s[it] || !outside[it] }
    }

    private fun boxBlur(a: IntArray, w: Int, h: Int): IntArray {
        val o = IntArray(a.size)
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0
            var cnt = 0
            for (dy in -1..1) for (dx in -1..1) {
                val xx = x + dx
                val yy = y + dy
                if (xx in 0 until w && yy in 0 until h) {
                    sum += a[yy * w + xx]
                    cnt++
                }
            }
            o[y * w + x] = sum / cnt
        }
        return o
    }

    // ---------------------------------------------------------------------------------------
    // Mask -> foreground PNG (editor / auto dono ke liye)
    // ---------------------------------------------------------------------------------------
    /** Background pixels ko mask ke hisaab se kaato: transparent PNG bitmap (background jaisi hi size). */
    fun composeForeground(bg: Bitmap, mask: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(bg.width, bg.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        c.drawBitmap(bg, 0f, 0f, p)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        c.drawBitmap(mask, null, Rect(0, 0, bg.width, bg.height), p)
        return out
    }

    /** Mask ko nayi size pe (ALPHA_8 hi rakhte hue, bilinear). */
    fun scaleMask(mask: Bitmap, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val c = Canvas(out)
        c.drawBitmap(mask, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }
}
