// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Marco Rinaldi (MarcorOnline)

package it.pistacchissimo.socialoverlay;

import android.app.Instrumentation;
import android.app.Activity;
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
        boolean hadOverride = MaskMode.hasVerticalOffsetOverride(test.getTargetContext());
        Instrumentation.ActivityMonitor aboutMonitor = test.addMonitor(AboutActivity.class.getName(), null, false);
        Activity[] about = new Activity[1];
        try {
            test.waitForIdleSync();
            boolean original = MaskMode.guideVisible(test.getTargetContext());
            MainActivity screen = activity;
            Throwable[] failure = new Throwable[1];
            test.runOnMainSync(() -> {
                try {
                    View root = screen.getWindow().getDecorView();
                    String showText = screen.getString(R.string.show_quick_button);
                    Button show = findButton(root, showText);
                    if (show == null) throw new AssertionError("Home must keep Mostra bottone rapido");
                    show.performClick();
                    show.performClick();
                    if (!showText.contentEquals(show.getText())) {
                        throw new AssertionError("Home changed into mask toggle");
                    }
                    ArrayList<View> positions = new ArrayList<>();
                    root.findViewsWithText(positions, screen.getString(R.string.vertical_picker_description),
                            View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
                    SeekBar position = (SeekBar) positions.get(0);
                    MaskMode.setVerticalOffset(screen, 60);
                    position.setProgress(420);
                    findButton(root, screen.getString(R.string.reset)).performClick();
                    int expected = DeviceProfiles.defaultOffset(screen);
                    if (position.getProgress() != 360 + expected
                            || MaskMode.verticalOffset(screen) != expected
                            || MaskMode.hasVerticalOffsetOverride(screen)) {
                        throw new AssertionError("Reset did not restore slider and saved default");
                    }
                    ArrayList<View> info = new ArrayList<>();
                    root.findViewsWithText(info, screen.getString(R.string.information),
                            View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
                    if (info.isEmpty()) throw new AssertionError("Missing information button");
                    info.get(0).performClick();
                } catch (Throwable error) { failure[0] = error; }
            });
            if (failure[0] != null) throw new AssertionError(failure[0]);
            about[0] = test.waitForMonitorWithTimeout(aboutMonitor, 5000);
            if (about[0] == null) throw new AssertionError("Information did not open attribution page");
            test.runOnMainSync(() -> {
                ArrayList<View> attribution = new ArrayList<>();
                View root = about[0].getWindow().getDecorView();
                root.findViewsWithText(attribution, about[0].getString(R.string.about_creator), View.FIND_VIEWS_WITH_TEXT);
                if (attribution.isEmpty() || findButton(root, about[0].getString(R.string.project_github)) == null
                        || findButton(root, about[0].getString(R.string.pistacchissimo_article)) == null
                        || findButton(root, about[0].getString(R.string.pistacchissimo_instagram)) == null
                        || findButton(root, about[0].getString(R.string.contact_instagram)) == null) {
                    failure[0] = new AssertionError("Missing creator or source attribution");
                }
            });
            if (failure[0] != null) throw new AssertionError(failure[0]);
            test.waitForIdleSync();
            if (MaskMode.guideVisible(test.getTargetContext()) != original) {
                throw new AssertionError("Home changed saved mask on/off state");
            }
        } finally {
            test.removeMonitor(aboutMonitor);
            test.runOnMainSync(() -> {
                if (about[0] != null) about[0].finish();
                if (hadOverride) MaskMode.setVerticalOffset(test.getTargetContext(), originalOffset);
                else MaskMode.resetVerticalOffset(test.getTargetContext());
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
