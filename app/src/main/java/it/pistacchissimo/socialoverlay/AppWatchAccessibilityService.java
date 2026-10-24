// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Marco Rinaldi (MarcorOnline)

package it.pistacchissimo.socialoverlay;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/** Uses full-screen window transitions without reading the screen's contents. */
public final class AppWatchAccessibilityService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || !event.isFullScreen()) return;
        CharSequence source = event.getPackageName();
        String selected = MaskMode.autoPackage(this);
        if (source == null || selected.isEmpty()) return;
        // Ignore our own home and overlay events. Non-full-screen popups/notification
        // panels must not make the selected app appear to close.
        if (getPackageName().contentEquals(source)) return;
        if (selected.contentEquals(source)) {
            OverlayService.startAutomatically(this);
        } else {
            OverlayService.hideAutomatically(this);
        }
    }

    @Override
    public void onInterrupt() {
        // No spoken feedback: this service only watches the app selected by its owner.
    }
}
