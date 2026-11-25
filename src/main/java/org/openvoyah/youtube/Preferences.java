package org.openvoyah.youtube;

import android.content.Context;
import android.content.SharedPreferences;

/** The app's own settings; the page keeps its own state in the WebView. */
final class Preferences {
    private static final String FILE = "youtube";
    private static final String STARTUP_ANIMATION = "startup_animation";

    private Preferences() {}

    static boolean startupAnimation(Context context) {
        return preferences(context).getBoolean(STARTUP_ANIMATION, true);
    }

    static void setStartupAnimation(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(STARTUP_ANIMATION, enabled).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
