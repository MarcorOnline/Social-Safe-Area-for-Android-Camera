// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Marco Rinaldi (MarcorOnline)

package it.pistacchissimo.socialoverlay;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

/** Checks upgrades and the actual independent controls without clearing other user settings. */
final class MaskSelectionTest {
    static void verify(Instrumentation test) {
        Context context = test.getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences("social_overlay", Context.MODE_PRIVATE);
        boolean hadSelection = prefs.contains("mask_selection");
        int original = MaskMode.get(context);
        boolean hadLegacy = prefs.contains("mask_mode");
        String legacy = prefs.getString("mask_mode", "both");
        MainActivity[] activity = new MainActivity[1];
        try {
            prefs.edit().remove("mask_selection").remove("mask_mode").commit();
            check(MaskMode.get(context) == MaskMode.BOTH, "fresh install enables only Reel and TikTok");
            String[] oldModes = {"reels", "tiktok", "both", "unknown"};
            int[] expected = {MaskMode.REELS, MaskMode.TIKTOK, MaskMode.BOTH, MaskMode.BOTH};
            for (int i = 0; i < oldModes.length; i++) {
                prefs.edit().putString("mask_mode", oldModes[i]).commit();
                check(MaskMode.get(context) == expected[i], "preserve legacy selection");
            }
            MaskMode.set(context, MaskMode.BOTH);
            activity[0] = open(test, context);
            for (int selection = 0; selection < 8; selection++) {
                int mode = selection;
                onMain(test, () -> {
                    View root = activity[0].getWindow().getDecorView();
                    setCheckbox(root, context.getString(R.string.reels), (mode & MaskMode.REELS) != 0);
                    setCheckbox(root, context.getString(R.string.tiktok), (mode & MaskMode.TIKTOK) != 0);
                    setCheckbox(root, context.getString(R.string.subtitles), (mode & MaskMode.SUBTITLES) != 0);
                    check(MaskMode.get(context) == mode, "checkboxes save every combination, including none");
                    activity[0].finish();
                });
                activity[0] = open(test, context);
                onMain(test, () -> {
                    View root = activity[0].getWindow().getDecorView();
                    check(find(root, context.getString(R.string.reels)).isChecked()
                            == ((mode & MaskMode.REELS) != 0), "Reel restored");
                    check(find(root, context.getString(R.string.tiktok)).isChecked()
                            == ((mode & MaskMode.TIKTOK) != 0), "TikTok restored");
                    check(find(root, context.getString(R.string.subtitles)).isChecked()
                            == ((mode & MaskMode.SUBTITLES) != 0), "subtitles restored");
                });
            }
        } finally {
            test.runOnMainSync(() -> {
                if (activity[0] != null) activity[0].finish();
            });
            SharedPreferences.Editor restore = prefs.edit();
            if (hadSelection) restore.putInt("mask_selection", original);
            else restore.remove("mask_selection");
            if (hadLegacy) restore.putString("mask_mode", legacy);
            else restore.remove("mask_mode");
            restore.commit();
        }
    }

    private static MainActivity open(Instrumentation test, Context context) {
        MainActivity activity = (MainActivity) test.startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        test.waitForIdleSync();
        return activity;
    }

    private static void setCheckbox(View root, String text, boolean checked) {
        CheckBox checkbox = find(root, text);
        check(checkbox != null, "missing checkbox: " + text);
        if (checkbox.isChecked() != checked) checkbox.performClick();
    }

    private static CheckBox find(View view, String text) {
        if (view instanceof CheckBox && text.contentEquals(((CheckBox) view).getText())) return (CheckBox) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                CheckBox found = find(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void onMain(Instrumentation test, Runnable action) {
        Throwable[] failure = new Throwable[1];
        test.runOnMainSync(() -> {
            try { action.run(); } catch (Throwable error) { failure[0] = error; }
        });
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
