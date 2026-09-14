package com.prototype.keyboard.keyboard

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Strip above the keys: quick actions (settings / clipboard / emoji / tools),
 * a persistent quick-paste chip (optional via settings), plus up to 3
 * live suggestions. Empty suggestions show the locale chip.
 * Buttons use text glyphs to avoid asset deps.
 */
class SuggestionStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    enum class StripAction { SETTINGS, CLIPBOARD, EMOJI, TOOLS }

    interface Listener {
        fun onStripAction(action: StripAction)
        fun onSuggestion(word: String)
        fun onQuickPaste()
    }

    var listener: Listener? = null

    var themeDark: Boolean = false
        set(value) {
            field = value
            applyTheme()
        }

    private val density: Float get() = resources.displayMetrics.density

    private val actionButtons: List<TextView>
    private val quickPasteButton: TextView
    private val suggestionViews: List<TextView>
    private val localeChip: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = (48 * density).toInt()
        setPadding((4 * density).toInt(), 0, (4 * density).toInt(), 0)

        fun actionButton(glyph: String, desc: String, action: StripAction): TextView {
            return TextView(context).apply {
                text = glyph
                contentDescription = desc
                textSize = 20f
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = false
                setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
                setOnClickListener { listener?.onStripAction(action) }
                addView(this, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
            }
        }

        actionButtons = listOf(
            actionButton("⚙", "Keyboard settings", StripAction.SETTINGS),
            actionButton("📋", "Clipboard", StripAction.CLIPBOARD),
            actionButton("😀", "Emoji", StripAction.EMOJI),
            actionButton("🛠", "Text tools", StripAction.TOOLS),
        )

        // Persistent quick-paste chip (optional via settings; replaces one-shot paste).
        quickPasteButton = TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = 13f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = (110 * density).toInt()
            isClickable = true
            isFocusable = false
            visibility = GONE
            contentDescription = "Quick paste"
            setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
            setOnClickListener { listener?.onQuickPaste() }
            this@SuggestionStripView.addView(
                this,
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            )
        }

        suggestionViews = (0 until 3).map { index ->
            TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 16f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                isClickable = true
                isFocusable = false
                setOnClickListener {
                    val word = text?.toString().orEmpty()
                    if (word.isNotEmpty()) listener?.onSuggestion(word)
                }
                addView(
                    this,
                    LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                        gravity = Gravity.CENTER_VERTICAL
                    }
                )
                tag = index
            }
        }

        localeChip = TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = 13f
            maxLines = 1
            addView(this, LayoutParams(0, LayoutParams.MATCH_PARENT, 3f))
        }

        applyTheme()
        setSuggestions(emptyList(), "EN")
    }

    /** Persistent quick-paste chip. Null hides it. */
    fun setQuickPaste(preview: String?) {
        if (preview.isNullOrEmpty()) {
            quickPasteButton.visibility = GONE
        } else {
            quickPasteButton.visibility = VISIBLE
            quickPasteButton.text = "📥 $preview"
        }
    }

    fun setSuggestions(words: List<String>, localeLabel: String) {
        if (words.isEmpty()) {
            suggestionViews.forEach { it.visibility = GONE }
            localeChip.visibility = VISIBLE
            localeChip.text = localeLabel
        } else {
            localeChip.visibility = GONE
            suggestionViews.forEachIndexed { i, view ->
                view.visibility = VISIBLE
                view.text = words.getOrNull(i).orEmpty()
            }
        }
    }

    private fun applyTheme() {
        val dark = themeDark
        setBackgroundColor(if (dark) 0xFF1F2021.toInt() else 0xFFE8EAED.toInt())
        val fg = if (dark) 0xFFE8EAED.toInt() else 0xFF1F1F1F.toInt()
        val hint = if (dark) 0xFF9AA0A6.toInt() else 0xFF5F6368.toInt()
        (actionButtons + suggestionViews + quickPasteButton).forEach { it.setTextColor(fg) }
        localeChip.setTextColor(hint)
    }
}
