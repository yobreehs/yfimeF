package app.revanced.extension.spotify.misc;

import static io.github.chsbuffer.revancedxposed.MainHookKt.HostClassLoader;

import de.robv.android.xposed.XposedHelpers;

public class Section {

    static {
        try {
            var clazz = HostClassLoader.loadClass("com.spotify.home.evopage.homeapi.proto.Section");
            VIDEO_BRAND_AD_FIELD_NUMBER = XposedHelpers.getStaticIntField(clazz, "VIDEO_BRAND_AD_FIELD_NUMBER");
            IMAGE_BRAND_AD_FIELD_NUMBER = XposedHelpers.getStaticIntField(clazz, "IMAGE_BRAND_AD_FIELD_NUMBER");
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static int VIDEO_BRAND_AD_FIELD_NUMBER;
    public static int IMAGE_BRAND_AD_FIELD_NUMBER;
}
