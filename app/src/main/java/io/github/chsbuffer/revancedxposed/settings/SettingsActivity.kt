package io.github.chsbuffer.revancedxposed.settings

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.system.Os
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.github.chsbuffer.revancedxposed.BuildConfig
import io.github.chsbuffer.revancedxposed.R
import org.json.JSONObject
import java.io.File

/**
 * Settings screen of the module — Material 3 looking, AMOLED black.
 *
 * The colours come from the system palette (`system_accent1_*`, `system_neutral1/2_*`) on Android
 * 12+, so the screen follows the user's wallpaper ("Material You"). Below Android 12, or when the
 * system exposes no palette, it falls back to the Spotify green. The Material Components library is
 * not a dependency on purpose: the module APK is loaded by Spotify and staying small matters more
 * than using the official widgets here.
 *
 * Values are stored privately in the module app and exposed to the Spotify hooks by
 * [FemifySettingsProvider] / [FemifySettingsReceiver] (see [ModuleSettings]).
 */
class SettingsActivity : Activity() {

    private var experimentalToast: Toast? = null
    private var snackbarView: View? = null
    private var snackbarHide: Runnable? = null
    private lateinit var palette: Palette
    private lateinit var statusView: TextView
    private var statusTarget: String = ""
    private var statusGeneration = 0
    private val statusHandler = Handler(Looper.getMainLooper())

    private data class Toggle(val key: String, val title: Int, val summary: Int)
    private data class Section(val title: Int, val toggles: List<Toggle>)

    /**
     * Countries offered by the experimental region proxy. Order is the order in the chooser;
     * the first one is the default. "NL" and "DE" are the two the user asked about, the rest are
     * the other large Spotify markets.
     */
    private val regions = listOf(
        "NL" to "Netherlands",
        "DE" to "Germany",
        "US" to "United States",
        "GB" to "United Kingdom",
        "SE" to "Sweden",
        "FR" to "France",
        "PL" to "Poland",
        "ES" to "Spain",
        "IT" to "Italy",
        "CA" to "Canada",
        "AU" to "Australia",
        "BR" to "Brazil",
        "NO" to "Norway",
        "FI" to "Finland",
        "DK" to "Denmark",
        "CH" to "Switzerland",
        "AT" to "Austria",
        "IE" to "Ireland",
        "PT" to "Portugal",
        "CZ" to "Czechia",
    )

    private val sections = listOf(
        Section(
            R.string.section_playback,
            listOf(
                Toggle(
                    ModuleSettings.PREMIUM_UNLOCK,
                    R.string.setting_premium_title,
                    R.string.setting_premium_summary,
                ),
                Toggle(
                    ModuleSettings.BLOCK_ADS,
                    R.string.setting_ads_title,
                    R.string.setting_ads_summary,
                ),
            ),
        ),
        Section(
            R.string.section_library,
            listOf(
                Toggle(
                    ModuleSettings.SHOW_UNAVAILABLE_TRACKS,
                    R.string.setting_unavailable_title,
                    R.string.setting_unavailable_summary,
                ),
            ),
        ),
        Section(
            R.string.section_privacy,
            listOf(
                Toggle(
                    ModuleSettings.SANITIZE_SHARING_LINKS,
                    R.string.setting_links_title,
                    R.string.setting_links_summary,
                ),
            ),
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        migrateLegacyPrefsPermissions()
        setContentView(buildContent(openPrefs()))
    }

    override fun onResume() {
        super.onResume()
        // The provider stores the settings Spotify loaded at its last start. Re-check on resume so
        // a restart in the background flips the status from "restart required" to "applied".
        if (::statusView.isInitialized) refreshApplyStatus()
    }

    override fun onDestroy() {
        statusHandler.removeCallbacksAndMessages(null)
        snackbarHide?.let { window.decorView.removeCallbacks(it) }
        snackbarView?.animate()?.cancel()
        super.onDestroy()
    }

    private fun openPrefs(): SharedPreferences =
        getSharedPreferences(ModuleSettings.PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Old builds stored the preferences as world-readable for `XSharedPreferences`. New files are
     * already private, but an existing XML may still carry the old mode, so fix it once.
     */
    private fun migrateLegacyPrefsPermissions() {
        val prefsDir = File(dataDir, "shared_prefs")
        listOf(
            File(prefsDir, "${ModuleSettings.PREFS_NAME}.xml"),
            File(prefsDir, "${ModuleSettings.PREFS_NAME}.xml.bak"),
        ).forEach { file ->
            if (!file.exists()) return@forEach
            try {
                if ((Os.stat(file.absolutePath).st_mode and 0x3F) != 0) {
                    Os.chmod(file.absolutePath, 0x180) // 0600
                }
            } catch (_: Throwable) {
                // Not fatal: the next MODE_PRIVATE write creates a correctly permissioned file.
            }
        }
    }

    private fun buildContent(prefs: SharedPreferences): View {
        palette = Palette()
        val colors = palette
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(16), dp(24), dp(16), dp(28))
        }

        // The old "Xposed module for Spotify" subtitle is intentionally gone: the title now uses
        // that vertical room and reads as the single header of the screen.
        content.addView(text(R.string.settings_title, 40f, colors.onSurface).apply {
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(4), 0, 0, dp(10))
            setOnLongClickListener {
                showOkSnackbar()
                true
            }
        })

        sections.forEach { section ->
            content.addView(sectionHeader(section.title, colors))
            content.addView(sectionCard(prefs, section, colors))
        }

        if (prefs.getBoolean(ModuleSettings.EXPERIMENTAL_UNLOCKED, false)) {
            content.addView(sectionHeader(R.string.section_experimental, colors))
            content.addView(experimentalCard(prefs, colors))
        }

        statusView = textValue("", 13f, colors.onSurfaceVariant).apply {
            setPadding(dp(4), dp(24), 0, dp(4))
            minHeight = dp(20)
            gravity = Gravity.CENTER_VERTICAL
        }
        content.addView(statusView)
        setStatusImmediately(applyStatusText(prefs))

        val about = text(
            R.string.settings_about, 12f, colors.onSurfaceVariant, BuildConfig.VERSION_NAME,
            BuildConfig.COMMIT_HASH,
        ).apply {
            setPadding(dp(4), 0, 0, 0)
        }
        attachExperimentalUnlock(about, prefs)
        content.addView(about)

        return ScrollView(this).apply {
            isFillViewport = true
            fitsSystemWindows = true
            setBackgroundColor(Color.BLACK)
            addView(
                content,
                android.widget.FrameLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    /**
     * HMA-OSS shows a bottom Snackbar with "OK" when its module-status card is long-pressed.
     * Femify has no Material dependency, so this is the same idea built from a plain overlay:
     * a rounded bar that slides up from the bottom and disappears after the SHORT timeout.
     */
    private fun showOkSnackbar() {
        hideSnackbar(immediately = true)
        val colors = palette
        val bar = TextView(this).apply {
            text = getString(android.R.string.ok)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(colors.onSurface)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            background = GradientDrawable().apply {
                cornerRadius = dpf(8f)
                setColor(colors.trackOff)
            }
            elevation = dpf(6f)
            alpha = 0f
            translationY = dp(24).toFloat()
            setOnClickListener { hideSnackbar() }
        }
        addContentView(
            bar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(48),
                Gravity.BOTTOM,
            ).apply {
                setMargins(dp(16), 0, dp(16), dp(32))
            },
        )
        bar.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(180L)
            .start()
        snackbarView = bar
        val hide = Runnable { hideSnackbar() }
        snackbarHide = hide
        window.decorView.postDelayed(hide, 4_000L)
    }

    private fun hideSnackbar(immediately: Boolean = false) {
        snackbarHide?.let { window.decorView.removeCallbacks(it) }
        snackbarHide = null
        val bar = snackbarView ?: return
        snackbarView = null
        bar.animate().cancel()
        if (immediately) {
            (bar.parent as? ViewGroup)?.removeView(bar)
            return
        }
        bar.animate()
            .alpha(0f)
            .translationY(dp(24).toFloat())
            .setDuration(160L)
            .withEndAction { (bar.parent as? ViewGroup)?.removeView(bar) }
            .start()
    }

    private fun text(res: Int, sizeSp: Float, color: Int, vararg args: Any?): TextView =
        TextView(this).apply {
            text = if (args.isEmpty()) getString(res) else getString(res, *args)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(color)
        }

    private fun textValue(value: String, sizeSp: Float, color: Int): TextView =
        TextView(this).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(color)
        }

    private fun sectionHeader(titleRes: Int, colors: Palette): TextView =
        text(titleRes, 12f, colors.primary).apply {
            text = text.toString().uppercase()
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.08f
            setPadding(dp(8), dp(20), 0, dp(8))
        }

    private fun sectionCard(
        prefs: SharedPreferences,
        section: Section,
        colors: Palette,
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dpf(20f)
                setColor(colors.surface)
            }
        }
        section.toggles.forEachIndexed { index, toggle ->
            if (index > 0) card.addView(divider(colors))
            card.addView(toggleRow(prefs, toggle, colors))
        }
        return card
    }

    private fun experimentalCard(prefs: SharedPreferences, colors: Palette): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dpf(20f)
                setColor(colors.surface)
            }
        }

        fun add(view: View) {
            if (card.childCount > 0) card.addView(divider(colors))
            card.addView(view)
        }

        add(
            toggleRow(
                prefs,
                Toggle(
                    ModuleSettings.REGION_PROXY_ENABLED,
                    R.string.setting_region_proxy_title,
                    R.string.setting_region_proxy_summary,
                ),
                colors,
            ),
        )

        add(
            valueRow(
                R.string.setting_region_title,
                regionLabel(prefs.getString(ModuleSettings.REGION_PROXY_COUNTRY, "NL") ?: "NL"),
                colors,
            ) { summary ->
                showRegionDialog(prefs, summary)
            },
        )

        add(
            valueRow(
                R.string.setting_region_custom_title,
                prefs.getString(ModuleSettings.REGION_PROXY_CUSTOM, "").orEmpty()
                    .ifEmpty { getString(R.string.setting_region_custom_none) },
                colors,
            ) { summary ->
                showCustomProxyDialog(prefs, summary)
            },
        )

        add(
            toggleRow(
                prefs,
                Toggle(
                    ModuleSettings.FORCE_SHOW_REGION_DIALOG,
                    R.string.setting_force_show_region_title,
                    R.string.setting_force_show_region_summary,
                ),
                colors,
            ),
        )

        return card
    }

    /**
     * A settings row that shows a string value and opens a dialog when tapped. Used by the region
     * chooser and the custom proxy field.
     */
    private fun valueRow(
        titleRes: Int,
        value: String,
        colors: Palette,
        onClick: (TextView) -> Unit,
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = ripple(colors)
            isClickable = true
        }

        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(text(titleRes, 16f, colors.onSurface))
        val summary = textValue(value, 13f, colors.onSurfaceVariant).apply {
            setPadding(0, dp(2), dp(8), 0)
        }
        texts.addView(summary)
        row.addView(
            texts,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.setOnClickListener { onClick(summary) }
        return row
    }

    private fun showRegionDialog(prefs: SharedPreferences, summary: TextView) {
        val current = prefs.getString(ModuleSettings.REGION_PROXY_COUNTRY, "NL") ?: "NL"
        val colors = palette
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                minOf(regions.size * dp(52), (resources.displayMetrics.heightPixels * 0.55f).toInt())
                    .coerceAtLeast(dp(120)),
            )

            isFillViewport = false
            // The list already has a visible edge; a scrollbar on top of the AMOLED card looks
            // like a stock Android dialog and was explicitly removed.
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            addView(list, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        }

        showFemifyDialog(
            title = getString(R.string.setting_region_dialog_title),
            body = { dialog ->
                regions.forEachIndexed { index, (code, name) ->
                    if (index > 0) list.addView(divider(colors))
                    val selected = code == current
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(14), dp(12), dp(14), dp(12))
                        background = ripple(colors)
                        isClickable = true
                    }
                    row.addView(
                        textValue(
                            "$name ($code)",
                            16f,
                            if (selected) colors.primary else colors.onSurface,
                        ),
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                    )
                    row.addView(
                        textValue(
                            if (selected) getString(R.string.setting_region_selected) else "",
                            12f,
                            colors.primary,
                        ).apply {
                            setTypeface(typeface, Typeface.BOLD)
                            gravity = Gravity.END
                            setPadding(dp(8), 0, 0, 0)
                        },
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                    row.setOnClickListener {
                        prefs.edit()
                            .putString(ModuleSettings.REGION_PROXY_COUNTRY, code)
                            .apply()
                        summary.text = regionLabel(code)
                        refreshApplyStatus()
                        dialog.dismiss()
                    }
                    list.addView(row)
                }
                scroll
            },
        )
    }

    private fun showCustomProxyDialog(prefs: SharedPreferences, summary: TextView) {
        val colors = palette
        val input = EditText(this).apply {
            setText(prefs.getString(ModuleSettings.REGION_PROXY_CUSTOM, "").orEmpty())
            hint = getString(R.string.setting_region_custom_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(colors.onSurface)
            setHintTextColor(colors.onSurfaceVariant)
            background = GradientDrawable().apply {
                cornerRadius = dpf(12f)
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), colors.outline)
            }
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }

        showFemifyDialog(
            title = getString(R.string.setting_region_custom_title),
            message = getString(R.string.setting_region_custom_message),
            body = { input },
            positiveLabel = getString(android.R.string.ok),
        ) {
            val value = input.text?.toString()?.trim().orEmpty()
            prefs.edit().putString(ModuleSettings.REGION_PROXY_CUSTOM, value).apply()
            summary.text = value.ifEmpty {
                getString(R.string.setting_region_custom_none)
            }
            refreshApplyStatus()
        }
    }

    /**
     * One dialog style for every chooser in the screen: grey surface card matching the settings
     * cards, dimmed black background and the same accent colours as the rest of the screen.
     */
    private fun showFemifyDialog(
        title: String,
        message: String? = null,
        body: ((Dialog) -> View)? = null,
        positiveLabel: String? = null,
        onPositive: (() -> Unit)? = null,
    ): Dialog {
        val colors = palette
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dpf(24f)
                setColor(colors.surface)
            }
            setPadding(dp(24), dp(20), dp(24), dp(14))
        }

        card.addView(textValue(title, 22f, colors.onSurface).apply {
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(12))
        })

        message?.let {
            card.addView(textValue(it, 14f, colors.onSurfaceVariant).apply {
                setPadding(0, 0, 0, dp(14))
            })
        }

        body?.let { build ->
            val view = build(dialog)
            val params = view.layoutParams ?: LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            card.addView(view, params)
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(10), 0, 0)
        }
        buttons.addView(
            dialogButton(getString(android.R.string.cancel)) { dialog.dismiss() }
        )
        if (positiveLabel != null) {
            buttons.addView(
                dialogButton(positiveLabel) {
                    onPositive?.invoke()
                    dialog.dismiss()
                }
            )
        }
        card.addView(buttons)

        val root = FrameLayout(this).apply {
            setPadding(dp(18), dp(18), dp(18), dp(18))
            addView(card, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        }
        dialog.setContentView(root)
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            val params = window.attributes
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.gravity = Gravity.CENTER
            params.dimAmount = 0.78f
            window.attributes = params
        }
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        return dialog
    }

    private fun dialogButton(label: String, onClick: () -> Unit): TextView {
        val colors = palette
        return textValue(label.uppercase(), 14f, colors.primary).apply {
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = ripple(colors)
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    private fun regionLabel(code: String): String {
        val name = regions.firstOrNull { it.first == code }?.second ?: code
        return "$name ($code)"
    }

    private fun applyStatusText(prefs: SharedPreferences): String =
        if (settingsMatchApplied(prefs)) {
            getString(R.string.settings_applied)
        } else {
            getString(R.string.settings_restart_hint)
        }

    /**
     * Compares the live preferences with the snapshot Spotify reported when it started. If the
     * snapshot is missing (first launch, provider hidden) we assume the process is up to date
     * instead of showing a false "restart required".
     */
    private fun settingsMatchApplied(prefs: SharedPreferences): Boolean {
        val raw = prefs.getString(ModuleSettings.APPLIED_SNAPSHOT, null) ?: return true
        val snapshot = runCatching { JSONObject(raw) }.getOrNull() ?: return true

        ModuleSettings.booleanDefaults.forEach { (key, default) ->
            if (snapshot.has(key) && snapshot.optBoolean(key, default) != prefs.getBoolean(key, default)) {
                return false
            }
        }
        ModuleSettings.stringDefaults.forEach { (key, default) ->
            val applied = snapshot.optString(key, default)
            val current = prefs.getString(key, default) ?: default
            if (snapshot.has(key) && applied != current) return false
        }
        return true
    }

    private fun setStatusImmediately(value: String) {
        statusTarget = value
        statusView.text = value
    }

    private fun refreshApplyStatus() {
        if (!::statusView.isInitialized) return
        val target = applyStatusText(openPrefs())
        if (target == statusTarget) return

        statusTarget = target
        statusGeneration++
        statusHandler.removeCallbacksAndMessages(null)
        statusHandler.post(StatusTypewriter(target, statusGeneration))
    }

    /**
     * Typewriter transition for the status line: erase the old message, then type the new one.
     * Changes are frequent while the user flips switches, so each update supersedes the previous
     * animation through [statusGeneration] instead of queueing behind it.
     */
    private inner class StatusTypewriter(
        private val target: String,
        private val generation: Int,
    ) : Runnable {
        private var erasing = true

        override fun run() {
            if (generation != statusGeneration || !::statusView.isInitialized) return
            val current = statusView.text.toString()
            if (erasing) {
                if (current.isNotEmpty()) {
                    statusView.text = current.dropLast(1)
                    statusHandler.postDelayed(this, 7L)
                } else {
                    erasing = false
                    // A short empty pause makes the erase/type transition readable instead of
                    // looking like one continuous string morphing into another.
                    statusHandler.postDelayed(this, 700L)
                }
            } else if (current.length < target.length) {
                statusView.text = target.substring(0, current.length + 1)
                statusHandler.postDelayed(this, 14L)
            }
        }
    }

    /**
     * Experimental features are hidden by default. Eight consecutive taps on the version footer
     * unlock the section; eight taps again hide it. Every tap shows how many are left, so the
     * gesture is discoverable instead of feeling like a random click.
     */
    private fun attachExperimentalUnlock(view: TextView, prefs: SharedPreferences) {
        var taps = 0
        var lastTapAt = 0L
        view.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTapAt > 3_000L) taps = 0
            lastTapAt = now
            taps++

            val unlocked = prefs.getBoolean(ModuleSettings.EXPERIMENTAL_UNLOCKED, false)
            val remaining = (8 - taps).coerceAtLeast(0)
            when {
                remaining > 0 -> {
                    val res = if (unlocked) R.plurals.experimental_taps_to_disable
                    else R.plurals.experimental_taps_to_enable
                    showExperimentalToast(
                        resources.getQuantityString(res, remaining, remaining)
                    )
                }

                unlocked -> {
                    prefs.edit().putBoolean(ModuleSettings.EXPERIMENTAL_UNLOCKED, false).apply()
                    showExperimentalToast(getString(R.string.experimental_disabled))
                    recreate()
                }

                else -> {
                    val editor = prefs.edit()
                    // First-ever unlock starts with every experimental switch off. Values the
                    // user already changed in an earlier session stay untouched.
                    if (!prefs.getBoolean(ModuleSettings.EXPERIMENTAL_INITIALIZED, false)) {
                        if (!prefs.contains(ModuleSettings.REGION_PROXY_ENABLED)) {
                            editor.putBoolean(ModuleSettings.REGION_PROXY_ENABLED, false)
                        }
                        if (!prefs.contains(ModuleSettings.FORCE_SHOW_REGION_DIALOG)) {
                            editor.putBoolean(ModuleSettings.FORCE_SHOW_REGION_DIALOG, false)
                        }
                        editor.putBoolean(ModuleSettings.EXPERIMENTAL_INITIALIZED, true)
                    }
                    editor.putBoolean(ModuleSettings.EXPERIMENTAL_UNLOCKED, true).apply()
                    showExperimentalToast(getString(R.string.experimental_unlocked))
                    recreate()
                }
            }
        }
    }

    private fun showExperimentalToast(message: String) {
        experimentalToast?.cancel()
        experimentalToast = Toast.makeText(this, message, Toast.LENGTH_SHORT).also { it.show() }
    }

    private fun toggleRow(prefs: SharedPreferences, toggle: Toggle, colors: Palette): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = ripple(colors)
            isClickable = true
        }

        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(text(toggle.title, 16f, colors.onSurface))
        texts.addView(text(toggle.summary, 13f, colors.onSurfaceVariant).apply {
            setPadding(0, dp(2), dp(8), 0)
        })
        row.addView(
            texts,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )

        val switch = Md3Switch(this).apply {
            isChecked = prefs.getBoolean(
                toggle.key, ModuleSettings.booleanDefaults[toggle.key] ?: false
            )
            trackColorOn = colors.primary
            trackColorOff = colors.trackOff
            thumbColorOn = colors.onPrimary
            thumbColorOff = colors.outline
            outlineColor = colors.outline
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(toggle.key, checked).apply()
                refreshApplyStatus()
            }
        }
        row.addView(switch, LinearLayout.LayoutParams(dp(52), dp(48)))

        // The whole row toggles the switch, as in the system settings.
        row.setOnClickListener { switch.toggle() }
        return row
    }

    private fun divider(colors: Palette): View = View(this).apply {
        setBackgroundColor(colors.divider)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1),
        ).apply {
            marginStart = dp(16)
            marginEnd = dp(16)
        }
    }

    private fun ripple(colors: Palette): RippleDrawable = RippleDrawable(
        ColorStateList.valueOf(withAlpha(colors.primary, 0.16f)),
        null,
        GradientDrawable().apply {
            cornerRadius = dpf(20f)
            setColor(Color.WHITE)
        },
    )

    private fun withAlpha(color: Int, alpha: Float): Int = Color.argb(
        (Color.alpha(color) * alpha).toInt(), Color.red(color), Color.green(color), Color.blue(color)
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun dpf(value: Float): Float = value * resources.displayMetrics.density

    /**
     * MD3 dark-scheme colours. On Android 12+ the tones come from the wallpaper-derived palette;
     * otherwise the module keeps its Spotify-green accent on AMOLED black.
     */
    private inner class Palette {
        private val dynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        @Suppress("DEPRECATION")
        private fun sys(id: Int, fallback: Int): Int =
            if (dynamic) runCatching { resources.getColor(id, theme) }.getOrDefault(fallback)
            else fallback

        val primary: Int = sys(android.R.color.system_accent1_200, SPOTIFY_GREEN)
        val onPrimary: Int = sys(android.R.color.system_accent1_900, 0xFF06280F.toInt())
        val surface: Int = sys(android.R.color.system_neutral1_900, 0xFF101010.toInt())
        val onSurface: Int = sys(android.R.color.system_neutral1_50, Color.WHITE)
        val onSurfaceVariant: Int = sys(android.R.color.system_neutral2_200, 0xFF9A9A9A.toInt())
        val outline: Int = sys(android.R.color.system_neutral2_500, 0xFF6E6E6E.toInt())
        val trackOff: Int = sys(android.R.color.system_neutral2_700, 0xFF393939.toInt())
        val divider: Int = sys(android.R.color.system_neutral2_800, 0xFF1F1F1F.toInt())
    }

    private companion object {
        const val SPOTIFY_GREEN = 0xFF1DB954.toInt()
    }
}
