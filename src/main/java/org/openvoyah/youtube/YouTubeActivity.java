package org.openvoyah.youtube;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * YouTube's touch site in the system WebView, as its own launcher app so Controls lists it and the window service
 * applies its width and DPI profile like any other app's. Until the page shows something, and whenever it cannot,
 * {@link StatusView} covers it with a spinner or the reason, so the window is never silently black.
 */
public final class YouTubeActivity extends Activity {
    private static final String TAG = "OvcYouTube";
    private static final String STATE_URL = "url";

    private FrameLayout root;
    private ScaledFrame page;
    private StatusView status;
    private PlayerHost player;
    private WebView web;
    private final PageLoad load = new PageLoad();
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean webViewVersionRead;
    private String webViewVersion;
    private String bridge;
    private View fullscreen;
    private WebChromeClient.CustomViewCallback fullscreenDone;
    /** The address the current main-frame load is for, so Retry repeats it. */
    private String target = WebPolicy.START_URL;
    /** The first script error of the current load, shown when the page stays blank. */
    private String scriptError = "";
    private PageStatus logged;
    private final Runnable timeout = () -> {
        load.timedOut();
        render();
    };
    private final Runnable slow = () -> status.slow();
    private final Runnable blankCheck = this::checkBlank;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setFitsSystemWindows(true);
        player = new PlayerHost(this, this::script, playing -> {
            if (playing) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
        page = new ScaledFrame(this);
        page.setScale(pageScale());
        status = new StatusView(this, this::retry, this::openAnyway);
        root.addView(page, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        root.addView(status, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        setContentView(root);
        target = WebPolicy.resumeUrl(savedInstanceState != null ? savedInstanceState.getString(STATE_URL) : null);
        String version = webViewVersion();
        Log.i(TAG, "start webview=" + (version != null ? version : "none") + " scale=" + page.getScale());
        Problem problem = WebViewSupport.problem(version);
        if (problem == null) open(target);
        else fail(problem);
    }

    @Override protected void onStart() {
        super.onStart();
        if (web != null) web.onResume();
    }

    @Override protected void onStop() {
        player.hidden();
        if (web != null) web.onPause();
        CookieManager.getInstance().flush();
        super.onStop();
    }

    /**
     * Only the page address survives a recreation, after a DPI change for example. The WebView's own saved state holds
     * the whole history with page data and can exceed the binder limit, which makes Android kill the app when it stops.
     */
    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        String url = web != null ? web.getUrl() : null;
        outState.putString(STATE_URL, WebPolicy.resumeUrl(url != null ? url : target));
    }

    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        player.release();
        if (web != null) {
            page.removeView(web);
            web.destroy();
        }
        web = null;
        super.onDestroy();
    }

    /** Android 13 and newer also deliver Back here while the app does not opt in to predictive back. */
    @Deprecated
    @Override public void onBackPressed() {
        WebView view = web;
        switch (WebPolicy.back(fullscreen != null, view != null && view.canGoBack() && load.status() == PageStatus.SHOWN)) {
            case EXIT_FULLSCREEN:
                if (fullscreenDone != null) fullscreenDone.onCustomViewHidden();
                else leaveFullscreen();
                break;
            case PAGE_BACK:
                if (view != null) view.goBack();
                break;
            case LEAVE:
                // Keep the page and its history for the next launch from the grid; leaving pauses the video.
                moveTaskToBack(true);
                break;
        }
    }

    /** Loads {@code url}, creating the WebView first; a WebView that cannot be created is reported, not thrown. */
    private void open(String url) {
        target = url;
        WebView view = web;
        if (view == null) {
            try {
                view = newWebView();
                web = view;
                page.addView(view, 0, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
            } catch (RuntimeException error) {
                fail(Problem.NO_WEBVIEW, error.getClass().getSimpleName());
                return;
            }
        }
        started();
        view.loadUrl(url);
    }

    private void retry() {
        if (web == null && WebViewSupport.problem(webViewVersion()) == Problem.NO_WEBVIEW) recreate();
        else open(WebPolicy.resumeUrl(target));
    }

    private void openAnyway() {
        open(target);
    }

    private void started() {
        scriptError = "";
        load.started();
        main.removeCallbacks(timeout);
        main.removeCallbacks(slow);
        main.removeCallbacks(blankCheck);
        main.postDelayed(slow, PageLoad.SLOW_MS);
        main.postDelayed(timeout, PageLoad.TIMEOUT_MS);
        render();
    }

    private void visible() {
        boolean wasShown = load.status() == PageStatus.SHOWN;
        load.visible();
        if (load.status() == PageStatus.SHOWN && !wasShown) {
            main.removeCallbacks(blankCheck);
            main.postDelayed(blankCheck, PageLoad.BLANK_CHECK_MS);
        }
        render();
    }

    private void fail(Problem problem) {
        fail(problem, "");
    }

    private void fail(Problem problem, String detail) {
        load.failed(problem, detail);
        render();
    }

    private void checkBlank() {
        WebView view = web;
        if (view == null) return;
        if (load.status() != PageStatus.SHOWN || fullscreen != null) return;
        view.evaluateJavascript(PageLoad.BLANK_PROBE, result -> {
            if (load.status() == PageStatus.SHOWN && PageLoad.drewNothing(result)) {
                load.failed(Problem.BLANK, scriptError);
                render();
            }
        });
    }

    private void render() {
        PageStatus current = load.status();
        if (current != PageStatus.LOADING) {
            main.removeCallbacks(timeout);
            main.removeCallbacks(slow);
        }
        if (current instanceof PageStatus.Failed && !current.equals(logged)) {
            Log.w(TAG, PageLoad.logLine((PageStatus.Failed) current, webViewVersion()));
        }
        logged = current;
        status.show(current, webViewVersion());
    }

    private boolean online() {
        ConnectivityManager connectivity = getSystemService(ConnectivityManager.class);
        if (connectivity == null) return true;
        Network network = connectivity.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = connectivity.getNetworkCapabilities(network);
        return capabilities == null || capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    /** The DPI profile's density relative to the display's, which the WebView would otherwise ignore. */
    @SuppressWarnings("deprecation")
    private float pageScale() {
        DisplayMetrics display = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(display);
        return WebPolicy.pageScale(getResources().getConfiguration().densityDpi, display.densityDpi);
    }

    private String webViewVersion() {
        if (!webViewVersionRead) {
            webViewVersionRead = true;
            try {
                PackageInfo provider = WebView.getCurrentWebViewPackage();
                webViewVersion = provider != null ? provider.versionName : null;
            } catch (RuntimeException unavailable) {
                webViewVersion = null;
            }
        }
        return webViewVersion;
    }

    /** The player bridge script, read from the assets on first use. */
    private String bridge() {
        if (bridge == null) {
            try (InputStream input = getAssets().open("player-bridge.js")) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                for (int read; (read = input.read(buffer)) > 0; ) bytes.write(buffer, 0, read);
                bridge = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
        }
        return bridge;
    }

    private void script(String code) {
        if (!isDestroyed() && web != null) web.evaluateJavascript(code, null);
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private WebView newWebView() {
        WebView view = new WebView(this);
        view.setBackgroundColor(Color.BLACK);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setBuiltInZoomControls(false);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(WebPolicy.userAgent(settings.getUserAgentString()));
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(view, true);
        view.addJavascriptInterface(player, "OvcYouTubeHost");
        view.setWebViewClient(new Client());
        view.setWebChromeClient(new Chrome());
        return view;
    }

    private void openOutside(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));
        } catch (ActivityNotFoundException noBrowser) {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show();
        }
    }

    private void leaveFullscreen() {
        View view = fullscreen;
        if (view == null) return;
        page.removeView(view);
        fullscreen = null;
        fullscreenDone = null;
        if (web != null) web.setVisibility(View.VISIBLE);
        systemBars(true);
    }

    @SuppressWarnings("deprecation")
    private void systemBars(boolean show) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller == null) return;
            if (show) {
                controller.show(WindowInsets.Type.systemBars());
            } else {
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.systemBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(show ? View.SYSTEM_UI_FLAG_VISIBLE
                    : View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private final class Client extends WebViewClient {
        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            switch (WebPolicy.route(url)) {
                case IN_APP:
                    return false;
                case IGNORED:
                    return true;
                case EXTERNAL:
                default:
                    if (!request.isForMainFrame()) return false;
                    openOutside(url);
                    return true;
            }
        }

        @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (url != null && WebPolicy.route(url) == WebPolicy.Route.IN_APP && !url.startsWith("data:")) target = url;
            started();
        }

        @Override public void onPageCommitVisible(WebView view, String url) {
            visible();
        }

        @Override public void onPageFinished(WebView view, String url) {
            view.evaluateJavascript(bridge(), null);
            visible();
        }

        @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (!request.isForMainFrame()) return;
            fail(online() ? Problem.UNREACHABLE : Problem.NO_NETWORK,
                    error.getDescription() + " (" + error.getErrorCode() + ")");
        }

        @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
            if (request.isForMainFrame() && response.getStatusCode() >= 400) {
                String reason = response.getReasonPhrase();
                fail(Problem.HTTP, "HTTP " + response.getStatusCode() + " " + (reason != null ? reason : ""));
            }
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel();
            if (load.status() == PageStatus.LOADING) fail(Problem.CERTIFICATE, "SSL error " + error.getPrimaryError());
        }

        /** The renderer died: report it and keep a fresh WebView ready, so Retry does not start from a dead one. */
        @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            String url = view.getUrl();
            target = WebPolicy.resumeUrl(url != null ? url : target);
            leaveFullscreen();
            page.removeView(view);
            view.destroy();
            if (web == view) web = null;
            load.rendererGone(detail.didCrash());
            render();
            return true;
        }
    }

    private final class Chrome extends WebChromeClient {
        @Override public void onShowCustomView(View view, CustomViewCallback callback) {
            if (fullscreen != null) {
                callback.onCustomViewHidden();
                return;
            }
            fullscreen = view;
            fullscreenDone = callback;
            page.addView(view, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
            if (web != null) web.setVisibility(View.INVISIBLE);
            systemBars(false);
        }

        @Override public void onHideCustomView() {
            leaveFullscreen();
        }

        @Override public boolean onConsoleMessage(ConsoleMessage message) {
            if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR && scriptError.isEmpty()) {
                scriptError = PageLoad.bounded(message.message());
            }
            return false;
        }

        /** A black frame instead of the grey play arrow the WebView draws before a video's first frame. */
        @Override public Bitmap getDefaultVideoPoster() {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        }
    }
}
