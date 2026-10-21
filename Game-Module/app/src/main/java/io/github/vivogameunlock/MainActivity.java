package io.github.vivogameunlock;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public final class MainActivity extends Activity {
    private static final int INK = 0xff172e2b, MUTED = 0xff62746f, ACCENT = 0xff176e5b;
    private SharedPreferences prefs;
    private LinearLayout body;
    private EditText selected, excluded;
    private Spinner fps;
    private boolean vietnamese;

    private String tr(String english, String vietnameseText) {
        return vietnamese ? vietnameseText : english;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = ConfigStore.preferences(this);
        vietnamese = "vi".equals(prefs.getString("language", "en"));
        getWindow().setStatusBarColor(0xffedf3ef);
        getWindow().setNavigationBarColor(0xffedf3ef);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xffedf3ef, 0xfffaf8f2}));
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(24), dp(20), dp(28));
        scroll.addView(body);
        setContentView(scroll);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        // Edge-to-edge Android 15 requires explicit IME insets as well as system bars.
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.displayCutout());
            int keyboard = insets.getInsets(android.view.WindowInsets.Type.ime()).bottom;
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard));
            if (insets.isVisible(android.view.WindowInsets.Type.ime())) {
                view.post(() -> {
                    View focused = scroll.findFocus();
                    if (focused instanceof EditText) {
                        android.graphics.Rect area = new android.graphics.Rect();
                        focused.getDrawingRect(area);
                        focused.requestRectangleOnScreen(area, false);
                    }
                });
            }
            return insets;
        });
        scroll.requestApplyInsets();

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(header);
        TextView brand = text(header, "VIVO / GAME TOOLS", 12, ACCENT, true);
        brand.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton language = new ImageButton(this);
        language.setImageResource(getResources().getIdentifier("ic_language", "drawable", getPackageName()));
        language.setImageTintList(ColorStateList.valueOf(ACCENT));
        language.setBackgroundResource(android.R.drawable.list_selector_background);
        language.setContentDescription(tr("Choose language", "Chọn ngôn ngữ"));
        header.addView(language, new LinearLayout.LayoutParams(dp(48), dp(48)));
        language.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle(tr("Language", "Ngôn ngữ"))
                .setSingleChoiceItems(new String[]{"English", "Tiếng Việt"}, vietnamese ? 1 : 0, (dialog, which) -> {
                    prefs.edit().putString("language", which == 1 ? "vi" : "en").apply();
                    dialog.dismiss();
                    if (vietnamese != (which == 1)) recreate();
                }).setNegativeButton(tr("Cancel", "Hủy"), null).show());
        text(body, "Game Unlock", 34, INK, true);
        text(body, tr("Tune your gaming experience · 0.1.3", "Điều chỉnh trải nghiệm chơi game · 0.1.3"), 14, MUTED, false);

        LinearLayout device = card(tr("Device", "Thiết bị"));
        text(device, Build.MODEL + " / " + Build.DEVICE, 21, INK, true);
        text(device, "Android " + Build.VERSION.RELEASE + "   ·   SDK " + Build.VERSION.SDK_INT, 14, MUTED, false);
        text(device, "GameCube 14.0.10.00_ex\nGameWatch 25.0.0.03", 13, MUTED, false);

        LinearLayout features = card(tr("Features", "Chức năng"));
        toggle(features, "enabled", tr("Enable module", "Mở khóa tính năng"), tr("Turn the module on or off", "Bật hoặc tắt module"), true);
        toggle(features, "memc", tr("Frame interpolation", "Nội suy khung hình"), tr("MEMC · Smoother motion", "MEMC · Chuyển động mượt hơn"), true);
        toggle(features, "vsr", tr("Super resolution", "Siêu độ phân giải"), tr("VSR · Enhanced image detail", "VSR · Tăng độ chi tiết hình ảnh"), true);
        LinearLayout frames = card(tr("Frame rate", "Khung hình"));
        text(frames, tr("Interpolation profile", "Profile nội suy"), 17, INK, true);
        text(frames, tr("For games without a vivo profile", "Áp dụng cho game chưa có cấu hình vivo"), 13, MUTED, false);
        fps = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,
                new String[]{"30 → 60 FPS", "45 → 90 FPS", "60 → 120 FPS"}) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView)super.getView(position, convertView, parent);
                view.setTextColor(ACCENT);
                view.setTextSize(20);
                view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fps.setAdapter(adapter);
        fps.setContentDescription(tr("Frame interpolation profile", "Profile nội suy khung hình"));
        fps.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        int value = prefs.getInt("target_fps", 120);
        fps.setSelection(value == 60 ? 0 : value == 90 ? 1 : 2);
        frames.addView(fps, new LinearLayout.LayoutParams(-1, dp(56)));

        LinearLayout apps = card(tr("Applications", "Ứng dụng"));
        toggle(apps, "all_apps", tr("All applications", "Mọi ứng dụng"), tr("In vivo Game Mode", "Trong Game Mode của vivo"), true);
        String allowlist = tr("Allowed packages", "Danh sách cho phép");
        TextView selectedLabel = text(apps, allowlist, 15, INK, true);
        text(apps, tr("When All applications is off · One package per line", "Dùng khi tắt Mọi ứng dụng · Mỗi dòng một package"), 12, MUTED, false);
        selected = input(apps, prefs.getString("selected", ""), allowlist);
        selectedLabel.setLabelFor(selected.getId());
        String exclusions = tr("Excluded packages", "Danh sách loại trừ");
        TextView excludedLabel = text(apps, exclusions, 15, INK, true);
        text(apps, tr("Keep the original vivo configuration", "Giữ cấu hình gốc của vivo"), 12, MUTED, false);
        excluded = input(apps, prefs.getString("excluded", ""), exclusions);
        excludedLabel.setLabelFor(excluded.getId());

        fps.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int target = new int[]{60, 90, 120}[position];
                if (prefs.getInt("target_fps", 120) == target) return;
                prefs.edit().putInt("target_fps", target).apply();
                ConfigStore.publish(MainActivity.this);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        watch(selected, "selected");
        watch(excluded, "excluded");
        ConfigStore.publish(this);
    }

    private void watch(EditText field, String key) {
        field.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                prefs.edit().putString(key, ConfigContract.limited(s.toString())).apply();
                ConfigStore.publish(MainActivity.this);
            }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private LinearLayout card(String title) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(14), dp(18), dp(14));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xfffefefd);
        background.setCornerRadius(dp(20));
        background.setStroke(dp(1), 0xffdfe7e1);
        box.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(18);
        body.addView(box, params);
        text(box, title, 12, ACCENT, true);
        return box;
    }

    private void toggle(LinearLayout parent, String key, String label, String detail, boolean def) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(76));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        text(labels, label, 16, INK, true);
        text(labels, detail, 12, MUTED, false);
        Switch item = new Switch(this);
        item.setMinimumWidth(dp(48));
        item.setMinimumHeight(dp(48));
        item.setContentDescription(label);
        item.setChecked(prefs.getBoolean(key, def));
        int[][] states = new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}};
        item.setThumbTintList(new ColorStateList(states, new int[]{ACCENT, 0xff82918b}));
        item.setTrackTintList(new ColorStateList(states, new int[]{0xffb7d7c9, 0xffdce3df}));
        row.addView(item);
        row.setOnClickListener(v -> item.toggle());
        item.setOnCheckedChangeListener((v, checked) -> {
            prefs.edit().putBoolean(key, checked).apply();
            ConfigStore.publish(this);
        });
        parent.addView(row);
    }

    private TextView text(LinearLayout parent, String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        view.setPadding(0, dp(4), 0, dp(4));
        parent.addView(view);
        return view;
    }

    private EditText input(LinearLayout parent, String value, String label) {
        EditText view = new EditText(this);
        view.setId(View.generateViewId());
        view.setText(value);
        view.setTextColor(INK);
        view.setHintTextColor(MUTED);
        view.setTextSize(14);
        view.setTypeface(Typeface.MONOSPACE);
        view.setMinLines(2);
        view.setGravity(Gravity.TOP | Gravity.START);
        view.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        view.setHint("com.example.game");
        view.setContentDescription(label);
        view.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xfff2f5f1);
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), 0xffd8e2da);
        view.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        params.bottomMargin = dp(12);
        parent.addView(view, params);
        return view;
    }
}
