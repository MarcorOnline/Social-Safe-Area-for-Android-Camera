// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Marco Rinaldi (MarcorOnline)

package it.pistacchissimo.socialoverlay;

import android.content.Context;
import android.content.SharedPreferences;

/** Small persisted state for the deliberately single-purpose Beta. */
final class MaskMode {
    static final String REELS = "reels";
    static final String TIKTOK = "tiktok";
    static final String BOTH = "both";
    private static final String PREFERENCES = "social_overlay";
    private static final String KEY = "mask_mode";
    private static final String BUTTON_X = "button_x";
    private static final String BUTTON_Y = "button_y";
    private static final String GUIDE_VISIBLE = "guide_visible";
    private static final String OPACITY = "opacity";
    private static final String VERTICAL_OFFSET = "vertical_offset";
    private static final String AUTO_PACKAGE = "auto_package";
    private static final String DISMISSED = "dismissed";

    private MaskMode() {
    }

    static String get(Context context) {
        String mode = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(KEY, BOTH);
        return REELS.equals(mode) || TIKTOK.equals(mode) || BOTH.equals(mode) ? mode : BOTH;
    }

    static void set(Context context, String mode) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putString(KEY, mode).apply();
    }

    static String label(String mode) {
        if (REELS.equals(mode)) return "Reels";
        if (TIKTOK.equals(mode)) return "TikTok";
        return "Reels + TikTok";
    }

    static int opacity(Context context) {
        return preferences(context).getInt(OPACITY, 23);
    }

    static void setOpacity(Context context, int value) {
        preferences(context).edit().putInt(OPACITY, Math.max(8, Math.min(70, value))).apply();
    }

    /** Offset in dp: enough travel for different manufacturer camera previews. */
    static int verticalOffset(Context context) {
        return preferences(context).getInt(VERTICAL_OFFSET, DeviceProfiles.defaultOffset(context));
    }

    static boolean hasVerticalOffsetOverride(Context context) {
        return preferences(context).contains(VERTICAL_OFFSET);
    }

    static void resetVerticalOffset(Context context) {
        preferences(context).edit().remove(VERTICAL_OFFSET).apply();
    }

    static void setVerticalOffset(Context context, int value) {
        preferences(context).edit()
                .putInt(VERTICAL_OFFSET, Math.max(-360, Math.min(360, value))).apply();
    }

    static boolean guideVisible(Context context) {
        return preferences(context).getBoolean(GUIDE_VISIBLE, true);
    }

    static void setGuideVisible(Context context, boolean visible) {
        preferences(context).edit().putBoolean(GUIDE_VISIBLE, visible).apply();
    }

    static String autoPackage(Context context) {
        return preferences(context).getString(AUTO_PACKAGE, "");
    }

    static void setAutoPackage(Context context, String packageName) {
        preferences(context).edit().putString(AUTO_PACKAGE, packageName).apply();
    }

    static boolean dismissed(Context context) {
        return preferences(context).getBoolean(DISMISSED, false);
    }

    static void setDismissed(Context context, boolean dismissed) {
        preferences(context).edit().putBoolean(DISMISSED, dismissed).apply();
    }

    static int buttonX(Context context, int maxX, int defaultInset) {
        float saved = preferences(context).getFloat(BUTTON_X, -1f);
        return saved < 0f ? Math.max(0, maxX - defaultInset) : Math.round(saved * maxX);
    }

    static int buttonY(Context context, int maxY) {
        float saved = preferences(context).getFloat(BUTTON_Y, -1f);
        return saved < 0f ? maxY / 2 : Math.round(saved * maxY);
    }

    static void saveButtonPosition(Context context, int x, int y, int maxX, int maxY) {
        preferences(context).edit()
                .putFloat(BUTTON_X, maxX == 0 ? 0f : (float) x / maxX)
                .putFloat(BUTTON_Y, maxY == 0 ? 0f : (float) y / maxY)
                .apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
