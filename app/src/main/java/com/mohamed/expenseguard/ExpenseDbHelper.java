package com.mohamed.expenseguard;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.os.Environment;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

public class ExpenseDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "expense_guard.db";
    private static final int DB_VERSION = 8;
    private static final String PUBLIC_BACKUP_FILE = "masrofaty_backup.json";
    private static final String PUBLIC_BACKUP_FILE_ALT = "masrofaty_backup_DO_NOT_DELETE.json";
    private static final String PUBLIC_BACKUP_DIR = Environment.DIRECTORY_DOCUMENTS + "/Masrofaty/";
    private static final String PUBLIC_BACKUP_DOWNLOAD_DIR = Environment.DIRECTORY_DOWNLOADS + "/Masrofaty/";
    private final Context appContext;
    private boolean localBackupInProgress = false;


    public ExpenseDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.appContext = context.getApplicationContext();
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        db.execSQL("CREATE TABLE transactions(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "type TEXT, status TEXT, amount REAL, currency TEXT," +
                "title TEXT, merchant TEXT, category TEXT, source TEXT, raw TEXT," +
                "dateMillis INTEGER, fingerprint TEXT UNIQUE, card TEXT, extra TEXT," +
                "affectsBudget INTEGER, createdAt INTEGER)");
        db.execSQL("CREATE TABLE debts(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT, amount REAL, paid REAL, currency TEXT, whatsapp TEXT, facebook TEXT," +
                "notes TEXT, status TEXT, direction TEXT, dueDateMillis INTEGER," +
                "createdAt INTEGER, updatedAt INTEGER)");
        db.execSQL("CREATE TABLE debt_payments(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "debtId INTEGER, amount REAL, note TEXT, dateMillis INTEGER, txId INTEGER, screenshotUri TEXT)");
        db.execSQL("CREATE TABLE subscriptions(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT, amount REAL, currency TEXT, merchant TEXT, category TEXT," +
                "nextDateMillis INTEGER, active INTEGER, notes TEXT, lastChargedMillis INTEGER," +
                "createdAt INTEGER, updatedAt INTEGER)");
        db.execSQL("CREATE TABLE budget_categories(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT UNIQUE, monthlyLimit REAL, currency TEXT, active INTEGER," +
                "createdAt INTEGER, updatedAt INTEGER)");
        db.execSQL("CREATE TABLE month_archives(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "monthKey TEXT UNIQUE, budget REAL, spent REAL, extraIncome REAL, cashBalance REAL," +
                "closedAt INTEGER, summary TEXT)");
        createProductivityTables(db);
        setSetting(db, "monthly_budget", "0");
        setSetting(db, "monthly_budget_template", "0");
        setSetting(db, "monthly_budget_applied_month", "");
        setSetting(db, "budget_alert_80_month", "");
        setSetting(db, "budget_alert_100_month", "");
        setSetting(db, "currency", "SAR");
        setSetting(db, "debt_remind_day", "1");
        setSetting(db, "debt_remind_2h", "1");
        setSetting(db, "debt_repeat_overdue", "1");
        setSetting(db, "app_lock_enabled", "0");
        setSetting(db, "app_lock_pin", "");
        setSetting(db, "privacy_mode", "0");
        setSetting(db, "language", "ar");
        setSetting(db, "cash_balance", "0");
        setSetting(db, "abnormal_expense_threshold", "0");
        setSetting(db, "subscription_remind_3d", "1");
        setSetting(db, "subscription_remind_1d", "1");
        setSetting(db, "last_open_month", currentMonthKey());
        seedDefaultCategories(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try { db.execSQL("ALTER TABLE debts ADD COLUMN direction TEXT DEFAULT 'OWED_TO_ME'"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE debts ADD COLUMN dueDateMillis INTEGER DEFAULT 0"); } catch (Exception ignored) {}
            try { setSetting(db, "currency", "SAR"); } catch (Exception ignored) {}
        }
        if (oldVersion < 3) {
            try { db.execSQL("CREATE TABLE IF NOT EXISTS subscriptions(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT, amount REAL, currency TEXT, merchant TEXT, category TEXT," +
                    "nextDateMillis INTEGER, active INTEGER, notes TEXT, lastChargedMillis INTEGER," +
                    "createdAt INTEGER, updatedAt INTEGER)"); } catch (Exception ignored) {}
            try { setSetting(db, "debt_remind_day", "1"); } catch (Exception ignored) {}
            try { setSetting(db, "debt_remind_2h", "1"); } catch (Exception ignored) {}
            try { setSetting(db, "debt_repeat_overdue", "1"); } catch (Exception ignored) {}
            try { setSetting(db, "app_lock_enabled", "0"); } catch (Exception ignored) {}
            try { setSetting(db, "app_lock_pin", ""); } catch (Exception ignored) {}
        }
        if (oldVersion < 4) {
            try { db.execSQL("CREATE TABLE IF NOT EXISTS budget_categories(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT UNIQUE, monthlyLimit REAL, currency TEXT, active INTEGER," +
                    "createdAt INTEGER, updatedAt INTEGER)"); } catch (Exception ignored) {}
            try { db.execSQL("CREATE TABLE IF NOT EXISTS month_archives(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "monthKey TEXT UNIQUE, budget REAL, spent REAL, extraIncome REAL, cashBalance REAL," +
                    "closedAt INTEGER, summary TEXT)"); } catch (Exception ignored) {}
            try { setSetting(db, "language", "ar"); } catch (Exception ignored) {}
            try { setSetting(db, "cash_balance", "0"); } catch (Exception ignored) {}
            try { setSetting(db, "abnormal_expense_threshold", "0"); } catch (Exception ignored) {}
            try { setSetting(db, "subscription_remind_3d", "1"); } catch (Exception ignored) {}
            try { setSetting(db, "subscription_remind_1d", "1"); } catch (Exception ignored) {}
            try { setSetting(db, "last_open_month", currentMonthKey()); } catch (Exception ignored) {}
            try { setSetting(db, "monthly_budget_template", getSetting("monthly_budget", "0")); } catch (Exception ignored) {}
            try { setSetting(db, "monthly_budget_applied_month", ""); } catch (Exception ignored) {}
            try { seedDefaultCategories(db); } catch (Exception ignored) {}
        }
        if (oldVersion < 5) {
            try { db.execSQL("ALTER TABLE debts ADD COLUMN currency TEXT DEFAULT 'SAR'"); } catch (Exception ignored) {}
            try { setSetting(db, "privacy_mode", "0"); } catch (Exception ignored) {}
        }
        if (oldVersion < 6) {
            try { db.execSQL("ALTER TABLE debt_payments ADD COLUMN screenshotUri TEXT DEFAULT ''"); } catch (Exception ignored) {}
        }
        if (oldVersion < 7) createProductivityTables(db);
        if (oldVersion < 8) {
            try { createSavingGoalsTable(db); } catch (Exception ignored) {}
            try { setSetting(db, "budget_alert_80_month", ""); } catch (Exception ignored) {}
            try { setSetting(db, "budget_alert_100_month", ""); } catch (Exception ignored) {}
        }
    }

    private void createProductivityTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS tasks(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,notes TEXT,priority INTEGER," +
                "dueAt INTEGER,repeatMinutes INTEGER,completed INTEGER,createdAt INTEGER,updatedAt INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS gold_holdings(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,label TEXT,karat INTEGER,grams REAL," +
                "purchasePrice REAL,createdAt INTEGER,updatedAt INTEGER)");
        createSavingGoalsTable(db);
    }

    private void createSavingGoalsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS saving_goals(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,type TEXT,target REAL,current REAL,unit TEXT," +
                "completed INTEGER,createdAt INTEGER,updatedAt INTEGER)");
    }

    public long saveTask(Long id, String title, String notes, int priority, long dueAt, long repeatMinutes, boolean completed) {
        ContentValues cv = new ContentValues(); long now = System.currentTimeMillis();
        cv.put("title", title); cv.put("notes", notes); cv.put("priority", priority); cv.put("dueAt", dueAt);
        cv.put("repeatMinutes", repeatMinutes); cv.put("completed", completed ? 1 : 0); cv.put("updatedAt", now);
        long result;
        if (id == null) { cv.put("createdAt", now); result = getWritableDatabase().insert("tasks", null, cv); }
        else { getWritableDatabase().update("tasks", cv, "id=?", new String[]{String.valueOf(id)}); result = id; }
        autoLocalBackupAfterChange(); return result;
    }
    public List<TaskItem> getTasks(boolean includeCompleted) {
        List<TaskItem> list = new ArrayList<>(); String where = includeCompleted ? "" : " WHERE completed=0";
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM tasks" + where + " ORDER BY completed ASC,priority DESC,CASE WHEN dueAt=0 THEN 1 ELSE 0 END,dueAt ASC,createdAt DESC", null)) {
            while (c.moveToNext()) list.add(TaskItem.from(c));
        } return list;
    }
    public TaskItem getTask(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM tasks WHERE id=?", new String[]{String.valueOf(id)})) { if (c.moveToFirst()) return TaskItem.from(c); }
        return null;
    }
    public void setTaskCompleted(long id, boolean completed) { ContentValues cv=new ContentValues(); cv.put("completed",completed?1:0); cv.put("updatedAt",System.currentTimeMillis()); getWritableDatabase().update("tasks",cv,"id=?",new String[]{String.valueOf(id)}); autoLocalBackupAfterChange(); }
    public void deleteTask(long id) { getWritableDatabase().delete("tasks","id=?",new String[]{String.valueOf(id)}); autoLocalBackupAfterChange(); }

    public long saveGoldHolding(Long id, String label, int karat, double grams, double purchasePrice) {
        ContentValues cv=new ContentValues(); long now=System.currentTimeMillis(); cv.put("label",label); cv.put("karat",karat); cv.put("grams",grams); cv.put("purchasePrice",purchasePrice); cv.put("updatedAt",now);
        long result; if(id==null){cv.put("createdAt",now);result=getWritableDatabase().insert("gold_holdings",null,cv);}else{getWritableDatabase().update("gold_holdings",cv,"id=?",new String[]{String.valueOf(id)});result=id;} autoLocalBackupAfterChange(); return result;
    }
    public List<GoldHolding> getGoldHoldings(){List<GoldHolding> list=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM gold_holdings ORDER BY createdAt DESC",null)){while(c.moveToNext())list.add(GoldHolding.from(c));}return list;}
    public void deleteGoldHolding(long id){getWritableDatabase().delete("gold_holdings","id=?",new String[]{String.valueOf(id)});autoLocalBackupAfterChange();}
    public double getTotalGoldGrams(){try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(grams),0) FROM gold_holdings",null)){return c.moveToFirst()?c.getDouble(0):0;}catch(Exception e){return 0;}}

    public long saveSavingGoal(Long id, String title, String type, double target, double current, String unit, boolean completed) {
        ContentValues cv=new ContentValues(); long now=System.currentTimeMillis();
        cv.put("title", title == null || title.trim().isEmpty() ? "هدف ادخار" : title.trim());
        cv.put("type", "GOLD_GRAMS".equals(type) ? "GOLD_GRAMS" : "MONEY");
        cv.put("target", Math.max(0, target)); cv.put("current", Math.max(0, current));
        cv.put("unit", unit == null || unit.trim().isEmpty() ? ("GOLD_GRAMS".equals(type) ? "جرام" : currencySymbol()) : unit.trim());
        cv.put("completed", completed ? 1 : 0); cv.put("updatedAt", now);
        long result; if(id==null){cv.put("createdAt",now);result=getWritableDatabase().insert("saving_goals",null,cv);}else{getWritableDatabase().update("saving_goals",cv,"id=?",new String[]{String.valueOf(id)});result=id;}
        autoLocalBackupAfterChange(); return result;
    }
    public List<SavingGoal> getSavingGoals(boolean includeCompleted){List<SavingGoal> list=new ArrayList<>();String where=includeCompleted?"":" WHERE completed=0";try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM saving_goals"+where+" ORDER BY completed ASC,updatedAt DESC,createdAt DESC",null)){while(c.moveToNext())list.add(SavingGoal.from(c));}return list;}
    public void deleteSavingGoal(long id){getWritableDatabase().delete("saving_goals","id=?",new String[]{String.valueOf(id)});autoLocalBackupAfterChange();}
    public void setSavingGoalCompleted(long id, boolean completed){ContentValues cv=new ContentValues();cv.put("completed",completed?1:0);cv.put("updatedAt",System.currentTimeMillis());getWritableDatabase().update("saving_goals",cv,"id=?",new String[]{String.valueOf(id)});autoLocalBackupAfterChange();}
    public void syncSavingGoalProgressFromHoldings(long id, String type){SavingGoal g=getSavingGoal(id);if(g==null)return;double current="GOLD_GRAMS".equals(type)?getTotalGoldGrams():getCashBalance();saveSavingGoal(id,g.title,g.type,g.target,current,g.unit,g.completed==1);}
    private SavingGoal getSavingGoal(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM saving_goals WHERE id=?",new String[]{String.valueOf(id)})){if(c.moveToFirst())return SavingGoal.from(c);}return null;}

    public double getBudget() { return getDoubleSetting("monthly_budget", 0); }
    public double getMonthlyBudgetTemplate() {
        double template = getDoubleSetting("monthly_budget_template", 0);
        return template > 0 ? template : getBudget();
    }
    public void setBudget(double value) {
        double safe = Math.max(0, value);
        setSetting("monthly_budget", String.valueOf(safe));
        setSetting("monthly_budget_template", String.valueOf(safe));
        setSetting("monthly_budget_applied_month", currentMonthKey());
        setSetting("budget_alert_80_month", "");
        setSetting("budget_alert_100_month", "");
    }
    public void addToBudget(double delta) { setBudget(getBudget() + delta); }
    public int getMonthlyBudgetPercent() {
        double budget = getBudget();
        if (budget <= 0) return 0;
        return (int)Math.floor((getMonthlySpent() * 100.0) / budget);
    }
    public boolean consumeMonthlyBudgetAlert(int levelPercent) {
        if (levelPercent != 80 && levelPercent != 100) return false;
        double budget = getBudget();
        if (budget <= 0 || getMonthlyBudgetPercent() < levelPercent) return false;
        String key = levelPercent == 100 ? "budget_alert_100_month" : "budget_alert_80_month";
        String current = currentMonthKey();
        if (current.equals(getSetting(key, ""))) return false;
        setSetting(key, current);
        return true;
    }

    public String getCurrency() { return getSetting("currency", "SAR"); }
    public void setCurrency(String value) {
        String v = "EGP".equalsIgnoreCase(value) ? "EGP" : "SAR";
        setSetting("currency", v);
    }
    public String currencyName() { return "EGP".equals(getCurrency()) ? "جنيه مصري" : "ريال سعودي"; }
    public String currencySymbol() { return "EGP".equals(getCurrency()) ? "ج.م" : "ر.س"; }
    public String money(double v) { return String.format(Locale.US, "%.2f %s", v, currencySymbol()); }

    public String getSetting(String key, String fallback) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT value FROM settings WHERE key=?", new String[]{key})) {
            if (c.moveToFirst()) return c.getString(0);
        }
        return fallback;
    }

    public double getDoubleSetting(String key, double fallback) {
        try { return Double.parseDouble(getSetting(key, String.valueOf(fallback))); } catch (Exception e) { return fallback; }
    }

    public void setSetting(String key, String value) {
        setSetting(getWritableDatabase(), key, value);
        if (!isBackupMetaKey(key)) autoLocalBackupAfterChange();
    }
    private void setSetting(SQLiteDatabase db, String key, String value) {
        ContentValues cv = new ContentValues();
        cv.put("key", key); cv.put("value", value);
        db.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private boolean isBackupMetaKey(String key) {
        if (key == null) return false;
        return key.equals("last_local_backup")
                || key.equals("last_local_restore")
                || key.equals("last_cloud_restore")
                || key.equals("last_cloud_sync")
                || key.equals("last_cloud_error");
    }

    public void autoLocalBackupAfterChange() {
        if (localBackupInProgress) return;
        localBackupInProgress = true;
        try {
            String json = exportBackupJson();
            writeFile(privateBackupFile(), json);
            try { writePublicBackupText(json); } catch (Exception ignored) {}
            try { writeChosenBackupText(json); } catch (Exception ignored) {}
            setSetting(getWritableDatabase(), "last_local_backup", String.valueOf(System.currentTimeMillis()));
        } catch (Exception ignored) {
        } finally {
            localBackupInProgress = false;
        }
        try { FirebaseSyncManager.scheduleAutoBackup(appContext); } catch (Exception ignored) {}
    }

    private void writeChosenBackupText(String json) throws Exception {
        String saved = getSetting("phone_backup_uri", "");
        if (saved == null || saved.trim().isEmpty()) return;
        OutputStream out = null;
        try {
            Uri uri = Uri.parse(saved.trim());
            out = appContext.getContentResolver().openOutputStream(uri, "wt");
            if (out != null) out.write((json == null ? "" : json).getBytes(StandardCharsets.UTF_8));
        } finally {
            try { if (out != null) out.close(); } catch (Exception ignored) {}
        }
    }

    public long insertParsed(MessageParser.ParsedTransaction tx) {
        if (tx == null) return -1;
        if (isLikelyDuplicate(tx)) return -2;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("type", tx.type);
        cv.put("status", tx.status);
        cv.put("amount", tx.amount);
        cv.put("currency", tx.currency);
        cv.put("title", tx.title);
        cv.put("merchant", tx.merchant);
        String categoryOverride = extractCategoryOverride(tx.extra);
        if (tx.category != null && tx.category.trim().length() > 0) {
            cv.put("category", tx.category.trim());
        } else {
            cv.put("category", categoryOverride != null ? categoryOverride : guessCategory(tx.title + " " + tx.merchant + " " + tx.raw));
        }
        cv.put("source", tx.source);
        cv.put("raw", tx.raw);
        cv.put("dateMillis", tx.dateMillis);
        cv.put("fingerprint", tx.fingerprint);
        cv.put("card", tx.card);
        cv.put("extra", tx.extra);
        cv.put("affectsBudget", tx.affectsBudget);
        cv.put("createdAt", System.currentTimeMillis());
        long id = db.insertWithOnConflict("transactions", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        if (id > 0) autoLocalBackupAfterChange();
        return id;
    }


    private String extractCategoryOverride(String extra) {
        if (extra == null) return null;
        String marker = "category=";
        int i = extra.indexOf(marker);
        if (i < 0) return null;
        String cat = extra.substring(i + marker.length()).trim();
        int end = cat.indexOf(";");
        if (end >= 0) cat = cat.substring(0, end).trim();
        return cat.length() == 0 ? null : cat;
    }

    public MessageParser.ParsedTransaction parseBankMessageSmart(String raw) {
        MessageParser.ParsedTransaction tx = MessageParser.parseBankMessage(raw);
        JSONObject rule = matchLearnedBankRule(raw);
        if (rule != null) {
            String learnedKind = rule.optString("kind", "");
            if ("SKIP".equalsIgnoreCase(learnedKind)) return null;
            if (tx == null) tx = parseLearnedBankMessage(raw, rule);
            else applyLearnedRule(tx, rule);
        } else if (tx != null && shouldSendToSmartReview(tx)) {
            moveToSmartReview(tx, "شكل رسالة جديد محتاج تأكيد");
        }
        if (tx != null) {
            tx.fingerprint = buildStableBankFingerprint(tx);
        }
        return tx;
    }

    private boolean shouldSendToSmartReview(MessageParser.ParsedTransaction tx) {
        if (tx == null) return false;
        String type = tx.type == null ? "" : tx.type;
        String status = tx.status == null ? "" : tx.status;
        if ("PENDING_REVIEW".equals(status)) return true;
        if ("PENDING_ONLINE".equals(status) || "PENDING_INCOMING".equals(status)) return false;
        // أي رسالة بنك عامة مش متعلمة تدخل شاشة مراجعة ذكية بدل ما تتخصم تلقائيًا بالغلط.
        return type.startsWith("GENERIC_BANK") || type.startsWith("UNKNOWN_BANK");
    }

    private void moveToSmartReview(MessageParser.ParsedTransaction tx, String reason) {
        tx.type = "SMART_REVIEW_BANK_MESSAGE";
        tx.status = "PENDING_REVIEW";
        tx.affectsBudget = 0;
        String base = safeTitle(tx.merchant, tx.title);
        tx.title = "رسالة بنك للمراجعة - " + base;
        tx.extra = appendExtra(tx.extra, "reviewReason=" + reason);
    }

    private String buildStableBankFingerprint(MessageParser.ParsedTransaction tx) {
        if (tx == null) return "";
        String src = tx.source == null ? "" : tx.source.toLowerCase(Locale.ROOT);
        String raw = tx.raw == null ? "" : tx.raw;
        boolean bankLike = src.contains("sms") || src.contains("notification") || src.contains("bank") || src.contains("learned")
                || raw.toLowerCase(Locale.ROOT).contains("sar") || raw.contains("ريال") || raw.contains("شراء") || raw.contains("حوالة");
        if (!bankLike) return tx.fingerprint == null ? MessageParser.sha256("manual|" + raw + "|" + tx.dateMillis) : tx.fingerprint;
        long minuteBucket = (tx.dateMillis > 0 ? tx.dateMillis : System.currentTimeMillis()) / (15L * 60L * 1000L);
        String direction = tx.affectsBudget == 1 ? "expense" : ("EXTRA_INCOME".equals(tx.type) || "INCOMING_TRANSFER".equals(tx.type) ? "income" : "review");
        String key = direction + "|" + String.format(Locale.US, "%.2f", tx.amount) + "|" + safe(tx.currency) + "|" + safe(tx.card) + "|" + compactText(tx.merchant + " " + tx.title) + "|" + minuteBucket;
        return MessageParser.sha256(key);
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }

    private String compactText(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{Nd}]+", " ")
                .replaceAll("\\b[0-9]{1,2}[:/][0-9]{1,2}[^ ]*", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public void learnBankMessage(String sample, String kind, String category) {
        if (sample == null || sample.trim().length() == 0) return;
        JSONArray arr = getLearnedRulesArray();
        JSONObject rule = new JSONObject();
        try {
            rule.put("keywords", buildRuleKeywords(sample));
            rule.put("kind", kind == null ? "EXPENSE" : kind);
            rule.put("category", category == null || category.trim().isEmpty() ? "عام" : category.trim());
            rule.put("sample", sample.trim());
            rule.put("createdAt", System.currentTimeMillis());
            arr.put(rule);
            setSetting("bank_learning_rules", arr.toString());
        } catch (Exception ignored) {}
    }

    public int learnedBankRuleCount() {
        return getLearnedRulesArray().length();
    }

    public void clearLearnedBankRules() {
        setSetting("bank_learning_rules", "[]");
    }

    private JSONArray getLearnedRulesArray() {
        try { return new JSONArray(getSetting("bank_learning_rules", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private JSONObject matchLearnedBankRule(String raw) {
        if (raw == null) return null;
        String n = normalizeRuleText(raw);
        JSONArray arr = getLearnedRulesArray();
        JSONObject best = null;
        int bestScore = 0;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String[] keys = o.optString("keywords", "").split("\\|");
            int total = 0, score = 0;
            for (String k : keys) {
                k = normalizeRuleText(k);
                if (k.length() < 2) continue;
                total++;
                if (n.contains(k)) score++;
            }
            int need = total <= 2 ? total : Math.max(2, (int)Math.ceil(total * 0.55));
            if (total > 0 && score >= need && score > bestScore) {
                bestScore = score;
                best = o;
            }
        }
        return best;
    }

    private void applyLearnedRule(MessageParser.ParsedTransaction tx, JSONObject rule) {
        if (tx == null || rule == null) return;
        String kind = rule.optString("kind", "EXPENSE");
        if ("SKIP".equals(kind)) {
            tx.type = "BANK_SKIP_RULE";
            tx.status = "SKIPPED";
            tx.affectsBudget = 0;
            tx.title = "رسالة متخطاة";
        } else if ("INCOME".equals(kind)) {
            tx.type = "EXTRA_INCOME";
            tx.status = "CONFIRMED";
            tx.affectsBudget = 0;
            tx.title = "دخل بنكي - " + safeTitle(tx.merchant, tx.title);
        } else if ("ONLINE".equals(kind)) {
            tx.type = "ONLINE_PURCHASE";
            tx.status = "PENDING_ONLINE";
            tx.affectsBudget = 0;
            tx.title = "شراء أونلاين للمراجعة - " + safeTitle(tx.merchant, tx.title);
        } else if ("SAVE_ONLY".equals(kind)) {
            tx.type = "BANK_SAVED_ONLY";
            tx.status = "SAVED_ONLY";
            tx.affectsBudget = 0;
            tx.title = "عملية محفوظة فقط - " + safeTitle(tx.merchant, tx.title);
        } else {
            tx.type = "LEARNED_BANK_EXPENSE";
            tx.status = "CONFIRMED";
            tx.affectsBudget = 1;
            tx.title = "خصم بنكي - " + safeTitle(tx.merchant, tx.title);
        }
        String cat = rule.optString("category", "عام").trim();
        if (cat.length() > 0) tx.extra = appendExtra(tx.extra, "category=" + cat);
    }

    private MessageParser.ParsedTransaction parseLearnedBankMessage(String raw, JSONObject rule) {
        double amount = findLearnedAmount(raw);
        if (amount <= 0) return null;
        MessageParser.ParsedTransaction tx = new MessageParser.ParsedTransaction();
        tx.raw = raw == null ? "" : raw.trim();
        tx.amount = amount;
        tx.currency = guessCurrency(tx.raw);
        tx.dateMillis = parseLearnedDate(tx.raw);
        tx.fingerprint = MessageParser.sha256("learned|" + normalizeRuleText(tx.raw));
        tx.card = findLearnedCard(tx.raw);
        tx.merchant = findLearnedName(tx.raw);
        tx.source = "learned_bank_message";
        tx.extra = "";
        applyLearnedRule(tx, rule);
        return tx;
    }

    private String appendExtra(String base, String add) {
        if (base == null || base.trim().isEmpty()) return add;
        if (base.contains(add)) return base;
        return base + ";" + add;
    }

    private String safeTitle(String merchant, String title) {
        if (merchant != null && merchant.trim().length() > 0) return merchant.trim();
        if (title != null && title.trim().length() > 0) return title.trim();
        return "رسالة بنك";
    }

    private String buildRuleKeywords(String sample) {
        String n = normalizeRuleText(sample);
        String[] parts = n.split("\\s+");
        List<String> keys = new ArrayList<>();
        String stop = " sar sr egp usd eur ريال جنيه مبلغ المبلغ رصيد بطاقة عبر لدى لدي من الى إلى في on at the and تم تمت عملية عميل عزيزي dear customer account acct no رقم ";
        for (String p : parts) {
            p = p.trim();
            if (p.length() < 3) continue;
            if (p.matches("[0-9]+")) continue;
            if (stop.contains(" " + p + " ")) continue;
            if (!keys.contains(p)) keys.add(p);
            if (keys.size() >= 7) break;
        }
        if (keys.size() == 0) keys.add(n.length() > 20 ? n.substring(0, 20) : n);
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            if (sb.length() > 0) sb.append("|");
            sb.append(k);
        }
        return sb.toString();
    }

    private String normalizeRuleText(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replace('\u061C', ' ')
                .replace('\u200F', ' ')
                .replace('\u200E', ' ')
                .replaceAll("[^\\p{L}\\p{Nd}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private double findLearnedAmount(String text) {
        if (text == null) return 0;
        String[] regexes = {
                "(?:اجمالي|إجمالي|مبلغ|المبلغ|بقيمة|بمبلغ|خصم|دفع|شراء|سحب|ايداع|إيداع|تحويل|حوالة)\\s*[:：]?\\s*(?:SAR|SR|ريال|EGP|جنيه)?\\s*([0-9]+(?:[\\.,][0-9]+)?)",
                "(?:SAR|SR|ريال|EGP|جنيه)\\s*([0-9]+(?:[\\.,][0-9]+)?)",
                "([0-9]+(?:[\\.,][0-9]+)?)\\s*(?:SAR|SR|ريال|EGP|جنيه)"
        };
        for (String r : regexes) {
            Matcher m = Pattern.compile(r, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text);
            if (m.find()) return parseLearnedDouble(m.group(1));
        }
        Matcher m = Pattern.compile("([0-9]+(?:[\\.,][0-9]+)?)").matcher(text);
        return m.find() ? parseLearnedDouble(m.group(1)) : 0;
    }

    private double parseLearnedDouble(String s) {
        try { return Double.parseDouble(s.replace(",", ".")); } catch (Exception e) { return 0; }
    }

    private String guessCurrency(String text) {
        String t = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return (t.contains("egp") || t.contains("جنيه")) ? "EGP" : "SAR";
    }

    private String findLearnedCard(String text) {
        if (text == null) return "";
        Matcher m = Pattern.compile("(?:بطاقة|card|عبر|من|لـ|ending)\\s*[:：;]?\\s*([0-9]{4})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text);
        return m.find() ? m.group(1) : "";
    }

    private String findLearnedName(String text) {
        if (text == null) return "رسالة بنك";
        String[] markers = {"لدى", "لدي", "لـ", "الى", "إلى", "من", "merchant", "at"};
        for (String marker : markers) {
            Matcher m = Pattern.compile(Pattern.quote(marker) + "\\s*[:：;]?\\s*([^\\n]+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text);
            if (m.find()) {
                String v = m.group(1).replaceAll("[0-9]{1,2}/[0-9]{1,2}/[0-9]{2,4}.*", "").trim();
                if (v.length() > 0) return v.length() > 40 ? v.substring(0, 40) : v;
            }
        }
        String[] lines = text.split("\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.length() > 3 && !line.matches(".*[0-9].*")) return line.length() > 40 ? line.substring(0, 40) : line;
        }
        return "رسالة بنك";
    }

    private long parseLearnedDate(String text) {
        if (text == null) return System.currentTimeMillis();
        Matcher m = Pattern.compile("([0-9]{1,2}/[0-9]{1,2}/[0-9]{2,4})\\s+([0-9]{1,2}:[0-9]{2})").matcher(text);
        if (m.find()) {
            String v = m.group(1) + " " + m.group(2);
            String[] formats = {"d/M/yy HH:mm", "d/M/yyyy HH:mm"};
            for (String f : formats) {
                try { Date d = new SimpleDateFormat(f, Locale.US).parse(v); if (d != null) return d.getTime(); }
                catch (Exception ignored) {}
            }
        }
        return System.currentTimeMillis();
    }


    private boolean isLikelyDuplicate(MessageParser.ParsedTransaction tx) {
        if (tx == null || tx.amount <= 0) return false;
        String src = tx.source == null ? "" : tx.source.toLowerCase(Locale.ROOT);
        String raw = tx.raw == null ? "" : tx.raw.toLowerCase(Locale.ROOT);
        boolean bankAuto = src.contains("sms") || src.contains("notification") || src.contains("bank") || src.contains("learned")
                || raw.contains("sar") || raw.contains("sr") || raw.contains("ريال") || raw.contains("شراء") || raw.contains("حوالة");
        if (!bankAuto) return false;

        long center = tx.dateMillis > 0 ? tx.dateMillis : System.currentTimeMillis();
        long from = center - 6L * 60L * 60L * 1000L;
        long to = center + 6L * 60L * 60L * 1000L;
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT title, merchant, card, type, amount, dateMillis, currency, raw, status, affectsBudget FROM transactions WHERE ABS(amount-?) < 0.01 AND dateMillis BETWEEN ? AND ? ORDER BY dateMillis DESC LIMIT 60", new String[]{String.valueOf(tx.amount), String.valueOf(from), String.valueOf(to)})) {
            String inMerchant = norm(tx.merchant + " " + tx.title);
            String inCompact = compactText(tx.raw + " " + tx.merchant + " " + tx.title);
            String inCard = tx.card == null ? "" : tx.card.trim();
            String inDirection = txDirection(tx.type, tx.status, tx.affectsBudget);
            String inCur = safe(tx.currency);
            while (c.moveToNext()) {
                String title = c.getString(0);
                String merchant = c.getString(1);
                String card = c.getString(2);
                String type = c.getString(3);
                long oldDate = c.getLong(5);
                String cur = c.getString(6);
                String oldRaw = c.getString(7);
                String status = c.getString(8);
                int affects = c.getInt(9);
                if (inCur.length() > 0 && cur != null && cur.length() > 0 && !inCur.equalsIgnoreCase(cur)) continue;

                String oldDirection = txDirection(type, status, affects);
                if (!inDirection.equals(oldDirection)) continue;

                long diff = Math.abs(center - oldDate);
                String existing = norm(merchant + " " + title);
                String oldCompact = compactText(oldRaw + " " + merchant + " " + title);

                // أقوى حالة: نفس المبلغ + نفس آخر 4 أرقام + نفس الاتجاه. ده غالبًا SMS وإشعار لنفس العملية.
                if (inCard.length() > 0 && card != null && inCard.equals(card.trim()) && diff <= 6L * 60L * 60L * 1000L) return true;

                // نفس التاجر/العنوان خلال 6 ساعات.
                if (existing.length() > 0 && inMerchant.length() > 0 && (existing.contains(inMerchant) || inMerchant.contains(existing)) && diff <= 6L * 60L * 60L * 1000L) return true;

                // مقارنة نص العملية بعد تنظيف التاريخ والأرقام، مفيدة لما الإشعار مختصر والـ SMS طويل.
                if (textSimilarity(inCompact, oldCompact) >= 0.52 && diff <= 6L * 60L * 60L * 1000L) return true;

                // لو مفيش تاجر واضح، نفس مبلغ واتجاه خلال 10 دقائق فقط.
                if (inMerchant.length() == 0 && existing.length() == 0 && diff <= 10L * 60L * 1000L) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private String txDirection(String type, String status, int affectsBudget) {
        String t = type == null ? "" : type.toUpperCase(Locale.ROOT);
        String st = status == null ? "" : status.toUpperCase(Locale.ROOT);
        if (t.contains("INCOME") || t.contains("INCOMING")) return "income";
        if (t.contains("DEBT_PAYMENT")) return "income";
        if (affectsBudget == 1 || t.contains("EXPENSE") || t.contains("PURCHASE") || t.contains("OUTGOING") || t.contains("WITHDRAWAL")) return "expense";
        if (st.contains("PENDING_INCOMING")) return "income";
        return "review";
    }

    private double textSimilarity(String a, String b) {
        if (a == null || b == null || a.length() == 0 || b.length() == 0) return 0;
        String[] aa = a.split("\\s+");
        String[] bb = b.split("\\s+");
        int common = 0, total = 0;
        for (String x : aa) {
            if (x.length() < 3 || x.matches("[0-9]+")) continue;
            total++;
            for (String y : bb) {
                if (x.equals(y)) { common++; break; }
            }
        }
        if (total == 0) return 0;
        return common / (double) Math.max(1, total);
    }

    private String norm(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}]", "").trim();
    }

    public long insertManual(String type, String status, double amount, String title, int affectsBudget, String raw) {
        return insertManualCurrency(type, status, amount, title, affectsBudget, raw, getCurrency());
    }

    public long insertManualCurrency(String type, String status, double amount, String title, int affectsBudget, String raw, String currency) {
        return insertManualCurrencyCategory(type, status, amount, title, affectsBudget, raw, currency, null);
    }

    public long insertManualCurrencyCategory(String type, String status, double amount, String title, int affectsBudget, String raw, String currency, String category) {
        MessageParser.ParsedTransaction tx = new MessageParser.ParsedTransaction();
        tx.type = type; tx.status = status; tx.amount = amount; tx.currency = "EGP".equalsIgnoreCase(currency) ? "EGP" : "SAR";
        tx.title = title; tx.merchant = title; tx.source = "manual"; tx.raw = raw;
        tx.dateMillis = System.currentTimeMillis();
        tx.fingerprint = MessageParser.sha256("manual|" + raw + "|" + tx.dateMillis);
        tx.affectsBudget = affectsBudget; tx.card = ""; tx.extra = category == null ? "" : ("category=" + category.trim());
        long id = insertParsed(tx);
        if (id > 0 && category != null && category.trim().length() > 0) {
            ContentValues cv = new ContentValues();
            cv.put("category", category.trim());
            getWritableDatabase().update("transactions", cv, "id=?", new String[]{String.valueOf(id)});
            autoLocalBackupAfterChange();
        }
        return id;
    }

    public double getMonthlySpent() {
        long start = monthStart();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE affectsBudget=1 AND status='CONFIRMED' AND dateMillis>=?", new String[]{String.valueOf(start)})) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    public double getExtraIncome() {
        long start = monthStart();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE type='EXTRA_INCOME' AND status='CONFIRMED' AND dateMillis>=?", new String[]{String.valueOf(start)})) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    public int getPendingCount() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM transactions WHERE status LIKE 'PENDING%'", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public List<Tx> getPending() {
        return queryTx("SELECT * FROM transactions WHERE status LIKE 'PENDING%' ORDER BY dateMillis DESC", null);
    }

    public List<Tx> getRecent(int limit) {
        return queryTx("SELECT * FROM transactions ORDER BY dateMillis DESC LIMIT " + limit, null);
    }

    public List<Tx> getRecentFiltered(String currency, String category, int limit) {
        String sql = "SELECT * FROM transactions WHERE 1=1";
        List<String> args = new ArrayList<>();
        if (currency != null && currency.trim().length() > 0 && !"ALL".equals(currency)) {
            sql += " AND currency=?";
            args.add(currency.trim());
        }
        if (category != null && category.trim().length() > 0 && !"ALL".equals(category)) {
            sql += " AND category=?";
            args.add(category.trim());
        }
        sql += " ORDER BY dateMillis DESC LIMIT " + limit;
        return queryTx(sql, args.toArray(new String[0]));
    }

    public List<String> getCategories() {
        List<String> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT DISTINCT COALESCE(category,'عام') FROM transactions ORDER BY 1", null)) {
            while (c.moveToNext()) {
                String cat = c.getString(0);
                if (cat != null && cat.trim().length() > 0) list.add(cat);
            }
        }
        return list;
    }


    public List<String> getAllCategoryNames() {
        List<String> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT name FROM budget_categories WHERE active=1 ORDER BY id ASC", null)) {
            while (c.moveToNext()) {
                String name = c.getString(0);
                if (name != null && name.trim().length() > 0 && !list.contains(name)) list.add(name);
            }
        } catch (Exception ignored) {}
        if (!list.contains("عام")) list.add("عام");
        return list;
    }

    private List<Tx> queryTx(String sql, String[] args) {
        List<Tx> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery(sql, args)) {
            while (c.moveToNext()) list.add(Tx.from(c));
        }
        return list;
    }

    public void approveOnline(long id) {
        ContentValues cv = new ContentValues();
        cv.put("status", "CONFIRMED"); cv.put("affectsBudget", 1); cv.put("type", "ONLINE_PURCHASE_APPROVED");
        getWritableDatabase().update("transactions", cv, "id=?", new String[]{String.valueOf(id)});
        autoLocalBackupAfterChange();
    }

    public void saveOnly(long id) {
        ContentValues cv = new ContentValues();
        cv.put("status", "SAVED_ONLY"); cv.put("affectsBudget", 0);
        getWritableDatabase().update("transactions", cv, "id=?", new String[]{String.valueOf(id)});
        autoLocalBackupAfterChange();
    }

    public void markExtraIncome(long id) {
        ContentValues cv = new ContentValues();
        cv.put("status", "CONFIRMED"); cv.put("affectsBudget", 0); cv.put("type", "EXTRA_INCOME");
        getWritableDatabase().update("transactions", cv, "id=?", new String[]{String.valueOf(id)});
        autoLocalBackupAfterChange();
    }

    public void markDebtPayment(long id) {
        ContentValues cv = new ContentValues();
        cv.put("status", "CONFIRMED"); cv.put("affectsBudget", 0); cv.put("type", "DEBT_PAYMENT");
        getWritableDatabase().update("transactions", cv, "id=?", new String[]{String.valueOf(id)});
        autoLocalBackupAfterChange();
    }

    public void updateTransactionAmount(long id, double amount) {
        ContentValues cv = new ContentValues(); cv.put("amount", amount);
        getWritableDatabase().update("transactions", cv, "id=?", new String[]{String.valueOf(id)});
        autoLocalBackupAfterChange();
    }

    public void updateTransaction(long id, double amount, String title, String category, int affectsBudget) {
        ContentValues cv = new ContentValues();
        cv.put("amount", amount);
        cv.put("title", title == null ? "" : title.trim());
        cv.put("merchant", title == null ? "" : title.trim());
        cv.put("category", category == null || category.trim().isEmpty() ? "عام" : category.trim());
        cv.put("affectsBudget", affectsBudget);
        getWritableDatabase().update("transactions", cv, "id=?", new String[]{String.valueOf(id)});
        autoLocalBackupAfterChange();
    }

    public void updateTransactionDecision(long id, double amount, String title, String category, String type, String status, int affectsBudget) {
        ContentValues cv = new ContentValues();
        if (amount > 0) cv.put("amount", amount);
        if (title != null) {
            cv.put("title", title.trim());
            cv.put("merchant", title.trim());
        }
        cv.put("category", category == null || category.trim().isEmpty() ? "عام" : category.trim());
        cv.put("type", type == null || type.trim().isEmpty() ? "SMART_REVIEW_DECIDED" : type.trim());
        cv.put("status", status == null || status.trim().isEmpty() ? "CONFIRMED" : status.trim());
        cv.put("affectsBudget", affectsBudget);
        getWritableDatabase().update("transactions", cv, "id=?", new String[]{String.valueOf(id)});
        autoLocalBackupAfterChange();
    }

    public void deleteTransaction(long id) {
        int changed = getWritableDatabase().delete("transactions", "id=?", new String[]{String.valueOf(id)});
        if (changed > 0) autoLocalBackupAfterChange();
    }

    public void adjustMonthlySpentTo(double targetSpent) {
        targetSpent = Math.max(0, targetSpent);
        double current = getMonthlySpent();
        double delta = targetSpent - current;
        if (Math.abs(delta) < 0.01) return;
        String title = delta >= 0 ? "تصحيح زيادة المصروف" : "تصحيح تقليل المصروف";
        insertManual("SPENT_ADJUSTMENT", "CONFIRMED", delta, title, 1, "تعديل إجمالي المصروف إلى " + targetSpent);
    }

    public long addDebt(String name, double amount, String whatsapp, String facebook, String notes, String direction, long dueDateMillis) {
        return addDebt(name, amount, whatsapp, facebook, notes, direction, dueDateMillis, getCurrency());
    }

    public long addDebt(String name, double amount, String whatsapp, String facebook, String notes, String direction, long dueDateMillis, String currency) {
        ContentValues cv = new ContentValues();
        cv.put("name", name); cv.put("amount", amount); cv.put("paid", 0);
        cv.put("currency", "EGP".equalsIgnoreCase(currency) ? "EGP" : "SAR");
        cv.put("whatsapp", whatsapp); cv.put("facebook", facebook); cv.put("notes", notes);
        cv.put("status", "OPEN");
        cv.put("direction", "OWE_TO_OTHERS".equals(direction) ? "OWE_TO_OTHERS" : "OWED_TO_ME");
        cv.put("dueDateMillis", Math.max(0, dueDateMillis));
        cv.put("createdAt", System.currentTimeMillis()); cv.put("updatedAt", System.currentTimeMillis());
        long id = getWritableDatabase().insert("debts", null, cv);
        if (id > 0) autoLocalBackupAfterChange();
        return id;
    }

    public void updateDebtDueDate(long debtId, long dueDateMillis) {
        ContentValues cv = new ContentValues();
        cv.put("dueDateMillis", Math.max(0, dueDateMillis));
        cv.put("updatedAt", System.currentTimeMillis());
        getWritableDatabase().update("debts", cv, "id=?", new String[]{String.valueOf(debtId)});
        autoLocalBackupAfterChange();
    }

    public List<Debt> getDebts() {
        List<Debt> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM debts WHERE status!='PAID' AND (amount-paid)>0.009 ORDER BY status ASC, dueDateMillis ASC, updatedAt DESC", null)) {
            while (c.moveToNext()) list.add(Debt.from(c));
        }
        return list;
    }

    public Debt getDebtById(long id) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM debts WHERE id=?", new String[]{String.valueOf(id)})) {
            if (c.moveToFirst()) return Debt.from(c);
        }
        return null;
    }

    public List<Debt> getDebtsByDirection(String direction) {
        List<Debt> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String dir = "OWE_TO_OTHERS".equals(direction) ? "OWE_TO_OTHERS" : "OWED_TO_ME";
        try (Cursor c = db.rawQuery("SELECT * FROM debts WHERE direction=? AND status!='PAID' AND (amount-paid)>0.009 ORDER BY status ASC, dueDateMillis ASC, updatedAt DESC", new String[]{dir})) {
            while (c.moveToNext()) list.add(Debt.from(c));
        }
        return list;
    }

    public void addDebtPayment(long debtId, double amount, String note, long txId) {
        addDebtPayment(debtId, amount, note, txId, "");
    }

    public void addDebtPayment(long debtId, double amount, String note, long txId, String screenshotUri) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues p = new ContentValues();
        p.put("debtId", debtId); p.put("amount", amount); p.put("note", note); p.put("dateMillis", System.currentTimeMillis()); p.put("txId", txId);
        p.put("screenshotUri", screenshotUri == null ? "" : screenshotUri);
        db.insert("debt_payments", null, p);
        try (Cursor c = db.rawQuery("SELECT amount, paid FROM debts WHERE id=?", new String[]{String.valueOf(debtId)})) {
            if (c.moveToFirst()) {
                double total = c.getDouble(0); double paid = c.getDouble(1) + amount;
                ContentValues cv = new ContentValues(); cv.put("paid", paid); cv.put("updatedAt", System.currentTimeMillis());
                cv.put("status", paid >= total ? "PAID" : "PARTIAL");
                db.update("debts", cv, "id=?", new String[]{String.valueOf(debtId)});
            }
        }
        autoLocalBackupAfterChange();
    }

    public List<DebtPayment> getDebtPayments(long debtId) {
        List<DebtPayment> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM debt_payments WHERE debtId=? ORDER BY dateMillis DESC", new String[]{String.valueOf(debtId)})) {
            while (c.moveToNext()) list.add(DebtPayment.from(c));
        } catch (Exception ignored) {}
        return list;
    }

    public long addSubscription(String name, double amount, String currency, String merchant, String category, long nextDateMillis, String notes) {
        ContentValues cv = new ContentValues();
        cv.put("name", name == null ? "" : name.trim());
        cv.put("amount", amount);
        cv.put("currency", "EGP".equalsIgnoreCase(currency) ? "EGP" : "SAR");
        cv.put("merchant", merchant == null ? "" : merchant.trim());
        cv.put("category", category == null || category.trim().isEmpty() ? "اشتراكات/أونلاين" : category.trim());
        cv.put("nextDateMillis", Math.max(0, nextDateMillis));
        cv.put("active", 1);
        cv.put("notes", notes == null ? "" : notes.trim());
        cv.put("lastChargedMillis", 0);
        cv.put("createdAt", System.currentTimeMillis());
        cv.put("updatedAt", System.currentTimeMillis());
        long id = getWritableDatabase().insert("subscriptions", null, cv);
        if (id > 0) autoLocalBackupAfterChange();
        return id;
    }

    public List<Subscription> getSubscriptions() {
        List<Subscription> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM subscriptions ORDER BY active DESC, nextDateMillis ASC, updatedAt DESC", null)) {
            while (c.moveToNext()) list.add(Subscription.from(c));
        }
        return list;
    }

    public Subscription getSubscriptionById(long id) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM subscriptions WHERE id=?", new String[]{String.valueOf(id)})) {
            if (c.moveToFirst()) return Subscription.from(c);
        }
        return null;
    }

    public void toggleSubscription(long id, boolean active) {
        ContentValues cv = new ContentValues();
        cv.put("active", active ? 1 : 0); cv.put("updatedAt", System.currentTimeMillis());
        getWritableDatabase().update("subscriptions", cv, "id=?", new String[]{String.valueOf(id)});
        autoLocalBackupAfterChange();
    }

    public long chargeSubscription(long id) {
        Subscription s = getSubscriptionById(id);
        if (s == null) return -1;
        long tx = insertManualCurrency("SUBSCRIPTION", "CONFIRMED", s.amount, s.name, 1, "اشتراك شهري: " + s.name, s.currency);
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(s.nextDateMillis > 0 ? s.nextDateMillis : System.currentTimeMillis());
        cal.add(Calendar.MONTH, 1);
        ContentValues cv = new ContentValues();
        cv.put("lastChargedMillis", System.currentTimeMillis());
        cv.put("nextDateMillis", cal.getTimeInMillis());
        cv.put("updatedAt", System.currentTimeMillis());
        getWritableDatabase().update("subscriptions", cv, "id=?", new String[]{String.valueOf(id)});
        autoLocalBackupAfterChange();
        return tx;
    }

    public double getActiveSubscriptionsTotal() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM subscriptions WHERE active=1", null)) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    public double getOpenDebtsTotal() { return getDebtTotal(null); }

    public double getDebtTotal(String direction) {
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT COALESCE(SUM(amount-paid),0) FROM debts WHERE status!='PAID'";
        String[] args = null;
        if (direction != null) { sql += " AND direction=?"; args = new String[]{direction}; }
        try (Cursor c = db.rawQuery(sql, args)) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }


    public double getDebtTotal(String direction, String currency) {
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT COALESCE(SUM(amount-paid),0) FROM debts WHERE status!='PAID' AND COALESCE(currency,'SAR')=?";
        List<String> args = new ArrayList<>();
        args.add("EGP".equalsIgnoreCase(currency) ? "EGP" : "SAR");
        if (direction != null) { sql += " AND direction=?"; args.add(direction); }
        try (Cursor c = db.rawQuery(sql, args.toArray(new String[0]))) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    public int getUpcomingDebtCount(long untilMillis) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM debts WHERE status!='PAID' AND dueDateMillis>0 AND dueDateMillis<=?", new String[]{String.valueOf(untilMillis)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public double getTodaySpent() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE affectsBudget=1 AND status='CONFIRMED' AND dateMillis>=?", new String[]{String.valueOf(start)})) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    public int getMonthlyExpenseCount() {
        long start = monthStart();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM transactions WHERE affectsBudget=1 AND status='CONFIRMED' AND dateMillis>=?", new String[]{String.valueOf(start)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public double getPendingTotal() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE status LIKE 'PENDING%'", null)) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    public List<CatTotal> getSpendingByCategory(int limit) {
        long start = monthStart();
        List<CatTotal> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COALESCE(category,'عام') AS cat, COALESCE(SUM(amount),0) AS total FROM transactions WHERE affectsBudget=1 AND status='CONFIRMED' AND dateMillis>=? GROUP BY category ORDER BY total DESC LIMIT " + limit, new String[]{String.valueOf(start)})) {
            while (c.moveToNext()) {
                CatTotal ct = new CatTotal();
                ct.category = c.getString(0) == null ? "عام" : c.getString(0);
                ct.total = c.getDouble(1);
                list.add(ct);
            }
        }
        return list;
    }

    private long monthStart() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private String guessCategory(String text) {
        String t = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (t.contains("google") || t.contains("netflix") || t.contains("apple") || t.contains("spotify") || t.contains("شاهد") || t.contains("اشتراك") || t.contains("subscription") || t.contains("youtube") || t.contains("icloud")) return "اشتراكات/أونلاين";
        if (t.contains("مطعم") || t.contains("raghif") || t.contains("قهوة") || t.contains("قهوه") || t.contains("كوفي") || t.contains("شاي") || t.contains("اكل") || t.contains("أكل") || t.contains("غدا") || t.contains("عشا") || t.contains("فطار") || t.contains("كافيه") || t.contains("food") || t.contains("restaurant") || t.contains("cafe") || t.contains("coffee")) return "أكل ومشروبات";
        if (t.contains("بنزين") || t.contains("وقود") || t.contains("uber") || t.contains("careem") || t.contains("taxi") || t.contains("أوبر") || t.contains("كريم") || t.contains("مواصلات")) return "مواصلات";
        if (t.contains("صيدلية") || t.contains("pharmacy") || t.contains("دواء") || t.contains("مستشفى") || t.contains("طبيب")) return "صحة";
        if (t.contains("stc") || t.contains("زين") || t.contains("كهرب") || t.contains("مياه") || t.contains("فاتورة") || t.contains("فواتير") || t.contains("اتصالات")) return "فواتير";
        if (t.contains("حوالة") || t.contains("تحويل") || t.contains("transfer")) return "تحويلات";
        return "عام";
    }

    private void seedDefaultCategories(SQLiteDatabase db) {
        String[] names = {"أكل ومشروبات", "مواصلات", "فواتير", "اشتراكات/أونلاين", "تحويلات", "صحة", "كاش", "ترفيه", "عام"};
        for (String n : names) {
            ContentValues cv = new ContentValues();
            cv.put("name", n); cv.put("monthlyLimit", 0); cv.put("currency", "SAR"); cv.put("active", 1);
            cv.put("createdAt", System.currentTimeMillis()); cv.put("updatedAt", System.currentTimeMillis());
            db.insertWithOnConflict("budget_categories", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    public long addOrUpdateBudgetCategory(String name, double monthlyLimit) {
        if (name == null || name.trim().isEmpty()) return -1;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name.trim()); cv.put("monthlyLimit", Math.max(0, monthlyLimit)); cv.put("currency", getCurrency()); cv.put("active", 1);
        cv.put("createdAt", System.currentTimeMillis()); cv.put("updatedAt", System.currentTimeMillis());
        long id = db.insertWithOnConflict("budget_categories", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        if (id != -1) autoLocalBackupAfterChange();
        return id;
    }

    public void deleteBudgetCategory(long id) {
        int changed = getWritableDatabase().delete("budget_categories", "id=?", new String[]{String.valueOf(id)});
        if (changed > 0) autoLocalBackupAfterChange();
    }

    public List<BudgetCategory> getBudgetCategories() {
        List<BudgetCategory> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM budget_categories WHERE active=1 ORDER BY name", null)) {
            while (c.moveToNext()) list.add(BudgetCategory.from(c));
        }
        return list;
    }

    public double getCategorySpent(String category) {
        long start = monthStart();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE affectsBudget=1 AND status='CONFIRMED' AND dateMillis>=? AND category=?", new String[]{String.valueOf(start), category})) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    public CategoryBudgetAlert getCategoryBudgetAlert(String category, double threshold) {
        if (category == null || category.trim().isEmpty()) return null;
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM budget_categories WHERE active=1 AND name=? LIMIT 1", new String[]{category.trim()})) {
            if (!c.moveToFirst()) return null;
            BudgetCategory bc = BudgetCategory.from(c);
            if (bc.monthlyLimit <= 0) return null;
            double spent = getCategorySpent(bc.name);
            double pct = spent / Math.max(1, bc.monthlyLimit);
            if (pct < threshold) return null;
            return new CategoryBudgetAlert(bc.name, bc.monthlyLimit, spent, pct);
        } catch (Exception e) {
            return null;
        }
    }

    public List<CategoryBudgetAlert> getCategoryBudgetAlerts(double threshold) {
        List<CategoryBudgetAlert> out = new ArrayList<>();
        for (BudgetCategory bc : getBudgetCategories()) {
            if (bc.monthlyLimit <= 0) continue;
            double spent = getCategorySpent(bc.name);
            double pct = spent / Math.max(1, bc.monthlyLimit);
            if (pct >= threshold) out.add(new CategoryBudgetAlert(bc.name, bc.monthlyLimit, spent, pct));
        }
        return out;
    }

    public double getCategoryBudgetLimit(String category) {
        CategoryBudgetAlert a = getCategoryBudgetAlert(category, 0.0);
        return a == null ? 0 : a.limit;
    }

    public double getCashBalance() { return getDoubleSetting("cash_balance", 0); }
    public void setCashBalance(double value) { setSetting("cash_balance", String.valueOf(Math.max(0, value))); }
    public void addCash(double amount, String note) {
        if (amount <= 0) return;
        setCashBalance(getCashBalance() + amount);
        insertManualCurrencyCategory("CASH_IN", "CONFIRMED", amount, note == null || note.isEmpty() ? "إضافة كاش" : note, 0, "cash in", getCurrency(), "كاش");
    }
    public void spendCash(double amount, String title, String category) {
        if (amount <= 0) return;
        setCashBalance(Math.max(0, getCashBalance() - amount));
        insertManualCurrencyCategory("CASH_EXPENSE", "CONFIRMED", amount, title == null || title.isEmpty() ? "مصروف كاش" : title, 1, "cash expense", getCurrency(), category == null || category.trim().isEmpty() ? "كاش" : category);
    }

    public double abnormalThreshold() {
        double custom = getDoubleSetting("abnormal_expense_threshold", 0);
        if (custom > 0) return custom;
        double b = getBudget();
        return Math.max(500, b > 0 ? b * 0.35 : 500);
    }
    public boolean isAbnormalExpense(double amount) { return amount >= abnormalThreshold(); }

    public String currentMonthKey() {
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
    }
    public void autoArchiveIfNewMonth() {
        String current = currentMonthKey();
        String last = getSetting("last_open_month", "");
        if (last.length() == 0) { setSetting("last_open_month", current); return; }
        if (!current.equals(last)) {
            closeMonth(last);
            setSetting("last_open_month", current);
        }
    }
    public void applyMonthlyBudgetForCurrentMonth() {
        try {
            String current = currentMonthKey();
            String applied = getSetting("monthly_budget_applied_month", "");
            if (current.equals(applied)) return;
            double template = getMonthlyBudgetTemplate();
            if (template > 0) setSetting("monthly_budget", String.valueOf(template));
            setSetting("monthly_budget_applied_month", current);
        } catch (Exception ignored) {}
    }
    public void closeCurrentMonth() { closeMonth(currentMonthKey()); }
    private void closeMonth(String monthKey) {
        long[] range = monthRange(monthKey);
        double spent = sumTransactions(range[0], range[1], "affectsBudget=1 AND status='CONFIRMED'");
        double income = sumTransactions(range[0], range[1], "type='EXTRA_INCOME' AND status='CONFIRMED'");
        JSONObject summary = new JSONObject();
        try {
            summary.put("monthKey", monthKey); summary.put("currency", getCurrency()); summary.put("closedAt", System.currentTimeMillis());
            JSONArray cats = new JSONArray();
            SQLiteDatabase rdb = getReadableDatabase();
            try (Cursor c = rdb.rawQuery("SELECT COALESCE(category,'عام') AS cat, COALESCE(SUM(amount),0) AS total FROM transactions WHERE affectsBudget=1 AND status='CONFIRMED' AND dateMillis>=? AND dateMillis<? GROUP BY category ORDER BY total DESC", new String[]{String.valueOf(range[0]), String.valueOf(range[1])})) {
                while (c.moveToNext()) { JSONObject o = new JSONObject(); o.put("category", c.getString(0)); o.put("total", c.getDouble(1)); cats.put(o); }
            }
            summary.put("categories", cats);
        } catch (Exception ignored) {}
        ContentValues cv = new ContentValues();
        cv.put("monthKey", monthKey); cv.put("budget", getBudget()); cv.put("spent", spent); cv.put("extraIncome", income); cv.put("cashBalance", getCashBalance());
        cv.put("closedAt", System.currentTimeMillis()); cv.put("summary", summary.toString());
        getWritableDatabase().insertWithOnConflict("month_archives", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        autoLocalBackupAfterChange();
    }
    private long[] monthRange(String monthKey) {
        Calendar cal = Calendar.getInstance();
        try { Date d = new SimpleDateFormat("yyyy-MM", Locale.US).parse(monthKey); if (d != null) cal.setTime(d); } catch (Exception ignored) {}
        cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis(); cal.add(Calendar.MONTH, 1); return new long[]{start, cal.getTimeInMillis()};
    }
    private double sumTransactions(long start, long end, String where) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE " + where + " AND dateMillis>=? AND dateMillis<?", new String[]{String.valueOf(start), String.valueOf(end)})) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }
    public List<MonthArchive> getMonthArchives() {
        List<MonthArchive> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM month_archives ORDER BY monthKey DESC", null)) { while (c.moveToNext()) list.add(MonthArchive.from(c)); }
        return list;
    }


    public String exportBackupJson() throws Exception {
        JSONObject root = new JSONObject();
        root.put("app", "مصروفاتي");
        root.put("backupVersion", 1);
        root.put("generatedAt", System.currentTimeMillis());

        JSONObject settingsJson = new JSONObject();
        SQLiteDatabase rdb = getReadableDatabase();
        try (Cursor c = rdb.rawQuery("SELECT key,value FROM settings", null)) {
            while (c.moveToNext()) {
                String key = c.getString(0);
                if (key == null) continue;
                if (key.startsWith("firebase_") || key.startsWith("google_")) continue;
                settingsJson.put(key, c.getString(1));
            }
        }
        root.put("settings", settingsJson);
        root.put("transactions", tableToJson("transactions"));
        root.put("debts", tableToJson("debts"));
        root.put("debt_payments", tableToJson("debt_payments"));
        root.put("subscriptions", tableToJson("subscriptions"));
        root.put("budget_categories", tableToJson("budget_categories"));
        root.put("month_archives", tableToJson("month_archives"));
        root.put("tasks", tableToJson("tasks"));
        root.put("gold_holdings", tableToJson("gold_holdings"));
        root.put("saving_goals", tableToJson("saving_goals"));
        return root.toString();
    }

    private JSONArray tableToJson(String table) throws Exception {
        JSONArray arr = new JSONArray();
        SQLiteDatabase rdb = getReadableDatabase();
        try (Cursor c = rdb.rawQuery("SELECT * FROM " + table, null)) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                for (int i = 0; i < c.getColumnCount(); i++) {
                    String name = c.getColumnName(i);
                    int type = c.getType(i);
                    if (type == Cursor.FIELD_TYPE_NULL) o.put(name, JSONObject.NULL);
                    else if (type == Cursor.FIELD_TYPE_INTEGER) o.put(name, c.getLong(i));
                    else if (type == Cursor.FIELD_TYPE_FLOAT) o.put(name, c.getDouble(i));
                    else o.put(name, c.getString(i));
                }
                arr.put(o);
            }
        }
        return arr;
    }

    public boolean backupHasData(String jsonText) {
        try { return backupDataCount(new JSONObject(jsonText)) > 0; }
        catch (Exception e) { return false; }
    }

    public int backupRecordCount(String jsonText) {
        try { return backupDataCount(new JSONObject(jsonText)); }
        catch (Exception e) { return 0; }
    }

    private int backupDataCount(JSONObject root) {
        if (root == null) return 0;
        int total = 0;
        String[] tables = {"transactions", "debts", "debt_payments", "subscriptions", "budget_categories", "month_archives", "tasks", "gold_holdings", "saving_goals"};
        for (String t : tables) {
            JSONArray arr = root.optJSONArray(t);
            if (arr != null) total += arr.length();
        }
        return total;
    }

    public void importBackupJson(String jsonText) throws Exception {
        importBackupJsonMerge(jsonText);
    }

    public void importBackupJsonReplace(String jsonText) throws Exception {
        JSONObject root = new JSONObject(jsonText);
        if (backupDataCount(root) <= 0) throw new Exception("النسخة الاحتياطية فاضية ومش هتستبدل الداتا الحالية");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("transactions", null, null);
            db.delete("debts", null, null);
            db.delete("debt_payments", null, null);
            db.delete("subscriptions", null, null);
            db.delete("budget_categories", null, null);
            db.delete("month_archives", null, null);
            db.delete("tasks", null, null);
            db.delete("gold_holdings", null, null);
            db.delete("saving_goals", null, null);

            JSONObject settingsJson = root.optJSONObject("settings");
            if (settingsJson != null) {
                JSONArray keys = settingsJson.names();
                if (keys != null) {
                    for (int i = 0; i < keys.length(); i++) {
                        String key = keys.getString(i);
                        if (key.startsWith("firebase_") || key.startsWith("google_")) continue;
                        setSetting(db, key, settingsJson.optString(key, ""));
                    }
                }
            }
            jsonToTable(db, "transactions", root.optJSONArray("transactions"));
            jsonToTable(db, "debts", root.optJSONArray("debts"));
            jsonToTable(db, "debt_payments", root.optJSONArray("debt_payments"));
            jsonToTable(db, "subscriptions", root.optJSONArray("subscriptions"));
            jsonToTable(db, "budget_categories", root.optJSONArray("budget_categories"));
            jsonToTable(db, "month_archives", root.optJSONArray("month_archives"));
            jsonToTable(db, "tasks", root.optJSONArray("tasks"));
            jsonToTable(db, "gold_holdings", root.optJSONArray("gold_holdings"));
            jsonToTable(db, "saving_goals", root.optJSONArray("saving_goals"));
            setSetting(db, "last_cloud_restore", String.valueOf(System.currentTimeMillis()));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        autoLocalBackupAfterChange();
    }

    public void importBackupJsonMerge(String jsonText) throws Exception {
        JSONObject root = new JSONObject(jsonText);
        if (backupDataCount(root) <= 0) throw new Exception("النسخة الاحتياطية فاضية");
        SQLiteDatabase db = getWritableDatabase();
        String safetySnapshot = null;
        try { safetySnapshot = exportBackupJson(); } catch (Exception ignored) {}
        db.beginTransaction();
        try {
            JSONObject settingsJson = root.optJSONObject("settings");
            if (settingsJson != null) {
                JSONArray keys = settingsJson.names();
                if (keys != null) {
                    for (int i = 0; i < keys.length(); i++) {
                        String key = keys.getString(i);
                        if (key.startsWith("firebase_") || key.startsWith("google_")) continue;
                        setSetting(db, key, settingsJson.optString(key, ""));
                    }
                }
            }
            jsonToTableMerge(db, "transactions", root.optJSONArray("transactions"), null);
            jsonToTableMerge(db, "subscriptions", root.optJSONArray("subscriptions"), null);
            jsonToTableMerge(db, "budget_categories", root.optJSONArray("budget_categories"), null);
            jsonToTableMerge(db, "month_archives", root.optJSONArray("month_archives"), null);
            jsonToTableMerge(db, "tasks", root.optJSONArray("tasks"), null);
            jsonToTableMerge(db, "gold_holdings", root.optJSONArray("gold_holdings"), null);
            jsonToTableMerge(db, "saving_goals", root.optJSONArray("saving_goals"), null);

            JSONObject debtIdMap = jsonToTableMerge(db, "debts", root.optJSONArray("debts"), null);
            jsonToTableMerge(db, "debt_payments", root.optJSONArray("debt_payments"), debtIdMap);

            setSetting(db, "last_cloud_restore", String.valueOf(System.currentTimeMillis()));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (safetySnapshot != null && safetySnapshot.trim().length() > 0) {
            try { writeFile(privateRestoreSnapshotFile(), safetySnapshot); } catch (Exception ignored) {}
        }
        autoLocalBackupAfterChange();
    }

    private void jsonToTable(SQLiteDatabase db, String table, JSONArray arr) throws Exception {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            ContentValues cv = new ContentValues();
            JSONArray names = o.names();
            if (names == null) continue;
            for (int n = 0; n < names.length(); n++) {
                String k = names.getString(n);
                Object v = o.opt(k);
                if (v == null || v == JSONObject.NULL) cv.putNull(k);
                else if (v instanceof Integer || v instanceof Long) cv.put(k, ((Number) v).longValue());
                else if (v instanceof Float || v instanceof Double) cv.put(k, ((Number) v).doubleValue());
                else cv.put(k, String.valueOf(v));
            }
            db.insertWithOnConflict(table, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    private JSONObject jsonToTableMerge(SQLiteDatabase db, String table, JSONArray arr, JSONObject debtIdMap) throws Exception {
        JSONObject idMap = new JSONObject();
        if (arr == null) return idMap;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            ContentValues cv = jsonToContentValues(o);
            long oldId = o.optLong("id", 0);
            if ("debt_payments".equals(table) && debtIdMap != null && o.has("debtId")) {
                String mapped = debtIdMap.optString(String.valueOf(o.optLong("debtId", 0)), "");
                if (mapped.length() > 0) cv.put("debtId", Long.parseLong(mapped));
            }
            long inserted = db.insertWithOnConflict(table, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
            if (inserted > 0) {
                if (oldId > 0) idMap.put(String.valueOf(oldId), inserted);
                continue;
            }
            if (oldId > 0 && rowSame(db, table, o, oldId)) {
                idMap.put(String.valueOf(oldId), oldId);
                continue;
            }
            cv.remove("id");
            long cloned = db.insertWithOnConflict(table, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
            if (oldId > 0) idMap.put(String.valueOf(oldId), cloned > 0 ? cloned : oldId);
        }
        return idMap;
    }

    private ContentValues jsonToContentValues(JSONObject o) throws Exception {
        ContentValues cv = new ContentValues();
        JSONArray names = o.names();
        if (names == null) return cv;
        for (int n = 0; n < names.length(); n++) {
            String k = names.getString(n);
            Object v = o.opt(k);
            if (v == null || v == JSONObject.NULL) cv.putNull(k);
            else if (v instanceof Integer || v instanceof Long) cv.put(k, ((Number) v).longValue());
            else if (v instanceof Float || v instanceof Double) cv.put(k, ((Number) v).doubleValue());
            else cv.put(k, String.valueOf(v));
        }
        return cv;
    }

    private boolean rowSame(SQLiteDatabase db, String table, JSONObject incoming, long id) {
        try (Cursor c = db.rawQuery("SELECT * FROM " + table + " WHERE id=?", new String[]{String.valueOf(id)})) {
            if (!c.moveToFirst()) return false;
            JSONArray names = incoming.names();
            if (names == null) return true;
            for (int i = 0; i < names.length(); i++) {
                String k = names.getString(i);
                int idx = c.getColumnIndex(k);
                if (idx < 0) continue;
                Object v = incoming.opt(k);
                String a = v == null || v == JSONObject.NULL ? "" : String.valueOf(v);
                String b = c.isNull(idx) ? "" : c.getString(idx);
                if (!a.equals(b)) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    public void saveLocalBackup() {
        autoLocalBackupAfterChange();
    }

    public boolean restoreLocalBackupIfEmpty() {
        try {
            if (dataCount() > 0) return false;
            return restoreLocalBackupForce();
        } catch (Exception ignored) { return false; }
    }

    public boolean restoreLocalBackupForce() {
        try {
            String json = null;
            try { json = readPublicBackupText(); } catch (Exception ignored) {}
            if (json == null || json.trim().isEmpty()) {
                File f = privateBackupFile();
                if (f != null && f.exists()) json = readFile(f);
            }
            if (json == null || json.trim().isEmpty()) return false;
            importBackupJson(json);
            setSetting("last_local_restore", String.valueOf(System.currentTimeMillis()));
            return true;
        } catch (Exception ignored) { return false; }
    }

    public boolean hasUserData() {
        return dataCount() > 0;
    }

    public int userDataCount() {
        return dataCount();
    }

    private int dataCount() {
        int total = 0;
        SQLiteDatabase db = getReadableDatabase();
        String[] tables = {"transactions", "debts", "debt_payments", "subscriptions", "budget_categories", "month_archives", "tasks", "gold_holdings", "saving_goals"};
        for (String t : tables) {
            try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + t, null)) {
                if (c.moveToFirst()) total += c.getInt(0);
            } catch (Exception ignored) {}
        }
        return total;
    }

    private File privateBackupFile() {
        File dir = appContext.getExternalFilesDir(null);
        if (dir == null) dir = appContext.getFilesDir();
        return new File(dir, "masrofaty_backup.json");
    }

    private File privateRestoreSnapshotFile() {
        File dir = appContext.getExternalFilesDir(null);
        if (dir == null) dir = appContext.getFilesDir();
        return new File(dir, "masrofaty_before_restore.json");
    }

    private File publicBackupFile() {
        File base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File dir = new File(base, "Masrofaty");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, PUBLIC_BACKUP_FILE);
    }

    private File publicDownloadBackupFile() {
        File base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File dir = new File(base, "Masrofaty");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, PUBLIC_BACKUP_FILE_ALT);
    }

    private void writePublicBackupText(String text) throws Exception {
        String safe = text == null ? "" : text;
        if (Build.VERSION.SDK_INT >= 29) {
            // اكتب نسختين: Documents و Downloads. كده لو جهاز منع واحدة نلاقي التانية بعد إعادة التثبيت.
            try { writeMediaStoreBackup(safe, PUBLIC_BACKUP_DIR, PUBLIC_BACKUP_FILE); } catch (Exception ignored) {}
            try { writeMediaStoreBackup(safe, PUBLIC_BACKUP_DOWNLOAD_DIR, PUBLIC_BACKUP_FILE); } catch (Exception ignored) {}
            try { writeMediaStoreBackup(safe, PUBLIC_BACKUP_DOWNLOAD_DIR, PUBLIC_BACKUP_FILE_ALT); } catch (Exception ignored) {}
            return;
        }
        writeFile(publicBackupFile(), safe);
        try { writeFile(publicDownloadBackupFile(), safe); } catch (Exception ignored) {}
    }

    private void writeMediaStoreBackup(String text, String relativePath, String fileName) throws Exception {
        ContentResolver resolver = appContext.getContentResolver();
        Uri uri = findBackupUri(relativePath, fileName);
        if (uri == null) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
            if (Build.VERSION.SDK_INT >= 29) cv.put(MediaStore.MediaColumns.IS_PENDING, 0);
            Uri collection = relativePath.startsWith(Environment.DIRECTORY_DOWNLOADS)
                    ? MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    : MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            uri = resolver.insert(collection, cv);
        }
        if (uri != null) {
            OutputStream out = resolver.openOutputStream(uri, "wt");
            if (out != null) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.flush();
                out.close();
            }
        }
    }

    private String readPublicBackupText() throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            // جرب Documents ثم Downloads ثم ابحث باسم الملف فقط لو المسار اختلف من الجهاز.
            String[] names = new String[]{PUBLIC_BACKUP_FILE, PUBLIC_BACKUP_FILE_ALT};
            String[] paths = new String[]{PUBLIC_BACKUP_DIR, PUBLIC_BACKUP_DOWNLOAD_DIR};
            for (String name : names) {
                for (String path : paths) {
                    Uri uri = findBackupUri(path, name);
                    String v = readUriText(uri);
                    if (v != null && !v.trim().isEmpty()) return v;
                }
                Uri any = findBackupUriByName(name);
                String v = readUriText(any);
                if (v != null && !v.trim().isEmpty()) return v;
            }
        }
        File f = publicBackupFile();
        if (f != null && f.exists()) return readFile(f);
        File d = publicDownloadBackupFile();
        return d != null && d.exists() ? readFile(d) : null;
    }

    private String readUriText(Uri uri) throws Exception {
        if (uri == null) return null;
        InputStream in = appContext.getContentResolver().openInputStream(uri);
        if (in == null) return null;
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    private Uri findPublicBackupUri() {
        return findBackupUri(PUBLIC_BACKUP_DIR, PUBLIC_BACKUP_FILE);
    }

    private Uri findBackupUri(String relativePath, String fileName) {
        if (Build.VERSION.SDK_INT < 29) return null;
        try {
            ContentResolver resolver = appContext.getContentResolver();
            Uri collection = relativePath.startsWith(Environment.DIRECTORY_DOWNLOADS)
                    ? MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    : MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            String[] projection = new String[]{MediaStore.MediaColumns._ID};
            String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?";
            String[] args = new String[]{fileName, relativePath};
            Cursor c = resolver.query(collection, projection, selection, args, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) return Uri.withAppendedPath(collection, String.valueOf(c.getLong(0)));
                } finally { c.close(); }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Uri findBackupUriByName(String fileName) {
        if (Build.VERSION.SDK_INT < 29) return null;
        Uri[] collections = new Uri[]{
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        };
        for (Uri collection : collections) {
            try {
                String[] projection = new String[]{MediaStore.MediaColumns._ID};
                String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=?";
                Cursor c = appContext.getContentResolver().query(collection, projection, selection, new String[]{fileName}, null);
                if (c != null) {
                    try {
                        if (c.moveToFirst()) return Uri.withAppendedPath(collection, String.valueOf(c.getLong(0)));
                    } finally { c.close(); }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private void writeFile(File f, String text) throws Exception {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        FileWriter w = new FileWriter(f, false);
        w.write(text == null ? "" : text);
        w.flush();
        w.close();
    }

    private String readFile(File f) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(f));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    public static String date(long ms) { return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(ms); }

    public static class CatTotal {
        public String category;
        public double total;
    }

    public static class CategoryBudgetAlert {
        public String category;
        public double limit;
        public double spent;
        public double percent;
        public CategoryBudgetAlert(String category, double limit, double spent, double percent) {
            this.category = category;
            this.limit = limit;
            this.spent = spent;
            this.percent = percent;
        }
    }

    public static class BudgetCategory {
        public long id; public String name; public double monthlyLimit; public String currency; public int active;
        static BudgetCategory from(Cursor c) {
            BudgetCategory b = new BudgetCategory();
            b.id = c.getLong(c.getColumnIndexOrThrow("id"));
            b.name = c.getString(c.getColumnIndexOrThrow("name"));
            b.monthlyLimit = c.getDouble(c.getColumnIndexOrThrow("monthlyLimit"));
            b.currency = c.getString(c.getColumnIndexOrThrow("currency"));
            b.active = c.getInt(c.getColumnIndexOrThrow("active"));
            return b;
        }
    }

    public static class MonthArchive {
        public long id; public String monthKey; public double budget; public double spent; public double extraIncome; public double cashBalance; public long closedAt; public String summary;
        static MonthArchive from(Cursor c) {
            MonthArchive m = new MonthArchive();
            m.id = c.getLong(c.getColumnIndexOrThrow("id"));
            m.monthKey = c.getString(c.getColumnIndexOrThrow("monthKey"));
            m.budget = c.getDouble(c.getColumnIndexOrThrow("budget"));
            m.spent = c.getDouble(c.getColumnIndexOrThrow("spent"));
            m.extraIncome = c.getDouble(c.getColumnIndexOrThrow("extraIncome"));
            m.cashBalance = c.getDouble(c.getColumnIndexOrThrow("cashBalance"));
            m.closedAt = c.getLong(c.getColumnIndexOrThrow("closedAt"));
            m.summary = c.getString(c.getColumnIndexOrThrow("summary"));
            return m;
        }
    }

    public static class TaskItem {
        public long id,dueAt,repeatMinutes; public String title,notes; public int priority,completed;
        static TaskItem from(Cursor c){TaskItem t=new TaskItem();t.id=c.getLong(c.getColumnIndexOrThrow("id"));t.title=c.getString(c.getColumnIndexOrThrow("title"));t.notes=c.getString(c.getColumnIndexOrThrow("notes"));t.priority=c.getInt(c.getColumnIndexOrThrow("priority"));t.dueAt=c.getLong(c.getColumnIndexOrThrow("dueAt"));t.repeatMinutes=c.getLong(c.getColumnIndexOrThrow("repeatMinutes"));t.completed=c.getInt(c.getColumnIndexOrThrow("completed"));return t;}
    }
    public static class GoldHolding {
        public long id; public String label; public int karat; public double grams,purchasePrice;
        static GoldHolding from(Cursor c){GoldHolding g=new GoldHolding();g.id=c.getLong(c.getColumnIndexOrThrow("id"));g.label=c.getString(c.getColumnIndexOrThrow("label"));g.karat=c.getInt(c.getColumnIndexOrThrow("karat"));g.grams=c.getDouble(c.getColumnIndexOrThrow("grams"));g.purchasePrice=c.getDouble(c.getColumnIndexOrThrow("purchasePrice"));return g;}
    }

    public static class SavingGoal {
        public long id; public String title,type,unit; public double target,current; public int completed; public long createdAt,updatedAt;
        static SavingGoal from(Cursor c){SavingGoal g=new SavingGoal();g.id=c.getLong(c.getColumnIndexOrThrow("id"));g.title=c.getString(c.getColumnIndexOrThrow("title"));g.type=c.getString(c.getColumnIndexOrThrow("type"));g.target=c.getDouble(c.getColumnIndexOrThrow("target"));g.current=c.getDouble(c.getColumnIndexOrThrow("current"));g.unit=c.getString(c.getColumnIndexOrThrow("unit"));g.completed=c.getInt(c.getColumnIndexOrThrow("completed"));g.createdAt=c.getLong(c.getColumnIndexOrThrow("createdAt"));g.updatedAt=c.getLong(c.getColumnIndexOrThrow("updatedAt"));return g;}
    }

    public static class Tx {
        public long id; public String type; public String status; public double amount; public String currency; public String title; public String merchant;
        public String category; public String raw; public long dateMillis; public String card; public String extra; public int affectsBudget;
        static Tx from(Cursor c) {
            Tx t = new Tx();
            t.id = c.getLong(c.getColumnIndexOrThrow("id"));
            t.type = c.getString(c.getColumnIndexOrThrow("type"));
            t.status = c.getString(c.getColumnIndexOrThrow("status"));
            t.amount = c.getDouble(c.getColumnIndexOrThrow("amount"));
            int curIndex = c.getColumnIndex("currency");
            t.currency = curIndex >= 0 ? c.getString(curIndex) : "SAR";
            t.title = c.getString(c.getColumnIndexOrThrow("title"));
            t.merchant = c.getString(c.getColumnIndexOrThrow("merchant"));
            t.category = c.getString(c.getColumnIndexOrThrow("category"));
            t.raw = c.getString(c.getColumnIndexOrThrow("raw"));
            t.dateMillis = c.getLong(c.getColumnIndexOrThrow("dateMillis"));
            t.card = c.getString(c.getColumnIndexOrThrow("card"));
            t.extra = c.getString(c.getColumnIndexOrThrow("extra"));
            t.affectsBudget = c.getInt(c.getColumnIndexOrThrow("affectsBudget"));
            return t;
        }
    }

    public static class Subscription {
        public long id; public String name; public double amount; public String currency; public String merchant; public String category; public long nextDateMillis; public int active; public String notes; public long lastChargedMillis;
        static Subscription from(Cursor c) {
            Subscription s = new Subscription();
            s.id = c.getLong(c.getColumnIndexOrThrow("id"));
            s.name = c.getString(c.getColumnIndexOrThrow("name"));
            s.amount = c.getDouble(c.getColumnIndexOrThrow("amount"));
            s.currency = c.getString(c.getColumnIndexOrThrow("currency"));
            s.merchant = c.getString(c.getColumnIndexOrThrow("merchant"));
            s.category = c.getString(c.getColumnIndexOrThrow("category"));
            s.nextDateMillis = c.getLong(c.getColumnIndexOrThrow("nextDateMillis"));
            s.active = c.getInt(c.getColumnIndexOrThrow("active"));
            s.notes = c.getString(c.getColumnIndexOrThrow("notes"));
            s.lastChargedMillis = c.getLong(c.getColumnIndexOrThrow("lastChargedMillis"));
            return s;
        }
    }

    public static class DebtPayment {
        public long id; public long debtId; public double amount; public String note; public long dateMillis; public long txId; public String screenshotUri;
        static DebtPayment from(Cursor c) {
            DebtPayment p = new DebtPayment();
            p.id = c.getLong(c.getColumnIndexOrThrow("id"));
            p.debtId = c.getLong(c.getColumnIndexOrThrow("debtId"));
            p.amount = c.getDouble(c.getColumnIndexOrThrow("amount"));
            p.note = c.getString(c.getColumnIndexOrThrow("note"));
            p.dateMillis = c.getLong(c.getColumnIndexOrThrow("dateMillis"));
            p.txId = c.getLong(c.getColumnIndexOrThrow("txId"));
            int shotIndex = c.getColumnIndex("screenshotUri");
            p.screenshotUri = shotIndex >= 0 ? c.getString(shotIndex) : "";
            return p;
        }
    }

    public static class Debt {
        public long id; public String name; public double amount; public double paid; public String currency; public String whatsapp; public String facebook; public String notes; public String status; public String direction; public long dueDateMillis;
        static Debt from(Cursor c) {
            Debt d = new Debt();
            d.id = c.getLong(c.getColumnIndexOrThrow("id"));
            d.name = c.getString(c.getColumnIndexOrThrow("name"));
            d.amount = c.getDouble(c.getColumnIndexOrThrow("amount"));
            d.paid = c.getDouble(c.getColumnIndexOrThrow("paid"));
            int currencyIndex = c.getColumnIndex("currency");
            d.currency = currencyIndex >= 0 ? c.getString(currencyIndex) : "SAR";
            d.whatsapp = c.getString(c.getColumnIndexOrThrow("whatsapp"));
            d.facebook = c.getString(c.getColumnIndexOrThrow("facebook"));
            d.notes = c.getString(c.getColumnIndexOrThrow("notes"));
            d.status = c.getString(c.getColumnIndexOrThrow("status"));
            int dirIndex = c.getColumnIndex("direction");
            d.direction = dirIndex >= 0 ? c.getString(dirIndex) : "OWED_TO_ME";
            int dueIndex = c.getColumnIndex("dueDateMillis");
            d.dueDateMillis = dueIndex >= 0 ? c.getLong(dueIndex) : 0;
            return d;
        }
    }
}
