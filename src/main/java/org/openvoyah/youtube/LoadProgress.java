package org.openvoyah.youtube;

import android.animation.TimeInterpolator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * A thin accent line along the top of the window that follows a page load, so a slow page visibly moves. It eases
 * toward each new value, runs to the full width when the load ends and fades out; the next load starts it again
 * from the left. It only scales and fades, which the render thread does without drawing the line again, and it
 * never takes touches.
 */
@SuppressLint("ViewConstructor") // Created in code only.
final class LoadProgress extends View {
    private static final long MOVE_MS = 240;
    private static final long FADE_MS = 220;
    /** Where a new load starts, so the line shows at once before the WebView reports any progress. */
    private static final float FIRST = .08f;
    private static final TimeInterpolator DECELERATE = new DecelerateInterpolator();

    private boolean loading;
    /** The share the line is moving to, so a late or repeated report never moves it back. */
    private float target;

    LoadProgress(Context context) {
        super(context);
        setBackgroundColor(context.getColor(R.color.youtube_accent));
        setPivotX(0f);
        setScaleX(0f);
        setAlpha(0f);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** A main-frame load began; a second start of the same load just after the first changes nothing. */
    void started() {
        if (loading && target <= FIRST) return;
        animate().cancel();
        loading = true;
        target = 0f;
        setScaleX(0f);
        setAlpha(1f);
        moveTo(FIRST);
    }

    /** The WebView reports {@code percent} of the current load done. */
    void progress(int percent) {
        if (percent >= 100) {
            finished();
            return;
        }
        // Progress after the load ended belongs to the WebView's own pages under a message, not to the page.
        if (!loading) return;
        moveTo(Math.max(FIRST, percent / 100f));
    }

    /** The load ended: the line runs to the end and fades out. */
    void finished() {
        if (!loading) return;
        loading = false;
        target = 1f;
        animate().cancel();
        animate().scaleX(1f).alpha(1f).setDuration(MOVE_MS).setInterpolator(DECELERATE)
                .withEndAction(() -> animate().alpha(0f).setDuration(FADE_MS).setInterpolator(DECELERATE));
    }

    /** Hides the line at once, for a page that went away. */
    void clear() {
        animate().cancel();
        loading = false;
        target = 0f;
        setAlpha(0f);
        setScaleX(0f);
    }

    private void moveTo(float share) {
        if (share <= target) return;
        target = share;
        animate().cancel();
        animate().scaleX(share).alpha(1f).setDuration(MOVE_MS).setInterpolator(DECELERATE);
    }
}
