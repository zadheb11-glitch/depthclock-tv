package com.depthclock.tv;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

final class ClockView {
    private ClockView() {}

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    static WebView create(Context context) {
        final SharedPreferences prefs =
                context.getSharedPreferences("depthclock", Context.MODE_PRIVATE);
        WebView web = new WebView(context);
        web.setBackgroundColor(0xFF0E1030);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void save(int index) {
                prefs.edit().putInt("scene", index).apply();
            }
        }, "AndroidBridge");
        web.loadUrl("file:///android_asset/clock.html#" + prefs.getInt("scene", 0));
        return web;
    }
}
