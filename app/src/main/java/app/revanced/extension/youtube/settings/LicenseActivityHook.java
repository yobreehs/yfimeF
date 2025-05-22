package app.revanced.extension.youtube.settings;

import android.app.Activity;
import android.preference.PreferenceFragment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toolbar;

import java.util.Objects;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.youtube.ThemeHelper;
import app.revanced.extension.youtube.settings.preference.ReVancedPreferenceFragment;
import app.revanced.extension.youtube.settings.preference.SponsorBlockPreferenceFragment;
import io.github.chsbuffer.revancedxposed.youtube.misc.RevancedSettingsLayout;

/**
 * Hooks LicenseActivity.
 * <p>
 * This class is responsible for injecting our own fragment by replacing the LicenseActivity.
 */
@SuppressWarnings("unused")
public class LicenseActivityHook {

    private static ViewGroup.LayoutParams toolbarLayoutParams;

    public static void setToolbarLayoutParams(Toolbar toolbar) {
        if (toolbarLayoutParams != null) {
            toolbar.setLayoutParams(toolbarLayoutParams);
        }
    }

    /**
     * Injection point.
     */
    public static boolean useCairoSettingsFragment(boolean original) {
        // On the first launch of a clean install, forcing the cairo menu can give a
        // half broken appearance because all the preference icons may not be available yet.
        // 19.34+ cairo settings are always on, so it doesn't need to be forced anyway.
        // Cairo setting will show on the next launch of the app.
        return original;
    }

    /**
     * Injection point.
     * <p>
     * Hooks LicenseActivity#onCreate in order to inject our own fragment.
     */
    public static void initialize(Activity licenseActivity) {
        try {
            ThemeHelper.setActivityTheme(licenseActivity);
            ThemeHelper.setNavigationBarColor(licenseActivity.getWindow());
            RevancedSettingsLayout layout = new RevancedSettingsLayout(licenseActivity);
            licenseActivity.setContentView(layout);

            PreferenceFragment fragment;
            String toolbarTitleResourceName;
            String dataString = Objects.requireNonNullElse(licenseActivity.getIntent().getStringExtra("data"), "revanced_settings_intent");

            switch (dataString) {
                case "revanced_sb_settings_intent":
                    toolbarTitleResourceName = "revanced_sb_settings_title";
                    fragment = new SponsorBlockPreferenceFragment();
                    break;
/*                case "revanced_ryd_settings_intent":
                    toolbarTitleResourceName = "revanced_ryd_settings_title";
                    fragment = new ReturnYouTubeDislikePreferenceFragment();
                    break;
                    */
                case "revanced_settings_intent":
                    toolbarTitleResourceName = "revanced_settings_title";
                    fragment = new ReVancedPreferenceFragment();
                    break;
                default:
                    Logger.printException(() -> "Unknown setting: " + dataString);
                    return;
            }

            layout.setTitle(toolbarTitleResourceName);

            var containerId = View.generateViewId();
            layout.getFragmentsContainer().setId(containerId);
            //noinspection deprecation
            licenseActivity.getFragmentManager()
                    .beginTransaction()
                    .replace(containerId, fragment)
                    .commit();
        } catch (Exception ex) {
            Logger.printException(() -> "initialize failure", ex);
        }
    }
}
