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
import android.graphics.Typeface;
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
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
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
    private View movingTile;
    private List<Item> hiddenItems = new ArrayList<>();
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

        // frosted dock along the bottom
        FrameLayout dock = new FrameLayout(this);
        GradientDrawable dockBg = new GradientDrawable();
        dockBg.setCornerRadius(dp(26));
        dockBg.setColor(0x40FFFFFF);
        dockBg.setStroke(dp(1), 0x55FFFFFF);
        dock.setBackground(dockBg);
        dock.setClipChildren(false);
        dock.setClipToPadding(false);

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.setPadding(dp(18), dp(16), dp(18), dp(16));
        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        scroller.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dock.addView(scroller, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        dockLp.leftMargin = dp(36);
        dockLp.rightMargin = dp(36);
        dockLp.bottomMargin = dp(32);
        root.addView(dock, dockLp);

        // rounded status pill at the top centre: time, Wi-Fi, wallpaper, settings
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setClipChildren(false);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setCornerRadius(dp(30));
        pillBg.setColor(0x7014182E);
        pillBg.setStroke(dp(1), 0x33FFFFFF);
        pill.setBackground(pillBg);
        pill.setPadding(dp(22), dp(10), dp(14), dp(10));

        TextClock timeView = new TextClock(this);
        timeView.setFormat12Hour("h:mm a");
        timeView.setFormat24Hour("HH:mm");
        timeView.setTextColor(Color.WHITE);
        timeView.setTextSize(24);
        timeView.setTypeface(Typeface.DEFAULT_BOLD);
        pill.addView(timeView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        pill.addView(makeQuickButton(IconView.WIFI, new Runnable() {
            @Override
            public void run() {
                openSettingsAction(Settings.ACTION_WIFI_SETTINGS);
            }
        }, null));
        pill.addView(makeQuickButton(IconView.WALLPAPER, new Runnable() {
            @Override
            public void run() {
                nextScene();
            }
        }, null));
        pill.addView(makeQuickButton(IconView.SETTINGS, new Runnable() {
            @Override
            public void run() {
                openSettingsAction(Settings.ACTION_SETTINGS);
            }
        }, new Runnable() {
            @Override
            public void run() {
                showHiddenManager();
            }
        }));
        FrameLayout.LayoutParams pillLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        pillLp.topMargin = dp(26);
        root.addView(pill, pillLp);

        setContentView(root);
        hideSystemUi();
    }

    private View makeQuickButton(int iconType, final Runnable click, final Runnable longClick) {
        final FrameLayout button = new FrameLayout(this);
        button.setFocusable(true);
        final GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x33FFFFFF);
        button.setBackground(bg);
        final IconView icon = new IconView(this, iconType);
        button.addView(icon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), dp(42));
        lp.leftMargin = dp(10);
        button.setLayoutParams(lp);
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                bg.setColor(hasFocus ? 0xF2FFFFFF : 0x33FFFFFF);
                icon.setColor(hasFocus ? 0xFF14162B : 0xFFFFFFFF);
            }
        });
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                click.run();
            }
        });
        if (longClick != null) {
            button.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    longClick.run();
                    return true;
                }
            });
        }
        return button;
    }

    private void openSettingsAction(String action) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        }
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
        final List<String> order = new ArrayList<>();
        String saved = getSharedPreferences("depthclock", MODE_PRIVATE).getString("order", "");
        for (String o : saved.split("\n")) {
            if (!o.isEmpty()) order.add(o);
        }
        Collections.sort(apps, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                int ia = order.indexOf(a.pkg);
                int ib = order.indexOf(b.pkg);
                if (ia >= 0 && ib >= 0 && ia != ib) return ia - ib;
                if (ia >= 0 && ib < 0) return -1;
                if (ia < 0 && ib >= 0) return 1;
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        Set<String> hidden = hiddenSet();
        List<Item> visible = new ArrayList<>();
        hiddenItems = new ArrayList<>();
        for (Item i : apps) {
            if (hidden.contains(i.pkg)) hiddenItems.add(i);
            else visible.add(i);
        }
        return visible;
    }

    private Set<String> hiddenSet() {
        Set<String> set = new HashSet<>();
        String saved = getSharedPreferences("depthclock", MODE_PRIVATE).getString("hidden", "");
        for (String h : saved.split("\n")) {
            if (!h.isEmpty()) set.add(h);
        }
        return set;
    }

    private void saveHidden(Set<String> set) {
        StringBuilder sb = new StringBuilder();
        for (String h : set) sb.append(h).append('\n');
        getSharedPreferences("depthclock", MODE_PRIVATE).edit()
                .putString("hidden", sb.toString()).apply();
        lastSignature = "";
    }

    private void hideApp(Item item) {
        Set<String> set = hiddenSet();
        set.add(item.pkg);
        saveHidden(set);
        rebuild(null);
        Toast.makeText(this, item.label + " hidden. Long-press the gear at the top to show it again.",
                Toast.LENGTH_LONG).show();
    }

    private void showHiddenManager() {
        if (hiddenItems.isEmpty()) {
            Toast.makeText(this, "No hidden apps", Toast.LENGTH_SHORT).show();
            return;
        }
        final List<Item> list = new ArrayList<>(hiddenItems);
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) names[i] = list.get(i).label;
        new AlertDialog.Builder(this)
                .setTitle("Hidden apps (select one to show it again)")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Item item = list.get(which);
                        Set<String> set = hiddenSet();
                        set.remove(item.pkg);
                        saveHidden(set);
                        rebuild(item.pkg);
                    }
                })
                .show();
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
        List<String> names = new ArrayList<>();
        for (Item i : apps) names.add(i.label);
        Collections.sort(names);
        StringBuilder sig = new StringBuilder();
        for (String n : names) sig.append(n).append('|');
        sig.append(customSignature);
        String signature = sig.toString();
        if (signature.equals(lastSignature) && row.getChildCount() > 0) return;
        lastSignature = signature;

        row.removeAllViews();
        for (Item i : apps) row.addView(makeTile(i));

        View target = null;
        if (focusPkg != null) target = row.findViewWithTag(focusPkg);
        if (target == null && row.getChildCount() > 0) target = row.getChildAt(0);
        if (target != null) target.requestFocus();
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
        String[] options = {"Hide app", "Move", "Built-in icons", "Choose image from storage",
                "Reset image", "Reset order", "Show hidden apps", "Where to put image files"};
        new AlertDialog.Builder(this)
                .setTitle(item.label)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            hideApp(item);
                        } else if (which == 1) {
                            startMove(item);
                        } else if (which == 2) {
                            showBuiltIn(item);
                        } else if (which == 3) {
                            pickImage(item);
                        } else if (which == 4) {
                            removeCustom(item.pkg, item.label);
                            lastSignature = "";
                            rebuild(item.pkg);
                        } else if (which == 5) {
                            getSharedPreferences("depthclock", MODE_PRIVATE)
                                    .edit().remove("order").apply();
                            lastSignature = "";
                            rebuild(item.pkg);
                        } else if (which == 6) {
                            showHiddenManager();
                        } else {
                            showFolderHelp(item);
                        }
                    }
                })
                .show();
    }

    private void showBuiltIn(final Item item) {
        final String[] files;
        try {
            files = getAssets().list("icons");
        } catch (IOException e) {
            return;
        }
        if (files == null || files.length == 0) return;
        String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            names[i] = stemName(files[i]);
        }
        new AlertDialog.Builder(this)
                .setTitle("Icon for " + item.label)
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        copyBuiltIn(item, files[which]);
                    }
                })
                .show();
    }

    private static String stemName(String file) {
        int dot = file.lastIndexOf('.');
        String n = dot > 0 ? file.substring(0, dot) : file;
        return n.replace('_', ' ');
    }

    private void copyBuiltIn(Item item, String assetName) {
        InputStream in = null;
        FileOutputStream out = null;
        try {
            removeCustom(item.pkg, null);
            in = getAssets().open("icons/" + assetName);
            out = new FileOutputStream(new File(iconsDir(), item.pkg + ".png"));
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (IOException e) {
            Toast.makeText(this, "Could not apply icon", Toast.LENGTH_LONG).show();
        } finally {
            try {
                if (in != null) in.close();
            } catch (IOException ignored) {
            }
            try {
                if (out != null) out.close();
            } catch (IOException ignored) {
            }
        }
        lastSignature = "";
        rebuild(item.pkg);
    }

    private void startMove(Item item) {
        View tile = row.findViewWithTag(item.pkg);
        if (tile == null) return;
        movingTile = tile;
        tile.requestFocus();
        Toast.makeText(this, "Press left or right to move. Press OK when done.",
                Toast.LENGTH_LONG).show();
    }

    private void moveTile(int dir) {
        if (movingTile == null) return;
        int idx = row.indexOfChild(movingTile);
        int target = idx + dir;
        if (target < 0 || target > row.getChildCount() - 1) return;
        row.removeView(movingTile);
        row.addView(movingTile, target);
        movingTile.requestFocus();
        saveOrder();
    }

    private void saveOrder() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < row.getChildCount(); i++) {
            Object tag = row.getChildAt(i).getTag();
            if (tag != null) sb.append(tag.toString()).append('\n');
        }
        getSharedPreferences("depthclock", MODE_PRIVATE)
                .edit().putString("order", sb.toString()).apply();
    }

    private void endMove() {
        movingTile = null;
        Toast.makeText(this, "Order saved", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (movingTile != null && event.getAction() == KeyEvent.ACTION_DOWN) {
            int k = event.getKeyCode();
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) {
                moveTile(-1);
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                moveTile(1);
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                    || k == KeyEvent.KEYCODE_BACK) {
                endMove();
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
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

    private View makeTile(final Item item) {
        final FrameLayout tile = new FrameLayout(this);
        tile.setFocusable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(0xFF1B1D33);
        tile.setBackground(bg);
        tile.setClipToOutline(true);
        LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(dp(200), dp(112));
        tileLp.rightMargin = dp(16);
        tile.setLayoutParams(tileLp);
        tile.setTag(item.pkg);

        Drawable art = item.art;
        boolean banner = item.banner;
        if (item.custom != null) {
            Bitmap bm = decodeFile(item.custom, 800);
            if (bm != null) {
                art = new BitmapDrawable(getResources(), bm);
                banner = true;
            }
        }

        if (art != null && banner) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(art);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.addView(iv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            if (art != null) {
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(art);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(
                        dp(54), dp(54), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                ilp.topMargin = dp(14);
                tile.addView(iv, ilp);
            }
            TextView name = new TextView(this);
            name.setText(item.label);
            name.setTextColor(Color.WHITE);
            name.setTextSize(14);
            name.setSingleLine(true);
            name.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    art != null ? (Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL) : Gravity.CENTER);
            nlp.bottomMargin = dp(10);
            nlp.leftMargin = dp(8);
            nlp.rightMargin = dp(8);
            tile.addView(name, nlp);
        }

        final View ring = new View(this);
        GradientDrawable ringBg = new GradientDrawable();
        ringBg.setCornerRadius(dp(14));
        ringBg.setStroke(dp(3), 0xFFFFFFFF);
        ringBg.setColor(0x00000000);
        ring.setBackground(ringBg);
        ring.setAlpha(0f);
        tile.addView(ring, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float sc = hasFocus ? 1.1f : 1f;
                v.animate().scaleX(sc).scaleY(sc).setDuration(150).start();
                v.animate().translationZ(hasFocus ? dp(16) : 0f).setDuration(150).start();
                ring.animate().alpha(hasFocus ? 1f : 0f).setDuration(150).start();
            }
        });
        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(item.intent);
                } catch (Exception ignored) {
                }
            }
        });
        tile.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showEditor(item);
                return true;
            }
        });
        return tile;
    }
}
