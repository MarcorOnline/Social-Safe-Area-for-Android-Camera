// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Marco Rinaldi (MarcorOnline)

package it.pistacchissimo.socialoverlay;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PatternMatcher;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;

/** Exercises cancellation, affirmative consent and the real privacy link without leaving the app. */
public final class PrivacyFlowTest extends Instrumentation {
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        Context context = getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences("social_overlay", Context.MODE_PRIVATE);
        boolean hadConsent = prefs.contains(MaskMode.ACCESSIBILITY_CONSENT);
        boolean originalConsent = MaskMode.accessibilityConsent(context);
        Activity[] activity = new Activity[1];
        IntentFilter settings = new IntentFilter("android.settings.ACCESSIBILITY_DETAILS_SETTINGS");
        settings.addAction(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        ActivityMonitor settingsMonitor = addMonitor(settings,
                new ActivityResult(Activity.RESULT_CANCELED, null), true);
        IntentFilter privacy = new IntentFilter(Intent.ACTION_VIEW);
        privacy.addDataScheme("https");
        privacy.addDataAuthority("www.pistacchissimo.it", null);
        privacy.addDataPath("/news/social-safe-area-lapp-per-girare-reels-e-tiktok-migliori-gratis/",
                PatternMatcher.PATTERN_LITERAL);
        ActivityMonitor privacyMonitor = addMonitor(privacy,
                new ActivityResult(Activity.RESULT_CANCELED, null), true);
        int resultCode = Activity.RESULT_CANCELED;
        try {
            prefs.edit().remove(MaskMode.ACCESSIBILITY_CONSENT).commit();
            activity[0] = startActivitySync(new Intent(context, MainActivity.class)
                    .setAction(Intent.ACTION_MAIN)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            click(context.getString(R.string.automatic_opening));
            click(context.getString(R.string.enable_detection));
            awaitText(context.getString(R.string.accessibility_disclosure_title)).recycle();
            awaitText(context.getString(R.string.accessibility_disclosure)).recycle();
            check(!MaskMode.accessibilityConsent(context) && settingsMonitor.getHits() == 0,
                    "show disclosure before saving consent or opening settings");
            click(context.getString(android.R.string.cancel));
            check(!MaskMode.accessibilityConsent(context) && settingsMonitor.getHits() == 0,
                    "cancelling must not grant consent or open settings");
            click(context.getString(R.string.enable_detection));
            click(context.getString(R.string.accessibility_continue));
            waitForIdleSync();
            check(MaskMode.accessibilityConsent(context) && settingsMonitor.getHits() == 1,
                    "only affirmative consent opens Android settings");
            runOnMainSync(() -> activity[0].finish());
            activity[0] = startActivitySync(new Intent(context, AboutActivity.class)
                    .setAction(Intent.ACTION_MAIN)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(() -> {
                Button link = findButton(activity[0].getWindow().getDecorView(),
                        context.getString(R.string.privacy_policy));
                check(link != null, "privacy link is present in About");
                link.performClick();
            });
            check(privacyMonitor.getHits() == 1, "privacy link opens the published HTTPS policy");
            result.putString("result", "PASS: disclosure, cancellation, affirmative consent and privacy link");
            resultCode = Activity.RESULT_OK;
        } catch (Throwable error) {
            result.putString("result", "FAIL: " + error);
        } finally {
            removeMonitor(settingsMonitor);
            removeMonitor(privacyMonitor);
            runOnMainSync(() -> {
                if (activity[0] != null) activity[0].finish();
            });
            SharedPreferences.Editor restore = prefs.edit();
            if (hadConsent) restore.putBoolean(MaskMode.ACCESSIBILITY_CONSENT, originalConsent);
            else restore.remove(MaskMode.ACCESSIBILITY_CONSENT);
            restore.commit();
        }
        finish(resultCode, result);
    }

    private void click(String text) {
        AccessibilityNodeInfo node = awaitText(text);
        try {
            check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK), "click " + text);
        } finally {
            node.recycle();
        }
        waitForIdleSync();
    }

    private AccessibilityNodeInfo awaitText(String text) {
        long deadline = SystemClock.uptimeMillis() + 10000;
        do {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                try {
                    for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
                        if (node.getText() != null && text.equalsIgnoreCase(node.getText().toString())) return node;
                        node.recycle();
                    }
                } finally {
                    root.recycle();
                }
            }
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Visible text missing: " + text);
    }

    private static Button findButton(View view, String text) {
        if (view instanceof Button && text.contentEquals(((Button) view).getText())) return (Button) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = findButton(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
