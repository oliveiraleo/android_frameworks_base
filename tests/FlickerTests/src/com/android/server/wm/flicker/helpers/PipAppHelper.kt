/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.wm.flicker.helpers

import android.app.Instrumentation
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.util.Log
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.android.server.wm.flicker.testapp.ActivityOptions
import com.android.server.wm.traces.common.Rect
import com.android.server.wm.traces.common.WindowManagerConditionsFactory
import com.android.server.wm.traces.common.region.Region
import com.android.server.wm.traces.parser.toFlickerComponent
import com.android.server.wm.traces.parser.windowmanager.WindowManagerStateHelper

open class PipAppHelper(instrumentation: Instrumentation) : StandardAppHelper(
    instrumentation,
    ActivityOptions.Pip.LABEL,
    ActivityOptions.Pip.COMPONENT.toFlickerComponent()
) {
    private val mediaSessionManager: MediaSessionManager
        get() = context.getSystemService(MediaSessionManager::class.java)
            ?: error("Could not get MediaSessionManager")

    private val mediaController: MediaController?
        get() = mediaSessionManager.getActiveSessions(null).firstOrNull {
            it.packageName == `package`
        }

    open fun clickObject(resId: String) {
        val selector = By.res(`package`, resId)
        val obj = uiDevice.findObject(selector) ?: error("Could not find `$resId` object")

        obj.click()
    }

    /**
     * Launches the app through an intent instead of interacting with the launcher and waits
     * until the app window is in PIP mode
     */
    @JvmOverloads
    fun launchViaIntentAndWaitForPip(
        wmHelper: WindowManagerStateHelper,
        expectedWindowName: String = "",
        action: String? = null,
        stringExtras: Map<String, String>
    ) {
        launchViaIntentAndWaitShown(
            wmHelper, expectedWindowName, action, stringExtras,
            waitConditions = arrayOf(WindowManagerConditionsFactory.hasPipWindow())
        )

        wmHelper.StateSyncBuilder()
            .withPipShown()
            .waitForAndVerify()
    }

    /**
     * Expand the PIP window back to full screen via intent and wait until the app is visible
     */
    fun exitPipToFullScreenViaIntent(wmHelper: WindowManagerStateHelper) =
        launchViaIntentAndWaitShown(wmHelper)

    fun clickEnterPipButton(wmHelper: WindowManagerStateHelper) {
        clickObject(ENTER_PIP_BUTTON_ID)

        // Wait on WMHelper or simply wait for 3 seconds
        wmHelper.StateSyncBuilder()
            .withPipShown()
            .waitForAndVerify()
        // when entering pip, the dismiss button is visible at the start. to ensure the pip
        // animation is complete, wait until the pip dismiss button is no longer visible.
        // b/176822698: dismiss-only state will be removed in the future
        uiDevice.wait(Until.gone(By.res(SYSTEMUI_PACKAGE, "dismiss")), FIND_TIMEOUT)
    }

    fun enableEnterPipOnUserLeaveHint() {
        clickObject(ENTER_PIP_ON_USER_LEAVE_HINT)
    }

    fun enableAutoEnterForPipActivity() {
        clickObject(ENTER_PIP_AUTOENTER)
    }

    fun clickStartMediaSessionButton() {
        clickObject(MEDIA_SESSION_START_RADIO_BUTTON_ID)
    }

    fun checkWithCustomActionsCheckbox() = uiDevice
        .findObject(By.res(`package`, WITH_CUSTOM_ACTIONS_BUTTON_ID))
        ?.takeIf { it.isCheckable }
        ?.apply { if (!isChecked) clickObject(WITH_CUSTOM_ACTIONS_BUTTON_ID) }
        ?: error("'With custom actions' checkbox not found")

    fun pauseMedia() = mediaController?.transportControls?.pause()
        ?: error("No active media session found")

    fun stopMedia() = mediaController?.transportControls?.stop()
        ?: error("No active media session found")

    @Deprecated(
        "Use PipAppHelper.closePipWindow(wmHelper) instead",
        ReplaceWith("closePipWindow(wmHelper)")
    )
    open fun closePipWindow() {
        closePipWindow(WindowManagerStateHelper(mInstrumentation))
    }

    private fun getWindowRect(wmHelper: WindowManagerStateHelper): Rect {
        val windowRegion = wmHelper.getWindowRegion(this)
        require(!windowRegion.isEmpty) {
            "Unable to find a PIP window in the current state"
        }
        return windowRegion.bounds
    }

    /**
     * Taps the pip window and dismisses it by clicking on the X button.
     */
    open fun closePipWindow(wmHelper: WindowManagerStateHelper) {
        val windowRect = getWindowRect(wmHelper)
        uiDevice.click(windowRect.centerX(), windowRect.centerY())
        // search and interact with the dismiss button
        val dismissSelector = By.res(SYSTEMUI_PACKAGE, "dismiss")
        uiDevice.wait(Until.hasObject(dismissSelector), FIND_TIMEOUT)
        val dismissPipObject = uiDevice.findObject(dismissSelector)
            ?: error("PIP window dismiss button not found")
        val dismissButtonBounds = dismissPipObject.visibleBounds
        uiDevice.click(dismissButtonBounds.centerX(), dismissButtonBounds.centerY())

        // Wait for animation to complete.
        wmHelper.StateSyncBuilder()
            .withPipGone()
            .withHomeActivityVisible()
            .waitForAndVerify()
    }

    /**
     * Close the pip window by pressing the expand button
     */
    fun expandPipWindowToApp(wmHelper: WindowManagerStateHelper) {
        val windowRect = getWindowRect(wmHelper)
        uiDevice.click(windowRect.centerX(), windowRect.centerY())
        // search and interact with the expand button
        val expandSelector = By.res(SYSTEMUI_PACKAGE, "expand_button")
        uiDevice.wait(Until.hasObject(expandSelector), FIND_TIMEOUT)
        val expandPipObject = uiDevice.findObject(expandSelector)
            ?: error("PIP window expand button not found")
        val expandButtonBounds = expandPipObject.visibleBounds
        uiDevice.click(expandButtonBounds.centerX(), expandButtonBounds.centerY())
        wmHelper.StateSyncBuilder()
            .withPipGone()
            .withFullScreenApp(this)
            .waitForAndVerify()
    }

    /**
     * Double click on the PIP window to expand it
     */
    fun doubleClickPipWindow(wmHelper: WindowManagerStateHelper) {
        val windowRect = getWindowRect(wmHelper)
        Log.d(TAG, "First click")
        uiDevice.click(windowRect.centerX(), windowRect.centerY())
        Log.d(TAG, "Second click")
        uiDevice.click(windowRect.centerX(), windowRect.centerY())
        Log.d(TAG, "Wait for app transition to end")
        wmHelper.StateSyncBuilder()
            .withAppTransitionIdle()
            .waitForAndVerify()
        waitForPipWindowToExpandFrom(wmHelper, Region.from(windowRect))
    }

    private fun waitForPipWindowToExpandFrom(
        wmHelper: WindowManagerStateHelper,
        windowRect: Region
    ) {
        wmHelper.StateSyncBuilder().add("pipWindowExpanded") {
            val pipAppWindow = it.wmState.visibleWindows.firstOrNull { window ->
                this.windowMatchesAnyOf(window)
            } ?: return@add false
            val pipRegion = pipAppWindow.frameRegion
            return@add pipRegion.coversMoreThan(windowRect)
        }.waitForAndVerify()
    }

    companion object {
        private const val TAG = "PipAppHelper"
        private const val ENTER_PIP_BUTTON_ID = "enter_pip"
        private const val WITH_CUSTOM_ACTIONS_BUTTON_ID = "with_custom_actions"
        private const val MEDIA_SESSION_START_RADIO_BUTTON_ID = "media_session_start"
        private const val ENTER_PIP_ON_USER_LEAVE_HINT = "enter_pip_on_leave_manual"
        private const val ENTER_PIP_AUTOENTER = "enter_pip_on_leave_autoenter"
    }
}
