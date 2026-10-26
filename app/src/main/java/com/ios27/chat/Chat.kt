package com.ios27.chat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.widget.PopupWindow
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.app.Activity
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.content.Intent
import android.util.Base64
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun isEmojiOnly(s: String): Boolean {
    val t = s.trim()
    if (t.isEmpty()) return false
    var n = 0
    var i = 0
    while (i < t.length) {
        val cp = t.codePointAt(i); i += Character.charCount(cp)
        if (cp == 0x200D || cp == 0xFE0F || cp == 0x20E3) continue
        val emoji = cp >= 0x1F000 || cp in 0x2190..0x21FF || cp in 0x2300..0x23FF || cp in 0x2600..0x27BF || cp in 0x2B00..0x2BFF
        if (!emoji) return false
        n++
    }
    return n in 1..3
}

/** Gelembung pesan gaya iMessage: biru untuk kamu, abu-abu untuk teman; emoji tunggal tampil besar tanpa gelembung. */
class BubbleView(ctx: Context) : View(ctx) {
    private var text = ""
    private var mine = false
    private var tail = false
    private var sizeSp = 17f
    private var emojiOnly = false
    private var reacts = ""
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val bp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private var layout: StaticLayout? = null
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun set(t: String, m: Boolean, tl: Boolean, size: Float, r: String = "") {
        text = t; mine = m; tail = tl; sizeSp = size; emojiOnly = isEmojiOnly(t); reacts = r
        requestLayout(); invalidate()
    }

    private fun topX() = if (reacts.isEmpty()) 0 else context.dp(14f)
    private fun padH() = if (emojiOnly) 0 else context.dp(13f)
    private fun padV() = if (emojiOnly) 0 else context.dp(8f)
    private fun tailPad() = if (emojiOnly) 0 else context.dp(6f)

    override fun onMeasure(ws: Int, hs: Int) {
        if (MeasureSpec.getMode(ws) == MeasureSpec.EXACTLY) {
            val sz = MeasureSpec.getSize(ws)
            tp.textSize = (if (emojiOnly) sizeSp * 2.4f else sizeSp) * resources.displayMetrics.scaledDensity
            tp.typeface = Emo.typeface(context) ?: Typeface.DEFAULT
            tp.color = if (mine) Color.WHITE else Theme.text
            val l0 = StaticLayout.Builder.obtain(text, 0, text.length, tp, (sz - padH() * 2 - tailPad()).coerceAtLeast(1)).build()
            layout = l0
            setMeasuredDimension(sz, l0.height + padV() * 2 + topX())
            return
        }
        val avail = MeasureSpec.getSize(ws).coerceAtLeast(context.dp(120f))
        val maxW = (avail * 0.78f).toInt()
        val dens = resources.displayMetrics.scaledDensity
        tp.textSize = (if (emojiOnly) sizeSp * 2.4f else sizeSp) * dens
        tp.typeface = Emo.typeface(context) ?: Typeface.DEFAULT
        tp.color = if (mine) Color.WHITE else Theme.text
        val maxText = (maxW - padH() * 2 - tailPad()).coerceAtLeast(context.dp(40f))
        val want = Math.ceil(Layout.getDesiredWidth(text, tp).toDouble()).toInt().coerceIn(1, maxText)
        val l = StaticLayout.Builder.obtain(text, 0, text.length, tp, want).build()
        layout = l
        var mw = 0f
        for (i in 0 until l.lineCount) mw = maxOf(mw, l.getLineWidth(i))
        setMeasuredDimension(Math.ceil(mw.toDouble()).toInt() + padH() * 2 + tailPad(), l.height + padV() * 2 + topX())
    }

    override fun onDraw(c: Canvas) {
        val l = layout ?: return
        val w = width.toFloat(); val h = height.toFloat()
        val left = if (mine) 0f else tailPad().toFloat()
        val right = if (mine) w - tailPad() else w
        val top = topX().toFloat()
        if (!emojiOnly) {
            bg.color = if (mine) Theme.blue else Theme.bubbleIn
            c.drawRoundRect(RectF(left, top, right, h), context.dpf(18f), context.dpf(18f), bg)
            if (tail) {
                path.reset()
                if (mine) {
                    path.moveTo(right - context.dpf(14f), h - context.dpf(16f)); path.lineTo(right + context.dpf(5f), h); path.lineTo(right - context.dpf(20f), h - context.dpf(4f))
                } else {
                    path.moveTo(left + context.dpf(14f), h - context.dpf(16f)); path.lineTo(left - context.dpf(5f), h); path.lineTo(left + context.dpf(20f), h - context.dpf(4f))
                }
                path.close(); c.drawPath(path, bg)
            }
        }
        c.save(); c.translate(left + padH(), top + padV()); l.draw(c); c.restore()
        if (reacts.isNotEmpty()) {
            bp.textSize = 13f * resources.displayMetrics.scaledDensity
            bp.typeface = Emo.typeface(context) ?: Typeface.DEFAULT
            bp.color = Theme.text
            val bw = bp.measureText(reacts) + context.dpf(14f); val bh = context.dpf(24f)
            val bx = (if (mine) left - context.dpf(8f) else right - bw + context.dpf(8f)).coerceIn(0f, maxOf(0f, w - bw))
            bg.color = Theme.card
            c.drawRoundRect(RectF(bx, 0f, bx + bw, bh), bh / 2f, bh / 2f, bg)
            bg.style = Paint.Style.STROKE; bg.strokeWidth = context.dpf(1f); bg.color = Theme.sep
            c.drawRoundRect(RectF(bx, 0f, bx + bw, bh), bh / 2f, bh / 2f, bg)
            bg.style = Paint.Style.FILL
            c.drawText(reacts, bx + context.dpf(7f), bh / 2f - (bp.ascent() + bp.descent()) / 2f, bp)
        }
    }
}

/** Papan emoji bawaan (memakai font emoji kustom bila dipilih). */
class EmojiPanel(ctx: Context, private val onPick: (String) -> Unit) : LinearLayout(ctx) {
    private val cats: List<Pair<String, List<IntRange>>> = listOf(
        "😀" to listOf(0x1F600..0x1F64F, 0x1F910..0x1F92F, 0x1F970..0x1F97A),
        "👍" to listOf(0x1F440..0x1F487, 0x270A..0x270D),
        "❤" to listOf(0x1F493..0x1F49F, 0x1F9E1..0x1F9E1, 0x2728..0x2728, 0x1F525..0x1F525, 0x1F4A5..0x1F4AF),
        "🐶" to listOf(0x1F400..0x1F43F, 0x1F980..0x1F9AF, 0x1F330..0x1F344),
        "🍔" to listOf(0x1F345..0x1F37F, 0x1F950..0x1F96F),
        "⚽" to listOf(0x1F380..0x1F3CF),
        "🚗" to listOf(0x1F680..0x1F6C5, 0x1F3E0..0x1F3F0, 0x1F30D..0x1F320),
        "💡" to listOf(0x1F4A1..0x1F4FF, 0x1F500..0x1F53D)
    )
    private val grid = GridView(ctx)
    private var items: List<String> = emptyList()
    private val tabs = LinearLayout(ctx)

    init {
        orientation = VERTICAL
        setBackgroundColor(Theme.field)
        grid.numColumns = 8; grid.verticalSpacing = ctx.dp(4f); grid.setPadding(ctx.dp(6f), ctx.dp(6f), ctx.dp(6f), 0)
        grid.adapter = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(i: Int) = items[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, cv: View?, parent: ViewGroup?): View {
                val tv = (cv as? TextView) ?: TextView(context).apply { textSize = 26f; gravity = Gravity.CENTER; setPadding(0, context.dp(4f), 0, context.dp(4f)) }
                tv.text = items[i]; Emo.apply(tv)
                return tv
            }
        }
        grid.setOnItemClickListener { _, _, pos, _ -> onPick(items[pos]) }
        addView(grid, LayoutParams(-1, 0, 1f))
        tabs.orientation = HORIZONTAL
        cats.forEachIndexed { i, c ->
            tabs.addView(TextView(ctx).apply { text = c.first; textSize = 22f; gravity = Gravity.CENTER; setOnClickListener { show(i) } }, LayoutParams(0, ctx.dp(44f), 1f))
        }
        addView(tabs, LayoutParams(-1, -2))
        show(0)
    }

    private fun show(i: Int) {
        val p = Paint(); p.typeface = Emo.typeface(context)
        val out = ArrayList<String>()
        for (r in cats[i].second) for (cp in r) {
            val s = String(Character.toChars(cp))
            if (p.hasGlyph(s)) out.add(s)
        }
        items = out
        (grid.adapter as BaseAdapter).notifyDataSetChanged()
        grid.setSelection(0)
        for (k in 0 until tabs.childCount) tabs.getChildAt(k).alpha = if (k == i) 1f else 0.45f
    }
}

class Msg(val key: String, val from: String, val text: String, val ts: Long, var failed: Boolean = false, val reacts: Map<String, String> = emptyMap(),
    val type: String = "text", val thumb: String = "", val mid: String = "", val w: Int = 0, val h: Int = 0, val dur: Long = 0L)
class Row(val type: Int, val msg: Msg? = null, val text: String = "", val tail: Boolean = false, val mine: Boolean = false, val topGap: Boolean = false)

class ChatScreen(act: MainActivity, private val cid: String, private val peer: String, private val peerName: String) : Screen(act) {
    private val me = Cfg.get(act, "uid")
    private var msgStream: Stream? = null
    private var metaStream: Stream? = null
    private var messages: List<Msg> = emptyList()
    private val pending = LinkedHashMap<String, Msg>()
    private var rows: List<Row> = emptyList()
    private var peerRead = 0L
    private var typing = false
    private var metaFirst = true
    private var lastPeerTyping = 0L
    private var lastTypingSent = 0L
    private var lastMarked = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val hideTyping = Runnable { typing = false; rebuild() }
    private val stopTyping = Runnable { setTyping(0L) }
    private lateinit var adapter: BaseAdapter
    private lateinit var list: ListView
    private lateinit var input: EditText
    private lateinit var sendBtn: TextView
    private lateinit var micView: View
    private lateinit var frame: FrameLayout
    private lateinit var column: LinearLayout
    private var topAvatar: AvatarView? = null
    private var tts: TextToSpeech? = null
    private var overlay: FrameLayout? = null
    private var dismissOverlay: (() -> Unit)? = null
    private lateinit var panel: EmojiPanel
    private lateinit var stickerPanel: StickerPanel
    private var panelOn = false

    private fun sizeSp(): Float = when (Cfg.int(act, "tsize", 1)) { 0 -> 15f; 2 -> 20f else -> 17f }

    private val s26: Boolean get() = Cfg.int(act, "style", 1) == 1

    override fun build(): View {
        frame = FrameLayout(act)
        column = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Theme.bg) }
        column.addView(topBar())
        adapter = object : BaseAdapter() {
            override fun getCount() = rows.size
            override fun getItem(i: Int) = rows[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getViewTypeCount() = 5
            override fun getItemViewType(i: Int) = rows[i].type
            override fun getView(i: Int, cv: View?, parent: ViewGroup?): View {
                val r = rows[i]
                return when (r.type) {
                    0 -> ((cv as? TextView) ?: TextView(act).apply { textSize = 12f; setTextColor(Theme.sub); gravity = Gravity.CENTER; setPadding(0, act.dp(12f), 0, act.dp(4f)) }).also { it.text = r.text }
                    2 -> ((cv as? TextView) ?: TextView(act).apply { textSize = 12f; setTextColor(Theme.sub); gravity = Gravity.END; setPadding(0, act.dp(2f), act.dp(18f), act.dp(2f)) }).also { it.text = r.text; it.setTextColor(if (r.text == "Not Delivered") 0xFFFF453A.toInt() else Theme.sub) }
                    3 -> bubbleRow(cv, null, "•••", false, true, true)
                    4 -> mediaRow(cv, r.msg!!, r.mine, r.topGap)
                    else -> bubbleRow(cv, r.msg, r.msg!!.text, r.mine, r.tail, r.topGap)
                }
            }
        }
        list = ListView(act).apply {
            divider = null; isVerticalScrollBarEnabled = false; adapter = this@ChatScreen.adapter
            transcriptMode = ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL; isStackFromBottom = true
            setOnTouchListener { _, _ -> hidePanelAndKeyboard(); false }
        }
        column.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        column.addView(bottomBar())
        panel = EmojiPanel(act) { e -> input.append(e) }
        panel.visibility = View.GONE
        column.addView(panel, LinearLayout.LayoutParams(-1, act.dp(260f)))
        stickerPanel = StickerPanel(act, { f -> sendSticker(f) }, { act.pickImage(53) })
        stickerPanel.visibility = View.GONE
        column.addView(stickerPanel, LinearLayout.LayoutParams(-1, act.dp(260f)))
        frame.addView(column, FrameLayout.LayoutParams(-1, -1))
        return frame
    }

    // ---------------------------------------------------------------- bilah atas dan bawah
    private fun topBar(): View {
        val bar = FrameLayout(act).apply { setPadding(act.dp(10f), act.dp(8f), act.dp(10f), act.dp(6f)); setBackgroundColor(Theme.bg) }
        if (s26) {
            bar.addView(act.circleButton("‹", 42f, 26f) { act.pop() }, FrameLayout.LayoutParams(act.dp(42f), act.dp(42f), Gravity.START or Gravity.CENTER_VERTICAL))
        } else {
            val left = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setOnClickListener { act.pop() } }
            left.addView(TextView(act).apply { text = "‹"; textSize = 36f; setTextColor(Theme.blue) })
            val n = Unread.count(me, cid)
            if (n > 0) left.addView(TextView(act).apply {
                text = if (n > 99) "99+" else n.toString(); textSize = 13f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                background = GradientDrawable().apply { setColor(Theme.blue); cornerRadius = act.dpf(12f) }
                setPadding(act.dp(8f), act.dp(2f), act.dp(8f), act.dp(2f))
            }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = act.dp(2f) })
            bar.addView(left, FrameLayout.LayoutParams(-2, act.dp(48f), Gravity.START or Gravity.CENTER_VERTICAL))
        }
        val center = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setOnClickListener { act.push(ProfileScreen(act, peer)) } }
        val av = AvatarView(act, 44f).apply { name = peerName; bmp = b64ToBitmap(Users.cached(peer)?.optString("avatar")) }
        topAvatar = av
        if (Users.cached(peer) == null) Users.fetch(act, peer) { p -> av.bmp = b64ToBitmap(p?.optString("avatar")) }
        center.addView(av, LinearLayout.LayoutParams(act.dp(44f), act.dp(44f)))
        center.addView(TextView(act).apply { text = "$peerName ›"; textSize = 12f; setTextColor(Theme.text); gravity = Gravity.CENTER; maxLines = 1; setPadding(0, act.dp(2f), 0, 0) })
        bar.addView(center, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL))
        if (s26) bar.addView(act.circleButton("☎", 42f, 20f) { act.startCall(cid, peer, peerName) }, FrameLayout.LayoutParams(act.dp(42f), act.dp(42f), Gravity.END or Gravity.CENTER_VERTICAL))
        else bar.addView(TextView(act).apply {
            text = "☎"; textSize = 26f; setTextColor(Theme.blue); gravity = Gravity.CENTER; setPadding(act.dp(10f), 0, act.dp(10f), 0)
            setOnClickListener { act.startCall(cid, peer, peerName) }
        }, FrameLayout.LayoutParams(-2, act.dp(48f), Gravity.END or Gravity.CENTER_VERTICAL))
        return bar
    }

    private fun bottomBar(): View {
        val wrap = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Theme.bg) }
        val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM; setPadding(act.dp(10f), act.dp(6f), act.dp(10f), act.dp(8f)) }
        if (s26) {
            row.addView(act.circleButton("+", 44f, 30f) { attachMenu(row.getChildAt(0)) }, LinearLayout.LayoutParams(act.dp(44f), act.dp(44f)).apply { rightMargin = act.dp(8f) })
        } else {
            row.addView(outlineButton("▦") { hidePanelAndKeyboard(); act.pickImage(51) }, LinearLayout.LayoutParams(act.dp(36f), act.dp(36f)).apply { rightMargin = act.dp(6f); bottomMargin = act.dp(4f) })
            row.addView(outlineButton("A") { attachMenu(row.getChildAt(1)) }, LinearLayout.LayoutParams(act.dp(36f), act.dp(36f)).apply { rightMargin = act.dp(8f); bottomMargin = act.dp(4f) })
        }
        val field = FrameLayout(act)
        input = EditText(act).apply {
            hint = if (s26) "Message" else "iMessage"; setHintTextColor(Theme.sub); setTextColor(Theme.text); textSize = sizeSp(); maxLines = 5
            background = GradientDrawable().apply { setColor(Theme.field); cornerRadius = act.dpf(if (s26) 22f else 18f); setStroke(1, Theme.sep) }
            setPadding(act.dp(14f), act.dp(10f), act.dp(46f), act.dp(10f)); Emo.apply(this)
            setOnClickListener { setPanel(false) }
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(e: Editable?) { onTyped(e?.toString() ?: "") }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        field.addView(input, FrameLayout.LayoutParams(-1, -2))
        micView = MicIcon(act).apply { setOnClickListener { dictate() } }
        field.addView(micView, FrameLayout.LayoutParams(act.dp(30f), act.dp(30f), Gravity.END or Gravity.BOTTOM).apply { rightMargin = act.dp(8f); bottomMargin = act.dp(7f) })
        sendBtn = TextView(act).apply {
            text = "↑"; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Theme.blue) }
            visibility = View.INVISIBLE; setOnClickListener { send() }
        }
        field.addView(sendBtn, FrameLayout.LayoutParams(act.dp(30f), act.dp(30f), Gravity.END or Gravity.BOTTOM).apply { rightMargin = act.dp(6f); bottomMargin = act.dp(6f) })
        row.addView(field, LinearLayout.LayoutParams(0, -2, 1f))
        wrap.addView(row)
        if (!s26) {
            val apps = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(act.dp(14f), act.dp(2f), act.dp(14f), act.dp(10f)) }
            val items = listOf(
                Triple("▦", 0xFFFFA726.toInt(), 0xFFE91E63.toInt()) to { hidePanelAndKeyboard(); act.pickImage(51) },
                Triple("▶", 0xFFFF5252.toInt(), 0xFFD32F2F.toInt()) to { hidePanelAndKeyboard(); act.pickVideo(52) },
                Triple("☺", 0xFFFFD54F.toInt(), 0xFFFFA000.toInt()) to { hideKeyboard(); setMode(2) },
                Triple("☻", 0xFF42A5F5.toInt(), 0xFF1E88E5.toInt()) to { hideKeyboard(); setMode(1) }
            )
            for ((spec, run) in items) apps.addView(appCircle(spec.first, spec.second, spec.third, run), LinearLayout.LayoutParams(act.dp(44f), act.dp(44f)).apply { rightMargin = act.dp(14f) })
            wrap.addView(apps)
        }
        return wrap
    }

    private fun outlineButton(glyph: String, run: () -> Unit): TextView = TextView(act).apply {
        text = glyph; textSize = 17f; setTextColor(Theme.sub); gravity = Gravity.CENTER
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT); setStroke(act.dp(1.5f), Theme.sub) }
        setOnClickListener { run() }
    }

    private fun appCircle(glyph: String, c1: Int, c2: Int, run: () -> Unit): TextView = TextView(act).apply {
        text = glyph; textSize = 20f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1, c2)).apply { shape = GradientDrawable.OVAL }
        setOnClickListener { run() }
    }

    private fun hideKeyboard() { (act.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0) }

    private fun dictate() {
        try {
            act.startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM), 54)
        } catch (_: Exception) { Toast.makeText(act, "Speech input is not available on this phone.", Toast.LENGTH_SHORT).show() }
    }

    // ---------------------------------------------------------------- tekan lama gaya iOS 26: gelembung terangkat
    private fun onLong(anchor: View, m: Msg) { if (s26) showLifted(anchor, m) else showPopup(anchor, m) }

    override fun onBack(): Boolean {
        val d = dismissOverlay
        if (overlay != null && d != null) { d(); return true }
        return false
    }

    private fun glassBg(r: Float) = GradientDrawable().apply {
        setColor(if (Theme.dark) 0xEE2C2C2E.toInt() else 0xEEF5F5F7.toInt()); cornerRadius = act.dpf(r); setStroke(1, Theme.sep)
    }

    private fun showLifted(anchor: View, m: Msg) {
        val isText = m.type == "text"
        val mineSent = m.from == me && !pending.containsKey(m.key)
        if (!isText && !mineSent) return
        if (overlay != null) return
        val ov = FrameLayout(act)
        overlay = ov
        val dim = View(act).apply { setBackgroundColor(0x99000000.toInt()); alpha = 0f }
        ov.addView(dim, FrameLayout.LayoutParams(-1, -1))
        val a = IntArray(2); anchor.getLocationInWindow(a)
        val f = IntArray(2); frame.getLocationInWindow(f)
        val ax = a[0] - f[0]; val ay = a[1] - f[1]
        val bw = anchor.width; val bh = anchor.height
        val mine = m.from == me
        val dismiss = {
            dim.animate().alpha(0f).setDuration(150).withEndAction { frame.removeView(ov); overlay = null; dismissOverlay = null }.start()
            anchor.alpha = 1f
            if (Build.VERSION.SDK_INT >= 31) column.setRenderEffect(null)
        }
        dismissOverlay = dismiss
        dim.setOnClickListener { dismiss() }

        // reaksi
        val pill = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(act.dp(8f), act.dp(6f), act.dp(8f), act.dp(6f)); background = glassBg(28f) }
        if (isText) for (e in listOf("❤️", "👍", "👎", "😂", "‼️", "❓")) {
            pill.addView(TextView(act).apply {
                text = e; textSize = 26f; gravity = Gravity.CENTER; setPadding(act.dp(7f), act.dp(2f), act.dp(7f), act.dp(2f)); Emo.apply(this)
                if (m.reacts[me] == e) background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x330A84FF) }
                setOnClickListener { react(m, e); dismiss() }
            })
        }

        // menu
        val menu = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; background = glassBg(24f); setPadding(0, act.dp(6f), 0, act.dp(6f)) }
        fun item(glyph: String, label: String, color: Int, run: () -> Unit) {
            val r = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(act.dp(18f), act.dp(12f), act.dp(18f), act.dp(12f)); foreground = act.ripple() }
            r.addView(TextView(act).apply { text = glyph; textSize = 18f; setTextColor(color); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(act.dp(30f), -2))
            r.addView(TextView(act).apply { text = label; textSize = 17f; setTextColor(color) })
            r.setOnClickListener { dismiss(); run() }
            menu.addView(r)
        }
        fun gap() { menu.addView(View(act).apply { setBackgroundColor(0x22808080) }, LinearLayout.LayoutParams(-1, act.dp(7f))) }
        if (isText) {
            item("↩", "Reply", Theme.text) {
                val q = m.text.replace('\n', ' ').take(80)
                input.setText("“$q”\n"); input.setSelection(input.text.length); input.requestFocus()
                (act.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, 0)
            }
            item("☺", "Send Sticker", Theme.text) { hideKeyboard(); setMode(2) }
            gap()
            item("❐", "Copy", Theme.text) {
                (act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("message", m.text))
                Toast.makeText(act, "Copied", Toast.LENGTH_SHORT).show()
            }
            item("A文", "Translate", Theme.text) {
                try {
                    val i = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain").putExtra(Intent.EXTRA_PROCESS_TEXT, m.text).putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                    act.startActivity(Intent.createChooser(i, "Translate"))
                } catch (_: Exception) { Toast.makeText(act, "No translation app found.", Toast.LENGTH_SHORT).show() }
            }
            item("⛶", "Select", Theme.text) {
                IosDialog(act).title("Select text").message(m.text).selectable()
                    .button("Copy all") { (act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("message", m.text)) }
                    .button("Done", 1).show()
            }
            item("♪", "Speak", Theme.text) {
                tts?.shutdown()
                tts = TextToSpeech(act) { st ->
                    if (st == TextToSpeech.SUCCESS) { tts?.language = java.util.Locale.getDefault(); tts?.speak(m.text, TextToSpeech.QUEUE_FLUSH, null, "msg") }
                }
            }
        }
        if (mineSent) {
            if (isText) gap()
            item("🗑", "Delete", 0xFFFF453A.toInt()) { Net.bg { Db.delete(act, "/chats/$cid/messages/${m.key}"); if (!isText) Db.delete(act, "/media/$cid/${m.key}") } }
        }

        // salinan gelembung yang terangkat
        val clone: View = if (isText) BubbleView(act).also { it.set(m.text, mine, true, sizeSp(), reactStr(m)) } else MediaBubble(act).also { it.bind(m, cid) }
        val menuW = act.dp(250f)
        pill.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        menu.measure(View.MeasureSpec.makeMeasureSpec(menuW, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        val pillW = pill.measuredWidth; val pillH = if (isText) pill.measuredHeight else 0
        val menuH = menu.measuredHeight
        val fw = frame.width; val fh = frame.height
        val gapPx = act.dp(10f)
        val minY = pillH + gapPx + act.dp(64f)
        val maxY = fh - menuH - gapPx - act.dp(16f) - bh
        var y = ay.coerceAtLeast(minY)
        if (maxY >= minY) y = y.coerceAtMost(maxY)
        val px = (if (mine) ax + bw - pillW else ax).coerceIn(act.dp(8f), maxOf(act.dp(8f), fw - pillW - act.dp(8f)))
        val mx = (if (mine) ax + bw - menuW else ax).coerceIn(act.dp(8f), maxOf(act.dp(8f), fw - menuW - act.dp(8f)))
        ov.addView(clone, FrameLayout.LayoutParams(bw, bh).apply { leftMargin = ax; topMargin = y })
        if (isText) ov.addView(pill, FrameLayout.LayoutParams(-2, -2).apply { leftMargin = px; topMargin = y - gapPx - pillH })
        ov.addView(menu, FrameLayout.LayoutParams(menuW, -2).apply { leftMargin = mx; topMargin = y + bh + gapPx })

        anchor.alpha = 0f
        if (Build.VERSION.SDK_INT >= 31) column.setRenderEffect(RenderEffect.createBlurEffect(14f, 14f, Shader.TileMode.CLAMP))
        frame.addView(ov, FrameLayout.LayoutParams(-1, -1))
        dim.animate().alpha(1f).setDuration(160).start()
        clone.animate().scaleX(1.05f).scaleY(1.05f).setDuration(240).setInterpolator(OvershootInterpolator(2f)).start()
        for (v in listOf<View>(pill, menu)) {
            v.alpha = 0f; v.scaleX = 0.85f; v.scaleY = 0.85f
            v.pivotX = v.measuredWidth / 2f; v.pivotY = if (v === pill) v.measuredHeight.toFloat() else 0f
            v.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(60).setDuration(240).setInterpolator(OvershootInterpolator(1.2f)).start()
        }
        anchor.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
    }

    private fun bubbleRow(cv: View?, m: Msg?, text: String, mine: Boolean, tail: Boolean, gap: Boolean): View {
        val row = (cv as? LinearLayout) ?: LinearLayout(act).apply { addView(BubbleView(act), LinearLayout.LayoutParams(-2, -2)) }
        val bv = row.getChildAt(0) as BubbleView
        row.gravity = if (mine) Gravity.END else Gravity.START
        row.setPadding(act.dp(10f), if (gap) act.dp(8f) else act.dp(2f), act.dp(10f), 0)
        bv.set(text, mine, tail, sizeSp(), reactStr(m))
        row.setOnClickListener { if (m != null && m.failed) resend(m) }
        bv.setOnLongClickListener { if (m != null) { onLong(bv, m); true } else false }
        return row
    }

    // ---------------------------------------------------------------- data
    override fun onShow() {
        if (msgStream == null) {
            msgStream = Stream(act, "/chats/$cid/messages", "orderBy=%22%24key%22&limitToLast=100") { tree ->
                val out = ArrayList<Msg>()
                if (tree != null) {
                    val ks = tree.keys()
                    while (ks.hasNext()) {
                        val k = ks.next(); val o = tree.optJSONObject(k) ?: continue
                        val rm = HashMap<String, String>()
                        val ro = o.optJSONObject("r")
                        if (ro != null) { val rk = ro.keys(); while (rk.hasNext()) { val u = rk.next(); rm[u] = ro.optString(u) } }
                        out.add(Msg(k, o.optString("from"), o.optString("text"), o.optLong("ts"), false, rm, o.optString("type", "text"), o.optString("thumb"), o.optString("mid"), o.optInt("w"), o.optInt("h"), o.optLong("dur")))
                        pending.remove(k)
                    }
                }
                out.sortBy { it.key }
                messages = out
                rebuild()
                markRead()
            }.also { it.start() }
            metaStream = Stream(act, "/chats/$cid/meta", "") { tree ->
                peerRead = tree?.optJSONObject("read")?.optLong(peer) ?: 0L
                val tv = tree?.optJSONObject("typing")?.optLong(peer) ?: 0L
                if (metaFirst) { metaFirst = false; lastPeerTyping = tv }
                else if (tv != 0L && tv != lastPeerTyping) {
                    typing = true; handler.removeCallbacks(hideTyping); handler.postDelayed(hideTyping, 6000)
                } else if (tv == 0L) { typing = false }
                lastPeerTyping = tv
                rebuild()
            }.also { it.start() }
        }
    }

    override fun onPop() {
        tts?.shutdown()
        msgStream?.close(); metaStream?.close()
        handler.removeCallbacksAndMessages(null)
        setTyping(0L)
    }

    private fun header(ts: Long): String {
        val d = Date(ts); val f = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(d)
        return if (f.format(d) == f.format(Date())) "Today $t" else SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(d) + " $t"
    }

    private fun rebuild() {
        val all = ArrayList<Msg>(messages.filter { !(Blocked.has(act, peer) && it.from == peer) })
        for (p in pending.values) if (all.none { it.key == p.key }) all.add(p)
        all.sortBy { it.key }
        val out = ArrayList<Row>()
        var prevTs = 0L; var prevFrom = ""
        val gapMs = 15 * 60 * 1000L
        for ((i, m) in all.withIndex()) {
            val ts = if (m.ts > 0L) m.ts else System.currentTimeMillis()
            if (prevTs == 0L || ts - prevTs > gapMs) out.add(Row(0, text = header(ts)))
            val next = all.getOrNull(i + 1)
            val tail = next == null || next.from != m.from || (next.ts > 0L && next.ts - ts > gapMs)
            out.add(Row(if (m.type == "text") 1 else 4, m, tail = tail, mine = m.from == me, topGap = prevFrom != m.from || prevTs == 0L || ts - prevTs > gapMs))
            prevTs = ts; prevFrom = m.from
        }
        val last = all.lastOrNull()
        if (last != null && last.from == me) {
            val st = when {
                last.failed -> "Not Delivered"
                pending.containsKey(last.key) -> "Sending…"
                peerRead > 0L && peerRead >= last.ts -> "Read"
                else -> "Delivered"
            }
            out.add(Row(2, text = st))
        }
        if (typing) out.add(Row(3))
        rows = out
        if (::adapter.isInitialized) adapter.notifyDataSetChanged()
    }

    private val sv: JSONObject get() = JSONObject().put(".sv", "timestamp")

    private fun markRead() {
        val newest = messages.filter { it.from == peer }.maxOfOrNull { it.ts } ?: return
        if (newest <= lastMarked) return
        lastMarked = newest
        Net.bg { Db.patch(act, "/", JSONObject().put("userChats/$me/$cid/read", sv).put("chats/$cid/meta/read/$me", sv).toString()) }
    }

    private fun setTyping(v: Long) {
        Net.bg { Db.put(act, "/chats/$cid/meta/typing/$me", v.toString()) }
    }

    private fun onTyped(t: String) {
        sendBtn.visibility = if (t.isBlank()) View.INVISIBLE else View.VISIBLE
        micView.visibility = if (t.isBlank()) View.VISIBLE else View.INVISIBLE
        val now = System.currentTimeMillis()
        if (t.isNotBlank() && now - lastTypingSent > 2500L) { lastTypingSent = now; setTyping(now) }
        handler.removeCallbacks(stopTyping)
        if (t.isNotBlank()) handler.postDelayed(stopTyping, 3500)
    }

    private fun send() {
        val t = input.text.toString().trim()
        if (t.isEmpty()) return
        input.setText("")
        val text = if (t.length > 2000) t.substring(0, 2000) else t
        val key = "%013d".format(System.currentTimeMillis()) + Integer.toString((Math.random() * 1295).toInt(), 36).padStart(2, '0')
        val m = Msg(key, me, text, 0L)
        pending[key] = m
        rebuild()
        deliver(m)
    }

    private fun resend(m: Msg) { m.failed = false; rebuild(); deliver(m) }

    private fun newKey() = "%013d".format(System.currentTimeMillis()) + Integer.toString((Math.random() * 1295).toInt(), 36).padStart(2, '0')

    private fun labelOf(type: String) = when (type) { "image" -> "📷 Photo"; "video" -> "🎬 Video"; else -> "Sticker" }

    private fun sendMedia(type: String, o: MediaPrep.Out) {
        val key = newKey()
        MediaCache.put(act, cid, key, type, o.bytes)
        val m = Msg(key, me, labelOf(type), 0L, false, emptyMap(), type, o.thumb, key, o.w, o.h, o.dur)
        pending[key] = m
        rebuild()
        deliver(m)
    }

    private fun sendSticker(f: java.io.File) {
        setMode(0)
        Net.bg { val o = MediaPrep.imageFile(f); Net.ui { if (o != null) sendMedia("sticker", o) } }
    }

    private fun deliverMedia(m: Msg) {
        Net.bg {
            val ok = try {
                val f = MediaCache.file(act, cid, m.key, m.type)
                val b64 = Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
                if (!Db.put(act, "/media/$cid/${m.key}", JSONObject.quote(b64))) false else {
                    val up = JSONObject()
                    up.put("chats/$cid/messages/${m.key}", JSONObject().put("from", me).put("text", m.text).put("type", m.type)
                        .put("thumb", m.thumb).put("mid", m.key).put("w", m.w).put("h", m.h).put("dur", m.dur).put("ts", sv))
                    up.put("chats/$cid/meta/typing/$me", 0)
                    for (u in listOf(me, peer)) { up.put("userChats/$u/$cid/lastText", m.text); up.put("userChats/$u/$cid/lastFrom", me); up.put("userChats/$u/$cid/lastTs", sv) }
                    up.put("userChats/$me/$cid/read", sv)
                    Db.patch(act, "/", up.toString())
                }
            } catch (_: Throwable) { false }
            if (!ok) Net.ui { m.failed = true; rebuild() }
        }
    }

    private fun mediaRow(cv: View?, m: Msg, mine: Boolean, gap: Boolean): View {
        val row = (cv as? LinearLayout) ?: LinearLayout(act).apply { addView(MediaBubble(act), LinearLayout.LayoutParams(-2, -2)) }
        row.gravity = if (mine) Gravity.END else Gravity.START
        row.setPadding(act.dp(10f), if (gap) act.dp(8f) else act.dp(2f), act.dp(10f), 0)
        val mb = row.getChildAt(0) as MediaBubble
        mb.bind(m, cid)
        mb.setOnClickListener { if (m.failed) resend(m) else if (!pending.containsKey(m.key)) act.push(MediaViewScreen(act, cid, m)) }
        mb.setOnLongClickListener { onLong(mb, m); true }
        return row
    }

    private fun attachMenu(anchor: View) {
        var pw: PopupWindow? = null
        val box = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Theme.card); cornerRadius = act.dpf(16f); setStroke(1, Theme.sep) }
            elevation = act.dpf(10f)
        }
        fun item(label: String, run: () -> Unit) {
            box.addView(TextView(act).apply {
                text = label; textSize = 16f; setTextColor(Theme.text); setPadding(act.dp(20f), act.dp(13f), act.dp(30f), act.dp(13f))
                setOnClickListener { pw?.dismiss(); run() }
            })
        }
        item("📷  Photo") { hidePanelAndKeyboard(); act.pickImage(51) }
        item("🎬  Video (up to 3 MB)") { hidePanelAndKeyboard(); act.pickVideo(52) }
        item("🙂  Stickers") {
            (act.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
            setMode(2)
        }
        item("☺  Emoji") { hideKeyboard(); setMode(1) }
        val w = PopupWindow(box, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true)
        pw = w
        w.isOutsideTouchable = true
        w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        box.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val loc = IntArray(2); anchor.getLocationOnScreen(loc)
        w.showAtLocation(anchor, Gravity.NO_GRAVITY, loc[0], loc[1] - box.measuredHeight - act.dp(8f))
    }

    override fun onResult(req: Int, res: Int, data: Intent?) {
        if (res != Activity.RESULT_OK) return
        if (req == 54) {
            val t = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (t != null) input.append((if (input.text.isNotEmpty()) " " else "") + t)
            return
        }
        if (data?.data == null) return
        val uri = data.data!!
        when (req) {
            51 -> Net.bg {
                val o = MediaPrep.image(act, uri, 1024, false)
                Net.ui { if (o == null) Toast.makeText(act, "Could not use this photo.", Toast.LENGTH_SHORT).show() else sendMedia("image", o) }
            }
            52 -> Net.bg {
                val r = MediaPrep.video(act, uri)
                Net.ui { val o = r.first; if (o == null) Toast.makeText(act, r.second ?: "Could not use this video.", Toast.LENGTH_LONG).show() else sendMedia("video", o) }
            }
            53 -> Net.bg {
                val ok = Stickers.add(act, uri)
                Net.ui { if (ok) stickerPanel.refresh() else Toast.makeText(act, "Could not use this image.", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun deliver(m: Msg) {
        if (m.type != "text") { deliverMedia(m); return }
        Net.bg {
            val up = JSONObject()
            up.put("chats/$cid/messages/${m.key}", JSONObject().put("from", me).put("text", m.text).put("ts", sv))
            up.put("chats/$cid/meta/typing/$me", 0)
            for (u in listOf(me, peer)) {
                up.put("userChats/$u/$cid/lastText", m.text); up.put("userChats/$u/$cid/lastFrom", me); up.put("userChats/$u/$cid/lastTs", sv)
            }
            up.put("userChats/$me/$cid/read", sv)
            val ok = try { Db.patch(act, "/", up.toString()) } catch (_: Throwable) { false }
            if (!ok) Net.ui { m.failed = true; rebuild() }
        }
    }

    // ---------------------------------------------------------------- tekan lama pada gelembung
    private fun reactStr(m: Msg?): String {
        if (m == null || m.reacts.isEmpty()) return ""
        val counts = LinkedHashMap<String, Int>()
        for (e in m.reacts.values) if (e.isNotEmpty()) counts[e] = (counts[e] ?: 0) + 1
        return counts.entries.joinToString("") { if (it.value > 1) it.key + it.value else it.key }
    }

    private fun showPopup(anchor: View, m: Msg) {
        val isText = m.type == "text"
        val mineSent = m.from == me && !pending.containsKey(m.key)
        if (!isText && !mineSent) return
        var pw: PopupWindow? = null
        val box = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Theme.card); cornerRadius = act.dpf(18f); setStroke(1, Theme.sep) }
            elevation = act.dpf(10f)
        }
        val rr = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(act.dp(8f), act.dp(8f), act.dp(8f), act.dp(8f)) }
        for (e in listOf("❤️", "👍", "😂", "‼️", "❓", "👎")) {
            rr.addView(TextView(act).apply {
                text = e; textSize = 26f; setPadding(act.dp(8f), act.dp(4f), act.dp(8f), act.dp(4f)); Emo.apply(this)
                setOnClickListener { react(m, e); pw?.dismiss() }
            })
        }
        if (isText) box.addView(rr)
        fun item(label: String, color: Int, run: () -> Unit) {
            box.addView(View(act).apply { setBackgroundColor(Theme.sep) }, LinearLayout.LayoutParams(-1, 1))
            box.addView(TextView(act).apply {
                text = label; textSize = 16f; setTextColor(color); setPadding(act.dp(18f), act.dp(12f), act.dp(18f), act.dp(12f))
                setOnClickListener { pw?.dismiss(); run() }
            })
        }
        if (isText) item("Copy", Theme.text) {
            (act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("message", m.text))
            Toast.makeText(act, "Copied", Toast.LENGTH_SHORT).show()
        }
        if (m.from == me && !pending.containsKey(m.key)) item("Delete", 0xFFFF453A.toInt()) { Net.bg { Db.delete(act, "/chats/$cid/messages/${m.key}"); if (!isText) Db.delete(act, "/media/$cid/${m.key}") } }
        val w = PopupWindow(box, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true)
        pw = w
        w.isOutsideTouchable = true
        w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        box.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val loc = IntArray(2); anchor.getLocationOnScreen(loc)
        val sw = act.resources.displayMetrics.widthPixels
        val x = (loc[0] + anchor.width / 2 - box.measuredWidth / 2).coerceIn(act.dp(8f), maxOf(act.dp(8f), sw - box.measuredWidth - act.dp(8f)))
        val y = (loc[1] - box.measuredHeight - act.dp(6f)).coerceAtLeast(act.dp(60f))
        w.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
        anchor.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
    }

    private fun react(m: Msg, e: String) {
        Net.bg {
            val path = "/chats/$cid/messages/${m.key}/r/$me"
            if (m.reacts[me] == e) Db.delete(act, path) else Db.put(act, path, JSONObject.quote(e))
        }
    }

    // ---------------------------------------------------------------- papan emoji
    private fun setPanel(on: Boolean) { setMode(if (on) 1 else 0) }

    private fun setMode(m: Int) {
        panelOn = m != 0
        panel.visibility = if (m == 1) View.VISIBLE else View.GONE
        if (::stickerPanel.isInitialized) {
            stickerPanel.visibility = if (m == 2) View.VISIBLE else View.GONE
            if (m == 2) stickerPanel.refresh()
        }
    }

    private fun togglePanel() {
        val imm = act.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (!panelOn) { imm.hideSoftInputFromWindow(input.windowToken, 0); setPanel(true) } else { setPanel(false); input.requestFocus(); imm.showSoftInput(input, 0) }
    }

    private fun hidePanelAndKeyboard() {
        if (panelOn) setPanel(false)
        (act.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
    }
}
