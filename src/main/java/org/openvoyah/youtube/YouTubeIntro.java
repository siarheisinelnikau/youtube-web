package org.openvoyah.youtube;

import static org.openvoyah.youtube.IntroPacing.Easing;
import static org.openvoyah.youtube.IntroPacing.clamp;
import static org.openvoyah.youtube.IntroPacing.ease;
import static org.openvoyah.youtube.IntroPacing.span;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.provider.Settings;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import java.util.function.Consumer;

/**
 * The startup intro of YouTube Web, in the language of the Open Voyah Controls intro: a steel mark on the dark
 * backdrop, quiet corner lights, a glint across the metal, an accent charge, a pulse and a still hold before the
 * mark leaves toward the viewer.
 *
 * <p>The mark is a screen with a play button. Video tiles slide out from behind it on both sides like the wings of
 * the Open Voyah Controls emblem, the button pops up and a progress bar with chapters fills under the name, its
 * buffer running ahead. When the bar is full the button is pressed: the screen comes on in the accent like a picture
 * tube, from a bright line across the middle, the steel charges from the screen outward and two rings leave the
 * screen. The mark then holds as the app's icon in steel and light.
 *
 * <p>The intro plays each time YouTube comes back on screen from another app or the home screen, and the page itself
 * never goes away. On a return to a phone or tablet, where Android shows the page as it was left, the backdrop
 * spreads over the page from the screen like a wave while the page steps back. After a cold start, and on car head
 * units, which show the plain window background on the way back instead, the backdrop covers the window from the
 * first frame, matching that background, while the page lays itself out underneath. Every intro ends with the mark
 * flying past the viewer as the iris opens from the screen and the page steps forward into place. A tap runs that
 * ending at once.
 */
@SuppressLint("ViewConstructor") // Created in code with the activity's run settings.
final class YouTubeIntro extends View {
    /** The stages in milliseconds of intro time. */
    static final class Timeline {
        private Timeline() {}

        /** On a return the backdrop has spread over the whole page by this time. */
        static final float VEIL_END = 240;
        static final float BODY_START = 120;
        static final float BODY_VISIBLE = 420;
        static final float BUTTON_START = 240;
        static final float BUTTON_FULL = 520;
        static final float TILES_START = 280;
        static final float TILE_STAGGER = 50;
        static final float TILE_OPEN = 360;
        static final float TILE_VISIBLE = 160;
        static final float TITLE_START = 340;
        static final float TITLE_VISIBLE = 600;
        static final float BAR_START = 420;
        static final float BUFFER_DRAWN = 700;
        static final float PLAYED_START = 450;
        static final float BAR_DRAWN = 780;
        static final float SCRUBBER_GONE = 980;
        static final float CAPTION_START = 500;
        static final float CAPTION_VISIBLE = 760;
        static final float GLINT_START = 380;
        static final float GLINT_END = 1080;
        /** The play button goes down as the bar fills and comes back up while the screen comes on. */
        static final float PRESS = 760;
        static final float PRESS_LENGTH = 240;
        /** The screen comes on as a line that widens, then opens to the full picture. */
        static final float POWER_START = 820;
        static final float POWER_WIDE = 880;
        static final float POWER_FULL = 960;
        static final float CHARGE_START = 820;
        static final float CHARGE_FULL = 930;
        static final float CHARGE_FADE = 1010;
        static final float CHARGE_END = 1190;
        /** Each tile charges this much later than the one inside it. */
        static final float CHARGE_STAGGER = 30;
        static final float PULSE_START = 830;
        static final float PULSE_SECOND = 940;
        static final float PULSE_LENGTH = 420;
        /** The mark flies past the viewer and fades between these times. */
        static final float EXIT_START = 1370;
        static final float EXIT_END = 1690;
        /** The iris opens from the screen onto the page between this time and the end. */
        static final float OPEN_START = 1430;
        static final float END = 1770;
        /** A tap runs the ending over this time. */
        static final float DISMISS = 340;
        /** A point at which nearly every part is drawn, used to prepare the drawing before the intro shows. */
        static final float WARM = 970;
    }

    /** The extra that holds a debuggable build's intro at one point of its timeline, for screenshots. */
    static final String DEBUG_PHASE = "intro_millis";

    private static final int STEADY_FRAMES = 6;
    private static final long SETTLE_LIMIT_NANOS = 450_000_000L;
    /** Beyond the slowest pace the clock allows, the intro removes itself, so it can never stay over the app. */
    private static final float WATCHDOG_SLACK_MS = 1_500f;
    private static final float CHARGE_DEPTH = .82f;
    /** How far the page steps back behind the backdrop, as a share of its size. */
    private static final float CONTENT_DEPTH = .08f;
    /** How much the mark grows as it flies past the viewer. */
    private static final float FLIGHT = .32f;
    /** The share of the spreading backdrop and of the iris that is solid; the rest is a soft edge. */
    private static final float IRIS_CLEAR = .68f;
    /** How bright the screen stays once it is on; the charge lights it fully for a moment. */
    private static final float SCREEN_ON = .72f;
    private static final Object TAG = new Object();
    private static final IntroPacing.Arrivals ARRIVALS = new IntroPacing.Arrivals();
    /**
     * The last intro ended because the window left the screen. Android keeps the last frame of a leaving window
     * as the picture it shows on the way back, so that return starts from the backdrop rather than the page.
     */
    private static boolean interrupted;
    /** The corner lights of the Open Voyah Controls family; the accent is kept for the moment the screen comes on. */
    private static final int COOL_LIGHT = 0xFFB5CFFF;
    private static final int WARM_LIGHT = 0xFFE1C992;

    // The mark in its own units: 254 wide and 84 tall, the screen in the middle.
    private static final float WIDTH = 254f;
    private static final float HEIGHT = 84f;
    private static final float CX = 127f;
    private static final float CY = 42f;
    private static final float SCREEN_HALF_WIDTH = 50f;
    private static final float SCREEN_HALF_HEIGHT = 31f;
    private static final float SCREEN_RADIUS = 12f;
    private static final float RIM = 2.6f;
    // The play button around its centroid at the middle of the screen.
    private static final float BUTTON_BACK = -8.5f;
    private static final float BUTTON_TIP = 17f;
    private static final float BUTTON_HALF_HEIGHT = 14f;
    private static final float BUTTON_ROUND = 3.2f;
    private static final float[] TILE_HALF_WIDTHS = {13f, 10f, 7.5f};
    private static final float[] TILE_HALF_HEIGHTS = {8.5f, 6.5f, 5f};
    private static final float[] TILE_RADII = {3.2f, 2.6f, 2.1f};
    private static final float[] TILE_OFFSETS = {69f, 97f, 118.5f};
    private static final float[] TILE_OPACITY = {.92f, .76f, .6f};
    /** Where the chapters of the progress bar end, as shares of its length. */
    private static final float[] CHAPTERS = {.34f, .58f, .79f, 1f};
    /** Room around each raster for antialiased edges and the button's shadow, in mark units. */
    private static final float LAYER_MARGIN = 1.5f;
    /** Rasters are drawn finer than the mark, which grows as it flies past the viewer. */
    private static final float LAYER_DETAIL = 1.25f;

    private final float durationScale;
    private final float heldMillis;
    /** The page was on screen when the intro began, so the backdrop spreads over it first. */
    private final boolean veiled;
    private final View content;
    private final float density;
    private final String title;
    private final String caption;

    private final int backdrop;
    private final int accent;
    private final int text;
    private final int muted;
    private final int shade;
    private final int body;
    private final int light;
    private final int glowCore;
    private final int glowEdge;
    private final ColorFilter charged;
    /** Turns the steel of the play button into plain light while the screen comes on behind it. */
    private final ColorFilter lit;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint plain = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint captionPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Matrix matrix = new Matrix();
    private final Matrix irisMatrix = new Matrix();
    private final Path button;
    private final Path glass = new Path();
    private final Shader rimShader;
    private final Shader glassShader;
    private final Shader reflectionShader;
    private final Shader buttonShader;
    private final Shader tileShader;
    private final Shader screenLight;
    private final Shader glintShader;
    /** The backdrop around a clear circle of radius 1 at the origin, placed and sized for each frame. */
    private final Shader iris;
    /** A disc of backdrop of radius 1 at the origin with a soft edge, placed and sized for each frame. */
    private final Shader wave;

    private Bitmap rimLayer;
    private Bitmap glassLayer;
    private Bitmap buttonLayer;
    private final Bitmap[] tileLayers = new Bitmap[TILE_OFFSETS.length];
    private Shader coolLight;
    private Shader warmShade;
    private Shader ambient;
    private Shader chargeGlow;
    private Shader shadow;
    private Shader scrubberGlow;

    private float markHeight;
    private float markScale;
    private float centerX;
    private float top;
    private float titleTop;
    private float barY;
    private float captionTop;
    private float barWidth;
    private float screenY;
    private float glowRadius;
    private float irisReach;
    private float titleAscent;
    private float captionAscent;
    private String shownTitle = "";
    private String shownCaption = "";
    private float titleWidth;
    private float captionWidth;

    private final Choreographer.FrameCallback tick = this::onFrame;
    private IntroPacing.Settle settle;
    private IntroPacing.Clock clock;
    private long frameNanos = 16_666_667L;
    private long firstFrame = -1L;
    private long dismissedAt = -1L;
    private boolean dismissRequested;
    private boolean scheduled;
    private boolean finished;
    private boolean shown;
    private float introMillis;
    /** How far a tap has run the ending, 0..1. */
    private float dismissed;
    private final Runnable watchdog = this::finish;

    /**
     * Called from the activity's {@code onStart}. Plays the intro when YouTube comes back on screen from another app
     * or the home screen, the intro is enabled and system animations are on.
     */
    static void onActivityStart(Activity activity, boolean enabled) {
        boolean arrived = ARRIVALS.onStart();
        float held = heldMillis(activity);
        if (held < 0f && (!arrived || !enabled)) return;
        float scale = Settings.Global.getFloat(activity.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        if (held < 0f && (!ValueAnimator.areAnimatorsEnabled() || scale <= 0f)) return;
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        View previous = decor.findViewWithTag(TAG);
        if (previous instanceof YouTubeIntro) ((YouTubeIntro) previous).finish();
        // A window that is already attached shows the page as it was left, from a snapshot or on its own; a new
        // window starts from the plain window background, which the backdrop matches. Car head units take no task
        // snapshots and show that background on every return as well.
        boolean veiled = held < 0f && decor.isAttachedToWindow() && !interrupted
                && !activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
        interrupted = false;
        decor.addView(new YouTubeIntro(activity, Math.max(scale, .01f), held, veiled),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Called from the activity's {@code onStop}. */
    static void onActivityStop(Activity activity) {
        ARRIVALS.onStop(activity.isChangingConfigurations());
    }

    /** Called when the activity opens another screen itself, so that coming back from it skips the intro. */
    static void onOwnScreenOpened() {
        ARRIVALS.handOff();
    }

    private static float heldMillis(Activity activity) {
        boolean debuggable = (activity.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        Intent intent = activity.getIntent();
        return debuggable && intent != null ? intent.getFloatExtra(DEBUG_PHASE, -1f) : -1f;
    }

    private YouTubeIntro(Activity activity, float durationScale, float heldMillis, boolean veiled) {
        super(activity);
        this.durationScale = durationScale;
        this.heldMillis = heldMillis;
        this.veiled = veiled;
        content = activity.findViewById(android.R.id.content);
        setTag(TAG);
        density = getResources().getDisplayMetrics().density;
        title = activity.getString(R.string.app_name);
        caption = activity.getString(R.string.intro_caption);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Above the bar backgrounds that the window adds later, without a shadow.
        setOutlineProvider(null);
        setTranslationZ(1_000f);

        backdrop = activity.getColor(R.color.youtube_background);
        accent = activity.getColor(R.color.youtube_accent);
        text = activity.getColor(R.color.youtube_text);
        muted = activity.getColor(R.color.youtube_muted);
        int steel = mix(muted, activity.getColor(R.color.youtube_edge), .30f);
        shade = mix(mix(steel, backdrop, .78f), accent, .12f);
        body = mix(steel, backdrop, .32f);
        light = mix(steel, Color.WHITE, .86f);
        // The room stays neutral until the screen comes on and lights it in the accent.
        glowCore = mix(backdrop, steel, .24f);
        glowEdge = alpha(mix(backdrop, steel, .11f), .64f);
        // Multiplying by the accent keeps the bevels and shadows of the steel while it takes the accent color.
        charged = new LightingColorFilter(accent & 0xFFFFFF, 0);
        lit = new PorterDuffColorFilter(mix(light, Color.WHITE, .7f), PorterDuff.Mode.SRC_ATOP);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        titlePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titlePaint.setTextSize(sp(24));
        titlePaint.setLetterSpacing(.067f);
        captionPaint.setTextSize(sp(14));
        captionPaint.setLetterSpacing(.043f);

        button = roundedTriangle(
                new float[] {CX + BUTTON_BACK, CX + BUTTON_TIP, CX + BUTTON_BACK},
                new float[] {CY - BUTTON_HALF_HEIGHT, CY, CY + BUTTON_HALF_HEIGHT}, BUTTON_ROUND);
        float inset = RIM / 2f;
        glass.addRoundRect(CX - SCREEN_HALF_WIDTH + inset, CY - SCREEN_HALF_HEIGHT + inset,
                CX + SCREEN_HALF_WIDTH - inset, CY + SCREEN_HALF_HEIGHT - inset,
                SCREEN_RADIUS - inset, SCREEN_RADIUS - inset, Path.Direction.CW);

        rimShader = new LinearGradient(CX - SCREEN_HALF_WIDTH, CY - SCREEN_HALF_HEIGHT,
                CX + SCREEN_HALF_WIDTH, CY + SCREEN_HALF_HEIGHT,
                new int[] {tone(1f), tone(.78f), tone(.30f), tone(.66f)}, new float[] {0f, .30f, .68f, 1f},
                Shader.TileMode.CLAMP);
        // The glass stays nearly black so the steel and the button carry the light until the screen comes on.
        glassShader = new RadialGradient(CX, CY - 6f, SCREEN_HALF_WIDTH * 1.15f,
                new int[] {mix(backdrop, body, .22f), mix(backdrop, body, .10f), mix(backdrop, Color.BLACK, .35f)},
                new float[] {0f, .45f, 1f}, Shader.TileMode.CLAMP);
        reflectionShader = new LinearGradient(CX - SCREEN_HALF_WIDTH, CY - SCREEN_HALF_HEIGHT, CX - 6f, CY + 14f,
                new int[] {alpha(light, .10f), alpha(light, .035f), alpha(light, 0f)}, new float[] {0f, .55f, 1f},
                Shader.TileMode.CLAMP);
        buttonShader = new LinearGradient(CX + BUTTON_BACK, CY - BUTTON_HALF_HEIGHT, CX + BUTTON_TIP,
                CY + BUTTON_HALF_HEIGHT, new int[] {tone(1f), tone(.82f), tone(.46f)}, new float[] {0f, .45f, 1f},
                Shader.TileMode.CLAMP);
        // Tiles are drawn from one gradient between -1 and 1, stretched to each tile's height.
        tileShader = new LinearGradient(0f, -1f, 0f, 1f,
                new int[] {tone(.95f, .85f), tone(.66f, .50f), tone(.40f, .34f), tone(.66f, .50f), tone(.92f, .78f)},
                new float[] {0f, .28f, .50f, .72f, 1f}, Shader.TileMode.CLAMP);
        screenLight = new RadialGradient(CX, CY, SCREEN_HALF_WIDTH * 1.1f,
                new int[] {mix(accent, Color.WHITE, .22f), accent, mix(accent, Color.BLACK, .42f)},
                new float[] {0f, .42f, 1f}, Shader.TileMode.CLAMP);
        glintShader = new LinearGradient(0f, 0f, 30f, -17f,
                new int[] {Color.TRANSPARENT, mix(Color.WHITE, accent, .10f), Color.TRANSPARENT}, null,
                Shader.TileMode.CLAMP);
        iris = new RadialGradient(0f, 0f, 1f, new int[] {alpha(backdrop, 0f), alpha(backdrop, 0f), backdrop},
                new float[] {0f, IRIS_CLEAR, 1f}, Shader.TileMode.CLAMP);
        wave = new RadialGradient(0f, 0f, 1f, new int[] {backdrop, backdrop, alpha(backdrop, 0f)},
                new float[] {0f, IRIS_CLEAR, 1f}, Shader.TileMode.CLAMP);
    }

    /** A triangle through the given corners with each corner rounded off {@code round} units from its point. */
    private static Path roundedTriangle(float[] xs, float[] ys, float round) {
        Path path = new Path();
        for (int i = 0; i < 3; i++) {
            float x = xs[i];
            float y = ys[i];
            float previousX = xs[(i + 2) % 3] - x;
            float previousY = ys[(i + 2) % 3] - y;
            float nextX = xs[(i + 1) % 3] - x;
            float nextY = ys[(i + 1) % 3] - y;
            float toPrevious = round / (float) Math.hypot(previousX, previousY);
            float toNext = round / (float) Math.hypot(nextX, nextY);
            if (i == 0) path.moveTo(x + previousX * toPrevious, y + previousY * toPrevious);
            else path.lineTo(x + previousX * toPrevious, y + previousY * toPrevious);
            path.quadTo(x, y, x + nextX * toNext, y + nextY * toNext);
        }
        path.close();
        return path;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Display display = getDisplay();
        float refresh = display == null ? 60f : display.getRefreshRate();
        frameNanos = (long) (1_000_000_000L / (refresh >= 20f ? refresh : 60f));
        settle = new IntroPacing.Settle(frameNanos, STEADY_FRAMES);
        if (heldMillis >= 0f) {
            introMillis = heldMillis;
        } else {
            schedule();
            float limit = SETTLE_LIMIT_NANOS / 1_000_000f + Timeline.END * 1.5f + Timeline.DISMISS;
            postDelayed(watchdog, (long) (limit * durationScale + WATCHDOG_SLACK_MS));
        }
    }

    @Override protected void onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(tick);
        scheduled = false;
        placeContent(0f);
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        // Leaving YouTube while the intro plays ends it; the next return plays it again.
        if (visibility == VISIBLE) {
            shown = true;
            if (heldMillis < 0f) schedule();
        } else if (shown && heldMillis < 0f && !finished) {
            interrupted = clock != null || !veiled;
            post(this::finish);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        // Until a returning intro draws, the page shows through it, and touches reach the page as well.
        if (veiled && clock == null && !dismissRequested && heldMillis < 0f) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) performClick();
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        if (heldMillis >= 0f) {
            post(this::finish);
        } else {
            dismissRequested = true;
            schedule();
        }
        return true;
    }

    private void schedule() {
        if (scheduled || finished) return;
        scheduled = true;
        Choreographer.getInstance().postFrameCallback(tick);
    }

    private void onFrame(long now) {
        scheduled = false;
        if (finished) return;
        // A returning window may still be hidden when the intro starts; frames count once it shows.
        if (getWindowVisibility() != VISIBLE) return;
        if (clock == null && !dismissRequested) {
            if (firstFrame < 0) firstFrame = now;
            // A returning window already shows the page, so its intro starts with the first visible frame.
            if (veiled || settle.onFrame(now) || now - firstFrame >= SETTLE_LIMIT_NANOS) {
                clock = new IntroPacing.Clock(frameNanos, (long) (Timeline.END * durationScale * 1_000_000L));
            }
        }
        if (clock != null) introMillis = clock.onFrame(now) / 1_000_000f / durationScale;
        if (dismissRequested) {
            if (dismissedAt < 0) dismissedAt = now;
            dismissed = clamp((now - dismissedAt) / (Timeline.DISMISS * durationScale * 1_000_000f));
        }
        placeContent(clock != null || dismissRequested ? contentDepth(introMillis) : veiled ? 0f : 1f);
        invalidate();
        if (introMillis >= Timeline.END || dismissed >= 1f) finish();
        else schedule();
    }

    private void finish() {
        if (finished) return;
        finished = true;
        Choreographer.getInstance().removeFrameCallback(tick);
        removeCallbacks(watchdog);
        placeContent(0f);
        ViewParent parent = getParent();
        if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(this);
    }

    /** How far the page stands back: it steps back while the backdrop spreads and forward while the iris opens. */
    private float contentDepth(float t) {
        float closed = veiled ? ease(t, 0f, Timeline.VEIL_END) : 1f;
        float opened = Easing.DECELERATE.at(Math.max(span(t, Timeline.OPEN_START, Timeline.END), dismissed));
        return Math.min(closed, 1f - opened);
    }

    /** Scales the page down by {@code depth} times {@link #CONTENT_DEPTH} around the screen of the mark. */
    private void placeContent(float depth) {
        if (content == null) return;
        if (depth <= 0f) {
            content.setScaleX(1f);
            content.setScaleY(1f);
            content.resetPivot();
            return;
        }
        // The intro covers the window, so the screen in the page's own coordinates is offset by its position.
        float x = centerX;
        float y = screenY;
        for (View view = content; view != null && view != getParent(); ) {
            x -= view.getLeft();
            y -= view.getTop();
            ViewParent parent = view.getParent();
            view = parent instanceof View ? (View) parent : null;
        }
        float scale = 1f - CONTENT_DEPTH * depth;
        content.setPivotX(x);
        content.setPivotY(y);
        content.setScaleX(scale);
        content.setScaleY(scale);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        float maxText = Math.max(0f, width - dp(32));
        shownTitle = TextUtils.ellipsize(title, titlePaint, maxText, TextUtils.TruncateAt.END).toString();
        shownCaption = TextUtils.ellipsize(caption, captionPaint, maxText, TextUtils.TruncateAt.END).toString();
        titleWidth = titlePaint.measureText(shownTitle);
        captionWidth = captionPaint.measureText(shownCaption);
        Paint.FontMetrics titleMetrics = titlePaint.getFontMetrics();
        Paint.FontMetrics captionMetrics = captionPaint.getFontMetrics();
        titleAscent = titleMetrics.ascent;
        captionAscent = captionMetrics.ascent;
        float titleHeight = titleMetrics.descent - titleMetrics.ascent;
        float captionHeight = captionMetrics.descent - captionMetrics.ascent;

        markHeight = Math.min(dp(150), Math.min(height * .40f, width * .16f));
        markScale = markHeight / HEIGHT;
        if (markScale > 0f) buildLayers(Math.min(markScale * LAYER_DETAIL, 6f));
        centerX = width / 2f;
        float block = markHeight + dp(18) + titleHeight + dp(14) + dp(3) + dp(12) + captionHeight;
        top = Math.max(0f, (height - block) / 2f);
        titleTop = top + markHeight + dp(18);
        barY = titleTop + titleHeight + dp(14);
        captionTop = barY + dp(3) + dp(12);
        barWidth = Math.min(Math.max(Math.max(titleWidth, captionWidth), dp(220)), maxText);
        screenY = top + CY * markScale;
        glowRadius = Math.max(1f, markHeight * 1.25f);
        irisReach = (float) Math.max(Math.hypot(centerX, screenY), Math.hypot(width - centerX, height - screenY));
        if (heldMillis >= 0f) post(() -> placeContent(contentDepth(heldMillis)));

        float shortEdge = Math.max(1f, Math.min(width, height));
        coolLight = new RadialGradient(width * .94f, height * .06f, shortEdge * 1.1f,
                new int[] {alpha(COOL_LIGHT, .08f), alpha(COOL_LIGHT, .022f), alpha(COOL_LIGHT, 0f)}, null,
                Shader.TileMode.CLAMP);
        warmShade = new RadialGradient(width * .03f, height * .98f, shortEdge * .85f,
                new int[] {alpha(WARM_LIGHT, .035f), alpha(WARM_LIGHT, 0f)}, null, Shader.TileMode.CLAMP);
        ambient = new RadialGradient(centerX, screenY, glowRadius,
                new int[] {glowCore, glowEdge, Color.TRANSPARENT}, null, Shader.TileMode.CLAMP);
        chargeGlow = new RadialGradient(centerX, screenY, glowRadius,
                new int[] {alpha(accent, .40f), alpha(accent, .12f), alpha(accent, 0f)}, new float[] {0f, .45f, 1f},
                Shader.TileMode.CLAMP);
        shadow = new RadialGradient(0f, 0f, 1f, new int[] {alpha(Color.BLACK, .65f), Color.TRANSPARENT}, null,
                Shader.TileMode.CLAMP);
        scrubberGlow = new RadialGradient(0f, 0f, dp(15),
                new int[] {alpha(accent, .45f), alpha(accent, 0f)}, null, Shader.TileMode.CLAMP);
    }

    @Override protected void onDraw(Canvas canvas) {
        if (clock == null && heldMillis < 0f && !dismissRequested) {
            // A returning page stays on screen as it was left until its window shows.
            if (veiled) return;
            // Drawn once per frame under the opaque backdrop while frames settle, so that the first visible
            // frames do not pay for shaders, paths and text.
            drawIntro(canvas, Timeline.WARM, 0f, 1f);
            drawBackdrop(canvas, 0f);
            return;
        }
        float t = introMillis;
        float closed = veiled ? ease(t, 0f, Timeline.VEIL_END) : 1f;
        float open = Easing.STANDARD.at(Math.max(span(t, Timeline.OPEN_START, Timeline.END), dismissed));
        if (closed < 1f) drawWave(canvas, closed * (1f - open));
        else drawBackdrop(canvas, open);
        drawIntro(canvas, t, Math.max(span(t, Timeline.EXIT_START, Timeline.EXIT_END), dismissed),
                closed * (1f - open));
    }

    /** The backdrop spreading from the screen over the page: {@code reach} 0 shows the page, 1 covers it all. */
    private void drawWave(Canvas canvas, float reach) {
        if (reach <= 0f) return;
        float radius = Math.max(1f, irisReach * reach / IRIS_CLEAR);
        irisMatrix.setScale(radius, radius);
        irisMatrix.postTranslate(centerX, screenY);
        wave.setLocalMatrix(irisMatrix);
        plain.setShader(wave);
        plain.setColor(Color.BLACK);
        canvas.drawRect(0, 0, getWidth(), getHeight(), plain);
        plain.setShader(null);
    }

    /** The backdrop around a soft round opening at the screen: {@code hole} 0 covers the page, 1 shows it all. */
    private void drawBackdrop(Canvas canvas, float hole) {
        if (hole >= 1f) return;
        plain.setShader(null);
        plain.setColor(backdrop);
        if (hole > 0f) {
            float radius = Math.max(1f, irisReach * hole / IRIS_CLEAR);
            irisMatrix.setScale(radius, radius);
            irisMatrix.postTranslate(centerX, screenY);
            iris.setLocalMatrix(irisMatrix);
            plain.setShader(iris);
        }
        canvas.drawRect(0, 0, getWidth(), getHeight(), plain);
        plain.setShader(null);
    }

    /**
     * The mark, the name and the corner lights at intro time {@code t}. {@code leave} runs the exit from 0 to 1
     * and {@code lights} dims the corner lights while the page shows around the backdrop.
     */
    private void drawIntro(Canvas canvas, float t, float leave, float lights) {
        if (t <= Timeline.BODY_START || markScale <= 0f) return;
        float out = 1f - Easing.STANDARD.at(leave);
        if (out <= 0f) return;
        float reveal = ease(t, Timeline.BODY_START, Timeline.BODY_VISIBLE);
        float charge = charge(t);
        float power = ease(t, Timeline.POWER_START, Timeline.POWER_FULL);
        float titleAlpha = ease(t, Timeline.TITLE_START, Timeline.TITLE_VISIBLE);
        float captionAlpha = ease(t, Timeline.CAPTION_START, Timeline.CAPTION_VISIBLE);
        float thump = .02f * bump(span(t, Timeline.POWER_START, Timeline.POWER_START + 200f));

        fill.setColorFilter(null);
        fill.setShader(coolLight);
        fill.setAlpha(alpha255(reveal * out * lights));
        canvas.drawRect(0, 0, getWidth(), getHeight(), fill);
        fill.setShader(warmShade);
        canvas.drawRect(0, 0, getWidth(), getHeight(), fill);

        canvas.save();
        // The mark leaves by flying past the viewer from the screen, faster as it goes, while it fades.
        float flight = 1f + FLIGHT * Easing.ACCELERATE.at(leave);
        canvas.scale(flight, flight, centerX, screenY);

        fill.setShader(ambient);
        fill.setAlpha(alpha255(reveal * out));
        canvas.drawCircle(centerX, screenY, glowRadius, fill);
        // The screen lights the room once it is on, brightest at the charge.
        float glow = Math.max(charge, power * .45f);
        if (glow > 0f) {
            fill.setShader(chargeGlow);
            fill.setAlpha(alpha255(glow * out));
            canvas.drawCircle(centerX, screenY, glowRadius, fill);
        }
        drawPulse(canvas, span(t, Timeline.PULSE_START, Timeline.PULSE_START + Timeline.PULSE_LENGTH), out);
        drawPulse(canvas, span(t, Timeline.PULSE_SECOND, Timeline.PULSE_SECOND + Timeline.PULSE_LENGTH), out * .7f);

        canvas.save();
        canvas.translate(centerX, top + (CY + SCREEN_HALF_HEIGHT + 4f) * markScale);
        canvas.scale(markHeight * .62f, markHeight * .04f);
        fill.setShader(shadow);
        fill.setAlpha(alpha255(reveal * out));
        canvas.drawCircle(0f, 0f, 1f, fill);
        canvas.restore();

        canvas.save();
        canvas.translate(centerX - WIDTH * markScale / 2f, top);
        canvas.scale(markScale, markScale);
        canvas.scale(1f + thump, 1f + thump, CX, CY);
        float glint = t >= Timeline.GLINT_START && t <= Timeline.GLINT_END
                ? -80f + (WIDTH + 120f) * span(t, Timeline.GLINT_START, Timeline.GLINT_END) : Float.NaN;
        drawTiles(canvas, t, out, glint);
        if (reveal > 0f) drawScreen(canvas, t, reveal, out, charge, glint);
        canvas.restore();

        if (titleAlpha > 0f) {
            titlePaint.setColor(mix(text, accent, charge * .85f));
            titlePaint.setAlpha(alpha255(titleAlpha * out));
            canvas.drawText(shownTitle, centerX - titleWidth / 2f,
                    titleTop + dp(6) * (1f - titleAlpha) - titleAscent, titlePaint);
        }
        drawProgress(canvas, t, charge, out);
        if (captionAlpha > 0f) {
            captionPaint.setColor(muted);
            captionPaint.setAlpha(alpha255(captionAlpha * out));
            canvas.drawText(shownCaption, centerX - captionWidth / 2f,
                    captionTop + dp(6) * (1f - captionAlpha) - captionAscent, captionPaint);
        }
        canvas.restore();
    }

    /** A ring in the shape of the screen that leaves it and fades. */
    private void drawPulse(Canvas canvas, float pulse, float out) {
        if (pulse <= 0f || pulse >= 1f) return;
        float fade = (1f - pulse) * (1f - pulse);
        float grow = markHeight * (.04f + .80f * Easing.STANDARD.at(pulse));
        float halfWidth = SCREEN_HALF_WIDTH * markScale + grow;
        float halfHeight = SCREEN_HALF_HEIGHT * markScale + grow;
        float radius = SCREEN_RADIUS * markScale + grow;
        stroke.setShader(null);
        stroke.setColorFilter(null);
        stroke.setStrokeWidth(dp(1.5f));
        stroke.setColor(alpha(accent, .5f * fade * out));
        rect.set(centerX - halfWidth, screenY - halfHeight, centerX + halfWidth, screenY + halfHeight);
        canvas.drawRoundRect(rect, radius, radius, stroke);
    }

    /** The video tiles: they slide out from behind the screen like wings and charge outward after it. */
    private void drawTiles(Canvas canvas, float t, float out, float glint) {
        for (int i = 0; i < TILE_OFFSETS.length; i++) {
            float start = Timeline.TILES_START + i * Timeline.TILE_STAGGER;
            float opacity = ease(t, start, start + Timeline.TILE_VISIBLE) * out * TILE_OPACITY[i];
            if (opacity <= 0f || tileLayers[i] == null) continue;
            float spread = Easing.SPRING.at(span(t, start, start + Timeline.TILE_OPEN));
            float offset = TILE_OFFSETS[i] * (.45f + .55f * spread);
            float tileCharge = charge(t - (i + 1) * Timeline.CHARGE_STAGGER);
            float halfWidth = TILE_HALF_WIDTHS[i];
            float halfHeight = TILE_HALF_HEIGHTS[i];
            for (int side = -1; side <= 1; side += 2) {
                float x = CX + side * offset;
                rect.set(x - halfWidth - LAYER_MARGIN, CY - halfHeight - LAYER_MARGIN,
                        x + halfWidth + LAYER_MARGIN, CY + halfHeight + LAYER_MARGIN);
                paintLayer(canvas, tileLayers[i], rect, opacity, null);
                // Stop short of a full tint so the bevels of the steel stay readable under the accent.
                if (tileCharge > 0f) {
                    paintLayer(canvas, tileLayers[i], rect, opacity * tileCharge * CHARGE_DEPTH, charged);
                }
                if (!Float.isNaN(glint)) {
                    rect.set(x - halfWidth, CY - halfHeight, x + halfWidth, CY + halfHeight);
                    fill.setColorFilter(null);
                    fill.setShader(glint(glint));
                    fill.setAlpha(alpha255((.66f - .30f * tileCharge) * opacity));
                    canvas.drawRoundRect(rect, TILE_RADII[i], TILE_RADII[i], fill);
                    fill.setShader(null);
                }
            }
        }
    }

    /** The screen: dark glass in a steel rim, the play button on it and the picture that comes on behind it. */
    private void drawScreen(Canvas canvas, float t, float reveal, float out, float charge, float glint) {
        if (glassLayer == null) return;
        float opacity = reveal * out;
        float grow = .86f + .14f * reveal;
        canvas.save();
        canvas.translate(0f, 6f * (1f - reveal));
        canvas.scale(grow, grow, CX, CY);

        rect.set(CX - SCREEN_HALF_WIDTH - LAYER_MARGIN, CY - SCREEN_HALF_HEIGHT - LAYER_MARGIN,
                CX + SCREEN_HALF_WIDTH + LAYER_MARGIN, CY + SCREEN_HALF_HEIGHT + LAYER_MARGIN);
        paintLayer(canvas, glassLayer, rect, opacity, null);
        float power = drawPicture(canvas, t, opacity, charge);
        if (!Float.isNaN(glint) && power < 1f) {
            fill.setColorFilter(null);
            fill.setShader(glint(glint));
            fill.setAlpha(alpha255(.22f * (1f - power) * opacity));
            canvas.drawPath(glass, fill);
            fill.setShader(null);
        }
        rect.set(CX - SCREEN_HALF_WIDTH - LAYER_MARGIN, CY - SCREEN_HALF_HEIGHT - LAYER_MARGIN,
                CX + SCREEN_HALF_WIDTH + LAYER_MARGIN, CY + SCREEN_HALF_HEIGHT + LAYER_MARGIN);
        paintLayer(canvas, rimLayer, rect, opacity, null);
        if (charge > 0f) paintLayer(canvas, rimLayer, rect, opacity * charge * CHARGE_DEPTH, charged);
        if (!Float.isNaN(glint)) {
            float inset = RIM / 2f;
            rect.set(CX - SCREEN_HALF_WIDTH + inset, CY - SCREEN_HALF_HEIGHT + inset,
                    CX + SCREEN_HALF_WIDTH - inset, CY + SCREEN_HALF_HEIGHT - inset);
            stroke.setColorFilter(null);
            stroke.setShader(glint(glint));
            stroke.setStrokeWidth(RIM);
            stroke.setAlpha(alpha255((.66f - .30f * charge) * opacity));
            canvas.drawRoundRect(rect, SCREEN_RADIUS - inset, SCREEN_RADIUS - inset, stroke);
            stroke.setShader(null);
        }
        drawButton(canvas, t, out, charge, power, glint);
        canvas.restore();
    }

    /**
     * The picture coming on like a tube: a bright line across the middle widens, then opens to the whole glass and
     * stays lit. Answers how far the picture is on, 0..1.
     */
    private float drawPicture(Canvas canvas, float t, float opacity, float charge) {
        float wide = Easing.DECELERATE.at(span(t, Timeline.POWER_START, Timeline.POWER_WIDE));
        if (wide <= 0f) return 0f;
        float tall = Easing.DECELERATE.at(span(t, Timeline.POWER_WIDE - 30f, Timeline.POWER_FULL));
        float inset = RIM / 2f;
        float glassHalfWidth = SCREEN_HALF_WIDTH - inset;
        float glassHalfHeight = SCREEN_HALF_HEIGHT - inset;
        float glassRadius = SCREEN_RADIUS - inset;
        float halfWidth = glassHalfWidth * (.08f + .92f * wide);
        float halfHeight = Math.max(.7f, glassHalfHeight * tall);
        float radius = Math.min(glassRadius, halfHeight);
        rect.set(CX - halfWidth, CY - halfHeight, CX + halfWidth, CY + halfHeight);
        fill.setColorFilter(null);
        fill.setShader(screenLight);
        fill.setAlpha(alpha255((SCREEN_ON + (1f - SCREEN_ON) * charge) * wide * opacity));
        canvas.drawRoundRect(rect, radius, radius, fill);
        fill.setShader(null);
        float line = wide * (1f - tall);
        if (line > 0f) {
            fill.setColor(alpha(mix(accent, Color.WHITE, .75f), line * opacity));
            rect.set(CX - halfWidth, CY - .6f, CX + halfWidth, CY + .6f);
            canvas.drawRoundRect(rect, .6f, .6f, fill);
        }
        return wide * tall;
    }

    /** The play button: it pops up, goes down as the bar fills and lights up as the picture comes on behind it. */
    private void drawButton(Canvas canvas, float t, float out, float charge, float power, float glint) {
        float opacity = ease(t, Timeline.BUTTON_START, Timeline.BUTTON_START + 180f) * out;
        if (opacity <= 0f || buttonLayer == null) return;
        float pop = Easing.BACK.at(span(t, Timeline.BUTTON_START, Timeline.BUTTON_FULL));
        float press = 1f - .10f * bump(span(t, Timeline.PRESS, Timeline.PRESS + Timeline.PRESS_LENGTH));
        float size = pop * press;
        if (size <= 0f) return;
        canvas.save();
        canvas.scale(size, size, CX, CY);
        float half = BUTTON_TIP + LAYER_MARGIN;
        rect.set(CX - half, CY - half, CX + half, CY + half);
        paintLayer(canvas, buttonLayer, rect, opacity, null);
        float shine = Math.max(power * .55f, charge * .85f);
        if (shine > 0f) paintLayer(canvas, buttonLayer, rect, opacity * shine, lit);
        if (!Float.isNaN(glint)) {
            fill.setColorFilter(null);
            fill.setShader(glint(glint));
            fill.setAlpha(alpha255((.66f - .30f * charge) * opacity));
            canvas.drawPath(button, fill);
            fill.setShader(null);
        }
        canvas.restore();
    }

    private void paintLayer(Canvas canvas, Bitmap layer, RectF bounds, float opacity, ColorFilter filter) {
        image.setColorFilter(filter);
        image.setAlpha(alpha255(opacity));
        canvas.drawBitmap(layer, null, bounds, image);
    }

    /**
     * Rasterizes the steel and the glass once at the rendering scale, as the Open Voyah Controls emblem does, so a
     * frame only moves, scales and tints finished images instead of drawing every gradient and edge again.
     */
    private void buildLayers(float scale) {
        float inset = RIM / 2f;
        glassLayer = layer(SCREEN_HALF_WIDTH, SCREEN_HALF_HEIGHT, scale, canvas -> {
            fill.setColorFilter(null);
            fill.setShader(glassShader);
            fill.setAlpha(255);
            canvas.drawPath(glass, fill);
            // A soft reflection across the upper left of the glass.
            canvas.save();
            canvas.clipPath(glass);
            fill.setShader(reflectionShader);
            canvas.drawRect(CX - SCREEN_HALF_WIDTH, CY - SCREEN_HALF_HEIGHT, CX + SCREEN_HALF_WIDTH,
                    CY + SCREEN_HALF_HEIGHT, fill);
            canvas.restore();
            fill.setShader(null);
        });
        rimLayer = layer(SCREEN_HALF_WIDTH, SCREEN_HALF_HEIGHT, scale, canvas -> {
            stroke.setColorFilter(null);
            stroke.setShader(rimShader);
            stroke.setStrokeWidth(RIM);
            stroke.setAlpha(255);
            rect.set(CX - SCREEN_HALF_WIDTH + inset, CY - SCREEN_HALF_HEIGHT + inset,
                    CX + SCREEN_HALF_WIDTH - inset, CY + SCREEN_HALF_HEIGHT - inset);
            canvas.drawRoundRect(rect, SCREEN_RADIUS - inset, SCREEN_RADIUS - inset, stroke);
            stroke.setShader(null);
            // A dark line where the glass meets the steel and a bright one on the outer edge.
            stroke.setStrokeWidth(.5f);
            stroke.setColor(tone(.08f, .85f));
            rect.inset(inset + .25f, inset + .25f);
            canvas.drawRoundRect(rect, SCREEN_RADIUS - RIM - .25f, SCREEN_RADIUS - RIM - .25f, stroke);
            stroke.setStrokeWidth(.4f);
            stroke.setColor(tone(1f, .5f));
            rect.set(CX - SCREEN_HALF_WIDTH + .2f, CY - SCREEN_HALF_HEIGHT + .2f,
                    CX + SCREEN_HALF_WIDTH - .2f, CY + SCREEN_HALF_HEIGHT - .2f);
            canvas.drawRoundRect(rect, SCREEN_RADIUS - .2f, SCREEN_RADIUS - .2f, stroke);
        });
        buttonLayer = layer(BUTTON_TIP, BUTTON_TIP, scale, canvas -> {
            // A raised button: a soft shadow on the glass, a steel face and a bright edge.
            canvas.save();
            canvas.translate(.7f, 1.1f);
            fill.setColorFilter(null);
            fill.setShader(null);
            fill.setColor(alpha(Color.BLACK, .5f));
            canvas.drawPath(button, fill);
            canvas.restore();
            fill.setShader(buttonShader);
            fill.setAlpha(255);
            canvas.drawPath(button, fill);
            fill.setShader(null);
            stroke.setShader(null);
            stroke.setColorFilter(null);
            stroke.setStrokeWidth(.5f);
            stroke.setColor(tone(.98f, .7f));
            canvas.drawPath(button, stroke);
        });
        for (int i = 0; i < TILE_OFFSETS.length; i++) {
            float halfWidth = TILE_HALF_WIDTHS[i];
            float halfHeight = TILE_HALF_HEIGHTS[i];
            float radius = TILE_RADII[i];
            tileLayers[i] = layer(halfWidth, halfHeight, scale, canvas -> {
                rect.set(CX - halfWidth, CY - halfHeight, CX + halfWidth, CY + halfHeight);
                matrix.setScale(1f, halfHeight);
                matrix.postTranslate(0f, CY);
                tileShader.setLocalMatrix(matrix);
                fill.setColorFilter(null);
                fill.setShader(tileShader);
                fill.setAlpha(255);
                canvas.drawRoundRect(rect, radius, radius, fill);
                fill.setShader(null);
                stroke.setShader(null);
                stroke.setColorFilter(null);
                stroke.setStrokeWidth(.4f);
                stroke.setColor(tone(.96f, .55f));
                canvas.drawRoundRect(rect, radius, radius, stroke);
                // The picture of a tile is a darker pane inside its steel frame.
                rect.inset(1.5f, 1.5f);
                fill.setColor(alpha(mix(backdrop, body, .18f), .9f));
                canvas.drawRoundRect(rect, radius * .6f, radius * .6f, fill);
            });
        }
    }

    /** A raster of the part drawn by {@code paint} around the mark's center, with a margin for antialiased edges. */
    private static Bitmap layer(float halfWidth, float halfHeight, float scale, Consumer<Canvas> paint) {
        float width = 2f * (halfWidth + LAYER_MARGIN);
        float height = 2f * (halfHeight + LAYER_MARGIN);
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, (int) Math.ceil(width * scale)),
                Math.max(1, (int) Math.ceil(height * scale)), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(bitmap.getWidth() / width, bitmap.getHeight() / height);
        canvas.translate(-(CX - halfWidth - LAYER_MARGIN), -(CY - halfHeight - LAYER_MARGIN));
        paint.accept(canvas);
        return bitmap;
    }

    /**
     * The progress bar under the name, cut into chapters: the buffer runs ahead, the played part follows with the
     * scrubber at its head, and the scrubber leaves once the bar is full. The bar swells for a moment at the charge.
     */
    private void drawProgress(Canvas canvas, float t, float charge, float out) {
        float track = ease(t, Timeline.BAR_START, Timeline.BAR_START + 220f);
        if (track <= 0f) return;
        float left = centerX - barWidth / 2f;
        float thickness = dp(3) + dp(1.5f) * charge;
        float middle = barY + dp(1.5f);
        fill.setColorFilter(null);
        fill.setShader(null);
        chapters(canvas, left, 1f, middle, thickness, alpha(light, .16f * track * out));
        float buffered = Easing.STANDARD.at(span(t, Timeline.BAR_START, Timeline.BUFFER_DRAWN));
        chapters(canvas, left, buffered, middle, thickness, alpha(light, .30f * track * out));
        float played = Easing.STANDARD.at(span(t, Timeline.PLAYED_START, Timeline.BAR_DRAWN));
        chapters(canvas, left, played, middle, thickness, alpha(accent, (.85f + .15f * charge) * out));

        float gone = ease(t, Timeline.BAR_DRAWN, Timeline.SCRUBBER_GONE);
        float scrubber = ease(t, Timeline.PLAYED_START, Timeline.PLAYED_START + 160f) * (1f - gone) * out;
        if (scrubber <= 0f) return;
        canvas.save();
        canvas.translate(left + barWidth * played, middle);
        fill.setShader(scrubberGlow);
        fill.setAlpha(alpha255(scrubber));
        canvas.drawCircle(0f, 0f, dp(15), fill);
        fill.setShader(null);
        fill.setColor(alpha(accent, scrubber));
        canvas.drawCircle(0f, 0f, dp(6) * (.4f + .6f * (1f - gone)), fill);
        canvas.restore();
    }

    /** The chapters of the bar from its left end up to {@code share} of its length. */
    private void chapters(Canvas canvas, float left, float share, float middle, float thickness, int color) {
        if (share <= 0f) return;
        fill.setColor(color);
        float end = left + barWidth * share;
        float gap = dp(3);
        float from = left;
        for (int k = 0; k < CHAPTERS.length; k++) {
            boolean last = k == CHAPTERS.length - 1;
            float to = left + barWidth * CHAPTERS[k] - (last ? 0f : gap / 2f);
            float right = Math.min(to, end);
            if (right > from) canvas.drawRect(from, middle - thickness / 2f, right, middle + thickness / 2f, fill);
            from = to + gap;
            if (from >= end) break;
        }
    }

    private Shader glint(float x) {
        matrix.setTranslate(x, 0f);
        glintShader.setLocalMatrix(matrix);
        return glintShader;
    }

    private static float charge(float t) {
        return ease(t, Timeline.CHARGE_START, Timeline.CHARGE_FULL)
                * (1f - ease(t, Timeline.CHARGE_FADE, Timeline.CHARGE_END));
    }

    /** A quick rise and a slower fall over 0..1, for a press or a beat. */
    static float bump(float x) {
        if (x <= 0f || x >= 1f) return 0f;
        if (x < .3f) return Easing.STANDARD.at(x / .3f);
        float fall = (1f - x) / .7f;
        return fall * fall;
    }

    /** A three-point steel ramp: level 0 is the deepest shadow and 1 the brightest edge. */
    private int tone(float level) {
        return level < .5f ? mix(shade, body, level * 2f) : mix(body, light, (level - .5f) * 2f);
    }

    private int tone(float level, float opacity) {
        return alpha(tone(level), opacity);
    }

    private float dp(float value) {
        return value * density;
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }

    private static int alpha255(float opacity) {
        return Math.round(clamp(opacity) * 255f);
    }

    private static int alpha(int color, float opacity) {
        return (color & 0xFFFFFF) | (Math.round(clamp(opacity) * Color.alpha(color)) << 24);
    }

    static int mix(int from, int to, float fraction) {
        float f = clamp(fraction);
        return Color.argb(
                Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * f),
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * f),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * f),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f));
    }
}
