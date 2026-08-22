package com.safetyoffice.firemanager;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseHelper extends SQLiteOpenHelper {
    public static final String DB_NAME = "fire_salary_manager.db";
    public static final int DB_VERSION = 11;

    private static final List<String> TABLES = Arrays.asList(
            "employees", "advances", "customers", "extinguishers",
            "safety_certificates", "technical_reports", "maintenance_contracts",
            "customer_attachments", "extinguisher_images", "tasks", "settings"
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
                "place_name TEXT, location TEXT, customer_status TEXT DEFAULT 'جديد', created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE extinguishers (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, customer_id INTEGER, customer_name TEXT NOT NULL, " +
                "phone TEXT, place_name TEXT, location TEXT, customer_status TEXT DEFAULT 'جديد', extinguisher_type TEXT, weight TEXT, count INTEGER NOT NULL, " +
                "total_price REAL NOT NULL, paid_amount REAL DEFAULT 0, sticker_date INTEGER NOT NULL, reminder_at INTEGER NOT NULL, image_uri TEXT, delivered_again INTEGER DEFAULT 0, created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE safety_certificates (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, customer_name TEXT NOT NULL, phone TEXT, " +
                "place_name TEXT, location TEXT, customer_status TEXT DEFAULT 'جديد', total_price REAL DEFAULT 0, certificate_date INTEGER NOT NULL, reminder_at INTEGER NOT NULL, created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE technical_reports (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, customer_name TEXT NOT NULL, phone TEXT, " +
                "place_name TEXT, location TEXT, customer_status TEXT DEFAULT 'جديد', total_price REAL DEFAULT 0, report_date INTEGER NOT NULL, reminder_at INTEGER NOT NULL, created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE maintenance_contracts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, customer_name TEXT NOT NULL, phone TEXT, " +
                "place_name TEXT, location TEXT, customer_status TEXT DEFAULT 'جديد', start_date INTEGER NOT NULL, next_visit_at INTEGER NOT NULL, " +
                "reminder_at INTEGER NOT NULL, created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE customer_attachments (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, customer_name TEXT NOT NULL, phone TEXT, " +
                "place_name TEXT, location TEXT, title TEXT, uri TEXT NOT NULL, created_at INTEGER NOT NULL)");

        createExtinguisherImages(db);

        db.execSQL("CREATE TABLE tasks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, note TEXT, " +
                "due_date INTEGER DEFAULT 0, is_done INTEGER DEFAULT 0, created_at INTEGER NOT NULL)");

        createTeamAssignments(db);
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
        if (oldVersion < 4) {
            createSettings(db);
            replaceWhatsappTemplatesIfMissingCount(db);
        }
        if (oldVersion < 5) {
            addPlaceNameColumns(db);
            createSettings(db);
        }
        if (oldVersion < 6) {
            addStatusColumns(db);
            createAttachments(db);
            createSettings(db);
        }
        if (oldVersion < 7) {
            addExtinguisherExtraColumns(db);
            createTasks(db);
            createSettings(db);
        }
        if (oldVersion < 8) {
            createExtinguisherImages(db);
            migrateLegacyExtinguisherImages(db);
            createSettings(db);
        }
        if (oldVersion < 9) {
            addColumnIfMissing(db, "extinguishers", "paid_amount", "REAL DEFAULT 0");
            createSettings(db);
        }
        if (oldVersion < 10) {
            createSettings(db);
        }
        if (oldVersion < 11) {
            createTeamAssignments(db);
        }
    }

    public long insert(String table, ContentValues values) {
        return getWritableDatabase().insert(table, null, values);
    }

    public int update(String table, ContentValues values, String where, String... args) {
        return getWritableDatabase().update(table, values, where, args);
    }

    public int delete(String table, String where, String... args) {
        return getWritableDatabase().delete(table, where, args);
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

    public void saveTeamAssignment(String assignmentId, String teamCode, String name, String phone, String place, String location) {
        ContentValues cv = new ContentValues();
        cv.put("assignment_id", assignmentId);
        cv.put("team_code", teamCode);
        cv.put("customer_name", name);
        cv.put("phone", phone);
        cv.put("place_name", place);
        cv.put("location", location);
        cv.put("status", "open");
        cv.put("created_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("team_assignments", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public boolean hasTeamAssignment(String assignmentId) {
        Cursor c = getReadableDatabase().query("team_assignments", new String[]{"assignment_id"},
                "assignment_id=?", new String[]{safe(assignmentId)}, null, null, null, "1");
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    public String teamAssignmentValue(String name, String phone, String place, String location, String column) {
        Cursor c = getReadableDatabase().query("team_assignments", new String[]{column},
                "customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=? AND status='open'",
                new String[]{name, safe(phone), safe(place), safe(location)}, null, null, "created_at DESC", "1");
        try {
            return c.moveToFirst() ? safe(c.getString(0)) : "";
        } finally {
            c.close();
        }
    }

    public void closeTeamAssignment(String assignmentId) {
        ContentValues cv = new ContentValues();
        cv.put("status", "completed");
        getWritableDatabase().update("team_assignments", cv, "assignment_id=?", new String[]{assignmentId});
    }

    public void deleteCustomerAttachment(long id) {
        getWritableDatabase().delete("customer_attachments", "id=?", new String[]{String.valueOf(id)});
    }

    public void deleteCustomerEverywhere(String name, String phone, String place, String location) {
        SQLiteDatabase db = getWritableDatabase();
        String[] args = new String[]{name, safe(phone), safe(place), safe(location)};
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM extinguisher_images WHERE extinguisher_id IN (" +
                    "SELECT id FROM extinguishers WHERE customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=?)", args);
            db.delete("extinguishers", "customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=?", args);
            db.delete("customers", "name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=?", args);
            db.delete("safety_certificates", "customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=?", args);
            db.delete("technical_reports", "customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=?", args);
            db.delete("maintenance_contracts", "customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=?", args);
            db.delete("customer_attachments", "customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=?", args);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public Cursor all(String table) {
        return getReadableDatabase().query(table, null, null, null, null, null, "created_at DESC");
    }

    public Cursor raw(String sql, String... args) {
        return getReadableDatabase().rawQuery(sql, args);
    }

    public void updateCustomerEverywhere(String oldName, String oldPhone, String oldPlace, String oldLocation,
                                         String newName, String newPhone, String newPlace, String newLocation) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues customer = new ContentValues();
            customer.put("name", newName);
            customer.put("phone", newPhone);
            customer.put("place_name", newPlace);
            customer.put("location", newLocation);
            db.update("customers", customer, "name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=?",
                    new String[]{oldName, safe(oldPhone), safe(oldPlace), safe(oldLocation)});

            updateCustomerTable(db, "extinguishers", oldName, oldPhone, oldPlace, oldLocation, newName, newPhone, newPlace, newLocation);
            updateCustomerTable(db, "safety_certificates", oldName, oldPhone, oldPlace, oldLocation, newName, newPhone, newPlace, newLocation);
            updateCustomerTable(db, "technical_reports", oldName, oldPhone, oldPlace, oldLocation, newName, newPhone, newPlace, newLocation);
            updateCustomerTable(db, "maintenance_contracts", oldName, oldPhone, oldPlace, oldLocation, newName, newPhone, newPlace, newLocation);
            updateAttachmentCustomer(db, oldName, oldPhone, oldPlace, oldLocation, newName, newPhone, newPlace, newLocation);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void updateCustomerStatusEverywhere(String name, String phone, String place, String location, String status) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            updateStatus(db, "customers", "name", name, phone, place, location, status);
            updateStatus(db, "extinguishers", "customer_name", name, phone, place, location, status);
            updateStatus(db, "safety_certificates", "customer_name", name, phone, place, location, status);
            updateStatus(db, "technical_reports", "customer_name", name, phone, place, location, status);
            updateStatus(db, "maintenance_contracts", "customer_name", name, phone, place, location, status);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void updateStatus(SQLiteDatabase db, String table, String nameColumn, String name, String phone,
                              String place, String location, String status) {
        ContentValues cv = new ContentValues();
        cv.put("customer_status", status);
        db.update(table, cv, nameColumn + "=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=?",
                new String[]{name, safe(phone), safe(place), safe(location)});
    }

    private void updateCustomerTable(SQLiteDatabase db, String table, String oldName, String oldPhone, String oldPlace, String oldLocation,
                                     String newName, String newPhone, String newPlace, String newLocation) {
        ContentValues cv = new ContentValues();
        cv.put("customer_name", newName);
        cv.put("phone", newPhone);
        cv.put("place_name", newPlace);
        cv.put("location", newLocation);
        db.update(table, cv, "customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=?",
                new String[]{oldName, safe(oldPhone), safe(oldPlace), safe(oldLocation)});
    }

    private void updateAttachmentCustomer(SQLiteDatabase db, String oldName, String oldPhone, String oldPlace, String oldLocation,
                                          String newName, String newPhone, String newPlace, String newLocation) {
        ContentValues cv = new ContentValues();
        cv.put("customer_name", newName);
        cv.put("phone", newPhone);
        cv.put("place_name", newPlace);
        cv.put("location", newLocation);
        db.update("customer_attachments", cv, "customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=?",
                new String[]{oldName, safe(oldPhone), safe(oldPlace), safe(oldLocation)});
    }

    private void createSettings(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS settings (" +
                "key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        insertDefaultSetting(db, "extinguisher_percent", "25");
        insertDefaultSetting(db, "certificate_percent", "0");
        insertDefaultSetting(db, "report_percent", "0");
        insertDefaultSetting(db, "auto_save_contacts", "0");
        insertDefaultSetting(db, "team_code", "");
        insertDefaultSetting(db, "selected_whatsapp_template", "0");
        insertDefaultSetting(db, "whatsapp_templates", defaultWhatsappTemplates());
    }

    private void createTeamAssignments(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS team_assignments (" +
                "assignment_id TEXT PRIMARY KEY, team_code TEXT NOT NULL, customer_name TEXT NOT NULL, " +
                "phone TEXT, place_name TEXT, location TEXT, status TEXT DEFAULT 'open', created_at INTEGER NOT NULL)");
    }

    private void insertDefaultSetting(SQLiteDatabase db, String key, String value) {
        ContentValues cv = new ContentValues();
        cv.put("key", key);
        cv.put("value", value);
        db.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private void replaceWhatsappTemplatesIfMissingCount(SQLiteDatabase db) {
        Cursor c = db.query("settings", new String[]{"value"}, "key=?",
                new String[]{"whatsapp_templates"}, null, null, null);
        try {
            if (c.moveToFirst() && safe(c.getString(0)).contains("{count}")) return;
        } finally {
            c.close();
        }
        ContentValues cv = new ContentValues();
        cv.put("key", "whatsapp_templates");
        cv.put("value", defaultWhatsappTemplates());
        db.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private void addPlaceNameColumns(SQLiteDatabase db) {
        addColumnIfMissing(db, "customers", "place_name", "TEXT");
        addColumnIfMissing(db, "extinguishers", "place_name", "TEXT");
        addColumnIfMissing(db, "safety_certificates", "place_name", "TEXT");
        addColumnIfMissing(db, "technical_reports", "place_name", "TEXT");
        addColumnIfMissing(db, "maintenance_contracts", "place_name", "TEXT");
    }

    private void addStatusColumns(SQLiteDatabase db) {
        addColumnIfMissing(db, "customers", "customer_status", "TEXT DEFAULT 'جديد'");
        addColumnIfMissing(db, "extinguishers", "customer_status", "TEXT DEFAULT 'جديد'");
        addColumnIfMissing(db, "safety_certificates", "customer_status", "TEXT DEFAULT 'جديد'");
        addColumnIfMissing(db, "technical_reports", "customer_status", "TEXT DEFAULT 'جديد'");
        addColumnIfMissing(db, "maintenance_contracts", "customer_status", "TEXT DEFAULT 'جديد'");
    }

    private void createAttachments(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS customer_attachments (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, customer_name TEXT NOT NULL, phone TEXT, " +
                "place_name TEXT, location TEXT, title TEXT, uri TEXT NOT NULL, created_at INTEGER NOT NULL)");
    }

    private void createExtinguisherImages(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS extinguisher_images (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, extinguisher_id INTEGER NOT NULL, " +
                "uri TEXT NOT NULL, created_at INTEGER NOT NULL)");
    }

    private void migrateLegacyExtinguisherImages(SQLiteDatabase db) {
        Cursor c = db.rawQuery("SELECT id, image_uri FROM extinguishers WHERE IFNULL(image_uri,'')<>''", null);
        try {
            while (c.moveToNext()) {
                long extinguisherId = c.getLong(0);
                String uri = safe(c.getString(1));
                Cursor existing = db.rawQuery("SELECT id FROM extinguisher_images WHERE extinguisher_id=? AND uri=? LIMIT 1",
                        new String[]{String.valueOf(extinguisherId), uri});
                try {
                    if (existing.moveToFirst()) continue;
                } finally {
                    existing.close();
                }
                ContentValues cv = new ContentValues();
                cv.put("extinguisher_id", extinguisherId);
                cv.put("uri", uri);
                cv.put("created_at", System.currentTimeMillis());
                db.insert("extinguisher_images", null, cv);
            }
        } finally {
            c.close();
        }
    }

    private void addExtinguisherExtraColumns(SQLiteDatabase db) {
        addColumnIfMissing(db, "extinguishers", "image_uri", "TEXT");
        addColumnIfMissing(db, "extinguishers", "delivered_again", "INTEGER DEFAULT 0");
    }

    private void createTasks(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS tasks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, note TEXT, " +
                "due_date INTEGER DEFAULT 0, is_done INTEGER DEFAULT 0, created_at INTEGER NOT NULL)");
    }

    private String defaultWhatsappTemplates() {
        return "تحياتنا وتقديرنا لك {name}\nحبيت أذكركم إن موعد انتهاء شهادة/استيكر الطفايات قرب، وعدد الطفايات المسجلة عندكم {count} طفاية. ودي فرصة نرتب زيارة صيانة في الوقت اللي يناسبكم عشان نتأكد إن كل شي جاهز وآمن.\nالله يعطيكم العافية.|||" +
                "تحياتنا لك {name}\nنذكركم بقرب موعد متابعة الطفايات، وعددها عندكم {count} طفاية. فضلا حددوا لنا وقت مناسب للزيارة والصيانة، وبإذن الله نخدمكم بالشكل اللي يرضيكم.\nشاكرين لكم تعاونكم.|||" +
                "تحياتنا وتقديرنا\nعندكم {count} طفاية مسجلة لدينا، وموعد شهادة/استيكر الطفايات قرب ينتهي. نحتاج ننسق معكم موعد زيارة صيانة مناسب، وربي يبارك لكم.";
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

    public void importTeamJson(JSONObject root) throws Exception {
        importTeamJsonInternal(root, true);
    }

    public void importTeamCompletedJson(JSONObject root) throws Exception {
        importTeamJsonInternal(root, false);
    }

    private void importTeamJsonInternal(JSONObject root, boolean skipExisting) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Map<Long, Long> extinguisherIds = new HashMap<>();
            for (String table : TABLES) {
                if ("settings".equals(table) || !root.has(table)) continue;
                JSONArray rows = root.getJSONArray(table);
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    if (skipExisting && teamRowExists(db, table, row)) continue;
                    if ("extinguishers".equals(table)) {
                        long oldId = row.optLong("id", -1);
                        ContentValues cv = rowValues(row, "id", "customer_id");
                        long newId = db.insert(table, null, cv);
                        if (oldId > 0 && newId > 0) extinguisherIds.put(oldId, newId);
                    } else if ("extinguisher_images".equals(table)) {
                        long oldExtinguisherId = row.optLong("extinguisher_id", -1);
                        if (oldExtinguisherId > 0 && !extinguisherIds.containsKey(oldExtinguisherId)) continue;
                        ContentValues cv = rowValues(row, "id");
                        if (oldExtinguisherId > 0) cv.put("extinguisher_id", extinguisherIds.get(oldExtinguisherId));
                        db.insert(table, null, cv);
                    } else {
                        db.insert(table, null, rowValues(row, "id"));
                    }
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private boolean teamRowExists(SQLiteDatabase db, String table, JSONObject row) {
        if (!row.has("created_at")) return false;
        String identityColumn = teamIdentityColumn(row);
        String where = "created_at=?";
        String[] args;
        if (identityColumn.length() > 0) {
            where += " AND IFNULL(" + identityColumn + ",'')=?";
            args = new String[]{String.valueOf(row.optLong("created_at")), row.optString(identityColumn, "")};
        } else {
            args = new String[]{String.valueOf(row.optLong("created_at"))};
        }
        Cursor c = db.query(table, new String[]{"id"}, where, args, null, null, null, "1");
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    private String teamIdentityColumn(JSONObject row) {
        if (row.has("uri")) return "uri";
        if (row.has("image_uri")) return "image_uri";
        if (row.has("customer_name")) return "customer_name";
        if (row.has("employee_name")) return "employee_name";
        if (row.has("name")) return "name";
        if (row.has("title")) return "title";
        return "";
    }

    private ContentValues rowValues(JSONObject row, String... skipKeys) throws Exception {
        ContentValues cv = new ContentValues();
        JSONArray names = row.names();
        if (names == null) return cv;
        for (int n = 0; n < names.length(); n++) {
            String key = names.getString(n);
            if (isSkipped(key, skipKeys)) continue;
            Object value = row.get(key);
            if (value == JSONObject.NULL) cv.putNull(key);
            else if (value instanceof Integer) cv.put(key, (Integer) value);
            else if (value instanceof Long) cv.put(key, (Long) value);
            else if (value instanceof Double) cv.put(key, (Double) value);
            else cv.put(key, String.valueOf(value));
        }
        return cv;
    }

    private boolean isSkipped(String key, String... skipKeys) {
        for (String skip : skipKeys) {
            if (skip.equals(key)) return true;
        }
        return false;
    }
}
