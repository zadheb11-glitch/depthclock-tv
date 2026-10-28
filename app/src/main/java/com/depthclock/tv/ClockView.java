package com.depthclock.tv;

import android.annotation.SuppressLint;
import android.content.Context;
import android.webkit.WebSettings;
import android.webkit.WebView;

final class ClockView {
    private ClockView() {}

    @SuppressLint("SetJavaScriptEnabled")
    static WebView create(Context context) {
        WebView web = new WebView(context);
        web.setBackgroundColor(0xFF0E1030);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        web.loadUrl("file:///android_asset/clock.html");
        return web;
    }
}
