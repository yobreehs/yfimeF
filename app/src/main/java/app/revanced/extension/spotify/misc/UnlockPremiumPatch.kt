package app.revanced.extension.spotify.misc

import app.revanced.extension.shared.Logger
import de.robv.android.xposed.XposedHelpers
import kotlin.properties.Delegates

@Suppress("unused")
object UnlockPremiumPatch {
    /**
     * If the app target is 8.6.98.900.
     */
    var IS_SPOTIFY_LEGACY_APP_TARGET by Delegates.notNull<Boolean>()
    lateinit var classLoader: ClassLoader

    private class OverrideAttribute(
        /**
         * Account attribute key.
         */
        val key: String,
        /**
         * Override value.
         */
        val overrideValue: Any,
        /**
         * If this attribute is expected to be present in all situations.
         * If false, then no error is raised if the attribute is missing.
         */
        val isExpected: Boolean = true
    )

    private val OVERRIDES by lazy {
        listOf(
            // Disables player and app ads.
            OverrideAttribute("ads", false),
            // Works along on-demand, allows playing any song without restriction.
            OverrideAttribute("player-license", "premium"),
            // Disables shuffle being initially enabled when first playing a playlist.
            OverrideAttribute("shuffle", false),
            // Allows playing any song on-demand, without a shuffled order.
            OverrideAttribute("on-demand", true),
            // Make sure playing songs is not disabled remotely and playlists show up.
            OverrideAttribute("streaming", true),
            // Allows adding songs to queue and removes the smart shuffle mode restriction,
            // allowing to pick any of the other modes. Flag is not present in legacy app target.
            OverrideAttribute("pick-and-shuffle", false, !IS_SPOTIFY_LEGACY_APP_TARGET),
            // Disables shuffle-mode streaming-rule, which forces songs to be played shuffled
            // and breaks the player when other patches are applied.
            OverrideAttribute("streaming-rules", ""),
            // Enables premium UI in settings and removes the premium button in the nav-bar.
            OverrideAttribute("nft-disabled", "1"),
            // Enable Spotify Connect and disable other premium related UI, like buying premium.
            // It also removes the download button.
            OverrideAttribute("type", "premium"),
            // Enable Spotify Car Thing hardware device.
            // Device is discontinued and no longer works with the latest releases,
            // but it might still work with older app targets.
            OverrideAttribute("can_use_superbird", true, false),
            // Removes the premium button in the nav-bar for tablet users.
            OverrideAttribute("tablet-free", false, false)
        )
    }

    private val REMOVED_HOME_SECTIONS by lazy {
        val clazz = classLoader.loadClass("com.spotify.home.evopage.homeapi.proto.Section")
        listOf(
            XposedHelpers.getStaticIntField(clazz, "VIDEO_BRAND_AD_FIELD_NUMBER"),
            XposedHelpers.getStaticIntField(clazz, "IMAGE_BRAND_AD_FIELD_NUMBER")
        )
    }

    /**
     * Injection point. Override account attributes.
     */
    fun overrideAttribute(attributes: Map<String, *>) {
        try {
            for (override in OVERRIDES) {
                val attribute = attributes[override.key]
                if (attribute == null) {
                    if (override.isExpected) {
                        Logger.printException { "'" + override.key + "' expected but not found" }
                    }
                } else {
                    XposedHelpers.setObjectField(attribute, "value_", override.overrideValue)
                }
            }
        } catch (ex: Exception) {
            Logger.printException( { "overrideAttribute failure" }, ex)
        }
    }

    /**
     * Injection point. Remove station data from Google assistant URI.
     */
    fun removeStationString(spotifyUriOrUrl: String): String {
        return spotifyUriOrUrl.replace("spotify:station:", "spotify:")
    }

    /**
     * Injection point. Remove ads sections from home.
     * Depends on patching protobuffer list remove method.
     */
    fun removeHomeSections(sections: MutableList<Any>) {
        try {
            sections.removeIf { section: Any ->
                REMOVED_HOME_SECTIONS.contains(
                    XposedHelpers.getIntField(section, "featureTypeCase_")
                )
            }
        } catch (ex: Exception) {
            Logger.printException( { "Remove home sections failure" }, ex)
        }
    }

}
