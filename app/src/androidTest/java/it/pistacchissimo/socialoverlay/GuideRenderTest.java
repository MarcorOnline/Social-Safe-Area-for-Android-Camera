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
        int originalMode = MaskMode.get(context);
        int originalOpacity = MaskMode.opacity(context);
        int originalOffset = MaskMode.verticalOffset(context);
        boolean hadOverride = MaskMode.hasVerticalOffsetOverride(context);
        try {
            DeviceProfilesTest.verify(this);
            MaskSelectionTest.verify(this);
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
                for (int selection = 0; selection < 8; selection++) {
                    MaskMode.set(context, selection);
                    render.eraseColor(Color.TRANSPARENT);
                    guide[0].draw(canvas);
                    check((render.getPixel(500, 338) == Color.rgb(255, 59, 123))
                            == ((selection & MaskMode.REELS) != 0), "independent Reel boundary");
                    check((Color.alpha(render.getPixel(500, 1580)) > 0)
                            == ((selection & MaskMode.TIKTOK) != 0), "independent TikTok mask");
                    check((Color.alpha(render.getPixel(540, 1452)) > 0)
                            == ((selection & MaskMode.SUBTITLES) != 0), "independent subtitle box");
                    check(Color.alpha(render.getPixel(540, 1350)) == 0
                            && Color.alpha(render.getPixel(540, 1554)) == 0
                            && Color.alpha(render.getPixel(250, 1452)) == 0
                            && Color.alpha(render.getPixel(830, 1452)) == 0,
                            "subtitle box leaves a small gap above TikTok and clears its side band");
                    if ((selection & MaskMode.SUBTITLES) != 0) {
                        int fill = render.getPixel(540, 1452);
                        check(Color.alpha(fill) == Math.round(23 * 2.55f)
                                && Color.blue(fill) > Color.green(fill)
                                && Color.green(fill) > Color.red(fill), "translucent blue subtitles");
                        check(render.getPixel(540, 1542) == context.getColor(R.color.mask_subtitles),
                                "subtitle bottom is 24 reference pixels above TikTok boundary");
                    }
                    if (selection == MaskMode.SUBTITLES) {
                        int first = 0;
                        int last = render.getWidth() - 1;
                        while (first < last && Color.alpha(render.getPixel(first, 1452)) == 0) first++;
                        while (last > first && Color.alpha(render.getPixel(last, 1452)) == 0) last--;
                        // Rasterised stroke edges may differ by one pixel due to antialiasing.
                        check(Math.abs(first + last - (render.getWidth() - 1)) <= 1,
                                "subtitle box must be centred on the full screen");
                    }
                }
                result.putString("stream", "PASS: actual window origin=" + origin[0] + "," + origin[1]
                        + "; legacy migration, defaults, checkbox persistence and all 8 mask combinations correct; subtitle box blue and inside safe area; controls, transparency, quick button and vertical reset correct.\n");
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
                if (hadOverride) MaskMode.setVerticalOffset(context, originalOffset);
                else MaskMode.resetVerticalOffset(context);
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
