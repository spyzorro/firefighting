package com.safetyoffice.firemanager;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int VOICE_REQUEST = 5042;
    private static final int RECORD_AUDIO_REQUEST = 5043;
    private static final int LOCATION_REQUEST = 5044;
    private static final int BRAND = Color.rgb(220, 38, 38);
    private static final int BRAND_DARK = Color.rgb(127, 29, 29);
    private static final int BRAND_LIGHT = Color.rgb(254, 226, 226);
    private static final int ACCENT = Color.rgb(245, 158, 11);
    private static final int BG = Color.rgb(255, 247, 237);
    private static final int CARD = Color.rgb(255, 255, 255);
    private static final int BORDER = Color.rgb(203, 213, 225);
    private static final int TEXT = Color.rgb(15, 23, 42);

    private DatabaseHelper db;
    private SyncManager sync;
    private LinearLayout content;
    private EditText voiceTarget;
    private EditText[] voiceGroup;
    private EditText locationTarget;
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
        } else if (requestCode == VOICE_REQUEST && resultCode == RESULT_OK && data != null) {
            ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) {
                String spoken = matches.get(0);
                if (voiceGroup != null && voiceGroup.length > 0) {
                    fillVoiceGroup(spoken, voiceGroup);
                } else if (voiceTarget != null) {
                    voiceTarget.setText(cleanVoiceText(spoken, voiceTarget.getInputType()));
                    voiceTarget.setSelection(voiceTarget.getText().length());
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RECORD_AUDIO_REQUEST && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (voiceGroup != null && voiceGroup.length > 0) startVoiceGroupInput(voiceGroup);
            else if (voiceTarget != null) startVoiceInput(voiceTarget);
        } else if (requestCode == LOCATION_REQUEST && locationTarget != null) {
            boolean granted = false;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) granted = true;
            }
            if (granted) fillCurrentLocation(locationTarget);
            else toast("لازم تسمح للتطبيق باستخدام الموقع علشان زر موقعي يشتغل");
        }
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(14), dp(14), dp(14), dp(10));

        TextView title = new TextView(this);
        title.setText("إدارة السلامة والطفايات");
        title.setTextSize(25);
        title.setTextColor(BRAND_DARK);
        title.setGravity(Gravity.RIGHT);
        title.setTypeface(null, 1);
        root.addView(title, matchWrap());

        TextView sub = new TextView(this);
        sub.setText("عملاء، طفايات، واتساب، تنبيهات، ومزامنة Google");
        sub.setTextColor(Color.rgb(120, 53, 15));
        sub.setGravity(Gravity.RIGHT);
        sub.setTextSize(14);
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
        addTab(tabs, "الإعدادات", v -> showSettings());
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
        b.setTextSize(14);
        b.setBackground(rounded(BRAND, BRAND_DARK, dp(20)));
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
        card("لوحة المتابعة",
                "كل شغلك في مكان واحد: تسجيل سريع بالصوت، فتح خرائط، رسالة واتساب جاهزة للعميل، ونسب شهرية قابلة للتعديل.");
        card("إجمالي الطفايات", count + " طفاية");
        card("إجمالي مبلغ الطفايات", money(total));
        card("العملاء المسجلين", customers + " عميل");
        card("عقود الصيانة", maintenance + " عقد");
        small("ابدأ من أي تبويب بالأعلى. اضغط صوت للإدخال بالكلام، أو موقعي بجانب اللوكيشن لحفظ رابط Google Maps الحالي. كل البيانات محفوظة محليا، ولو سجلت Google سيتم رفع نسخة تلقائيا.");
    }

    private void showSalaries() {
        currentTab = "salaries";
        clear();
        section("تسجيل شخص ومرتبه");
        EditText name = input("اسم الشخص", InputType.TYPE_CLASS_TEXT);
        EditText salary = input("المرتب", numberType());
        voiceAllButton("قول بيانات الشخص مرة واحدة", name, salary);
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
        voiceAllButton("قول بيانات السلفة مرة واحدة", advName, amount, note);
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
        voiceAllButton("تسجيل سريع بالصوت: قول بيانات الطفايات مرة واحدة", customer, location, type, weight, count, price, date);
        small("مثال: محمد 20 طفاية بودرة co2 120 ريال. رقم الموبايل اكتبه عادي، واللوكيشن اضغط موقعي.");
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
                long id = c.getLong(c.getColumnIndexOrThrow("id"));
                String oldCustomer = c.getString(c.getColumnIndexOrThrow("customer_name"));
                card(oldCustomer,
                        "عدد: " + c.getInt(c.getColumnIndexOrThrow("count")) +
                                "\nنوع: " + val(c, "extinguisher_type") +
                                "\nوزن: " + val(c, "weight") +
                                "\nمبلغ: " + money(c.getDouble(c.getColumnIndexOrThrow("total_price"))) +
                                "\nالتذكير: " + ReminderScheduler.formatDate(c.getLong(c.getColumnIndexOrThrow("reminder_at"))) +
                                "\nرقم: " + val(c, "phone") + "\nلوكيشن: " + val(c, "location"));
                EditText editCustomer = input("تعديل اسم العميل", InputType.TYPE_CLASS_TEXT);
                editCustomer.setText(oldCustomer);
                EditText editPhone = input("تعديل رقم العميل", InputType.TYPE_CLASS_PHONE);
                editPhone.setText(val(c, "phone"));
                EditText editLocation = input("تعديل اللوكيشن", InputType.TYPE_CLASS_TEXT);
                editLocation.setText(val(c, "location"));
                EditText editType = input("تعديل نوع الطفاية", InputType.TYPE_CLASS_TEXT);
                editType.setText(val(c, "extinguisher_type"));
                EditText editWeight = input("تعديل وزن الطفاية", InputType.TYPE_CLASS_TEXT);
                editWeight.setText(val(c, "weight"));
                EditText editCount = input("تعديل عدد الطفايات", InputType.TYPE_CLASS_NUMBER);
                editCount.setText(String.valueOf(c.getInt(c.getColumnIndexOrThrow("count"))));
                EditText editPrice = input("تعديل إجمالي مبلغ الطفايات", numberType());
                editPrice.setText(cleanNumber(c.getDouble(c.getColumnIndexOrThrow("total_price"))));
                button("حفظ تعديل عملية الطفايات", () -> {
                    if (empty(editCustomer) || empty(editCount) || empty(editPrice)) return;
                    ContentValues cv = new ContentValues();
                    cv.put("customer_name", txt(editCustomer));
                    cv.put("phone", txt(editPhone));
                    cv.put("location", txt(editLocation));
                    cv.put("extinguisher_type", txt(editType));
                    cv.put("weight", txt(editWeight));
                    cv.put("count", integer(editCount));
                    cv.put("total_price", dbl(editPrice));
                    db.update("extinguishers", cv, "id=?", String.valueOf(id));
                    afterSave("تم تعديل عملية الطفايات");
                    showExtinguishers();
                });
            }
        } finally {
            c.close();
        }
    }

    private void showCustomers() {
        currentTab = "customers";
        clear();
        section("العملاء");
        Cursor c = db.raw("SELECT customer_name, phone, location, SUM(total_count) total_count, SUM(total_price) total_price " +
                "FROM (" +
                "SELECT customer_name, phone, location, SUM(count) total_count, SUM(total_price) total_price, MAX(created_at) last_at FROM extinguishers GROUP BY customer_name, phone, location " +
                "UNION ALL SELECT customer_name, phone, location, 0 total_count, SUM(total_price) total_price, MAX(created_at) last_at FROM safety_certificates GROUP BY customer_name, phone, location " +
                "UNION ALL SELECT customer_name, phone, location, 0 total_count, SUM(total_price) total_price, MAX(created_at) last_at FROM technical_reports GROUP BY customer_name, phone, location " +
                "UNION ALL SELECT customer_name, phone, location, 0 total_count, 0 total_price, MAX(created_at) last_at FROM maintenance_contracts GROUP BY customer_name, phone, location" +
                ") GROUP BY customer_name, phone, location ORDER BY MAX(last_at) DESC");
        try {
            while (c.moveToNext()) {
                String oldName = c.getString(0);
                String oldPhone = emptyForDb(c.getString(1));
                String oldLocation = emptyForDb(c.getString(2));
                card(oldName,
                        "رقم: " + safe(oldPhone) +
                                "\nلوكيشن: " + safe(oldLocation) +
                                "\nإجمالي الطفايات: " + c.getInt(3) +
                                "\nإجمالي المبلغ: " + money(c.getDouble(4)));
                secondaryButton("رسالة واتساب للعميل", () -> sendWhatsApp(oldPhone, oldName));
                EditText name = input("تعديل اسم العميل", InputType.TYPE_CLASS_TEXT);
                name.setText(oldName);
                EditText phone = input("تعديل رقم العميل", InputType.TYPE_CLASS_PHONE);
                phone.setText(oldPhone);
                EditText location = input("تعديل اللوكيشن", InputType.TYPE_CLASS_TEXT);
                location.setText(oldLocation);
                button("حفظ تعديل العميل", () -> {
                    if (empty(name)) return;
                    db.updateCustomerEverywhere(oldName, oldPhone, oldLocation, txt(name), txt(phone), txt(location));
                    afterSave("تم تعديل بيانات العميل في كل السجلات");
                    showCustomers();
                });
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
        EditText amount = input("مبلغ/سعر البند", numberType());
        EditText date = input("تاريخ البداية yyyy-MM-dd", InputType.TYPE_CLASS_DATETIME);
        date.setText(today());
        voiceAllButton("قول كل البيانات مرة واحدة", customer, location, amount, date);
        button(buttonText, () -> {
            if (empty(customer) || empty(date)) return;
            try {
                long base = ReminderScheduler.parseDate(txt(date));
                long reminder = ReminderScheduler.annualReminder(base);
                ContentValues cv = new ContentValues();
                cv.put("customer_name", txt(customer));
                cv.put("phone", txt(phone));
                cv.put("location", txt(location));
                cv.put("total_price", dbl(amount));
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
        voiceAllButton("قول بيانات عقد الصيانة مرة واحدة", customer, location, start);
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
                double pct = db.settingDouble("extinguisher_percent", 25);
                card("عدد الطفايات هذا الشهر", count + " طفاية");
                card("إجمالي مبلغ الطفايات", money(total));
                card("نسبتك في الطفايات " + cleanNumber(pct) + "%", money(total * pct / 100.0));
            }
        } finally {
            c.close();
        }

        monthlyAmountCard("شهادات السلامة", "safety_certificates", "certificate_percent", range);
        monthlyAmountCard("التقارير الفنية", "technical_reports", "report_percent", range);

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

    private void monthlyAmountCard(String label, String table, String settingKey, long[] range) {
        Cursor c = db.raw("SELECT COALESCE(SUM(total_price),0) FROM " + table +
                        " WHERE created_at BETWEEN ? AND ?",
                String.valueOf(range[0]), String.valueOf(range[1]));
        try {
            if (c.moveToFirst()) {
                double total = c.getDouble(0);
                double pct = db.settingDouble(settingKey, 0);
                card(label, "الإجمالي: " + money(total) +
                        "\nنسبتك " + cleanNumber(pct) + "%: " + money(total * pct / 100.0));
            }
        } finally {
            c.close();
        }
    }

    private void showSettings() {
        currentTab = "settings";
        clear();
        section("إعدادات النسب");
        EditText extinguisher = input("نسبة الطفايات %", numberType());
        extinguisher.setText(db.setting("extinguisher_percent", "25"));
        EditText certificates = input("نسبة شهادات السلامة %", numberType());
        certificates.setText(db.setting("certificate_percent", "0"));
        EditText reports = input("نسبة التقارير الفنية %", numberType());
        reports.setText(db.setting("report_percent", "0"));
        button("حفظ النسب", () -> {
            db.setSetting("extinguisher_percent", txt(extinguisher).isEmpty() ? "0" : txt(extinguisher));
            db.setSetting("certificate_percent", txt(certificates).isEmpty() ? "0" : txt(certificates));
            db.setSetting("report_percent", txt(reports).isEmpty() ? "0" : txt(reports));
            afterSave("تم حفظ النسب");
            showSettings();
        });
        small("كل نسبة تتحسب في تقرير الشهر على إجمالي مبلغ البند الخاص بها.");

        section("رسائل واتساب");
        String[] templates = whatsappTemplates();
        int selected = selectedWhatsappTemplateIndex(templates.length);
        for (int i = 0; i < templates.length; i++) {
            card((i + 1) + (i == selected ? " - الرسالة المختارة" : " - رسالة واتساب"),
                    templates[i].replace("{name}", "اسم العميل"));
            final int index = i;
            secondaryButton("اختيار الرسالة " + (i + 1), () -> {
                db.setSetting("selected_whatsapp_template", String.valueOf(index));
                afterSave("تم اختيار رسالة واتساب");
                showSettings();
            });
        }
        EditText newTemplate = input("إضافة رسالة واتساب جديدة", InputType.TYPE_CLASS_TEXT);
        newTemplate.setSingleLine(false);
        newTemplate.setMinLines(3);
        small("استخدم {name} داخل الرسالة لو عايز اسم العميل يظهر تلقائيا.");
        button("إضافة الرسالة", () -> {
            if (empty(newTemplate)) return;
            db.setSetting("whatsapp_templates", joinTemplates(appendTemplate(templates, txt(newTemplate))));
            db.setSetting("selected_whatsapp_template", String.valueOf(templates.length));
            afterSave("تمت إضافة الرسالة واختيارها");
            showSettings();
        });
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
                                "\nالمبلغ: " + money(c.getDouble(c.getColumnIndexOrThrow("total_price"))) +
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
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        box.setBackground(rounded(CARD, Color.rgb(254, 202, 202), dp(14)));
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
        String mapLink = extractMapLink(body);
        if (mapLink != null) {
            Button open = new Button(this);
            open.setText("فتح اللوكيشن");
            open.setTextColor(BRAND_DARK);
            open.setTextSize(13);
            open.setBackground(rounded(BRAND_LIGHT, BRAND_LIGHT, dp(14)));
            open.setOnClickListener(v -> openLocation(mapLink));
            LinearLayout.LayoutParams openLp = matchWrap();
            openLp.setMargins(0, dp(6), 0, 0);
            box.addView(open, openLp);
        }
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(5), 0, dp(9));
        content.addView(box, lp);
    }

    private String extractMapLink(String text) {
        if (text == null) return null;
        Matcher matcher = Pattern.compile("(https?://\\S+|geo:\\S+)").matcher(text);
        while (matcher.find()) {
            String cleaned = matcher.group(1).trim();
            while (cleaned.endsWith(".") || cleaned.endsWith("،") || cleaned.endsWith(";")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }
            if (cleaned.startsWith("https://www.google.com/maps") ||
                    cleaned.startsWith("http://www.google.com/maps") ||
                    cleaned.startsWith("https://maps.google.com") ||
                    cleaned.startsWith("https://maps.app.goo.gl") ||
                    cleaned.startsWith("geo:")) return cleaned;
        }
        Matcher coordinates = Pattern.compile("(-?\\d{1,3}\\.\\d+\\s*,\\s*-?\\d{1,3}\\.\\d+)").matcher(text);
        if (coordinates.find()) {
            String value = coordinates.group(1).replace(" ", "");
            return "geo:" + value + "?q=" + Uri.encode(value);
        }
        return null;
    }

    private void openLocation(String link) {
        try {
            Uri uri = normalizeMapUri(link);
            Intent maps = new Intent(Intent.ACTION_VIEW, uri);
            maps.setPackage("com.google.android.apps.maps");
            try {
                startActivity(maps);
            } catch (ActivityNotFoundException e) {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            }
        } catch (Exception e) {
            toast("تعذر فتح اللوكيشن");
        }
    }

    private Uri normalizeMapUri(String link) {
        if (link.startsWith("geo:")) return Uri.parse(link);
        Uri uri = Uri.parse(link);
        String value = uri.getQueryParameter("query");
        if (value != null && value.matches("-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?")) {
            return Uri.parse("geo:" + value + "?q=" + Uri.encode(value));
        }
        return uri;
    }

    private void sendWhatsApp(String phone, String customerName) {
        String normalizedPhone = normalizePhone(phone);
        if (normalizedPhone.isEmpty()) {
            toast("رقم العميل غير مسجل");
            return;
        }
        String[] templates = whatsappTemplates();
        int selected = selectedWhatsappTemplateIndex(templates.length);
        String message = templates[selected].replace("{name}", safe(customerName));
        Uri uri = Uri.parse("https://wa.me/" + normalizedPhone + "?text=" + Uri.encode(message));
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            toast("تعذر فتح واتساب");
        }
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String digits = normalizeDigits(phone).replaceAll("[^0-9]", "");
        while (digits.startsWith("00")) digits = digits.substring(2);
        if (digits.length() == 10 && digits.startsWith("05")) return "966" + digits.substring(1);
        if (digits.length() == 9 && digits.startsWith("5")) return "966" + digits;
        return digits;
    }

    private String[] whatsappTemplates() {
        String raw = db.setting("whatsapp_templates", defaultWhatsappTemplates());
        String[] parts = raw.split("\\|\\|\\|");
        ArrayList<String> clean = new ArrayList<>();
        for (String part : parts) {
            String value = part.trim();
            if (!value.isEmpty()) clean.add(value);
        }
        if (clean.isEmpty()) clean.add(defaultWhatsappTemplates().split("\\|\\|\\|")[0]);
        return clean.toArray(new String[0]);
    }

    private int selectedWhatsappTemplateIndex(int size) {
        int selected = (int) Math.round(parseNumber(db.setting("selected_whatsapp_template", "0")));
        if (selected < 0 || selected >= size) return 0;
        return selected;
    }

    private String[] appendTemplate(String[] templates, String value) {
        String[] next = new String[templates.length + 1];
        System.arraycopy(templates, 0, next, 0, templates.length);
        next[templates.length] = value;
        return next;
    }

    private String joinTemplates(String[] templates) {
        StringBuilder out = new StringBuilder();
        for (String template : templates) {
            if (out.length() > 0) out.append("|||");
            out.append(template.replace("|||", " "));
        }
        return out.toString();
    }

    private String defaultWhatsappTemplates() {
        return "تحياتنا لك {name}\nنذكركم بأن موعد انتهاء شهادة/استيكر الطفايات قرب، ونحتاج نحدد معكم موعد زيارة مناسب لصيانة الطفايات والتأكد من جاهزيتها.\nيعطيكم العافية.|||" +
                "تحياتنا {name}\nحبيت أذكركم بقرب موعد صيانة الطفايات وتجديد المتابعة، فضلا زودونا بالوقت المناسب للزيارة.\nشاكرين تعاونكم.|||" +
                "تحياتنا لكم\nموعد متابعة طفايات الحريق قرب، ونحتاج ترتيب زيارة صيانة في أقرب وقت يناسبكم.\nمع خالص الشكر.";
    }

    private void small(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.rgb(71, 85, 105));
        tv.setGravity(Gravity.RIGHT);
        tv.setTextSize(14);
        content.addView(tv, matchWrap());
    }

    private void secondaryButton(String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(BRAND_DARK);
        b.setTextSize(14);
        b.setBackground(rounded(BRAND_LIGHT, BRAND_LIGHT, dp(14)));
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(3), 0, dp(8));
        content.addView(b, lp);
    }

    private EditText input(String hint, int inputType) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(4), dp(8), dp(4));
        row.setBackground(rounded(Color.WHITE, BORDER, dp(14)));

        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextColor(TEXT);
        et.setHintTextColor(Color.rgb(100, 116, 139));
        et.setInputType(inputType);
        et.setGravity(Gravity.RIGHT);
        et.setSingleLine(true);
        et.setMinHeight(dp(48));
        et.setBackgroundColor(Color.TRANSPARENT);
        row.addView(et, new LinearLayout.LayoutParams(0, -2, 1));

        if (hint.contains("لوكيشن")) {
            Button current = new Button(this);
            current.setText("موقعي");
            current.setTextSize(12);
            current.setTextColor(BRAND);
            current.setBackground(rounded(BRAND_LIGHT, BRAND_LIGHT, dp(18)));
            current.setOnClickListener(v -> fillCurrentLocation(et));
            LinearLayout.LayoutParams locLp = new LinearLayout.LayoutParams(dp(78), dp(42));
            locLp.setMargins(dp(6), 0, 0, 0);
            row.addView(current, locLp);
        }

        int inputClass = inputType & InputType.TYPE_MASK_CLASS;
        if (inputClass != InputType.TYPE_CLASS_PHONE) {
            Button mic = new Button(this);
            mic.setText("صوت");
            mic.setTextSize(12);
            mic.setTextColor(BRAND);
            mic.setBackground(rounded(BRAND_LIGHT, BRAND_LIGHT, dp(18)));
            mic.setOnClickListener(v -> startVoiceInput(et));
            LinearLayout.LayoutParams micLp = new LinearLayout.LayoutParams(dp(72), dp(42));
            micLp.setMargins(dp(6), 0, 0, 0);
            row.addView(mic, micLp);
        }

        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(5), 0, dp(6));
        content.addView(row, lp);
        return et;
    }

    private void button(String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setBackground(rounded(BRAND, BRAND, dp(14)));
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(8), 0, dp(8));
        content.addView(b, lp);
    }

    private void voiceAllButton(String text, EditText... fields) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(BRAND);
        b.setTextSize(14);
        b.setBackground(rounded(BRAND_LIGHT, BRAND_LIGHT, dp(14)));
        b.setOnClickListener(v -> startVoiceGroupInput(fields));
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(3), 0, dp(8));
        content.addView(b, lp);
    }

    private void fillCurrentLocation(EditText target) {
        locationTarget = target;
        if (!hasLocationPermission()) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, LOCATION_REQUEST);
            return;
        }

        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager == null) {
            toast("خدمة الموقع غير متاحة على الجهاز");
            return;
        }

        Location best = bestLastKnownLocation(manager);
        if (best != null) {
            setLocationLink(target, best);
        }

        String provider = manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                ? LocationManager.GPS_PROVIDER
                : (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER : null);
        if (provider == null) {
            toast("افتح GPS/Location من إعدادات الموبايل ثم اضغط موقعي");
            return;
        }

        try {
            manager.requestSingleUpdate(provider, new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    setLocationLink(target, location);
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                }

                @Override
                public void onProviderEnabled(String provider) {
                }

                @Override
                public void onProviderDisabled(String provider) {
                }
            }, null);
            toast(best == null ? "جاري تحديد الموقع..." : "تم وضع أقرب موقع، وجاري تحسين الدقة");
        } catch (SecurityException e) {
            toast("اسمح للتطبيق باستخدام الموقع");
        } catch (Exception e) {
            if (best == null) toast("تعذر تحديد الموقع الحالي");
        }
    }

    private Location bestLastKnownLocation(LocationManager manager) {
        Location best = null;
        try {
            for (String provider : manager.getProviders(true)) {
                Location location = manager.getLastKnownLocation(provider);
                if (location == null) continue;
                if (best == null || location.getAccuracy() < best.getAccuracy()) best = location;
            }
        } catch (SecurityException ignored) {
        }
        return best;
    }

    private boolean hasLocationPermission() {
        return Build.VERSION.SDK_INT < 23 ||
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void setLocationLink(EditText target, Location location) {
        String link = String.format(Locale.US,
                "https://www.google.com/maps/search/?api=1&query=%.7f,%.7f",
                location.getLatitude(), location.getLongitude());
        target.setText(link);
        target.setSelection(target.getText().length());
        toast("تم حفظ لينك اللوكيشن الحالي");
    }

    private GradientDrawable rounded(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), stroke);
        return drawable;
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
        return parseNumber(txt(et));
    }

    private int integer(EditText et) {
        return (int) Math.round(parseNumber(txt(et)));
    }

    private void startVoiceInput(EditText target) {
        voiceTarget = target;
        voiceGroup = null;
        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_REQUEST);
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "اتكلم دلوقتي");
        try {
            startActivityForResult(intent, VOICE_REQUEST);
        } catch (ActivityNotFoundException e) {
            toast("خاصية الإدخال الصوتي غير متاحة على هذا الجهاز");
        }
    }

    private void startVoiceGroupInput(EditText... targets) {
        voiceTarget = null;
        voiceGroup = targets;
        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_REQUEST);
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "قول كل البيانات مرة واحدة");
        try {
            startActivityForResult(intent, VOICE_REQUEST);
        } catch (ActivityNotFoundException e) {
            toast("خاصية الإدخال الصوتي غير متاحة على هذا الجهاز");
        }
    }

    private void fillVoiceGroup(String spoken, EditText[] fields) {
        String normalized = normalizeDigits(spoken);
        int assigned = 0;
        for (EditText field : fields) {
            String hint = String.valueOf(field.getHint());
            String value = extractFieldValue(normalized, hint);
            if (value == null || value.trim().isEmpty()) continue;
            field.setText(cleanVoiceText(value, field.getInputType()));
            field.setSelection(field.getText().length());
            assigned++;
        }
        if (assigned == 0 && smartExtinguisherFill(normalized, fields)) {
            assigned = fields.length;
        }
        if (assigned == 0) {
            String[] parts = normalized.split("[،,؛\\n]+");
            int max = Math.min(parts.length, fields.length);
            for (int i = 0; i < max; i++) {
                String value = trimSeparators(parts[i]);
                if (value.isEmpty()) continue;
                fields[i].setText(cleanVoiceText(value, fields[i].getInputType()));
                fields[i].setSelection(fields[i].getText().length());
            }
        }
        toast("تم توزيع البيانات الصوتية على الخانات");
    }

    private boolean smartExtinguisherFill(String text, EditText[] fields) {
        EditText customer = fieldByHint(fields, "اسم العميل");
        EditText type = fieldByHint(fields, "نوع الطفاية");
        EditText weight = fieldByHint(fields, "وزن الطفاية");
        EditText count = fieldByHint(fields, "عدد الطفايات");
        EditText price = fieldByHint(fields, "إجمالي مبلغ الطفايات");
        if (customer == null || type == null || count == null || price == null) return false;

        String normalized = normalizeDigits(text);
        NumberMention countMention = findNumberBeforeUnit(normalized, new String[]{"طفايه", "طفاية", "طفايات"});
        NumberMention priceMention = findNumberBeforeUnit(normalized, new String[]{"ريال", "ريالات", "جنيه", "جنيهات"});
        if (priceMention == null) {
            priceMention = findLastNumberMention(normalized, countMention == null ? 0 : countMention.end);
        }

        boolean filled = false;
        if (countMention != null) {
            count.setText(cleanNumber(countMention.value));
            filled = true;
        }
        if (priceMention != null) {
            price.setText(cleanNumber(priceMention.value));
            filled = true;
        }

        int firstNumberStart = countMention != null ? countMention.start : (priceMention != null ? priceMention.start : -1);
        if (firstNumberStart > 0) {
            String name = trimSeparators(normalized.substring(0, firstNumberStart));
            if (!name.isEmpty()) {
                customer.setText(name);
                filled = true;
            }
        }

        int typeStart = countMention == null ? 0 : endAfterAnyUnit(normalized, countMention.end,
                new String[]{"طفايه", "طفاية", "طفايات"});
        int typeEnd = priceMention == null ? normalized.length() : priceMention.start;
        if (typeEnd > typeStart) {
            String typeText = trimSeparators(normalized.substring(typeStart, typeEnd));
            NumberMention weightMention = findNumberBeforeUnit(typeText, new String[]{"كيلو", "كجم", "kg"});
            if (weightMention != null && weight != null) {
                int unitEnd = endAfterAnyUnit(typeText, weightMention.end, new String[]{"كيلو", "كجم", "kg"});
                String weightText = trimSeparators(typeText.substring(weightMention.start, unitEnd));
                weight.setText(weightText);
                typeText = trimSeparators(typeText.substring(0, weightMention.start) + " " + typeText.substring(unitEnd));
            }
            typeText = typeText
                    .replace("طفايه", "")
                    .replace("طفاية", "")
                    .replace("طفايات", "")
                    .replace("ريال", "")
                    .replace("ريالات", "")
                    .trim();
            if (!typeText.isEmpty()) {
                type.setText(typeText);
                filled = true;
            }
        }

        for (EditText field : fields) field.setSelection(field.getText().length());
        return filled;
    }

    private EditText fieldByHint(EditText[] fields, String hintPart) {
        for (EditText field : fields) {
            if (String.valueOf(field.getHint()).contains(hintPart)) return field;
        }
        return null;
    }

    private String cleanNumber(double value) {
        if (Math.rint(value) == value) return String.valueOf((long) value);
        return String.format(Locale.US, "%.2f", value);
    }

    private int endAfterAnyUnit(String text, int from, String[] units) {
        int best = -1;
        int bestLength = 0;
        for (String unit : units) {
            int idx = text.indexOf(unit, from);
            if (idx >= 0 && (best < 0 || idx < best)) {
                best = idx;
                bestLength = unit.length();
            }
        }
        return best < 0 ? from : best + bestLength;
    }

    private NumberMention findNumberBeforeUnit(String text, String[] units) {
        ArrayList<TokenPos> tokens = tokenPositions(text);
        for (int i = 0; i < tokens.size(); i++) {
            if (!isAnyUnit(tokens.get(i).clean, units)) continue;
            int startToken = i - 1;
            while (startToken >= 0 && isNumberToken(tokens.get(startToken).clean)) startToken--;
            startToken++;
            if (startToken < i) {
                String phrase = text.substring(tokens.get(startToken).start, tokens.get(i - 1).end);
                double value = parseNumber(phrase);
                if (value > 0) return new NumberMention(value, tokens.get(startToken).start, tokens.get(i - 1).end);
            }
        }
        return null;
    }

    private NumberMention findLastNumberMention(String text, int from) {
        ArrayList<TokenPos> tokens = tokenPositions(text);
        NumberMention last = null;
        int i = 0;
        while (i < tokens.size()) {
            if (tokens.get(i).end < from || !isNumberToken(tokens.get(i).clean)) {
                i++;
                continue;
            }
            int start = i;
            while (i < tokens.size() && isNumberToken(tokens.get(i).clean)) i++;
            String phrase = text.substring(tokens.get(start).start, tokens.get(i - 1).end);
            double value = parseNumber(phrase);
            if (value > 0) last = new NumberMention(value, tokens.get(start).start, tokens.get(i - 1).end);
        }
        return last;
    }

    private ArrayList<TokenPos> tokenPositions(String text) {
        ArrayList<TokenPos> result = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\S+").matcher(text);
        while (matcher.find()) {
            String raw = matcher.group();
            String clean = trimSeparators(raw).toLowerCase(Locale.US);
            result.add(new TokenPos(clean, matcher.start(), matcher.end()));
        }
        return result;
    }

    private boolean isAnyUnit(String token, String[] units) {
        for (String unit : units) {
            if (token.equals(unit)) return true;
        }
        return false;
    }

    private boolean isNumberToken(String token) {
        if (token == null || token.isEmpty()) return false;
        String clean = trimSeparators(token);
        if (clean.startsWith("و") && clean.length() > 1) clean = clean.substring(1);
        return clean.matches("[0-9]+(\\.[0-9]+)?") ||
                smallArabicNumber(clean) >= 0 ||
                isHundred(clean) ||
                isThousand(clean) ||
                isMillion(clean);
    }

    private static class NumberMention {
        final double value;
        final int start;
        final int end;

        NumberMention(double value, int start, int end) {
            this.value = value;
            this.start = start;
            this.end = end;
        }
    }

    private static class TokenPos {
        final String clean;
        final int start;
        final int end;

        TokenPos(String clean, int start, int end) {
            this.clean = clean;
            this.start = start;
            this.end = end;
        }
    }

    private String extractFieldValue(String text, String hint) {
        String[] keys = keysForHint(hint);
        int bestStart = -1;
        int bestKeyEnd = -1;
        for (String key : keys) {
            int idx = indexOfKey(text, key);
            if (idx >= 0 && (bestStart < 0 || idx < bestStart)) {
                bestStart = idx;
                bestKeyEnd = idx + key.length();
            }
        }
        if (bestStart < 0) return null;

        int valueStart = bestKeyEnd;
        while (valueStart < text.length() && isSeparator(text.charAt(valueStart))) valueStart++;
        int valueEnd = text.length();
        for (String stop : allVoiceKeys()) {
            int stopIndex = indexOfKey(text, stop, valueStart + 1);
            if (stopIndex >= 0 && stopIndex < valueEnd) valueEnd = stopIndex;
        }
        String value = text.substring(valueStart, valueEnd)
                .replace("يساوي", "")
                .replace("هو", "")
                .replace("هي", "")
                .replace("بكام", "")
                .trim();
        return trimSeparators(value);
    }

    private int indexOfKey(String text, String key) {
        return indexOfKey(text, key, 0);
    }

    private int indexOfKey(String text, String key, int start) {
        int idx = text.indexOf(key + ":", start);
        if (idx >= 0) return idx;
        idx = text.indexOf(key + " ", start);
        if (idx >= 0) return idx;
        idx = text.indexOf(" " + key + " ", start);
        return idx >= 0 ? idx + 1 : -1;
    }

    private String[] keysForHint(String hint) {
        if (hint.contains("اسم العميل")) return new String[]{"اسم العميل", "العميل", "عميل", "اسم"};
        if (hint.contains("اسم الشخص")) return new String[]{"اسم الشخص", "الشخص", "الموظف", "اسم"};
        if (hint.contains("رقم")) return new String[]{"رقم العميل", "رقم", "الموبايل", "التليفون", "الهاتف"};
        if (hint.contains("لوكيشن")) return new String[]{"اللوكيشن", "لوكيشن", "الموقع", "العنوان", "مكان"};
        if (hint.contains("نوع")) return new String[]{"نوع الطفايه", "نوع الطفاية", "النوع", "نوع"};
        if (hint.contains("وزن")) return new String[]{"وزن الطفايه", "وزن الطفاية", "الوزن", "وزن"};
        if (hint.contains("عدد")) return new String[]{"عدد الطفايات", "العدد", "عدد"};
        if (hint.contains("مبلغ") || hint.contains("سعر") || hint.contains("إجمالي")) {
            return new String[]{"اجمالي مبلغ الطفايات", "إجمالي مبلغ الطفايات", "مبلغ الطفايات", "السعر", "سعر", "المبلغ", "مبلغ", "بكام"};
        }
        if (hint.contains("المرتب")) return new String[]{"المرتب", "مرتب", "الراتب", "راتب"};
        if (hint.contains("السلفة")) return new String[]{"مبلغ السلفه", "مبلغ السلفة", "السلفه", "السلفة", "سلفه", "سلفة"};
        if (hint.contains("ملاحظة")) return new String[]{"ملاحظه", "ملاحظة", "ملحوظه", "ملحوظة", "نوت"};
        if (hint.contains("تاريخ الاستيكر")) return new String[]{"تاريخ الاستيكر", "الاستيكر", "تاريخ"};
        if (hint.contains("تاريخ بداية العقد")) return new String[]{"تاريخ بداية العقد", "بداية العقد", "تاريخ العقد", "التاريخ", "تاريخ"};
        if (hint.contains("تاريخ البداية")) return new String[]{"تاريخ البدايه", "تاريخ البداية", "التاريخ", "تاريخ"};
        return new String[]{hint};
    }

    private String[] allVoiceKeys() {
        return new String[]{
                "اسم العميل", "العميل", "عميل", "اسم الشخص", "الشخص", "الموظف",
                "رقم العميل", "رقم", "الموبايل", "التليفون", "الهاتف",
                "اللوكيشن", "لوكيشن", "الموقع", "العنوان", "مكان",
                "نوع الطفايه", "نوع الطفاية", "النوع", "نوع",
                "وزن الطفايه", "وزن الطفاية", "الوزن", "وزن",
                "عدد الطفايات", "العدد", "عدد",
                "اجمالي مبلغ الطفايات", "إجمالي مبلغ الطفايات", "مبلغ الطفايات", "السعر", "سعر", "المبلغ", "مبلغ", "بكام",
                "المرتب", "مرتب", "الراتب", "راتب",
                "مبلغ السلفه", "مبلغ السلفة", "السلفه", "السلفة", "سلفه", "سلفة",
                "ملاحظه", "ملاحظة", "ملحوظه", "ملحوظة", "نوت",
                "تاريخ الاستيكر", "الاستيكر", "تاريخ بداية العقد", "بداية العقد", "تاريخ العقد", "تاريخ البدايه", "تاريخ البداية", "التاريخ", "تاريخ"
        };
    }

    private boolean isSeparator(char c) {
        return c == ' ' || c == ':' || c == '-' || c == '،' || c == ',' || c == '=';
    }

    private String trimSeparators(String value) {
        String result = value == null ? "" : value.trim();
        while (!result.isEmpty() && isSeparator(result.charAt(0))) result = result.substring(1).trim();
        while (!result.isEmpty() && isSeparator(result.charAt(result.length() - 1))) {
            result = result.substring(0, result.length() - 1).trim();
        }
        return result;
    }

    private String cleanVoiceText(String value, int inputType) {
        String cleaned = normalizeDigits(value).trim();
        int inputClass = inputType & InputType.TYPE_MASK_CLASS;
        if (inputClass == InputType.TYPE_CLASS_NUMBER) {
            double number = parseNumber(cleaned);
            if (Math.rint(number) == number) return String.valueOf((long) number);
            return String.format(Locale.US, "%.2f", number);
        }
        if (inputClass == InputType.TYPE_CLASS_PHONE) {
            return parsePhoneVoice(cleaned);
        }
        if (inputClass == InputType.TYPE_CLASS_DATETIME) {
            return cleaned.replace(" ", "").replace("/", "-");
        }
        return cleaned;
    }

    private String parsePhoneVoice(String value) {
        String normalized = normalizeDigits(value)
                .replace("زيرو", "صفر")
                .replace("او", "صفر")
                .replace("O", "0")
                .replace("o", "0");
        String compact = normalized.replaceAll("[^0-9+]", "");
        if (compact.length() >= 5) return compact;

        StringBuilder out = new StringBuilder();
        for (String token : normalized.split("\\s+")) {
            token = token.trim();
            if (token.startsWith("و") && token.length() > 1) token = token.substring(1);
            double digit = smallArabicNumber(token);
            if (digit >= 0 && digit <= 9) out.append((int) digit);
        }
        return out.length() > 0 ? out.toString() : compact;
    }

    private double parseNumber(String value) {
        String normalized = normalizeDigits(value)
                .replace(",", ".")
                .replace("جنيه", "")
                .replace("جنيهات", "")
                .replace("ريال", "")
                .replace("ريالات", "")
                .replace("طفاية", "")
                .replace("طفايات", "")
                .trim();
        try {
            String compact = normalized.replaceAll("[^0-9.\\-]", "");
            if (!compact.isEmpty() && compact.matches("-?[0-9]+(\\.[0-9]+)?")) {
                return Double.parseDouble(compact);
            }
        } catch (Exception ignored) {
        }
        return parseArabicWordsNumber(normalized);
    }

    private String normalizeDigits(String value) {
        if (value == null) return "";
        char[] out = value.toCharArray();
        for (int i = 0; i < out.length; i++) {
            if (out[i] >= '٠' && out[i] <= '٩') out[i] = (char) ('0' + (out[i] - '٠'));
            else if (out[i] >= '۰' && out[i] <= '۹') out[i] = (char) ('0' + (out[i] - '۰'));
        }
        return new String(out)
                .replace("أ", "ا")
                .replace("إ", "ا")
                .replace("آ", "ا")
                .replace("ة", "ه");
    }

    private double parseArabicWordsNumber(String value) {
        String cleaned = value.replace("-", " ")
                .replace(" و", " ")
                .replace("وال", "ال")
                .trim();
        if (cleaned.isEmpty()) return 0;

        double total = 0;
        double current = 0;
        for (String token : cleaned.split("\\s+")) {
            if (token.startsWith("و") && token.length() > 1) token = token.substring(1);
            double small = smallArabicNumber(token);
            if (small >= 0) {
                current += small;
            } else if (isHundred(token)) {
                current = current == 0 ? 100 : current * 100;
            } else if (isThousand(token)) {
                total += token.equals("الفين") ? 2000 : (current == 0 ? 1 : current) * 1000;
                current = 0;
            } else if (isMillion(token)) {
                total += (current == 0 ? 1 : current) * 1000000;
                current = 0;
            }
        }
        return total + current;
    }

    private double smallArabicNumber(String token) {
        switch (token) {
            case "صفر": return 0;
            case "واحد":
            case "واحده":
            case "احد": return 1;
            case "اثنين":
            case "اثنان":
            case "اتنين": return 2;
            case "ثلاثه":
            case "تلاته": return 3;
            case "اربعه": return 4;
            case "خمسه": return 5;
            case "سته": return 6;
            case "سبعه": return 7;
            case "ثمانيه":
            case "تمانيه": return 8;
            case "تسعه": return 9;
            case "عشره": return 10;
            case "حداشر":
            case "احدعشر": return 11;
            case "اتناشر":
            case "اثناعشر": return 12;
            case "تلتاشر":
            case "ثلاثتعشر": return 13;
            case "اربعتاشر":
            case "اربعهعشر": return 14;
            case "خمستاشر":
            case "خمسهعشر": return 15;
            case "ستاشر":
            case "ستهعشر": return 16;
            case "سبعتاشر":
            case "سبعهعشر": return 17;
            case "تمنتاشر":
            case "ثمانيهعشر": return 18;
            case "تسعتاشر":
            case "تسعهعشر": return 19;
            case "عشرين": return 20;
            case "ثلاثين":
            case "تلاتين": return 30;
            case "اربعين": return 40;
            case "خمسين": return 50;
            case "ستين": return 60;
            case "سبعين": return 70;
            case "ثمانين":
            case "تمانين": return 80;
            case "تسعين": return 90;
            case "ميه":
            case "مئه":
            case "مائه": return 100;
            case "مئتين":
            case "ميتين": return 200;
            case "تلتميه":
            case "ثلاثميه": return 300;
            case "ربعمية":
            case "اربعمية":
            case "اربعميه": return 400;
            case "خمسمية":
            case "خمسميه": return 500;
            case "ستميه":
            case "ستمائه": return 600;
            case "سبعميه": return 700;
            case "تمنميه":
            case "ثمانميه": return 800;
            case "تسعميه": return 900;
            default: return -1;
        }
    }

    private boolean isHundred(String token) {
        return token.equals("مئه") || token.equals("مائه") || token.equals("ميه");
    }

    private boolean isThousand(String token) {
        return token.equals("الف") || token.equals("الاف") || token.equals("الفين");
    }

    private boolean isMillion(String token) {
        return token.equals("مليون") || token.equals("ملايين");
    }

    private String val(Cursor c, String column) {
        String value = c.getString(c.getColumnIndexOrThrow(column));
        return safe(value);
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    private String emptyForDb(String value) {
        return value == null ? "" : value;
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
