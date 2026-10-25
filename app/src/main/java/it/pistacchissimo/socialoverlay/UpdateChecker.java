// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Marco Rinaldi (MarcorOnline)

package it.pistacchissimo.socialoverlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** One best-effort request per week, only when the control screen is opened. */
final class UpdateChecker {
    static final String RELEASE_URL = "https://api.github.com/repos/MarcorOnline/Social-Safe-Area-for-Android-Camera/releases/latest";
    static final long INTERVAL_MS = 7L * 24 * 60 * 60 * 1000;
    static final int MAX_BYTES = 128 * 1024;
    private static final int TIMEOUT_MS = 5000;
    private static WeakReference<Consumer<Release>> listener = new WeakReference<>(null);

    static synchronized void check(Context context, Consumer<Release> result) {
        // A recreated screen receives an in-flight result without keeping the old Activity alive.
        listener = new WeakReference<>(result);
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences("app_updates", Context.MODE_PRIVATE);
        result.accept(parse(preferences.getString("release", "")));
        long now = System.currentTimeMillis();
        if (!isDue(preferences.getLong("last_attempt", 0), now)) return;
        // Count failures too: a rate limit or blocked response must not cause repeated requests.
        preferences.edit().putLong("last_attempt", now).apply();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            Release release = null;
            try {
                release = fetch((HttpURLConnection) new URL(RELEASE_URL).openConnection());
            } catch (IOException | RuntimeException ignored) {
                // Offline, DNS and TLS failures leave the app and the last valid result usable.
            }
            if (release != null) {
                preferences.edit().putString("release", release.json).apply();
                Release available = release;
                main.post(() -> {
                    Consumer<Release> current = listener.get();
                    if (current != null) current.accept(available);
                });
            }
        }, "safe-area-updates").start();
    }

    static boolean isDue(long lastAttempt, long now) {
        return lastAttempt <= 0 || now < lastAttempt || now - lastAttempt >= INTERVAL_MS;
    }

    static Release fetch(HttpURLConnection connection) {
        try {
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "SafeArea-UpdateCheck");
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            String type = connection.getContentType();
            if (type == null || !"application/json".equalsIgnoreCase(type.split(";", 2)[0].trim())
                    || connection.getContentLengthLong() > MAX_BYTES) return null;
            long deadline = System.nanoTime() + 2L * TIMEOUT_MS * 1_000_000;
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                while (true) {
                    long remaining = (deadline - System.nanoTime()) / 1_000_000;
                    if (remaining <= 0) return null;
                    connection.setReadTimeout((int) Math.min(TIMEOUT_MS, remaining));
                    int count = input.read(buffer);
                    if (count == -1) break;
                    if (output.size() + count > MAX_BYTES) return null;
                    output.write(buffer, 0, count);
                }
                return parse(new String(output.toByteArray(), StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException ignored) {
            return null;
        } finally {
            connection.disconnect();
        }
    }

    static Release parse(String text) {
        if (text == null || text.length() > MAX_BYTES) return null;
        try {
            JSONTokener tokens = new JSONTokener(text);
            Object value = tokens.nextValue();
            if (!(value instanceof JSONObject) || tokens.nextClean() != 0) return null;
            JSONObject object = (JSONObject) value;
            Object tag = object.opt("tag_name");
            Object url = object.opt("html_url");
            if (!(tag instanceof String) || !Boolean.FALSE.equals(object.opt("draft"))
                    || !Boolean.FALSE.equals(object.opt("prerelease"))) {
                return null;
            }
            String name = ((String) tag).startsWith("v") ? ((String) tag).substring(1) : (String) tag;
            if (versionParts(name) == null
                    || !(AboutActivity.PROJECT_URL + "/releases/tag/" + tag).equals(url)) return null;
            return new Release(name, (String) url, object.toString());
        } catch (JSONException | RuntimeException ignored) {
            return null;
        }
    }

    private static int[] versionParts(String name) {
        if (name == null || name.length() > 64 || !name.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")) {
            return null;
        }
        String[] parts = name.split("\\.");
        try {
            return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static final class Release {
        final String versionName;
        final String releaseUrl;
        final String json;

        private Release(String versionName, String releaseUrl, String json) {
            this.versionName = versionName;
            this.releaseUrl = releaseUrl;
            this.json = json;
        }

        boolean isNewerThan(String installedName) {
            int[] installed = versionParts(installedName);
            int[] available = versionParts(versionName);
            if (installed == null || available == null) return false;
            for (int i = 0; i < installed.length; i++) {
                if (available[i] != installed[i]) return available[i] > installed[i];
            }
            return false;
        }
    }
}
