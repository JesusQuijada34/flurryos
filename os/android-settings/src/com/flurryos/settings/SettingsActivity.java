package com.flurryos.settings;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * First-boot FlurryOS settings application.
 *
 * This activity is packaged as a privileged/system app in the Android 17
 * image. It uses public Settings APIs where possible and delegates complex
 * configuration to Android's own settings panels instead of changing hidden
 * state through shell commands.
 */
public final class SettingsActivity extends Activity {
    private static final int PURPLE = Color.rgb(61, 20, 53);
    private static final int ORANGE = Color.rgb(233, 84, 32);
    private static final int INK = Color.rgb(44, 42, 41);
    private static final int MUTED = Color.rgb(103, 97, 95);
    private static final int BACKGROUND = Color.rgb(246, 244, 243);

    private TextView brightnessValue;
    private TextView setupStatus;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(PURPLE);
        getWindow().setNavigationBarColor(Color.rgb(31, 29, 29));
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = vertical();
        root.setBackgroundColor(BACKGROUND);
        root.setPadding(20, 18, 20, 28);

        LinearLayout header = horizontal();
        TextView mark = text("F", 28, Color.WHITE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(round(PURPLE, 14));
        header.addView(mark, size(52, 52));
        LinearLayout title = vertical();
        title.addView(text("FlurryOS Ajustes", 23, INK, true));
        title.addView(text("Configura tu dispositivo Android desde cero", 13, MUTED, false));
        header.addView(title, weight(1.0f));
        root.addView(header);

        setupStatus = text("Configuración inicial disponible", 14, Color.WHITE, true);
        setupStatus.setPadding(16, 14, 16, 14);
        setupStatus.setBackground(round(ORANGE, 14));
        root.addView(setupStatus, margins(0, 16, 0, 12));

        root.addView(section("Dispositivo"));
        root.addView(infoCard(), margins(0, 6, 0, 14));

        root.addView(section("Pantalla y apariencia"));
        root.addView(displayCard(), margins(0, 6, 0, 14));

        root.addView(section("Conectividad y aplicaciones"));
        root.addView(actionCard(), margins(0, 6, 0, 14));

        TextView footer = text("Android 17 · AOSP · Cuttlefish x86_64 · FlurryOS", 12, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, margins(0, 10, 0, 0));
        setContentView(root);
    }

    private View infoCard() {
        LinearLayout card = vertical();
        card.setPadding(16, 14, 16, 14);
        card.setBackground(round(Color.WHITE, 15));
        card.addView(infoRow("Versión Android", "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"));
        card.addView(infoRow("Arquitectura", Build.SUPPORTED_ABIS.length == 0 ? "desconocida" : Build.SUPPORTED_ABIS[0]));
        card.addView(infoRow("Dispositivo", safe(Build.MANUFACTURER) + " " + safe(Build.MODEL)));
        card.addView(infoRow("Motor", "AOSP/Cuttlefish con puente FlurryOS"));
        return card;
    }

    private View displayCard() {
        LinearLayout card = vertical();
        card.setPadding(16, 14, 16, 14);
        card.setBackground(round(Color.WHITE, 15));
        LinearLayout brightnessRow = horizontal();
        brightnessRow.addView(text("Brillo", 15, INK, true), weight(1.0f));
        brightnessValue = text("50%", 13, MUTED, false);
        brightnessRow.addView(brightnessValue, size(52, -2));
        card.addView(brightnessRow);
        SeekBar brightness = new SeekBar(this);
        brightness.setMax(100);
        brightness.setProgress(readBrightnessPercent());
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                brightnessValue.setText(progress + "%");
                if (fromUser) writeBrightness(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        card.addView(brightness, margins(0, 3, 0, 8));

        LinearLayout buttons = horizontal();
        Button light = button("Tema claro", Color.rgb(238, 232, 229), INK);
        light.setOnClickListener(v -> setNightMode(false));
        Button dark = button("Tema oscuro", PURPLE, Color.WHITE);
        dark.setOnClickListener(v -> setNightMode(true));
        buttons.addView(light, weight(1.0f));
        buttons.addView(dark, weight(1.0f));
        card.addView(buttons);
        return card;
    }

    private View actionCard() {
        LinearLayout card = vertical();
        card.setPadding(16, 14, 16, 14);
        card.setBackground(round(Color.WHITE, 15));
        addAction(card, "Wi-Fi y red", "Conecta el dispositivo virtual", Settings.ACTION_WIFI_SETTINGS);
        addAction(card, "Pantalla", "Resolución, rotación y escala", Settings.ACTION_DISPLAY_SETTINGS);
        addAction(card, "Aplicaciones", "Permisos y aplicaciones instaladas", Settings.ACTION_APPLICATION_SETTINGS);
        Button test = button("Comprobar configuración", ORANGE, Color.WHITE);
        test.setOnClickListener(v -> runSetupCheck());
        card.addView(test, margins(0, 8, 0, 0));
        return card;
    }

    private void addAction(LinearLayout parent, String title, String detail, String action) {
        Button button = button(title + "\n" + detail, Color.rgb(248, 247, 246), INK);
        button.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        button.setOnClickListener(v -> openPanel(action));
        parent.addView(button, margins(0, 0, 0, 8));
    }

    private void runSetupCheck() {
        boolean writable = Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(this);
        String status = writable ? "Configuración básica lista" : "Abre el permiso de modificación de ajustes para completar la configuración";
        setupStatus.setText(status);
        if (!writable && Build.VERSION.SDK_INT >= 23) {
            try { startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:" + getPackageName()))); }
            catch (ActivityNotFoundException ignored) { Toast.makeText(this, status, Toast.LENGTH_LONG).show(); }
        } else {
            Toast.makeText(this, status, Toast.LENGTH_SHORT).show();
        }
    }

    private int readBrightnessPercent() {
        try {
            int value = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
            return Math.max(1, Math.min(100, Math.round(value * 100.0f / 255.0f)));
        } catch (SecurityException exception) { return 50; }
    }

    private void writeBrightness(int percent) {
        try {
            int value = Math.max(1, Math.min(255, Math.round(percent * 255.0f / 100.0f)));
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, value);
        } catch (SecurityException exception) {
            Toast.makeText(this, "Se necesita permiso para modificar el brillo", Toast.LENGTH_SHORT).show();
        }
    }

    private void setNightMode(boolean dark) {
        if (Build.VERSION.SDK_INT >= 29) {
            UiModeManager manager = getSystemService(UiModeManager.class);
            if (manager != null) manager.setNightMode(dark ? UiModeManager.MODE_NIGHT_YES : UiModeManager.MODE_NIGHT_NO);
        }
        Toast.makeText(this, dark ? "Tema oscuro activado" : "Tema claro activado", Toast.LENGTH_SHORT).show();
    }

    private void openPanel(String action) {
        try { startActivity(new Intent(action)); }
        catch (ActivityNotFoundException exception) { Toast.makeText(this, "Panel no disponible en esta imagen", Toast.LENGTH_SHORT).show(); }
    }

    private View infoRow(String name, String value) {
        LinearLayout row = horizontal();
        row.setPadding(0, 5, 0, 5);
        row.addView(text(name, 14, MUTED, false), weight(1.0f));
        TextView right = text(value, 14, INK, true);
        right.setGravity(Gravity.RIGHT);
        right.setMaxLines(2);
        row.addView(right, weight(1.0f));
        return row;
    }

    private TextView section(String value) { return text(value.toUpperCase(Locale.ROOT), 12, PURPLE, true); }
    private LinearLayout vertical() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout horizontal() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private TextView text(String value, float size, int color, boolean bold) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD); return view; }
    private Button button(String value, int color, int textColor) { Button button = new Button(this); button.setText(value); button.setTextSize(13); button.setTextColor(textColor); button.setAllCaps(false); button.setPadding(10, 0, 10, 0); button.setBackground(round(color, 12)); return button; }
    private android.graphics.drawable.GradientDrawable round(int color, int radius) { android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable(); shape.setColor(color); shape.setCornerRadius(radius); return shape; }
    private LinearLayout.LayoutParams size(int width, int height) { return new LinearLayout.LayoutParams(width, height); }
    private LinearLayout.LayoutParams weight(float value) { return new LinearLayout.LayoutParams(0, -2, value); }
    private LinearLayout.LayoutParams margins(int left, int top, int right, int bottom) { LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(left, top, right, bottom); return params; }
    private String safe(String value) { return value == null ? "desconocido" : value; }
}
