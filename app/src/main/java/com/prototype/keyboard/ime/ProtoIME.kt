package com.prototype.keyboard.ime

import android.content.ClipboardManager
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
import android.widget.ViewFlipper
import com.prototype.keyboard.R
import com.prototype.keyboard.data.BackupCodec
import com.prototype.keyboard.data.ClipboardRepository
import com.prototype.keyboard.data.KeyboardSettings
import com.prototype.keyboard.data.SettingsRepository
import com.prototype.keyboard.data.ThemeMode
import com.prototype.keyboard.data.UserDictionary
import com.prototype.keyboard.data.db.AppDatabase
import com.prototype.keyboard.keyboard.ClipboardBoardView
import com.prototype.keyboard.keyboard.EmojiBoardView
import com.prototype.keyboard.keyboard.GlideTyper
import com.prototype.keyboard.keyboard.KeyAction
import com.prototype.keyboard.keyboard.KeyCenter
import com.prototype.keyboard.keyboard.KeySpec
import com.prototype.keyboard.keyboard.KeyboardMode
import com.prototype.keyboard.keyboard.KeyboardView
import com.prototype.keyboard.keyboard.Layouts
import com.prototype.keyboard.keyboard.SuggestionStripView
import com.prototype.keyboard.keyboard.TrailPoint
import com.prototype.keyboard.suggestions.Suggester
import com.prototype.keyboard.ui.MainActivity
import com.prototype.keyboard.util.Feedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * System keyboard service (the IME).
 *
 * Phase 3a: tap typing + on-device suggestions, conservative autocorrect with
 * one-tap revert, next-word prediction, glide typing, emoji/kaomoji board,
 * sectioned clipboard board with quick-paste, 5 locales, learned words.
 *
 * Privacy gates: password fields get zero suggestions/learning/autocorrect/
 * glide. Cloud AI (Phase 3b) will be strictly per-action opt-in.
 */
class ProtoIME : InputMethodService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var userDict: UserDictionary
    private lateinit var clipboardRepo: ClipboardRepository
    private var settings = KeyboardSettings()

    private var keyboardView: KeyboardView? = null
    private var stripView: SuggestionStripView? = null
    private var emojiBoard: EmojiBoardView? = null
    private var clipBoard: ClipboardBoardView? = null
    private var flipper: ViewFlipper? = null

    private var suggester: Suggester? = null
    private var bundledEn: List<String> = emptyList()

    private var mode: KeyboardMode = KeyboardMode.LETTERS
    private var locale: String = "en"
    private var shifted: Boolean = false
    private var capsLock: Boolean = false
    private var isPasswordField: Boolean = false
    private var imeAction: Int = EditorInfo.IME_ACTION_UNSPECIFIED
    private var lastShiftTapMs: Long = 0L

    private var pendingRevert: AutocorrectSpan? = null
    private var pendingGlideWord: String? = null
    private var lastQuickPasteId: String? = null
    private var lastQuickPasteText: String? = null

    private var clipManager: ClipboardManager? = null
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        onSystemClipboardChanged()
    }

    // ---- Service lifecycle ----

    override fun onCreate() {
        super.onCreate()
        settingsRepo = SettingsRepository(applicationContext)
        userDict = UserDictionary(AppDatabase.get(applicationContext))
        clipboardRepo = ClipboardRepository(applicationContext)
        clipManager = getSystemService(ClipboardManager::class.java)
        runCatching { clipManager?.addPrimaryClipChangedListener(clipListener) }
        scope.launch {
            settingsRepo.settings.collect {
                val localeChanged = it.currentLocale != settings.currentLocale
                settings = it
                if (localeChanged || locale != it.currentLocale) {
                    locale = it.currentLocale
                    refreshForCurrentEditor()
                    scope.launch(Dispatchers.Default) { rebuildSuggesterNow() }
                }
                applySettingsToViews()
            }
        }
        scope.launch(Dispatchers.Default) {
            bundledEn = readBundledWords()
            rebuildSuggesterNow()
        }
    }

    override fun onCreateInputView(): View {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val strip = SuggestionStripView(this).apply {
            listener = object : SuggestionStripView.Listener {
                override fun onStripAction(action: SuggestionStripView.StripAction) {
                    handleStripAction(action)
                }

                override fun onSuggestion(word: String) = acceptSuggestion(word)
                override fun onQuickPaste() = doQuickPaste()
            }
        }
        val keyboard = KeyboardView(this).apply {
            listener = object : KeyboardView.Listener {
                override fun onKey(spec: KeySpec) = handleKey(spec)
                override fun onKeyLongPress(spec: KeySpec): Boolean = handleKeyLongPress(spec)
                override fun onHideRequested() = requestHideSelf(0)
                override fun onGlide(path: List<TrailPoint>, keys: List<KeyCenter>) {
                    handleGlide(path, keys)
                }
            }
            onPressFeedback = { performFeedback() }
        }
        val emoji = EmojiBoardView(this).apply {
            listener = object : EmojiBoardView.Listener {
                override fun onEmoji(text: String) {
                    pendingRevert = null
                    pendingGlideWord = null
                    commitText(text)
                    refreshSuggestions()
                }

                override fun onBackToKeyboard() = showBoard(BOARD_KEYS)
                override fun onFeedback() = performFeedback()
            }
        }
        val clips = ClipboardBoardView(this).apply {
            listener = object : ClipboardBoardView.Listener {
                override fun onPaste(clipId: String, text: String) {
                    pendingRevert = null
                    pendingGlideWord = null
                    commitText(text)
                    updateAutoCaps()
                    refreshSuggestions()
                    scope.launch {
                        clipboardRepo.mutate { it.touch(clipId) }
                        refreshQuickPaste()
                    }
                }

                override fun onBackToKeyboard() = showBoard(BOARD_KEYS)
                override fun onFeedback() = performFeedback()
            }
        }
        val switcher = ViewFlipper(this).apply {
            addView(keyboard)
            addView(emoji)
            addView(clips)
        }

        container.addView(
            strip,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        container.addView(
            switcher,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        stripView = strip
        keyboardView = keyboard
        emojiBoard = emoji
        clipBoard = clips
        flipper = switcher
        clips.bind(clipboardRepo, scope)
        applySettingsToViews()
        refreshForCurrentEditor()
        refreshSuggestions()
        refreshQuickPaste()
        return container
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        inspectEditor(attribute)
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        inspectEditor(info)
        if (!capsLock) setShift(false)
        pendingRevert = null
        pendingGlideWord = null
        mode = if (isNumericClass(info)) KeyboardMode.NUMERIC else KeyboardMode.LETTERS
        showBoard(BOARD_KEYS)
        refreshForCurrentEditor()
        updateAutoCaps()
        refreshSuggestions()
        refreshQuickPaste()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        if (!capsLock) setShift(false)
        pendingRevert = null
        pendingGlideWord = null
    }

    override fun onDestroy() {
        runCatching { clipManager?.removePrimaryClipChangedListener(clipListener) }
        scope.cancel()
        keyboardView = null
        stripView = null
        emojiBoard = null
        clipBoard = null
        flipper = null
        super.onDestroy()
    }

    // ---- Dictionary ----

    private fun readBundledWords(): List<String> {
        return runCatching {
            resources.openRawResource(R.raw.words_en).bufferedReader().useLines { lines ->
                lines.map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") && Suggester.isIndexable(it) }
                    .toList()
            }
        }.getOrNull().orEmpty()
    }

    private fun bundledForLocale(): List<String> =
        if (locale == "en") bundledEn else emptyList()

    private suspend fun rebuildSuggesterNow() {
        val user = runCatching { userDict.wordsForLocale(locale) }.getOrNull().orEmpty()
        val bundled = bundledForLocale()
        val current = suggester
        if (current == null) {
            suggester = Suggester(bundled, user)
        } else {
            current.rebuild(bundled, user)
        }
        withContext(Dispatchers.Main) { refreshSuggestions() }
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
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> isSystemDark()
        }
        keyboardView?.let {
            it.themeDark = dark
            it.keyBorders = settings.keyBorders
            it.glideEnabled = settings.glideEnabled
            if (it.keyHeightDp != settings.keyHeightDp) it.keyHeightDp = settings.keyHeightDp
        }
        stripView?.themeDark = dark
        emojiBoard?.themeDark = dark
        clipBoard?.themeDark = dark
    }

    private fun isSystemDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun localeLabel(): String = locale.uppercase()

    // ---- Boards ----

    private fun showBoard(index: Int) {
        flipper?.displayedChild = index
        if (index == BOARD_CLIPS) clipBoard?.refresh()
    }

    private fun handleStripAction(action: SuggestionStripView.StripAction) {
        when (action) {
            SuggestionStripView.StripAction.SETTINGS -> {
                val intent = Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { startActivity(intent) }
            }
            SuggestionStripView.StripAction.CLIPBOARD -> {
                val target = if (flipper?.displayedChild == BOARD_CLIPS) BOARD_KEYS else BOARD_CLIPS
                showBoard(target)
            }
            SuggestionStripView.StripAction.EMOJI -> {
                val target = if (flipper?.displayedChild == BOARD_EMOJI) BOARD_KEYS else BOARD_EMOJI
                showBoard(target)
            }
        }
    }

    // ---- Key handling ----

    private fun handleKey(spec: KeySpec) {
        when (spec.action) {
            KeyAction.CHAR -> {
                pendingRevert = null
                pendingGlideWord = null
                val text = spec.commitText(shifted || capsLock) ?: return
                commitText(text)
                if (shifted && !capsLock) setShift(false)
            }
            KeyAction.SHIFT -> handleShiftTap()
            KeyAction.DELETE -> handleDelete()
            KeyAction.SPACE -> handleSpace()
            KeyAction.ENTER -> {
                pendingRevert = null
                pendingGlideWord = null
                handleEnter()
            }
            KeyAction.MODE_SYMBOLS -> {
                pendingRevert = null
                mode = KeyboardMode.SYMBOLS
                if (!capsLock) setShift(false)
                refreshForCurrentEditor()
            }
            KeyAction.MODE_SYMBOLS_MORE -> {
                pendingRevert = null
                mode = KeyboardMode.SYMBOLS_MORE
                refreshForCurrentEditor()
            }
            KeyAction.MODE_LETTERS -> {
                pendingRevert = null
                mode = KeyboardMode.LETTERS
                refreshForCurrentEditor()
                updateAutoCaps()
            }
            KeyAction.LANG -> cycleLocale()
            KeyAction.HIDE -> requestHideSelf(0)
        }
        refreshSuggestions()
    }

    private fun handleKeyLongPress(spec: KeySpec): Boolean = when (spec.action) {
        KeyAction.SHIFT -> {
            capsLock = true
            setShift(true)
            toast("Caps lock on")
            true
        }
        KeyAction.SPACE -> {
            cycleLocale()
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

    private fun handleDelete() {
        val ic = currentInputConnection
        val revert = pendingRevert
        if (revert != null && ic != null) {
            // One backspace undoes the last autocorrection (and learns the original).
            runCatching {
                ic.deleteSurroundingText(revert.corrected.length + 1, 0)
                ic.commitText(revert.original, 1)
            }
            pendingRevert = null
            learnWord(revert.original)
            refreshSuggestions()
            return
        }
        pendingRevert = null
        if (ic == null) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        } else {
            runCatching { ic.deleteSurroundingText(1, 0) }
        }
        updateAutoCaps()
    }

    private fun handleSpace() {
        pendingGlideWord = null
        val ic = currentInputConnection
        if (settings.doubleSpacePeriodEnabled && ic != null) {
            val before = runCatching { ic.getTextBeforeCursor(2, 0)?.toString() }.getOrNull()
            if (before != null && before.length == 2 && before[1] == ' ' &&
                before[0] != ' ' && before[0] != '\n'
            ) {
                pendingRevert = null
                runCatching {
                    ic.deleteSurroundingText(1, 0)
                    ic.commitText(". ", 1)
                }
                updateAutoCaps()
                return
            }
        }
        // Autocorrect on space (conservative; never in password fields).
        val sg = suggester
        if (settings.autocorrectEnabled && !isPasswordField && sg != null && ic != null &&
            mode == KeyboardMode.LETTERS
        ) {
            val before = runCatching { ic.getTextBeforeCursor(32, 0)?.toString().orEmpty() }
                .getOrNull().orEmpty()
            val word = extractCurrentWord(before)
            val correction = if (word.isNotEmpty()) sg.autocorrect(word) else null
            if (correction != null) {
                val out = matchCase(word, correction)
                runCatching {
                    ic.deleteSurroundingText(word.length, 0)
                    ic.commitText(out, 1)
                    ic.commitText(" ", 1)
                }
                pendingRevert = AutocorrectSpan(word, out)
                if (shifted && !capsLock) setShift(false)
                updateAutoCaps()
                return
            }
        }
        pendingRevert = null
        if (ic != null) {
            val before = runCatching { ic.getTextBeforeCursor(32, 0)?.toString().orEmpty() }
                .getOrNull().orEmpty()
            val word = extractCurrentWord(before)
            if (word.length >= 2) learnWord(word)
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

    // ---- Suggestions / glide ----

    private fun refreshSuggestions() {
        val strip = stripView ?: return
        val sg = suggester
        if (!settings.suggestionsEnabled || isPasswordField || mode != KeyboardMode.LETTERS ||
            sg == null
        ) {
            strip.setSuggestions(emptyList(), localeLabel())
            return
        }
        val before = runCatching {
            currentInputConnection?.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        }.getOrNull().orEmpty()
        val current = extractCurrentWord(before)
        val list = if (current.isNotEmpty()) {
            sg.completions(current)
        } else {
            val prev = previousWord(before)
            if (prev.isEmpty()) emptyList() else sg.nextWords(prev)
        }
        strip.setSuggestions(list, localeLabel())
    }

    private fun acceptSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        val before = runCatching { ic.getTextBeforeCursor(64, 0)?.toString().orEmpty() }
            .getOrNull().orEmpty()
        val current = extractCurrentWord(before)
        val glideWord = pendingGlideWord
        pendingRevert = null
        pendingGlideWord = null
        runCatching {
            if (current.isNotEmpty()) {
                ic.deleteSurroundingText(current.length, 0)
            } else if (glideWord != null && before.endsWith("$glideWord ")) {
                ic.deleteSurroundingText(glideWord.length + 1, 0)
            }
            val out = if (shifted || capsLock) matchCase("X", word) else word
            ic.commitText("$out ", 1)
        }
        learnWord(word)
        if (shifted && !capsLock) setShift(false)
        updateAutoCaps()
        refreshSuggestions()
    }

    private fun handleGlide(path: List<TrailPoint>, keys: List<KeyCenter>) {
        pendingRevert = null
        pendingGlideWord = null
        val sg = suggester
        if (!settings.glideEnabled || !settings.suggestionsEnabled || isPasswordField ||
            mode != KeyboardMode.LETTERS || locale != "en" || sg == null
        ) {
            return
        }
        val keyWidth = keyboardView?.keyUnitWidthPx ?: 60f
        val results = GlideTyper.decode(path, keys, keyWidth, bundledEn.ifEmpty {
            emptyList()
        })
        val ic = currentInputConnection
        if (results.isEmpty() || ic == null) {
            // Fallback: commit the nearest letter to the trail end.
            val end = path.lastOrNull()
            if (end != null && ic != null) {
                val nearest = keys.minByOrNull {
                    val dx = it.x - end.x
                    val dy = it.y - end.y
                    dx * dx + dy * dy
                }
                if (nearest != null) {
                    val dx = nearest.x - end.x
                    val dy = nearest.y - end.y
                    if (dx * dx + dy * dy <= keyWidth * keyWidth) {
                        val ch = if (shifted || capsLock) {
                            nearest.ch.uppercaseChar().toString()
                        } else {
                            nearest.ch.lowercaseChar().toString()
                        }
                        commitText(ch)
                        if (shifted && !capsLock) setShift(false)
                        refreshSuggestions()
                    }
                }
            }
            return
        }
        val top = results[0]
        val before = runCatching { ic.getTextBeforeCursor(64, 0)?.toString().orEmpty() }
            .getOrNull().orEmpty()
        val current = extractCurrentWord(before)
        runCatching {
            if (current.isNotEmpty()) ic.deleteSurroundingText(current.length, 0)
            val out = if (shifted || capsLock) matchCase("X", top) else top
            ic.commitText("$out ", 1)
        }
        pendingGlideWord = top
        stripView?.setSuggestions(results, localeLabel())
        learnWord(top)
        if (shifted && !capsLock) setShift(false)
        updateAutoCaps()
    }

    private fun learnWord(word: String) {
        if (!settings.learningEnabled || isPasswordField) return
        scope.launch(Dispatchers.Default) {
            userDict.learn(word, locale)
            rebuildSuggesterNow()
        }
    }

    // ---- Locales ----

    private fun cycleLocale() {
        val order = LOCALE_ORDER
        val next = order[(order.indexOf(locale) + 1).mod(order.size)]
        toast(LOCALE_NAMES[next] ?: next)
        scope.launch { settingsRepo.setCurrentLocale(next) }
    }

    // ---- Clipboard capture + quick paste ----

    private fun onSystemClipboardChanged() {
        if (!settings.clipboardCaptureEnabled) return
        scope.launch {
            val text = runCatching {
                val clip = clipManager?.primaryClip ?: return@launch
                if (clip.itemCount == 0) return@launch
                clip.getItemAt(0)?.coerceToText(this@ProtoIME)?.toString()
            }.getOrNull()
            if (text.isNullOrBlank()) return@launch
            clipboardRepo.mutate { it.addClip(text) }
            refreshQuickPaste()
            if (flipper?.displayedChild == BOARD_CLIPS) clipBoard?.refresh()
        }
    }

    private fun refreshQuickPaste() {
        val strip = stripView
        if (strip == null) return
        if (!settings.quickPasteEnabled) {
            lastQuickPasteId = null
            lastQuickPasteText = null
            strip.setQuickPaste(null)
            return
        }
        scope.launch {
            val store = clipboardRepo.load()
            val latest = store.latestPasteable()
            lastQuickPasteId = latest?.id
            lastQuickPasteText = latest?.text
            if (latest == null) {
                strip.setQuickPaste(null)
            } else {
                val sensitive = store.isSensitiveSection(latest.sectionId)
                val firstLine = latest.text.lineSequence().firstOrNull().orEmpty()
                val preview = (if (sensitive) "🔒 " else "") + firstLine.take(10)
                strip.setQuickPaste(preview)
            }
        }
    }

    private fun doQuickPaste() {
        val text = lastQuickPasteText ?: return
        val id = lastQuickPasteId
        pendingRevert = null
        pendingGlideWord = null
        commitText(text)
        updateAutoCaps()
        refreshSuggestions()
        if (id != null) {
            scope.launch {
                clipboardRepo.mutate { it.touch(id) }
                refreshQuickPaste()
            }
        }
    }

    // ---- Helpers ----

    private fun commitText(text: String) {
        val ic = currentInputConnection
        if (ic == null) {
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

    /** Capitalize [correction] like [typed] (ALL-CAPS or Leading-cap). */
    private fun matchCase(typed: String, correction: String): String {
        if (correction.isEmpty()) return correction
        return when {
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } ->
                correction.uppercase()
            typed.firstOrNull()?.isUpperCase() == true ->
                correction.replaceFirstChar { it.uppercase() }
            else -> correction
        }
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

        private const val BOARD_KEYS = 0
        private const val BOARD_EMOJI = 1
        private const val BOARD_CLIPS = 2

        private val LOCALE_ORDER = listOf("en", "es", "de", "fr", "hi")
        private val LOCALE_NAMES = mapOf(
            "en" to "English",
            "es" to "Español",
            "de" to "Deutsch",
            "fr" to "Français",
            "hi" to "हिन्दी"
        )

        // Referenced to keep the backup codec linked & covered (used by the app UI).
        @Suppress("unused")
        private const val BACKUP_VERSION = 1
    }
}
