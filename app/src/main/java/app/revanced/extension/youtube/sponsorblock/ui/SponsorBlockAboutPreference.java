package app.revanced.extension.youtube.sponsorblock.ui;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Keep;

import app.revanced.extension.youtube.settings.preference.UrlLinkPreference;

@SuppressWarnings("unused")
public class SponsorBlockAboutPreference extends UrlLinkPreference {
    {
        externalUrl = "https://sponsor.ajay.app";
    }

    public SponsorBlockAboutPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }
    public SponsorBlockAboutPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Keep
    public SponsorBlockAboutPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    public SponsorBlockAboutPreference(Context context) {
        super(context);
    }
}
