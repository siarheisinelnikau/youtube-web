package org.openvoyah.youtube;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Covers the page while it loads or after it failed, so the window is never a silent black rectangle: a spinner while
 * loading, then the reason in words, the WebView version and the action that can help.
 */
final class StatusView extends LinearLayout {
    private final ProgressBar progress;
    private final TextView message;
    private final TextView detail;
    private final Button retryButton;
    private final Button anywayButton;

    StatusView(Context context, Runnable retry, Runnable openAnyway) {
        super(context);
        progress = new ProgressBar(context);
        progress.setIndeterminate(true);
        message = new TextView(context);
        message.setTextColor(Color.WHITE);
        message.setTextSize(22f);
        message.setGravity(Gravity.CENTER);
        detail = new TextView(context);
        detail.setTextColor(Color.rgb(0xB0, 0xB0, 0xB0));
        detail.setTextSize(15f);
        detail.setGravity(Gravity.CENTER);
        retryButton = new Button(context);
        retryButton.setText(R.string.retry);
        retryButton.setTextSize(20f);
        retryButton.setOnClickListener(view -> retry.run());
        anywayButton = new Button(context);
        anywayButton.setText(R.string.open_anyway);
        anywayButton.setTextSize(20f);
        anywayButton.setOnClickListener(view -> openAnyway.run());

        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setBackgroundColor(Color.BLACK);
        setClickable(true);
        int gap = (int) (16 * getResources().getDisplayMetrics().density);
        setPadding(gap * 3, gap, gap * 3, gap);
        LayoutParams progressParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        progressParams.bottomMargin = gap;
        addView(progress, progressParams);
        addView(message, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        LayoutParams detailParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = gap / 2;
        addView(detail, detailParams);
        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        buttons.addView(anywayButton);
        buttons.addView(retryButton);
        LayoutParams buttonsParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        buttonsParams.topMargin = gap;
        addView(buttons, buttonsParams);
    }

    /** Shows {@code status}; {@code webView} is the provider version, or null when there is none. */
    void show(PageStatus status, String webView) {
        String version = webView != null ? getContext().getString(R.string.webview_version, webView)
                : getContext().getString(R.string.webview_none);
        if (status == PageStatus.SHOWN) {
            setVisibility(GONE);
        } else if (status == PageStatus.LOADING) {
            setVisibility(VISIBLE);
            progress.setVisibility(VISIBLE);
            message.setText(R.string.loading);
            detail.setText(version);
            retryButton.setVisibility(GONE);
            anywayButton.setVisibility(GONE);
        } else if (status instanceof PageStatus.Failed) {
            PageStatus.Failed failure = (PageStatus.Failed) status;
            boolean old = failure.problem == Problem.OLD_WEBVIEW;
            setVisibility(VISIBLE);
            progress.setVisibility(GONE);
            message.setText(text(failure.problem, webView));
            detail.setText(failure.detail.isEmpty() ? version : failure.detail + " · " + version);
            anywayButton.setVisibility(old ? VISIBLE : GONE);
            retryButton.setVisibility(old ? GONE : VISIBLE);
        }
    }

    /** The load is taking long; says so without giving up on it. */
    void slow() {
        if (getVisibility() == VISIBLE && progress.getVisibility() == VISIBLE) message.setText(R.string.still_loading);
    }

    private String text(Problem problem, String webView) {
        Context context = getContext();
        switch (problem) {
            case NO_WEBVIEW: return context.getString(R.string.problem_no_webview);
            case OLD_WEBVIEW: return context.getString(R.string.problem_old_webview, webView != null ? webView : "?",
                    WebViewSupport.MIN_MAJOR);
            case NO_NETWORK: return context.getString(R.string.problem_no_network);
            case UNREACHABLE: return context.getString(R.string.problem_unreachable);
            case HTTP: return context.getString(R.string.problem_http);
            case CERTIFICATE: return context.getString(R.string.problem_certificate);
            case RENDERER_CRASHED: return context.getString(R.string.problem_renderer_crashed);
            case RENDERER_KILLED: return context.getString(R.string.problem_renderer_killed);
            case TIMEOUT: return context.getString(R.string.problem_timeout, (int) (PageLoad.TIMEOUT_MS / 1000));
            case BLANK: return context.getString(R.string.problem_blank);
            default: throw new IllegalArgumentException(problem.name());
        }
    }
}
