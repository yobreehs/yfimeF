package app.revanced.extension.spotify.misc

import app.revanced.extension.shared.Logger
import app.revanced.extension.shared.Utils
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

    private val PREMIUM_OVERRIDES by lazy {
        listOf(
            // Disables player and app ads.
            OverrideAttribute("ads", false),
            // Works along on-demand, allows playing any song without restriction.
            OverrideAttribute("player-license", "premium"),
            OverrideAttribute("player-license-v2", "premium", !IS_SPOTIFY_LEGACY_APP_TARGET),
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

    /**
     * A list of home sections feature types ids which should be removed. These ids match the ones from the protobuf
     * response which delivers home sections.
     */
    private val REMOVED_HOME_SECTIONS by lazy {
        val clazz = classLoader.loadClass("com.spotify.home.evopage.homeapi.proto.Section")
        listOf(
            XposedHelpers.getStaticIntField(clazz, "VIDEO_BRAND_AD_FIELD_NUMBER"),
            XposedHelpers.getStaticIntField(clazz, "IMAGE_BRAND_AD_FIELD_NUMBER")
        )
    }

    /**
     * A list of lists which contain strings that match whether a context menu item should be filtered out.
     * The main approach used is matching context menu items by the id of their text resource.
     */
    private val FILTERED_CONTEXT_MENU_ITEMS_BY_STRINGS: List<List<String>> =
        listOf( // "Listen to music ad-free" upsell on playlists.
            listOf(getResourceIdentifier("context_menu_remove_ads")),
            // "Listen to music ad-free" upsell on albums.
            listOf(getResourceIdentifier("playlist_entity_reinventfree_adsfree_context_menu_item")),
            // "Start a Jam" context menu item, but only filtered if the user does not have premium and the item is
            // being used as a Premium upsell (ad).
            listOf(
                getResourceIdentifier("group_session_context_menu_start"),
                "isPremiumUpsell=true"
            )
        )

    /**
     * Utility method for returning resources ids as strings.
     */
    private fun getResourceIdentifier(resourceIdentifierName: String): String {
        return Utils.getResourceIdentifier(resourceIdentifierName, "id").toString()
    }

    /**
     * Injection point. Override account attributes.
     */
    fun overrideAttributes(attributes: Map<String, *>) {
        try {
            for (override in PREMIUM_OVERRIDES) {
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
            Logger.printException( { "overrideAttributes failure" }, ex)
        }
    }

    /**
     * Injection point. Remove station data from Google Assistant URI.
     */
    fun removeStationString(spotifyUriOrUrl: String): String {
        return spotifyUriOrUrl.replace("spotify:station:", "spotify:")
    }

    /**
     * Injection point. Remove ads sections from home.
     * Depends on patching abstract protobuf list ensureIsMutable method.
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

    /**
     * Injection point. Returns whether the context menu item is a Premium ad.
     */
    fun isFilteredContextMenuItem(contextMenuItem: Any?): Boolean {
        if (contextMenuItem == null) {
            return false
        }

        val stringifiedContextMenuItem = contextMenuItem.toString()
        return FILTERED_CONTEXT_MENU_ITEMS_BY_STRINGS.any { filters ->
            filters.all(stringifiedContextMenuItem::contains)
        }
    }
}
