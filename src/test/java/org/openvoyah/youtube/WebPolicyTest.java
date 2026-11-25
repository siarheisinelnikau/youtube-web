package org.openvoyah.youtube;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.openvoyah.youtube.WebPolicy.Route;

import java.util.Arrays;

public class WebPolicyTest {
    @Test public void youtubeAndSignInPagesStayInTheWrapper() {
        for (String url : new String[] {
                "https://m.youtube.com/watch?v=abc", "https://www.youtube.com/", "https://youtu.be/abc",
                "https://accounts.google.com/ServiceLogin?service=youtube", "https://accounts.youtube.com/accounts/SetSID",
                "https://consent.youtube.com/m?continue=x", "https://accounts.google.ru/", "http://youtube.com/",
                "https://www.youtube-nocookie.com/embed/abc", "about:blank",
        }) {
            assertEquals(url, Route.IN_APP, WebPolicy.route(url));
        }
    }

    @Test public void otherSitesOpenInTheBrowser() {
        for (String url : new String[] {
                "https://example.com/", "https://notyoutube.com/", "https://youtube.com.example.org/", "mailto:a@b.c",
        }) {
            assertEquals(url, Route.EXTERNAL, WebPolicy.route(url));
        }
    }

    @Test public void appAndStoreLinksAreIgnoredOnAUnitWithoutGooglePlay() {
        for (String url : new String[] {
                "intent://www.youtube.com/watch?v=abc#Intent;package=com.google.android.youtube;scheme=https;end",
                "vnd.youtube:abc", "market://details?id=com.google.android.youtube",
                "https://play.google.com/store/apps/details?id=com.google.android.youtube", "javascript:alert(1)",
                "file:///sdcard/a.html", "not a url",
        }) {
            assertEquals(url, Route.IGNORED, WebPolicy.route(url));
        }
    }

    @Test public void userAgentDropsOnlyTheEmbeddedViewMarkers() {
        String system = "Mozilla/5.0 (Linux; Android 11; H97 Build/RQ3A.211001.001; wv) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Version/4.0 Chrome/83.0.4103.106 Mobile Safari/537.36";
        assertEquals("Mozilla/5.0 (Linux; Android 11; H97 Build/RQ3A.211001.001) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/83.0.4103.106 Mobile Safari/537.36", WebPolicy.userAgent(system));
        String browser = "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 "
                + "Mobile Safari/537.36";
        assertEquals(browser, WebPolicy.userAgent(browser));
    }

    @Test public void recreationReopensOnlyAShortYouTubeAddress() {
        assertEquals("https://m.youtube.com/watch?v=abc", WebPolicy.resumeUrl("https://m.youtube.com/watch?v=abc"));
        char[] query = new char[3000];
        Arrays.fill(query, 'a');
        for (String url : new String[] {
                null, "https://example.com/", "about:blank", "data:text/html;base64,AAAA",
                "https://m.youtube.com/results?q=" + new String(query),
        }) {
            assertEquals(url, WebPolicy.START_URL, WebPolicy.resumeUrl(url));
        }
    }

    @Test public void mediaTitleDropsTheSiteNameAndTheUnreadCount() {
        assertEquals("Song", WebPolicy.mediaTitle("Song - YouTube"));
        assertEquals("Song - Live", WebPolicy.mediaTitle("(3) Song - Live - YouTube"));
        assertEquals("Song", WebPolicy.mediaTitle("(99+) Song - YouTube"));
        assertEquals("YouTube", WebPolicy.mediaTitle("YouTube"));
        assertEquals("", WebPolicy.mediaTitle(""));
    }

    @Test public void pageScaleFollowsTheWindowDensityOverTheDisplayDensity() {
        assertEquals(1.5f, WebPolicy.pageScale(240, 160), .001f);
        assertEquals(.75f, WebPolicy.pageScale(120, 160), .001f);
        assertEquals(1f, WebPolicy.pageScale(160, 160), 0f);
        assertEquals(1f, WebPolicy.pageScale(0, 160), 0f);
        assertEquals(1f, WebPolicy.pageScale(240, 0), 0f);
        assertEquals(2.5f, WebPolicy.pageScale(640, 160), .001f);
    }

    @Test public void backClosesFullScreenThenWalksHistoryThenLeaves() {
        assertEquals(WebPolicy.Back.EXIT_FULLSCREEN, WebPolicy.back(true, true));
        assertEquals(WebPolicy.Back.PAGE_BACK, WebPolicy.back(false, true));
        assertEquals(WebPolicy.Back.LEAVE, WebPolicy.back(false, false));
    }
}
