package io.github.vivogameunlock;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;

public final class MainActivity extends Activity {
    private SharedPreferences prefs;
    private LinearLayout body;
    private EditText selected, excluded;
    private Spinner fps;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("config", 0);
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(20 * getResources().getDisplayMetrics().density);
        body.setPadding(pad, pad, pad, pad); scroll.addView(body); setContentView(scroll);
        text("Vivo Game Unlock · 0.1.1", 24);
        text("Dành cho PD2507 / Android 15\nGameCube 14.0.10.00_ex · GameWatch 25.0.0.03", 14);
        text("Scope LSPosed: System Framework, GameCube, GameWatch. Module không tự cài, kích hoạt, dừng app hay khởi động lại máy.", 15);
        toggle("enabled", "Mở khóa tính năng", true);
        toggle("all_apps", "Cho phép mọi ứng dụng người dùng khi dùng Game Mode", true);
        toggle("memc", "Nội suy khung hình (MEMC)", true);
        toggle("vsr", "Siêu độ phân giải (VSR)", true);
        toggle("vaa", "Khử răng cưa (VAA, theo API phần cứng hỗ trợ)", true);
        text("Profile nội suy cho game chưa có cấu hình vivo", 16);
        fps = new Spinner(this);
        fps.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"30 → 60 FPS", "45 → 90 FPS", "60 → 120 FPS"}));
        int value = prefs.getInt("target_fps", 120);
        fps.setSelection(value == 60 ? 0 : value == 90 ? 1 : 2); body.addView(fps);
        text("Danh sách cho phép (khi tắt chế độ mọi ứng dụng). Mỗi dòng một package:", 14);
        selected = input(prefs.getString("selected", ""));
        text("Loại trừ mở khóa. Các game này dùng hành vi gốc của vivo:", 14);
        excluded = input(prefs.getString("excluded", ""));
        Button save = new Button(this); save.setText("Lưu cấu hình"); body.addView(save);
        save.setOnClickListener(v -> {
            prefs.edit().putInt("target_fps", new int[]{60,90,120}[fps.getSelectedItemPosition()])
                    .putString("selected", selected.getText().toString())
                    .putString("excluded", excluded.getText().toString()).apply();
            ConfigStore.publish(this);
            Toast.makeText(this, "Đã lưu và gửi cấu hình tới các scope. Mở lại sidebar; kiểm tra CONFIG_READY để xác nhận hook đã nhận.", Toast.LENGTH_LONG).show();
        });
        text("Sau khi cập nhật module, hãy nhấn Lưu cấu hình. Gửi cấu hình chưa chứng minh hook đã nhận; xem CONFIG_READY hoặc CONFIG_RECEIVED trong log LSPosed.", 14);
        text("Nút bật trên sidebar và yêu cầu tới backend chưa chứng minh có khung hình nội suy/VSR đầu ra. Bản sửa 0.1.1 chưa được thử runtime trên điện thoại.", 14);
        text("Game đã có profile vivo giữ cấu hình gốc. Game chưa có profile dùng GPU route 3 và PID thật; không giả package thành Genshin. Giữ kiểm tra nhiệt độ, lỗi pipeline, ghi màn hình và phần cứng. Không bổ sung thuật toán mới vào engine.", 14);
    }
    private void toggle(String key, String label, boolean def) {
        Switch item = new Switch(this); item.setText(label); item.setChecked(prefs.getBoolean(key, def));
        item.setPadding(0, 12, 0, 12); body.addView(item);
        item.setOnCheckedChangeListener((v, checked) -> {
            prefs.edit().putBoolean(key, checked).apply(); ConfigStore.publish(this);
        });
    }
    private void text(String value, int size) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setPadding(0, 10, 0, 12); body.addView(v);
    }
    private EditText input(String value) {
        EditText v = new EditText(this); v.setText(value); v.setMinLines(2);
        v.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        v.setHint("com.garena.game.kgvn\ncom.miHoYo.GenshinImpact"); body.addView(v); return v;
    }
}
