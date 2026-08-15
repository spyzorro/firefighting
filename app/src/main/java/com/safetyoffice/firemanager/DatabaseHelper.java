package com.safetyoffice.firemanager;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    public static final String DB_NAME = "fire_salary_manager.db";
    public static final int DB_VERSION = 3;

    private static final List<String> TABLES = Arrays.asList(
            "employees", "advances", "customers", "extinguishers",
            "safety_certificates", "technical_reports", "maintenance_contracts", "settings"
    );

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE employees (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, salary REAL NOT NULL, created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE advances (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, employee_id INTEGER, " +
                "employee_name TEXT NOT NULL, amount REAL NOT NULL, note TEXT, created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE customers (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT, " +
                "location TEXT, created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE extinguishers (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, customer_id INTEGER, customer_name TEXT NOT NULL, " +
                "phone TEXT, location TEXT, extinguisher_type TEXT, weight TEXT, count INTEGER NOT NULL, " +
                "total_price REAL NOT NULL, sticker_date INTEGER NOT NULL, reminder_at INTEGER NOT NULL, created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE safety_certificates (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, customer_name TEXT NOT NULL, phone TEXT, " +
                "location TEXT, total_price REAL DEFAULT 0, certificate_date INTEGER NOT NULL, reminder_at INTEGER NOT NULL, created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE technical_reports (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, customer_name TEXT NOT NULL, phone TEXT, " +
                "location TEXT, total_price REAL DEFAULT 0, report_date INTEGER NOT NULL, reminder_at INTEGER NOT NULL, created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE maintenance_contracts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, customer_name TEXT NOT NULL, phone TEXT, " +
                "location TEXT, start_date INTEGER NOT NULL, next_visit_at INTEGER NOT NULL, " +
                "reminder_at INTEGER NOT NULL, created_at INTEGER NOT NULL)");

        createSettings(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            addColumnIfMissing(db, "safety_certificates", "total_price", "REAL DEFAULT 0");
            addColumnIfMissing(db, "technical_reports", "total_price", "REAL DEFAULT 0");
            createSettings(db);
        }
        if (oldVersion < 3) {
            createSettings(db);
        }
    }

    public long insert(String table, ContentValues values) {
        return getWritableDatabase().insert(table, null, values);
    }

    public int update(String table, ContentValues values, String where, String... args) {
        return getWritableDatabase().update(table, values, where, args);
    }

    public String setting(String key, String fallback) {
        Cursor c = getReadableDatabase().query("settings", new String[]{"value"}, "key=?",
                new String[]{key}, null, null, null);
        try {
            return c.moveToFirst() ? c.getString(0) : fallback;
        } finally {
            c.close();
        }
    }

    public double settingDouble(String key, double fallback) {
        try {
            return Double.parseDouble(setting(key, String.valueOf(fallback)));
        } catch (Exception e) {
            return fallback;
        }
    }

    public void setSetting(String key, String value) {
        ContentValues cv = new ContentValues();
        cv.put("key", key);
        cv.put("value", value);
        getWritableDatabase().insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public Cursor all(String table) {
        return getReadableDatabase().query(table, null, null, null, null, null, "created_at DESC");
    }

    public Cursor raw(String sql, String... args) {
        return getReadableDatabase().rawQuery(sql, args);
    }

    public void updateCustomerEverywhere(String oldName, String oldPhone, String oldLocation,
                                         String newName, String newPhone, String newLocation) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues customer = new ContentValues();
            customer.put("name", newName);
            customer.put("phone", newPhone);
            customer.put("location", newLocation);
            db.update("customers", customer, "name=? AND IFNULL(phone,'')=? AND IFNULL(location,'')=?",
                    new String[]{oldName, safe(oldPhone), safe(oldLocation)});

            updateCustomerTable(db, "extinguishers", oldName, oldPhone, oldLocation, newName, newPhone, newLocation);
            updateCustomerTable(db, "safety_certificates", oldName, oldPhone, oldLocation, newName, newPhone, newLocation);
            updateCustomerTable(db, "technical_reports", oldName, oldPhone, oldLocation, newName, newPhone, newLocation);
            updateCustomerTable(db, "maintenance_contracts", oldName, oldPhone, oldLocation, newName, newPhone, newLocation);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void updateCustomerTable(SQLiteDatabase db, String table, String oldName, String oldPhone, String oldLocation,
                                     String newName, String newPhone, String newLocation) {
        ContentValues cv = new ContentValues();
        cv.put("customer_name", newName);
        cv.put("phone", newPhone);
        cv.put("location", newLocation);
        db.update(table, cv, "customer_name=? AND IFNULL(phone,'')=? AND IFNULL(location,'')=?",
                new String[]{oldName, safe(oldPhone), safe(oldLocation)});
    }

    private void createSettings(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS settings (" +
                "key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        insertDefaultSetting(db, "extinguisher_percent", "25");
        insertDefaultSetting(db, "certificate_percent", "0");
        insertDefaultSetting(db, "report_percent", "0");
        insertDefaultSetting(db, "selected_whatsapp_template", "0");
        insertDefaultSetting(db, "whatsapp_templates",
                "تحياتنا لك {name}\nنذكركم بأن موعد انتهاء شهادة/استيكر الطفايات قرب، ونحتاج نحدد معكم موعد زيارة مناسب لصيانة الطفايات والتأكد من جاهزيتها.\nيعطيكم العافية.|||" +
                        "تحياتنا {name}\nحبيت أذكركم بقرب موعد صيانة الطفايات وتجديد المتابعة، فضلا زودونا بالوقت المناسب للزيارة.\nشاكرين تعاونكم.|||" +
                        "تحياتنا لكم\nموعد متابعة طفايات الحريق قرب، ونحتاج ترتيب زيارة صيانة في أقرب وقت يناسبكم.\nمع خالص الشكر.");
    }

    private void insertDefaultSetting(SQLiteDatabase db, String key, String value) {
        ContentValues cv = new ContentValues();
        cv.put("key", key);
        cv.put("value", value);
        db.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private void addColumnIfMissing(SQLiteDatabase db, String table, String column, String definition) {
        Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        try {
            while (c.moveToNext()) {
                if (column.equals(c.getString(c.getColumnIndexOrThrow("name")))) return;
            }
        } finally {
            c.close();
        }
        db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public JSONObject exportJson() throws Exception {
        JSONObject root = new JSONObject();
        for (String table : TABLES) {
            JSONArray rows = new JSONArray();
            Cursor c = getReadableDatabase().query(table, null, null, null, null, null, null);
            try {
                while (c.moveToNext()) {
                    JSONObject row = new JSONObject();
                    for (int i = 0; i < c.getColumnCount(); i++) {
                        int type = c.getType(i);
                        String name = c.getColumnName(i);
                        if (type == Cursor.FIELD_TYPE_NULL) row.put(name, JSONObject.NULL);
                        else if (type == Cursor.FIELD_TYPE_INTEGER) row.put(name, c.getLong(i));
                        else if (type == Cursor.FIELD_TYPE_FLOAT) row.put(name, c.getDouble(i));
                        else row.put(name, c.getString(i));
                    }
                    rows.put(row);
                }
            } finally {
                c.close();
            }
            root.put(table, rows);
        }
        root.put("exported_at", System.currentTimeMillis());
        return root;
    }

    public void importJson(JSONObject root) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (String table : TABLES) {
                if (!root.has(table)) continue;
                JSONArray rows = root.getJSONArray(table);
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    ContentValues cv = new ContentValues();
                    JSONArray names = row.names();
                    if (names == null) continue;
                    for (int n = 0; n < names.length(); n++) {
                        String key = names.getString(n);
                        Object value = row.get(key);
                        if (value == JSONObject.NULL) cv.putNull(key);
                        else if (value instanceof Integer) cv.put(key, (Integer) value);
                        else if (value instanceof Long) cv.put(key, (Long) value);
                        else if (value instanceof Double) cv.put(key, (Double) value);
                        else cv.put(key, String.valueOf(value));
                    }
                    db.insertWithOnConflict(table, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
