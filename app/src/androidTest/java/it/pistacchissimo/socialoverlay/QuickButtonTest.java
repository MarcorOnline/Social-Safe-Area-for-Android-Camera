// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Marco Rinaldi (MarcorOnline)

package it.pistacchissimo.socialoverlay;

import android.app.Instrumentation;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;

import java.util.ArrayList;

/** Exercises the real home control while the overlay service is already running. */
final class QuickButtonTest {
    static void verify(Instrumentation test) {
        MainActivity activity = (MainActivity) test.startActivitySync(
                new Intent(test.getTargetContext(), MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        int originalOffset = MaskMode.verticalOffset(test.getTargetContext());
        try {
            test.waitForIdleSync();
            boolean original = MaskMode.guideVisible(test.getTargetContext());
            MainActivity screen = activity;
            Throwable[] failure = new Throwable[1];
            test.runOnMainSync(() -> {
                try {
                    View root = screen.getWindow().getDecorView();
                    Button show = findButton(root, "Mostra bottone rapido");
                    if (show == null) throw new AssertionError("Home must keep Mostra bottone rapido");
                    show.performClick();
                    show.performClick();
                    if (!"Mostra bottone rapido".contentEquals(show.getText())) {
                        throw new AssertionError("Home changed into mask toggle");
                    }
                    ArrayList<View> positions = new ArrayList<>();
                    root.findViewsWithText(positions, "Sposta verticalmente la maschera",
                            View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
                    SeekBar position = (SeekBar) positions.get(0);
                    position.setProgress(420);
                    findButton(root, "Reset").performClick();
                    if (position.getProgress() != 360
                            || MaskMode.verticalOffset(test.getTargetContext()) != 0) {
                        throw new AssertionError("Reset did not restore slider and saved default");
                    }
                } catch (Throwable error) { failure[0] = error; }
            });
            if (failure[0] != null) throw new AssertionError(failure[0]);
            test.waitForIdleSync();
            if (MaskMode.guideVisible(test.getTargetContext()) != original) {
                throw new AssertionError("Home changed saved mask on/off state");
            }
        } finally {
            test.runOnMainSync(() -> {
                MaskMode.setVerticalOffset(test.getTargetContext(), originalOffset);
                activity.finish();
            });
        }
    }

    private static Button findButton(View view, String text) {
        if (view instanceof Button && text.contentEquals(((Button) view).getText())) {
            return (Button) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = findButton(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
