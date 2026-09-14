package com.prototype.keyboard.ime

import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodService
import android.widget.LinearLayout
import android.widget.Toast
import com.prototype.keyboard.data.KeyboardSettings
import com.prototype.keyboard.data.SettingsRepository
import com.prototype.keyboard.keyboard.KeyAction
import com.prototype.keyboard.keyboard.KeySpec
import com.prototype.keyboard.keyboard.KeyboardMode
import com.prototype.keyboard.keyboard.KeyboardView
import com.prototype.keyboard.keyboard.Layouts
import com.prototype.keyboard.keyboard.SuggestionStripView
import com.prototype.keyboard.ui.MainActivity
import com.prototype.keyboard.util.Feedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * System keyboard service (the IME).
 *
 * Phase 1: reliable tap typing across text / password / numeric fields, with
 * auto-caps, double-space period, editor-action enter key, delete repeat and
 * per-field layout switching. Suggestions / autocorrect / glide arrive in
 * Phase 3 (see docs/TEST_PLAN.md gates).
 */
class ProtoIME : InputMethodService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settingsRepo: SettingsRepository
    private var settings = KeyboardSettings()

    private var keyboardView: KeyboardView? = null
    private var stripView: SuggestionStripView? = null

    private var mode: KeyboardMode = KeyboardMode.LETTERS
    private var locale: String = "en"
    private var shifted: Boolean = false
    private var capsLock: Boolean = false
    private var isPasswordField: Boolean = false
    private var imeAction: Int = EditorInfo.IME_ACTION_UNSPECIFIED
    private var lastShiftTapMs: Long = 0L

    // ---- Service lifecycle ----

    override fun onCreate() {
        super.onCreate()
        settingsRepo = SettingsRepository(applicationContext)
        scope.launch {
            settingsRepo.settings.collect {
                settings = it
                applySettingsToViews()
            }
        }
    }

    override fun onCreateInputView(): View {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val strip = SuggestionStripView(this).apply {
            listener = object : SuggestionStripView.Listener {
                override fun onStripAction(action: SuggestionStripView.StripAction) {
                    handleStripAction(action)
                }

                override fun onSuggestion(word: String) {
                    // Phase 3: suggestions always empty in Phase 1.
                    commitText("$word ")
                }
            }
        }
        val keyboard = KeyboardView(this).apply {
            listener = object : KeyboardView.Listener {
                override fun onKey(spec: KeySpec) = handleKey(spec)
                override fun onKeyLongPress(spec: KeySpec): Boolean = handleKeyLongPress(spec)
                override fun onHideRequested() = requestHideSelf(0)
            }
            onPressFeedback = { performFeedback() }
        }

        container.addView(
            strip,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        container.addView(
            keyboard,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        stripView = strip
        keyboardView = keyboard
        applySettingsToViews()
        refreshForCurrentEditor()
        return container
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        inspectEditor(attribute)
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        inspectEditor(info)
        // Fresh field: reset transient state (keep caps-lock, it is explicit).
        if (!capsLock) setShift(false)
        mode = if (isNumericClass(info)) KeyboardMode.NUMERIC else KeyboardMode.LETTERS
        refreshForCurrentEditor()
        updateAutoCaps()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        if (!capsLock) setShift(false)
    }

    override fun onDestroy() {
        scope.cancel()
        keyboardView = null
        stripView = null
        super.onDestroy()
    }

    // ---- Editor inspection ----

    private fun inspectEditor(info: EditorInfo) {
        val inputClass = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        isPasswordField = inputClass == InputType.TYPE_CLASS_TEXT &&
            (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
        imeAction = info.imeOptions and EditorInfo.IME_MASK_ACTION
    }

    private fun isNumericClass(info: EditorInfo): Boolean {
        val inputClass = info.inputType and InputType.TYPE_MASK_CLASS
        return inputClass == InputType.TYPE_CLASS_NUMBER ||
            inputClass == InputType.TYPE_CLASS_PHONE ||
            inputClass == InputType.TYPE_CLASS_DATETIME
    }

    private fun refreshForCurrentEditor() {
        val keyboard = keyboardView ?: return
        keyboard.setKeyboardLayout(Layouts.layoutFor(locale, mode))
        keyboard.enterLabel = labelForAction(imeAction)
        showStripForState()
    }

    private fun showStripForState() {
        // Phase 1: no suggestion engine yet; show locale chip.
        // (Password fields will additionally suppress learning in Phase 3.)
        stripView?.setSuggestions(emptyList(), locale.uppercase())
    }

    private fun labelForAction(action: Int): String = when (action) {
        EditorInfo.IME_ACTION_GO -> "Go"
        EditorInfo.IME_ACTION_SEARCH -> "Search"
        EditorInfo.IME_ACTION_SEND -> "Send"
        EditorInfo.IME_ACTION_NEXT -> "Next"
        EditorInfo.IME_ACTION_DONE -> "Done"
        EditorInfo.IME_ACTION_PREVIOUS -> "Prev"
        else -> KeyboardView.DEFAULT_ENTER_LABEL
    }

    private fun applySettingsToViews() {
        val dark = when (settings.themeMode) {
            com.prototype.keyboard.data.ThemeMode.LIGHT -> false
            com.prototype.keyboard.data.ThemeMode.DARK -> true
            com.prototype.keyboard.data.ThemeMode.SYSTEM -> isSystemDark()
        }
        keyboardView?.let {
            it.themeDark = dark
            it.keyBorders = settings.keyBorders
            if (it.keyHeightDp != settings.keyHeightDp) it.keyHeightDp = settings.keyHeightDp
        }
        stripView?.themeDark = dark
    }

    private fun isSystemDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    // ---- Key handling ----

    private fun handleKey(spec: KeySpec) {
        when (spec.action) {
            KeyAction.CHAR -> {
                val text = spec.commitText(shifted || capsLock) ?: return
                commitText(text)
                if (shifted && !capsLock) setShift(false)
            }
            KeyAction.SHIFT -> handleShiftTap()
            KeyAction.DELETE -> {
                val ic = currentInputConnection
                if (ic == null) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                } else {
                    runCatching { ic.deleteSurroundingText(1, 0) }
                }
                updateAutoCaps()
            }
            KeyAction.SPACE -> handleSpace()
            KeyAction.ENTER -> handleEnter()
            KeyAction.MODE_SYMBOLS -> {
                mode = KeyboardMode.SYMBOLS
                if (!capsLock) setShift(false)
                refreshForCurrentEditor()
            }
            KeyAction.MODE_SYMBOLS_MORE -> {
                mode = KeyboardMode.SYMBOLS_MORE
                refreshForCurrentEditor()
            }
            KeyAction.MODE_LETTERS -> {
                mode = KeyboardMode.LETTERS
                refreshForCurrentEditor()
                updateAutoCaps()
            }
            KeyAction.LANG -> cycleLocale()
            KeyAction.HIDE -> requestHideSelf(0)
        }
    }

    private fun handleKeyLongPress(spec: KeySpec): Boolean = when (spec.action) {
        KeyAction.SHIFT -> {
            capsLock = true
            setShift(true)
            toast("Caps lock on")
            true
        }
        KeyAction.SPACE -> {
            // Phase 1: single locale. Long-press space becomes the language
            // switcher in Phase 3.
            toast("More languages coming in Phase 3")
            true
        }
        KeyAction.ENTER -> {
            requestHideSelf(0)
            true
        }
        else -> false // let the view show long-press options, if any
    }

    private fun handleShiftTap() {
        val now = SystemClock.uptimeMillis()
        if (now - lastShiftTapMs < DOUBLE_TAP_MS) {
            capsLock = !capsLock
            setShift(capsLock)
            toast(if (capsLock) "Caps lock on" else "Caps lock off")
        } else {
            if (capsLock) {
                capsLock = false
                setShift(false)
            } else {
                setShift(!shifted)
            }
        }
        lastShiftTapMs = now
    }

    private fun handleSpace() {
        val ic = currentInputConnection
        if (settings.doubleSpacePeriodEnabled && ic != null) {
            val before = runCatching { ic.getTextBeforeCursor(2, 0)?.toString() }.getOrNull()
            if (before != null && before.length == 2 && before[1] == ' ' &&
                before[0] != ' ' && before[0] != '\n'
            ) {
                runCatching {
                    ic.deleteSurroundingText(1, 0)
                    ic.commitText(". ", 1)
                }
                updateAutoCaps()
                return
            }
        }
        commitText(" ")
        updateAutoCaps()
    }

    private fun handleEnter() {
        val action = imeAction
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            val handled = runCatching {
                currentInputConnection?.performEditorAction(action)
            }.getOrNull() ?: false
            if (!handled) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
        updateAutoCaps()
    }

    private fun cycleLocale() {
        // Phase 1: English only.
        toast("More languages coming in Phase 3")
    }

    private fun handleStripAction(action: SuggestionStripView.StripAction) {
        when (action) {
            SuggestionStripView.StripAction.SETTINGS -> {
                val intent = Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { startActivity(intent) }
            }
            SuggestionStripView.StripAction.CLIPBOARD ->
                toast("Clipboard history coming in Phase 3")
            SuggestionStripView.StripAction.EMOJI ->
                toast("Emoji view coming in Phase 3")
        }
    }

    // ---- Helpers ----

    private fun commitText(text: String) {
        val ic = currentInputConnection
        if (ic == null) {
            // Extremely rare (no focused editor): best effort via key events
            // only supports newline; other text is dropped safely.
            if (text == "\n") sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            return
        }
        runCatching { ic.commitText(text, 1) }
    }

    private fun setShift(value: Boolean) {
        shifted = value
        keyboardView?.shifted = value
        keyboardView?.capsLock = capsLock
    }

    private fun updateAutoCaps() {
        if (!settings.autoCapsEnabled || capsLock || mode != KeyboardMode.LETTERS) return
        val before = runCatching {
            currentInputConnection?.getTextBeforeCursor(AUTO_CAPS_LOOKBACK, 0)?.toString()
        }.getOrNull() ?: return
        setShift(shouldAutoCapitalize(before))
    }

    private fun performFeedback() {
        if (settings.hapticFeedback) {
            Feedback.keypressVibrate(this, settings.hapticStrength)
        }
        if (settings.keypressSound) {
            val audio = getSystemService(AudioManager::class.java)
            if (audio != null) Feedback.keyClick(audio)
        }
    }

    private fun toast(message: String) {
        runCatching { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        private const val DOUBLE_TAP_MS = 350L
        private const val AUTO_CAPS_LOOKBACK = 120
    }
}
