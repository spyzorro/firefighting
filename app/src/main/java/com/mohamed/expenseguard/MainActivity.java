package com.mohamed.expenseguard;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseUser;

import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BRAND = Color.rgb(15, 118, 110);
    private static final int BG = Color.rgb(247, 250, 249);
    private static final int TEXT = Color.rgb(15, 23, 42);

    private DatabaseHelper db;
    private SyncManager sync;
    private LinearLayout content;
    private String currentTab = "home";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DatabaseHelper(this);
        sync = new SyncManager(this, db);
        requestNotificationPermission();
        ReminderScheduler.scheduleAll(this, db);
        buildShell();
        showHome();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SyncManager.RC_SIGN_IN) {
            sync.handleSignInResult(data, this::showSync);
        }
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(14), dp(12), dp(14), dp(10));

        TextView title = new TextView(this);
        title.setText("إدارة الطفايات والمرتبات");
        title.setTextSize(23);
        title.setTextColor(TEXT);
        title.setGravity(Gravity.RIGHT);
        title.setTypeface(null, 1);
        root.addView(title, matchWrap());

        TextView sub = new TextView(this);
        sub.setText("تسجيل، تنبيهات، تقرير شهري، ومزامنة Google");
        sub.setTextColor(Color.rgb(71, 85, 105));
        sub.setGravity(Gravity.RIGHT);
        root.addView(sub, matchWrap());

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        addTab(tabs, "الرئيسية", v -> showHome());
        addTab(tabs, "المرتبات", v -> showSalaries());
        addTab(tabs, "الطفايات", v -> showExtinguishers());
        addTab(tabs, "العملاء", v -> showCustomers());
        addTab(tabs, "الشهادات", v -> showCertificates());
        addTab(tabs, "الصيانة", v -> showMaintenance());
        addTab(tabs, "تقرير الشهر", v -> showMonthlyReport());
        addTab(tabs, "Google", v -> showSync());
        hsv.addView(tabs);
        root.addView(hsv, matchWrap());

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(10), 0, dp(20));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void addTab(LinearLayout tabs, String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(BRAND);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44));
        lp.setMargins(dp(4), dp(10), dp(4), dp(6));
        tabs.addView(b, lp);
    }

    private void showHome() {
        currentTab = "home";
        clear();
        section("ملخص سريع");
        double total = singleDouble("SELECT COALESCE(SUM(total_price),0) FROM extinguishers");
        int count = (int) singleDouble("SELECT COALESCE(SUM(count),0) FROM extinguishers");
        int customers = (int) singleDouble("SELECT COUNT(*) FROM customers");
        int maintenance = (int) singleDouble("SELECT COUNT(*) FROM maintenance_contracts");
        card("إجمالي الطفايات", count + " طفاية");
        card("إجمالي مبلغ الطفايات", money(total));
        card("العملاء المسجلين", customers + " عميل");
        card("عقود الصيانة", maintenance + " عقد");
        small("ابدأ من أي تبويب بالأعلى. كل البيانات محفوظة محليا، ولو سجلت Google سيتم رفع نسخة تلقائيا.");
    }

    private void showSalaries() {
        currentTab = "salaries";
        clear();
        section("تسجيل شخص ومرتبه");
        EditText name = input("اسم الشخص", InputType.TYPE_CLASS_TEXT);
        EditText salary = input("المرتب", numberType());
        button("حفظ الشخص", () -> {
            if (empty(name) || empty(salary)) return;
            ContentValues cv = new ContentValues();
            cv.put("name", txt(name));
            cv.put("salary", dbl(salary));
            cv.put("created_at", System.currentTimeMillis());
            db.insert("employees", cv);
            afterSave("تم حفظ الشخص");
            showSalaries();
        });

        section("تسجيل سلفة داخل الشهر");
        EditText advName = input("اسم الشخص", InputType.TYPE_CLASS_TEXT);
        EditText amount = input("مبلغ السلفة", numberType());
        EditText note = input("ملاحظة اختيارية", InputType.TYPE_CLASS_TEXT);
        button("حفظ السلفة", () -> {
            if (empty(advName) || empty(amount)) return;
            ContentValues cv = new ContentValues();
            cv.put("employee_name", txt(advName));
            cv.put("amount", dbl(amount));
            cv.put("note", txt(note));
            cv.put("created_at", System.currentTimeMillis());
            db.insert("advances", cv);
            afterSave("تم حفظ السلفة");
            showSalaries();
        });

        section("الموظفين");
        Cursor e = db.all("employees");
        try {
            while (e.moveToNext()) {
                card(e.getString(e.getColumnIndexOrThrow("name")),
                        "المرتب: " + money(e.getDouble(e.getColumnIndexOrThrow("salary"))));
            }
        } finally {
            e.close();
        }

        section("السلف المسجلة");
        Cursor a = db.all("advances");
        try {
            while (a.moveToNext()) {
                card(a.getString(a.getColumnIndexOrThrow("employee_name")),
                        "سلفة: " + money(a.getDouble(a.getColumnIndexOrThrow("amount"))) +
                                "\n" + a.getString(a.getColumnIndexOrThrow("note")));
            }
        } finally {
            a.close();
        }
    }

    private void showExtinguishers() {
        currentTab = "extinguishers";
        clear();
        section("تسجيل طفايات لعميل");
        EditText customer = input("اسم العميل", InputType.TYPE_CLASS_TEXT);
        EditText phone = input("رقم العميل", InputType.TYPE_CLASS_PHONE);
        EditText location = input("اللوكيشن", InputType.TYPE_CLASS_TEXT);
        EditText type = input("نوع الطفاية", InputType.TYPE_CLASS_TEXT);
        EditText weight = input("وزن الطفاية", InputType.TYPE_CLASS_TEXT);
        EditText count = input("عدد الطفايات", InputType.TYPE_CLASS_NUMBER);
        EditText price = input("إجمالي مبلغ الطفايات", numberType());
        EditText date = input("تاريخ الاستيكر yyyy-MM-dd", InputType.TYPE_CLASS_DATETIME);
        date.setText(today());
        button("حفظ الطفايات وجدولة التذكير", () -> {
            if (empty(customer) || empty(count) || empty(price) || empty(date)) return;
            try {
                long stickerDate = ReminderScheduler.parseDate(txt(date));
                long reminder = ReminderScheduler.stickerReminder(stickerDate);

                ContentValues ccv = new ContentValues();
                ccv.put("name", txt(customer));
                ccv.put("phone", txt(phone));
                ccv.put("location", txt(location));
                ccv.put("created_at", System.currentTimeMillis());
                long customerId = db.insert("customers", ccv);

                ContentValues cv = new ContentValues();
                cv.put("customer_id", customerId);
                cv.put("customer_name", txt(customer));
                cv.put("phone", txt(phone));
                cv.put("location", txt(location));
                cv.put("extinguisher_type", txt(type));
                cv.put("weight", txt(weight));
                cv.put("count", integer(count));
                cv.put("total_price", dbl(price));
                cv.put("sticker_date", stickerDate);
                cv.put("reminder_at", reminder);
                cv.put("created_at", System.currentTimeMillis());
                db.insert("extinguishers", cv);
                afterSave("تم حفظ الطفايات والتنبيه يوم " + ReminderScheduler.formatDate(reminder));
                showExtinguishers();
            } catch (Exception ex) {
                toast("راجع التاريخ، لازم يكون بالشكل yyyy-MM-dd");
            }
        });

        section("آخر عمليات الطفايات");
        Cursor c = db.all("extinguishers");
        try {
            while (c.moveToNext()) {
                card(c.getString(c.getColumnIndexOrThrow("customer_name")),
                        "عدد: " + c.getInt(c.getColumnIndexOrThrow("count")) +
                                "\nنوع: " + val(c, "extinguisher_type") +
                                "\nوزن: " + val(c, "weight") +
                                "\nمبلغ: " + money(c.getDouble(c.getColumnIndexOrThrow("total_price"))) +
                                "\nالتذكير: " + ReminderScheduler.formatDate(c.getLong(c.getColumnIndexOrThrow("reminder_at"))) +
                                "\nرقم: " + val(c, "phone") + "\nلوكيشن: " + val(c, "location"));
            }
        } finally {
            c.close();
        }
    }

    private void showCustomers() {
        currentTab = "customers";
        clear();
        section("العملاء");
        Cursor c = db.raw("SELECT customer_name, phone, location, SUM(count) total_count, SUM(total_price) total_price " +
                "FROM extinguishers GROUP BY customer_name, phone, location ORDER BY MAX(created_at) DESC");
        try {
            while (c.moveToNext()) {
                card(c.getString(0),
                        "رقم: " + safe(c.getString(1)) +
                                "\nلوكيشن: " + safe(c.getString(2)) +
                                "\nإجمالي الطفايات: " + c.getInt(3) +
                                "\nإجمالي المبلغ: " + money(c.getDouble(4)));
            }
        } finally {
            c.close();
        }
    }

    private void showCertificates() {
        currentTab = "certificates";
        clear();
        section("شهادات السلامة");
        certificateForm("safety_certificates", "certificate_date", "حفظ شهادة السلامة");
        section("التقارير الفنية");
        certificateForm("technical_reports", "report_date", "حفظ تقرير فني");
        section("المسجل");
        listAnnual("safety_certificates", "certificate_date", "شهادة سلامة");
        listAnnual("technical_reports", "report_date", "تقرير فني");
    }

    private void certificateForm(String table, String dateColumn, String buttonText) {
        EditText customer = input("اسم العميل", InputType.TYPE_CLASS_TEXT);
        EditText phone = input("رقم العميل", InputType.TYPE_CLASS_PHONE);
        EditText location = input("اللوكيشن", InputType.TYPE_CLASS_TEXT);
        EditText date = input("تاريخ البداية yyyy-MM-dd", InputType.TYPE_CLASS_DATETIME);
        date.setText(today());
        button(buttonText, () -> {
            if (empty(customer) || empty(date)) return;
            try {
                long base = ReminderScheduler.parseDate(txt(date));
                long reminder = ReminderScheduler.annualReminder(base);
                ContentValues cv = new ContentValues();
                cv.put("customer_name", txt(customer));
                cv.put("phone", txt(phone));
                cv.put("location", txt(location));
                cv.put(dateColumn, base);
                cv.put("reminder_at", reminder);
                cv.put("created_at", System.currentTimeMillis());
                db.insert(table, cv);
                afterSave("تم الحفظ والتنبيه يوم " + ReminderScheduler.formatDate(reminder));
                showCertificates();
            } catch (Exception e) {
                toast("راجع التاريخ، لازم يكون بالشكل yyyy-MM-dd");
            }
        });
    }

    private void showMaintenance() {
        currentTab = "maintenance";
        clear();
        section("عقود الصيانة");
        EditText customer = input("اسم العميل", InputType.TYPE_CLASS_TEXT);
        EditText phone = input("رقم العميل", InputType.TYPE_CLASS_PHONE);
        EditText location = input("اللوكيشن", InputType.TYPE_CLASS_TEXT);
        EditText start = input("تاريخ بداية العقد yyyy-MM-dd", InputType.TYPE_CLASS_DATETIME);
        start.setText(today());
        button("حفظ عقد الصيانة", () -> {
            if (empty(customer) || empty(start)) return;
            try {
                long startDate = ReminderScheduler.parseDate(txt(start));
                long visit = ReminderScheduler.addMonthsAvoidWeekend(startDate, 3);
                long reminder = ReminderScheduler.maintenanceReminder(visit);
                ContentValues cv = new ContentValues();
                cv.put("customer_name", txt(customer));
                cv.put("phone", txt(phone));
                cv.put("location", txt(location));
                cv.put("start_date", startDate);
                cv.put("next_visit_at", visit);
                cv.put("reminder_at", reminder);
                cv.put("created_at", System.currentTimeMillis());
                db.insert("maintenance_contracts", cv);
                afterSave("تم الحفظ. الزيارة " + ReminderScheduler.formatDate(visit) +
                        " والتنبيه " + ReminderScheduler.formatDate(reminder));
                showMaintenance();
            } catch (Exception e) {
                toast("راجع التاريخ، لازم يكون بالشكل yyyy-MM-dd");
            }
        });

        section("العقود المسجلة");
        Cursor c = db.all("maintenance_contracts");
        try {
            while (c.moveToNext()) {
                card(c.getString(c.getColumnIndexOrThrow("customer_name")),
                        "الزيارة القادمة: " + ReminderScheduler.formatDate(c.getLong(c.getColumnIndexOrThrow("next_visit_at"))) +
                                "\nالتنبيه قبلها: " + ReminderScheduler.formatDate(c.getLong(c.getColumnIndexOrThrow("reminder_at"))) +
                                "\nرقم: " + val(c, "phone") + "\nلوكيشن: " + val(c, "location"));
            }
        } finally {
            c.close();
        }
    }

    private void showMonthlyReport() {
        currentTab = "report";
        clear();
        section("تقرير الشهر الحالي");
        long[] range = monthRange();
        Cursor c = db.raw("SELECT COALESCE(SUM(count),0), COALESCE(SUM(total_price),0) FROM extinguishers " +
                "WHERE created_at BETWEEN ? AND ?", String.valueOf(range[0]), String.valueOf(range[1]));
        try {
            if (c.moveToFirst()) {
                int count = c.getInt(0);
                double total = c.getDouble(1);
                card("عدد الطفايات هذا الشهر", count + " طفاية");
                card("إجمالي مبلغ الطفايات", money(total));
                card("نسبتك 25%", money(total * 0.25));
            }
        } finally {
            c.close();
        }

        section("سلف هذا الشهر");
        Cursor a = db.raw("SELECT employee_name, amount, note FROM advances WHERE created_at BETWEEN ? AND ? ORDER BY created_at DESC",
                String.valueOf(range[0]), String.valueOf(range[1]));
        try {
            while (a.moveToNext()) {
                card(a.getString(0), "سلفة: " + money(a.getDouble(1)) + "\n" + safe(a.getString(2)));
            }
        } finally {
            a.close();
        }
    }

    private void showSync() {
        currentTab = "sync";
        clear();
        section("Google Sync");
        FirebaseUser user = sync.user();
        if (user == null) {
            small("سجل بحساب Google علشان التطبيق يحفظ ويرجع بياناتك تلقائيا على Firebase.");
            button("تسجيل بحساب Google", () -> sync.signIn(this));
        } else {
            card("الحساب الحالي", safe(user.getEmail()));
            button("رفع نسخة الآن", () -> sync.upload(this::showSync));
            button("استرجاع من Google", () -> sync.restore(this::showSync));
            button("تسجيل خروج", () -> sync.signOut(this::showSync));
        }
        small("لو تسجيل Google رفض، أضف SHA-1 الذي يظهر في GitHub Actions داخل Firebase ثم أعد البناء.");
    }

    private void listAnnual(String table, String dateColumn, String label) {
        Cursor c = db.all(table);
        try {
            while (c.moveToNext()) {
                card(label + " - " + c.getString(c.getColumnIndexOrThrow("customer_name")),
                        "التاريخ: " + ReminderScheduler.formatDate(c.getLong(c.getColumnIndexOrThrow(dateColumn))) +
                                "\nالتذكير: " + ReminderScheduler.formatDate(c.getLong(c.getColumnIndexOrThrow("reminder_at"))) +
                                "\nرقم: " + val(c, "phone") + "\nلوكيشن: " + val(c, "location"));
            }
        } finally {
            c.close();
        }
    }

    private void afterSave(String message) {
        ReminderScheduler.scheduleAll(this, db);
        sync.autoUploadQuietly();
        toast(message);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
    }

    private void clear() {
        content.removeAllViews();
    }

    private void section(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(19);
        tv.setTypeface(null, 1);
        tv.setTextColor(TEXT);
        tv.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(12), 0, dp(6));
        content.addView(tv, lp);
    }

    private void card(String title, String body) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.setBackgroundColor(Color.WHITE);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(TEXT);
        t.setTextSize(16);
        t.setTypeface(null, 1);
        t.setGravity(Gravity.RIGHT);
        TextView b = new TextView(this);
        b.setText(body == null ? "" : body);
        b.setTextColor(Color.rgb(51, 65, 85));
        b.setTextSize(14);
        b.setGravity(Gravity.RIGHT);
        box.addView(t, matchWrap());
        box.addView(b, matchWrap());
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(4), 0, dp(8));
        content.addView(box, lp);
    }

    private void small(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.rgb(71, 85, 105));
        tv.setGravity(Gravity.RIGHT);
        tv.setTextSize(14);
        content.addView(tv, matchWrap());
    }

    private EditText input(String hint, int inputType) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextColor(TEXT);
        et.setHintTextColor(Color.rgb(100, 116, 139));
        et.setInputType(inputType);
        et.setGravity(Gravity.RIGHT);
        et.setSingleLine(false);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(3), 0, dp(3));
        content.addView(et, lp);
        return et;
    }

    private void button(String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(BRAND);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(8), 0, dp(8));
        content.addView(b, lp);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int numberType() {
        return InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
    }

    private boolean empty(EditText et) {
        if (txt(et).isEmpty()) {
            et.setError("مطلوب");
            return true;
        }
        return false;
    }

    private String txt(EditText et) {
        return et.getText().toString().trim();
    }

    private double dbl(EditText et) {
        try {
            return Double.parseDouble(txt(et));
        } catch (Exception e) {
            return 0;
        }
    }

    private int integer(EditText et) {
        try {
            return Integer.parseInt(txt(et));
        } catch (Exception e) {
            return 0;
        }
    }

    private String val(Cursor c, String column) {
        String value = c.getString(c.getColumnIndexOrThrow(column));
        return safe(value);
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    private String money(double value) {
        return String.format(Locale.US, "%.2f", value) + " جنيه";
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private String today() {
        Calendar cal = Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d-%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
    }

    private long[] monthRange() {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.DAY_OF_MONTH, 1);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.MONTH, 1);
        end.add(Calendar.MILLISECOND, -1);
        return new long[]{start.getTimeInMillis(), end.getTimeInMillis()};
    }

    private double singleDouble(String sql) {
        Cursor c = db.raw(sql);
        try {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        } finally {
            c.close();
        }
    }
}
