package io.github.chsbuffer.revancedxposed.settings

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.system.Os
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.chsbuffer.revancedxposed.BuildConfig
import io.github.chsbuffer.revancedxposed.R
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

    private data class Toggle(val key: String, val title: Int, val summary: Int)
    private data class Section(val title: Int, val toggles: List<Toggle>)

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
        val colors = Palette()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(16), dp(24), dp(16), dp(28))
        }

        content.addView(text(R.string.settings_title, 30f, colors.onSurface).apply {
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(4), 0, 0, 0)
        })

        content.addView(text(R.string.settings_subtitle, 13f, colors.onSurfaceVariant).apply {
            setPadding(dp(4), dp(4), 0, dp(8))
        })

        sections.forEach { section ->
            content.addView(sectionHeader(section.title, colors))
            content.addView(sectionCard(prefs, section, colors))
        }

        content.addView(text(R.string.settings_restart_hint, 13f, colors.onSurfaceVariant).apply {
            setPadding(dp(4), dp(24), 0, dp(4))
        })

        content.addView(
            text(
                R.string.settings_about, 12f, colors.onSurfaceVariant, BuildConfig.VERSION_NAME,
                BuildConfig.COMMIT_HASH,
            ).apply {
                setPadding(dp(4), 0, 0, 0)
            },
        )

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

    private fun text(res: Int, sizeSp: Float, color: Int, vararg args: Any?): TextView =
        TextView(this).apply {
            text = if (args.isEmpty()) getString(res) else getString(res, *args)
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
            isChecked = prefs.getBoolean(toggle.key, ModuleSettings.defaults[toggle.key] ?: false)
            trackColorOn = colors.primary
            trackColorOff = colors.trackOff
            thumbColorOn = colors.onPrimary
            thumbColorOff = colors.outline
            outlineColor = colors.outline
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(toggle.key, checked).apply()
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
