package com.flurryos.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * FlurryOS Android-side desktop launcher.
 *
 * The screen deliberately uses framework widgets only so it can run on the
 * minimal AOSP/Cuttlefish image without a separate UI toolkit. The visual
 * language follows the Ubuntu desktop palette while keeping all launches
 * explicit and package-name validated.
 */
public final class MainActivity extends Activity {
    private static final int PURPLE = Color.rgb(61, 20, 53);
    private static final int PURPLE_DARK = Color.rgb(45, 15, 40);
    private static final int ORANGE = Color.rgb(233, 84, 32);
    private static final int INK = Color.rgb(44, 42, 41);
    private static final int MUTED = Color.rgb(103, 97, 95);
    private static final int SURFACE = Color.rgb(255, 255, 255);
    private static final int BACKGROUND = Color.rgb(246, 244, 243);

    private final List<AppEntry> allApps = new ArrayList<>();
    private PackageManager packageManager;
    private LinearLayout appGrid;
    private TextView appCount;
    private TextView runtimeStatus;
    private EditText searchField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        packageManager = getPackageManager();
        getWindow().setStatusBarColor(PURPLE_DARK);
        getWindow().setNavigationBarColor(Color.rgb(31, 29, 29));
        buildUserInterface();
        loadLaunchableApps();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void buildUserInterface() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);

        LinearLayout content = vertical(20);
        content.setPadding(20, 18, 20, 28);
        scroll.addView(content);

        content.addView(buildTopBar());
        content.addView(buildHeroCard(), marginParams(0, 4, 0, 0));
        content.addView(buildStatusCard(), marginParams(0, 0, 0, 0));
        content.addView(buildSearch(), marginParams(0, 0, 0, 2));

        LinearLayout sectionHeader = horizontal(0);
        TextView title = label("Aplicaciones", 22, INK, true);
        sectionHeader.addView(title, new LinearLayout.LayoutParams(0, -2, 1.0f));
        appCount = label("", 13, MUTED, false);
        appCount.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        sectionHeader.addView(appCount, new LinearLayout.LayoutParams(-2, -1));
        content.addView(sectionHeader);

        appGrid = new LinearLayout(this);
        appGrid.setOrientation(LinearLayout.VERTICAL);
        content.addView(appGrid, marginParams(0, 8, 0, 0));
        content.addView(buildQuickActions(), marginParams(0, 16, 0, 0));

        TextView footer = label("FlurryOS · Ubuntu / GNOME / Wayland · AOSP / Cuttlefish", 12, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        content.addView(footer, marginParams(0, 20, 0, 0));
        setContentView(scroll);
    }

    private View buildTopBar() {
        LinearLayout bar = horizontal(8);
        TextView mark = label("F", 28, Color.WHITE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(round(PURPLE, 14));
        bar.addView(mark, size(52, 52));

        LinearLayout names = vertical(0);
        TextView title = label("FlurryOS", 24, INK, true);
        TextView subtitle = label("Centro de aplicaciones", 13, MUTED, false);
        names.addView(title);
        names.addView(subtitle);
        bar.addView(names, new LinearLayout.LayoutParams(0, -2, 1.0f));

        Button refresh = compactButton("Actualizar", ORANGE, Color.WHITE);
        refresh.setOnClickListener(v -> {
            loadLaunchableApps();
            Toast.makeText(this, "Aplicaciones actualizadas", Toast.LENGTH_SHORT).show();
        });
        bar.addView(refresh, size(112, 46));
        return bar;
    }

    private View buildHeroCard() {
        LinearLayout card = vertical(5);
        card.setPadding(20, 18, 20, 18);
        card.setBackground(round(PURPLE, 18));
        TextView eyebrow = label("ESCRITORIO ANDROID DE FLURRYOS", 11, Color.rgb(245, 184, 157), true);
        TextView title = label("Tus aplicaciones, en un solo lugar", 22, Color.WHITE, true);
        TextView description = label("Lanza aplicaciones Android desde una experiencia de escritorio sencilla, rápida y familiar.", 14, Color.rgb(246, 229, 224), false);
        description.setMaxLines(3);
        card.addView(eyebrow);
        card.addView(title);
        card.addView(description, marginParams(0, 3, 0, 0));
        return card;
    }

    private View buildStatusCard() {
        LinearLayout card = horizontal(12);
        card.setPadding(16, 13, 16, 13);
        card.setBackground(round(SURFACE, 15));

        TextView dot = label("●", 18, ORANGE, true);
        card.addView(dot, size(22, -1));
        LinearLayout copy = vertical(1);
        runtimeStatus = label("Runtime Android listo", 15, INK, true);
        TextView detail = label("Cuttlefish · AOSP x86_64 · puente C++ activo", 12, MUTED, false);
        copy.addView(runtimeStatus);
        copy.addView(detail);
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1.0f));
        return card;
    }

    private View buildSearch() {
        searchField = new EditText(this);
        searchField.setSingleLine(true);
        searchField.setTextSize(15);
        searchField.setTextColor(INK);
        searchField.setHintTextColor(MUTED);
        searchField.setHint("Buscar aplicaciones...");
        searchField.setPadding(18, 0, 18, 0);
        searchField.setBackground(round(SURFACE, 15));
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderApps(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
        return searchField;
    }

    private View buildQuickActions() {
        LinearLayout card = vertical(8);
        card.setPadding(16, 14, 16, 14);
        card.setBackground(round(SURFACE, 15));
        card.addView(label("Accesos rápidos", 16, INK, true));
        LinearLayout actions = horizontal(10);
        Button settings = compactButton("Ajustes", PURPLE, Color.WHITE);
        settings.setOnClickListener(v -> openSettings());
        Button files = compactButton("Archivos", Color.rgb(238, 232, 229), INK);
        files.setOnClickListener(v -> openFiles());
        actions.addView(settings, new LinearLayout.LayoutParams(0, 44, 1.0f));
        actions.addView(files, new LinearLayout.LayoutParams(0, 44, 1.0f));
        card.addView(actions);
        return card;
    }

    private void loadLaunchableApps() {
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        allApps.clear();
        for (ResolveInfo info : packageManager.queryIntentActivities(query, PackageManager.MATCH_ALL)) {
            ActivityInfo activity = info.activityInfo;
            if (activity == null || getPackageName().equals(activity.packageName)) continue;
            String name = info.loadLabel(packageManager).toString();
            allApps.add(new AppEntry(name, activity.packageName, activity.name, info));
        }
        Collections.sort(allApps, Comparator.comparing(app -> app.label.toLowerCase(Locale.ROOT)));
        renderApps(searchField == null ? "" : searchField.getText().toString());
        if (runtimeStatus != null) runtimeStatus.setText("Runtime Android listo · " + allApps.size() + " aplicaciones");
    }

    private void renderApps(String query) {
        if (appGrid == null) return;
        appGrid.removeAllViews();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<AppEntry> visible = new ArrayList<>();
        for (AppEntry app : allApps) {
            if (needle.isEmpty() || app.label.toLowerCase(Locale.ROOT).contains(needle) || app.packageName.toLowerCase(Locale.ROOT).contains(needle)) visible.add(app);
        }
        if (appCount != null) appCount.setText(visible.size() + " disponibles");
        GridLayout row = null;
        for (int i = 0; i < visible.size(); i++) {
            if (i % 2 == 0) {
                row = new GridLayout(this);
                row.setColumnCount(2);
                appGrid.addView(row, marginParams(0, 0, 0, 10));
            }
            row.addView(appCard(visible.get(i)), new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED, 1, 1.0f), GridLayout.spec(GridLayout.UNDEFINED, 1, 1.0f)));
        }
        if (visible.isEmpty()) {
            TextView empty = label("No se encontraron aplicaciones.", 15, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            appGrid.addView(empty, marginParams(0, 24, 0, 24));
        }
    }

    private View appCard(AppEntry app) {
        LinearLayout card = horizontal(12);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(14, 13, 12, 13);
        card.setBackground(round(SURFACE, 15));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.info.loadIcon(packageManager));
        card.addView(icon, size(48, 48));
        LinearLayout names = vertical(2);
        names.addView(label(app.label, 15, INK, true));
        names.addView(label(app.packageName, 11, MUTED, false));
        card.addView(names, new LinearLayout.LayoutParams(0, -2, 1.0f));
        card.setOnClickListener(v -> launchActivity(app.packageName, app.activityName));
        card.setOnLongClickListener(v -> { Toast.makeText(this, app.packageName, Toast.LENGTH_SHORT).show(); return true; });
        return card;
    }

    private void handleIntent(Intent intent) {
        if (intent == null || !"com.flurryos.launcher.OPEN_PACKAGE".equals(intent.getAction())) return;
        String packageName = intent.getStringExtra("package");
        String activityName = intent.getStringExtra("activity");
        if (validPackage(packageName) && (activityName == null || validActivity(activityName))) {
            launchActivity(packageName, activityName);
        } else {
            Toast.makeText(this, "Identificador de aplicación no válido", Toast.LENGTH_SHORT).show();
        }
    }

    private void launchActivity(String packageName, String activityName) {
        if (!validPackage(packageName)) return;
        Intent launchIntent;
        if (activityName != null && validActivity(activityName)) {
            launchIntent = new Intent(Intent.ACTION_MAIN);
            launchIntent.setComponent(new ComponentName(packageName, activityName));
        } else {
            launchIntent = packageManager.getLaunchIntentForPackage(packageName);
        }
        if (launchIntent == null) {
            Toast.makeText(this, "La aplicación no tiene una actividad lanzable", Toast.LENGTH_SHORT).show();
            return;
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(launchIntent);
        } catch (ActivityNotFoundException | SecurityException exception) {
            Toast.makeText(this, "No se pudo iniciar la aplicación", Toast.LENGTH_SHORT).show();
        }
    }

    private void openSettings() {
        try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        catch (ActivityNotFoundException exception) { Toast.makeText(this, "Ajustes no disponible", Toast.LENGTH_SHORT).show(); }
    }

    private void openFiles() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivity(intent); }
        catch (ActivityNotFoundException exception) { Toast.makeText(this, "Gestor de archivos no disponible", Toast.LENGTH_SHORT).show(); }
    }

    private boolean validPackage(String value) {
        return value != null && value.length() <= 255 && value.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*");
    }

    private boolean validActivity(String value) {
        return value != null && value.length() <= 255 && value.matches("[A-Za-z0-9_$.]+" );
    }

    private LinearLayout vertical(int spacing) { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); view.setShowDividers(LinearLayout.SHOW_DIVIDER_NONE); return view; }
    private LinearLayout horizontal(int spacing) { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private TextView label(String text, float size, int color, boolean bold) { TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color); if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD); return view; }
    private Button compactButton(String text, int color, int textColor) { Button button = new Button(this); button.setText(text); button.setTextSize(13); button.setTextColor(textColor); button.setAllCaps(false); button.setPadding(8, 0, 8, 0); button.setBackground(round(color, 12)); return button; }
    private GradientDrawable round(int color, int radius) { GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(radius); return shape; }
    private LinearLayout.LayoutParams size(int width, int height) { return new LinearLayout.LayoutParams(width, height); }
    private LinearLayout.LayoutParams marginParams(int left, int top, int right, int bottom) { LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(left, top, right, bottom); return params; }

    private static final class AppEntry {
        final String label;
        final String packageName;
        final String activityName;
        final ResolveInfo info;
        AppEntry(String label, String packageName, String activityName, ResolveInfo info) { this.label = label; this.packageName = packageName; this.activityName = activityName; this.info = info; }
    }
}
