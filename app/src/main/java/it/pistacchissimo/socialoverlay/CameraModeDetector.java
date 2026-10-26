// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Marco Rinaldi (MarcorOnline)

package it.pistacchissimo.socialoverlay;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** Matches only the camera controls described by an exact community profile. No image capture. */
final class CameraModeDetector {
    enum Mode { UNKNOWN, VIDEO, PHOTO }

    private CameraModeDetector() { }

    static Mode detect(AccessibilityNodeInfo root, JSONObject profile) {
        if (root == null || profile == null) return Mode.UNKNOWN;
        Rect window = new Rect();
        root.getBoundsInScreen(window);
        if (window.isEmpty() || (window.width() < window.height())
                != "portrait".equals(profile.optString("orientation"))) return Mode.UNKNOWN;
        JSONArray rules = profile.optJSONArray("rules");
        Mode found = Mode.UNKNOWN;
        Map<String, List<AccessibilityNodeInfo>> controls = new HashMap<>();
        try {
            for (int i = 0; rules != null && i < rules.length(); i++) {
                JSONObject rule = rules.optJSONObject(i);
                if (rule == null || rule.optString("view_id").isEmpty()) continue;
                Mode mode = "video".equals(rule.optString("mode")) ? Mode.VIDEO
                        : "photo".equals(rule.optString("mode")) ? Mode.PHOTO : Mode.UNKNOWN;
                if (mode == Mode.UNKNOWN) continue;
                String id = rule.optString("view_id");
                // Language alternatives share one lookup and one snapshot of each control.
                if (!controls.containsKey(id)) controls.put(id, root.findAccessibilityNodeInfosByViewId(id));
                List<AccessibilityNodeInfo> nodes = controls.get(id);
                for (AccessibilityNodeInfo node : nodes) {
                    if (!matches(node, rule, window)) continue;
                    if (found != Mode.UNKNOWN && found != mode) return Mode.UNKNOWN;
                    found = mode;
                }
            }
        } finally {
            for (List<AccessibilityNodeInfo> nodes : controls.values()) {
                for (AccessibilityNodeInfo node : nodes) node.recycle();
            }
        }
        return found;
    }

    static boolean matches(AccessibilityNodeInfo node, JSONObject rule, Rect window) {
        if (!node.isVisibleToUser()) return false;
        if (rule.has("text") && !same(node.getText(), rule.optString("text"))) return false;
        if (rule.has("content_description")
                && !same(node.getContentDescription(), rule.optString("content_description"))) return false;
        if (rule.optBoolean("selected", false) && !node.isSelected()) return false;
        JSONArray bounds = rule.optJSONArray("bounds");
        if (bounds != null) {
            Rect position = new Rect();
            node.getBoundsInScreen(position);
            double x = (position.exactCenterX() - window.left) / window.width();
            double y = (position.exactCenterY() - window.top) / window.height();
            if (bounds.length() != 4 || x < bounds.optDouble(0) || y < bounds.optDouble(1)
                    || x > bounds.optDouble(2) || y > bounds.optDouble(3)) return false;
        }
        return true;
    }

    private static boolean same(CharSequence actual, String expected) {
        return actual != null && actual.toString().trim().equalsIgnoreCase(expected.trim());
    }
}
