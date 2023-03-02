/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.telephony.satellite;

import android.annotation.NonNull;
import android.os.Binder;

import com.android.internal.telephony.ILongConsumer;

import java.util.concurrent.Executor;

/**
 * A callback class for listening to satellite datagrams.
 *
 * @hide
 */
public class SatelliteDatagramCallback {
    private final CallbackBinder mBinder = new CallbackBinder(this);

    private static class CallbackBinder extends ISatelliteDatagramCallback.Stub {
        private final SatelliteDatagramCallback mLocalCallback;
        private Executor mExecutor;

        private CallbackBinder(SatelliteDatagramCallback localCallback) {
            mLocalCallback = localCallback;
        }

        @Override
        public void onSatelliteDatagramReceived(long datagramId,
                @NonNull SatelliteDatagram datagram, int pendingCount,
                @NonNull ILongConsumer callback) {
            final long callingIdentity = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> mLocalCallback.onSatelliteDatagramReceived(datagramId,
                        datagram, pendingCount, callback));
            } finally {
                restoreCallingIdentity(callingIdentity);
            }
        }

        private void setExecutor(Executor executor) {
            mExecutor = executor;
        }
    }

    /**
     * Called when there is an incoming datagram to be received.
     * @param datagramId An id that uniquely identifies incoming datagram.
     * @param datagram Datagram to be received over satellite.
     * @param pendingCount Number of datagrams yet to be received by the app.
     * @param callback This callback will be used by datagram receiver app to send received
     *                 datagramId to Telephony. If the callback is not received within five minutes,
     *                 Telephony will resend the datagram.
     */
    public void onSatelliteDatagramReceived(long datagramId, @NonNull SatelliteDatagram datagram,
            int pendingCount, @NonNull ILongConsumer callback) {
        // Base Implementation
    }

    /** @hide */
    @NonNull
    final ISatelliteDatagramCallback getBinder() {
        return mBinder;
    }

    /** @hide */
    public void setExecutor(@NonNull Executor executor) {
        mBinder.setExecutor(executor);
    }
}
