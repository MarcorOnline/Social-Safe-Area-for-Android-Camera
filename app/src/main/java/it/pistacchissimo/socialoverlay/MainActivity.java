// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Marco Rinaldi (MarcorOnline)

package it.pistacchissimo.socialoverlay;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

/** The deliberately small control screen: Android owns the overlay permission flow. */
public final class MainActivity extends Activity {
    private static final int OVERLAY_PERMISSION_REQUEST = 100;
    private Button showButton;
    private TextView modeStatus;
    private TextView opacityStatus;
    private TextView positionStatus;
    private TextView autoStatus;
    private Button updateButton;
    private final Consumer<UpdateChecker.Release> updateResult = this::showUpdate;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        DeviceProfiles.defaultOffset(this);
        setContentView(createContent());
        if (Settings.canDrawOverlays(this)) OverlayService.start(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        UpdateChecker.check(this, updateResult);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQUEST && Settings.canDrawOverlays(this)) {
            OverlayService.start(this);
        }
    }

    private View createContent() {
        int padding = dp(28);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getColor(R.color.surface));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(padding, padding, padding, padding);
        content.setBackgroundColor(getColor(R.color.surface));

        modeStatus = new TextView(this);
        modeStatus.setTextColor(getColor(R.color.on_surface));
        modeStatus.setTextSize(12);
        content.addView(modeStatus);

        TextView title = new TextView(this);
        title.setText(R.string.home_title);
        title.setTextColor(getColor(R.color.brand));
        title.setTextSize(34);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        content.addView(title);

        TextView description = new TextView(this);
        description.setText(R.string.description);
        description.setTextColor(getColor(R.color.on_surface));
        description.setTextSize(17);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.topMargin = dp(18);
        content.addView(description, descriptionParams);

        updateButton = new Button(this, null, android.R.attr.borderlessButtonStyle);
        updateButton.setAllCaps(false);
        updateButton.setVisibility(View.GONE);
        content.addView(updateButton, new LinearLayout.LayoutParams(-1, -2));

        TextView modeLabel = new TextView(this);
        modeLabel.setText(R.string.mask_label);
        modeLabel.setTextColor(getColor(R.color.on_surface));
        modeLabel.setTextSize(15);
        LinearLayout.LayoutParams modeLabelParams = new LinearLayout.LayoutParams(-1, -2);
        modeLabelParams.topMargin = dp(28);
        content.addView(modeLabel, modeLabelParams);

        addMaskCheckbox(content, R.string.reels, R.color.mask_reels, MaskMode.REELS);
        addMaskCheckbox(content, R.string.tiktok, R.color.mask_tiktok, MaskMode.TIKTOK);
        addMaskCheckbox(content, R.string.subtitles, R.color.mask_subtitles, MaskMode.SUBTITLES);

        opacityStatus = sectionLabel(content, getString(R.string.opacity_label));
        SeekBar opacity = new SeekBar(this);
        opacity.setMax(62);
        opacity.setProgress(MaskMode.opacity(this) - 8);
        opacity.setContentDescription(getString(R.string.opacity_picker_description));
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                MaskMode.setOpacity(MainActivity.this, progress + 8);
                OverlayService.refreshMask(MainActivity.this);
                refresh();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        content.addView(opacity, new LinearLayout.LayoutParams(-1, -2));

        positionStatus = sectionLabel(content, getString(R.string.vertical_label));
        SeekBar verticalPosition = new SeekBar(this);
        verticalPosition.setMax(720);
        verticalPosition.setProgress(MaskMode.verticalOffset(this) + 360);
        verticalPosition.setContentDescription(getString(R.string.vertical_picker_description));
        verticalPosition.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) MaskMode.setVerticalOffset(MainActivity.this, progress - 360);
                OverlayService.refreshMask(MainActivity.this);
                refresh();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        LinearLayout positionControls = new LinearLayout(this);
        positionControls.setGravity(Gravity.CENTER_VERTICAL);
        positionControls.addView(verticalPosition, new LinearLayout.LayoutParams(0, -2, 1f));
        Button resetPosition = new Button(this, null, android.R.attr.borderlessButtonStyle);
        resetPosition.setText(R.string.reset);
        resetPosition.setTextSize(12);
        resetPosition.setAllCaps(false);
        resetPosition.setMinWidth(dp(64));
        resetPosition.setMinHeight(dp(48));
        resetPosition.setContentDescription(getString(R.string.reset_description));
        resetPosition.setOnClickListener(ignored -> {
            MaskMode.resetVerticalOffset(MainActivity.this);
            verticalPosition.setProgress(MaskMode.verticalOffset(MainActivity.this) + 360);
            OverlayService.refreshMask(MainActivity.this);
            refresh();
        });
        positionControls.addView(resetPosition, new LinearLayout.LayoutParams(-2, -2));
        content.addView(positionControls, new LinearLayout.LayoutParams(-1, -2));

        autoStatus = sectionLabel(content, getString(R.string.auto_label));
        Spinner appPicker = new Spinner(this);
        List<TargetApp> apps = launchableApps();
        ArrayAdapter<TargetApp> appAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, apps);
        appPicker.setAdapter(appAdapter);
        appPicker.setSelection(targetSelection(apps, MaskMode.autoPackage(this)));
        appPicker.setContentDescription(getString(R.string.auto_picker_description));
        appPicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                MaskMode.setAutoPackage(MainActivity.this, apps.get(position).packageName);
                verticalPosition.setProgress(MaskMode.verticalOffset(MainActivity.this) + 360);
                OverlayService.refreshMask(MainActivity.this);
                refresh();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        content.addView(appPicker, new LinearLayout.LayoutParams(-1, -2));

        Button accessibility = new Button(this);
        accessibility.setAllCaps(false);
        accessibility.setText(R.string.enable_detection);
        accessibility.setOnClickListener(ignored -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        content.addView(accessibility, new LinearLayout.LayoutParams(-1, -2));

        showButton = new Button(this);
        showButton.setAllCaps(false);
        showButton.setTextSize(17);
        showButton.setText(R.string.show_quick_button);
        showButton.setOnClickListener(ignored -> showQuickButton());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, -2);
        buttonParams.topMargin = dp(32);
        content.addView(showButton, buttonParams);
        Button information = new Button(this, null, android.R.attr.borderlessButtonStyle);
        information.setText("ⓘ");
        information.setAllCaps(false);
        information.setTextSize(24);
        information.setMinWidth(dp(48));
        information.setMinHeight(dp(48));
        information.setContentDescription(getString(R.string.information));
        information.setOnClickListener(ignored -> startActivity(new Intent(this, AboutActivity.class)));
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(-2, -2);
        infoParams.gravity = Gravity.CENTER_HORIZONTAL;
        infoParams.topMargin = dp(12);
        content.addView(information, infoParams);
        scroll.addView(content);
        return scroll;
    }

    private void showQuickButton() {
        if (!Settings.canDrawOverlays(this)) {
            Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(permission, OVERLAY_PERMISSION_REQUEST);
            return;
        }
        OverlayService.start(this);
        refresh();
    }

    private void showUpdate(UpdateChecker.Release release) {
        if (isFinishing() || isDestroyed()) return;
        String installed;
        try {
            installed = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            return;
        }
        boolean available = release != null && release.isNewerThan(installed);
        updateButton.setVisibility(available ? View.VISIBLE : View.GONE);
        if (!available) return;
        updateButton.setText(getString(R.string.update_available, release.versionName));
        updateButton.setOnClickListener(ignored -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(release.releaseUrl)));
            } catch (ActivityNotFoundException error) {
                Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refresh() {
        if (modeStatus != null) {
            modeStatus.setText(getString(R.string.mask_status, MaskMode.label(this, MaskMode.get(this))));
        }
        if (opacityStatus != null) opacityStatus.setText(getString(R.string.opacity_value, MaskMode.opacity(this)));
        if (positionStatus != null) {
            int offset = MaskMode.verticalOffset(this);
            positionStatus.setText(getString(MaskMode.hasVerticalOffsetOverride(this)
                    ? R.string.vertical_value : R.string.vertical_default, offset));
        }
        if (autoStatus != null) {
            String target = MaskMode.autoPackage(this);
            autoStatus.setText(target.isEmpty() ? getString(R.string.auto_disabled)
                    : getString(R.string.auto_selected, appLabel(target))
                    + (isWatcherEnabled() ? "" : getString(R.string.auto_enable_hint)));
        }
    }

    private TextView sectionLabel(LinearLayout content, String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(getColor(R.color.on_surface));
        label.setTextSize(15);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(24);
        content.addView(label, params);
        return label;
    }

    private List<TargetApp> launchableApps() {
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = getPackageManager().queryIntentActivities(launcher, 0);
        ArrayList<TargetApp> apps = new ArrayList<>();
        apps.add(new TargetApp("", getString(R.string.no_app)));
        HashSet<String> packages = new HashSet<>();
        for (ResolveInfo info : resolved) {
            String packageName = info.activityInfo.packageName;
            if (getPackageName().equals(packageName) || !packages.add(packageName)) continue;
            apps.add(new TargetApp(packageName, String.valueOf(info.loadLabel(getPackageManager()))));
        }
        Collections.sort(apps.subList(1, apps.size()), Comparator.comparing(app -> app.label, String.CASE_INSENSITIVE_ORDER));
        return apps;
    }

    private int targetSelection(List<TargetApp> apps, String packageName) {
        for (int i = 0; i < apps.size(); i++) if (apps.get(i).packageName.equals(packageName)) return i;
        return 0;
    }

    private String appLabel(String packageName) {
        for (TargetApp app : launchableApps()) if (app.packageName.equals(packageName)) return app.label;
        return packageName;
    }

    private boolean isWatcherEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(new ComponentName(this, AppWatchAccessibilityService.class).flattenToString());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void addMaskCheckbox(LinearLayout content, int label, int color, int mask) {
        CheckBox checkbox = new CheckBox(this);
        checkbox.setText(label);
        checkbox.setTextColor(getColor(R.color.on_surface));
        checkbox.setButtonTintList(android.content.res.ColorStateList.valueOf(getColor(color)));
        checkbox.setMinHeight(dp(48));
        checkbox.setChecked((MaskMode.get(this) & mask) != 0);
        checkbox.setOnCheckedChangeListener((button, checked) -> {
            int selection = MaskMode.get(this);
            MaskMode.set(this, checked ? selection | mask : selection & ~mask);
            OverlayService.refreshMask(this);
            refresh();
        });
        content.addView(checkbox, new LinearLayout.LayoutParams(-1, -2));
    }

    private static final class TargetApp {
        final String packageName;
        final String label;
        TargetApp(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }
        @Override public String toString() { return label; }
    }
}
