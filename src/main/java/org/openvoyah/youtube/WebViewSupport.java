package org.openvoyah.youtube;

/** Whether the installed WebView can run the page at all, judged from its provider's version. */
final class WebViewSupport {
    /**
     * The oldest WebView the wrapper opens without a warning. Chromium 80 is the first with the ES2020 syntax current
     * web apps ship; YouTube's scripts on an older engine tend to stop before anything is drawn.
     */
    static final int MIN_MAJOR = 80;

    private WebViewSupport() {}

    static Integer major(String version) {
        if (version == null) return null;
        String trimmed = Text.trim(version);
        int dot = trimmed.indexOf('.');
        return Text.toIntOrNull(dot < 0 ? trimmed : trimmed.substring(0, dot));
    }

    /** {@code version} is the provider's version name, or null when Android reports no WebView provider. */
    static Problem problem(String version) {
        if (version == null) return Problem.NO_WEBVIEW;
        Integer major = major(version);
        if (major == null) return null;
        return major < MIN_MAJOR ? Problem.OLD_WEBVIEW : null;
    }
}
