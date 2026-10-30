package com.depthclock.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Makes Depth Launcher act as the main home screen without being the system default.
 * When it is switched on in Accessibility, the Home button and the stock launcher both
 * open Depth Launcher instead.
 */
public class HomeKeyService extends AccessibilityService {
    private static final Set<String> STOCK_LAUNCHERS = new HashSet<>(Arrays.asList(
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher",
            "com.amazon.tv.launcher",
            "com.android.tv.launcher",
            "com.mbx.settingsmbox"));

    private long lastLaunch = 0;

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
            if (event.getAction() == KeyEvent.ACTION_UP) openLauncher();
            return true;
        }
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence pkg = event.getPackageName();
        if (pkg != null && STOCK_LAUNCHERS.contains(pkg.toString())) openLauncher();
    }

    @Override
    public void onInterrupt() {
    }

    private void openLauncher() {
        long now = SystemClock.uptimeMillis();
        if (now - lastLaunch < 800) return;
        lastLaunch = now;
        Intent i = new Intent(this, LauncherActivity.class).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }
    }
}
