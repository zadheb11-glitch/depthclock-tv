package com.depthclock.tv;

import android.service.dreams.DreamService;
import android.webkit.WebView;

public class ClockDream extends DreamService {
    private WebView web;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        web = ClockView.create(this);
        setContentView(web);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (web != null) {
            web.destroy();
            web = null;
        }
    }
}
