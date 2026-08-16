package com.safetyoffice.firemanager;

import android.content.Context;
import android.os.Environment;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public class LocalBackupManager {
    private static final int MAX_DAILY_BACKUPS = 10;

    public static void backupQuietly(Context context, DatabaseHelper db) {
        try {
            backupNow(context, db);
        } catch (Exception ignored) {
        }
    }

    public static String backupNow(Context context, DatabaseHelper db) throws Exception {
        JSONObject json = db.exportJson();
        File dir = backupDir(context);
        File latest = new File(dir, "fire-manager-latest-backup.json");
        writeJson(latest, json);

        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        File daily = new File(dir, "fire-manager-backup-" + day + ".json");
        writeJson(daily, json);
        trimDailyBackups(dir);
        return latest.getAbsolutePath();
    }

    public static String restoreLatest(Context context, DatabaseHelper db) throws Exception {
        File latest = latestBackup(context);
        if (!latest.isFile()) throw new Exception("no local backup");
        db.importJson(new JSONObject(readText(latest)));
        return latest.getAbsolutePath();
    }

    public static String latestPath(Context context) {
        return latestBackup(context).getAbsolutePath();
    }

    private static File latestBackup(Context context) {
        return new File(backupDir(context), "fire-manager-latest-backup.json");
    }

    private static File backupDir(Context context) {
        File base = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (base == null) base = context.getFilesDir();
        File dir = new File(base, "FireManagerBackups");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static void writeJson(File file, JSONObject json) throws Exception {
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
        try {
            writer.write(json.toString(2));
        } finally {
            writer.close();
        }
    }

    private static String readText(File file) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        try {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
            return out.toString();
        } finally {
            reader.close();
        }
    }

    private static void trimDailyBackups(File dir) {
        File[] files = dir.listFiles((folder, name) ->
                name.startsWith("fire-manager-backup-") && name.endsWith(".json"));
        if (files == null || files.length <= MAX_DAILY_BACKUPS) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < files.length - MAX_DAILY_BACKUPS; i++) {
            files[i].delete();
        }
    }
}
