package org.openvoyah.youtube;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** What the wrapper loads, which navigations stay inside it and how it introduces itself to the site. */
final class WebPolicy {
    /** The touch layout. It adapts to a wide car window and works signed out, with no Google services on the unit. */
    static final String START_URL = "https://m.youtube.com/";

    enum Route {
        /** Stays in the wrapper: YouTube itself and the Google pages that signing in passes through. */
        IN_APP,
        /** Another site from a description or a comment, handed to the head unit's browser. */
        EXTERNAL,
        /** App-store, intent and YouTube-app links that cannot work on a unit without Google Play. */
        IGNORED,
    }

    enum Back { EXIT_FULLSCREEN, PAGE_BACK, LEAVE }

    private static final List<String> IN_APP_DOMAINS =
            Arrays.asList("youtube.com", "youtu.be", "youtube-nocookie.com", "google.com");
    private static final Set<String> IGNORED_SCHEMES = new HashSet<>(
            Arrays.asList("intent", "vnd.youtube", "market", "android-app", "javascript", "file", "content"));
    private static final int MAX_RESUME_URL = 2048;
    private static final Pattern VERSION_TOKEN = Pattern.compile(" Version/\\d+(?:\\.\\d+)*");
    private static final Pattern UNREAD_COUNT = Pattern.compile("^\\(\\d+\\+?\\)\\s+");
    private static final String SITE_SUFFIX = " - YouTube";
    private static final float MIN_PAGE_SCALE = .5f;
    private static final float MAX_PAGE_SCALE = 2.5f;

    private WebPolicy() {}

    static Route route(String url) {
        URI uri;
        try {
            uri = new URI(Text.trim(url));
        } catch (Exception unparsable) {
            return Route.IGNORED;
        }
        if (uri.getScheme() == null) return Route.IGNORED;
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.equals("about") || scheme.equals("data") || scheme.equals("blob")) return Route.IN_APP;
        if (IGNORED_SCHEMES.contains(scheme)) return Route.IGNORED;
        if (!scheme.equals("https") && !scheme.equals("http")) return Route.EXTERNAL;
        if (uri.getHost() == null) return Route.IGNORED;
        String host = withoutTrailingDots(uri.getHost().toLowerCase(Locale.ROOT));
        if (host.equals("play.google.com") || host.equals("market.android.com")) return Route.IGNORED;
        for (String domain : IN_APP_DOMAINS) {
            if (host.equals(domain) || host.endsWith("." + domain)) return Route.IN_APP;
        }
        // Sign-in can pass through a country domain such as accounts.google.ru.
        if (host.startsWith("accounts.google.")) return Route.IN_APP;
        return Route.EXTERNAL;
    }

    private static String withoutTrailingDots(String host) {
        int end = host.length();
        while (end > 0 && host.charAt(end - 1) == '.') end--;
        return host.substring(0, end);
    }

    /** The page to reopen after a recreation or a renderer crash: a YouTube address of reasonable length, else the feed. */
    static String resumeUrl(String url) {
        if (url == null || url.length() > MAX_RESUME_URL || route(url) != Route.IN_APP) return START_URL;
        int colon = url.indexOf(':');
        String scheme = (colon < 0 ? url : url.substring(0, colon)).toLowerCase(Locale.ROOT);
        return scheme.equals("https") || scheme.equals("http") ? url : START_URL;
    }

    /**
     * The system WebView agent minus its embedded-view markers, so the site treats the wrapper as the browser it is
     * built on. Google refuses to sign in inside a view that announces itself as embedded; the engine stays the real one.
     */
    static String userAgent(String system) {
        return VERSION_TOKEN.matcher(system.replace("; wv)", ")")).replaceAll("");
    }

    /**
     * The video title for the media session. The WebView has no Media Session API for the page to fill, so the title
     * comes from the document: "(3) Song - YouTube" becomes "Song"; the feed's bare "YouTube" stays as it is.
     */
    static String mediaTitle(String documentTitle) {
        String title = UNREAD_COUNT.matcher(Text.trim(documentTitle)).replaceAll("");
        String bare = Text.trim(title.endsWith(SITE_SUFFIX)
                ? title.substring(0, title.length() - SITE_SUFFIX.length()) : title);
        return bare.isEmpty() ? title : bare;
    }

    /**
     * How much larger than the display's own scale the page is drawn: the window's density from its DPI profile over
     * the display's density. Unknown densities and a profile that matches the display leave the page as it is.
     */
    static float pageScale(int windowDpi, int displayDpi) {
        if (windowDpi <= 0 || displayDpi <= 0) return 1f;
        float scale = Math.max(MIN_PAGE_SCALE, Math.min(MAX_PAGE_SCALE, (float) windowDpi / displayDpi));
        return Math.abs(scale - 1f) < .01f ? 1f : scale;
    }

    /** Back closes full screen first, then walks the page history, then leaves the app like a media app does. */
    static Back back(boolean fullscreen, boolean canGoBack) {
        if (fullscreen) return Back.EXIT_FULLSCREEN;
        return canGoBack ? Back.PAGE_BACK : Back.LEAVE;
    }
}
