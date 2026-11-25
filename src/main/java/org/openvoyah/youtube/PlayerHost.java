package org.openvoyah.youtube;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.webkit.JavascriptInterface;

import org.json.JSONObject;

import java.util.function.Consumer;

/**
 * Publishes the page's player as a media session, so the head unit's media keys, the media card in Controls and the
 * cluster see what plays and can pause it.
 *
 * <p>Audio focus is not handled here: the WebView's own media session requests it for every playing video and pauses,
 * ducks or resumes the page when another source takes over. A second request from the app would take focus from the
 * WebView and stop the video it was meant to protect.
 *
 * <p>The class is public because the WebView calls {@link #state} through its JavaScript bridge.
 */
public final class PlayerHost {
    /** Lets the window keep the screen on during playback. */
    interface PlayingListener {
        void playingChanged(boolean playing);
    }

    private static final int MAX_STATE_LENGTH = 2048;
    private static final int MAX_TEXT = 200;
    private static final long ACTIONS = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
            | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SKIP_TO_NEXT
            | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Consumer<String> script;
    private final PlayingListener listener;
    private final MediaSession session;
    private boolean playing;
    private String metadataKey = "";

    /** {@code script} runs JavaScript in the page. */
    PlayerHost(Activity activity, Consumer<String> script, PlayingListener listener) {
        this.script = script;
        this.listener = listener;
        session = new MediaSession(activity, "OpenVoyahYouTube");
        session.setSessionActivity(PendingIntent.getActivity(activity, 0,
                new Intent(activity, YouTubeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE));
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() {
                player("play()");
            }

            @Override public void onPause() {
                player("pause()");
            }

            @Override public void onStop() {
                player("pause()");
            }

            @Override public void onSkipToNext() {
                player("next()");
            }

            @Override public void onSkipToPrevious() {
                player("previous()");
            }

            @Override public void onSeekTo(long pos) {
                player("seek(" + Math.max(pos, 0L) + ")");
            }
        }, main);
    }

    /** Called by the injected player bridge on the WebView's binder thread. */
    @JavascriptInterface
    public void state(String json) {
        if (json.length() > MAX_STATE_LENGTH) return;
        main.post(() -> update(json));
    }

    /**
     * The window stopped being visible: nobody can watch the video, so it pauses, as on a phone without Premium. Sent
     * even when no playback was reported, in case the page started a video before the bridge was in place.
     */
    void hidden() {
        player("pause()");
    }

    void release() {
        session.setActive(false);
        session.release();
    }

    private void update(String json) {
        JSONObject state;
        try {
            state = new JSONObject(json);
        } catch (Exception malformed) {
            return;
        }
        boolean nowPlaying = state.optBoolean("playing");
        if (nowPlaying != playing) {
            playing = nowPlaying;
            listener.playingChanged(nowPlaying);
        }
        publish(Text.take(WebPolicy.mediaTitle(state.optString("title")), MAX_TEXT),
                Text.take(state.optString("artist"), MAX_TEXT),
                Math.max(state.optLong("position"), 0L), Math.max(state.optLong("duration"), 0L));
    }

    private void publish(String title, String artist, long position, long duration) {
        String key = title + '\u0000' + artist + '\u0000' + duration;
        if (!key.equals(metadataKey)) {
            metadataKey = key;
            session.setMetadata(new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
                    .build());
        }
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(ACTIONS)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, position,
                        playing ? 1f : 0f, SystemClock.elapsedRealtime())
                .build());
        if (playing && !session.isActive()) session.setActive(true);
    }

    private void player(String call) {
        script.accept("window.__ovcYouTube && window.__ovcYouTube." + call);
    }
}
