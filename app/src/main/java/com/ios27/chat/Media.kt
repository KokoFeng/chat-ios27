package com.ios27.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.TextView
import android.widget.VideoView
import java.io.ByteArrayOutputStream
import java.io.File

/** Foto, stiker, dan video disimpan di cache HP dan diambil dari database hanya saat dibutuhkan. */
object MediaCache {
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val thumbs = HashMap<String, Bitmap?>()

    private fun dir(c: Context) = File(c.cacheDir, "media").also { it.mkdirs() }
    fun ext(type: String) = when (type) { "video" -> "mp4"; "sticker" -> "png"; else -> "jpg" }
    fun file(c: Context, cid: String, key: String, type: String) = File(dir(c), "${cid}_$key.${ext(type)}")
    fun put(c: Context, cid: String, key: String, type: String, bytes: ByteArray) { try { file(c, cid, key, type).writeBytes(bytes) } catch (_: Throwable) { } }
    fun thumb(key: String, b64: String): Bitmap? = thumbs.getOrPut(key) { b64ToBitmap(b64) }

    /** Ambil berkas media (dari cache atau dari database). cb dipanggil di thread utama. */
    fun fetch(c: Context, cid: String, key: String, type: String, cb: (File?) -> Unit) {
        val f = file(c, cid, key, type)
        if (f.exists() && f.length() > 0L) { cb(f); return }
        Net.bg {
            val v = Db.get(c, "/media/$cid/$key") as? String
            var out: File? = null
            if (v != null) { try { f.writeBytes(Base64.decode(v, Base64.DEFAULT)); out = f } catch (_: Throwable) { } }
            Net.ui { cb(out) }
        }
    }

    fun decodeSampled(f: File, maxPx: Int): Bitmap? {
        val k = f.path + "#" + maxPx
        val hit = cache.get(k)
        if (hit != null) return hit
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxPx) s *= 2
        val b = try { BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s }) } catch (_: Throwable) { null }
        if (b != null) cache.put(k, b)
        return b
    }
}

object MediaPrep {
    class Out(val bytes: ByteArray, val thumb: String, val w: Int, val h: Int, val dur: Long = 0L)

    private fun thumbOf(src: Bitmap): String {
        val tw = 40
        val th = (tw.toFloat() * src.height / src.width).toInt().coerceAtLeast(1)
        val t = Bitmap.createScaledBitmap(src, tw, th, true)
        val bo = ByteArrayOutputStream(); t.compress(Bitmap.CompressFormat.JPEG, 40, bo)
        return Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP)
    }

    /** Foto diperkecil dan dikompres (JPEG), stiker disimpan PNG agar transparan. */
    fun image(c: Context, uri: Uri, maxPx: Int, png: Boolean): Out? {
        try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxPx) s *= 2
            var bmp = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) } ?: return null
            val rot = (try {
                c.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            } catch (_: Throwable) { null }) ?: ExifInterface.ORIENTATION_NORMAL
            val deg = when (rot) { ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f; ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f }
            if (deg != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true)
            val scale = minOf(1f, maxPx.toFloat() / maxOf(bmp.width, bmp.height))
            val bw = (bmp.width * scale).toInt().coerceAtLeast(1); val bh = (bmp.height * scale).toInt().coerceAtLeast(1)
            val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, bw, bh, true) else bmp
            val bo = ByteArrayOutputStream()
            if (png) scaled.compress(Bitmap.CompressFormat.PNG, 100, bo) else scaled.compress(Bitmap.CompressFormat.JPEG, 72, bo)
            return Out(bo.toByteArray(), thumbOf(scaled), bw, bh)
        } catch (_: Throwable) { return null }
    }

    fun imageFile(f: File): Out? {
        return try {
            val b = BitmapFactory.decodeFile(f.path) ?: return null
            Out(f.readBytes(), thumbOf(b), b.width, b.height)
        } catch (_: Throwable) { null }
    }

    private fun sizeOf(c: Context, uri: Uri): Long =
        try { c.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else -1L } ?: -1L } catch (_: Throwable) { -1L }

    /** Video dikirim apa adanya (tanpa kompres), jadi dibatasi sekitar 3 MB karena disimpan di database. */
    fun video(c: Context, uri: Uri): Pair<Out?, String?> {
        val max = 3_000_000L
        val size = sizeOf(c, uri)
        if (size > max) return Pair(null, "This video is ${size / 1_000_000} MB. Videos up to 3 MB (a few seconds) can be sent. Trim it or choose a shorter clip.")
        val bytes = (try { c.contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch (_: Throwable) { null })
            ?: return Pair(null, "Could not read the video.")
        if (bytes.size > max) return Pair(null, "This video is larger than 3 MB. Trim it or choose a shorter clip.")
        var frame: Bitmap? = null; var dur = 0L; var w = 0; var h = 0
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(c, uri)
            frame = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
        } catch (_: Throwable) { } finally { try { r.release() } catch (_: Throwable) { } }
        val f = frame ?: Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.DKGRAY) }
        if (w <= 0 || h <= 0) { w = f.width; h = f.height }
        return Pair(Out(bytes, thumbOf(f), w, h, dur), null)
    }
}

/** Perpustakaan stiker milik pengguna: gambar kecil yang disimpan di HP dan bisa dikirim berulang. */
object Stickers {
    fun dir(c: Context) = File(c.filesDir, "stickers").also { it.mkdirs() }
    fun list(c: Context): List<File> = (dir(c).listFiles() ?: emptyArray()).sortedBy { it.name }
    fun add(c: Context, uri: Uri): Boolean {
        val o = MediaPrep.image(c, uri, 256, true) ?: return false
        return try { File(dir(c), "s" + System.currentTimeMillis() + ".png").writeBytes(o.bytes); true } catch (_: Throwable) { false }
    }
}

/** Foto, video, atau stiker di dalam percakapan. */
class MediaBubble(ctx: Context) : FrameLayout(ctx) {
    private val img = ImageView(ctx)
    private val play = TextView(ctx)
    private val dur = TextView(ctx)
    private var key = ""

    init {
        clipToOutline = true
        addView(img, LayoutParams(-1, -1))
        play.text = "▶"; play.textSize = 22f; play.setTextColor(Color.WHITE); play.gravity = Gravity.CENTER
        play.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x88000000.toInt()) }
        addView(play, LayoutParams(ctx.dp(52f), ctx.dp(52f), Gravity.CENTER))
        dur.textSize = 12f; dur.setTextColor(Color.WHITE); dur.setPadding(ctx.dp(6f), ctx.dp(2f), ctx.dp(6f), ctx.dp(2f))
        dur.background = GradientDrawable().apply { setColor(0x88000000.toInt()); cornerRadius = ctx.dpf(8f) }
        addView(dur, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, ctx.dp(8f), ctx.dp(8f)) })
    }

    fun bind(m: Msg, cid: String) {
        key = m.key
        val sticker = m.type == "sticker"
        val w = context.dp(if (sticker) 150f else 230f)
        var h = if (sticker) w else if (m.w > 0 && m.h > 0) (w * m.h.toFloat() / m.w).toInt() else w
        h = h.coerceIn(context.dp(110f), context.dp(320f))
        layoutParams = LinearLayout.LayoutParams(w, h)
        background = if (sticker) null else GradientDrawable().apply { setColor(Theme.bubbleIn); cornerRadius = context.dpf(18f) }
        img.scaleType = if (sticker) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
        play.visibility = if (m.type == "video") View.VISIBLE else View.GONE
        dur.visibility = if (m.type == "video" && m.dur > 0L) View.VISIBLE else View.GONE
        if (m.dur > 0L) dur.text = "%d:%02d".format(m.dur / 1000 / 60, (m.dur / 1000) % 60)
        img.setImageBitmap(MediaCache.thumb(m.key, m.thumb))
        if (m.type != "video") {
            MediaCache.fetch(context, cid, m.key, m.type) { f ->
                if (key == m.key && f != null) img.setImageBitmap(MediaCache.decodeSampled(f, 700))
            }
        }
    }
}

private class StickerTile(ctx: Context) : FrameLayout(ctx) {
    private val iv = ImageView(ctx)
    private val tv = TextView(ctx)

    init {
        layoutParams = AbsListView.LayoutParams(-1, ctx.dp(80f))
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.setPadding(ctx.dp(4f), ctx.dp(4f), ctx.dp(4f), ctx.dp(4f))
        addView(iv, LayoutParams(-1, -1))
        tv.text = "+"; tv.textSize = 30f; tv.setTextColor(Theme.blue); tv.gravity = Gravity.CENTER
        addView(tv, LayoutParams(-1, -1))
    }

    fun setAdd() {
        iv.setImageDrawable(null); tv.visibility = View.VISIBLE
        background = GradientDrawable().apply { setColor(Theme.card); cornerRadius = context.dpf(12f) }
    }

    fun setSticker(f: File) {
        tv.visibility = View.GONE; background = null
        iv.setImageBitmap(MediaCache.decodeSampled(f, 200))
    }
}

class StickerPanel(private val act: MainActivity, private val onPick: (File) -> Unit, private val onAdd: () -> Unit) : LinearLayout(act) {
    private val grid = GridView(act)
    private var files: List<File> = emptyList()

    init {
        orientation = VERTICAL
        setBackgroundColor(Theme.field)
        grid.numColumns = 4
        grid.verticalSpacing = act.dp(8f); grid.horizontalSpacing = act.dp(8f)
        grid.setPadding(act.dp(10f), act.dp(10f), act.dp(10f), act.dp(10f))
        grid.adapter = object : BaseAdapter() {
            override fun getCount() = files.size + 1
            override fun getItem(i: Int) = i
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, cv: View?, parent: ViewGroup?): View {
                val t = (cv as? StickerTile) ?: StickerTile(act)
                if (i == 0) t.setAdd() else t.setSticker(files[i - 1])
                return t
            }
        }
        grid.setOnItemClickListener { _, _, pos, _ -> if (pos == 0) onAdd() else onPick(files[pos - 1]) }
        grid.setOnItemLongClickListener { _, _, pos, _ ->
            if (pos > 0) {
                val f = files[pos - 1]
                IosDialog(act).title("Delete sticker?").button("Cancel").button("Delete", 2) { f.delete(); refresh() }.show()
                true
            } else false
        }
        addView(grid, LayoutParams(-1, -1))
        refresh()
    }

    fun refresh() {
        files = Stickers.list(act)
        (grid.adapter as BaseAdapter).notifyDataSetChanged()
    }
}

/** Tampilan penuh foto atau pemutar video. */
class MediaViewScreen(act: MainActivity, private val cid: String, private val m: Msg) : Screen(act) {
    override fun build(): View {
        val root = FrameLayout(act).apply { setBackgroundColor(Color.BLACK) }
        val msg = TextView(act).apply { text = "Loading…"; textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER }
        root.addView(msg, FrameLayout.LayoutParams(-1, -1))
        root.addView(TextView(act).apply {
            text = "‹ Close"; textSize = 17f; setTextColor(Color.WHITE); setPadding(act.dp(18f), act.dp(18f), act.dp(18f), act.dp(18f)); setOnClickListener { act.pop() }
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START))
        MediaCache.fetch(act, cid, m.key, m.type) { f ->
            if (f == null) { msg.text = "Could not load this file."; return@fetch }
            root.removeView(msg)
            if (m.type == "video") {
                val vv = VideoView(act)
                val mc = MediaController(act)
                mc.setAnchorView(vv)
                vv.setMediaController(mc)
                vv.setVideoPath(f.path)
                vv.setOnPreparedListener { it.start() }
                root.addView(vv, 0, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
            } else {
                root.addView(ImageView(act).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setImageBitmap(MediaCache.decodeSampled(f, 2048)) }, 0, FrameLayout.LayoutParams(-1, -1))
            }
        }
        return root
    }
}
