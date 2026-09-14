package com.prototype.keyboard.keyboard

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.prototype.keyboard.data.ClipboardRepository
import com.prototype.keyboard.data.ClipboardStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Clipboard board: section chips + clip rows. Tap pastes, pin/delete per row,
 * sensitive clips stay masked until long-pressed to reveal.
 * Sections are managed in the companion app (Data tab); this board is for speed.
 */
class ClipboardBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    interface Listener {
        fun onPaste(clipId: String, text: String)
        fun onBackToKeyboard()
        fun onFeedback()
    }

    var listener: Listener? = null

    var themeDark: Boolean = false
        set(value) {
            field = value
            render()
        }

    private val density: Float get() = resources.displayMetrics.density

    private var repo: ClipboardRepository? = null
    private var scope: CoroutineScope? = null
    private var store: ClipboardStore = ClipboardStore()
    private var activeSection: String = ClipboardStore.GENERAL_ID
    private var revealed: MutableSet<String> = mutableSetOf()

    private val sectionRow = LinearLayout(context).apply { orientation = HORIZONTAL }
    private val clipList = LinearLayout(context).apply { orientation = VERTICAL }

    init {
        orientation = VERTICAL
        minimumHeight = (250 * density).toInt()

        val back = TextView(context).apply {
            text = "⌨"
            textSize = 22f
            gravity = Gravity.CENTER
            contentDescription = "Back to keyboard"
            isClickable = true
            isFocusable = false
            setPadding(pad(10), pad(6), pad(10), pad(6))
            setOnClickListener { listener?.onBackToKeyboard() }
        }
        val chipScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(sectionRow)
        }
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(back, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            addView(chipScroll, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val scroll = ScrollView(context).apply { addView(clipList) }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1f))
        render()
    }

    fun bind(repository: ClipboardRepository, uiScope: CoroutineScope) {
        repo = repository
        scope = uiScope
        refresh()
    }

    fun refresh() {
        val repository = repo
        val uiScope = scope
        if (repository == null || uiScope == null) {
            render()
            return
        }
        uiScope.launch {
            store = repository.load()
            if (store.section(activeSection) == null) activeSection = ClipboardStore.GENERAL_ID
            render()
        }
    }

    private fun pad(dp: Int): Int = (dp * density).toInt()

    private fun render() {
        val dark = themeDark
        setBackgroundColor(if (dark) 0xFF28292A.toInt() else 0xFFD8DCE3.toInt())
        val fg = if (dark) 0xFFE8EAED.toInt() else 0xFF1F1F1F.toInt()
        val hint = if (dark) 0xFF9AA0A6.toInt() else 0xFF5F6368.toInt()

        sectionRow.removeAllViews()
        store.sections.forEach { section ->
            val count = store.clipsIn(section.id).size
            val chip = TextView(context).apply {
                text = (if (section.sensitive) "🔒 " else "") + section.name + " ($count)"
                textSize = 14f
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = false
                alpha = if (section.id == activeSection) 1f else 0.55f
                setTextColor(fg)
                setPadding(pad(12), pad(10), pad(12), pad(10))
                setOnClickListener {
                    listener?.onFeedback()
                    activeSection = section.id
                    render()
                }
            }
            sectionRow.addView(chip)
        }

        clipList.removeAllViews()
        val clips = store.clipsIn(activeSection)
        if (clips.isEmpty()) {
            val empty = TextView(context).apply {
                text = if (activeSection == ClipboardStore.GENERAL_ID) {
                    "Copy something and it lands here.\nOrganize sections in the app's Data tab."
                } else {
                    "Nothing here yet."
                }
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(hint)
                setPadding(pad(16), pad(24), pad(16), pad(24))
            }
            clipList.addView(empty)
            return
        }
        val sensitive = store.isSensitiveSection(activeSection)
        clips.forEach { clip ->
            val masked = sensitive && clip.id !in revealed
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(pad(8), pad(4), pad(8), pad(4))
            }
            val body = TextView(context).apply {
                text = when {
                    masked -> "🔒 •••••••• (long-press to reveal)"
                    clip.pinned -> "📌 " + clip.text
                    else -> clip.text
                }
                textSize = 15f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                isClickable = true
                isFocusable = false
                setTextColor(if (masked) hint else fg)
                setPadding(pad(8), pad(12), pad(8), pad(12))
                setOnClickListener {
                    listener?.onFeedback()
                    listener?.onPaste(clip.id, clip.text)
                }
                setOnLongClickListener {
                    if (sensitive) {
                        if (clip.id in revealed) revealed.remove(clip.id) else revealed.add(clip.id)
                        listener?.onFeedback()
                        render()
                        true
                    } else {
                        false
                    }
                }
            }
            row.addView(body, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

            fun rowButton(glyph: String, desc: String, action: () -> Unit) {
                val button = TextView(context).apply {
                    text = glyph
                    contentDescription = desc
                    textSize = 18f
                    gravity = Gravity.CENTER
                    isClickable = true
                    isFocusable = false
                    setTextColor(fg)
                    setPadding(pad(10), pad(10), pad(10), pad(10))
                    setOnClickListener {
                        listener?.onFeedback()
                        action()
                    }
                }
                row.addView(button, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            }
            rowButton(if (clip.pinned) "📌" else "📍", "Pin") {
                mutate { it.togglePin(clip.id) }
            }
            rowButton("🗑", "Delete") {
                mutate { it.deleteClip(clip.id) }
            }
            clipList.addView(row)
        }
    }

    private fun mutate(block: (ClipboardStore) -> Unit) {
        val repository = repo
        val uiScope = scope
        if (repository == null || uiScope == null) return
        uiScope.launch {
            store = repository.mutate(block)
            render()
        }
    }
}
