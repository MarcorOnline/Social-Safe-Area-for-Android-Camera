// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Marco Rinaldi (MarcorOnline)

package it.pistacchissimo.socialoverlay;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/** Window following is always event-based; optional mode detection reads only the selected camera. */
public final class AppWatchAccessibilityService extends AccessibilityService
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences preferences;
    private JSONObject modeProfile;
    private boolean cameraForeground;
    private boolean readPending;
    private boolean unknownPending;
    private long nodeReads;
    private CameraModeDetector.Mode lastMode = CameraModeDetector.Mode.UNKNOWN;
    private final Runnable releaseUnknown = () -> {
        unknownPending = false;
        OverlayService.setPhotoMode(this, false);
    };
    private final Runnable readMode = () -> {
        readPending = false;
        if (!MaskMode.accessibilityConsent(this) || !cameraForeground
                || modeProfile == null || !MaskMode.videoOnly(this)) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        nodeReads++;
        CameraModeDetector.Mode mode = CameraModeDetector.Mode.UNKNOWN;
        try {
            if (root != null && root.getPackageName() != null
                    && MaskMode.autoPackage(this).contentEquals(root.getPackageName())) {
                mode = CameraModeDetector.detect(root, modeProfile);
            }
        } catch (IllegalStateException ignored) {
            // A camera window can disappear while Android is retrieving its nodes.
        } finally {
            if (root != null) root.recycle();
        }
        if (mode == CameraModeDetector.Mode.UNKNOWN) {
            // Brief transitions keep the previous state; stale photo detection eventually releases it.
            if (!unknownPending) {
                unknownPending = true;
                handler.postDelayed(releaseUnknown, 1500);
            }
        } else {
            handler.removeCallbacks(releaseUnknown);
            unknownPending = false;
            OverlayService.setPhotoMode(this, mode == CameraModeDetector.Mode.PHOTO);
        }
        lastMode = mode;
    };

    @Override
    protected void onServiceConnected() {
        preferences = getSharedPreferences("social_overlay", MODE_PRIVATE);
        preferences.registerOnSharedPreferenceChangeListener(this);
        configureModeDetection();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if ("experimental_video_only".equals(key) || "auto_package".equals(key)
                || MaskMode.ACCESSIBILITY_CONSENT.equals(key)) configureModeDetection();
    }

    private void configureModeDetection() {
        resetModeDetection();
        boolean consent = MaskMode.accessibilityConsent(this);
        modeProfile = consent && MaskMode.videoOnly(this) ? DeviceProfiles.modeDetection(this) : null;
        AccessibilityServiceInfo info = getServiceInfo();
        info.eventTypes = consent ? AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED : 0;
        info.flags &= ~AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        if (modeProfile != null) {
            info.eventTypes |= AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    | AccessibilityEvent.TYPE_VIEW_SELECTED | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        }
        setServiceInfo(info);
        // No node retrieval at all when the experimental feature is off or unsupported.
        if (modeProfile == null) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        try {
            cameraForeground = root != null && root.getPackageName() != null
                    && MaskMode.autoPackage(this).contentEquals(root.getPackageName());
        } finally {
            if (root != null) root.recycle();
        }
        if (cameraForeground) scheduleRead();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!MaskMode.accessibilityConsent(this)) return;
        CharSequence source = event.getPackageName();
        String selected = MaskMode.autoPackage(this);
        if (source == null || selected.isEmpty()) return;
        // Overlay windows are ours too: they must not clear camera detection or feed it back.
        if (getPackageName().contentEquals(source)) {
            if (MainActivity.class.getName().contentEquals(event.getClassName() == null ? "" : event.getClassName())) {
                cameraForeground = false;
                resetModeDetection();
            }
            return;
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.isFullScreen()) {
            cameraForeground = selected.contentEquals(source);
            if (!cameraForeground) resetModeDetection();
            // Partial popups/notification panels must not look like leaving the camera.
            if (cameraForeground) OverlayService.startAutomatically(this);
            else OverlayService.hideAutomatically(this);
        }
        if (cameraForeground && selected.contentEquals(source) && modeProfile != null) scheduleRead();
    }

    private void scheduleRead() {
        if (readPending) return;
        readPending = true;
        // Coalesce event bursts instead of scanning on every event or polling on a timer.
        handler.postDelayed(readMode, 200);
    }

    private void resetModeDetection() {
        handler.removeCallbacks(readMode);
        handler.removeCallbacks(releaseUnknown);
        readPending = false;
        unknownPending = false;
        lastMode = CameraModeDetector.Mode.UNKNOWN;
        OverlayService.setPhotoMode(this, false);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.println("modeDetection=" + (modeProfile != null) + " cameraForeground=" + cameraForeground
                + " mode=" + lastMode + " nodeReads=" + nodeReads);
    }

    @Override
    public void onInterrupt() {
        resetModeDetection();
    }

    @Override
    public void onDestroy() {
        if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(this);
        resetModeDetection();
        super.onDestroy();
    }
}
