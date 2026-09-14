package com.prototype.keyboard.keyboard

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.prototype.keyboard.plugins.TextTool

/**
 * Text-tools board: runs enabled tools from installed TOOLS packs against
 * the selected text (or current word). Tools are declarative chains executed
 * host-side — packs never run code.
 */
class TextToolsBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    interface Listener {
        fun onTool(tool: TextTool)
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
    private var tools: List<TextTool> = emptyList()
    private val listHost = LinearLayout(context).apply { orientation = VERTICAL }

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
        val title = TextView(context).apply {
            text = "Text tools"
            textSize = 15f
            setPadding(pad(4), pad(6), pad(4), pad(6))
        }
        title.tag = "title"
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(back, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            addView(title, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val scroll = ScrollView(context).apply { addView(listHost) }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1f))
        render()
    }

    fun setTools(value: List<TextTool>) {
        tools = value
        render()
    }

    private fun pad(dp: Int): Int = (dp * density).toInt()

    private fun render() {
        val dark = themeDark
        setBackgroundColor(if (dark) 0xFF28292A.toInt() else 0xFFD8DCE3.toInt())
        val fg = if (dark) 0xFFE8EAED.toInt() else 0xFF1F1F1F.toInt()
        val hint = if (dark) 0xFF9AA0A6.toInt() else 0xFF5F6368.toInt()
        for (i in 0 until childCount) {
            ((getChildAt(i) as? LinearLayout)?.getChildWithTag("title") as? TextView)?.setTextColor(hint)
        }
        listHost.removeAllViews()
        if (tools.isEmpty()) {
            val empty = TextView(context).apply {
                text = "No tools enabled.\nBuild or import tool packs in the app's Studio tab."
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(hint)
                setPadding(pad(16), pad(24), pad(16), pad(24))
            }
            listHost.addView(empty)
            return
        }
        tools.forEach { tool ->
            val row = TextView(context).apply {
                text = "${tool.icon}  ${tool.title}"
                textSize = 16f
                isClickable = true
                isFocusable = false
                setTextColor(fg)
                setPadding(pad(16), pad(14), pad(16), pad(14))
                setOnClickListener {
                    listener?.onFeedback()
                    listener?.onTool(tool)
                }
            }
            listHost.addView(row)
        }
    }

    private fun LinearLayout.getChildWithTag(tag: String): android.view.View? {
        for (i in 0 until childCount) {
            if (getChildAt(i).tag == tag) return getChildAt(i)
        }
        return null
    }
}
