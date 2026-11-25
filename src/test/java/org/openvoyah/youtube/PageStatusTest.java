package org.openvoyah.youtube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.openvoyah.youtube.PageStatus.Failed;

import java.util.Arrays;

public class PageStatusTest {
    @Test public void firstContentHidesTheLoadingScreen() {
        PageLoad load = new PageLoad();
        assertEquals(PageStatus.LOADING, load.status());
        load.visible();
        assertEquals(PageStatus.SHOWN, load.status());
    }

    @Test public void theWebViewErrorPageDoesNotHideAFailure() {
        PageLoad load = new PageLoad();
        load.started();
        load.failed(Problem.UNREACHABLE, "net::ERR_CONNECTION_RESET (-6)");
        load.visible();
        assertEquals(new Failed(Problem.UNREACHABLE, "net::ERR_CONNECTION_RESET (-6)"), load.status());
    }

    @Test public void theFirstFailureOfALoadWins() {
        PageLoad load = new PageLoad();
        load.started();
        load.failed(Problem.CERTIFICATE, "SSL error 3");
        load.failed(Problem.UNREACHABLE);
        assertEquals(Problem.CERTIFICATE, ((Failed) load.status()).problem);
    }

    @Test public void aTimeoutGivesWayToALatePageOrTheRealError() {
        PageLoad late = new PageLoad();
        late.started();
        late.timedOut();
        assertEquals(new Failed(Problem.TIMEOUT, "30 s"), late.status());
        late.visible();
        assertEquals(PageStatus.SHOWN, late.status());

        PageLoad explained = new PageLoad();
        explained.started();
        explained.timedOut();
        explained.failed(Problem.UNREACHABLE, "reset");
        assertEquals(new Failed(Problem.UNREACHABLE, "reset"), explained.status());
    }

    @Test public void aTimeoutNeverReplacesAShownPageOrAnError() {
        PageLoad shown = new PageLoad();
        shown.visible();
        shown.timedOut();
        assertEquals(PageStatus.SHOWN, shown.status());
        PageLoad failed = new PageLoad();
        failed.failed(Problem.HTTP, "HTTP 503");
        failed.timedOut();
        assertEquals(Problem.HTTP, ((Failed) failed.status()).problem);
    }

    @Test public void retryStartsAFreshLoad() {
        PageLoad load = new PageLoad();
        load.failed(Problem.NO_NETWORK);
        load.started();
        assertEquals(PageStatus.LOADING, load.status());
    }

    @Test public void rendererLossAlwaysShows() {
        PageLoad load = new PageLoad();
        load.visible();
        load.rendererGone(false);
        assertEquals(new Failed(Problem.RENDERER_KILLED), load.status());
        load.rendererGone(true);
        assertEquals(new Failed(Problem.RENDERER_CRASHED), load.status());
    }

    @Test public void detailsAreOneBoundedLine() {
        char[] tail = new char[500];
        Arrays.fill(tail, 'x');
        String detail = PageLoad.bounded("Uncaught SyntaxError:\n   Unexpected token ." + new String(tail));
        assertTrue(detail.startsWith("Uncaught SyntaxError: Unexpected token ."));
        assertEquals(160, detail.length());
    }

    @Test public void logLineNamesProblemVersionAndDetail() {
        assertEquals("page_failed problem=timeout webview=74.0.3729.136 detail=30 s",
                PageLoad.logLine(new Failed(Problem.TIMEOUT, "30 s"), "74.0.3729.136"));
        assertEquals("page_failed problem=no_webview webview=none",
                PageLoad.logLine(new Failed(Problem.NO_WEBVIEW), null));
    }

    @Test public void blankProbeOnlyFlagsAnExplicitZero() {
        assertTrue(PageLoad.drewNothing("0"));
        assertFalse(PageLoad.drewNothing("42"));
        assertFalse(PageLoad.drewNothing("null"));
        assertFalse(PageLoad.drewNothing(null));
    }

    @Test public void webViewVersionDecidesWhetherThePageCanRun() {
        assertEquals(Problem.NO_WEBVIEW, WebViewSupport.problem(null));
        assertEquals(Problem.OLD_WEBVIEW, WebViewSupport.problem("74.0.3729.136"));
        assertNull(WebViewSupport.problem("80.0.3987.99"));
        assertNull(WebViewSupport.problem("124.0.6367.219"));
        assertNull(WebViewSupport.problem("unknown"));
        assertEquals(Integer.valueOf(124), WebViewSupport.major("124.0.6367.219"));
    }

    @Test public void textsTrimUnicodeSpacesAndParseOnlyNumbers() {
        assertEquals("a b", Text.trim(" \t a b \n "));
        assertEquals("", Text.trim("   "));
        assertEquals("ab", Text.take("abc", 2));
        assertEquals("abc", Text.take("abc", 5));
        assertEquals(Integer.valueOf(-7), Text.toIntOrNull("-7"));
        assertNull(Text.toIntOrNull(""));
        assertNull(Text.toIntOrNull("1.5"));
    }
}
