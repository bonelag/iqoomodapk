package io.github.vivogameunlock;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public final class MainActivity extends Activity {
    private int INK, MUTED, ACCENT, surface, page;
    private boolean dark;
    private SharedPreferences prefs;
    private LinearLayout body;
    private Spinner fps;
    private int selectedTab;
    private LinearLayout navigation, appsList;
    private ScrollView scroll;
    private TextView gamesSummary, gamesCount;
    private ImageButton addGame;
    private EditText gamesSearch;
    private String gamesQuery = "", loadedMembers;
    private final ArrayList<AppChoice> managedApps = new ArrayList<>();
    private final android.util.LruCache<String, android.graphics.Bitmap> appIcons = new android.util.LruCache<>(80);

    private static final class AppChoice {
        final String name, pkg;
        AppChoice(String name, String pkg) { this.name = name; this.pkg = pkg; }
    }
    private String renderedGames;
    private String pendingId = "", pendingPackage = "", pendingOperation = "";
    private long pendingSince;
    private boolean pickerLoading;
    private final ExecutorService appExecutor = Executors.newSingleThreadExecutor();
    private boolean vietnamese;
    private LinearLayout statusCard;
    private TextView statusTitle, statusCount, statusDetail, statusIcon;
    private final android.os.Handler statusHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable statusRefresh = new Runnable() {
        @Override public void run() {
            if (selectedTab == 0) updateStatus();
            refreshGames();
            checkGameResult();
            statusHandler.postDelayed(this, 2000);
        }
    };

    private String tr(String english, String vietnameseText) {
        return vietnamese ? vietnameseText : english;
    }

    @Override public void onCreate(Bundle state) {
        prefs = ConfigStore.preferences(this);
        boolean systemDark = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        dark = prefs.contains("dark_theme") ? prefs.getBoolean("dark_theme", systemDark) : systemDark;
        setTheme(dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(state);
        vietnamese = "vi".equals(prefs.getString("language", "en"));
        page = dark ? 0xff080808 : 0xfff4f4f4;
        surface = dark ? 0xff212121 : 0xffffffff;
        INK = dark ? 0xffeeeeee : 0xff202020;
        MUTED = dark ? 0xffaaaaaa : 0xff696969;
        ACCENT = dark ? 0xffb5f1d0 : 0xff176e5b;
        getWindow().setStatusBarColor(page);
        getWindow().setNavigationBarColor(page);
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 :
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (state != null) {
            selectedTab = Math.max(0, Math.min(2, state.getInt("selected_tab", 0)));
            gamesQuery = state.getString("games_query", "");
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(page);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(page);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(24), dp(20), dp(28));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        navigation.setBackgroundColor(surface);
        root.addView(navigation, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        // Edge-to-edge Android 15 requires explicit IME insets as well as system bars.
        root.setOnApplyWindowInsetsListener((view, insets) -> {
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
        root.requestApplyInsets();
        showTab(selectedTab);
        ConfigStore.publish(this);
    }

    private void showTab(int tab) {
        selectedTab = tab;
        body.removeAllViews();
        statusCard = null;
        appsList = null;
        addGame = null;
        renderedGames = null;
        buildHeader();
        if (tab == 0) {
            addStatusCard();
            LinearLayout device = card(tr("About this device", "Thông tin thiết bị"));
            info(device, tr("Device", "Thiết bị"), Build.MODEL + " · " + Build.DEVICE);
            info(device, "Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
            info(device, "Cube Unlocker", version(getPackageName()));
            info(device, "GameCube", version("com.vivo.gamecube"));
            info(device, "GameWatch", version("com.vivo.gamewatch"));
            LinearLayout master = card(tr("Module", "Mô-đun"));
            toggle(master, "enabled", tr("Enable module", "Bật mô-đun"), tr("Master switch for all games", "Công tắc tổng cho mọi trò chơi"), true);
        } else if (tab == 1) {
            buildApps();
        } else {
            buildSettings();
        }
        buildNavigation();
        scroll.scrollTo(0, 0);
    }

    private void buildNavigation() {
        navigation.removeAllViews();
        String[] labels = {tr("Home", "Trang chủ"), tr("Apps", "Ứng dụng"), tr("Settings", "Cài đặt")};
        for (int i = 0; i < labels.length; i++) {
            final int tab = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(4), dp(8), dp(4), dp(8));
            item.setMinimumHeight(dp(64));
            item.setBackgroundResource(android.R.drawable.list_selector_background);
            item.setContentDescription(labels[i]);
            item.setSelected(i == selectedTab);
            item.setFocusable(true);
            int color = i == selectedTab ? ACCENT : MUTED;
            View icon = new View(this) {
                @Override protected void onDraw(android.graphics.Canvas canvas) {
                    super.onDraw(canvas);
                    canvas.save();
                    canvas.scale(getWidth() / 24f, getHeight() / 24f);
                    android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                    paint.setColor(color);
                    paint.setStyle(android.graphics.Paint.Style.STROKE);
                    paint.setStrokeWidth(2);
                    paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
                    if (tab == 0) {
                        android.graphics.Path path = new android.graphics.Path();
                        path.moveTo(3, 11); path.lineTo(12, 3); path.lineTo(21, 11);
                        path.moveTo(5, 10); path.lineTo(5, 21); path.lineTo(10, 21);
                        path.lineTo(10, 15); path.lineTo(14, 15); path.lineTo(14, 21);
                        path.lineTo(19, 21); path.lineTo(19, 10);
                        canvas.drawPath(path, paint);
                    } else if (tab == 1) {
                        for (int x = 4; x <= 14; x += 10)
                            for (int y = 4; y <= 14; y += 10)
                                canvas.drawRoundRect(x, y, x + 6, y + 6, 1, 1, paint);
                    } else {
                        canvas.drawCircle(12, 12, 7, paint);
                        canvas.drawCircle(12, 12, 3, paint);
                        for (int n = 0; n < 8; n++) {
                            canvas.drawLine(12, 2, 12, 5, paint);
                            canvas.rotate(45, 12, 12);
                        }
                    }
                    canvas.restore();
                }
            };
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            item.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
            TextView label = text(item, labels[i], 12, color, i == selectedTab);
            label.setGravity(Gravity.CENTER);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            navigation.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
            item.setOnClickListener(v -> showTab(tab));
        }
    }

    private void buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(header);
        TextView brand = text(header, "Cube Unlocker", 28, INK, false);
        brand.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton theme = new ImageButton(this);
        theme.setImageResource(getResources().getIdentifier("ic_theme", "drawable", getPackageName()));
        theme.setImageTintList(ColorStateList.valueOf(ACCENT));
        theme.setBackgroundResource(android.R.drawable.list_selector_background);
        theme.setContentDescription(tr("Appearance: system, light or dark", "Giao diện: hệ thống, sáng hoặc tối"));
        header.addView(theme, new LinearLayout.LayoutParams(dp(48), dp(48)));
        theme.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle(tr("Appearance", "Giao diện"))
                .setSingleChoiceItems(new String[]{tr("System default", "Theo hệ thống"), tr("Light", "Sáng"), tr("Dark", "Tối")},
                        prefs.contains("dark_theme") ? (dark ? 2 : 1) : 0, (dialog, which) -> {
                    SharedPreferences.Editor edit = prefs.edit();
                    if (which == 0) edit.remove("dark_theme"); else edit.putBoolean("dark_theme", which == 2);
                    edit.apply();
                    dialog.dismiss();
                    recreate();
                }).setNegativeButton(tr("Cancel", "Hủy"), null).show());
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
    }

    private void buildSettings() {
        LinearLayout features = card(tr("Defaults for new games", "Mặc định cho trò chơi mới"));
        text(features, tr("Copied when adding a game. Existing game profiles stay unchanged.",
                "Sao chép khi thêm trò chơi. Không thay đổi profile trò chơi đã có."), 13, MUTED, false);
        toggle(features, "memc", tr("Default frame interpolation", "Nội suy mặc định"), "MEMC", true);
        toggle(features, "vsr", tr("Default super resolution", "Siêu độ phân giải mặc định"), "VSR", true);
        LinearLayout frames = card(tr("Default frame rate", "Khung hình mặc định"));
        text(frames, tr("For newly added games", "Dành cho trò chơi thêm mới"), 13, MUTED, false);
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

        fps.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int target = new int[]{60, 90, 120}[position];
                if (prefs.getInt("target_fps", 120) == target) return;
                prefs.edit().putInt("target_fps", target).apply();
                ConfigStore.publish(MainActivity.this);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("selected_tab", selectedTab);
        state.putString("games_query", gamesQuery);
    }

    @Override protected void onDestroy() {
        appExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        sendGameCommand("list", "", false);
        statusHandler.removeCallbacks(statusRefresh);
        statusHandler.post(statusRefresh);
    }

    @Override protected void onPause() {
        statusHandler.removeCallbacks(statusRefresh);
        super.onPause();
    }

    private SharedPreferences runtime() {
        return createDeviceProtectedStorageContext().getSharedPreferences("runtime_status", 0);
    }

    private Set<String> games() {
        Set<String> result = new LinkedHashSet<>();
        for (String pkg : runtime().getString("games", "").split(";")) {
            pkg = pkg.trim();
            if (!pkg.isEmpty()) result.add(pkg);
        }
        return result;
    }

    private void buildApps() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(20), 0, dp(8));
        body.addView(header);
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        text(heading, tr("Your games", "Trò chơi của bạn"), 22, INK, true);
        gamesCount = text(heading, "", 12, ACCENT, true);
        ImageButton sync = iconButton("ic_refresh", tr("Refresh from GameCube", "Đồng bộ từ GameCube"));
        header.addView(sync, new LinearLayout.LayoutParams(dp(48), dp(48)));
        addGame = iconButton("ic_add", tr("Add game", "Thêm trò chơi"));
        addGame.setBackground(rounded(dark ? 0xff294c3c : 0xffd8f3e5, 16));
        header.addView(addGame, new LinearLayout.LayoutParams(dp(48), dp(48)));
        addGame.setOnClickListener(v -> showAppPicker());
        sync.setOnClickListener(v -> sendGameCommand("list", "", false));
        gamesSummary = text(body, "", 13, MUTED, false);
        gamesSummary.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        gamesSearch = searchField(body, tr("Search your games", "Tìm trò chơi của bạn"));
        gamesSearch.setText(gamesQuery);
        watchSearch(gamesSearch, () -> {
            gamesQuery = gamesSearch.getText().toString();
            renderManagedGames();
        });
        appsList = new LinearLayout(this);
        appsList.setOrientation(LinearLayout.VERTICAL);
        body.addView(appsList, new LinearLayout.LayoutParams(-1, -2));
        loadedMembers = null;
        refreshGames();
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private ImageButton iconButton(String icon, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(getResources().getIdentifier(icon, "drawable", getPackageName()));
        button.setImageTintList(ColorStateList.valueOf(ACCENT));
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setBackgroundResource(android.R.drawable.list_selector_background);
        button.setContentDescription(description);
        return button;
    }

    private EditText searchField(LinearLayout parent, String hint) {
        LinearLayout field = new LinearLayout(this);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(dp(14), dp(2), dp(10), dp(2));
        GradientDrawable shape = rounded(surface, 18);
        shape.setStroke(dp(1), dark ? 0xff43554b : 0xffc4d9cd);
        field.setBackground(shape);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(12);
        params.bottomMargin = dp(14);
        parent.addView(field, params);
        ImageView icon = new ImageView(this);
        icon.setImageResource(getResources().getIdentifier("ic_search", "drawable", getPackageName()));
        icon.setImageTintList(ColorStateList.valueOf(ACCENT));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        field.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setTextColor(INK);
        input.setHintTextColor(MUTED);
        input.setHint(hint);
        input.setContentDescription(hint);
        input.setBackground(null);
        input.setPadding(dp(12), dp(12), 0, dp(12));
        input.setMinimumHeight(dp(52));
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        field.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        return input;
    }

    private void watchSearch(EditText input, Runnable changed) {
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { changed.run(); }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });
    }

    private boolean matches(AppChoice app, String query) {
        String key = query.trim().toLowerCase(java.util.Locale.ROOT);
        return app.name.toLowerCase(java.util.Locale.ROOT).contains(key)
                || app.pkg.toLowerCase(java.util.Locale.ROOT).contains(key);
    }

    private LinearLayout appRow(AppChoice app) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(4), dp(12));
        row.setMinimumHeight(dp(80));
        ImageView logo = new ImageView(this);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(logo, new LinearLayout.LayoutParams(dp(48), dp(48)));
        bindAppIcon(logo, app.pkg);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, dp(4), 0);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView name = text(labels, app.name, 16, INK, true);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView pkg = text(labels, app.pkg, 12, MUTED, false);
        pkg.setSingleLine(true);
        pkg.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        return row;
    }

    private void bindAppIcon(ImageView view, String pkg) {
        view.setTag(pkg);
        android.graphics.Bitmap cached = appIcons.get(pkg);
        if (cached != null) { view.setImageBitmap(cached); return; }
        view.setImageDrawable(getPackageManager().getDefaultActivityIcon());
        appExecutor.execute(() -> {
            android.graphics.drawable.Drawable icon;
            try { icon = getPackageManager().getApplicationIcon(pkg); }
            catch (PackageManager.NameNotFoundException | RuntimeException missing) {
                icon = getPackageManager().getDefaultActivityIcon();
            }
            int size = dp(48);
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
            icon.setBounds(0, 0, size, size);
            icon.draw(new android.graphics.Canvas(bitmap));
            appIcons.put(pkg, bitmap);
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed() && pkg.equals(view.getTag())) view.setImageBitmap(bitmap);
            });
        });
    }

    private void refreshGames() {
        if (appsList == null) return;
        SharedPreferences status = runtime();
        int count = games().size();
        gamesSummary.setText(count > 0 ? count + tr(" games synced", " trò chơi đã đồng bộ")
                : tr("No games yet. Add one with +.", "Chưa có trò chơi. Thêm bằng nút +."));
        gamesCount.setText(count + tr(" games", " trò chơi"));
        addGame.setEnabled(!pickerLoading);
        addGame.setAlpha(addGame.isEnabled() ? 1f : 0.4f);
        String snapshot = status.getString("games", "") + "\n" + prefs.getString("profiles", "{}");
        if (snapshot.equals(renderedGames)) return;
        renderedGames = snapshot;
        String membership = status.getString("games", "");
        if (!membership.equals(loadedMembers)) {
            loadedMembers = membership;
            managedApps.clear();
            renderManagedGames();
            Set<String> members = games();
            appExecutor.execute(() -> {
                ArrayList<AppChoice> loaded = new ArrayList<>();
                for (String pkg : members) {
                    if (Thread.currentThread().isInterrupted()) return;
                    loaded.add(new AppChoice(appLabel(pkg), pkg));
                }
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || appsList == null || !membership.equals(loadedMembers)) return;
                    managedApps.clear();
                    managedApps.addAll(loaded);
                    renderManagedGames();
                });
            });
        } else renderManagedGames();
    }

    private void renderManagedGames() {
        if (appsList == null) return;
        appsList.removeAllViews();
        int shown = 0;
        Set<String> members = games();
        for (AppChoice app : managedApps) {
            if (!members.contains(app.pkg) || !matches(app, gamesQuery)) continue;
            shown++;
            String pkg = app.pkg, name = app.name;
            LinearLayout row = appRow(app);
            row.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(dark ? 0x3354d995 : 0x22176e5b),
                    rounded(surface, 20), null));
            LinearLayout labels = (LinearLayout) row.getChildAt(1);
            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.VERTICAL);
            labels.addView(chips);
            try {
                JSONObject profile = profile(pkg);
                profileChip(chips, "MEMC " + onOff(profile.optBoolean("memc", true)) + " · VSR "
                        + onOff(profile.optBoolean("vsr", true)));
                profileChip(chips, profile.optInt("target_fps", 120) + " FPS");
            } catch (JSONException error) {
                text(chips, tr("Invalid profile data", "Dữ liệu profile không hợp lệ"), 12, MUTED, false);
            }
            row.setFocusable(true);
            row.setOnClickListener(v -> editProfile(pkg));
            StringBuilder description = new StringBuilder(name).append(", ").append(pkg);
            for (int i = 0; i < chips.getChildCount(); i++)
                description.append(", ").append(((TextView) chips.getChildAt(i)).getText());
            row.setContentDescription(description.append(", ").append(tr("Edit profile", "Sửa profile")).toString());
            ImageButton remove = iconButton("ic_delete", tr("Remove ", "Xóa ") + name);
            row.addView(remove, new LinearLayout.LayoutParams(dp(48), dp(48)));
            remove.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                    .setTitle(tr("Remove game", "Xóa trò chơi"))
                    .setMessage(tr("Remove from GameCube? Profile will be kept.\n", "Xóa khỏi GameCube? Profile vẫn được giữ.\n") + name)
                    .setNegativeButton(tr("Cancel", "Hủy"), null)
                    .setPositiveButton(tr("Remove", "Xóa"), (dialog, which) -> sendGameCommand("remove", pkg, true)).show());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(10);
            appsList.addView(row, params);
        }
        if (shown == 0) {
            TextView empty = text(appsList, members.isEmpty()
                    ? tr("No games yet\nAdd a game after GameCube syncs.", "Chưa có trò chơi\nThêm trò chơi sau khi GameCube đồng bộ.")
                    : managedApps.isEmpty() ? tr("Loading games…", "Đang tải trò chơi…")
                    : tr("No matching games\nTry a name or package name.", "Không tìm thấy trò chơi\nThử tên hoặc tên gói ứng dụng."), 14, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(32), dp(16), dp(32));
            empty.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        }
    }

    private void profileChip(LinearLayout parent, String label) {
        TextView chip = text(parent, label, 11, ACCENT, true);
        chip.setPadding(dp(8), dp(3), dp(8), dp(3));
        chip.setBackground(rounded(dark ? 0xff294438 : 0xffe3f3e9, 8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.topMargin = dp(4);
        chip.setLayoutParams(params);
    }

    private String onOff(boolean value) {
        return value ? tr("On", "Bật") : tr("Off", "Tắt");
    }

    private String appLabel(String pkg) {
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException | SecurityException missing) {
            return pkg;
        }
    }

    private JSONObject defaults() throws JSONException {
        int target = prefs.getInt("target_fps", 120);
        return new JSONObject().put("memc", prefs.getBoolean("memc", true))
                .put("vsr", prefs.getBoolean("vsr", true))
                .put("target_fps", target == 60 || target == 90 ? target : 120);
    }

    private JSONObject profile(String pkg) throws JSONException {
        JSONObject profiles = new JSONObject(prefs.getString("profiles", "{}"));
        if (!profiles.has(pkg)) return defaults();
        return profiles.getJSONObject(pkg);
    }

    private void saveProfile(String pkg, JSONObject profile) throws JSONException {
        JSONObject profiles = new JSONObject(prefs.getString("profiles", "{}"));
        profiles.put(pkg, profile);
        String encoded = profiles.toString();
        if (!encoded.equals(ConfigContract.limited(encoded)))
            throw new IllegalStateException(tr("Profile storage is full. No changes saved.", "Bộ nhớ profile đã đầy. Chưa lưu thay đổi."));
        prefs.edit().putString("profiles", encoded).apply();
        ConfigStore.publish(this);
        renderedGames = null;
        refreshGames();
    }

    private void editProfile(String pkg) {
        try {
            JSONObject current = profile(pkg);
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(24), dp(8), dp(24), dp(8));
            Switch memc = profileSwitch(content, "MEMC", current.optBoolean("memc", true));
            Switch vsr = profileSwitch(content, "VSR", current.optBoolean("vsr", true));
            text(content, tr("Target frame rate", "Khung hình mục tiêu"), 14, MUTED, false);
            Spinner target = new Spinner(this);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                    new String[]{"60 FPS", "90 FPS", "120 FPS"});
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            target.setAdapter(adapter);
            int value = current.optInt("target_fps", 120);
            target.setSelection(value == 60 ? 0 : value == 90 ? 1 : 2);
            target.setContentDescription(tr("Target frame rate", "Khung hình mục tiêu"));
            content.addView(target, new LinearLayout.LayoutParams(-1, dp(56)));
            android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                    .setTitle(appLabel(pkg)).setView(content)
                    .setNegativeButton(tr("Cancel", "Hủy"), null)
                    .setPositiveButton(tr("Save", "Lưu"), null).create();
            dialog.setOnShowListener(ignored -> dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    saveProfile(pkg, new JSONObject().put("memc", memc.isChecked()).put("vsr", vsr.isChecked())
                            .put("target_fps", new int[]{60, 90, 120}[target.getSelectedItemPosition()]));
                    dialog.dismiss();
                } catch (JSONException | RuntimeException error) {
                    showError(tr("Could not save profile: ", "Không lưu được profile: ") + error.getMessage());
                }
            }));
            dialog.show();
        } catch (JSONException | RuntimeException error) {
            showError(tr("Could not read profile: ", "Không đọc được profile: ") + error.getMessage());
        }
    }

    private Switch profileSwitch(LinearLayout parent, String label, boolean checked) {
        Switch view = new Switch(this);
        view.setText(label);
        view.setTextColor(INK);
        view.setChecked(checked);
        view.setMinimumHeight(dp(56));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private void showAppPicker() {
        if (pickerLoading) return;
        pickerLoading = true;
        refreshGames();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(4), dp(20), 0);
        content.setBackgroundColor(page);
        EditText search = searchField(content, tr("Search installed apps", "Tìm ứng dụng đã cài"));
        FrameLayout results = new FrameLayout(this);
        content.addView(results, new LinearLayout.LayoutParams(-1, Math.min(dp(400), getResources().getDisplayMetrics().heightPixels / 2)));
        ListView list = new ListView(this);
        list.setDivider(null);
        list.setClipToPadding(false);
        results.addView(list, new FrameLayout.LayoutParams(-1, -1));
        TextView empty = new TextView(this);
        empty.setText(tr("Loading installed apps…", "Đang tải ứng dụng đã cài…"));
        empty.setTextColor(MUTED);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(16), dp(16), dp(16), dp(16));
        empty.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        results.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        list.setEmptyView(empty);
        ArrayList<AppChoice> choices = new ArrayList<>(), filtered = new ArrayList<>();
        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return filtered.size(); }
            @Override public Object getItem(int position) { return filtered.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                AppChoice app = filtered.get(position);
                if (convertView == null) return appRow(app);
                LinearLayout row = (LinearLayout) convertView;
                bindAppIcon((ImageView) row.getChildAt(0), app.pkg);
                LinearLayout labels = (LinearLayout) row.getChildAt(1);
                ((TextView) labels.getChildAt(0)).setText(app.name);
                ((TextView) labels.getChildAt(1)).setText(app.pkg);
                return row;
            }
        };
        list.setAdapter(adapter);
        Runnable filter = () -> {
            filtered.clear();
            for (AppChoice app : choices) if (matches(app, search.getText().toString())) filtered.add(app);
            if (!pickerLoading) empty.setText(choices.isEmpty()
                    ? tr("No more apps available to add.", "Không còn ứng dụng có thể thêm.")
                    : tr("No matching apps. Try a name or package name.", "Không tìm thấy ứng dụng. Thử tên hoặc tên gói."));
            adapter.notifyDataSetChanged();
        };
        watchSearch(search, filter);
        android.app.AlertDialog picker = new android.app.AlertDialog.Builder(this)
                .setTitle(tr("Add game", "Thêm trò chơi")).setView(content)
                .setNegativeButton(tr("Cancel", "Hủy"), null).create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            String pkg = filtered.get(position).pkg;
            if (games().contains(pkg)) {
                showError(tr("Game is already in the list.", "Trò chơi đã có trong danh sách."));
                return;
            }
            try {
                ApplicationInfo app = getPackageManager().getApplicationInfo(pkg, 0);
                if (!app.enabled || (app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                        || getPackageManager().getLaunchIntentForPackage(pkg) == null)
                    throw new IllegalStateException(tr("App is no longer available.", "Ứng dụng không còn khả dụng."));
                // Persist defaults before GameCube can acknowledge membership asynchronously.
                saveProfile(pkg, defaults());
                sendGameCommand("add", pkg, true);
                picker.dismiss();
            } catch (JSONException | PackageManager.NameNotFoundException | RuntimeException error) {
                showError(tr("Could not add game: ", "Không thêm được trò chơi: ") + error.getMessage());
            }
        });
        picker.show();
        picker.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        Set<String> members = games();
        appExecutor.execute(() -> {
            ArrayList<AppChoice> loaded = new ArrayList<>();
            String failure = null;
            try {
                PackageManager pm = getPackageManager();
                for (ApplicationInfo app : pm.getInstalledApplications(0)) {
                    if (Thread.currentThread().isInterrupted()) return;
                    if (!app.enabled || (app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                            || app.packageName.equals(getPackageName()) || members.contains(app.packageName)
                            || pm.getLaunchIntentForPackage(app.packageName) == null) continue;
                    loaded.add(new AppChoice(pm.getApplicationLabel(app).toString(), app.packageName));
                }
                java.text.Collator collator = java.text.Collator.getInstance(vietnamese ? new java.util.Locale("vi") : java.util.Locale.ENGLISH);
                loaded.sort((a, b) -> {
                    int order = collator.compare(a.name, b.name);
                    return order != 0 ? order : a.pkg.compareTo(b.pkg);
                });
            } catch (RuntimeException error) {
                failure = error.getMessage();
                if (failure == null) failure = error.getClass().getSimpleName();
            }
            final String queryError = failure;
            runOnUiThread(() -> {
                pickerLoading = false;
                if (isFinishing() || isDestroyed()) return;
                refreshGames();
                if (!picker.isShowing()) return;
                if (queryError != null) {
                    empty.setText(tr("Could not load apps: ", "Không tải được ứng dụng: ") + queryError);
                    search.setEnabled(false);
                    return;
                }
                choices.addAll(loaded);
                filter.run();
            });
        });
    }

    private void sendGameCommand(String operation, String pkg, boolean feedback) {
        String id = UUID.randomUUID().toString();
        if (feedback) {
            pendingId = id;
            pendingPackage = pkg;
            pendingOperation = operation;
            pendingSince = android.os.SystemClock.elapsedRealtime();
        }
        try {
            sendBroadcast(new Intent(ConfigContract.GAME_ACTION).setPackage("com.vivo.gamecube")
                    .putExtra("protocol", 1).putExtra("operation", operation).putExtra("package", pkg)
                    .putExtra("request_id", id));
        } catch (RuntimeException error) {
            if (feedback) {
                pendingId = "";
                showError(tr("Could not contact GameCube: ", "Không liên hệ được GameCube: ") + error.getMessage());
            }
        }
        refreshGames();
    }

    private void checkGameResult() {
        if (pendingId.isEmpty()) return;
        SharedPreferences status = runtime();
        boolean matched = pendingId.equals(status.getString("game_result_id", ""));
        String error = matched ? status.getString("game_error", "") : "";
        boolean membershipConfirmed = !"list".equals(pendingOperation)
                && ("add".equals(pendingOperation) == games().contains(pendingPackage));
        long age = android.os.SystemClock.elapsedRealtime() - pendingSince;
        if (matched && !error.isEmpty()) {
            pendingId = "";
            showError(error);
        } else if ((matched && "list".equals(pendingOperation)) || membershipConfirmed) {
            pendingId = "";
            Toast.makeText(this, tr("GameCube synced", "Đã đồng bộ GameCube"), Toast.LENGTH_SHORT).show();
        } else if (age < 0 || age > 8000) {
            // Never block the UI on a missing or delayed reply; the periodic list is authoritative.
            boolean requestedChange = !"list".equals(pendingOperation);
            pendingId = "";
            if (requestedChange) showError(tr("GameCube did not confirm the change. Press refresh to see the current list.",
                    "GameCube chưa xác nhận thay đổi. Nhấn làm mới để xem danh sách hiện tại."));
        }
        refreshGames();
    }

    private void showError(String message) {
        if (isFinishing() || isDestroyed()) return;
        new android.app.AlertDialog.Builder(this).setTitle(tr("GameCube", "GameCube"))
                .setMessage(message).setPositiveButton("OK", null).show();
    }

    private void addStatusCard() {
        statusCard = new LinearLayout(this);
        statusCard.setGravity(Gravity.CENTER_VERTICAL);
        statusCard.setPadding(dp(20), dp(18), dp(16), dp(18));
        statusCard.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(18);
        body.addView(statusCard, params);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        statusCard.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        statusTitle = text(labels, "", 23, 0xffffd9d4, true);
        statusCount = text(labels, "", 15, 0xffffd9d4, false);
        statusDetail = text(labels, "", 14, 0xffffd9d4, false);
        statusIcon = new TextView(this);
        statusIcon.setGravity(Gravity.CENTER);
        statusIcon.setTextSize(42);
        statusIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        statusCard.addView(statusIcon, new LinearLayout.LayoutParams(dp(64), dp(64)));
        updateStatus();
    }

    private void updateStatus() {
        SharedPreferences runtime = createDeviceProtectedStorageContext().getSharedPreferences("runtime_status", 0);
        long heartbeat = runtime.getLong("heartbeat", -1);
        // elapsedRealtime resets on reboot, so a negative age already means the heartbeat is stale.
        long age = android.os.SystemClock.elapsedRealtime() - heartbeat;
        boolean connected = heartbeat >= 0 && age >= 0 && age < 60000;
        boolean enabled = prefs.getBoolean("enabled", true);
        boolean ready = connected && enabled;
        int foreground = ready ? 0xffb5f1d0 : 0xffffada5;
        int background = ready ? 0xff173f32 : 0xff482022;
        if (connected && !enabled) { foreground = 0xffffd58b; background = 0xff49391d; }
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(background);
        shape.setCornerRadius(dp(26));
        statusCard.setBackground(shape);
        statusTitle.setText(ready ? tr("Module ready", "Mô-đun đã sẵn sàng")
                : connected ? tr("Module disabled", "Mô-đun đang tắt")
                : tr("Module not ready", "Mô-đun chưa sẵn sàng"));
        boolean gamesKnown = runtime.contains("games");
        statusCount.setText(gamesKnown ? runtime.getInt("game_count", 0) + tr(" games in Ultra Game Mode", " trò chơi trong Ultra Game Mode")
                : tr("Syncing game list…", "Đang đồng bộ danh sách game…"));
        statusDetail.setText(ready ? tr("LSPosed connected. Applies to games in Super game mode.",
                "Đã kết nối LSPosed. Áp dụng cho game trong Chế độ siêu trò chơi.")
                : connected ? tr("LSPosed connected. Enable the module below.", "Đã kết nối LSPosed. Bật mô-đun bên dưới.")
                : tr("No LSPosed connection. Check the framework, enable the module in LSPosed, then reboot.",
                "Chưa kết nối LSPosed. Kiểm tra framework, bật mô-đun trong LSPosed rồi khởi động lại."));
        for (TextView view : new TextView[]{statusTitle, statusCount, statusDetail, statusIcon}) view.setTextColor(foreground);
        statusIcon.setText(ready ? "✓" : "×");
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setStroke(dp(4), foreground);
        statusIcon.setBackground(ring);
    }

    private String version(String pkg) {
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(pkg, 0);
            return info.versionName + " (" + info.getLongVersionCode() + ")";
        } catch (android.content.pm.PackageManager.NameNotFoundException missing) {
            return tr("Not installed", "Chưa cài đặt");
        }
    }

    private void info(LinearLayout parent, String title, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(36));
        row.setPadding(0, dp(3), 0, dp(3));
        TextView label = text(row, title, 13, MUTED, false);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        TextView detail = text(row, value, 13, INK, false);
        detail.setGravity(Gravity.END);
        detail.setPadding(dp(12), 0, 0, 0);
        detail.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        parent.addView(row);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private LinearLayout card(String title) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(14), dp(18), dp(14));
        GradientDrawable background = new GradientDrawable();
        background.setColor(surface);
        background.setCornerRadius(dp(22));
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
        item.setTrackTintList(new ColorStateList(states, new int[]{dark ? 0xff346b50 : 0xffb7d7c9, dark ? 0xff505050 : 0xffdce3df}));
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

}
