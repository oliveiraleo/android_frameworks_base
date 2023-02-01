/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.view.choreographertests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.compat.CompatChanges;
import android.hardware.display.DisplayManager;
import android.os.Looper;
import android.support.test.uiautomator.UiDevice;
import android.util.Log;
import android.view.Choreographer;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class AttachedChoreographerTest {
    private static final String TAG = "AttachedChoreographerTest";
    private static final long DISPLAY_MODE_RETURNS_PHYSICAL_REFRESH_RATE_CHANGEID = 170503758;
    private static final long THRESHOLD_MS = 10;
    private static final int FRAME_ITERATIONS = 21;
    private static final int CALLBACK_MISSED_THRESHOLD = 2;

    private final CountDownLatch mTestCompleteSignal = new CountDownLatch(2);
    private final CountDownLatch mSurfaceCreationCountDown = new CountDownLatch(1);
    private final CountDownLatch mNoCallbackSignal = new CountDownLatch(1);

    private ActivityScenario<GraphicsActivity> mScenario;
    private int mInitialMatchContentFrameRate;
    private DisplayManager mDisplayManager;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private boolean mIsFirstCallback = true;
    private int mCallbackMissedCounter = 0;

    @Before
    public void setUp() throws Exception {
        mScenario = ActivityScenario.launch(GraphicsActivity.class);
        mScenario.moveToState(Lifecycle.State.CREATED);
        mScenario.onActivity(activity -> {
            mSurfaceView = activity.findViewById(R.id.surface);
            mSurfaceHolder = mSurfaceView.getHolder();
            mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {

                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    mSurfaceCreationCountDown.countDown();
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width,
                        int height) {
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                }
            });
        });

        UiDevice uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        uiDevice.wakeUp();
        uiDevice.executeShellCommand("wm dismiss-keyguard");
        mScenario.moveToState(Lifecycle.State.RESUMED);

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.LOG_COMPAT_CHANGE,
                        Manifest.permission.READ_COMPAT_CHANGE_CONFIG,
                        Manifest.permission.MODIFY_REFRESH_RATE_SWITCHING_TYPE,
                        Manifest.permission.OVERRIDE_DISPLAY_MODE_REQUESTS,
                        Manifest.permission.MANAGE_GAME_MODE);
        mScenario.onActivity(activity -> {
            mDisplayManager = activity.getSystemService(DisplayManager.class);
            mInitialMatchContentFrameRate = toSwitchingType(
                    mDisplayManager.getMatchContentFrameRateUserPreference());
            mDisplayManager.setRefreshRateSwitchingType(
                    DisplayManager.SWITCHING_TYPE_RENDER_FRAME_RATE_ONLY);
            mDisplayManager.setShouldAlwaysRespectAppRequestedMode(true);
            boolean changeIsEnabled =
                    CompatChanges.isChangeEnabled(
                            DISPLAY_MODE_RETURNS_PHYSICAL_REFRESH_RATE_CHANGEID);
            Log.i(TAG, "DISPLAY_MODE_RETURNS_PHYSICAL_REFRESH_RATE_CHANGE_ID is "
                    + (changeIsEnabled ? "enabled" : "disabled"));
        });
    }

    @After
    public void tearDown() {
        mDisplayManager.setRefreshRateSwitchingType(mInitialMatchContentFrameRate);
        mDisplayManager.setShouldAlwaysRespectAppRequestedMode(false);
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    public void testCreateChoreographer() {
        mScenario.onActivity(activity -> {
            if (waitForCountDown(mSurfaceCreationCountDown, /* timeoutInSeconds */ 1L)) {
                fail("Unable to create surface within 1 Second");
            }
            SurfaceControl sc = mSurfaceView.getSurfaceControl();
            mTestCompleteSignal.countDown();
            SurfaceControl sc1 = new SurfaceControl(sc, "AttachedChoreographerTests");
            // Create attached choreographer with getChoreographer
            sc1.getChoreographer();
            assertTrue(sc1.hasChoreographer());
            assertTrue(sc1.isValid());
            sc1.release();

            SurfaceControl sc2 = new SurfaceControl(sc, "AttachedChoreographerTests");
            // Create attached choreographer with Looper.myLooper
            sc2.getChoreographer(Looper.myLooper());
            assertTrue(sc2.hasChoreographer());
            assertTrue(sc2.isValid());
            sc2.release();

            SurfaceControl sc3 = new SurfaceControl(sc, "AttachedChoreographerTests");
            // Create attached choreographer with Looper.myLooper
            sc3.getChoreographer(Looper.getMainLooper());
            assertTrue(sc3.hasChoreographer());
            assertTrue(sc3.isValid());
            sc3.release();
            mTestCompleteSignal.countDown();
        });
        if (waitForCountDown(mTestCompleteSignal, /* timeoutInSeconds */ 2L)) {
            fail("Test not finished in 2 Seconds");
        }
    }

    @Test
    public void testCopySurfaceControl() {
        mScenario.onActivity(activity -> {
            if (waitForCountDown(mSurfaceCreationCountDown, /* timeoutInSeconds */ 1L)) {
                fail("Unable to create surface within 1 Second");
            }
            SurfaceControl sc = mSurfaceView.getSurfaceControl();
            // Create attached choreographer
            sc.getChoreographer();
            assertTrue(sc.hasChoreographer());

            // Use copy constructor
            SurfaceControl copyConstructorSc = new SurfaceControl(sc, "AttachedChoreographerTests");
            //Choreographer isn't copied over.
            assertFalse(copyConstructorSc.hasChoreographer());
            copyConstructorSc.getChoreographer();
            assertTrue(copyConstructorSc.hasChoreographer());
            mTestCompleteSignal.countDown();

            // Use copyFrom
            SurfaceControl copyFromSc = new SurfaceControl();
            copyFromSc.copyFrom(sc, "AttachedChoreographerTests");
            //Choreographer isn't copied over.
            assertFalse(copyFromSc.hasChoreographer());
            copyFromSc.getChoreographer();
            assertTrue(copyFromSc.hasChoreographer());
            mTestCompleteSignal.countDown();
        });
        if (waitForCountDown(mTestCompleteSignal, /* timeoutInSeconds */ 2L)) {
            fail("Test not finished in 2 Seconds");
        }
    }

    @Test
    public void testMirrorSurfaceControl() {
        mScenario.onActivity(activity -> {
            if (waitForCountDown(mSurfaceCreationCountDown, /* timeoutInSeconds */ 1L)) {
                fail("Unable to create surface within 1 Second");
            }
            SurfaceControl sc = mSurfaceView.getSurfaceControl();
            // Create attached choreographer
            sc.getChoreographer();
            assertTrue(sc.hasChoreographer());
            mTestCompleteSignal.countDown();

            // Use mirrorSurface
            SurfaceControl mirrorSc = SurfaceControl.mirrorSurface(sc);
            //Choreographer isn't copied over.
            assertFalse(mirrorSc.hasChoreographer());
            mirrorSc.getChoreographer();
            assertTrue(mirrorSc.hasChoreographer());
            // make SurfaceControl invalid by releasing it.
            mirrorSc.release();

            assertTrue(sc.isValid());
            assertFalse(mirrorSc.isValid());
            assertFalse(mirrorSc.hasChoreographer());
            assertThrows(NullPointerException.class, mirrorSc::getChoreographer);
            mTestCompleteSignal.countDown();
        });
        if (waitForCountDown(mTestCompleteSignal, /* timeoutInSeconds */ 2L)) {
            fail("Test not finished in 2 Seconds");
        }
    }

    @Test
    public void testPostFrameCallback() {
        mScenario.onActivity(activity -> {
            if (waitForCountDown(mSurfaceCreationCountDown, /* timeoutInSeconds */ 1L)) {
                fail("Unable to create surface within 1 Second");
            }
            SurfaceControl sc = mSurfaceView.getSurfaceControl();
            sc.getChoreographer().postFrameCallback(
                    frameTimeNanos -> mTestCompleteSignal.countDown());

            SurfaceControl copySc = new SurfaceControl(sc, "AttachedChoreographerTests");
            Choreographer copyChoreographer = copySc.getChoreographer();
            // make SurfaceControl invalid by releasing it.
            copySc.release();

            assertTrue(sc.isValid());
            assertFalse(copySc.isValid());
            copyChoreographer.postFrameCallback(frameTimeNanos -> mNoCallbackSignal.countDown());
            assertDoesReceiveCallback();
        });
        if (waitForCountDown(mTestCompleteSignal, /* timeoutInSeconds */ 2L)) {
            fail("Test not finished in 2 Seconds");
        }
    }

    @Test
    public void testPostFrameCallbackDelayed() {
        mScenario.onActivity(activity -> {
            if (waitForCountDown(mSurfaceCreationCountDown, /* timeoutInSeconds */ 1L)) {
                fail("Unable to create surface within 1 Second");
            }
            SurfaceControl sc = mSurfaceView.getSurfaceControl();
            sc.getChoreographer(Looper.getMainLooper()).postFrameCallbackDelayed(
                    callback -> mTestCompleteSignal.countDown(),
                    /* delayMillis */ 5);

            SurfaceControl copySc = new SurfaceControl(sc, "AttachedChoreographerTests");
            Choreographer copyChoreographer = copySc.getChoreographer();
            // make SurfaceControl invalid by releasing it.
            copySc.release();

            assertTrue(sc.isValid());
            assertFalse(copySc.isValid());
            copyChoreographer.postFrameCallbackDelayed(
                    frameTimeNanos -> mNoCallbackSignal.countDown(), /* delayMillis */5);
            assertDoesReceiveCallback();
        });
        if (waitForCountDown(mTestCompleteSignal, /* timeoutInSeconds */ 2L)) {
            fail("Test not finished in 2 Seconds");
        }
    }

    @Test
    public void testPostCallback() {
        mScenario.onActivity(activity -> {
            if (waitForCountDown(mSurfaceCreationCountDown, /* timeoutInSeconds */ 1L)) {
                fail("Unable to create surface within 1 Second");
            }
            SurfaceControl sc = mSurfaceView.getSurfaceControl();
            sc.getChoreographer().postCallback(Choreographer.CALLBACK_COMMIT,
                    mTestCompleteSignal::countDown, /* token */ this);

            SurfaceControl copySc = new SurfaceControl(sc, "AttachedChoreographerTests");
            Choreographer copyChoreographer = copySc.getChoreographer();
            // make SurfaceControl invalid by releasing it.
            copySc.release();

            assertTrue(sc.isValid());
            assertFalse(copySc.isValid());
            copyChoreographer.postCallback(Choreographer.CALLBACK_COMMIT,
                    mNoCallbackSignal::countDown, /* token */ this);
            assertDoesReceiveCallback();
        });
        if (waitForCountDown(mTestCompleteSignal, /* timeoutInSeconds */ 2L)) {
            fail("Test not finished in 2 Seconds");
        }
    }

    @Test
    public void testPostCallbackDelayed() {
        mScenario.onActivity(activity -> {
            if (waitForCountDown(mSurfaceCreationCountDown, /* timeoutInSeconds */ 1L)) {
                fail("Unable to create surface within 1 Second");
            }
            SurfaceControl sc = mSurfaceView.getSurfaceControl();
            sc.getChoreographer().postCallbackDelayed(Choreographer.CALLBACK_COMMIT,
                    mTestCompleteSignal::countDown, /* token */ this, /* delayMillis */ 5);

            SurfaceControl copySc = new SurfaceControl(sc, "AttachedChoreographerTests");
            Choreographer copyChoreographer = copySc.getChoreographer();
            // make SurfaceControl invalid by releasing it.
            copySc.release();

            assertTrue(sc.isValid());
            assertFalse(copySc.isValid());
            copyChoreographer.postCallbackDelayed(Choreographer.CALLBACK_COMMIT,
                    mNoCallbackSignal::countDown, /* token */ this, /* delayMillis */ 5);
            assertDoesReceiveCallback();
        });
        if (waitForCountDown(mTestCompleteSignal, /* timeoutInSeconds */ 2L)) {
            fail("Test not finished in 2 Seconds");
        }
    }

    @Test
    public void testPostVsyncCallback() {
        mScenario.onActivity(activity -> {
            if (waitForCountDown(mSurfaceCreationCountDown, /* timeout */ 1L)) {
                fail("Unable to create surface within 1 Second");
            }
            SurfaceControl sc = mSurfaceView.getSurfaceControl();
            sc.getChoreographer().postVsyncCallback(data -> mTestCompleteSignal.countDown());

            SurfaceControl copySc = new SurfaceControl(sc, "AttachedChoreographerTests");
            Choreographer copyChoreographer = copySc.getChoreographer();
            // make SurfaceControl invalid by releasing it.
            copySc.release();

            assertTrue(sc.isValid());
            assertFalse(copySc.isValid());
            copyChoreographer.postVsyncCallback(data -> mNoCallbackSignal.countDown());
            assertDoesReceiveCallback();
        });
        if (waitForCountDown(mTestCompleteSignal, /* timeoutInSeconds */ 2L)) {
            fail("Test not finished in 2 Seconds");
        }
    }

    @Test
    public void testChoreographerDivisorRefreshRate() {
        for (int divisor : new int[]{2, 3}) {
            CountDownLatch continueLatch = new CountDownLatch(1);
            mScenario.onActivity(activity -> {
                if (waitForCountDown(mSurfaceCreationCountDown, /* timeoutInSeconds */ 1L)) {
                    fail("Unable to create surface within 1 Second");
                }
                SurfaceControl sc = mSurfaceView.getSurfaceControl();
                Choreographer choreographer = sc.getChoreographer();
                SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
                float displayRefreshRate = activity.getDisplay().getMode().getRefreshRate();
                float fps = displayRefreshRate / divisor;
                long callbackDurationMs = Math.round(1000 / fps);
                mCallbackMissedCounter = 0;
                transaction.setFrameRate(sc, fps, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
                        .addTransactionCommittedListener(Runnable::run,
                                () -> verifyVsyncCallbacks(choreographer,
                                        callbackDurationMs, continueLatch, FRAME_ITERATIONS))
                        .apply();
            });
            // wait for the previous callbacks to finish before moving to the next divisor
            if (waitForCountDown(continueLatch, /* timeoutInSeconds */ 5L)) {
                fail("Test not finished in 5 Seconds");
            }
        }
    }

    private void verifyVsyncCallbacks(Choreographer choreographer, long callbackDurationMs,
            CountDownLatch continueLatch, int frameCount) {
        long callbackRequestedTimeNs = System.nanoTime();
        choreographer.postVsyncCallback(frameData -> {
            if (frameCount > 0) {
                if (!mIsFirstCallback) {
                    // Skip the first callback as it takes 1 frame
                    // to reflect the new refresh rate
                    long callbackDurationDiffMs = getCallbackDurationDiffInMs(
                            frameData.getFrameTimeNanos(),
                            callbackRequestedTimeNs, callbackDurationMs);
                    if (callbackDurationDiffMs < 0 || callbackDurationDiffMs > THRESHOLD_MS) {
                        mCallbackMissedCounter++;
                        Log.e(TAG, "Frame #" + Math.abs(frameCount - FRAME_ITERATIONS)
                                + " vsync callback failed, expected callback in "
                                + callbackDurationMs
                                + " With threshold of " + THRESHOLD_MS
                                + " but actual duration difference is " + callbackDurationDiffMs);
                    }
                }
                mIsFirstCallback = false;
                verifyVsyncCallbacks(choreographer, callbackDurationMs,
                        continueLatch, frameCount - 1);
            } else {
                assertTrue("Missed timeline for " + mCallbackMissedCounter + " callbacks, while "
                                + CALLBACK_MISSED_THRESHOLD + " missed callbacks are allowed",
                        mCallbackMissedCounter <= CALLBACK_MISSED_THRESHOLD);
                continueLatch.countDown();
            }
        });
    }

    private long getCallbackDurationDiffInMs(long callbackTimeNs, long requestedTimeNs,
            long expectedCallbackMs) {
        long actualTimeMs = TimeUnit.NANOSECONDS.toMillis(callbackTimeNs)
                - TimeUnit.NANOSECONDS.toMillis(requestedTimeNs);
        return Math.abs(expectedCallbackMs - actualTimeMs);
    }

    private boolean waitForCountDown(CountDownLatch countDownLatch, long timeoutInSeconds) {
        try {
            return !countDownLatch.await(timeoutInSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            throw new AssertionError("Test interrupted", ex);
        }
    }

    private int toSwitchingType(int matchContentFrameRateUserPreference) {
        switch (matchContentFrameRateUserPreference) {
            case DisplayManager.MATCH_CONTENT_FRAMERATE_NEVER:
                return DisplayManager.SWITCHING_TYPE_NONE;
            case DisplayManager.MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY:
                return DisplayManager.SWITCHING_TYPE_WITHIN_GROUPS;
            case DisplayManager.MATCH_CONTENT_FRAMERATE_ALWAYS:
                return DisplayManager.SWITCHING_TYPE_ACROSS_AND_WITHIN_GROUPS;
            default:
                return -1;
        }
    }

    private void assertDoesReceiveCallback() {
        try {
            if (mNoCallbackSignal.await(/* timeout */ 50L, TimeUnit.MILLISECONDS)) {
                fail("Callback not supposed to be generated");
            } else {
                mTestCompleteSignal.countDown();
            }
        } catch (InterruptedException e) {
            fail("Callback wait is interrupted " + e);
        }
    }
}
