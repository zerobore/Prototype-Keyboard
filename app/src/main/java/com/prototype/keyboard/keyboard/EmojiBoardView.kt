package com.prototype.keyboard.keyboard

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Emoji / kaomoji / text-art board. One category at a time; tap commits.
 * Fully offline (system emoji font).
 */
class EmojiBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    interface Listener {
        fun onEmoji(text: String)
        fun onBackToKeyboard()
        fun onFeedback()
    }

    var listener: Listener? = null

    var themeDark: Boolean = false
        set(value) {
            field = value
            applyTheme()
        }

    private val density: Float get() = resources.displayMetrics.density

    private var currentCategory = 0
    private val categoryRow = LinearLayout(context).apply { orientation = HORIZONTAL }
    private val gridHost = LinearLayout(context).apply { orientation = VERTICAL }

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
            addView(categoryRow)
        }
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(back, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            addView(chipScroll, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(gridHost)
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1f))

        rebuildChips()
        showCategory(0)
        applyTheme()
    }

    private fun pad(dp: Int): Int = (dp * density).toInt()

    private fun rebuildChips() {
        categoryRow.removeAllViews()
        EmojiData.categories.forEachIndexed { index, category ->
            val chip = TextView(context).apply {
                text = category.glyph
                textSize = if (category.id == "kaomoji" || category.id == "textart") 13f else 20f
                gravity = Gravity.CENTER
                contentDescription = category.name
                isClickable = true
                isFocusable = false
                minWidth = pad(48)
                setPadding(pad(8), pad(8), pad(8), pad(8))
                setOnClickListener {
                    listener?.onFeedback()
                    showCategory(index)
                }
            }
            chip.tag = index
            categoryRow.addView(chip)
        }
        highlightChip()
    }

    private fun showCategory(index: Int) {
        currentCategory = index.coerceIn(0, EmojiData.categories.size - 1)
        highlightChip()
        gridHost.removeAllViews()
        val category = EmojiData.categories[currentCategory]
        val label = TextView(context).apply {
            text = category.name
            textSize = 13f
            setPadding(pad(12), pad(4), pad(12), pad(4))
        }
        label.tag = "label"
        gridHost.addView(label)
        val grid = GridLayout(context).apply {
            columnCount = if (category.id == "kaomoji" || category.id == "textart") 3 else 8
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }
        val cellSize = if (grid.columnCount == 3) {
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        } else {
            null
        }
        category.items.forEach { item ->
            val cell = TextView(context).apply {
                text = item
                textSize = if (grid.columnCount == 3) 16f else 24f
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = false
                minWidth = pad(if (grid.columnCount == 3) 96 else 40)
                minHeight = pad(48)
                setPadding(pad(4), pad(6), pad(4), pad(6))
                setOnClickListener {
                    listener?.onFeedback()
                    listener?.onEmoji(item)
                }
            }
            if (cellSize != null) grid.addView(cell, cellSize) else grid.addView(cell)
        }
        gridHost.addView(grid)
        applyTheme()
    }

    private fun highlightChip() {
        for (i in 0 until categoryRow.childCount) {
            val chip = categoryRow.getChildAt(i) as? TextView ?: continue
            val selected = (chip.tag as? Int) == currentCategory
            chip.alpha = if (selected) 1f else 0.45f
        }
    }

    private fun applyTheme() {
        val dark = themeDark
        setBackgroundColor(if (dark) 0xFF28292A.toInt() else 0xFFD8DCE3.toInt())
        val fg = if (dark) 0xFFE8EAED.toInt() else 0xFF1F1F1F.toInt()
        val hint = if (dark) 0xFF9AA0A6.toInt() else 0xFF5F6368.toInt()
        for (i in 0 until categoryRow.childCount) {
            (categoryRow.getChildAt(i) as? TextView)?.setTextColor(fg)
        }
        for (i in 0 until gridHost.childCount) {
            val child = gridHost.getChildAt(i)
            if (child.tag == "label") {
                (child as? TextView)?.setTextColor(hint)
            } else if (child is GridLayout) {
                for (j in 0 until child.childCount) {
                    (child.getChildAt(j) as? TextView)?.setTextColor(fg)
                }
            }
        }
    }
}
