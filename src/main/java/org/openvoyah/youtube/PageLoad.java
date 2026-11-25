package org.openvoyah.youtube;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What covers the page during one main-frame load. The first real failure of a load wins, so the WebView's own error
 * page committing afterwards does not hide it. A timeout is only provisional: a page that arrives late replaces it,
 * and a real error that explains it replaces it too. A renderer loss always shows, whatever the load did.
 */
final class PageLoad {
    /** Long enough for a slow mobile connection, short enough that a blocked site does not look like a black app. */
    static final long TIMEOUT_MS = 30_000L;
    /** After this the loading screen says the page is still on its way. */
    static final long SLOW_MS = 10_000L;
    /** How long after the first content the page gets to draw something before it counts as blank. */
    static final long BLANK_CHECK_MS = 15_000L;
    private static final int MAX_DETAIL = 160;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** JavaScript for {@link #drewNothing}. */
    static final String BLANK_PROBE = "(function(){var b=document.body;if(!b)return 0;"
            + "return (b.innerText||'').trim().length+document.images.length+document.getElementsByTagName('video').length})()";

    private PageStatus status = PageStatus.LOADING;

    PageStatus status() {
        return status;
    }

    void started() {
        status = PageStatus.LOADING;
    }

    void visible() {
        if (status == PageStatus.LOADING || isTimedOut()) status = PageStatus.SHOWN;
    }

    void failed(Problem problem) {
        failed(problem, "");
    }

    void failed(Problem problem, String detail) {
        if (status instanceof PageStatus.Failed && !isTimedOut()) return;
        status = new PageStatus.Failed(problem, bounded(detail));
    }

    void timedOut() {
        if (status == PageStatus.LOADING) status = new PageStatus.Failed(Problem.TIMEOUT, TIMEOUT_MS / 1000 + " s");
    }

    void rendererGone(boolean crashed) {
        status = new PageStatus.Failed(crashed ? Problem.RENDERER_CRASHED : Problem.RENDERER_KILLED);
    }

    private boolean isTimedOut() {
        return status instanceof PageStatus.Failed && ((PageStatus.Failed) status).problem == Problem.TIMEOUT;
    }

    /** One line of at most {@link #MAX_DETAIL} characters: error texts can carry long URLs or stack traces. */
    static String bounded(String text) {
        return Text.take(Text.trim(WHITESPACE.matcher(text).replaceAll(" ")), MAX_DETAIL);
    }

    /** A logcat line for a failure: the problem, its bounded detail and the WebView version, never a page address. */
    static String logLine(PageStatus.Failed failure, String webView) {
        return "page_failed problem=" + failure.problem.name().toLowerCase(Locale.ROOT)
                + " webview=" + (webView == null ? "none" : webView)
                + (failure.detail.isEmpty() ? "" : " detail=" + failure.detail);
    }

    /**
     * The blank check's answer: the page's text length plus its images and videos, as the WebView returns it from
     * JavaScript. Zero means the page drew nothing; anything unreadable counts as drawn, so it never raises a false
     * alarm.
     */
    static boolean drewNothing(String probeResult) {
        Integer count = probeResult == null ? null : Text.toIntOrNull(Text.trim(probeResult));
        return count != null && count == 0;
    }
}
