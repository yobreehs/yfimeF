/*
 * Custom changes:
 * Wipe stubbed types: REMOVED_HOME_SECTIONS, overrideAttributes, removeHomeSections
 * */
package app.revanced.extension.spotify.misc;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import app.revanced.extension.shared.Logger;
import de.robv.android.xposed.XposedHelpers;

@SuppressWarnings("unused")
public final class UnlockPremiumPatch {

    private static class OverrideAttribute {
        /**
         * Account attribute key.
         */
        final String key;

        /**
         * Override value.
         */
        final Object overrideValue;

        /**
         * If this attribute is expected to be present in all situations.
         * If false, then no error is raised if the attribute is missing.
         */
        final boolean isExpected;

        OverrideAttribute(String key, Object overrideValue) {
            this(key, overrideValue, true);
        }

        OverrideAttribute(String key, Object overrideValue, boolean isExpected) {
            this.key = Objects.requireNonNull(key);
            this.overrideValue = Objects.requireNonNull(overrideValue);
            this.isExpected = isExpected;
        }
    }

    private static final List<OverrideAttribute> PREMIUM_OVERRIDES = List.of(
            // Disables player and app ads.
            new OverrideAttribute("ads", FALSE),
            // Works along on-demand, allows playing any song without restriction.
            new OverrideAttribute("player-license", "premium"),
            // Alternative key used by some app versions; not present on all accounts,
            // so its absence must not be reported as an error.
            new OverrideAttribute("player-license-v2", "premium", false),
            // Disables shuffle being initially enabled when first playing a playlist.
            new OverrideAttribute("shuffle", FALSE),
            // Allows playing any song on-demand, without a shuffled order.
            new OverrideAttribute("on-demand", TRUE),
            // Make sure playing songs is not disabled remotely and playlists show up.
            new OverrideAttribute("streaming", TRUE),
            // Allows adding songs to queue and removes the smart shuffle mode restriction,
            // allowing to pick any of the other modes. Flag is not present in legacy app target.
            new OverrideAttribute("pick-and-shuffle", FALSE),
            // Legacy hack: the old module blanked this value to disable the shuffle-mode rule.
            // It is an opaque server token validated by a checksum (CRC32 == 0x72FBEE40) and parsed
            // by the native player (liborbit-jni-spotify.so, streaming_rule.proto), so an empty
            // string fails validation — this is exactly what "Free-account playback mode" turns off.
            new OverrideAttribute("streaming-rules", ""),
            // Enables premium UI in settings and removes the premium button in the nav-bar.
            new OverrideAttribute("nft-disabled", "1"),
            // Enable Spotify Connect and disable other premium related UI, like buying premium.
            // It also removes the download button.
            new OverrideAttribute("type", "premium"),
            // Enable Spotify Car Thing hardware device.
            // Device is discontinued and no longer works with the latest releases,
            // but it might still work with older app targets.
            new OverrideAttribute("can_use_superbird", TRUE, false),
            // Removes the premium button in the nav-bar for tablet users.
            new OverrideAttribute("tablet-free", FALSE, false)
    );

    private static volatile boolean attributesDumped = false;

    /**
     * Log the full product state once per process. The previous builds only logged the attributes
     * that were overridden, which hid the real account type and every unknown key.
     */
    private static void dumpAttributes(Map<String, ?> attributes) {
        if (attributesDumped) return;
        attributesDumped = true;

        StringBuilder dump = new StringBuilder();
        for (Map.Entry<String, ?> entry : attributes.entrySet()) {
            Object value = entry.getValue();
            try {
                value = XposedHelpers.getObjectField(value, "value_");
            } catch (Throwable ignored) {
                // Not an attribute wrapper, log the raw map value.
            }

            String text;
            if (value instanceof String || value instanceof Boolean || value instanceof Number) {
                text = String.valueOf(value);
            } else {
                text = value == null ? "null" : value.getClass().getSimpleName();
            }
            if (text.length() > 80) {
                text = text.substring(0, 80) + "…";
            }

            dump.append(entry.getKey()).append('=').append(text).append("; ");
        }

        String text = dump.toString();
        Logger.printInfo(() -> "Product state attributes (" + attributes.size() + "): " + text);
    }

    /**
     * Read-only dump of the product state (used when premium spoofing is disabled, so we can still
     * see what the client believes about the account in bug reports). Logs once per process.
     */
    public static void logProductState(Map<String, ?> attributes) {
        try {
            dumpAttributes(attributes);
        } catch (Exception ex) {
            Logger.printException(() -> "logProductState failure", ex);
        }
    }

    public static void overrideAttributes(Map<String, ?> attributes) {
        try {
            dumpAttributes(attributes);
            for (OverrideAttribute override : PREMIUM_OVERRIDES) {
                var attribute = attributes.get(override.key);

                if (attribute == null) {
                    if (override.isExpected) {
                        Logger.printException(() -> "Attribute " + override.key + " expected but not found");
                    }
                    continue;
                }

                Object overrideValue = override.overrideValue;
                Object originalValue;
                originalValue = XposedHelpers.getObjectField(attribute, "value_");

                if (overrideValue.equals(originalValue)) {
                    continue;
                }

                Logger.printInfo(() -> "Overriding account attribute " + override.key +
                        " from " + originalValue + " to " + overrideValue);

                XposedHelpers.setObjectField(attribute, "value_", overrideValue);
            }
        } catch (Exception ex) {
            Logger.printException(() -> "overrideAttributes failure", ex);
        }
    }

    /**
     * Injection point. Remove station data from Google Assistant URI.
     */
    public static String removeStationString(String spotifyUriOrUrl) {
        try {
            Logger.printInfo(() -> "Removing station string from " + spotifyUriOrUrl);
            return spotifyUriOrUrl.replace("spotify:station:", "spotify:");
        } catch (Exception ex) {
            Logger.printException(() -> "removeStationString failure", ex);
            return spotifyUriOrUrl;
        }
    }

    private interface FeatureTypeIdProvider<T> {
        int getFeatureTypeId(T section);
    }

    private static <T> void removeSections(
            List<T> sections,
            FeatureTypeIdProvider<T> featureTypeExtractor,
            List<Integer> idsToRemove
    ) {
        try {
            Iterator<T> iterator = sections.iterator();

            while (iterator.hasNext()) {
                T section = iterator.next();
                int featureTypeId = featureTypeExtractor.getFeatureTypeId(section);
                if (idsToRemove.contains(featureTypeId)) {
                    Logger.printInfo(() -> "Removing section with feature type id " + featureTypeId);
                    iterator.remove();
                }
            }
        } catch (Exception ex) {
            Logger.printException(() -> "removeSections failure", ex);
        }
    }

    /**
     * Resolve a protobuf FIELD_NUMBER constant from the real section class at runtime.
     * Hardcoded numbers are not stable across app versions.
     */
    private static int getFieldNumber(Class<?> sectionClass, String fieldName) {
        try {
            return XposedHelpers.getStaticIntField(sectionClass, fieldName);
        } catch (Throwable t) {
            Logger.printException(() -> "Field number " + fieldName + " not found", t);
            return -1;
        }
    }

    /**
     * Remove ads sections from home.
     */
    public static void removeHomeSections(List<?> sections) {
        Logger.printInfo(() -> "Removing ads section from home");
        if (sections.isEmpty()) return;
        Class<?> sectionClass = sections.get(0).getClass();
        List<Integer> idsToRemove = List.of(
                getFieldNumber(sectionClass, "PROMOTION_V1_FIELD_NUMBER"),
                getFieldNumber(sectionClass, "PROMOTION_V3_FIELD_NUMBER"),
                getFieldNumber(sectionClass, "VIDEO_BRAND_AD_FIELD_NUMBER"),
                getFieldNumber(sectionClass, "IMAGE_BRAND_AD_FIELD_NUMBER")
        );
        removeSections(
                sections,
                section -> XposedHelpers.getIntField(section, "featureTypeCase_"),
                idsToRemove
        );
    }

    /**
     * Remove ads sections from browse.
     */
    public static void removeBrowseSections(List<?> sections) {
        Logger.printInfo(() -> "Removing ads section from browse");
        if (sections.isEmpty()) return;
        Class<?> sectionClass = sections.get(0).getClass();
        List<Integer> idsToRemove = List.of(
                getFieldNumber(sectionClass, "BRAND_ADS_FIELD_NUMBER")
        );
        removeSections(
                sections,
                section -> XposedHelpers.getIntField(section, "sectionTypeCase_"),
                idsToRemove
        );
    }

    /**
     * Remove the embedded brand-ad card(s) from the Now Playing View scroll feed, i.e. the ad
     * banner shown below the lyrics card.
     */
    public static void removeNpvBrandAdSections(List<?> sections) {
        Logger.printInfo(() -> "Removing brand ad section from now playing feed");
        if (sections.isEmpty()) return;
        Class<?> sectionClass = sections.get(0).getClass();
        List<Integer> idsToRemove = List.of(
                getFieldNumber(sectionClass, "IMAGE_BRAND_AD_FIELD_NUMBER")
        );
        removeSections(
                sections,
                section -> XposedHelpers.getIntField(section, "sectionTypeCase_"),
                idsToRemove
        );
    }
}
