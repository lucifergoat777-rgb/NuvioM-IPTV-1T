package com.liskovsoft.googlecommon.common.locale;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build.VERSION;
import android.os.LocaleList;
import com.liskovsoft.sharedutils.locale.LocaleUpdater;
import com.liskovsoft.sharedutils.locale.LocaleUtility;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;

import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class LocaleManager {
    private static LocaleManager sInstance;
    /** A region chosen for YouTube's feeds; null follows the device. */
    private static String sCountryOverride;
    private String mLang;
    private String mCountry;
    private int mOffsetFromUtcMinutes;

    private LocaleManager() {
        initLang();
        initUtcOffset();
    }

    public static LocaleManager instance() {
        if (sInstance == null) {
            sInstance = new LocaleManager();
        }

        return sInstance;
    }

    public static void unhold() {
        sInstance = null;
    }

    public String getLanguage() {
        return mLang;
    }

    /**
     * Used in tests only
     */
    public void setLanguage(String lang) {
        mLang = lang;
    }

    public String getCountry() {
        return sCountryOverride != null && !sCountryOverride.isEmpty() ? sCountryOverride : mCountry;
    }

    /** A region for YouTube's feeds, or null to follow the device. */
    public static void setCountryOverride(String country) {
        sCountryOverride = country;
    }

    /**
     * Used in tests only
     */
    public void setCountry(String country) {
        mCountry = country;
    }

    public int getUtcOffsetMinutes() {
        return mOffsetFromUtcMinutes;
    }

    private void initLang() {
        Locale locale = getLocale();

        if (VERSION.SDK_INT >= 21) {
            // Use BCP-47 code for language code to get content with correct language.
            // For example, BCP-47 has zn-CN for simplified Chinese and zh-TW for traditional Chinese.
            mLang = locale.toLanguageTag();
        } else {
            mLang = locale.getLanguage();
        }

        // The app's language can have no region (for example "es" or "es-419"), which made YouTube
        // fall back to a country such as Spain for News and Live. The device's own region is the
        // better default; a region chosen in YouTube settings overrides it in getCountry().
        mCountry = getSystemCountry();
        if (mCountry.isEmpty()) {
            mCountry = locale.getCountry();
        }
    }

    private String getSystemCountry() {
        try {
            Configuration configuration = Resources.getSystem().getConfiguration();
            if (VERSION.SDK_INT >= 24) {
                LocaleList locales = configuration.getLocales();
                if (!locales.isEmpty()) {
                    return locales.get(0).getCountry();
                }
            } else {
                return configuration.locale.getCountry();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private void initUtcOffset() {
        TimeZone tz = TimeZone.getDefault();
        Date now = new Date();
        mOffsetFromUtcMinutes = tz.getOffset(now.getTime()) / 1_000 / 60;
    }

    private Locale getLocale() {
        Locale locale;

        // Proper locale
        if (GlobalPreferences.isInitialized()) {
            locale = LocaleUpdater.getSavedLocale(GlobalPreferences.sInstance.getContext());

            if (locale == null) {
                locale = LocaleUtility.getCurrentLocale(GlobalPreferences.sInstance.getContext());
            }
        } else { // Fallback locale
            locale = Locale.getDefault();
        }

        return locale;
    }
}
