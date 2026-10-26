package com.ios27.chat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

/** Teman yang diblokir (hanya di HP ini: pesannya disembunyikan dan panggilannya diabaikan). */
object Blocked {
    private fun set(c: Context): MutableSet<String> = Cfg.get(c, "blocked").split(",").filter { it.isNotEmpty() }.toMutableSet()
    fun has(c: Context, uid: String) = set(c).contains(uid)
    fun toggle(c: Context, uid: String): Boolean {
        val s = set(c)
        val now = if (s.contains(uid)) { s.remove(uid); false } else { s.add(uid); true }
        Cfg.set(c, "blocked", s.joinToString(","))
        return now
    }
}

/** Jumlah percakapan lain yang belum dibaca (untuk lencana biru di sudut kiri atas layar chat). */
object Unread {
    var convs: List<Conv> = emptyList()
    fun count(me: String, except: String): Int =
        convs.count { it.cid != except && it.from.isNotEmpty() && it.from != me && it.ts > it.read }
}

class MicIcon(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        p.color = Theme.sub; p.style = Paint.Style.FILL
        c.drawRoundRect(RectF(w * 0.38f, h * 0.16f, w * 0.62f, h * 0.56f), w * 0.12f, w * 0.12f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = w * 0.07f; p.strokeCap = Paint.Cap.ROUND
        c.drawArc(RectF(w * 0.28f, h * 0.32f, w * 0.72f, h * 0.70f), 0f, 180f, false, p)
        c.drawLine(w * 0.5f, h * 0.70f, w * 0.5f, h * 0.84f, p)
    }
}

/** Tombol bulat bergaya kaca (tombol kembali, panggilan, plus di iOS 26). */
fun Context.circleButton(glyph: String, sizeDp: Float, textSp: Float, onClick: () -> Unit): TextView = TextView(this).apply {
    text = glyph; textSize = textSp; setTextColor(Theme.blue); gravity = Gravity.CENTER
    background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(if (Theme.dark) 0xCC2C2C2E.toInt() else 0xCCFFFFFF.toInt()); setStroke(1, Theme.sep)
    }
    elevation = dpf(3f)
    setOnClickListener { onClick() }
    layoutParams = FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
}

fun Context.glassBack(onClick: () -> Unit): FrameLayout = FrameLayout(this).apply {
    setPadding(dp(12f), dp(10f), dp(12f), dp(6f))
    addView(circleButton("‹", 42f, 26f, onClick), FrameLayout.LayoutParams(dp(42f), dp(42f), Gravity.START))
}
