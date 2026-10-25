// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Marco Rinaldi (MarcorOnline)

package it.pistacchissimo.socialoverlay;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Creator attribution, license and shareable, non-identifying camera calibration. */
public final class AboutActivity extends Activity {
    static final String PROJECT_URL = "https://github.com/MarcorOnline/SocialSafeArea";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getColor(R.color.surface));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);

        Button back = button(content, R.string.back);
        back.setOnClickListener(ignored -> finish());
        text(content, getString(R.string.about_title), 25);
        text(content, getString(R.string.about_creator), 18);
        try {
            text(content, getString(R.string.version_label,
                    getPackageManager().getPackageInfo(getPackageName(), 0).versionName), 14);
        } catch (PackageManager.NameNotFoundException error) {
            throw new IllegalStateException(error);
        }
        text(content, getString(R.string.about_intro), 16);
        text(content, getString(R.string.about_idea), 16);
        text(content, getString(R.string.about_support), 16);
        link(content, R.string.pistacchissimo_article,
                "https://www.pistacchissimo.it/news/social-safe-area-lapp-per-girare-reels-e-tiktok-migliori-gratis/");
        link(content, R.string.pistacchissimo_instagram, "https://www.instagram.com/pistacchissimo/");
        link(content, R.string.pistacchissimo_shop, "https://shop.pistacchissimo.it/");
        link(content, R.string.contact_instagram, "https://www.instagram.com/nonsolopistacchio/");
        link(content, R.string.project_github, PROJECT_URL);
        link(content, R.string.license_link, PROJECT_URL + "/blob/main/LICENSE");
        text(content, getString(R.string.license_notice), 13);

        text(content, getString(R.string.calibration_title), 20);
        String camera = DeviceProfiles.cameraPackage(this);
        TextView device = text(content, getString(R.string.device_details,
                Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT,
                camera.isEmpty() ? getString(R.string.no_app) : camera,
                DeviceProfiles.defaultOffset(this), MaskMode.verticalOffset(this)), 14);
        device.setTextIsSelectable(true);
        org.json.JSONObject matched = DeviceProfiles.detectedProfile(this);
        if (matched != null) {
            text(content, getString(R.string.selected_profile, matched.toString()), 13).setTextIsSelectable(true);
        }
        if (DeviceProfiles.detectedOffset(this) == null) {
            text(content, getString(R.string.profile_unknown), 14);
        }
        button(content, R.string.copy_profile).setOnClickListener(ignored -> {
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText(
                    getString(R.string.calibration_title), DeviceProfiles.calibrationJson(this)));
            Toast.makeText(this, R.string.profile_copied, Toast.LENGTH_SHORT).show();
        });
        scroll.addView(content);
        setContentView(scroll);
    }

    private TextView text(LinearLayout parent, String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(getColor(R.color.on_surface));
        view.setTextSize(size);
        int margin = Math.round(12 * getResources().getDisplayMetrics().density);
        view.setPadding(0, margin, 0, margin);
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private Button button(LinearLayout parent, int label) {
        Button button = new Button(this, null, android.R.attr.borderlessButtonStyle);
        button.setText(label);
        button.setAllCaps(false);
        parent.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    private void link(LinearLayout parent, int label, String url) {
        button(parent, label).setOnClickListener(ignored -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (ActivityNotFoundException error) {
                Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
