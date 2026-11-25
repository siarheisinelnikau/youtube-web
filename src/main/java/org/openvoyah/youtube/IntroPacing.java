package org.openvoyah.youtube;

/**
 * Timing for the startup intro, free of Android types so that it runs in JVM tests. The pacing follows the
 * Open Voyah Controls startup intro: wait for steady frames, then never let one late frame skip a stage.
 */
final class IntroPacing {
    private IntroPacing() {}

    /** How far {@code value} has moved from {@code begin} to {@code end}, clamped to 0..1. */
    static float span(float value, float begin, float end) {
        if (end <= begin) return value >= end ? 1f : 0f;
        return clamp((value - begin) / (end - begin));
    }

    /** {@link #span} through the standard Material curve. */
    static float ease(float value, float begin, float end) {
        return Easing.STANDARD.at(span(value, begin, end));
    }

    static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    static float lerp(float from, float to, float fraction) {
        return from + (to - from) * fraction;
    }

    /** A cubic Bézier timing curve from (0, 0) to (1, 1), as in CSS and Android's PathInterpolator. */
    static final class Easing {
        /** Fast out, slow in. */
        static final Easing STANDARD = new Easing(.4f, 0f, .2f, 1f);
        /** Starts at full speed and settles slowly. */
        static final Easing DECELERATE = new Easing(0f, 0f, .2f, 1f);
        /** Overshoots by about one percent and settles back, the curve of the Open Voyah Controls wings. */
        static final Easing SPRING = new Easing(.22f, 1.18f, .36f, 1f);
        /** Overshoots by about a tenth halfway through and settles back. */
        static final Easing BACK = new Easing(.34f, 1.56f, .64f, 1f);
        /** Starts slowly and leaves at full speed, for a mark that flies past the viewer. */
        static final Easing ACCELERATE = new Easing(.4f, 0f, 1f, 1f);

        private final float x1;
        private final float y1;
        private final float x2;
        private final float y2;

        Easing(float x1, float y1, float x2, float y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        float at(float fraction) {
            if (fraction <= 0f) return 0f;
            if (fraction >= 1f) return 1f;
            float t = fraction;
            for (int step = 0; step < 8; step++) {
                float error = curve(t, x1, x2) - fraction;
                if (Math.abs(error) < 1e-5f) return curve(t, y1, y2);
                float slope = slope(t, x1, x2);
                if (Math.abs(slope) < 1e-6f) break;
                t = clamp(t - error / slope);
            }
            float low = 0f;
            float high = 1f;
            t = fraction;
            for (int step = 0; step < 32; step++) {
                float x = curve(t, x1, x2);
                if (Math.abs(x - fraction) < 1e-5f) break;
                if (x < fraction) low = t;
                else high = t;
                t = (low + high) / 2f;
            }
            return curve(t, y1, y2);
        }

        private static float curve(float t, float p1, float p2) {
            float u = 1f - t;
            return 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t;
        }

        private static float slope(float t, float p1, float p2) {
            float u = 1f - t;
            return 3f * u * u * p1 + 6f * u * t * (p2 - p1) + 3f * t * t * (1f - p2);
        }
    }

    /**
     * Decides which starts of the activity bring it back from outside the app: the first start in a process and
     * every start after the activity left the screen for another app or the home screen. A stop for a
     * configuration change and a screen the activity opened itself, such as its settings or a system picker, do
     * not count, so coming back from them shows the app at once.
     */
    static final class Arrivals {
        private boolean away = true;
        private boolean handedOff;

        /** The activity opens another screen itself, so its next start returns from that screen. */
        void handOff() {
            handedOff = true;
        }

        void onStop(boolean changingConfigurations) {
            if (!changingConfigurations && !handedOff) away = true;
        }

        /** Answers whether this start brings the activity back from outside the app. */
        boolean onStart() {
            boolean arrived = away;
            away = false;
            handedOff = false;
            return arrived;
        }
    }

    /**
     * Decides when frames arrive steadily enough for the intro to begin. A cold start lays out the whole app
     * under the intro, and an intro started during those frames stutters; until then the intro shows only the
     * backdrop, which matches the window background.
     */
    static final class Settle {
        private final long allowedGap;
        private final int steadyFrames;
        private long last = -1L;
        private int run;

        Settle(long frameNanos, int steadyFrames) {
            // One missed vsync is not visible in the intro; two in a row are.
            allowedGap = 2 * frameNanos + frameNanos / 4;
            this.steadyFrames = steadyFrames;
        }

        /** Feeds one frame time and answers whether the last gaps all stayed within two frames. */
        boolean onFrame(long timeNanos) {
            if (last >= 0) run = timeNanos - last <= allowedGap ? run + 1 : 0;
            last = timeNanos;
            return run >= steadyFrames;
        }
    }

    /**
     * Turns frame times into intro time. A late frame moves the intro on by at most two frames, so a stall shows
     * as a short pause instead of a jump over a stage. Intro time never falls more than half the plan behind the
     * wall clock, so a device that stays slow still finishes within one and a half times the plan.
     */
    static final class Clock {
        private final long frameNanos;
        private final long plannedNanos;
        private long start = -1L;
        private long last = -1L;
        private long introNanos;

        Clock(long frameNanos, long plannedNanos) {
            this.frameNanos = frameNanos;
            this.plannedNanos = plannedNanos;
        }

        /** Intro time in nanoseconds; it starts at the first frame. */
        long onFrame(long timeNanos) {
            if (start < 0) {
                start = timeNanos;
                last = timeNanos;
                return introNanos;
            }
            long gap = Math.max(0L, timeNanos - last);
            last = timeNanos;
            introNanos = Math.max(introNanos + Math.min(gap, 2 * frameNanos), timeNanos - start - plannedNanos / 2);
            return introNanos;
        }
    }
}
