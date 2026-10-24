// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Marco Rinaldi (MarcorOnline)

package it.pistacchissimo.socialoverlay;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;

/** Renders the actual overlay window on the connected 1080x2340 reference device. */
public final class GuideRenderTest extends Instrumentation {
    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        Context context = getTargetContext();
        WindowManager manager = context.getSystemService(WindowManager.class);
        SafeAreaGuideView[] guide = new SafeAreaGuideView[1];
        String originalMode = MaskMode.get(context);
        int originalOpacity = MaskMode.opacity(context);
        int originalOffset = MaskMode.verticalOffset(context);
        try {
            QuickButtonTest.verify(this);
            onMain(() -> {
                MaskMode.set(context, MaskMode.BOTH);
                MaskMode.setOpacity(context, 23);
                MaskMode.setVerticalOffset(context, 0);
                guide[0] = new SafeAreaGuideView(context);
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        -1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                params.gravity = Gravity.TOP | Gravity.START;
                manager.addView(guide[0], params);
            });
            waitForIdleSync();
            onMain(() -> {
                Bitmap render = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(render);
                int[] origin = new int[2];
                guide[0].getLocationOnScreen(origin);
                canvas.translate(origin[0], origin[1]);
                guide[0].draw(canvas);
                check(Color.alpha(render.getPixel(500, 180)) == 0, "top controls clear");
                check(Color.alpha(render.getPixel(500, 190)) > 0, "mask starts at video y=184");
                check(Color.alpha(render.getPixel(500, 2090)) > 0, "mask reaches video bottom");
                check(Color.alpha(render.getPixel(500, 2110)) == 0, "bottom controls clear");
                check(Color.alpha(render.getPixel(500, 1000)) == 0, "safe area clear");
                check(Color.alpha(render.getPixel(900, 1020)) > 0, "right-hand unsafe band");
                check(Color.alpha(render.getPixel(900, 930)) == 0, "right band starts at reference height");
                check(Color.alpha(render.getPixel(500, 1500)) == 0, "lower band not compressed upwards");
                check(Color.alpha(render.getPixel(500, 1620)) > 0, "lower band at reference height");
                check(Color.alpha(render.getPixel(500, 200)) < 128, "strong transparency retained");
                check(render.getPixel(500, 338) == Color.rgb(255, 59, 123),
                        "Reels top boundary matches GIUSTO");
                check(render.getPixel(500, 357) == Color.rgb(37, 244, 238),
                        "TikTok top boundary matches GIUSTO");
                check(render.getPixel(500, 1566) == Color.rgb(37, 244, 238),
                        "TikTok lower boundary matches GIUSTO");
                check(render.getPixel(500, 1604) == Color.rgb(255, 59, 123),
                        "Reels lower boundary matches GIUSTO");
                result.putString("stream", "PASS: actual window origin=" + origin[0] + "," + origin[1]
                        + "; 9:16 mask at [0,184]-[1080,2104], controls clear, L geometry and transparency correct; home only shows quick button; vertical reset saves default.\n");
                render.recycle();
            });
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + error + "\n");
        } finally {
            runOnMainSync(() -> {
                if (guide[0] != null && guide[0].isAttachedToWindow()) {
                    manager.removeViewImmediate(guide[0]);
                }
                MaskMode.set(context, originalMode);
                MaskMode.setOpacity(context, originalOpacity);
                MaskMode.setVerticalOffset(context, originalOffset);
            });
        }
        finish(result.getString("stream", "").startsWith("PASS") ? -1 : 0, result);
    }

    private void onMain(Runnable action) {
        Throwable[] failure = new Throwable[1];
        runOnMainSync(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure[0] = error;
            }
        });
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
