// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Marco Rinaldi (MarcorOnline)

package it.pistacchissimo.socialoverlay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Paint;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
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
    private TextView subtitlePositionStatus;
    private SeekBar subtitlePosition;
    private Button resetSubtitlePosition;
    private TextView autoStatus;
    private Button updateButton;
    private Button expandedHeader;
    private LinearLayout expandedSection;
    private TextView experimentalStatus;
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
        scroll.setFitsSystemWindows(true);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getColor(R.color.surface));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.TOP);
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

        showButton = new Button(this, null, 0, R.style.SafeAreaButton_Primary);
        showButton.setAllCaps(false);
        showButton.setTextSize(17);
        showButton.setText(R.string.show_quick_button);
        showButton.setOnClickListener(ignored -> showQuickButton());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, -2);
        buttonParams.topMargin = dp(32);
        content.addView(showButton, buttonParams);

        updateButton = new Button(this, null, 0, R.style.SafeAreaButton_Text);
        updateButton.setAllCaps(false);
        updateButton.setVisibility(View.GONE);
        content.addView(updateButton, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout maskSettings = addAccordion(content, R.string.customize_mask);
        TextView modeLabel = new TextView(this);
        modeLabel.setText(R.string.mask_label);
        modeLabel.setTextColor(getColor(R.color.on_surface));
        modeLabel.setTextSize(15);
        LinearLayout.LayoutParams modeLabelParams = new LinearLayout.LayoutParams(-1, -2);
        modeLabelParams.topMargin = dp(12);
        maskSettings.addView(modeLabel, modeLabelParams);

        addMaskCheckbox(maskSettings, R.string.reels, R.color.mask_reels, MaskMode.REELS);
        addMaskCheckbox(maskSettings, R.string.tiktok, R.color.mask_tiktok, MaskMode.TIKTOK);
        addMaskCheckbox(maskSettings, R.string.subtitles, R.color.mask_subtitles, MaskMode.SUBTITLES);

        opacityStatus = sectionLabel(maskSettings, getString(R.string.opacity_label));
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
        maskSettings.addView(opacity, new LinearLayout.LayoutParams(-1, -2));

        positionStatus = sectionLabel(maskSettings, getString(R.string.vertical_label));
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
        Button resetPosition = new Button(this, null, 0, R.style.SafeAreaButton_Text);
        resetPosition.setText(R.string.reset);
        resetPosition.setTextSize(14);
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
        maskSettings.addView(positionControls, new LinearLayout.LayoutParams(-1, -2));

        subtitlePositionStatus = sectionLabel(maskSettings, getString(R.string.subtitle_vertical_label));
        subtitlePosition = new SeekBar(this);
        subtitlePosition.setMax(MaskMode.SUBTITLE_OFFSET_LIMIT * 2);
        subtitlePosition.setProgress(MaskMode.subtitleOffset(this) + MaskMode.SUBTITLE_OFFSET_LIMIT);
        subtitlePosition.setContentDescription(getString(R.string.subtitle_vertical_picker_description));
        subtitlePosition.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) MaskMode.setSubtitleOffset(MainActivity.this, progress - MaskMode.SUBTITLE_OFFSET_LIMIT);
                OverlayService.refreshMask(MainActivity.this);
                refresh();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        LinearLayout subtitleControls = new LinearLayout(this);
        subtitleControls.setGravity(Gravity.CENTER_VERTICAL);
        subtitleControls.addView(subtitlePosition, new LinearLayout.LayoutParams(0, -2, 1f));
        resetSubtitlePosition = new Button(this, null, 0, R.style.SafeAreaButton_Text);
        resetSubtitlePosition.setText(R.string.reset);
        resetSubtitlePosition.setTextSize(14);
        resetSubtitlePosition.setAllCaps(false);
        resetSubtitlePosition.setMinWidth(dp(64));
        resetSubtitlePosition.setMinHeight(dp(48));
        resetSubtitlePosition.setContentDescription(getString(R.string.subtitle_reset_description));
        resetSubtitlePosition.setOnClickListener(ignored -> {
            MaskMode.resetSubtitleOffset(MainActivity.this);
            subtitlePosition.setProgress(MaskMode.SUBTITLE_OFFSET_LIMIT);
            OverlayService.refreshMask(MainActivity.this);
            refresh();
        });
        subtitleControls.addView(resetSubtitlePosition, new LinearLayout.LayoutParams(-2, -2));
        maskSettings.addView(subtitleControls, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout autoSettings = addAccordion(content, R.string.automatic_opening);
        autoStatus = sectionLabel(autoSettings, getString(R.string.auto_label));
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
        autoSettings.addView(appPicker, new LinearLayout.LayoutParams(-1, -2));

        Button accessibility = new Button(this, null, 0, R.style.SafeAreaButton);
        accessibility.setAllCaps(false);
        accessibility.setText(R.string.enable_detection);
        accessibility.setOnClickListener(ignored -> openAccessibilitySettings());
        LinearLayout.LayoutParams accessibilityParams = new LinearLayout.LayoutParams(-1, -2);
        accessibilityParams.topMargin = dp(16);
        autoSettings.addView(accessibility, accessibilityParams);

        LinearLayout experimental = addAccordion(content, R.string.experimental_features);
        LinearLayout videoOnlyRow = new LinearLayout(this);
        videoOnlyRow.setGravity(Gravity.CENTER_VERTICAL);
        CheckBox videoOnly = new CheckBox(this);
        videoOnly.setText(R.string.experimental_video_only);
        videoOnly.setTextColor(getColor(R.color.on_surface));
        videoOnly.setMinHeight(dp(48));
        videoOnly.setChecked(MaskMode.videoOnly(this));
        videoOnly.setOnCheckedChangeListener((button, checked) -> {
            if (!checked) {
                MaskMode.setVideoOnly(this, false);
                OverlayService.refreshMask(this);
                refresh();
            } else if (!MaskMode.videoOnly(this)) {
                showVideoModeInfo(videoOnly);
            }
        });
        videoOnlyRow.addView(videoOnly, new LinearLayout.LayoutParams(0, -2, 1f));
        Button help = new Button(this, null, 0, R.style.SafeAreaButton_Text);
        help.setText("?");
        help.setContentDescription(getString(R.string.experimental_video_help_description));
        help.setOnClickListener(ignored -> showVideoModeInfo(null));
        videoOnlyRow.addView(help, new LinearLayout.LayoutParams(dp(48), -2));
        experimental.addView(videoOnlyRow, new LinearLayout.LayoutParams(-1, -2));
        experimentalStatus = sectionLabel(experimental, "");
        experimentalStatus.setMinHeight(dp(48));

        Button information = new Button(this, null, 0, R.style.SafeAreaButton_Text);
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

    private void showVideoModeInfo(CheckBox option) {
        SpannableString message = new SpannableString(getString(R.string.experimental_video_help));
        int github = message.toString().lastIndexOf("GitHub");
        if (github >= 0) {
            message.setSpan(new ClickableSpan() {
                @Override public void onClick(View widget) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(AboutActivity.PROJECT_URL
                                + "/blob/develop/CONTRIBUTING.md#contribute-experimental-mode-detection")));
                    } catch (ActivityNotFoundException error) {
                        Toast.makeText(MainActivity.this, R.string.no_browser, Toast.LENGTH_SHORT).show();
                    }
                }
            }, github, github + "GitHub".length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.experimental_video_only).setMessage(message);
        if (option == null) builder.setPositiveButton(android.R.string.ok, null);
        else builder.setPositiveButton(R.string.experimental_enable, (dialog, which) -> {
                    MaskMode.setVideoOnly(this, true);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> option.setChecked(false))
                .setOnCancelListener(dialog -> option.setChecked(false));
        TextView text = builder.show().findViewById(android.R.id.message);
        text.setText(message, TextView.BufferType.SPANNABLE);
        text.setMovementMethod(LinkMovementMethod.getInstance());
        text.setLinksClickable(true);
        text.setClickable(true);
        text.setLinkTextColor(getColor(R.color.brand));
    }

    private void openAccessibilitySettings() {
        String component = new ComponentName(this, AppWatchAccessibilityService.class).flattenToString();
        try {
            // This settings intent is provided by AOSP and some OEMs, but is not a public SDK constant.
            startActivity(new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                    .putExtra(Intent.EXTRA_COMPONENT_NAME, component));
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            try {
                Bundle arguments = new Bundle();
                arguments.putString(":settings:fragment_args_key", component);
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .putExtra(":settings:fragment_args_key", component)
                        .putExtra(":settings:show_fragment_args", arguments));
            } catch (ActivityNotFoundException | SecurityException error) {
                Toast.makeText(this, R.string.experimental_enable_accessibility, Toast.LENGTH_LONG).show();
            }
        }
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
        if (subtitlePositionStatus != null) {
            boolean enabled = (MaskMode.get(this) & MaskMode.SUBTITLES) != 0;
            subtitlePositionStatus.setText(getString(R.string.subtitle_vertical_value, MaskMode.subtitleOffset(this)));
            subtitlePositionStatus.setEnabled(enabled);
            subtitlePositionStatus.setAlpha(enabled ? 1f : .38f);
            subtitlePosition.setEnabled(enabled);
            resetSubtitlePosition.setEnabled(enabled);
        }
        if (autoStatus != null) {
            String target = MaskMode.autoPackage(this);
            autoStatus.setText(target.isEmpty() ? getString(R.string.auto_disabled)
                    : getString(R.string.auto_selected, appLabel(target))
                    + (isWatcherEnabled() ? "" : getString(R.string.auto_enable_hint)));
        }
        if (experimentalStatus != null) {
            boolean needsAccessibility = !MaskMode.autoPackage(this).isEmpty() && !isWatcherEnabled();
            experimentalStatus.setText(MaskMode.autoPackage(this).isEmpty()
                    ? R.string.experimental_choose_camera : !isWatcherEnabled()
                    ? R.string.experimental_enable_accessibility : DeviceProfiles.modeDetection(this) == null
                    ? R.string.experimental_no_profile : R.string.experimental_profile_ready);
            experimentalStatus.setOnClickListener(needsAccessibility ? ignored -> openAccessibilitySettings() : null);
            experimentalStatus.setClickable(needsAccessibility);
            experimentalStatus.setFocusable(needsAccessibility);
            experimentalStatus.setTextColor(getColor(needsAccessibility ? R.color.brand : R.color.on_surface));
            experimentalStatus.setPaintFlags(needsAccessibility
                    ? experimentalStatus.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG
                    : experimentalStatus.getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG);
        }
    }

    private LinearLayout addAccordion(LinearLayout content, int title) {
        Button header = new Button(this, null, 0, R.style.SafeAreaButton_Accordion);
        header.setText(title);
        header.setAllCaps(false);
        header.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_expand, 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(16);
        content.addView(header, params);
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(32), dp(8), dp(32), dp(20));
        content.addView(section, new LinearLayout.LayoutParams(-1, -2));
        setAccordionExpanded(header, section, false);
        header.setOnClickListener(ignored -> {
            boolean expand = section.getVisibility() != View.VISIBLE;
            if (expandedSection != null) setAccordionExpanded(expandedHeader, expandedSection, false);
            setAccordionExpanded(header, section, expand);
            expandedHeader = expand ? header : null;
            expandedSection = expand ? section : null;
        });
        header.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(section.getVisibility() == View.VISIBLE
                        ? AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE
                        : AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND);
            }
            @Override public boolean performAccessibilityAction(View host, int action, Bundle arguments) {
                if (action == AccessibilityNodeInfo.ACTION_EXPAND || action == AccessibilityNodeInfo.ACTION_COLLAPSE) {
                    if ((action == AccessibilityNodeInfo.ACTION_EXPAND) != (section.getVisibility() == View.VISIBLE)) {
                        return header.performClick();
                    }
                    return false;
                }
                return super.performAccessibilityAction(host, action, arguments);
            }
        });
        return section;
    }

    private void setAccordionExpanded(Button header, LinearLayout section, boolean expanded) {
        section.setVisibility(expanded ? View.VISIBLE : View.GONE);
        header.setSelected(expanded);
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
