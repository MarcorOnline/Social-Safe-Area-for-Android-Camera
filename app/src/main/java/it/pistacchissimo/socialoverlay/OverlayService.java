// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Marco Rinaldi (MarcorOnline)

package it.pistacchissimo.socialoverlay;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.TextView;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/** Owns the two system windows: a non-interactive guide and the quick button. */
public final class OverlayService extends Service {
    private static final String CHANNEL_ID = "safe_area";
    private static final int NOTIFICATION_ID = 7;
    private static final String ACTION_REFRESH = "it.pistacchissimo.socialoverlay.REFRESH";
    private static final String ACTION_AUTOMATIC = "it.pistacchissimo.socialoverlay.AUTOMATIC";
    private static final String ACTION_CLOSE = "it.pistacchissimo.socialoverlay.CLOSE";
    private static volatile boolean running;
    private static volatile boolean guideVisible;
    private static boolean photoMode;

    private WindowManager windowManager;
    private View guide;
    private View toggle;
    private View trash;
    private WindowManager.LayoutParams toggleParams;
    private int buttonMaxX;
    private int buttonMaxY;
    private int trashTop;
    private int touchSlop;
    private float touchDownX;
    private float touchDownY;
    private int buttonDownX;
    private int buttonDownY;
    private boolean dragging;
    private boolean longPressed;
    private boolean overTrash;
    private final Runnable longPress = this::beginLongPress;

    public static boolean isRunning() {
        return running;
    }

    public static void start(Context context) {
        Context app = context.getApplicationContext();
        MaskMode.setDismissed(app, false);
        Intent intent = new Intent(app, OverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(intent); else app.startService(intent);
    }

    static void startAutomatically(Context context) {
        Context app = context.getApplicationContext();
        if (!running && !MaskMode.dismissed(app)) {
            Intent intent = new Intent(app, OverlayService.class).setAction(ACTION_AUTOMATIC);
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(intent); else app.startService(intent);
        }
    }

    static void hideAutomatically(Context context) {
        // Leaving the selected app hides windows without dismissing the next automatic launch.
        context.getApplicationContext().stopService(new Intent(context, OverlayService.class));
    }

    public static void refreshMask(Context context) {
        if (running) {
            context.getApplicationContext().startService(
                    new Intent(context, OverlayService.class).setAction(ACTION_REFRESH));
        }
    }

    static void setPhotoMode(Context context, boolean photo) {
        if (photoMode == photo) return;
        photoMode = photo;
        refreshMask(context);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_CLOSE.equals(intent == null ? null : intent.getAction())) {
            MaskMode.setDismissed(this, true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_AUTOMATIC.equals(intent == null ? null : intent.getAction()) && MaskMode.dismissed(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!running) {
            showWindows();
        }
        running = true;
        updateGuideVisibility();
        if (ACTION_REFRESH.equals(intent == null ? null : intent.getAction()) && guideVisible) {
            guide.invalidate();
        }
        createChannel();
        startForeground(NOTIFICATION_ID, notification());
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        guideVisible = false;
        if (windowManager != null) {
            if (guide != null) {
                try {
                    windowManager.removeViewImmediate(guide);
                } catch (IllegalArgumentException ignored) {
                    // The guide can already be hidden while its toggle remains on screen.
                }
            }
            if (toggle != null) windowManager.removeViewImmediate(toggle);
            if (trash != null) windowManager.removeViewImmediate(trash);
        }
        guide = null;
        toggle = null;
        trash = null;
        toggleParams = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.println("guideVisible=" + guideVisible + " manualEnabled=" + MaskMode.guideVisible(this)
                + " photoMode=" + photoMode);
    }

    private void showWindows() {
        windowManager = getSystemService(WindowManager.class);

        TextView button = new TextView(this);
        button.setText(R.string.quick_button_text);
        button.setTextColor(Color.BLACK);
        button.setTextSize(8);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(getColor(R.color.brand));
        button.setBackground(background);
        button.setElevation(dp(8));
        button.setContentDescription(getString(R.string.quick_button_description));
        button.setOnClickListener(ignored -> toggleGuide());
        button.setOnTouchListener((view, event) -> handleToggleTouch(view, event));
        toggle = button;

        int buttonSize = dp(48);
        Point screen = screenSize();
        buttonMaxX = Math.max(0, screen.x - buttonSize);
        buttonMaxY = Math.max(0, screen.y - buttonSize);
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        toggleParams = new WindowManager.LayoutParams(
                buttonSize, buttonSize, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        toggleParams.gravity = Gravity.TOP | Gravity.START;
        toggleParams.x = MaskMode.buttonX(this, buttonMaxX, dp(16));
        toggleParams.y = MaskMode.buttonY(this, buttonMaxY);
        windowManager.addView(toggle, toggleParams);
    }

    private boolean handleToggleTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = event.getRawX();
                touchDownY = event.getRawY();
                buttonDownX = toggleParams.x;
                buttonDownY = toggleParams.y;
                dragging = false;
                longPressed = false;
                overTrash = false;
                view.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                return true;
            case MotionEvent.ACTION_MOVE:
                int moveX = Math.round(event.getRawX() - touchDownX);
                int moveY = Math.round(event.getRawY() - touchDownY);
                if (!dragging && Math.max(Math.abs(moveX), Math.abs(moveY)) > touchSlop) {
                    dragging = true;
                    view.removeCallbacks(longPress);
                    showTrash();
                }
                if (dragging) {
                    toggleParams.x = clamp(buttonDownX + moveX, 0, buttonMaxX);
                    toggleParams.y = clamp(buttonDownY + moveY, 0, buttonMaxY);
                    windowManager.updateViewLayout(toggle, toggleParams);
                    updateTrashHover();
                }
                return true;
            case MotionEvent.ACTION_UP:
                view.removeCallbacks(longPress);
                if (trash != null && overTrash) {
                    removeTrash();
                    MaskMode.setDismissed(this, true);
                    stopSelf();
                } else if (dragging) {
                    MaskMode.saveButtonPosition(this, toggleParams.x, toggleParams.y,
                            buttonMaxX, buttonMaxY);
                } else {
                    view.performClick();
                }
                removeTrash();
                return true;
            case MotionEvent.ACTION_CANCEL:
                view.removeCallbacks(longPress);
                removeTrash();
                return true;
            default:
                return true;
        }
    }

    private void toggleGuide() {
        MaskMode.setGuideVisible(this, !MaskMode.guideVisible(this));
        updateGuideVisibility();
    }

    private void updateGuideVisibility() {
        boolean visible = MaskMode.guideVisible(this) && !(MaskMode.videoOnly(this) && photoMode);
        if (visible && guide == null) addGuide();
        if (!visible) hideGuide();
        guideVisible = visible;
    }

    private Notification notification() {
        PendingIntent close = PendingIntent.getService(this, 0,
                new Intent(this, OverlayService.class).setAction(ACTION_CLOSE),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_safe_area_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(content)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        R.drawable.ic_safe_area_notification, getString(R.string.close_app), close).build())
                .build();
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.channel_description));
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private void addGuide() {
        guide = new SafeAreaGuideView(this);
        WindowManager.LayoutParams guideParams = new WindowManager.LayoutParams(
                -1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        guideParams.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(guide, guideParams);
    }

    private void hideGuide() {
        if (guide != null) {
            windowManager.removeViewImmediate(guide);
            guide = null;
        }
    }

    private void beginLongPress() {
        longPressed = true;
        dragging = true;
        showTrash();
        updateTrashHover();
    }

    private void showTrash() {
        if (trash != null) return;
        TextView target = new TextView(this);
        target.setText(R.string.trash_target);
        target.setTextColor(Color.WHITE);
        target.setTextSize(12);
        target.setGravity(Gravity.CENTER);
        target.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(130, 25, 40));
        background.setCornerRadius(dp(18));
        target.setBackground(background);
        int height = dp(72);
        Point screen = screenSize();
        trashTop = screen.y - height - dp(20);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(dp(224), height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = dp(20);
        trash = target;
        windowManager.addView(trash, params);
    }

    private void updateTrashHover() {
        if (trash == null) return;
        overTrash = toggleParams.y + dp(24) >= trashTop;
        trash.setAlpha(overTrash ? 1f : .65f);
    }

    private void removeTrash() {
        if (trash == null) return;
        windowManager.removeViewImmediate(trash);
        trash = null;
        longPressed = false;
        overTrash = false;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @SuppressWarnings("deprecation")
    private Point screenSize() {
        Point size = new Point();
        windowManager.getDefaultDisplay().getRealSize(size);
        return size;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
