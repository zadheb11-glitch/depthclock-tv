private View makeQuickButton(String asset, final String action) {
    final FrameLayout button = new FrameLayout(this);
    button.setFocusable(true);
    final GradientDrawable bg = new GradientDrawable();
    bg.setShape(GradientDrawable.OVAL);
    bg.setColor(0x66101226);
    button.setBackground(bg);
    
    final ImageView icon = new ImageView(this);
    InputStream in = null;
    try {
        in = getAssets().open("qs/" + asset + ".png");
        Bitmap bitmap = BitmapFactory.decodeStream(in);
        if (bitmap != null) {
            icon.setImageBitmap(bitmap);
        }
    } catch (IOException e) {
        e.printStackTrace();
    } finally {
        try {
            if (in != null) in.close();
        } catch (IOException ignored) {}
    }
    
    button.addView(icon, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(52), dp(52));
    lp.leftMargin = dp(14);
    button.setLayoutParams(lp);
    
    button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            float s = hasFocus ? 1.18f : 1f;
            v.animate().scaleX(s).scaleY(s).setDuration(140).start();
            bg.setColor(hasFocus ? 0xF2FFFFFF : 0x66101226);
            if (hasFocus) icon.setColorFilter(0xFF14162B);
            else icon.clearColorFilter();
        }
    });

    button.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            openSettingsAction(action);
        }
    });

    return button;
}