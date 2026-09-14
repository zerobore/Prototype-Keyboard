package com.prototype.keyboard.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Custom-drawn keyboard view.
 *
 * Capabilities: tap typing, slide-between-keys, shift/caps rendering,
 * key-press preview bubble, long-press popup alternatives, long-press delete
 * repeat, swipe-down-from-space to hide, and glide typing with a trail
 * overlay (when enabled; decoded by [GlideTyper] in the IME).
 *
 * The PopupWindow used for long-press alternatives is deliberately NOT focusable
 * / touchable: an IME must never steal input focus, so slide-to-select is
 * handled by this view's own touch tracking (the popup is purely visual).
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    interface Listener {
        /** A key was tapped (or a long-press option was selected). */
        fun onKey(spec: KeySpec)

        /** A glide trail finished: decode into a word. */
        fun onGlide(path: List<TrailPoint>, keys: List<KeyCenter>)

        /**
         * Long-press on a key. Return true if consumed (e.g. caps-lock toggle,
         * hide). Return false to let the view show [KeySpec.longPress] options.
         */
        fun onKeyLongPress(spec: KeySpec): Boolean

        /** Swipe down starting on space. */
        fun onHideRequested()
    }

    var listener: Listener? = null

    /** Haptic + sound feedback hook (wired by the IME so settings apply). */
    var onPressFeedback: (() -> Unit)? = null

    var shifted: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var capsLock: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var themeDark: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var keyBorders: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    /** Label drawn on the ENTER key (e.g. "Go", "Next", or "⏎"). */
    var enterLabel: String = DEFAULT_ENTER_LABEL
        set(value) {
            field = value
            invalidate()
        }

    var keyHeightDp: Int = 56
        set(value) {
            field = value
            requestLayout()
        }

    var glideEnabled: Boolean = false

    /** Representative single-key width in px (glide anchor tolerance). */
    var keyUnitWidthPx: Float = 60f
        private set

    private var layout: KeyboardLayout = Layouts.englishLetters()
    private var rects: List<List<RectF>> = emptyList()

    private val density: Float get() = resources.displayMetrics.density

    private val paintKey = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintFuncKey = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintPressed = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val paintAccent = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val paintBubble = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintBubbleText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val paintTrail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val tmpRect = RectF()

    private val handler = Handler(Looper.getMainLooper())

    // Touch state
    private var pressedRow = -1
    private var pressedCol = -1
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var downOnSpace = false
    private var hideGestureFired = false
    private var deleteRepeating = false

    // Glide trail state
    private val trail = ArrayList<TrailPoint>()
    private val trailKeys = LinkedHashSet<Pair<Int, Int>>()
    private var trailLengthPx = 0f
    private var glideActive = false

    // Long-press popup state (visual only; selection tracked here).
    private var popupWindow: PopupWindow? = null
    private var popupOptions: List<String> = emptyList()
    private var popupSelected = -1
    private var popupScreenX = 0
    private var popupOptionWidth = 1
    private var popupOptionViews: List<TextView> = emptyList()
    private var popupOriginal: KeySpec? = null

    private val longPressRunnable = Runnable {
        val spec = pressedSpec()
        if (spec == null) {
            clearPressed()
            return@Runnable
        }
        if (spec.action == KeyAction.DELETE) {
            deleteRepeating = true
            handler.post(deleteRepeatRunnable)
            return@Runnable
        }
        val consumed = runCatching { listener?.onKeyLongPress(spec) }.getOrNull() ?: false
        if (consumed) {
            clearPressed()
        } else if (spec.longPress.isNotEmpty()) {
            showOptionsPopup(spec)
        }
    }

    private val deleteRepeatRunnable = object : Runnable {
        override fun run() {
            if (!deleteRepeating) return
            val spec = pressedSpec()
            if (spec == null || spec.action != KeyAction.DELETE) {
                deleteRepeating = false
                return
            }
            runCatching { listener?.onKey(spec) }
            onPressFeedback?.invoke()
            handler.postDelayed(this, DELETE_REPEAT_MS)
        }
    }

    init {
        contentDescription = "Prototype keyboard"
        isFocusable = false
    }

    fun setKeyboardLayout(value: KeyboardLayout) {
        dismissPopup()
        trail.clear()
        trailKeys.clear()
        glideActive = false
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(deleteRepeatRunnable)
        deleteRepeating = false
        layout = value
        pressedRow = -1
        pressedCol = -1
        if (width > 0 && height > 0) computeRects(width, height)
        requestLayout()
        invalidate()
    }

    // ---- Measure / layout ----

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val totalWeight = layout.rows.sumOf { it.heightWeight.toDouble() }.toFloat().coerceAtLeast(1f)
        val height = (keyHeightDp * density * totalWeight).toInt()
        setMeasuredDimension(width, max(height, (48 * density).toInt()))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) computeRects(w, h)
    }

    private fun computeRects(w: Int, h: Int) {
        val totalH = layout.rows.sumOf { it.heightWeight.toDouble() }.toFloat().coerceAtLeast(1f)
        val gap = KEY_GAP_DP * density
        var top = 0f
        val newRects = mutableListOf<List<RectF>>()
        for (row in layout.rows) {
            val rowH = h * (row.heightWeight / totalH)
            val totalW = row.keys.sumOf { it.widthWeight.toDouble() }.toFloat().coerceAtLeast(1f)
            var left = 0f
            val rowRects = mutableListOf<RectF>()
            for (key in row.keys) {
                val keyW = w * (key.widthWeight / totalW)
                rowRects.add(
                    RectF(
                        left + gap / 2f, top + gap / 2f,
                        left + keyW - gap / 2f, top + rowH - gap / 2f
                    )
                )
                left += keyW
            }
            newRects.add(rowRects)
            top += rowH
        }
        rects = newRects
        // First-row keys are single-weight in every layout: good width unit.
        newRects.firstOrNull()?.firstOrNull()?.width()?.let {
            if (it > 0f) keyUnitWidthPx = it
        }
    }

    // ---- Draw ----

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val dark = themeDark
        val bg = if (dark) COLOR_BG_DARK else COLOR_BG_LIGHT
        canvas.drawColor(bg)

        paintKey.color = if (dark) COLOR_KEY_DARK else COLOR_KEY_LIGHT
        paintFuncKey.color = if (dark) COLOR_FUNC_DARK else COLOR_FUNC_LIGHT
        paintPressed.color = if (dark) COLOR_PRESSED_DARK else COLOR_PRESSED_LIGHT
        paintText.color = if (dark) COLOR_TEXT_DARK else COLOR_TEXT_LIGHT
        paintAccent.color = if (dark) COLOR_ACCENT_DARK else COLOR_ACCENT_LIGHT
        paintBubble.color = if (dark) COLOR_ACCENT_DARK else COLOR_ACCENT_LIGHT
        paintBubbleText.color = 0xFFFFFFFF.toInt()

        val radius = KEY_RADIUS_DP * density
        val isShifted = shifted || capsLock

        layout.rows.forEachIndexed { r, row ->
            row.keys.forEachIndexed { c, key ->
                val rect = rects.getOrNull(r)?.getOrNull(c) ?: return@forEachIndexed
                val isPressed = r == pressedRow && c == pressedCol
                val paint = when {
                    isPressed -> paintPressed
                    isFunctionKey(key) -> paintFuncKey
                    else -> paintKey
                }
                if (keyBorders) {
                    canvas.drawRoundRect(rect, radius, radius, paint)
                } else {
                    // Borderless: draw gap-colored separators by shrinking fill slightly.
                    tmpRect.set(rect)
                    tmpRect.inset(1f * density, 1f * density)
                    canvas.drawRoundRect(tmpRect, radius / 2f, radius / 2f, paint)
                }

                val label = labelFor(key, isShifted)
                if (label.isNotEmpty() && label.isNotBlank()) {
                    val paint = if (key.action == KeyAction.SHIFT && isShifted) paintAccent else paintText
                    paint.textSize = textSizeFor(label)
                    val x = rect.centerX()
                    val y = rect.centerY() - (paint.descent() + paint.ascent()) / 2f
                    canvas.drawText(label, x, y, paint)
                }

                // Caps-lock indicator dot on shift.
                if (key.action == KeyAction.SHIFT && capsLock) {
                    canvas.drawCircle(
                        rect.centerX(),
                        rect.top + 6f * density,
                        3f * density,
                        paintAccent
                    )
                }
            }
        }

        drawPreviewBubble(canvas, isShifted)
        drawTrail(canvas)
    }

    private fun drawTrail(canvas: Canvas) {
        if (trail.size < 2) return
        paintTrail.color = if (themeDark) COLOR_ACCENT_DARK else COLOR_ACCENT_LIGHT
        paintTrail.alpha = 170
        paintTrail.strokeWidth = 9f * density
        for (i in 1 until trail.size) {
            canvas.drawLine(trail[i - 1].x, trail[i - 1].y, trail[i].x, trail[i].y, paintTrail)
        }
        val start = trail.first()
        canvas.drawCircle(start.x, start.y, 7f * density, paintTrail)
    }

    /** Snapshot of letter keys for the glide decoder. */
    fun snapshotKeys(): List<KeyCenter> {
        val out = ArrayList<KeyCenter>()
        layout.rows.forEachIndexed { r, row ->
            row.keys.forEachIndexed { c, key ->
                if (key.action != KeyAction.CHAR) return@forEachIndexed
                val label = key.label
                if (label.length != 1 || !label[0].isLetter()) return@forEachIndexed
                val rect = rects.getOrNull(r)?.getOrNull(c) ?: return@forEachIndexed
                out.add(KeyCenter(label[0], rect.centerX(), rect.centerY()))
            }
        }
        return out
    }

    private fun drawPreviewBubble(canvas: Canvas, isShifted: Boolean) {
        if (popupWindow != null) return // options popup is showing instead
        if (glideActive) return
        val spec = pressedSpec() ?: return
        if (spec.action != KeyAction.CHAR) return
        val label = spec.displayLabel(isShifted)
        if (label.isBlank()) return
        val rect = rects.getOrNull(pressedRow)?.getOrNull(pressedCol) ?: return

        val bubbleW = max(rect.width() * 1.5f, 52f * density)
        val bubbleH = 58f * density
        val left = (rect.centerX() - bubbleW / 2f).coerceIn(0f, (width - bubbleW).coerceAtLeast(0f))
        val top = (rect.top - bubbleH - 6f * density).coerceAtLeast(0f)
        tmpRect.set(left, top, left + bubbleW, top + bubbleH)
        canvas.drawRoundRect(tmpRect, 10f * density, 10f * density, paintBubble)
        paintBubbleText.textSize = 26f * density
        val y = tmpRect.centerY() - (paintBubbleText.descent() + paintBubbleText.ascent()) / 2f
        canvas.drawText(label, tmpRect.centerX(), y, paintBubbleText)
    }

    private fun labelFor(key: KeySpec, isShifted: Boolean): String = when (key.action) {
        KeyAction.ENTER -> enterLabel
        KeyAction.CHAR -> key.displayLabel(isShifted)
        else -> key.label
    }

    private fun textSizeFor(label: String): Float = when {
        label.length <= 1 -> 22f * density
        label.length <= 2 -> 18f * density
        else -> 14f * density
    }

    private fun isFunctionKey(key: KeySpec): Boolean = when (key.action) {
        KeyAction.CHAR, KeyAction.SPACE -> false
        else -> true
    }

    // ---- Touch ----

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                hideGestureFired = false
                downX = event.x
                downY = event.y
                downTime = event.eventTime
                val pos = findKey(event.x, event.y)
                if (pos == null) {
                    clearPressed()
                    return false
                }
                pressedRow = pos.first
                pressedCol = pos.second
                downOnSpace = pressedSpec()?.action == KeyAction.SPACE
                trail.clear()
                trail.add(TrailPoint(event.x, event.y))
                trailKeys.clear()
                trailKeys.add(pos.first to pos.second)
                trailLengthPx = 0f
                glideActive = false
                invalidate()
                onPressFeedback?.invoke()
                handler.removeCallbacks(longPressRunnable)
                handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // Hide gesture: swipe down starting on space.
                if (!hideGestureFired && downOnSpace && popupWindow == null) {
                    val dy = event.y - downY
                    val dx = event.x - downX
                    if (dy > HIDE_SWIPE_DP * density && abs(dy) > abs(dx) * 2 &&
                        event.eventTime - downTime < HIDE_SWIPE_MAX_MS
                    ) {
                        hideGestureFired = true
                        cancelAll()
                        runCatching { listener?.onHideRequested() }
                        return true
                    }
                }
                if (popupWindow != null) {
                    updatePopupSelection(event.rawX)
                    return true
                }
                if (deleteRepeating) return true
                if (glideEnabled) {
                    val last = trail.lastOrNull()
                    if (last != null) {
                        val dx = event.x - last.x
                        val dy = event.y - last.y
                        trailLengthPx += kotlin.math.sqrt(dx * dx + dy * dy)
                    }
                    if (trail.size < MAX_TRAIL_POINTS) trail.add(TrailPoint(event.x, event.y))
                }
                val pos = findKey(event.x, event.y)
                if (pos != null) trailKeys.add(pos.first to pos.second)
                if (glideEnabled && !glideActive && pos != null &&
                    (trailKeys.size >= GLIDE_MIN_KEYS || trailLengthPx > keyUnitWidthPx * GLIDE_MIN_SPAN_KEYS)
                ) {
                    glideActive = true
                    handler.removeCallbacks(longPressRunnable)
                    pressedRow = -1
                    pressedCol = -1
                    invalidate()
                    return true
                }
                if (glideActive) {
                    invalidate()
                    return true
                }
                if (pos != null && (pos.first != pressedRow || pos.second != pressedCol)) {
                    // Sliding between keys cancels long-press.
                    handler.removeCallbacks(longPressRunnable)
                    pressedRow = pos.first
                    pressedCol = pos.second
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                if (hideGestureFired) {
                    hideGestureFired = false
                    trail.clear()
                    return true
                }
                if (glideActive) {
                    glideActive = false
                    val path = trail.toList()
                    trail.clear()
                    clearPressed()
                    if (path.size >= 2) {
                        runCatching { listener?.onGlide(path, snapshotKeys()) }
                    }
                    return true
                }
                if (popupWindow != null) {
                    commitPopupSelection()
                    return true
                }
                val wasRepeating = deleteRepeating
                deleteRepeating = false
                handler.removeCallbacks(deleteRepeatRunnable)
                val spec = pressedSpec()
                clearPressed()
                if (spec != null && !wasRepeating) {
                    runCatching { listener?.onKey(spec) }
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelAll()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findKey(x: Float, y: Float): Pair<Int, Int>? {
        rects.forEachIndexed { r, row ->
            row.forEachIndexed { c, rect ->
                if (rect.contains(x, y)) return r to c
            }
        }
        return null
    }

    private fun pressedSpec(): KeySpec? =
        layout.rows.getOrNull(pressedRow)?.keys?.getOrNull(pressedCol)

    private fun clearPressed() {
        pressedRow = -1
        pressedCol = -1
        invalidate()
    }

    private fun cancelAll() {
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(deleteRepeatRunnable)
        deleteRepeating = false
        trail.clear()
        trailKeys.clear()
        glideActive = false
        dismissPopup()
        clearPressed()
    }

    // ---- Long-press options popup (visual only) ----

    private fun showOptionsPopup(spec: KeySpec) {
        dismissPopup()
        val options = spec.longPress
        if (options.isEmpty()) return
        popupOptions = options
        popupOriginal = spec
        popupSelected = -1

        val dark = themeDark
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val bgColor = if (dark) COLOR_KEY_DARK else COLOR_KEY_LIGHT
            setBackgroundColor(bgColor)
            setPadding(
                (8 * density).toInt(), (8 * density).toInt(),
                (8 * density).toInt(), (8 * density).toInt()
            )
        }
        val views = options.map { label ->
            TextView(context).apply {
                text = if (shifted || capsLock) label.uppercase() else label
                textSize = 24f
                gravity = Gravity.CENTER
                setTextColor(if (dark) COLOR_TEXT_DARK else COLOR_TEXT_LIGHT)
                minWidth = (52 * density).toInt()
                setPadding((10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt())
                container.addView(this)
            }
        }
        popupOptionViews = views

        val popup = PopupWindow(container, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, false)
        popup.isTouchable = false // selection tracked by this view; popup is visual only
        popup.isFocusable = false // MUST NOT steal input focus from the editor
        popupWindow = popup

        container.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        val popupW = container.measuredWidth.coerceAtLeast(1)
        popupOptionWidth = popupW / options.size.coerceAtLeast(1)

        val anchor = rects.getOrNull(pressedRow)?.getOrNull(pressedCol)
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val keyCenterX = loc[0] + (anchor?.centerX() ?: (width / 2f))
        val keyTop = loc[1] + (anchor?.top ?: 0f)
        val screenW = resources.displayMetrics.widthPixels
        val x = (keyCenterX - popupW / 2f).toInt().coerceIn(0, max(0, screenW - popupW))
        val y = max(0, (keyTop - container.measuredHeight - 8 * density).toInt())
        popupScreenX = x
        runCatching { popup.showAtLocation(this, Gravity.NO_GRAVITY, x, y) }
        highlightPopup(-1)
    }

    private fun updatePopupSelection(rawX: Float) {
        if (popupWindow == null || popupOptions.isEmpty()) return
        val index = ((rawX - popupScreenX) / popupOptionWidth).toInt()
            .coerceIn(-1, popupOptions.size - 1)
        if (index != popupSelected) {
            popupSelected = index
            highlightPopup(index)
            if (index >= 0) onPressFeedback?.invoke()
        }
    }

    private fun highlightPopup(selected: Int) {
        popupOptionViews.forEachIndexed { i, view ->
            val color = if (i == selected) {
                if (themeDark) COLOR_PRESSED_DARK else COLOR_PRESSED_LIGHT
            } else {
                0x00000000
            }
            view.setBackgroundColor(color)
        }
    }

    private fun commitPopupSelection() {
        val selected = popupSelected
        val original = popupOriginal
        dismissPopup()
        clearPressed()
        if (selected in popupOptions.indices) {
            val label = popupOptions[selected]
            val shiftedNow = shifted || capsLock
            val text = if (shiftedNow) label.uppercase() else label
            runCatching {
                listener?.onKey(
                    KeySpec(
                        code = text.codePointAt(0),
                        label = text,
                        action = KeyAction.CHAR
                    )
                )
            }
        } else if (original != null) {
            // Released without sliding: commit the original key.
            runCatching { listener?.onKey(original) }
        }
        popupOriginal = null
    }

    private fun dismissPopup() {
        runCatching { popupWindow?.dismiss() }
        popupWindow = null
        popupOptions = emptyList()
        popupSelected = -1
        popupOptionViews = emptyList()
    }

    override fun onDetachedFromWindow() {
        cancelAll()
        super.onDetachedFromWindow()
    }

    companion object {
        const val DEFAULT_ENTER_LABEL = "⏎"

        private const val LONG_PRESS_MS = 400L
        private const val DELETE_REPEAT_MS = 60L
        private const val KEY_GAP_DP = 4f
        private const val KEY_RADIUS_DP = 6f
        private const val HIDE_SWIPE_DP = 80f
        private const val HIDE_SWIPE_MAX_MS = 600L
        private const val MAX_TRAIL_POINTS = 256
        private const val GLIDE_MIN_KEYS = 3
        private const val GLIDE_MIN_SPAN_KEYS = 3.5f

        // Key palette (light / dark).
        private const val COLOR_BG_LIGHT = 0xFFD8DCE3.toInt()
        private const val COLOR_KEY_LIGHT = 0xFFFFFFFF.toInt()
        private const val COLOR_FUNC_LIGHT = 0xFFB9C0CB.toInt()
        private const val COLOR_PRESSED_LIGHT = 0xFF9FB4D8.toInt()
        private const val COLOR_TEXT_LIGHT = 0xFF1F1F1F.toInt()
        private const val COLOR_ACCENT_LIGHT = 0xFF0B57D0.toInt()

        private const val COLOR_BG_DARK = 0xFF28292A.toInt()
        private const val COLOR_KEY_DARK = 0xFF3E3F42.toInt()
        private const val COLOR_FUNC_DARK = 0xFF2F3033.toInt()
        private const val COLOR_PRESSED_DARK = 0xFF5F6368.toInt()
        private const val COLOR_TEXT_DARK = 0xFFE8EAED.toInt()
        private const val COLOR_ACCENT_DARK = 0xFFA8C7FA.toInt()
    }
}

// Small helper to keep coercion readable.
private fun Float.coerceIn(minimumValue: Float, maximumValue: Float): Float =
    min(max(this, minimumValue), maximumValue)
