package org.openvoyah.youtube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IntroPacingTest {
    private static final long FRAME = 16_666_667L;

    @Test public void easingStartsAndEndsInPlaceAndStandardNeverOvershoots() {
        for (IntroPacing.Easing easing : new IntroPacing.Easing[] {
                IntroPacing.Easing.STANDARD, IntroPacing.Easing.DECELERATE, IntroPacing.Easing.SPRING,
                IntroPacing.Easing.BACK, IntroPacing.Easing.ACCELERATE}) {
            assertEquals(0f, easing.at(0f), 0f);
            assertEquals(1f, easing.at(1f), 0f);
            assertEquals(0f, easing.at(-1f), 0f);
            assertEquals(1f, easing.at(2f), 0f);
        }
        float previous = 0f;
        for (int step = 1; step <= 100; step++) {
            float value = IntroPacing.Easing.STANDARD.at(step / 100f);
            assertTrue(value >= previous);
            assertTrue(value <= 1f);
            previous = value;
        }
        assertEquals(.5f, new IntroPacing.Easing(.25f, .25f, .75f, .75f).at(.5f), 1e-4f);
    }

    @Test public void overshootingCurvesPeakOnceAndSettle() {
        assertPeak(IntroPacing.Easing.SPRING, 1.005f, 1.02f);
        assertPeak(IntroPacing.Easing.BACK, 1.07f, 1.13f);
    }

    private static void assertPeak(IntroPacing.Easing easing, float low, float high) {
        float peak = 0f;
        int peakStep = 0;
        for (int step = 0; step <= 200; step++) {
            float value = easing.at(step / 200f);
            if (value > peak) {
                peak = value;
                peakStep = step;
            }
        }
        assertTrue(peak > low);
        assertTrue(peak < high);
        // After the peak the curve only falls back toward its end.
        for (int step = peakStep + 1; step <= 200; step++) assertTrue(easing.at(step / 200f) <= peak);
    }

    @Test public void theFirstStartAndEveryReturnFromOutsideTheAppAreArrivals() {
        IntroPacing.Arrivals arrivals = new IntroPacing.Arrivals();
        assertTrue(arrivals.onStart());
        // Another app or the home screen covers the activity.
        arrivals.onStop(false);
        assertTrue(arrivals.onStart());
        arrivals.onStop(false);
        assertTrue(arrivals.onStart());
        // A start without a stop in between brings nothing back.
        assertFalse(arrivals.onStart());
    }

    @Test public void configurationChangesAndOwnScreensAreNotArrivals() {
        IntroPacing.Arrivals arrivals = new IntroPacing.Arrivals();
        assertTrue(arrivals.onStart());
        arrivals.onStop(true);
        assertFalse(arrivals.onStart());
        arrivals.handOff();
        arrivals.onStop(false);
        assertFalse(arrivals.onStart());
        // The hand-off covers one visit only; leaving for the home screen afterwards counts again.
        arrivals.onStop(false);
        assertTrue(arrivals.onStart());
    }

    @Test public void accelerateLeavesFasterThanItStarts() {
        float early = IntroPacing.Easing.ACCELERATE.at(.25f);
        float late = 1f - IntroPacing.Easing.ACCELERATE.at(.75f);
        assertTrue(early < .25f);
        assertTrue(late < .75f - .25f);
        float previous = 0f;
        for (int step = 1; step <= 100; step++) {
            float value = IntroPacing.Easing.ACCELERATE.at(step / 100f);
            assertTrue(value >= previous);
            previous = value;
        }
    }

    @Test public void spanClampsToTheStage() {
        assertEquals(0f, IntroPacing.span(100f, 200f, 300f), 0f);
        assertEquals(.5f, IntroPacing.span(250f, 200f, 300f), 1e-6f);
        assertEquals(1f, IntroPacing.span(400f, 200f, 300f), 0f);
        assertEquals(1f, IntroPacing.span(300f, 300f, 300f), 0f);
    }

    @Test public void settleWaitsForSteadyFramesAndRestartsAfterAStall() {
        IntroPacing.Settle settle = new IntroPacing.Settle(FRAME, 3);
        long time = 0L;
        assertFalse(settle.onFrame(time));
        assertFalse(settle.onFrame(time += FRAME));
        assertFalse(settle.onFrame(time += 2 * FRAME));
        assertFalse(settle.onFrame(time += 6 * FRAME));
        assertFalse(settle.onFrame(time += FRAME));
        assertFalse(settle.onFrame(time += FRAME));
        assertTrue(settle.onFrame(time + FRAME));
    }

    @Test public void clockTurnsAStallIntoAShortPause() {
        IntroPacing.Clock clock = new IntroPacing.Clock(FRAME, 3_000_000_000L);
        assertEquals(0L, clock.onFrame(5_000L));
        assertEquals(FRAME, clock.onFrame(5_000L + FRAME));
        // Half a second without frames moves the intro on by two frames only.
        assertEquals(3 * FRAME, clock.onFrame(5_000L + FRAME + 500_000_000L));
    }

    @Test public void clockNeverFallsMoreThanHalfThePlanBehind() {
        long planned = 1_000_000_000L;
        IntroPacing.Clock clock = new IntroPacing.Clock(FRAME, planned);
        clock.onFrame(0L);
        long intro = 0L;
        for (long time = 100_000_000L; time <= 2_000_000_000L; time += 100_000_000L) intro = clock.onFrame(time);
        assertEquals(2_000_000_000L - planned / 2, intro);
    }

    @Test public void youTubeStagesPlayInOrderAndTheEntranceEndsBeforeTheHold() {
        assertTrue(YouTubeIntro.Timeline.BODY_START < YouTubeIntro.Timeline.BODY_VISIBLE);
        assertTrue(YouTubeIntro.Timeline.BUTTON_FULL < YouTubeIntro.Timeline.PRESS);
        // The buffer runs ahead of the played part, the button goes down as the bar fills and the screen comes on
        // with the charge while the button is down.
        assertTrue(YouTubeIntro.Timeline.BUFFER_DRAWN < YouTubeIntro.Timeline.BAR_DRAWN);
        assertTrue(YouTubeIntro.Timeline.BAR_START < YouTubeIntro.Timeline.PLAYED_START);
        assertTrue(Math.abs(YouTubeIntro.Timeline.BAR_DRAWN - YouTubeIntro.Timeline.PRESS) <= 40f);
        float pressed = YouTubeIntro.Timeline.PRESS + .3f * YouTubeIntro.Timeline.PRESS_LENGTH;
        assertTrue(Math.abs(YouTubeIntro.Timeline.POWER_START - pressed) <= 20f);
        assertTrue(Math.abs(YouTubeIntro.Timeline.CHARGE_START - YouTubeIntro.Timeline.POWER_START) <= 20f);
        assertTrue(YouTubeIntro.Timeline.POWER_START < YouTubeIntro.Timeline.POWER_WIDE);
        assertTrue(YouTubeIntro.Timeline.POWER_WIDE < YouTubeIntro.Timeline.POWER_FULL);
        assertTrue(YouTubeIntro.Timeline.PULSE_START < YouTubeIntro.Timeline.PULSE_SECOND);
        float lastTile = YouTubeIntro.Timeline.TILES_START + 2 * YouTubeIntro.Timeline.TILE_STAGGER
                + YouTubeIntro.Timeline.TILE_OPEN;
        assertTrue(lastTile < YouTubeIntro.Timeline.PRESS);
        float charged = YouTubeIntro.Timeline.CHARGE_END + 3 * YouTubeIntro.Timeline.CHARGE_STAGGER;
        float pulses = YouTubeIntro.Timeline.PULSE_SECOND + YouTubeIntro.Timeline.PULSE_LENGTH;
        float settled = Math.max(YouTubeIntro.Timeline.SCRUBBER_GONE, YouTubeIntro.Timeline.GLINT_END);
        assertTrue(Math.max(charged, Math.max(pulses, settled)) < YouTubeIntro.Timeline.EXIT_START);
        // The iris opens while the mark flies off, and the whole intro stays short enough to play on every return.
        assertTrue(YouTubeIntro.Timeline.EXIT_START < YouTubeIntro.Timeline.OPEN_START);
        assertTrue(YouTubeIntro.Timeline.OPEN_START < YouTubeIntro.Timeline.EXIT_END);
        assertTrue(YouTubeIntro.Timeline.EXIT_END < YouTubeIntro.Timeline.END);
        assertTrue(YouTubeIntro.Timeline.END >= 1_700f);
        assertTrue(YouTubeIntro.Timeline.END <= 1_800f);
        // At the warm-up point the screen is on and the scrubber is still on the bar.
        assertTrue(YouTubeIntro.Timeline.POWER_FULL < YouTubeIntro.Timeline.WARM);
        assertTrue(YouTubeIntro.Timeline.BAR_DRAWN < YouTubeIntro.Timeline.WARM);
        assertTrue(YouTubeIntro.Timeline.WARM < YouTubeIntro.Timeline.SCRUBBER_GONE);
    }

    @Test public void aPressGoesDownQuicklyAndComesBackSlowly() {
        assertEquals(0f, YouTubeIntro.bump(0f), 0f);
        assertEquals(1f, YouTubeIntro.bump(.3f), 1e-4f);
        assertTrue(YouTubeIntro.bump(.15f) > YouTubeIntro.bump(.65f));
        assertEquals(0f, YouTubeIntro.bump(1f), 0f);
    }
}
