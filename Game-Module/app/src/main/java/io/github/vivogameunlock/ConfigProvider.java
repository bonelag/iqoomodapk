package io.github.vivogameunlock;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

/** No commands, shell, Settings writes, or file access on behalf of callers. */
public final class ConfigProvider extends ContentProvider {
    static final String AUTHORITY = Policy.MODULE + ".config";
    static final Uri URI = Uri.parse("content://" + AUTHORITY);

    @Override public boolean onCreate() { return true; }

    private void enforceCaller() {
        int appId = Binder.getCallingUid() % 100000;
        if (appId != 1000 && Binder.getCallingUid() != Process.myUid())
            throw new SecurityException("Only module and Android system UID may read configuration");
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        enforceCaller();
        if (!"config".equals(method)) throw new IllegalArgumentException("Unknown operation");
        return ConfigStore.read(getContext());
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { enforceCaller(); return null; }
    @Override public String getType(Uri u) { return "vnd.android.cursor.item/vnd.vivogameunlock.config"; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
