package com.depthclock.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Apple TV style home screen: live depth clock behind a single row of app tiles. */
public class LauncherActivity extends Activity {
    private static final int KIND_APP = 0;
    private static final int KIND_WALLPAPER = 1;
    private static final int KIND_SETTINGS = 2;

    private static final int REQ_PICK = 42;

    private static final class Item {
        final int kind;
        final String label;
        final String pkg;
        final Drawable art;
        final boolean banner;
        final Intent intent;
        final File custom;

        Item(int kind, String label, String pkg, Drawable art, boolean banner,
             Intent intent, File custom) {
            this.kind = kind;
            this.label = label;
            this.pkg = pkg;
            this.art = art;
            this.banner = banner;
            this.intent = intent;
            this.custom = custom;
        }
    }

    private WebView web;
    private LinearLayout row;
    private String lastSignature = "";
    private String pendingPkg;
    private Map<String, File> iconMap = new HashMap<>();
    private String customSignature = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0E1030);

        web = ClockView.create(this);
        web.setFocusable(false);
        web.setFocusableInTouchMode(false);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View scrim = new View(this);
        GradientDrawable fade = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x9905061A});
        scrim.setBackground(fade);
        FrameLayout.LayoutParams scrimLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(300), Gravity.BOTTOM);
        root.addView(scrim, scrimLp);

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.setPadding(dp(72), dp(40), dp(72), dp(40));
        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        scroller.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams scrollLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        scrollLp.bottomMargin = dp(20);
        root.addView(scroller, scrollLp);

        setContentView(root);
        hideSystemUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
        rebuild();
        hideSystemUi();
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        // This is the home screen, so Back does nothing.
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            nextScene();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    private void nextScene() {
        if (web != null) {
            web.evaluateJavascript("window.nextScene&&window.nextScene(1)", null);
        }
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private List<Item> loadApps() {
        PackageManager pm = getPackageManager();
        scanIcons();
        List<Item> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> leanbackPackages = new HashSet<>();

        Intent tvIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(tvIntent, 0)) {
            addApp(pm, ri, apps, seen);
            leanbackPackages.add(ri.activityInfo.packageName);
        }
        Intent phoneIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(phoneIntent, 0)) {
            if (leanbackPackages.contains(ri.activityInfo.packageName)) continue;
            addApp(pm, ri, apps, seen);
        }
        Collections.sort(apps, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return apps;
    }

    private void addApp(PackageManager pm, ResolveInfo ri, List<Item> out, Set<String> seen) {
        String pkg = ri.activityInfo.packageName;
        String cls = ri.activityInfo.name;
        if (pkg.equals(getPackageName()) && cls.endsWith("LauncherActivity")) return;
        if (!seen.add(pkg + "/" + cls)) return;

        CharSequence name = ri.loadLabel(pm);
        Drawable art = null;
        boolean banner = false;
        try {
            art = ri.activityInfo.loadBanner(pm);
            banner = art != null;
            if (art == null) art = ri.loadIcon(pm);
        } catch (Exception ignored) {
        }
        Intent launch = new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName(pkg, cls))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        String label = name == null ? pkg : name.toString();
        File custom = iconMap.get(pkg.toLowerCase(Locale.ROOT));
        if (custom == null) custom = iconMap.get(label.toLowerCase(Locale.ROOT));
        out.add(new Item(KIND_APP, label, pkg, art, banner, launch, custom));
    }

    private void rebuild() {
        rebuild(null);
    }

    private void rebuild(String focusPkg) {
        List<Item> apps = loadApps();
        StringBuilder sig = new StringBuilder();
        for (Item i : apps) sig.append(i.label).append('|');
        sig.append(customSignature);
        String signature = sig.toString();
        if (signature.equals(lastSignature) && row.getChildCount() > 0) return;
        lastSignature = signature;

        row.removeAllViews();
        row.addView(makeTile(new Item(KIND_WALLPAPER, "Wallpaper", null, null, false, null, null),
                "Change\nWallpaper"));
        for (Item i : apps) row.addView(makeTile(i, null));
        row.addView(makeTile(new Item(KIND_SETTINGS, "Settings", null, null, false, null, null),
                "Settings"));

        View target = null;
        if (focusPkg != null) target = row.findViewWithTag(focusPkg);
        if (target == null) {
            target = row.getChildCount() > 1 ? row.getChildAt(1) : row.getChildAt(0);
        }
        target.requestFocus();
    }

    // ---- custom icons -------------------------------------------------------------

    private File iconsDir() {
        File d = getExternalFilesDir("icons");
        if (d == null) d = new File(getFilesDir(), "icons");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static String stemOf(String name) {
        int dot = name.lastIndexOf('.');
        return (dot > 0 ? name.substring(0, dot) : name).toLowerCase(Locale.ROOT);
    }

    private static boolean isImage(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp");
    }

    private void scanIcons() {
        Map<String, File> map = new HashMap<>();
        StringBuilder sig = new StringBuilder();
        File[] files = iconsDir().listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && isImage(f.getName())) {
                    map.put(stemOf(f.getName()), f);
                    sig.append(f.getName()).append(f.lastModified()).append(f.length()).append(';');
                }
            }
        }
        iconMap = map;
        customSignature = sig.toString();
    }

    private Bitmap decodeFile(File f, int maxSide) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int sample = 1;
            while (o.outWidth / sample > maxSide || o.outHeight / sample > maxSide) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
        } catch (Exception e) {
            return null;
        }
    }

    private void removeCustom(String pkg, String label) {
        File[] files = iconsDir().listFiles();
        if (files == null) return;
        String a = pkg == null ? "" : pkg.toLowerCase(Locale.ROOT);
        String b = label == null ? "" : label.toLowerCase(Locale.ROOT);
        for (File f : files) {
            String stem = stemOf(f.getName());
            if (stem.equals(a) || stem.equals(b)) f.delete();
        }
    }

    private boolean saveCustom(String pkg, Uri uri) {
        InputStream in = null;
        FileOutputStream out = null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            in = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            int sample = 1;
            while (o.outWidth / sample > 1000 || o.outHeight / sample > 1000) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            in = getContentResolver().openInputStream(uri);
            Bitmap bm = BitmapFactory.decodeStream(in, null, o2);
            in.close();
            in = null;
            if (bm == null) return false;
            removeCustom(pkg, null);
            out = new FileOutputStream(new File(iconsDir(), pkg + ".png"));
            bm.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.close();
            out = null;
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) {
            }
            try {
                if (out != null) out.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void showEditor(final Item item) {
        String[] options = {"Choose image", "Reset to default", "Where to put image files"};
        new AlertDialog.Builder(this)
                .setTitle(item.label)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            pickImage(item);
                        } else if (which == 1) {
                            removeCustom(item.pkg, item.label);
                            lastSignature = "";
                            rebuild(item.pkg);
                        } else {
                            showFolderHelp(item);
                        }
                    }
                })
                .show();
    }

    private void showFolderHelp(Item item) {
        Toast.makeText(this,
                "Copy a 16:9 image (PNG or JPG) to:\n" + iconsDir().getAbsolutePath()
                        + "\nName it " + item.pkg + ".png or " + item.label + ".png",
                Toast.LENGTH_LONG).show();
    }

    private void pickImage(Item item) {
        pendingPkg = item.pkg;
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("image/*");
        try {
            startActivityForResult(pick, REQ_PICK);
        } catch (ActivityNotFoundException e) {
            showFolderHelp(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK && resultCode == RESULT_OK && data != null
                && data.getData() != null && pendingPkg != null) {
            boolean ok = saveCustom(pendingPkg, data.getData());
            if (!ok) Toast.makeText(this, "Could not read that image", Toast.LENGTH_LONG).show();
            lastSignature = "";
            rebuild(pendingPkg);
        }
    }

    private View makeTile(final Item item, String specialText) {
        final LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setFocusable(true);
        tile.setClipChildren(false);
        LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(dp(220),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tileLp.rightMargin = dp(28);
        tile.setLayoutParams(tileLp);

        final FrameLayout card = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(item.kind == KIND_APP ? 0xFF1B1D33 : 0xCC2A2D4A);
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setElevation(dp(2));
        tile.addView(card, new LinearLayout.LayoutParams(dp(220), dp(124)));

        Drawable art = item.art;
        boolean banner = item.banner;
        if (item.custom != null) {
            Bitmap bm = decodeFile(item.custom, 800);
            if (bm != null) {
                art = new BitmapDrawable(getResources(), bm);
                banner = true;
            }
        }
        if (item.pkg != null) tile.setTag(item.pkg);

        if (art != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(art);
            if (banner) {
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                card.addView(iv, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            } else {
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(72), dp(72), Gravity.CENTER);
                card.addView(iv, lp);
            }
        } else {
            TextView t = new TextView(this);
            t.setText(specialText == null ? item.label : specialText);
            t.setTextColor(Color.WHITE);
            t.setTextSize(18);
            t.setGravity(Gravity.CENTER);
            card.addView(t, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        final TextView label = new TextView(this);
        label.setText(item.label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(16);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setAlpha(0f);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = dp(12);
        tile.addView(label, labelLp);

        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float s = hasFocus ? 1.14f : 1f;
                v.animate().scaleX(s).scaleY(s).setDuration(160).start();
                card.animate().translationZ(hasFocus ? dp(18) : 0f).setDuration(160).start();
                label.animate().alpha(hasFocus ? 1f : 0f).setDuration(160).start();
            }
        });
        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (item.kind == KIND_WALLPAPER) {
                    nextScene();
                } else if (item.kind == KIND_SETTINGS) {
                    try {
                        startActivity(new Intent(Settings.ACTION_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (ActivityNotFoundException ignored) {
                    }
                } else {
                    try {
                        startActivity(item.intent);
                    } catch (Exception ignored) {
                    }
                }
            }
        });
        if (item.kind == KIND_APP) {
            tile.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showEditor(item);
                    return true;
                }
            });
        }
        return tile;
    }
}
