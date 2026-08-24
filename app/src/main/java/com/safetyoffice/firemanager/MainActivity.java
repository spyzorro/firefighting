package com.safetyoffice.firemanager;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ContentProviderOperation;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import androidx.core.content.FileProvider;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int VOICE_REQUEST = 5042;
    private static final int RECORD_AUDIO_REQUEST = 5043;
    private static final int LOCATION_REQUEST = 5044;
    private static final int CONTACTS_REQUEST = 5045;
    private static final int ATTACHMENT_REQUEST = 5046;
    private static final int EXTINGUISHER_IMAGE_REQUEST = 5047;
    private static final int EXTINGUISHER_CAMERA_REQUEST = 5048;
    private static final int CAMERA_PERMISSION_REQUEST = 5049;
    private static final int ATTACHMENT_CAMERA_REQUEST = 5050;
    private static final int INSTALLATION_IMAGE_REQUEST = 5051;
    private static final int INSTALLATION_CAMERA_REQUEST = 5052;
    private static final String TEAM_CHANNEL_ID = "team_assignments";
    private static final int BRAND = Color.rgb(15, 23, 42);
    private static final int BRAND_DARK = Color.rgb(2, 6, 23);
    private static final int BRAND_LIGHT = Color.rgb(241, 245, 249);
    private static final int ACCENT = Color.rgb(220, 38, 38);
    private static final String SUPERVISOR_EMAIL = "mohamede669@gmail.com";
    private static final int BG = Color.rgb(248, 250, 252);
    private static final int CARD = Color.rgb(255, 255, 255);
    private static final int BORDER = Color.rgb(203, 213, 225);
    private static final int TEXT = Color.rgb(15, 23, 42);

    private DatabaseHelper db;
    private SyncManager sync;
    private boolean appUpdateChecked;
    private boolean appVersionPublished;
    private boolean updatePromptShown;
    private boolean assignmentsAutoPulled;
    private ListenerRegistration assignmentListener;
    private ListenerRegistration completedAssignmentListener;
    private String activeAssignmentListenerTeamCode = "";
    private String activeCompletedAssignmentListenerTeamCode = "";
    private LinearLayout content;
    private TextView syncBadge;
    private EditText voiceTarget;
    private EditText[] voiceGroup;
    private SpeechRecognizer speechRecognizer;
    private Button manualVoiceButton;
    private String manualVoiceIdleText = "";
    private String manualVoiceCommittedText = "";
    private String manualVoiceCurrentText = "";
    private boolean manualVoiceActive = false;
    private boolean manualVoiceStopRequested = false;
    private boolean manualVoiceApplied = false;
    private EditText locationTarget;
    private String currentTab = "home";
    private String pendingAttachmentName = "";
    private String pendingAttachmentPhone = "";
    private String pendingAttachmentPlace = "";
    private String pendingAttachmentLocation = "";
    private String pendingAttachmentStage = "";
    private boolean cameraForAttachment;
    private boolean cameraForInstallation;
    private long pendingInstallationId = 0;
    private String pendingInstallationName = "";
    private String pendingInstallationPhone = "";
    private String pendingInstallationPlace = "";
    private String pendingInstallationLocation = "";
    private String pendingInstallationStage = "";
    private String pendingExtinguisherImageUri = "";
    private Uri pendingCameraImageUri;
    private final ArrayList<String> pendingExtinguisherImageUris = new ArrayList<>();
    private String customerSearch = "";
    private String customerStatusFilter = "";
    private String customerWorkFilter = "";

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
    protected void onDestroy() {
        if (assignmentListener != null) assignmentListener.remove();
        if (completedAssignmentListener != null) completedAssignmentListener.remove();
        destroySpeechRecognizer();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SyncManager.RC_SIGN_IN) {
            sync.handleSignInResult(data, this::showSync);
        } else if (requestCode == ATTACHMENT_REQUEST && resultCode == RESULT_OK && data != null) {
            int added = saveAttachmentIntent(data);
            if (added == 0) toast("لم يتم اختيار صورة");
            else toast(added > 1 ? "تم حفظ " + added + " صور" : "تم حفظ الصورة");
            refreshPendingAttachmentCustomer();
        } else if (requestCode == ATTACHMENT_CAMERA_REQUEST && resultCode == RESULT_OK && pendingCameraImageUri != null) {
            saveAttachmentUri(pendingCameraImageUri);
            toast("تم حفظ صورة الكاميرا");
            refreshPendingAttachmentCustomer();
        } else if (requestCode == EXTINGUISHER_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            int added = addSelectedExtinguisherImages(data);
            if (added == 0) toast("لم يتم اختيار صورة جديدة");
            else toast(added > 1 ? "تم اختيار " + added + " صور للطفاية" : "تم اختيار صورة الطفاية");
        } else if (requestCode == EXTINGUISHER_CAMERA_REQUEST && resultCode == RESULT_OK && pendingCameraImageUri != null) {
            addPendingExtinguisherImage(pendingCameraImageUri.toString());
            toast("تم حفظ صورة الكاميرا للطفاية");
        } else if (requestCode == INSTALLATION_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            int added = saveInstallationImageIntent(data);
            if (added == 0) toast("لم يتم اختيار صورة");
            else toast(added > 1 ? "تم حفظ " + added + " صور للتركيب" : "تم حفظ صورة التركيب");
            refreshPendingInstallationCustomer();
        } else if (requestCode == INSTALLATION_CAMERA_REQUEST && resultCode == RESULT_OK && pendingCameraImageUri != null) {
            saveInstallationImageUri(pendingCameraImageUri);
            toast("تم حفظ صورة التركيب");
            refreshPendingInstallationCustomer();
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
            toast("تم السماح بالصوت، اضغط زر الصوت مرة أخرى وابدأ التسجيل");
        } else if (requestCode == LOCATION_REQUEST && locationTarget != null) {
            boolean granted = false;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) granted = true;
            }
            if (granted) fillCurrentLocation(locationTarget);
            else toast("لازم تسمح للتطبيق باستخدام الموقع علشان زر موقعي يشتغل");
        } else if (requestCode == CONTACTS_REQUEST) {
            boolean granted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) granted = false;
            }
            if (granted) {
                db.setSetting("auto_save_contacts", "1");
                toast("تم تشغيل حفظ جهات الاتصال تلقائيا");
                showSettings();
            } else {
                toast("لازم تسمح بجهات الاتصال علشان الحفظ التلقائي يشتغل");
            }
        } else if (requestCode == CAMERA_PERMISSION_REQUEST) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                if (cameraForInstallation) takeInstallationPhoto();
                else if (cameraForAttachment) takeAttachmentPhoto();
                else takeExtinguisherPhoto();
            }
            else toast("لازم تسمح للتطبيق بالكاميرا علشان التصوير يشتغل");
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
        sub.setTextColor(Color.rgb(71, 85, 105));
        sub.setGravity(Gravity.RIGHT);
        sub.setTextSize(14);
        root.addView(sub, matchWrap());

        syncBadge = new TextView(this);
        syncBadge.setGravity(Gravity.RIGHT);
        syncBadge.setTextSize(13);
        syncBadge.setPadding(dp(10), dp(6), dp(10), dp(6));
        refreshSyncBadge();
        LinearLayout.LayoutParams syncLp = matchWrap();
        syncLp.setMargins(0, dp(6), 0, dp(2));
        root.addView(syncBadge, syncLp);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        addTab(tabs, "تسجيل", R.drawable.ic_nav_extinguisher, v -> showExtinguishers());
        addTab(tabs, "العملاء", R.drawable.ic_nav_customers, v -> showCustomers());
        addTab(tabs, "تركيبات", R.drawable.ic_nav_tasks, v -> showInstallations());
        addTab(tabs, "عقود", R.drawable.ic_nav_alerts, v -> showMaintenance());
        addTab(tabs, "تنبيهات", R.drawable.ic_nav_alerts, v -> showAlerts());
        addTab(tabs, "الفنيين", R.drawable.ic_nav_sync, v -> showTeamInbox());
        addTab(tabs, "التقرير", R.drawable.ic_nav_report, v -> showMonthlyReport());
        addTab(tabs, "المهام", R.drawable.ic_nav_tasks, v -> showTasks());
        addTab(tabs, "المزيد", R.drawable.ic_nav_settings, v -> showMore());
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

    private void addTab(LinearLayout tabs, String text, int iconRes, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(BRAND_DARK);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setCompoundDrawablesWithIntrinsicBounds(0, iconRes, 0, 0);
        b.setCompoundDrawablePadding(dp(5));
        b.setMinHeight(dp(68));
        b.setPadding(dp(6), dp(6), dp(6), dp(6));
        b.setBackground(rounded(Color.WHITE, Color.rgb(203, 213, 225), dp(12)));
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(104), dp(74));
        lp.setMargins(dp(5), dp(10), dp(5), dp(8));
        tabs.addView(b, lp);
    }

    private void showHome() {
        currentTab = "home";
        clear();
        syncAppVersionState();
        if (isTechnicianUser()) {
            hero("تكليفاتي",
                    "هذه نسخة الفني. لن يظهر هنا إلا العملاء الذين حولهم لك المشرف بالكود.");
            button("استلام التكليفات المرسلة لي", this::showSync);
            section("اختصارات");
            homeAction("العملاء المحولون", "الشغل المرسل لك فقط", this::showCustomers);
            homeAction("تركيبات", "تسجيل بنود التركيب وصورها", this::showInstallations);
            homeAction("عقود الصيانة", "تعديل تفاصيل الزيارة والملاحظات", this::showMaintenance);
            homeAction("مزامنة Google", "استلام التكليفات وإرسال التحديث", this::showSync);
            return;
        }
        double total = singleDouble("SELECT COALESCE(SUM(total_price),0) FROM extinguishers");
        int count = (int) singleDouble("SELECT COALESCE(SUM(count),0) FROM extinguishers");
        int customers = (int) singleDouble("SELECT COUNT(*) FROM customers");
        int maintenance = (int) singleDouble("SELECT COUNT(*) FROM maintenance_contracts");
        int certificates = (int) singleDouble("SELECT COUNT(*) FROM safety_certificates");
        long[] range = monthRange();
        double shareTotal = monthlyShareTotal(range);
        hero("لوحة التحكم",
                "الطفايات: " + count + " | العملاء: " + customers + " | العقود: " + maintenance +
                        "\nالشهادات: " + certificates +
                        "\nإجمالي مبالغ الطفايات: " + money(total) +
                        "\nإجمالي نسبتك هذا الشهر: " + money(shareTotal));
        button("استلام من الفنيين", this::showTeamInbox);
        card("ملخص الشهر",
                "نسبتك الإجمالية: " + money(shareTotal) +
                        "\nعدد شهادات السلامة: " + certificates);
        button("تسجيل عميل وطفايات بسرعة", this::showExtinguishers);
        section("اختصارات");
        homeAction("العملاء", "بحث وتغيير حالة وواتساب", this::showCustomers);
        homeAction("تركيبات", "بنود وصور التركيبات لكل العملاء", this::showInstallations);
        homeAction("عقود الصيانة", "زيارات كل 3 شهور وتحويل للفنيين", this::showMaintenance);
        homeAction("تنبيهات", "المواعيد القريبة والمتأخرة", this::showAlerts);
        homeAction("التقرير", "إجماليات الشهر والنسب", this::showMonthlyReport);
        homeAction("المزيد", "مرتبات، شهادات، صيانة، إعدادات", this::showMore);
    }

    private void showMore() {
        currentTab = "more";
        clear();
        section("المزيد");
        if (isTechnicianUser()) {
            homeAction("العملاء المحولون", "الشغل المرسل لك فقط", this::showCustomers);
            homeAction("تركيبات", "تسجيل بنود التركيب وصورها", this::showInstallations);
            homeAction("عقود الصيانة", "تفاصيل الزيارات المحولة لك", this::showMaintenance);
            homeAction("مزامنة Google", "استلام التكليفات وإرسال التحديث", this::showSync);
            return;
        }
        homeAction("المرتبات والسلف", "تسجيل المرتبات والسلف الشهرية", this::showSalaries);
        homeAction("الشهادات والتقارير", "شهادات السلامة والتقارير الفنية", this::showCertificates);
        homeAction("عقود الصيانة", "زيارات كل 3 شهور وتنبيهات", this::showMaintenance);
        homeAction("استلام من الفنيين", "مراجعة التحديثات قبل اعتمادها", this::showTeamInbox);
        homeAction("الإعدادات", "النسب، واتساب، جهات الاتصال، ونسخة احتياطية", this::showSettings);
        homeAction("مزامنة Google", "حفظ واسترجاع البيانات", this::showSync);
    }

    private boolean isSupervisorUser() {
        FirebaseUser user = sync == null ? null : sync.user();
        return user != null && SUPERVISOR_EMAIL.equalsIgnoreCase(safe(user.getEmail()).trim());
    }

    private boolean isTechnicianUser() {
        return sync != null && sync.user() != null && !isSupervisorUser();
    }

    private void syncAppVersionState() {
        if (sync == null) return;
        if (!appUpdateChecked) {
            appUpdateChecked = true;
            sync.checkHostedRequiredUpdate(appVersionCode(), this::showRequiredUpdateOnce);
            if (sync.user() != null) sync.checkRequiredUpdate(appVersionCode(), this::showRequiredUpdateOnce);
        }
        if (sync.user() != null && isSupervisorUser() && !appVersionPublished) {
            appVersionPublished = true;
            sync.publishRequiredUpdate(appVersionCode(), appVersionName());
        }
        autoPullAssignmentsIfNeeded();
        startAssignmentListenerIfNeeded();
        startCompletedAssignmentListenerIfNeeded();
    }

    private void autoPullAssignmentsIfNeeded() {
        if (!isTechnicianUser() || assignmentsAutoPulled) return;
        String teamCode = db.setting("team_code", "").trim();
        if (teamCode.isEmpty()) return;
        assignmentsAutoPulled = true;
        sync.restoreAssignments(teamCode, null);
    }

    private void startAssignmentListenerIfNeeded() {
        if (!isTechnicianUser()) {
            stopAssignmentListener();
            return;
        }
        String teamCode = db.setting("team_code", "").trim().replace("/", "_");
        if (teamCode.isEmpty()) {
            stopAssignmentListener();
            return;
        }
        if (assignmentListener != null && teamCode.equals(activeAssignmentListenerTeamCode)) return;
        stopAssignmentListener();
        activeAssignmentListenerTeamCode = teamCode;
        assignmentListener = sync.listenOpenAssignments(teamCode, new SyncManager.AssignmentListener() {
            @Override
            public void onAssignmentsImported(int count, String latestCustomer) {
                showTeamAssignmentNotification(count, latestCustomer);
                if ("customers".equals(currentTab) || "home".equals(currentTab) || "sync".equals(currentTab)) {
                    showCustomers();
                }
            }

            @Override
            public void onError(String message) {
                toast("تعذر تحديث التكليفات: " + safe(message));
            }
        });
    }

    private void startCompletedAssignmentListenerIfNeeded() {
        if (!isSupervisorUser()) {
            stopCompletedAssignmentListener();
            return;
        }
        String teamCode = db.setting("team_code", "").trim().replace("/", "_");
        if (teamCode.isEmpty()) {
            stopCompletedAssignmentListener();
            return;
        }
        if (completedAssignmentListener != null && teamCode.equals(activeCompletedAssignmentListenerTeamCode)) return;
        stopCompletedAssignmentListener();
        activeCompletedAssignmentListenerTeamCode = teamCode;
        completedAssignmentListener = sync.listenCompletedAssignments(teamCode, new SyncManager.AssignmentListener() {
            @Override
            public void onAssignmentsImported(int count, String latestCustomer) {
                showSupervisorCompletedNotification(count, latestCustomer);
                if ("team_inbox".equals(currentTab)) loadTeamInbox(teamCode);
            }

            @Override
            public void onError(String message) {
                toast("تعذر تحديث استلام الفنيين: " + safe(message));
            }
        });
    }

    private void stopAssignmentListener() {
        if (assignmentListener != null) {
            assignmentListener.remove();
            assignmentListener = null;
        }
        activeAssignmentListenerTeamCode = "";
    }

    private void stopCompletedAssignmentListener() {
        if (completedAssignmentListener != null) {
            completedAssignmentListener.remove();
            completedAssignmentListener = null;
        }
        activeCompletedAssignmentListenerTeamCode = "";
    }

    private int appVersionCode() {
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                return (int) getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode();
            }
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    private String appVersionName() {
        try {
            String name = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return name == null ? "" : name;
        } catch (Exception e) {
            return "";
        }
    }

    private void showRequiredUpdate(String versionName, String apkUrl) {
        String version = safe(versionName).isEmpty() ? "الجديدة" : versionName;
        new AlertDialog.Builder(this)
                .setTitle("تحديث مطلوب")
                .setMessage("لازم تثبت نسخة " + version + " قبل متابعة استخدام التطبيق.")
                .setCancelable(false)
                .setPositiveButton("تحميل التحديث", (dialog, which) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)));
                    } catch (Exception e) {
                        toast("تعذر فتح رابط التحديث");
                    }
                })
                .show();
    }

    private void showRequiredUpdateOnce(String versionName, String apkUrl) {
        if (updatePromptShown) return;
        updatePromptShown = true;
        showRequiredUpdate(versionName, apkUrl);
    }

    private void showTasks() {
        currentTab = "tasks";
        clear();
        section("قائمة المهام");
        EditText title = input("عنوان المهمة", InputType.TYPE_CLASS_TEXT);
        EditText note = input("ملاحظة اختيارية", InputType.TYPE_CLASS_TEXT);
        EditText due = input("تاريخ التنفيذ اختياري yyyy-MM-dd", InputType.TYPE_CLASS_DATETIME);
        voiceAllButton("قول بيانات المهمة مرة واحدة", title, note, due);
        button("حفظ المهمة", () -> {
            if (empty(title)) return;
            try {
                long dueDate = txt(due).isEmpty() ? 0 : ReminderScheduler.parseDate(txt(due));
                ContentValues cv = new ContentValues();
                cv.put("title", txt(title));
                cv.put("note", txt(note));
                cv.put("due_date", dueDate);
                cv.put("is_done", 0);
                cv.put("created_at", System.currentTimeMillis());
                db.insert("tasks", cv);
                afterSave("تم حفظ المهمة");
                showTasks();
            } catch (Exception e) {
                toast("راجع التاريخ، لازم يكون بالشكل yyyy-MM-dd");
            }
        });

        section("المهام المسجلة");
        Cursor c = db.all("tasks");
        try {
            while (c.moveToNext()) {
                long id = c.getLong(c.getColumnIndexOrThrow("id"));
                int done = c.getInt(c.getColumnIndexOrThrow("is_done"));
                long dueDate = c.getLong(c.getColumnIndexOrThrow("due_date"));
                card(c.getString(c.getColumnIndexOrThrow("title")),
                        "الحالة: " + (done == 1 ? "تمت" : "مفتوحة") +
                                "\nالتاريخ: " + (dueDate > 0 ? ReminderScheduler.formatDate(dueDate) : "-") +
                                "\nملاحظة: " + val(c, "note"));
                secondaryButton(done == 1 ? "إرجاع كمهمة مفتوحة" : "تم تنفيذ المهمة", () -> {
                    ContentValues cv = new ContentValues();
                    cv.put("is_done", done == 1 ? 0 : 1);
                    db.update("tasks", cv, "id=?", String.valueOf(id));
                    afterSave("تم تحديث المهمة");
                    showTasks();
                });
            }
        } finally {
            c.close();
        }
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
        if (isTechnicianUser()) {
            section("تكليفات الفريق فقط");
            small("الفني لا يسجل عملاء جدد من هنا. استلم التكليفات من المشرف ثم افتح العميل المحول وعدل بياناته أو ارفع الصور.");
            button("استلام التكليفات", this::showSync);
            secondaryButton("العملاء المحولون", this::showCustomers);
            return;
        }
        pendingExtinguisherImageUri = "";
        pendingExtinguisherImageUris.clear();
        pendingCameraImageUri = null;
        hero("تسجيل سريع",
                "اكتب المهم فقط، أو قول البيانات مرة واحدة. الموقع والصور بزر واحد.");
        EditText customer = input("اسم العميل", InputType.TYPE_CLASS_TEXT);
        EditText phone = input("رقم العميل", InputType.TYPE_CLASS_PHONE);
        EditText count = input("عدد الطفايات", InputType.TYPE_CLASS_NUMBER);
        EditText price = input("إجمالي مبلغ الطفايات", numberType());
        EditText paid = input("المدفوع", numberType());
        paid.setText("0");
        EditText type = input("نوع الطفاية", InputType.TYPE_CLASS_TEXT);
        quickChoiceBar(type, "نوع الطفاية", extinguisherTypes());
        EditText weight = input("وزن الطفاية", InputType.TYPE_CLASS_TEXT);
        quickChoiceBar(weight, "وزن الطفاية", extinguisherWeights());
        EditText place = input("اسم المكان", InputType.TYPE_CLASS_TEXT);
        EditText location = input("اللوكيشن", InputType.TYPE_CLASS_TEXT);
        EditText customerStatus = hiddenInput("حالة العميل");
        customerStatus.setText("جاري الصيانة");
        statusInputBar(customerStatus);
        EditText deliveredAgain = hiddenInput("استلم الطفايات تاني؟ نعم/لا");
        deliveredAgain.setText("لا");
        EditText date = input("تاريخ الاستيكر yyyy-MM-dd", InputType.TYPE_CLASS_DATETIME);
        date.setText(today());
        quickImageBar();
        EditText voiceDraft = input("النص الصوتي للمراجعة قبل التوزيع", InputType.TYPE_CLASS_TEXT);
        voiceDraft.setSingleLine(false);
        voiceDraft.setMinLines(3);
        voiceToFieldButton("سجل الكلام هنا", voiceDraft);
        secondaryButton("وزع النص على الخانات", () -> {
            if (empty(voiceDraft)) return;
            fillVoiceGroup(txt(voiceDraft), new EditText[]{customer, phone, count, price, paid, type, weight, place, location, date});
        });
        small("مثال: محمد 20 طفاية بودرة CO2 120 ريال. بعد كده اضغط موقعي لو عاوز اللوكيشن الحالي.");
        button("مراجعة قبل الحفظ", () -> reviewQuickExtinguisher(customer, phone, count, price, paid, type, weight, place, location, customerStatus, deliveredAgain, date));
        secondaryButton("حفظ مباشر بدون مراجعة", () -> {
            if (empty(customer) || empty(count) || empty(price) || empty(date)) return;
            saveQuickExtinguisher(customer, phone, count, price, paid, type, weight, place, location, customerStatus, deliveredAgain, date, false);
        });
        secondaryButton("عرض آخر عمليات الطفايات", this::showRecentExtinguishers);
    }

    private void showRecentExtinguishers() {
        currentTab = "recent_extinguishers";
        clear();
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
                                "\nمدفوع: " + money(c.getDouble(c.getColumnIndexOrThrow("paid_amount"))) +
                                "\nمتبقي: " + money(Math.max(0, c.getDouble(c.getColumnIndexOrThrow("total_price")) - c.getDouble(c.getColumnIndexOrThrow("paid_amount")))) +
                                "\nاستلم تاني: " + yesNoLabel(c.getInt(c.getColumnIndexOrThrow("delivered_again"))) +
                                "\nالتذكير: " + ReminderScheduler.formatDate(c.getLong(c.getColumnIndexOrThrow("reminder_at"))) +
                                "\nرقم: " + val(c, "phone") +
                                "\nاسم المكان: " + val(c, "place_name") +
                                "\nلوكيشن: " + val(c, "location"));
                String image = rawVal(c, "image_uri");
                listExtinguisherImages(id, image, null);
            }
        } finally {
            c.close();
        }
        secondaryButton("رجوع للتسجيل السريع", this::showExtinguishers);
    }

    private void reviewQuickExtinguisher(EditText customer, EditText phone, EditText count, EditText price, EditText paid,
                                         EditText type, EditText weight, EditText place, EditText location,
                                         EditText customerStatus, EditText deliveredAgain, EditText date) {
        if (empty(customer) || empty(count) || empty(price) || empty(date)) return;
        try {
            ReminderScheduler.parseDate(txt(date));
            String status = txt(customerStatus).isEmpty() ? "جاري الصيانة" : txt(customerStatus);
            double remaining = Math.max(0, dbl(price) - dbl(paid));
            String summary = txt(customer) +
                    "\nرقم: " + txt(phone) +
                    "\nعدد الطفايات: " + txt(count) +
                    "\nالنوع: " + txt(type) +
                    "\nالوزن: " + txt(weight) +
                    "\nالإجمالي: " + money(dbl(price)) +
                    "\nالمدفوع: " + money(dbl(paid)) +
                    "\nالمتبقي: " + money(remaining) +
                    "\nالحالة: " + status +
                    "\nاسم المكان: " + txt(place) +
                    "\nالتاريخ: " + txt(date);
            new AlertDialog.Builder(this)
                    .setTitle("مراجعة قبل الحفظ")
                    .setMessage(summary)
                    .setPositiveButton("تأكيد الحفظ", (dialog, which) ->
                            saveQuickExtinguisher(customer, phone, count, price, paid, type, weight, place, location, customerStatus, deliveredAgain, date, false))
                    .setNegativeButton("رجوع للتعديل", null)
                    .show();
        } catch (Exception ex) {
            toast("راجع التاريخ، لازم يكون بالشكل yyyy-MM-dd");
        }
    }

    private void saveQuickExtinguisher(EditText customer, EditText phone, EditText count, EditText price, EditText paid,
                                       EditText type, EditText weight, EditText place, EditText location,
                                       EditText customerStatus, EditText deliveredAgain, EditText date,
                                       boolean duplicateConfirmed) {
        if (empty(customer) || empty(count) || empty(price) || empty(date)) return;
        if (!duplicateConfirmed && !txt(phone).isEmpty() && customerExistsByPhone(txt(phone))) {
            new AlertDialog.Builder(this)
                    .setTitle("العميل موجود")
                    .setMessage("الرقم ده متسجل قبل كده. تحب تضيف العملية الجديدة على نفس العميل؟")
                    .setPositiveButton("أضف العملية", (dialog, which) ->
                            saveQuickExtinguisher(customer, phone, count, price, paid, type, weight, place, location, customerStatus, deliveredAgain, date, true))
                    .setNegativeButton("رجوع للتعديل", null)
                    .show();
            return;
        }
        try {
            long stickerDate = ReminderScheduler.parseDate(txt(date));
            long reminder = ReminderScheduler.stickerReminder(stickerDate);
            String status = txt(customerStatus).isEmpty() ? "جاري الصيانة" : txt(customerStatus);

            ContentValues ccv = new ContentValues();
            ccv.put("name", txt(customer));
            ccv.put("phone", txt(phone));
            ccv.put("place_name", txt(place));
            ccv.put("location", txt(location));
            ccv.put("customer_status", status);
            ccv.put("created_at", System.currentTimeMillis());
            long customerId = db.insert("customers", ccv);

            ContentValues cv = new ContentValues();
            cv.put("customer_id", customerId);
            cv.put("customer_name", txt(customer));
            cv.put("phone", txt(phone));
            cv.put("place_name", txt(place));
            cv.put("location", txt(location));
            cv.put("customer_status", status);
            cv.put("extinguisher_type", txt(type));
            cv.put("weight", txt(weight));
            cv.put("count", integer(count));
            cv.put("total_price", dbl(price));
            cv.put("paid_amount", dbl(paid));
            cv.put("sticker_date", stickerDate);
            cv.put("reminder_at", reminder);
            cv.put("image_uri", pendingExtinguisherImageUri);
            cv.put("delivered_again", yesNo(txt(deliveredAgain)) ? 1 : 0);
            cv.put("created_at", System.currentTimeMillis());
            long extinguisherId = db.insert("extinguishers", cv);
            saveExtinguisherImages(extinguisherId, pendingExtinguisherImageUris);
            saveContactIfEnabled(txt(customer), txt(phone));
            afterSave("تم حفظ الطفايات والتنبيه يوم " + ReminderScheduler.formatDate(reminder));
            showExtinguishers();
        } catch (Exception ex) {
            toast("راجع التاريخ، لازم يكون بالشكل yyyy-MM-dd");
        }
    }

    private boolean customerExistsByPhone(String phone) {
        String normalized = normalizePhone(phone);
        if (normalized.isEmpty()) return false;
        Cursor c = db.raw("SELECT phone FROM (" +
                        "SELECT phone FROM customers UNION ALL SELECT phone FROM extinguishers " +
                        "UNION ALL SELECT phone FROM safety_certificates UNION ALL SELECT phone FROM technical_reports " +
                        "UNION ALL SELECT phone FROM maintenance_contracts) WHERE IFNULL(phone,'')<>''",
                new String[]{});
        try {
            while (c.moveToNext()) {
                if (normalized.equals(normalizePhone(c.getString(0)))) return true;
            }
            return false;
        } finally {
            c.close();
        }
    }

    private void showCustomers() {
        currentTab = "customers";
        clear();
        section("العملاء");
        EditText search = input("بحث باسم العميل أو الرقم", InputType.TYPE_CLASS_TEXT);
        search.setText(customerSearch);
        button("بحث سريع", () -> {
            customerSearch = txt(search);
            showCustomers();
        });
        if (!customerSearch.isEmpty()) {
            secondaryButton("إظهار كل العملاء", () -> {
                customerSearch = "";
                showCustomers();
            });
        }
        section("فلترة حسب الحالة");
        statusFilterBar();
        section("فلترة حسب نوع الشغل");
        workFilterBar();

        String sql = "SELECT customer_name, phone, place_name, location, customer_status, SUM(total_count) total_count, SUM(total_price) total_price " +
                "FROM (" +
                "SELECT name customer_name, IFNULL(phone,'') phone, IFNULL(place_name,'') place_name, IFNULL(location,'') location, IFNULL(customer_status,'جديد') customer_status, 0 total_count, 0 total_price, created_at last_at, 'عميل' work_type FROM customers " +
                "UNION ALL SELECT customer_name, IFNULL(phone,'') phone, IFNULL(place_name,'') place_name, IFNULL(location,'') location, IFNULL(customer_status,'جديد') customer_status, SUM(count) total_count, SUM(total_price) total_price, MAX(created_at) last_at, 'طفايات' work_type FROM extinguishers GROUP BY customer_name, IFNULL(phone,''), IFNULL(place_name,''), IFNULL(location,''), IFNULL(customer_status,'جديد') " +
                "UNION ALL SELECT customer_name, IFNULL(phone,'') phone, IFNULL(place_name,'') place_name, IFNULL(location,'') location, IFNULL(customer_status,'جديد') customer_status, 0 total_count, SUM(total_price) total_price, MAX(created_at) last_at, 'شهادات' work_type FROM safety_certificates GROUP BY customer_name, IFNULL(phone,''), IFNULL(place_name,''), IFNULL(location,''), IFNULL(customer_status,'جديد') " +
                "UNION ALL SELECT customer_name, IFNULL(phone,'') phone, IFNULL(place_name,'') place_name, IFNULL(location,'') location, IFNULL(customer_status,'جديد') customer_status, 0 total_count, SUM(total_price) total_price, MAX(created_at) last_at, 'تقارير' work_type FROM technical_reports GROUP BY customer_name, IFNULL(phone,''), IFNULL(place_name,''), IFNULL(location,''), IFNULL(customer_status,'جديد') " +
                "UNION ALL SELECT customer_name, IFNULL(phone,'') phone, IFNULL(place_name,'') place_name, IFNULL(location,'') location, IFNULL(customer_status,'جديد') customer_status, 0 total_count, 0 total_price, MAX(created_at) last_at, 'عقود' work_type FROM maintenance_contracts GROUP BY customer_name, IFNULL(phone,''), IFNULL(place_name,''), IFNULL(location,''), IFNULL(customer_status,'جديد') " +
                "UNION ALL SELECT customer_name, IFNULL(phone,'') phone, IFNULL(place_name,'') place_name, IFNULL(location,'') location, IFNULL(customer_status,'جديد') customer_status, 0 total_count, 0 total_price, MAX(created_at) last_at, 'تركيبات' work_type FROM installation_items GROUP BY customer_name, IFNULL(phone,''), IFNULL(place_name,''), IFNULL(location,''), IFNULL(customer_status,'جديد')" +
                ") ";
        ArrayList<String> args = new ArrayList<>();
        sql += "WHERE 1=1 ";
        if (!customerSearch.isEmpty()) {
            sql += "AND (customer_name LIKE ? OR phone LIKE ?) ";
            args.add("%" + customerSearch + "%");
            args.add("%" + customerSearch + "%");
        }
        if (!customerStatusFilter.isEmpty()) {
            sql += "AND customer_status=? ";
            args.add(customerStatusFilter);
        }
        if (!customerWorkFilter.isEmpty()) {
            sql += "AND work_type=? ";
            args.add(customerWorkFilter);
        }
        sql += "GROUP BY customer_name, phone, place_name, location, customer_status ORDER BY MAX(last_at) DESC";
        Cursor c = db.raw(sql, args.toArray(new String[0]));
        try {
            while (c.moveToNext()) {
                String oldName = c.getString(0);
                String oldPhone = emptyForDb(c.getString(1));
                String oldPlace = emptyForDb(c.getString(2));
                String oldLocation = emptyForDb(c.getString(3));
                String status = emptyForDb(c.getString(4));
                int extinguisherCount = c.getInt(5);
                double totalPrice = c.getDouble(6);
                if (isTechnicianUser() && db.teamAssignmentValue(oldName, oldPhone, oldPlace, oldLocation, "assignment_id").isEmpty()) {
                    continue;
                }
                card(oldName,
                        "رقم: " + safe(oldPhone) +
                                "\nاسم المكان: " + safe(oldPlace) +
                                "\nلوكيشن: " + safe(oldLocation) +
                                "\nالحالة: " + safe(status.isEmpty() ? "جديد" : status) +
                                "\nإجمالي الطفايات: " + displayCount(extinguisherCount) +
                                "\nإجمالي المبلغ: " + money(totalPrice));
                secondaryButton("إجراء سريع", () -> showCustomerQuickActions(oldName, oldPhone, oldPlace, oldLocation, status, extinguisherCount, totalPrice));
            }
        } finally {
            c.close();
        }
    }

    private void showCustomerQuickActions(String name, String phone, String place, String location, String status,
                                          int extinguisherCount, double totalPrice) {
        currentTab = "customer_actions";
        clear();
        section("إجراء سريع");
        card(name,
                "رقم: " + safe(phone) +
                        "\nالحالة: " + safe(status.isEmpty() ? "جديد" : status) +
                        "\nالطفايات: " + displayCount(extinguisherCount) +
                        "\nالإجمالي: " + money(totalPrice));
        button("فتح ملف العميل", () -> showCustomerDetails(name, phone, place, location, status, extinguisherCount, totalPrice));
        if (isTechnicianUser()) {
            actionButton("WhatsApp - تواصل", Color.rgb(22, 163, 74), R.drawable.ic_action_whatsapp, () -> openWhatsAppChat(phone));
        } else {
            secondaryButton("رسالة واتساب", () -> sendWhatsApp(phone, name, extinguisherCount));
            secondaryButton("مشاركة تقرير واتساب", () -> shareCustomerReportWhatsApp(phone, name, place, location, status, extinguisherCount, totalPrice));
        }
        Runnable openMapAction = () -> {
            if (emptyForDb(location).isEmpty()) toast("لا يوجد لوكيشن مسجل");
            else openLocation(location);
        };
        if (isTechnicianUser()) actionButton("Google Maps - فتح اللوكيشن", Color.rgb(37, 99, 235), R.drawable.ic_action_maps, openMapAction);
        else secondaryButton("فتح اللوكيشن", openMapAction);
        if (isTechnicianUser()) actionButton("تعديل بيانات العميل", Color.rgb(234, 88, 12), () -> showCustomerEditPage(name, phone, place, location));
        else secondaryButton("تعديل بيانات العميل", () -> showCustomerEditPage(name, phone, place, location));
        secondaryButton("تغيير الحالة", () -> showCustomerStatusPage(name, phone, place, location, status));
        if (isSupervisorUser()) {
            secondaryButton("تحويل للفريق", () -> showAssignCustomerToTeam(name, phone, place, location, status, extinguisherCount, totalPrice));
        }
        String assignmentId = db.teamAssignmentValue(name, phone, place, location, "assignment_id");
        String assignmentTeam = db.teamAssignmentValue(name, phone, place, location, "team_code");
        if (isTechnicianUser() && !assignmentId.isEmpty()) {
            actionButton("تسليم للمشرف", Color.rgb(220, 38, 38), () -> finishTeamAssignment(assignmentTeam, assignmentId, name, phone, place, location));
        }
        secondaryButton("رجوع للعملاء", this::showCustomers);
    }

    private void showAssignCustomerToTeam(String name, String phone, String place, String location, String status,
                                          int extinguisherCount, double totalPrice) {
        currentTab = "assign_customer";
        clear();
        if (!isSupervisorUser()) {
            section("غير مصرح");
            small("تحويل العملاء للفريق متاح للمشرف فقط.");
            secondaryButton("رجوع", this::showCustomers);
            return;
        }
        section("تحويل العميل للفريق");
        card(name,
                "رقم: " + safe(phone) +
                        "\nالحالة: " + safe(status.isEmpty() ? "جديد" : status) +
                        "\nالطفايات: " + displayCount(extinguisherCount) +
                        "\nالإجمالي: " + money(totalPrice));
        EditText teamCode = input("كود الفريق الذي سيستلم العميل", InputType.TYPE_CLASS_TEXT);
        teamCode.setText(db.setting("last_assign_team_code", db.setting("team_code", "")));
        small("الفريق الذي يستخدم هذا الكود سيستلم هذا العميل تلقائيا عند فتح التطبيق.");
        button("تحويل العميل الآن", () -> {
            if (empty(teamCode)) return;
            try {
                db.setSetting("last_assign_team_code", txt(teamCode).trim().replace("/", "_"));
                sync.assignCustomerToTeam(txt(teamCode), customerSnapshot(name, phone, place, location), name, phone, place, location,
                        () -> showCustomerQuickActions(name, phone, place, location, status, extinguisherCount, totalPrice));
            } catch (Exception e) {
                toast("فشل تجهيز بيانات العميل: " + e.getMessage());
            }
        });
        secondaryButton("رجوع", () -> showCustomerQuickActions(name, phone, place, location, status, extinguisherCount, totalPrice));
    }

    private void finishTeamAssignment(String teamCode, String assignmentId, String name, String phone, String place, String location) {
        try {
            sync.upload(() -> {
                try {
                    sync.completeAssignment(teamCode, assignmentId, customerSnapshot(name, phone, place, location), () -> {
                        db.deleteCustomerEverywhere(name, phone, place, location);
                        showCustomers();
                    });
                } catch (Exception e) {
                    toast("فشل تجهيز تحديث المشرف: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            toast("فشل إرسال التحديث: " + e.getMessage());
        }
    }

    private void showCustomerDetails(String oldName, String oldPhone, String oldPlace, String oldLocation, String currentStatus,
                                     int extinguisherCount, double totalPrice) {
        currentTab = "customer_detail";
        clear();
        section("صفحة العميل");
        card(oldName,
                "رقم: " + safe(oldPhone) +
                        "\nاسم المكان: " + safe(oldPlace) +
                        "\nلوكيشن: " + safe(oldLocation) +
                        "\nالحالة: " + safe(currentStatus.isEmpty() ? "جديد" : currentStatus) +
                        "\nإجمالي الطفايات: " + displayCount(extinguisherCount) +
                        "\nإجمالي المبلغ: " + money(totalPrice));
        customerWorkSummaryCard(oldName, oldPhone, oldPlace, oldLocation);
        if (isTechnicianUser()) {
            actionButton("WhatsApp - تواصل", Color.rgb(22, 163, 74), R.drawable.ic_action_whatsapp, () -> openWhatsAppChat(oldPhone));
        } else {
            secondaryButton("رسالة واتساب جاهزة", () -> sendWhatsApp(oldPhone, oldName, extinguisherCount));
            secondaryButton("مشاركة تقرير العميل واتساب", () -> shareCustomerReportWhatsApp(oldPhone, oldName, oldPlace, oldLocation, currentStatus, extinguisherCount, totalPrice));
        }
        Runnable openMapAction = () -> {
            if (emptyForDb(oldLocation).isEmpty()) toast("لا يوجد لوكيشن مسجل");
            else openLocation(oldLocation);
        };
        if (isTechnicianUser()) actionButton("Google Maps - فتح اللوكيشن", Color.rgb(37, 99, 235), R.drawable.ic_action_maps, openMapAction);
        else secondaryButton("فتح اللوكيشن", openMapAction);
        if (isTechnicianUser()) actionButton("تعديل بيانات العميل", Color.rgb(234, 88, 12), () -> showCustomerEditPage(oldName, oldPhone, oldPlace, oldLocation));
        else secondaryButton("تعديل بيانات العميل", () -> showCustomerEditPage(oldName, oldPhone, oldPlace, oldLocation));
        secondaryButton("تغيير حالة العميل", () -> showCustomerStatusPage(oldName, oldPhone, oldPlace, oldLocation, currentStatus));
        if (isSupervisorUser()) actionButton("حذف العميل نهائيا", Color.rgb(220, 38, 38),
                () -> confirmDeleteCustomer(oldName, oldPhone, oldPlace, oldLocation));
        if (isTechnicianUser()) actionButton("إضافة صور", Color.rgb(124, 58, 237), () -> chooseAttachment(oldName, oldPhone, oldPlace, oldLocation));
        else secondaryButton("إضافة صورة/مرفق", () -> chooseAttachment(oldName, oldPhone, oldPlace, oldLocation));
        if (isTechnicianUser()) actionButton("إضافة تركيب", Color.rgb(14, 165, 233), () -> showAddInstallationPage(oldName, oldPhone, oldPlace, oldLocation));
        else secondaryButton("إضافة تركيب", () -> showAddInstallationPage(oldName, oldPhone, oldPlace, oldLocation));
        if (isTechnicianUser()) actionButton("إضافة/تعديل عقد صيانة", Color.rgb(15, 118, 110), () -> showAddMaintenanceForCustomer(oldName, oldPhone, oldPlace, oldLocation));
        else secondaryButton("إضافة عقد صيانة", () -> showAddMaintenanceForCustomer(oldName, oldPhone, oldPlace, oldLocation));
        if (isSupervisorUser()) {
            secondaryButton("تحويل كل شغل العميل للفريق", () ->
                    showAssignCustomerToTeam(oldName, oldPhone, oldPlace, oldLocation, currentStatus, extinguisherCount, totalPrice));
        }
        String assignmentId = db.teamAssignmentValue(oldName, oldPhone, oldPlace, oldLocation, "assignment_id");
        String assignmentTeam = db.teamAssignmentValue(oldName, oldPhone, oldPlace, oldLocation, "team_code");
        if (isTechnicianUser() && !assignmentId.isEmpty()) {
            actionButton("تم التسليم للمشرف", Color.rgb(220, 38, 38), () ->
                    finishTeamAssignment(assignmentTeam, assignmentId, oldName, oldPhone, oldPlace, oldLocation));
        }
        secondaryButton("رجوع لقائمة العملاء", this::showCustomers);

        section("كل بيانات العميل");
        listCustomerRecords(oldName, oldPhone, oldPlace, oldLocation);
        listCustomerInstallations(oldName, oldPhone, oldPlace, oldLocation);
        listCustomerAttachments(oldName, oldPhone, oldPlace, oldLocation);
    }

    private void confirmDeleteCustomer(String name, String phone, String place, String location) {
        new AlertDialog.Builder(this)
                .setTitle("حذف العميل")
                .setMessage("سيتم حذف العميل وكل بياناته من البرنامج. هل أنت متأكد؟")
                .setPositiveButton("حذف نهائي", (dialog, which) -> {
                    String teamCode = db.setting("team_code", "");
                    db.deleteCustomerEverywhere(name, phone, place, location);
                    sync.deleteCustomerAssignments(teamCode, name, emptyForDb(phone), emptyForDb(place), emptyForDb(location));
                    afterSave("تم حذف العميل وكل بياناته");
                    showCustomers();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void customerWorkSummaryCard(String name, String phone, String place, String location) {
        String installations = customerInstallationSummaryText(name, phone, place, location);
        String maintenance = customerMaintenanceSummaryText(name, phone, place, location);
        if (installations.isEmpty() && maintenance.isEmpty()) return;
        card("إجمالي شغل العميل", (installations.isEmpty() ? "التركيبات: لا يوجد" : installations) +
                "\n\n" + (maintenance.isEmpty() ? "عقد الصيانة: لا يوجد" : maintenance));
    }

    private String customerInstallationSummaryText(String name, String phone, String place, String location) {
        Cursor c = db.raw("SELECT item_type, IFNULL(subtype,''), COUNT(*) FROM installation_items " +
                        "WHERE customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=? " +
                        "GROUP BY item_type, IFNULL(subtype,'') ORDER BY item_type",
                customerArgs(name, phone, place, location));
        StringBuilder out = new StringBuilder();
        try {
            while (c.moveToNext()) {
                String type = safe(c.getString(0));
                String subtype = emptyForDb(c.getString(1));
                if (out.length() == 0) out.append("التركيبات:");
                out.append("\n").append(type);
                if (!subtype.isEmpty()) out.append(" - ").append(subtype);
                out.append(": ").append(c.getInt(2));
            }
        } finally {
            c.close();
        }
        return out.toString();
    }

    private String customerMaintenanceSummaryText(String name, String phone, String place, String location) {
        Cursor c = db.raw("SELECT COALESCE(SUM(extinguisher_count),0), COALESCE(SUM(detector_count),0), " +
                        "COALESCE(SUM(bell_count),0), COALESCE(SUM(breaker_count),0), COALESCE(SUM(exit_count),0), " +
                        "COALESCE(SUM(panel_count),0), MIN(next_visit_at) FROM maintenance_contracts " +
                        "WHERE customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=?",
                customerArgs(name, phone, place, location));
        try {
            if (!c.moveToFirst()) return "";
            int total = c.getInt(0) + c.getInt(1) + c.getInt(2) + c.getInt(3) + c.getInt(4) + c.getInt(5);
            long nextVisit = c.getLong(6);
            if (total == 0 && nextVisit == 0) return "";
            return "عقد الصيانة:" +
                    "\nطفايات: " + c.getInt(0) + " | كواشف: " + c.getInt(1) + " | أجراس: " + c.getInt(2) +
                    "\nكواسر: " + c.getInt(3) + " | Exit: " + c.getInt(4) + " | لوحات: " + c.getInt(5) +
                    (nextVisit > 0 ? "\nأقرب زيارة: " + ReminderScheduler.formatDate(nextVisit) : "");
        } finally {
            c.close();
        }
    }

    private void showCustomerEditPage(String oldName, String oldPhone, String oldPlace, String oldLocation) {
        currentTab = "customer_edit";
        clear();
        section("تعديل بيانات العميل");
        small("عدل البيانات هنا فقط، وبعد الحفظ هترجع لصفحة العميل بدون تداخل.");
        EditText name = input("اسم العميل", InputType.TYPE_CLASS_TEXT);
        name.setText(oldName);
        EditText phone = input("رقم العميل", InputType.TYPE_CLASS_PHONE);
        phone.setText(oldPhone);
        EditText place = input("اسم المكان", InputType.TYPE_CLASS_TEXT);
        place.setText(oldPlace);
        EditText location = input("اللوكيشن", InputType.TYPE_CLASS_TEXT);
        location.setText(oldLocation);
        button("حفظ وإغلاق صفحة العميل", () -> {
            if (empty(name)) return;
            db.updateCustomerEverywhere(oldName, oldPhone, oldPlace, oldLocation, txt(name), txt(phone), txt(place), txt(location));
            saveContactIfEnabled(txt(name), txt(phone));
            afterSave("تم حفظ بيانات العميل");
            openCustomerDetails(txt(name), txt(phone), txt(place), txt(location));
        });
        secondaryButton("إلغاء والرجوع لصفحة العميل", () -> openCustomerDetails(oldName, oldPhone, oldPlace, oldLocation));
    }

    private void showCustomerStatusPage(String name, String phone, String place, String location, String currentStatus) {
        currentTab = "customer_status";
        clear();
        section("تغيير حالة العميل");
        card(name, "الحالة الحالية: " + safe(currentStatus.isEmpty() ? "جديد" : currentStatus));
        for (String status : customerStatuses()) statusButton(status, currentStatus, name, phone, place, location);
        secondaryButton("إلغاء والرجوع لصفحة العميل", () -> openCustomerDetails(name, phone, place, location));
    }

    private void statusButton(String status, String currentStatus, String name, String phone, String place, String location) {
        String selected = status.equals(currentStatus) ? " ✓" : "";
        Button b = new Button(this);
        b.setText(status + selected);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setMinHeight(dp(48));
        styleStatusChoice(b, status.equals(currentStatus));
        b.setOnClickListener(v -> {
            db.updateCustomerStatusEverywhere(name, phone, place, location, status);
            afterSave("تم تحديث حالة العميل: " + status);
            showCustomerDetails(name, phone, place, location, status,
                    customerExtinguisherCount(name, phone, place, location),
                    customerTotalPrice(name, phone, place, location));
        });
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(3), 0, dp(8));
        content.addView(b, lp);
    }

    private void statusFilterBar() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        filterStatusButton(row, "كل الحالات", "");
        for (String status : customerStatuses()) filterStatusButton(row, status, status);
        hsv.addView(row);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(2), 0, dp(8));
        content.addView(hsv, lp);
    }

    private void filterStatusButton(LinearLayout row, String label, String status) {
        String selected = status.equals(customerStatusFilter) ? " ✓" : "";
        Button b = new Button(this);
        b.setText(label + selected);
        b.setTextColor(BRAND_DARK);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setBackground(rounded(status.equals(customerStatusFilter) ? BRAND_LIGHT : Color.WHITE,
                status.equals(customerStatusFilter) ? ACCENT : Color.rgb(203, 213, 225), dp(16)));
        b.setOnClickListener(v -> {
            customerStatusFilter = status;
            showCustomers();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44));
        lp.setMargins(dp(4), 0, dp(4), dp(4));
        row.addView(b, lp);
    }

    private void workFilterBar() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        workFilterButton(row, "كل الشغل", "");
        workFilterButton(row, "طفايات", "طفايات");
        workFilterButton(row, "تركيبات", "تركيبات");
        workFilterButton(row, "عقود صيانة", "عقود");
        hsv.addView(row);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(2), 0, dp(8));
        content.addView(hsv, lp);
    }

    private void workFilterButton(LinearLayout row, String label, String workType) {
        String selected = workType.equals(customerWorkFilter) ? " ✓" : "";
        Button b = new Button(this);
        b.setText(label + selected);
        b.setTextColor(BRAND_DARK);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setBackground(rounded(workType.equals(customerWorkFilter) ? BRAND_LIGHT : Color.WHITE,
                workType.equals(customerWorkFilter) ? ACCENT : Color.rgb(203, 213, 225), dp(16)));
        b.setOnClickListener(v -> {
            customerWorkFilter = workType;
            showCustomers();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44));
        lp.setMargins(dp(4), 0, dp(4), dp(4));
        row.addView(b, lp);
    }

    private void openCustomerDetails(String name, String phone, String place, String location) {
        showCustomerDetails(name, phone, place, location,
                customerStatus(name, phone, place, location),
                customerExtinguisherCount(name, phone, place, location),
                customerTotalPrice(name, phone, place, location));
    }

    private String[] customerStatuses() {
        return new String[]{
                "جديد",
                "استلام الطفايات",
                "تسليم الطفايات",
                "تسليم جزئي",
                "جاري الصيانة",
                "انتظار التحصيل"
        };
    }

    private String[] installationTypes() {
        String raw = db.setting("installation_types", defaultInstallationTypesText());
        String[] parts = raw.split("[\\n،,;|]+");
        ArrayList<String> values = new ArrayList<>();
        for (String part : parts) {
            String clean = part.trim();
            if (!clean.isEmpty() && !values.contains(clean)) values.add(clean);
        }
        if (values.isEmpty()) {
            for (String part : defaultInstallationTypesText().split("\\n")) values.add(part);
        }
        return values.toArray(new String[0]);
    }

    private String defaultInstallationTypesText() {
        return "كاشف عادي\n" +
                "كاشف زيتا\n" +
                "كاشف ادريسبول\n" +
                "جرس\n" +
                "كاسر\n" +
                "Exit\n" +
                "لوحة إنذار حريق\n" +
                "طفاية";
    }

    private String firstDetectorType() {
        for (String type : installationTypes()) {
            if (type.contains("كاشف")) return type.replace("كاشف", "").trim().isEmpty() ? type : type.replace("كاشف", "").trim();
        }
        return "عادي";
    }

    private void detectorTypeScroll(EditText target) {
        ArrayList<String> choices = new ArrayList<>();
        for (String type : installationTypes()) {
            if (type.contains("كاشف")) {
                String clean = type.replace("كاشف", "").trim();
                choices.add(clean.isEmpty() ? type : clean);
            }
        }
        if (choices.isEmpty()) {
            choices.add("عادي");
            choices.add("زيتا");
            choices.add("ادريسبول");
        }
        choiceScroll("اختار نوع الكاشف", target, choices);
    }

    private void installationTypeScroll(EditText target) {
        ArrayList<String> choices = new ArrayList<>();
        for (String type : installationTypes()) {
            if (!type.contains("كاشف") && !type.equals("جرس") && !type.equals("كاسر") &&
                    !type.equals("طفاية") && !type.equals("Exit") && !type.contains("لوحة")) {
                choices.add(type);
            }
        }
        if (!choices.isEmpty()) choiceScroll("بنود إضافية", target, choices);
    }

    private void choiceScroll(String title, EditText target, ArrayList<String> choices) {
        small(title);
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (String choice : choices) {
            Button b = new Button(this);
            b.setText(choice);
            b.setTextSize(13);
            b.setTextColor(Color.WHITE);
            b.setAllCaps(false);
            b.setMinHeight(dp(42));
            b.setBackground(rounded(installationTypeColor(choice), darken(installationTypeColor(choice)), dp(18)));
            b.setOnClickListener(v -> {
                target.setText(choice);
                target.setSelection(target.getText().length());
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44));
            lp.setMargins(dp(4), dp(2), dp(4), dp(8));
            row.addView(b, lp);
        }
        hsv.addView(row);
        content.addView(hsv, matchWrap());
    }

    private int saveInstallationItems(String name, String phone, String place, String location,
                                      String type, String subtype, int count, String note) {
        String cleanType = emptyForDb(type).trim();
        if (cleanType.isEmpty() || count <= 0) return 0;
        int added = 0;
        for (int i = 1; i <= count; i++) {
            ContentValues cv = new ContentValues();
            cv.put("customer_name", name);
            cv.put("phone", emptyForDb(phone));
            cv.put("place_name", emptyForDb(place));
            cv.put("location", emptyForDb(location));
            cv.put("customer_status", customerStatus(name, phone, place, location));
            cv.put("item_type", cleanType);
            cv.put("item_number", i);
            cv.put("subtype", emptyForDb(subtype));
            cv.put("note", emptyForDb(note));
            cv.put("created_at", System.currentTimeMillis() + i);
            db.insert("installation_items", cv);
            added++;
        }
        return added;
    }

    private int installationTypeColor(String type) {
        String value = emptyForDb(type);
        if (value.contains("كاشف")) return Color.rgb(37, 99, 235);
        if (value.contains("جرس")) return Color.rgb(220, 38, 38);
        if (value.contains("كاسر")) return Color.rgb(234, 88, 12);
        if (value.toLowerCase(Locale.US).contains("exit")) return Color.rgb(22, 163, 74);
        if (value.contains("لوحة")) return Color.rgb(124, 58, 237);
        if (value.contains("طفاية")) return Color.rgb(245, 158, 11);
        return Color.rgb(15, 118, 110);
    }

    private void listCustomerRecords(String name, String phone, String place, String location) {
        String[] args = customerArgs(name, phone, place, location);
        Cursor e = db.raw("SELECT id, extinguisher_type, weight, count, total_price, sticker_date, reminder_at, image_uri, delivered_again, IFNULL(paid_amount,0) FROM extinguishers " +
                "WHERE customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=? ORDER BY created_at DESC", args);
        try {
            while (e.moveToNext()) {
                long id = e.getLong(0);
                card("طفايات",
                        "النوع: " + safe(e.getString(1)) +
                                "\nالوزن: " + safe(e.getString(2)) +
                                "\nالعدد: " + e.getInt(3) +
                                "\nالمبلغ: " + money(e.getDouble(4)) +
                                "\nالمدفوع: " + money(e.getDouble(9)) +
                                "\nالمتبقي: " + money(Math.max(0, e.getDouble(4) - e.getDouble(9))) +
                                "\nتاريخ الاستيكر: " + ReminderScheduler.formatDate(e.getLong(5)) +
                                "\nالتذكير: " + ReminderScheduler.formatDate(e.getLong(6)) +
                                "\nاستلم تاني: " + yesNoLabel(e.getInt(8)));
                String image = emptyForDb(e.getString(7));
                listExtinguisherImages(id, image, () -> openCustomerDetails(name, phone, place, location));
                secondaryButton("تعديل بيانات الطفايات", () -> showExtinguisherEdit(id));
            }
        } finally {
            e.close();
        }

        listCustomerAnnualRecords("safety_certificates", "certificate_date", "شهادة سلامة", args);
        listCustomerAnnualRecords("technical_reports", "report_date", "تقرير فني", args);

        Cursor m = db.raw("SELECT id, start_date, next_visit_at, reminder_at, extinguisher_count, detector_count, bell_count, breaker_count, exit_count, panel_count, IFNULL(note,'') FROM maintenance_contracts " +
                "WHERE customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=? ORDER BY created_at DESC", args);
        try {
            while (m.moveToNext()) {
                long id = m.getLong(0);
                card("عقد صيانة",
                        "تاريخ البداية: " + ReminderScheduler.formatDate(m.getLong(1)) +
                                "\nالزيارة القادمة: " + ReminderScheduler.formatDate(m.getLong(2)) +
                                "\nالتذكير: " + ReminderScheduler.formatDate(m.getLong(3)) +
                                "\nطفايات: " + m.getInt(4) + " | كواشف: " + m.getInt(5) + " | أجراس: " + m.getInt(6) +
                                "\nكواسر: " + m.getInt(7) + " | Exit: " + m.getInt(8) + " | لوحات: " + m.getInt(9) +
                                "\nملاحظات: " + safe(m.getString(10)));
                secondaryButton("تعديل عقد الصيانة", () -> showMaintenanceEdit(id));
            }
        } finally {
            m.close();
        }
    }

    private void showAddInstallationPage(String name, String phone, String place, String location) {
        currentTab = "installation_add";
        clear();
        section("إضافة تركيب");
        card(name, "سجل كل بنود التركيب للعميل مرة واحدة، وبعدها أضف الصور لكل بند من قائمة التركيبات.");
        EditText detectorCount = input("عدد الكواشف", numberType());
        EditText detectorType = input("نوع الكاشف", InputType.TYPE_CLASS_TEXT);
        detectorType.setText(firstDetectorType());
        detectorTypeScroll(detectorType);
        EditText breakerCount = input("عدد الكواسر", numberType());
        EditText bellCount = input("عدد الأجراس", numberType());
        EditText extinguisherCount = input("عدد الطفايات", numberType());
        EditText exitCount = input("عدد Exit", numberType());
        EditText panelCount = input("عدد لوحات الإنذار/الحريق", numberType());
        EditText extraType = input("بند إضافي من الإعدادات", InputType.TYPE_CLASS_TEXT);
        EditText extraCount = input("عدد البند الإضافي", numberType());
        installationTypeScroll(extraType);
        EditText note = input("ملاحظات", InputType.TYPE_CLASS_TEXT);
        note.setSingleLine(false);
        note.setMinLines(2);
        voiceAllButton("قول بيانات التركيبات مرة واحدة", detectorCount, detectorType,
                breakerCount, bellCount, extinguisherCount, exitCount, panelCount, extraType, extraCount, note);
        button("حفظ كل بنود التركيب", () -> {
            int added = 0;
            added += saveInstallationItems(name, phone, place, location, "كاشف", txt(detectorType), parseInt(txt(detectorCount), 0), txt(note));
            added += saveInstallationItems(name, phone, place, location, "كاسر", "", parseInt(txt(breakerCount), 0), txt(note));
            added += saveInstallationItems(name, phone, place, location, "جرس", "", parseInt(txt(bellCount), 0), txt(note));
            added += saveInstallationItems(name, phone, place, location, "طفاية", "", parseInt(txt(extinguisherCount), 0), txt(note));
            added += saveInstallationItems(name, phone, place, location, "Exit", "", parseInt(txt(exitCount), 0), txt(note));
            added += saveInstallationItems(name, phone, place, location, "لوحة إنذار حريق", "", parseInt(txt(panelCount), 0), txt(note));
            added += saveInstallationItems(name, phone, place, location, txt(extraType), "", parseInt(txt(extraCount), 0), txt(note));
            if (added == 0) {
                toast("اكتب عدد بند واحد على الأقل");
                return;
            }
            afterSave("تم حفظ " + added + " بند تركيب");
            openCustomerDetails(name, phone, place, location);
        });
        secondaryButton("رجوع لصفحة العميل", () -> openCustomerDetails(name, phone, place, location));
    }

    private void listCustomerInstallations(String name, String phone, String place, String location) {
        section("التركيبات");
        Cursor c = db.raw("SELECT id, item_type, item_number, IFNULL(subtype,''), IFNULL(note,''), created_at FROM installation_items " +
                        "WHERE customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=? ORDER BY created_at DESC",
                customerArgs(name, phone, place, location));
        boolean any = false;
        try {
            while (c.moveToNext()) {
                any = true;
                long id = c.getLong(0);
                String type = safe(c.getString(1));
                int number = c.getInt(2);
                String subtype = safe(c.getString(3));
                String note = safe(c.getString(4));
                card(type + " رقم " + number,
                        "النوع التفصيلي: " + blankDash(subtype) +
                                "\nملاحظات: " + blankDash(note) +
                                "\nتاريخ الإضافة: " + ReminderScheduler.formatDate(c.getLong(5)));
                listInstallationImages(id, name, phone, place, location);
                actionButton("إضافة صور لهذا البند", Color.rgb(14, 165, 233),
                        () -> chooseInstallationImage(name, phone, place, location, id, "بعد التنفيذ"));
                secondaryButton("مشاركة هذا البند واتساب", () -> shareInstallationToWhatsApp(type, number, subtype, note, id, name, phone, place, location));
                if (isSupervisorUser()) secondaryButton("حذف بند التركيب", () -> confirmDeleteInstallation(id, name, phone, place, location));
            }
        } finally {
            c.close();
        }
        if (!any) small("لا توجد تركيبات مسجلة للعميل حتى الآن.");
    }

    private void listInstallationImages(long installationId, String name, String phone, String place, String location) {
        Cursor c = db.raw("SELECT id, uri, IFNULL(stage,''), created_at FROM installation_images WHERE installation_id=? ORDER BY created_at DESC",
                String.valueOf(installationId));
        try {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String uri = c.getString(1);
                String stage = emptyForDb(c.getString(2));
                card(stage.isEmpty() ? "صورة تركيب" : "صورة " + stage,
                        "وقت التصوير/الإضافة: " + ReminderScheduler.formatDate(c.getLong(3)));
                if (looksLikeImage(uri)) imagePreview(uri);
                secondaryButton("فتح الصورة", () -> openAttachment(uri));
                secondaryButton("حذف الصورة", () -> confirmDeleteInstallationImage(id, name, phone, place, location));
            }
        } finally {
            c.close();
        }
    }

    private ArrayList<String> installationImageUris(long installationId) {
        ArrayList<String> images = new ArrayList<>();
        Cursor c = db.raw("SELECT uri FROM installation_images WHERE installation_id=? ORDER BY created_at ASC",
                String.valueOf(installationId));
        try {
            while (c.moveToNext()) {
                String uri = emptyForDb(c.getString(0));
                if (!uri.isEmpty() && looksLikeImage(uri) && !images.contains(uri)) images.add(uri);
            }
        } finally {
            c.close();
        }
        return images;
    }

    private void confirmDeleteInstallation(long id, String name, String phone, String place, String location) {
        new AlertDialog.Builder(this)
                .setTitle("حذف بند التركيب")
                .setMessage("تحب تحذف البند وصوره؟")
                .setPositiveButton("حذف", (dialog, which) -> {
                    db.delete("installation_images", "installation_id=?", String.valueOf(id));
                    db.delete("installation_items", "id=?", String.valueOf(id));
                    afterSave("تم حذف بند التركيب");
                    openCustomerDetails(name, phone, place, location);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void confirmDeleteInstallationImage(long id, String name, String phone, String place, String location) {
        new AlertDialog.Builder(this)
                .setTitle("حذف الصورة")
                .setMessage("تحب تحذف صورة التركيب دي؟")
                .setPositiveButton("حذف", (dialog, which) -> {
                    db.delete("installation_images", "id=?", String.valueOf(id));
                    afterSave("تم حذف الصورة");
                    openCustomerDetails(name, phone, place, location);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void listCustomerAnnualRecords(String table, String dateColumn, String label, String[] args) {
        Cursor c = db.raw("SELECT " + dateColumn + ", total_price, reminder_at FROM " + table +
                " WHERE customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=? ORDER BY created_at DESC", args);
        try {
            while (c.moveToNext()) {
                card(label,
                        "التاريخ: " + ReminderScheduler.formatDate(c.getLong(0)) +
                                "\nالمبلغ: " + money(c.getDouble(1)) +
                                "\nالتذكير: " + ReminderScheduler.formatDate(c.getLong(2)));
            }
        } finally {
            c.close();
        }
    }

    private String[] customerArgs(String name, String phone, String place, String location) {
        return new String[]{name, emptyForDb(phone), emptyForDb(place), emptyForDb(location)};
    }

    private JSONObject customerSnapshot(String name, String phone, String place, String location) throws Exception {
        String[] args = customerArgs(name, phone, place, location);
        JSONObject root = new JSONObject();
        addRows(root, "customers", "SELECT * FROM customers WHERE name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=?", args);
        addRows(root, "extinguishers", "SELECT * FROM extinguishers WHERE customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=?", args);
        addRows(root, "safety_certificates", "SELECT * FROM safety_certificates WHERE customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=?", args);
        addRows(root, "technical_reports", "SELECT * FROM technical_reports WHERE customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=?", args);
        addRows(root, "maintenance_contracts", "SELECT * FROM maintenance_contracts WHERE customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=?", args);
        addRows(root, "customer_attachments", "SELECT * FROM customer_attachments WHERE customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=?", args);
        addRows(root, "extinguisher_images", "SELECT * FROM extinguisher_images WHERE extinguisher_id IN (" +
                "SELECT id FROM extinguishers WHERE customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=?)", args);
        addRows(root, "installation_items", "SELECT * FROM installation_items WHERE customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=?", args);
        addRows(root, "installation_images", "SELECT * FROM installation_images WHERE installation_id IN (" +
                "SELECT id FROM installation_items WHERE customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=?)", args);
        root.put("exported_at", System.currentTimeMillis());
        return root;
    }

    private void addRows(JSONObject root, String key, String sql, String... args) throws Exception {
        JSONArray rows = new JSONArray();
        Cursor c = db.raw(sql, args);
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
        root.put(key, rows);
    }

    private void showExtinguisherEdit(long id) {
        currentTab = "extinguisher_edit";
        clear();
        section("تعديل بيانات الطفايات");
        Cursor c = db.raw("SELECT * FROM extinguishers WHERE id=?", String.valueOf(id));
        try {
            if (!c.moveToFirst()) {
                toast("السجل غير موجود");
                showCustomers();
                return;
            }
            EditText customer = input("اسم العميل", InputType.TYPE_CLASS_TEXT);
            customer.setText(rawVal(c, "customer_name"));
            EditText phone = input("رقم العميل", InputType.TYPE_CLASS_PHONE);
            phone.setText(rawVal(c, "phone"));
            EditText place = input("اسم المكان", InputType.TYPE_CLASS_TEXT);
            place.setText(rawVal(c, "place_name"));
            EditText location = input("اللوكيشن", InputType.TYPE_CLASS_TEXT);
            location.setText(rawVal(c, "location"));
            EditText type = input("نوع الطفاية", InputType.TYPE_CLASS_TEXT);
            type.setText(rawVal(c, "extinguisher_type"));
            quickChoiceBar(type, "نوع الطفاية", extinguisherTypes());
            EditText weight = input("وزن الطفاية", InputType.TYPE_CLASS_TEXT);
            weight.setText(rawVal(c, "weight"));
            quickChoiceBar(weight, "وزن الطفاية", extinguisherWeights());
            EditText count = input("عدد الطفايات", InputType.TYPE_CLASS_NUMBER);
            count.setText(String.valueOf(c.getInt(c.getColumnIndexOrThrow("count"))));
            EditText price = input("إجمالي مبلغ الطفايات", numberType());
            price.setText(cleanNumber(c.getDouble(c.getColumnIndexOrThrow("total_price"))));
            EditText paid = input("المدفوع", numberType());
            paid.setText(cleanNumber(c.getDouble(c.getColumnIndexOrThrow("paid_amount"))));
            EditText deliveredAgain = input("استلم الطفايات تاني؟ نعم/لا", InputType.TYPE_CLASS_TEXT);
            deliveredAgain.setText(yesNoLabel(c.getInt(c.getColumnIndexOrThrow("delivered_again"))));
            pendingExtinguisherImageUri = rawVal(c, "image_uri");
            pendingExtinguisherImageUris.clear();
            pendingExtinguisherImageUris.addAll(extinguisherImages(id, pendingExtinguisherImageUri));
            pendingCameraImageUri = null;
            secondaryButton("إضافة صور من المعرض", () -> chooseExtinguisherImage());
            secondaryButton("تصوير صورة جديدة بالكاميرا", () -> takeExtinguisherPhoto());
            listExtinguisherImages(id, pendingExtinguisherImageUri, () -> showExtinguisherEdit(id));
            EditText date = input("تاريخ الاستيكر yyyy-MM-dd", InputType.TYPE_CLASS_DATETIME);
            date.setText(ReminderScheduler.formatDate(c.getLong(c.getColumnIndexOrThrow("sticker_date"))));
            button("حفظ وإغلاق تعديل الطفايات", () -> {
                if (empty(customer) || empty(count) || empty(price) || empty(date)) return;
                try {
                    long stickerDate = ReminderScheduler.parseDate(txt(date));
                    ContentValues cv = new ContentValues();
                    cv.put("customer_name", txt(customer));
                    cv.put("phone", txt(phone));
                    cv.put("place_name", txt(place));
                    cv.put("location", txt(location));
                    cv.put("extinguisher_type", txt(type));
                    cv.put("weight", txt(weight));
                    cv.put("count", integer(count));
                    cv.put("total_price", dbl(price));
                    cv.put("paid_amount", dbl(paid));
                    cv.put("sticker_date", stickerDate);
                    cv.put("reminder_at", ReminderScheduler.stickerReminder(stickerDate));
                    pendingExtinguisherImageUri = pendingExtinguisherImageUris.isEmpty() ? "" : pendingExtinguisherImageUris.get(0);
                    cv.put("image_uri", pendingExtinguisherImageUri);
                    cv.put("delivered_again", yesNo(txt(deliveredAgain)) ? 1 : 0);
                    db.update("extinguishers", cv, "id=?", String.valueOf(id));
                    db.delete("extinguisher_images", "extinguisher_id=?", String.valueOf(id));
                    saveExtinguisherImages(id, pendingExtinguisherImageUris);
                    saveContactIfEnabled(txt(customer), txt(phone));
                    afterSave("تم حفظ تعديل الطفايات");
                    openCustomerDetails(txt(customer), txt(phone), txt(place), txt(location));
                } catch (Exception ex) {
                    toast("راجع التاريخ، لازم يكون بالشكل yyyy-MM-dd");
                }
            });
            secondaryButton("إغلاق بدون حفظ", this::showCustomers);
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
        EditText place = input("اسم المكان", InputType.TYPE_CLASS_TEXT);
        EditText location = input("اللوكيشن", InputType.TYPE_CLASS_TEXT);
        EditText amount = input("مبلغ/سعر البند", numberType());
        EditText date = input("تاريخ البداية yyyy-MM-dd", InputType.TYPE_CLASS_DATETIME);
        date.setText(today());
        voiceAllButton("قول كل البيانات مرة واحدة", customer, place, location, amount, date);
        button(buttonText, () -> {
            if (empty(customer) || empty(date)) return;
            try {
                long base = ReminderScheduler.parseDate(txt(date));
                long reminder = ReminderScheduler.annualReminder(base);
                ContentValues cv = new ContentValues();
                cv.put("customer_name", txt(customer));
                cv.put("phone", txt(phone));
                cv.put("place_name", txt(place));
                cv.put("location", txt(location));
                cv.put("customer_status", "جديد");
                cv.put("total_price", dbl(amount));
                cv.put(dateColumn, base);
                cv.put("reminder_at", reminder);
                cv.put("created_at", System.currentTimeMillis());
                db.insert(table, cv);
                saveContactIfEnabled(txt(customer), txt(phone));
                afterSave("تم الحفظ والتنبيه يوم " + ReminderScheduler.formatDate(reminder));
                showCertificates();
            } catch (Exception e) {
                toast("راجع التاريخ، لازم يكون بالشكل yyyy-MM-dd");
            }
        });
    }

    private void showInstallations() {
        currentTab = "installations";
        clear();
        section("تركيبات");
        if (isSupervisorUser()) {
            card("إنشاء ملف عميل للتركيبات", "سجل بيانات العميل هنا، وبعد الحفظ هتفتح صفحة العميل وكل التركيبات هتبقى تحت نفس الملف.");
            EditText customer = input("اسم العميل", InputType.TYPE_CLASS_TEXT);
            EditText phone = input("رقم العميل", InputType.TYPE_CLASS_PHONE);
            EditText place = input("اسم المكان", InputType.TYPE_CLASS_TEXT);
            EditText location = input("اللوكيشن", InputType.TYPE_CLASS_TEXT);
            section("بنود التركيب الاختيارية");
            EditText detectorCount = input("عدد الكواشف", numberType());
            EditText detectorType = input("نوع الكاشف", InputType.TYPE_CLASS_TEXT);
            detectorType.setText(firstDetectorType());
            detectorTypeScroll(detectorType);
            EditText breakerCount = input("عدد الكواسر", numberType());
            EditText bellCount = input("عدد الأجراس", numberType());
            EditText extinguisherCount = input("عدد الطفايات", numberType());
            EditText exitCount = input("عدد Exit", numberType());
            EditText panelCount = input("عدد لوحات الإنذار/الحريق", numberType());
            EditText extraType = input("بند إضافي من الإعدادات", InputType.TYPE_CLASS_TEXT);
            EditText extraCount = input("عدد البند الإضافي", numberType());
            installationTypeScroll(extraType);
            EditText note = input("ملاحظات التركيبات", InputType.TYPE_CLASS_TEXT);
            note.setSingleLine(false);
            note.setMinLines(2);
            voiceAllButton("قول بيانات العميل والتركيبات مرة واحدة", customer, phone, place, location,
                    detectorCount, detectorType, breakerCount, bellCount, extinguisherCount, exitCount, panelCount, extraType, extraCount, note);
            button("إنشاء ملف العميل", () -> {
                if (empty(customer)) return;
                String name = txt(customer);
                String customerPhone = txt(phone);
                String customerPlace = txt(place);
                String customerLocation = txt(location);
                saveCustomerIfMissing(name, customerPhone, customerPlace, customerLocation);
                int added = 0;
                added += saveInstallationItems(name, customerPhone, customerPlace, customerLocation, "كاشف", txt(detectorType), parseInt(txt(detectorCount), 0), txt(note));
                added += saveInstallationItems(name, customerPhone, customerPlace, customerLocation, "كاسر", "", parseInt(txt(breakerCount), 0), txt(note));
                added += saveInstallationItems(name, customerPhone, customerPlace, customerLocation, "جرس", "", parseInt(txt(bellCount), 0), txt(note));
                added += saveInstallationItems(name, customerPhone, customerPlace, customerLocation, "طفاية", "", parseInt(txt(extinguisherCount), 0), txt(note));
                added += saveInstallationItems(name, customerPhone, customerPlace, customerLocation, "Exit", "", parseInt(txt(exitCount), 0), txt(note));
                added += saveInstallationItems(name, customerPhone, customerPlace, customerLocation, "لوحة إنذار حريق", "", parseInt(txt(panelCount), 0), txt(note));
                added += saveInstallationItems(name, customerPhone, customerPlace, customerLocation, txt(extraType), "", parseInt(txt(extraCount), 0), txt(note));
                saveContactIfEnabled(name, customerPhone);
                afterSave(added > 0 ? "تم إنشاء ملف العميل وحفظ " + added + " بند تركيب" : "تم إنشاء ملف العميل");
                openCustomerDetails(name, customerPhone, customerPlace, customerLocation);
            });
        } else {
            small("التركيبات بتتسجل من داخل ملف العميل. افتح العميل واضغط إضافة تركيب، وكل البنود والصور هتكون تحت نفس العميل.");
        }
        button("فتح العملاء", this::showCustomers);
        secondaryButton("تعديل بنود التركيبات من الإعدادات", this::showSettings);
        section("عملاء عندهم تركيبات");
        Cursor c = db.raw("SELECT customer_name, IFNULL(phone,''), IFNULL(place_name,''), IFNULL(location,''), COUNT(*) " +
                "FROM installation_items GROUP BY customer_name, IFNULL(phone,''), IFNULL(place_name,''), IFNULL(location,'') ORDER BY MAX(created_at) DESC");
        boolean any = false;
        try {
            while (c.moveToNext()) {
                any = true;
                String name = c.getString(0);
                String itemPhone = c.getString(1);
                String itemPlace = c.getString(2);
                String itemLocation = c.getString(3);
                card(name,
                        "عدد بنود التركيب: " + c.getInt(4) +
                                "\nرقم: " + safe(itemPhone) +
                                "\nاسم المكان: " + safe(itemPlace) +
                                "\n" + customerInstallationSummaryText(name, itemPhone, itemPlace, itemLocation));
                secondaryButton("فتح ملف العميل", () -> openCustomerDetails(name, itemPhone, itemPlace, itemLocation));
            }
        } finally {
            c.close();
        }
        if (!any) small("لا توجد تركيبات مسجلة. افتح ملف العميل واضغط إضافة تركيب.");
    }

    private void showMaintenance() {
        currentTab = "maintenance";
        clear();
        section("عقود الصيانة");
        if (isSupervisorUser()) {
            card("إنشاء عقد صيانة", "سجل بيانات العميل والعقد هنا، وبعد الحفظ هتفتح صفحة العميل والعقد يكون تحت ملفه.");
            EditText customer = input("اسم العميل", InputType.TYPE_CLASS_TEXT);
            EditText phone = input("رقم العميل", InputType.TYPE_CLASS_PHONE);
            EditText place = input("اسم المكان", InputType.TYPE_CLASS_TEXT);
            EditText location = input("اللوكيشن", InputType.TYPE_CLASS_TEXT);
            EditText start = input("تاريخ بداية العقد yyyy-MM-dd", InputType.TYPE_CLASS_DATETIME);
            start.setText(today());
            EditText durationMonths = input("مدة العقد بالشهور", numberType());
            durationMonths.setText("12");
            EditText extinguisherCount = input("عدد الطفايات في العقد", numberType());
            EditText detectorCount = input("عدد الكواشف", numberType());
            EditText bellCount = input("عدد الأجراس", numberType());
            EditText breakerCount = input("عدد الكواسر", numberType());
            EditText exitCount = input("عدد Exit", numberType());
            EditText panelCount = input("عدد لوحات الإنذار/الحريق", numberType());
            EditText note = input("تفاصيل وملاحظات العقد", InputType.TYPE_CLASS_TEXT);
            note.setSingleLine(false);
            note.setMinLines(2);
            voiceAllButton("قول بيانات عقد الصيانة مرة واحدة", customer, phone, place, location, start, durationMonths,
                    extinguisherCount, detectorCount, bellCount, breakerCount, exitCount, panelCount, note);
            button("إنشاء عقد الصيانة", () -> {
                if (empty(customer) || empty(start)) return;
                try {
                    String name = txt(customer);
                    String customerPhone = txt(phone);
                    String customerPlace = txt(place);
                    String customerLocation = txt(location);
                    long startDate = ReminderScheduler.parseDate(txt(start));
                    long visit = ReminderScheduler.addMonthsAvoidWeekend(startDate, 3);
                    long reminder = ReminderScheduler.maintenanceReminder(visit);
                    saveCustomerIfMissing(name, customerPhone, customerPlace, customerLocation);
                    ContentValues cv = new ContentValues();
                    cv.put("customer_name", name);
                    cv.put("phone", customerPhone);
                    cv.put("place_name", customerPlace);
                    cv.put("location", customerLocation);
                    cv.put("customer_status", customerStatus(name, customerPhone, customerPlace, customerLocation));
                    cv.put("start_date", startDate);
                    cv.put("next_visit_at", visit);
                    cv.put("reminder_at", reminder);
                    cv.put("extinguisher_count", parseInt(txt(extinguisherCount), 0));
                    cv.put("detector_count", parseInt(txt(detectorCount), 0));
                    cv.put("bell_count", parseInt(txt(bellCount), 0));
                    cv.put("breaker_count", parseInt(txt(breakerCount), 0));
                    cv.put("exit_count", parseInt(txt(exitCount), 0));
                    cv.put("panel_count", parseInt(txt(panelCount), 0));
                    cv.put("note", "مدة العقد: " + parseInt(txt(durationMonths), 12) + " شهر\n" + txt(note));
                    cv.put("created_at", System.currentTimeMillis());
                    db.insert("maintenance_contracts", cv);
                    saveContactIfEnabled(name, customerPhone);
                    afterSave("تم إنشاء عقد الصيانة");
                    openCustomerDetails(name, customerPhone, customerPlace, customerLocation);
                } catch (Exception e) {
                    toast("راجع التاريخ، لازم يكون بالشكل yyyy-MM-dd");
                }
            });
        } else {
            small("العقد بيتسجل ويتعرض داخل ملف العميل نفسه. افتح العميل واضغط إضافة عقد صيانة.");
        }
        button("فتح العملاء", this::showCustomers);
        section("عملاء عندهم عقود صيانة");
        Cursor c = db.raw("SELECT customer_name, IFNULL(phone,''), IFNULL(place_name,''), IFNULL(location,''), " +
                "COUNT(*), MIN(next_visit_at) FROM maintenance_contracts " +
                "GROUP BY customer_name, IFNULL(phone,''), IFNULL(place_name,''), IFNULL(location,'') ORDER BY MIN(next_visit_at)");
        boolean any = false;
        try {
            while (c.moveToNext()) {
                any = true;
                String name = c.getString(0);
                String itemPhone = c.getString(1);
                String itemPlace = c.getString(2);
                String itemLocation = c.getString(3);
                card(name,
                        "عدد العقود: " + c.getInt(4) +
                                "\nأقرب زيارة: " + ReminderScheduler.formatDate(c.getLong(5)) +
                                "\nرقم: " + itemPhone +
                                "\nاسم المكان: " + itemPlace +
                                "\nلوكيشن: " + itemLocation +
                                "\n" + customerMaintenanceSummaryText(name, itemPhone, itemPlace, itemLocation));
                secondaryButton("فتح ملف العميل", () -> openCustomerDetails(name, itemPhone, itemPlace, itemLocation));
            }
        } finally {
            c.close();
        }
        if (!any) small("لا توجد عقود صيانة. افتح ملف العميل واضغط إضافة عقد صيانة.");
    }

    private void showAddMaintenanceForCustomer(String name, String phone, String place, String location) {
        currentTab = "maintenance_add";
        clear();
        section("إضافة عقد صيانة للعميل");
        card(name, "العقد هيظهر داخل صفحة العميل، والتنبيه قبل الزيارة بـ 5 أيام. لو الزيارة وقعت جمعة أو سبت التطبيق يزحزحها تلقائيا.");
        EditText start = input("تاريخ بداية العقد yyyy-MM-dd", InputType.TYPE_CLASS_DATETIME);
        start.setText(today());
        EditText extinguisherCount = input("عدد الطفايات في العقد", numberType());
        EditText detectorCount = input("عدد الكواشف", numberType());
        EditText bellCount = input("عدد الأجراس", numberType());
        EditText breakerCount = input("عدد الكواسر", numberType());
        EditText exitCount = input("عدد Exit", numberType());
        EditText panelCount = input("عدد لوحات الإنذار/الحريق", numberType());
        EditText note = input("تفاصيل وملاحظات العقد", InputType.TYPE_CLASS_TEXT);
        note.setSingleLine(false);
        note.setMinLines(2);
        voiceAllButton("قول بيانات عقد الصيانة مرة واحدة", start,
                extinguisherCount, detectorCount, bellCount, breakerCount, exitCount, panelCount, note);
        button("حفظ عقد الصيانة داخل العميل", () -> {
            if (empty(start)) return;
            try {
                long startDate = ReminderScheduler.parseDate(txt(start));
                long visit = ReminderScheduler.addMonthsAvoidWeekend(startDate, 3);
                long reminder = ReminderScheduler.maintenanceReminder(visit);
                saveCustomerIfMissing(name, phone, place, location);
                ContentValues cv = new ContentValues();
                cv.put("customer_name", name);
                cv.put("phone", emptyForDb(phone));
                cv.put("place_name", emptyForDb(place));
                cv.put("location", emptyForDb(location));
                cv.put("customer_status", customerStatus(name, phone, place, location));
                cv.put("start_date", startDate);
                cv.put("next_visit_at", visit);
                cv.put("reminder_at", reminder);
                cv.put("extinguisher_count", parseInt(txt(extinguisherCount), 0));
                cv.put("detector_count", parseInt(txt(detectorCount), 0));
                cv.put("bell_count", parseInt(txt(bellCount), 0));
                cv.put("breaker_count", parseInt(txt(breakerCount), 0));
                cv.put("exit_count", parseInt(txt(exitCount), 0));
                cv.put("panel_count", parseInt(txt(panelCount), 0));
                cv.put("note", txt(note));
                cv.put("created_at", System.currentTimeMillis());
                db.insert("maintenance_contracts", cv);
                afterSave("تم حفظ عقد الصيانة داخل العميل");
                openCustomerDetails(name, phone, place, location);
            } catch (Exception e) {
                toast("راجع التاريخ، لازم يكون بالشكل yyyy-MM-dd");
            }
        });
        secondaryButton("رجوع لصفحة العميل", () -> openCustomerDetails(name, phone, place, location));
    }

    private String maintenanceDetailsText(Cursor c) {
        return "\nتفاصيل العقد:" +
                "\nطفايات: " + intVal(c, "extinguisher_count") +
                " | كواشف: " + intVal(c, "detector_count") +
                " | أجراس: " + intVal(c, "bell_count") +
                "\nكواسر: " + intVal(c, "breaker_count") +
                " | Exit: " + intVal(c, "exit_count") +
                " | لوحات: " + intVal(c, "panel_count") +
                "\nملاحظات: " + safe(rawVal(c, "note"));
    }

    private void showMaintenanceEdit(long id) {
        currentTab = "maintenance_edit";
        clear();
        section("تعديل عقد الصيانة");
        Cursor c = db.raw("SELECT * FROM maintenance_contracts WHERE id=?", String.valueOf(id));
        try {
            if (!c.moveToFirst()) {
                small("العقد غير موجود.");
                secondaryButton("رجوع", this::showMaintenance);
                return;
            }
            EditText customer = input("اسم العميل", InputType.TYPE_CLASS_TEXT);
            customer.setText(rawVal(c, "customer_name"));
            EditText phone = input("رقم العميل", InputType.TYPE_CLASS_PHONE);
            phone.setText(rawVal(c, "phone"));
            EditText place = input("اسم المكان", InputType.TYPE_CLASS_TEXT);
            place.setText(rawVal(c, "place_name"));
            EditText location = input("اللوكيشن", InputType.TYPE_CLASS_TEXT);
            location.setText(rawVal(c, "location"));
            EditText start = input("تاريخ بداية العقد yyyy-MM-dd", InputType.TYPE_CLASS_DATETIME);
            start.setText(ReminderScheduler.formatDate(c.getLong(c.getColumnIndexOrThrow("start_date"))));
            EditText extinguisherCount = input("عدد الطفايات في العقد", numberType());
            extinguisherCount.setText(String.valueOf(intVal(c, "extinguisher_count")));
            EditText detectorCount = input("عدد الكواشف", numberType());
            detectorCount.setText(String.valueOf(intVal(c, "detector_count")));
            EditText bellCount = input("عدد الأجراس", numberType());
            bellCount.setText(String.valueOf(intVal(c, "bell_count")));
            EditText breakerCount = input("عدد الكواسر", numberType());
            breakerCount.setText(String.valueOf(intVal(c, "breaker_count")));
            EditText exitCount = input("عدد Exit", numberType());
            exitCount.setText(String.valueOf(intVal(c, "exit_count")));
            EditText panelCount = input("عدد لوحات الإنذار/الحريق", numberType());
            panelCount.setText(String.valueOf(intVal(c, "panel_count")));
            EditText note = input("تفاصيل وملاحظات العقد", InputType.TYPE_CLASS_TEXT);
            note.setSingleLine(false);
            note.setMinLines(2);
            note.setText(rawVal(c, "note"));
            voiceAllButton("قول تعديل العقد مرة واحدة", customer, place, location, start,
                    extinguisherCount, detectorCount, bellCount, breakerCount, exitCount, panelCount, note);
            button("حفظ تعديل العقد", () -> {
                if (empty(customer) || empty(start)) return;
                try {
                    long startDate = ReminderScheduler.parseDate(txt(start));
                    long visit = ReminderScheduler.addMonthsAvoidWeekend(startDate, 3);
                    long reminder = ReminderScheduler.maintenanceReminder(visit);
                    ContentValues cv = new ContentValues();
                    cv.put("customer_name", txt(customer));
                    cv.put("phone", txt(phone));
                    cv.put("place_name", txt(place));
                    cv.put("location", txt(location));
                    cv.put("customer_status", customerStatus(txt(customer), txt(phone), txt(place), txt(location)));
                    cv.put("start_date", startDate);
                    cv.put("next_visit_at", visit);
                    cv.put("reminder_at", reminder);
                    cv.put("extinguisher_count", parseInt(txt(extinguisherCount), 0));
                    cv.put("detector_count", parseInt(txt(detectorCount), 0));
                    cv.put("bell_count", parseInt(txt(bellCount), 0));
                    cv.put("breaker_count", parseInt(txt(breakerCount), 0));
                    cv.put("exit_count", parseInt(txt(exitCount), 0));
                    cv.put("panel_count", parseInt(txt(panelCount), 0));
                    cv.put("note", txt(note));
                    db.update("maintenance_contracts", cv, "id=?", String.valueOf(id));
                    saveCustomerIfMissing(txt(customer), txt(phone), txt(place), txt(location));
                    saveContactIfEnabled(txt(customer), txt(phone));
                    afterSave("تم حفظ تعديل عقد الصيانة");
                    openCustomerDetails(txt(customer), txt(phone), txt(place), txt(location));
                } catch (Exception e) {
                    toast("راجع التاريخ، لازم يكون بالشكل yyyy-MM-dd");
                }
            });
            secondaryButton("إلغاء والرجوع لصفحة العميل", () -> openCustomerDetails(txt(customer), txt(phone), txt(place), txt(location)));
        } finally {
            c.close();
        }
    }

    private void showMonthlyReport() {
        currentTab = "report";
        clear();
        if (isTechnicianUser()) {
            section("غير متاح للفني");
            small("التقارير العامة تظهر للمشرف فقط. الفني يرى العملاء المحولين له من صفحة العملاء.");
            button("العملاء المحولون", this::showCustomers);
            return;
        }
        section("تقرير الشهر الحالي");
        button("تصدير Excel الشهر", this::exportMonthlyExcel);
        button("تصدير تقرير الشهر PDF", this::exportMonthlyPdf);
        long[] range = monthRange();
        Cursor c = db.raw("SELECT COALESCE(SUM(count),0), COALESCE(SUM(total_price),0), COALESCE(SUM(IFNULL(paid_amount,0)),0) FROM extinguishers " +
                "WHERE created_at BETWEEN ? AND ?", String.valueOf(range[0]), String.valueOf(range[1]));
        try {
            if (c.moveToFirst()) {
                int count = c.getInt(0);
                double total = c.getDouble(1);
                double paid = c.getDouble(2);
                double pct = db.settingDouble("extinguisher_percent", 25);
                card("عدد الطفايات هذا الشهر", count + " طفاية");
                card("إجمالي مبلغ الطفايات", money(total) +
                        "\nالمدفوع: " + money(paid) +
                        "\nالمتبقي: " + money(Math.max(0, total - paid)));
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

    private double monthlyShareTotal(long[] range) {
        double extinguisherTotal = monthlyTableTotal("extinguishers", range);
        double certificateTotal = monthlyTableTotal("safety_certificates", range);
        double reportTotal = monthlyTableTotal("technical_reports", range);
        return extinguisherTotal * db.settingDouble("extinguisher_percent", 25) / 100.0 +
                certificateTotal * db.settingDouble("certificate_percent", 0) / 100.0 +
                reportTotal * db.settingDouble("report_percent", 0) / 100.0;
    }

    private double monthlyTableTotal(String table, long[] range) {
        Cursor c = db.raw("SELECT COALESCE(SUM(total_price),0) FROM " + table +
                        " WHERE created_at BETWEEN ? AND ?",
                String.valueOf(range[0]), String.valueOf(range[1]));
        try {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        } finally {
            c.close();
        }
    }

    private void showAlerts() {
        currentTab = "alerts";
        clear();
        if (isTechnicianUser()) {
            section("غير متاح للفني");
            small("التنبيهات العامة تظهر للمشرف فقط. الفني يرى العملاء المحولين له من صفحة العملاء.");
            button("العملاء المحولون", this::showCustomers);
            return;
        }
        section("تنبيهات قريبة");
        long now = System.currentTimeMillis();
        long soon = now + 30L * 24L * 60L * 60L * 1000L;
        small("يعرض المتأخر والجاي خلال 30 يوم للطفايات والشهادات وعقود الصيانة.");
        listAlerts("طفايات", "extinguishers", "sticker_date", now, soon);
        listAlerts("شهادات السلامة", "safety_certificates", "certificate_date", now, soon);
        listMaintenanceAlerts(now, soon);
    }

    private void listAlerts(String label, String table, String dateColumn, long now, long soon) {
        Cursor c = db.raw("SELECT customer_name, phone, place_name, location, " + dateColumn + ", reminder_at FROM " + table +
                        " WHERE reminder_at<=? ORDER BY reminder_at ASC",
                String.valueOf(soon));
        try {
            while (c.moveToNext()) {
                long reminder = c.getLong(5);
                card(label + " - " + c.getString(0),
                        "الحالة: " + alertState(reminder, now) +
                                "\nتاريخ البند: " + ReminderScheduler.formatDate(c.getLong(4)) +
                                "\nالتذكير: " + ReminderScheduler.formatDate(reminder) +
                                "\nرقم: " + safe(c.getString(1)) +
                                "\nاسم المكان: " + safe(c.getString(2)) +
                                "\nلوكيشن: " + safe(c.getString(3)));
            }
        } finally {
            c.close();
        }
    }

    private void listMaintenanceAlerts(long now, long soon) {
        Cursor c = db.raw("SELECT customer_name, phone, place_name, location, next_visit_at, reminder_at FROM maintenance_contracts " +
                        "WHERE reminder_at<=? ORDER BY reminder_at ASC",
                String.valueOf(soon));
        try {
            while (c.moveToNext()) {
                long reminder = c.getLong(5);
                card("عقد صيانة - " + c.getString(0),
                        "الحالة: " + alertState(reminder, now) +
                                "\nالزيارة القادمة: " + ReminderScheduler.formatDate(c.getLong(4)) +
                                "\nالتذكير: " + ReminderScheduler.formatDate(reminder) +
                                "\nرقم: " + safe(c.getString(1)) +
                                "\nاسم المكان: " + safe(c.getString(2)) +
                                "\nلوكيشن: " + safe(c.getString(3)));
            }
        } finally {
            c.close();
        }
    }

    private String alertState(long reminder, long now) {
        return reminder < now ? "متأخر" : "قريب";
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

        section("أنواع التركيبات");
        EditText installationTypes = input("أنواع التركيبات", InputType.TYPE_CLASS_TEXT);
        installationTypes.setSingleLine(false);
        installationTypes.setMinLines(7);
        installationTypes.setText(db.setting("installation_types", defaultInstallationTypesText()));
        small("اكتب كل نوع في سطر لوحده. الأنواع دي تظهر للفني كأزرار سريعة في صفحة إضافة تركيب.");
        button("حفظ أنواع التركيبات", () -> {
            db.setSetting("installation_types", txt(installationTypes).trim().isEmpty() ? defaultInstallationTypesText() : txt(installationTypes));
            afterSave("تم حفظ أنواع التركيبات");
            showSettings();
        });

        section("النسخ الاحتياطي المحلي");
        card("نسخة تلقائية على الموبايل",
                "بعد كل حفظ التطبيق بيحدث نسخة احتياطية تلقائيا.\nآخر ملف: " + LocalBackupManager.latestPath(this));
        button("عمل نسخة احتياطية الآن", () -> {
            try {
                String path = LocalBackupManager.backupNow(this, db);
                toast("تم حفظ النسخة: " + path);
            } catch (Exception e) {
                toast("تعذر حفظ النسخة الاحتياطية");
            }
        });
        secondaryButton("استرجاع آخر نسخة محلية", () -> {
            try {
                LocalBackupManager.restoreLatest(this, db);
                ReminderScheduler.scheduleAll(this, db);
                sync.autoUploadQuietly();
                toast("تم استرجاع آخر نسخة محلية");
                showHome();
            } catch (Exception e) {
                toast("لا توجد نسخة محلية صالحة للاسترجاع");
            }
        });

        section("جهات الاتصال");
        boolean contactsEnabled = "1".equals(db.setting("auto_save_contacts", "0"));
        card("حفظ العملاء في جهات الاتصال",
                contactsEnabled ? "شغال: أي عميل جديد باسمه ورقمه يتحفظ باسم العميل - عميل طفايات لو الرقم مش محفوظ." :
                        "مقفول: فعل الاختيار لو عايز العملاء الجدد يتحفظوا تلقائيا على الموبايل.");
        button(contactsEnabled ? "إيقاف حفظ جهات الاتصال" : "تشغيل حفظ جهات الاتصال", () -> {
            if (contactsEnabled) {
                db.setSetting("auto_save_contacts", "0");
                afterSave("تم إيقاف حفظ جهات الاتصال");
                showSettings();
            } else {
                enableContactSaving();
            }
        });
        small("الحفظ يتم فقط لو اسم العميل ورقم الموبايل موجودين مع بعض، ولو الرقم محفوظ قبل كده مش هيتكرر.");

        section("رسائل واتساب");
        String[] templates = whatsappTemplates();
        int selected = selectedWhatsappTemplateIndex(templates.length);
        for (int i = 0; i < templates.length; i++) {
            card((i + 1) + (i == selected ? " - الرسالة المختارة" : " - رسالة واتساب"),
                    templates[i].replace("{name}", "اسم العميل").replace("{count}", "عدد الطفايات"));
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
        small("استخدم {name} لاسم العميل و {count} لعدد الطفايات داخل الرسالة.");
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
        syncAppVersionState();
        if (user == null) {
            small("سجل بحساب Google علشان التطبيق يحفظ ويرجع بياناتك تلقائيا على Firebase.");
            button("تسجيل بحساب Google", () -> sync.signIn(this));
        } else {
            card("الحساب الحالي", safe(user.getEmail()));
            button("رفع نسخة الآن", () -> sync.upload(this::showSync));
            button("استرجاع من Google", () -> sync.restore(this::showSync));
            button("تسجيل خروج", () -> sync.signOut(this::showSync));
        }

        section("فريق العمل");
        EditText teamCode = input("كود الفريق المشترك", InputType.TYPE_CLASS_TEXT);
        teamCode.setText(db.setting("team_code", ""));
        small(isSupervisorUser()
                ? "أنت مشرف. تقدر تحول عملاء للفريق وتستلم تحديثاتهم."
                : "أنت فني. سيظهر لك فقط الشغل الذي يحوله المشرف لهذا الكود.");
        button("حفظ كود الفريق", () -> {
            db.setSetting("team_code", txt(teamCode).trim().replace("/", "_"));
            afterSave("تم حفظ كود الفريق");
            showSync();
        });
        if (user != null) {
            if (isSupervisorUser()) {
                button("رفع بياناتي للفريق", () -> {
                    db.setSetting("team_code", txt(teamCode).trim().replace("/", "_"));
                    sync.uploadTeam(txt(teamCode), this::showSync);
                });
                button("استرجاع بيانات الفريق", () -> {
                    db.setSetting("team_code", txt(teamCode).trim().replace("/", "_"));
                    sync.restoreTeam(txt(teamCode), this::showSync);
                });
            }
            button("استلام التكليفات المرسلة للكود", () -> {
                db.setSetting("team_code", txt(teamCode).trim().replace("/", "_"));
                sync.restoreAssignments(txt(teamCode), this::showCustomers);
            });
            if (isSupervisorUser()) {
                button("استلام من الفنيين", () -> {
                    db.setSetting("team_code", txt(teamCode).trim().replace("/", "_"));
                    showTeamInbox();
                });
            }
        } else {
            small("بعد تسجيل الدخول بحساب Google ستظهر أزرار المشرف أو أزرار الفني حسب الإيميل.");
        }
    }

    private void showTeamInbox() {
        currentTab = "team_inbox";
        clear();
        section("استلام من الفنيين");
        if (!isSupervisorUser()) {
            small("هذه الصفحة للمشرف فقط.");
            secondaryButton("رجوع", this::showHome);
            return;
        }
        FirebaseUser user = sync.user();
        if (user == null) {
            small("سجل بحساب Google الأول علشان تستلم تحديثات الفنيين.");
            button("تسجيل بحساب Google", () -> sync.signIn(this));
            return;
        }
        EditText teamCode = input("كود الفريق", InputType.TYPE_CLASS_TEXT);
        teamCode.setText(db.setting("team_code", ""));
        button("تحديث القائمة", () -> {
            db.setSetting("team_code", txt(teamCode).trim().replace("/", "_"));
            loadTeamInbox(txt(teamCode));
        });
        small("اكتب كود الفريق واضغط تحديث القائمة. الصفحة لا تحمل الصور تلقائيا عشان تفضل سريعة وثابتة.");
    }

    private void loadTeamInbox(String teamCode) {
        String code = emptyForDb(teamCode).trim().replace("/", "_");
        if (code.isEmpty()) {
            small("اكتب كود الفريق ثم اضغط تحديث القائمة.");
            return;
        }
        sync.fetchCompletedAssignments(code, new SyncManager.CompletedAssignmentsListener() {
            @Override
            public void onLoaded(List<SyncManager.CompletedAssignment> assignments) {
                runOnUiThread(() -> {
                    try {
                        showTeamInboxResults(code, assignments);
                    } catch (Exception e) {
                        toast("تعذر عرض قائمة الفنيين");
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> toast("فشل تحميل تحديثات الفنيين: " + safe(message)));
            }
        });
    }

    private void showTeamInboxResults(String teamCode, List<SyncManager.CompletedAssignment> assignments) {
        currentTab = "team_inbox";
        clear();
        section("استلام من الفنيين");
        card("كود الفريق", teamCode);
        if (assignments == null || assignments.isEmpty()) {
            card("لا توجد تكليفات", "مافيش تكليفات مسجلة على كود الفريق حاليا.");
            secondaryButton("رجوع", this::showHome);
            return;
        }
        int visible = 0;
        for (SyncManager.CompletedAssignment item : assignments) {
            if ("open".equals(emptyForDb(item.status)) && item.rejectedAt > 0) continue;
            visible++;
            card(emptyForDb(item.customerName).isEmpty() ? "تحديث فني" : item.customerName,
                    assignmentReviewText(item));
            if (!emptyForDb(item.completedSnapshot).isEmpty()) {
                actionButton("مشاركة للجروب واتساب", Color.rgb(22, 163, 74), R.drawable.ic_action_whatsapp,
                        () -> shareAssignmentToWhatsAppGroup(item));
            }
            ArrayList<String> images = snapshotImageUris(item.completedSnapshot);
            if (!images.isEmpty()) {
                small("صور الفني: " + images.size());
                for (int i = 0; i < images.size(); i++) {
                    String uri = images.get(i);
                    secondaryButton("فتح صورة الفني " + (i + 1), () -> openAttachment(uri));
                }
            }
            showSnapshotInstallations(item.completedSnapshot, item.customerName, item.phone, item.place, item.location);
            String status = emptyForDb(item.status);
            if ("completed".equals(status)) {
                actionButton("اعتماد وإضافته عندي", Color.rgb(22, 163, 74), () ->
                        sync.approveCompletedAssignment(item, () -> loadTeamInbox(teamCode)));
                secondaryButton("رفض التحديث", () -> confirmRejectCompletedAssignment(item, teamCode));
            } else if ("open".equals(status)) {
                small("التكليف لسه عند الفني ولم يتم تسليمه للمشرف.");
            } else if ("supervisor_received".equals(status)) {
                small("تم اعتماد هذا التحديث سابقا.");
            } else if ("supervisor_rejected".equals(status)) {
                small("تم رفض هذا التحديث سابقا.");
            }
        }
        if (visible == 0) {
            card("لا توجد تحديثات للمراجعة", "أي تحديث مرفوض يرجع للفنيين للتعديل ولن يظهر هنا إلا بعد تسليمه مرة أخرى.");
        }
        secondaryButton("رجوع", this::showHome);
    }

    private void confirmRejectCompletedAssignment(SyncManager.CompletedAssignment item, String teamCode) {
        new AlertDialog.Builder(this)
                .setTitle("رفض تحديث الفني")
                .setMessage("هل تريد رفض التحديث بدون إضافته عندك؟")
                .setPositiveButton("رفض", (dialog, which) ->
                        sync.rejectCompletedAssignment(item, () -> loadTeamInbox(teamCode)))
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showSnapshotInstallations(String rawSnapshot, String customerName, String phone, String place, String location) {
        try {
            JSONObject root = new JSONObject(emptyForDb(rawSnapshot).isEmpty() ? "{}" : rawSnapshot);
            JSONArray items = root.optJSONArray("installation_items");
            if (items == null || items.length() == 0) return;
            small("تركيبات الفني:");
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                long id = item.optLong("id", -1);
                String type = item.optString("item_type", "-");
                int number = item.optInt("item_number", i + 1);
                String subtype = item.optString("subtype", "");
                String note = item.optString("note", "");
                card(type + " رقم " + number,
                        "النوع: " + blankDash(subtype) +
                                "\nملاحظات: " + blankDash(note) +
                                "\nصور البند: " + snapshotInstallationImages(root, id).size());
                ArrayList<String> images = snapshotInstallationImages(root, id);
                for (int p = 0; p < images.size(); p++) {
                    String uri = images.get(p);
                    secondaryButton("فتح صورة " + type + " " + (p + 1), () -> openAttachment(uri));
                }
                secondaryButton("مشاركة " + type + " واتساب", () ->
                        shareSnapshotInstallationToWhatsApp(type, number, subtype, note, images, customerName, phone, place, location));
            }
        } catch (Exception ignored) {
        }
    }

    private ArrayList<String> snapshotInstallationImages(JSONObject root, long installationId) {
        ArrayList<String> images = new ArrayList<>();
        JSONArray rows = root.optJSONArray("installation_images");
        if (rows == null) return images;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            if (installationId > 0 && row.optLong("installation_id", -2) != installationId) continue;
            String uri = row.optString("uri", "");
            if (!emptyForDb(uri).isEmpty() && looksLikeImage(uri) && !images.contains(uri)) images.add(uri);
        }
        return images;
    }

    private void shareSnapshotInstallationToWhatsApp(String type, int number, String subtype, String note, ArrayList<String> images,
                                                     String name, String phone, String place, String location) {
        StringBuilder out = new StringBuilder();
        out.append("تقرير بند تركيب\n");
        out.append("العميل: ").append(safe(name)).append("\n");
        out.append("الرقم: ").append(safe(phone)).append("\n");
        out.append("اسم المكان: ").append(safe(place)).append("\n");
        if (!emptyForDb(location).isEmpty()) out.append("اللوكيشن: ").append(location).append("\n");
        out.append("\n").append(type).append(" رقم ").append(number).append("\n");
        if (!emptyForDb(subtype).isEmpty()) out.append("النوع: ").append(subtype).append("\n");
        if (!emptyForDb(note).isEmpty()) out.append("ملاحظات: ").append(note).append("\n");
        out.append("تم التنفيذ، مرفق الصور.");
        shareTextAndImagesToWhatsApp(out.toString(), images, type + " رقم " + number);
    }

    private String assignmentReviewText(SyncManager.CompletedAssignment item) {
        StringBuilder out = new StringBuilder();
        out.append("الفني: ").append(safe(item.completedByEmail)).append("\n");
        out.append("حالة التكليف: ").append(assignmentStatusLabel(item.status)).append("\n");
        if (item.completedAt > 0) out.append("وقت الإرسال: ").append(ReminderScheduler.formatDate(item.completedAt)).append("\n");
        out.append("رقم: ").append(safe(item.phone)).append("\n");
        out.append("اسم المكان: ").append(safe(item.place)).append("\n");
        out.append("اللوكيشن: ").append(safe(item.location)).append("\n\n");
        if ("open".equals(emptyForDb(item.status))) {
            out.append("البيانات المرسلة للفني:\n");
            try {
                JSONObject sent = new JSONObject(emptyForDb(item.originalSnapshot).isEmpty() ? "{}" : item.originalSnapshot);
                out.append("عدد الطفايات: ").append(blankDash(snapshotExtinguisherValue(sent, "count"))).append("\n");
                out.append("المبلغ: ").append(blankDash(snapshotExtinguisherValue(sent, "total_price"))).append("\n");
                out.append("الوزن: ").append(blankDash(snapshotExtinguisherValue(sent, "weight"))).append("\n");
                out.append("نوع الطفاية: ").append(blankDash(snapshotExtinguisherValue(sent, "type"))).append("\n");
            } catch (Exception ignored) {
            }
            return out.toString();
        }
        out.append("مراجعة التعديل:\n");
        try {
            JSONObject before = new JSONObject(emptyForDb(item.originalSnapshot).isEmpty() ? "{}" : item.originalSnapshot);
            JSONObject after = new JSONObject(emptyForDb(item.completedSnapshot).isEmpty() ? "{}" : item.completedSnapshot);
            int changes = 0;
            changes += appendSnapshotDiff(out, before, after, "count", "عدد الطفايات");
            changes += appendSnapshotDiff(out, before, after, "total_price", "المبلغ");
            changes += appendSnapshotDiff(out, before, after, "weight", "الوزن");
            changes += appendSnapshotDiff(out, before, after, "type", "نوع الطفاية");
            changes += appendMaintenanceSnapshotDiff(out, before, after);
            if (changes == 0) out.append("لا يوجد تعديل في الطفايات أو تفاصيل عقد الصيانة.\n");
            out.append("صور الفني: ").append(snapshotImageUris(item.completedSnapshot).size()).append("\n");
            out.append("بنود التركيبات: ").append(snapshotArrayCount(after, "installation_items")).append("\n");
        } catch (Exception e) {
            out.append("تعذر قراءة تفاصيل التعديل.");
        }
        return out.toString();
    }

    private int appendMaintenanceSnapshotDiff(StringBuilder out, JSONObject before, JSONObject after) {
        int changes = 0;
        changes += appendMaintenanceValueDiff(out, before, after, "extinguisher_count", "عقد الصيانة - طفايات");
        changes += appendMaintenanceValueDiff(out, before, after, "detector_count", "عقد الصيانة - كواشف");
        changes += appendMaintenanceValueDiff(out, before, after, "bell_count", "عقد الصيانة - أجراس");
        changes += appendMaintenanceValueDiff(out, before, after, "breaker_count", "عقد الصيانة - كواسر");
        changes += appendMaintenanceValueDiff(out, before, after, "exit_count", "عقد الصيانة - Exit");
        changes += appendMaintenanceValueDiff(out, before, after, "panel_count", "عقد الصيانة - لوحات");
        changes += appendMaintenanceValueDiff(out, before, after, "note", "عقد الصيانة - ملاحظات");
        return changes;
    }

    private int appendMaintenanceValueDiff(StringBuilder out, JSONObject before, JSONObject after, String key, String label) {
        String oldValue = snapshotMaintenanceValue(before, key);
        String newValue = snapshotMaintenanceValue(after, key);
        if (oldValue.equals(newValue)) return 0;
        out.append(label).append(": ").append(oldValue.isEmpty() ? "-" : oldValue)
                .append(" -> ").append(newValue.isEmpty() ? "-" : newValue).append("\n");
        return 1;
    }

    private String snapshotMaintenanceValue(JSONObject root, String key) {
        try {
            JSONArray rows = root.optJSONArray("maintenance_contracts");
            if (rows == null || rows.length() == 0) return "";
            Object value = rows.getJSONObject(0).opt(key);
            return value == null || value == JSONObject.NULL ? "" : String.valueOf(value);
        } catch (Exception e) {
            return "";
        }
    }

    private void shareAssignmentToWhatsAppGroup(SyncManager.CompletedAssignment item) {
        String message = buildAssignmentGroupMessage(item);
        ArrayList<String> images = snapshotImageUris(!emptyForDb(item.completedSnapshot).isEmpty() ? item.completedSnapshot : item.originalSnapshot);
        shareTextAndImagesToWhatsApp(message, images, "تقرير التنفيذ");
    }

    private void shareInstallationToWhatsApp(String type, int number, String subtype, String note, long installationId,
                                             String name, String phone, String place, String location) {
        StringBuilder out = new StringBuilder();
        out.append("تقرير بند تركيب\n");
        out.append("العميل: ").append(safe(name)).append("\n");
        out.append("الرقم: ").append(safe(phone)).append("\n");
        out.append("اسم المكان: ").append(safe(place)).append("\n");
        if (!emptyForDb(location).isEmpty()) out.append("اللوكيشن: ").append(location).append("\n");
        out.append("\n").append(type).append(" رقم ").append(number).append("\n");
        if (!emptyForDb(subtype).isEmpty()) out.append("النوع: ").append(subtype).append("\n");
        if (!emptyForDb(note).isEmpty()) out.append("ملاحظات: ").append(note).append("\n");
        out.append("تم التنفيذ، مرفق الصور.");
        shareTextAndImagesToWhatsApp(out.toString(), installationImageUris(installationId), type + " رقم " + number);
    }

    private void shareTextAndImagesToWhatsApp(String message, ArrayList<String> imageUris, String title) {
        if (imageUris == null || imageUris.isEmpty()) {
            startWhatsAppShare(message, new ArrayList<>(), title);
            return;
        }
        toast("جاري تجهيز الصور للمشاركة");
        new Thread(() -> {
            ArrayList<Uri> streams = prepareShareUris(imageUris);
            runOnUiThread(() -> startWhatsAppShare(message, streams, title));
        }).start();
    }

    private void startWhatsAppShare(String message, ArrayList<Uri> streams, String title) {
        Intent intent = new Intent(streams.isEmpty() ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE);
        intent.setType(streams.isEmpty() ? "text/plain" : "image/*");
        intent.putExtra(Intent.EXTRA_TEXT, message);
        if (!streams.isEmpty()) {
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, streams);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        intent.setPackage("com.whatsapp");
        try {
            startActivity(intent);
        } catch (Exception e) {
            try {
                intent.setPackage(null);
                startActivity(Intent.createChooser(intent, title));
            } catch (Exception ignored) {
                toast("تعذر فتح واتساب للمشاركة");
            }
        }
    }

    private ArrayList<Uri> prepareShareUris(ArrayList<String> imageUris) {
        ArrayList<Uri> out = new ArrayList<>();
        File dir = new File(getFilesDir(), "whatsapp_share");
        if (!dir.exists()) dir.mkdirs();
        for (int i = 0; i < imageUris.size(); i++) {
            String raw = emptyForDb(imageUris.get(i));
            if (raw.isEmpty()) continue;
            try {
                if (raw.startsWith("http://") || raw.startsWith("https://")) {
                    File file = new File(dir, "share-" + System.currentTimeMillis() + "-" + i + imageExtension(raw));
                    InputStream input = new URL(raw).openStream();
                    FileOutputStream output = new FileOutputStream(file);
                    try {
                        copyStream(input, output);
                    } finally {
                        input.close();
                        output.close();
                    }
                    out.add(FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file));
                } else {
                    out.add(Uri.parse(raw));
                }
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private void copyStream(InputStream input, FileOutputStream output) throws Exception {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
    }

    private String imageExtension(String uri) {
        String lower = uri.toLowerCase(Locale.US);
        if (lower.contains(".png")) return ".png";
        if (lower.contains(".webp")) return ".webp";
        return ".jpg";
    }

    private String buildAssignmentGroupMessage(SyncManager.CompletedAssignment item) {
        JSONObject root;
        try {
            String raw = !emptyForDb(item.completedSnapshot).isEmpty() ? item.completedSnapshot : item.originalSnapshot;
            root = new JSONObject(emptyForDb(raw).isEmpty() ? "{}" : raw);
        } catch (Exception e) {
            root = new JSONObject();
        }
        StringBuilder out = new StringBuilder();
        out.append("تقرير تنفيذ الشغل\n");
        out.append("العميل: ").append(safe(item.customerName)).append("\n");
        out.append("الرقم: ").append(safe(item.phone)).append("\n");
        out.append("اسم المكان: ").append(safe(item.place)).append("\n");
        if (!emptyForDb(item.location).isEmpty()) out.append("اللوكيشن: ").append(item.location).append("\n");
        out.append("نوع الشغل: ").append(detectWorkTypes(root)).append("\n");
        out.append("الفني: ").append(safe(item.completedByEmail)).append("\n");
        if (item.completedAt > 0) out.append("تاريخ التسليم: ").append(ReminderScheduler.formatDate(item.completedAt)).append("\n");
        appendWorkDetails(out, root);
        out.append("\nعدد الصور المرفقة: ").append(snapshotImageUris(root.toString()).size()).append("\n");
        out.append("\nتم التنفيذ، برجاء مراجعة الصور والتفاصيل.");
        return out.toString();
    }

    private String detectWorkTypes(JSONObject root) {
        ArrayList<String> types = new ArrayList<>();
        if (snapshotArrayCount(root, "extinguishers") > 0) types.add("طفايات");
        if (snapshotArrayCount(root, "safety_certificates") > 0) types.add("شهادة سلامة");
        if (snapshotArrayCount(root, "technical_reports") > 0) types.add("تقرير فني");
        if (snapshotArrayCount(root, "maintenance_contracts") > 0) types.add("عقد صيانة");
        if (snapshotArrayCount(root, "installation_items") > 0) types.add("تركيبات");
        return types.isEmpty() ? "شغل عام" : joinText(types);
    }

    private void appendWorkDetails(StringBuilder out, JSONObject root) {
        JSONArray extinguishers = root.optJSONArray("extinguishers");
        if (extinguishers != null && extinguishers.length() > 0) {
            out.append("\nتفاصيل الطفايات:");
            for (int i = 0; i < extinguishers.length(); i++) {
                JSONObject row = extinguishers.optJSONObject(i);
                if (row == null) continue;
                out.append("\n- النوع: ").append(row.optString("extinguisher_type", row.optString("type", "-")));
                out.append(" | الوزن: ").append(row.optString("weight", "-"));
                out.append(" | العدد: ").append(row.optString("count", "-"));
                out.append(" | المبلغ: ").append(row.optString("total_price", "-")).append(" ريال");
            }
            out.append("\n");
        }
        appendSimpleWorkCount(out, root, "safety_certificates", "شهادات السلامة");
        appendSimpleWorkCount(out, root, "technical_reports", "التقارير الفنية");
        JSONArray contracts = root.optJSONArray("maintenance_contracts");
        if (contracts != null && contracts.length() > 0) {
            out.append("\n\nتفاصيل عقود الصيانة:");
            for (int i = 0; i < contracts.length(); i++) {
                JSONObject row = contracts.optJSONObject(i);
                if (row == null) continue;
                out.append("\n- الزيارة: ").append(formatJsonDate(row, "next_visit_at"));
                out.append(" | طفايات: ").append(row.optString("extinguisher_count", "0"));
                out.append(" | كواشف: ").append(row.optString("detector_count", "0"));
                out.append(" | أجراس: ").append(row.optString("bell_count", "0"));
                out.append(" | كواسر: ").append(row.optString("breaker_count", "0"));
                out.append(" | Exit: ").append(row.optString("exit_count", "0"));
                out.append(" | لوحات: ").append(row.optString("panel_count", "0"));
                String note = row.optString("note", "");
                if (!note.isEmpty()) out.append(" | ملاحظات: ").append(note);
            }
            out.append("\n");
        }
        JSONArray installations = root.optJSONArray("installation_items");
        if (installations != null && installations.length() > 0) {
            out.append("\n\nتفاصيل التركيبات:");
            for (int i = 0; i < installations.length(); i++) {
                JSONObject row = installations.optJSONObject(i);
                if (row == null) continue;
                out.append("\n- ").append(row.optString("item_type", "-"));
                out.append(" رقم ").append(row.optString("item_number", "-"));
                String subtype = row.optString("subtype", "");
                if (!subtype.isEmpty()) out.append(" | النوع: ").append(subtype);
                String note = row.optString("note", "");
                if (!note.isEmpty()) out.append(" | ملاحظات: ").append(note);
            }
            out.append("\n");
        }
    }

    private void appendSimpleWorkCount(StringBuilder out, JSONObject root, String key, String label) {
        int count = snapshotArrayCount(root, key);
        if (count > 0) out.append("\n").append(label).append(": ").append(count);
    }

    private String formatJsonDate(JSONObject row, String key) {
        long value = row.optLong(key, 0);
        return value > 0 ? ReminderScheduler.formatDate(value) : "-";
    }

    private void appendBeforeAfterLinks(StringBuilder out, JSONObject root) {
        ArrayList<String> before = new ArrayList<>();
        ArrayList<String> after = new ArrayList<>();
        ArrayList<String> other = new ArrayList<>();
        JSONArray attachments = root.optJSONArray("customer_attachments");
        if (attachments != null) {
            for (int i = 0; i < attachments.length(); i++) {
                JSONObject row = attachments.optJSONObject(i);
                if (row == null) continue;
                String uri = row.optString("uri", "");
                String stage = row.optString("stage", "");
                addUriByStage(before, after, other, uri, stage);
            }
        }
        addSnapshotImagesTo(after, root.optJSONArray("extinguisher_images"), "uri");
        addSnapshotImagesTo(after, root.optJSONArray("extinguishers"), "image_uri");
        JSONArray installationImages = root.optJSONArray("installation_images");
        if (installationImages != null) {
            for (int i = 0; i < installationImages.length(); i++) {
                JSONObject row = installationImages.optJSONObject(i);
                if (row == null) continue;
                addUriByStage(before, after, other, row.optString("uri", ""), row.optString("stage", ""));
            }
        }
        appendImageSection(out, "\nصور قبل التنفيذ", before);
        appendImageSection(out, "\nصور بعد التنفيذ", after);
        appendImageSection(out, "\nمرفقات أخرى", other);
    }

    private void addUriByStage(ArrayList<String> before, ArrayList<String> after, ArrayList<String> other, String uri, String stage) {
        String clean = emptyForDb(uri);
        if (clean.isEmpty()) return;
        if ("قبل التنفيذ".equals(stage)) addUnique(before, clean);
        else if ("بعد التنفيذ".equals(stage)) addUnique(after, clean);
        else addUnique(other, clean);
    }

    private void addSnapshotImagesTo(ArrayList<String> target, JSONArray rows, String key) {
        if (rows == null) return;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            addUnique(target, row.optString(key, ""));
        }
    }

    private void appendImageSection(StringBuilder out, String title, ArrayList<String> uris) {
        if (uris.isEmpty()) return;
        out.append(title).append(":");
        for (String uri : uris) out.append("\n- ").append(uri);
        out.append("\n");
    }

    private void addUnique(ArrayList<String> list, String value) {
        String clean = emptyForDb(value);
        if (!clean.isEmpty() && !list.contains(clean)) list.add(clean);
    }

    private int snapshotArrayCount(JSONObject root, String key) {
        JSONArray rows = root.optJSONArray(key);
        return rows == null ? 0 : rows.length();
    }

    private String joinText(ArrayList<String> values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append("، ");
            out.append(values.get(i));
        }
        return out.toString();
    }

    private String assignmentStatusLabel(String status) {
        String value = emptyForDb(status);
        if ("open".equals(value)) return "مرسل للفني";
        if ("completed".equals(value)) return "بانتظار مراجعة المشرف";
        if ("supervisor_received".equals(value)) return "تم الاعتماد";
        if ("supervisor_rejected".equals(value)) return "تم الرفض";
        return value.isEmpty() ? "-" : value;
    }

    private String blankDash(String value) {
        return emptyForDb(value).isEmpty() ? "-" : value;
    }

    private int appendSnapshotDiff(StringBuilder out, JSONObject before, JSONObject after, String key, String label) {
        String oldValue = snapshotExtinguisherValue(before, key);
        String newValue = snapshotExtinguisherValue(after, key);
        if (oldValue.equals(newValue)) return 0;
        out.append(label).append(": ").append(oldValue.isEmpty() ? "-" : oldValue)
                .append(" -> ").append(newValue.isEmpty() ? "-" : newValue).append("\n");
        return 1;
    }

    private String snapshotExtinguisherValue(JSONObject root, String key) {
        try {
            JSONArray rows = root.optJSONArray("extinguishers");
            if (rows == null || rows.length() == 0) return "";
            Object value = rows.getJSONObject(0).opt(key);
            return value == null || value == JSONObject.NULL ? "" : String.valueOf(value);
        } catch (Exception e) {
            return "";
        }
    }

    private ArrayList<String> snapshotImageUris(String rawSnapshot) {
        ArrayList<String> images = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(emptyForDb(rawSnapshot).isEmpty() ? "{}" : rawSnapshot);
            addSnapshotImages(images, root.optJSONArray("customer_attachments"), "uri");
            addSnapshotImages(images, root.optJSONArray("extinguisher_images"), "uri");
            addSnapshotImages(images, root.optJSONArray("extinguishers"), "image_uri");
            addSnapshotImages(images, root.optJSONArray("installation_images"), "uri");
        } catch (Exception ignored) {
        }
        return images;
    }

    private void addSnapshotImages(ArrayList<String> images, JSONArray rows, String key) {
        if (rows == null) return;
        for (int i = 0; i < rows.length(); i++) {
            String uri = rows.optJSONObject(i) == null ? "" : rows.optJSONObject(i).optString(key, "");
            if (!uri.isEmpty() && !images.contains(uri) && looksLikeImage(uri)) images.add(uri);
        }
    }

    private void listAnnual(String table, String dateColumn, String label) {
        Cursor c = db.all(table);
        try {
            while (c.moveToNext()) {
                card(label + " - " + c.getString(c.getColumnIndexOrThrow("customer_name")),
                        "التاريخ: " + ReminderScheduler.formatDate(c.getLong(c.getColumnIndexOrThrow(dateColumn))) +
                                "\nالمبلغ: " + money(c.getDouble(c.getColumnIndexOrThrow("total_price"))) +
                                "\nالتذكير: " + ReminderScheduler.formatDate(c.getLong(c.getColumnIndexOrThrow("reminder_at"))) +
                                "\nرقم: " + val(c, "phone") +
                                "\nاسم المكان: " + val(c, "place_name") +
                                "\nلوكيشن: " + val(c, "location"));
            }
        } finally {
            c.close();
        }
    }

    private void afterSave(String message) {
        ReminderScheduler.scheduleAll(this, db);
        LocalBackupManager.backupQuietly(this, db);
        sync.autoUploadQuietly();
        toast(message);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
    }

    private void showTeamAssignmentNotification(int count, String latestCustomer) {
        showWorkNotification(count > 1 ? "وصلت تكليفات جديدة" : "وصل تكليف جديد",
                emptyForDb(latestCustomer).isEmpty() ? "افتح العملاء المحولين لمراجعة الشغل." : "عميل: " + latestCustomer,
                6100 + Math.min(count, 99));
    }

    private void showSupervisorCompletedNotification(int count, String latestCustomer) {
        showWorkNotification(count > 1 ? "الفنيون سلموا تحديثات" : "الفني سلم تحديث للمراجعة",
                emptyForDb(latestCustomer).isEmpty() ? "افتح استلام من الفنيين لمراجعة الشغل." : "عميل: " + latestCustomer,
                6300 + Math.min(count, 99));
    }

    private void showWorkNotification(String title, String text, int notificationId) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(TEAM_CHANNEL_ID, "تكليفات الفريق", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("إشعارات عند وصول تكليف جديد للفني");
            manager.createNotificationChannel(channel);
        }
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 5051, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, TEAM_CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_nav_tasks)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setPriority(Notification.PRIORITY_HIGH);
        manager.notify(notificationId, builder.build());
    }

    private void clear() {
        refreshSyncBadge();
        if (manualVoiceActive) cancelManualVoice();
        content.removeAllViews();
    }

    private void refreshSyncBadge() {
        if (syncBadge == null || sync == null) return;
        FirebaseUser user = sync.user();
        boolean connected = user != null;
        String teamCode = db == null ? "" : db.setting("team_code", "");
        syncBadge.setText(connected
                ? (teamCode.trim().isEmpty() ? "متزامن مع Google" : "متزامن مع Google + فريق")
                : "في انتظار مزامنة Google");
        syncBadge.setTextColor(connected ? Color.rgb(22, 101, 52) : Color.rgb(146, 64, 14));
        syncBadge.setBackground(rounded(connected ? Color.rgb(220, 252, 231) : Color.rgb(255, 247, 237),
                connected ? Color.rgb(134, 239, 172) : Color.rgb(253, 186, 116), dp(10)));
    }

    private void hero(String title, String body) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        box.setBackground(rounded(BRAND, ACCENT, dp(12)));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(23);
        t.setTypeface(null, 1);
        t.setGravity(Gravity.RIGHT);
        TextView b = new TextView(this);
        b.setText(body);
        b.setTextColor(Color.rgb(255, 237, 213));
        b.setTextSize(15);
        b.setGravity(Gravity.RIGHT);
        box.addView(t, matchWrap());
        box.addView(b, matchWrap());
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(4), 0, dp(10));
        content.addView(box, lp);
    }

    private void homeAction(String title, String subtitle, Runnable action) {
        Button b = new Button(this);
        b.setText(title + "\n" + subtitle);
        b.setTextColor(TEXT);
        b.setTextSize(15);
        b.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        b.setAllCaps(false);
        b.setPadding(dp(16), dp(6), dp(16), dp(6));
        b.setMinHeight(dp(64));
        b.setBackground(rounded(Color.WHITE, Color.rgb(226, 232, 240), dp(10)));
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(6), 0, dp(6));
        content.addView(b, lp);
    }

    private void section(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(19);
        tv.setTypeface(null, 1);
        tv.setTextColor(BRAND_DARK);
        tv.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(14), 0, dp(7));
        content.addView(tv, lp);
    }

    private void card(String title, String body) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        box.setBackground(rounded(CARD, Color.rgb(226, 232, 240), dp(10)));
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
            open.setAllCaps(false);
            open.setBackground(rounded(BRAND_LIGHT, Color.rgb(254, 202, 202), dp(10)));
            open.setOnClickListener(v -> openLocation(mapLink));
            LinearLayout.LayoutParams openLp = matchWrap();
            openLp.setMargins(0, dp(6), 0, 0);
            box.addView(open, openLp);
        }
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(5), 0, dp(10));
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

    private void chooseAttachment(String name, String phone, String place, String location) {
        pendingAttachmentName = name;
        pendingAttachmentPhone = phone;
        pendingAttachmentPlace = place;
        pendingAttachmentLocation = location;
        new AlertDialog.Builder(this)
                .setTitle("إضافة صورة")
                .setItems(new String[]{"قبل التنفيذ - كاميرا", "قبل التنفيذ - معرض", "بعد التنفيذ - كاميرا", "بعد التنفيذ - معرض", "اختيار ملف PDF"}, (dialog, which) -> {
                    if (which == 0) {
                        pendingAttachmentStage = "قبل التنفيذ";
                        takeAttachmentPhoto();
                    } else if (which == 1) {
                        pendingAttachmentStage = "قبل التنفيذ";
                        chooseAttachmentFromGallery();
                    } else if (which == 2) {
                        pendingAttachmentStage = "بعد التنفيذ";
                        takeAttachmentPhoto();
                    } else if (which == 3) {
                        pendingAttachmentStage = "بعد التنفيذ";
                        chooseAttachmentFromGallery();
                    } else {
                        pendingAttachmentStage = "مرفق";
                        chooseAttachmentFile();
                    }
                })
                .show();
    }

    private void chooseAttachmentFromGallery() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, ATTACHMENT_REQUEST);
        } catch (Exception e) {
            toast("تعذر فتح المعرض");
        }
    }

    private void chooseAttachmentFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, ATTACHMENT_REQUEST);
        } catch (Exception e) {
            toast("تعذر فتح اختيار الملف");
        }
    }

    private void takeAttachmentPhoto() {
        cameraForAttachment = true;
        cameraForInstallation = false;
        if (!hasCameraPermission()) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            return;
        }
        try {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            File photo = createCameraImageFile("CustomerPhotos", "customer-");
            pendingCameraImageUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photo);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraImageUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, ATTACHMENT_CAMERA_REQUEST);
        } catch (Exception e) {
            toast("تعذر فتح الكاميرا");
        }
    }

    private void chooseExtinguisherImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, EXTINGUISHER_IMAGE_REQUEST);
        } catch (Exception e) {
            toast("تعذر فتح اختيار الصورة");
        }
    }

    private void takeExtinguisherPhoto() {
        cameraForAttachment = false;
        cameraForInstallation = false;
        if (!hasCameraPermission()) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            return;
        }
        try {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            File photo = createCameraImageFile("ExtinguisherPhotos", "extinguisher-");
            pendingCameraImageUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photo);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraImageUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, EXTINGUISHER_CAMERA_REQUEST);
        } catch (Exception e) {
            toast("تعذر فتح الكاميرا");
        }
    }

    private void chooseInstallationImage(String name, String phone, String place, String location, long installationId, String defaultStage) {
        setPendingInstallationCustomer(name, phone, place, location, defaultStage);
        pendingInstallationId = installationId;
        new AlertDialog.Builder(this)
                .setTitle("صور التركيب")
                .setItems(new String[]{"قبل التنفيذ - كاميرا", "قبل التنفيذ - معرض", "بعد التنفيذ - كاميرا", "بعد التنفيذ - معرض"}, (dialog, which) -> {
                    pendingInstallationStage = which < 2 ? "قبل التنفيذ" : "بعد التنفيذ";
                    if (which == 0 || which == 2) takeInstallationPhoto();
                    else chooseInstallationFromGallery();
                })
                .show();
    }

    private void chooseInstallationFromGallery() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, INSTALLATION_IMAGE_REQUEST);
        } catch (Exception e) {
            toast("تعذر فتح اختيار الصورة");
        }
    }

    private void takeInstallationPhoto() {
        cameraForAttachment = false;
        cameraForInstallation = true;
        if (!hasCameraPermission()) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            return;
        }
        try {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            File photo = createCameraImageFile("InstallationPhotos", "installation-");
            pendingCameraImageUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photo);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraImageUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, INSTALLATION_CAMERA_REQUEST);
        } catch (Exception e) {
            toast("تعذر فتح الكاميرا");
        }
    }

    private boolean hasCameraPermission() {
        return Build.VERSION.SDK_INT < 23 ||
                checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private File createCameraImageFile(String folder, String prefix) throws Exception {
        File base = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (base == null) base = getFilesDir();
        File dir = new File(base, folder);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, prefix + System.currentTimeMillis() + ".jpg");
    }

    private int addSelectedExtinguisherImages(Intent data) {
        int added = 0;
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                String saved = persistReadableUri(uri);
                if (addPendingExtinguisherImage(saved.isEmpty() ? uri.toString() : saved)) added++;
            }
        } else if (data.getData() != null) {
            Uri uri = data.getData();
            String saved = persistReadableUri(uri);
            if (addPendingExtinguisherImage(saved.isEmpty() ? uri.toString() : saved)) added++;
        }
        return added;
    }

    private boolean addPendingExtinguisherImage(String uri) {
        String safeUri = emptyForDb(uri);
        if (safeUri.isEmpty() || pendingExtinguisherImageUris.contains(safeUri)) return false;
        pendingExtinguisherImageUris.add(safeUri);
        if (pendingExtinguisherImageUri.isEmpty()) pendingExtinguisherImageUri = safeUri;
        return true;
    }

    private int saveAttachmentIntent(Intent data) {
        int added = 0;
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                saveAttachmentUri(clipData.getItemAt(i).getUri());
                added++;
            }
        } else if (data.getData() != null) {
            saveAttachmentUri(data.getData());
            added++;
        }
        return added;
    }

    private void saveAttachmentUri(Uri uri) {
        String savedUri = persistReadableUri(uri);
        if (savedUri.isEmpty()) savedUri = uri.toString();
        Uri finalUri = Uri.parse(savedUri);
        ContentValues cv = new ContentValues();
        cv.put("customer_name", pendingAttachmentName);
        cv.put("phone", pendingAttachmentPhone);
        cv.put("place_name", pendingAttachmentPlace);
        cv.put("location", pendingAttachmentLocation);
        cv.put("title", attachmentTitle(finalUri));
        cv.put("uri", savedUri);
        cv.put("stage", pendingAttachmentStage);
        cv.put("created_at", System.currentTimeMillis());
        db.insert("customer_attachments", cv);
        afterSave("تم حفظ المرفق مع العميل");
    }

    private int saveInstallationImageIntent(Intent data) {
        int added = 0;
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                saveInstallationImageUri(clipData.getItemAt(i).getUri());
                added++;
            }
        } else if (data.getData() != null) {
            saveInstallationImageUri(data.getData());
            added++;
        }
        return added;
    }

    private void saveInstallationImageUri(Uri uri) {
        if (pendingInstallationId <= 0 || uri == null) return;
        String savedUri = persistReadableUri(uri);
        if (savedUri.isEmpty()) savedUri = uri.toString();
        ContentValues cv = new ContentValues();
        cv.put("installation_id", pendingInstallationId);
        cv.put("uri", savedUri);
        cv.put("stage", pendingInstallationStage);
        cv.put("created_at", System.currentTimeMillis());
        db.insert("installation_images", cv);
        afterSave("تم حفظ صورة التركيب");
    }

    private void setPendingInstallationCustomer(String name, String phone, String place, String location, String stage) {
        pendingInstallationName = name;
        pendingInstallationPhone = phone;
        pendingInstallationPlace = place;
        pendingInstallationLocation = location;
        pendingInstallationStage = stage;
    }

    private void refreshPendingInstallationCustomer() {
        openCustomerDetails(pendingInstallationName, pendingInstallationPhone, pendingInstallationPlace, pendingInstallationLocation);
    }

    private void refreshPendingAttachmentCustomer() {
        int count = customerExtinguisherCount(pendingAttachmentName, pendingAttachmentPhone, pendingAttachmentPlace, pendingAttachmentLocation);
        double total = customerTotalPrice(pendingAttachmentName, pendingAttachmentPhone, pendingAttachmentPlace, pendingAttachmentLocation);
        String status = customerStatus(pendingAttachmentName, pendingAttachmentPhone, pendingAttachmentPlace, pendingAttachmentLocation);
        showCustomerDetails(pendingAttachmentName, pendingAttachmentPhone, pendingAttachmentPlace,
                pendingAttachmentLocation, status, count, total);
    }

    private void saveExtinguisherImages(long extinguisherId, ArrayList<String> imageUris) {
        HashSet<String> unique = new HashSet<>();
        for (String uri : imageUris) {
            String safeUri = emptyForDb(uri);
            if (safeUri.isEmpty() || unique.contains(safeUri)) continue;
            unique.add(safeUri);
            ContentValues cv = new ContentValues();
            cv.put("extinguisher_id", extinguisherId);
            cv.put("uri", safeUri);
            cv.put("created_at", System.currentTimeMillis());
            db.insert("extinguisher_images", cv);
        }
    }

    private ArrayList<String> extinguisherImages(long extinguisherId, String legacyImage) {
        ArrayList<String> images = new ArrayList<>();
        String legacy = emptyForDb(legacyImage);
        if (!legacy.isEmpty()) images.add(legacy);
        Cursor c = db.raw("SELECT uri FROM extinguisher_images WHERE extinguisher_id=? ORDER BY created_at DESC",
                String.valueOf(extinguisherId));
        try {
            while (c.moveToNext()) {
                String uri = emptyForDb(c.getString(0));
                if (!uri.isEmpty() && !images.contains(uri)) images.add(uri);
            }
        } finally {
            c.close();
        }
        return images;
    }

    private void listExtinguisherImages(long extinguisherId, String legacyImage, Runnable refresh) {
        ArrayList<String> images = extinguisherImages(extinguisherId, legacyImage);
        for (int i = 0; i < images.size(); i++) {
            String uri = images.get(i);
            card("صورة الطفاية " + (i + 1), "معرض صور الطفايات");
            imagePreview(uri);
            secondaryButton("فتح الصورة", () -> openAttachment(uri));
            secondaryButton("حذف الصورة", () -> confirmDeleteExtinguisherImage(extinguisherId, uri, refresh));
        }
    }

    private void imagePreview(String uriText) {
        ImageView preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackground(rounded(Color.rgb(248, 250, 252), Color.rgb(203, 213, 225), dp(12)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(190));
        lp.setMargins(0, dp(6), 0, dp(8));
        content.addView(preview, lp);

        String uri = emptyForDb(uriText);
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            new Thread(() -> {
                try {
                    Bitmap bitmap = decodeRemotePreview(uri);
                    if (bitmap == null) return;
                    runOnUiThread(() -> preview.setImageBitmap(bitmap));
                } catch (Throwable ignored) {
                }
            }).start();
        } else {
            try {
                preview.setImageURI(Uri.parse(uri));
            } catch (Throwable ignored) {
            }
        }
    }

    private Bitmap decodeRemotePreview(String uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        InputStream first = new URL(uri).openStream();
        try {
            BitmapFactory.decodeStream(first, null, bounds);
        } finally {
            first.close();
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = previewSampleSize(bounds, 1200, 900);
        InputStream second = new URL(uri).openStream();
        try {
            return BitmapFactory.decodeStream(second, null, options);
        } finally {
            second.close();
        }
    }

    private int previewSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int sample = 1;
        while (height / sample > reqHeight || width / sample > reqWidth) {
            sample *= 2;
        }
        return Math.max(1, sample);
    }

    private void confirmDeleteExtinguisherImage(long extinguisherId, String uri, Runnable refresh) {
        new AlertDialog.Builder(this)
                .setTitle("حذف الصورة")
                .setMessage("تحب تحذف الصورة دي من سجل الطفاية؟")
                .setPositiveButton("حذف", (dialog, which) -> {
                    db.delete("extinguisher_images", "extinguisher_id=? AND uri=?", String.valueOf(extinguisherId), uri);
                    ContentValues cv = new ContentValues();
                    cv.put("image_uri", firstExtinguisherImageUri(extinguisherId));
                    db.update("extinguishers", cv, "id=?", String.valueOf(extinguisherId));
                    afterSave("تم حذف الصورة");
                    if (refresh != null) refresh.run();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private String firstExtinguisherImageUri(long extinguisherId) {
        Cursor c = db.raw("SELECT uri FROM extinguisher_images WHERE extinguisher_id=? ORDER BY created_at DESC LIMIT 1",
                String.valueOf(extinguisherId));
        try {
            return c.moveToFirst() ? emptyForDb(c.getString(0)) : "";
        } finally {
            c.close();
        }
    }

    private String persistReadableUri(Uri uri) {
        try {
            final int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception ignored) {
        }
        return uri == null ? "" : uri.toString();
    }

    private String attachmentTitle(Uri uri) {
        String text = uri.getLastPathSegment();
        return text == null || text.trim().isEmpty() ? "مرفق عميل" : text;
    }

    private void listCustomerAttachments(String name, String phone, String place, String location) {
        section("صور ومرفقات العميل");
        Cursor c = db.raw("SELECT id, title, uri, created_at, IFNULL(stage,'') FROM customer_attachments " +
                "WHERE customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=? ORDER BY created_at DESC",
                customerArgs(name, phone, place, location));
        try {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String title = safe(c.getString(1));
                String uri = c.getString(2);
                String stage = emptyForDb(c.getString(4));
                card(title, "المرحلة: " + (stage.isEmpty() ? "-" : stage) +
                        "\nتمت الإضافة: " + ReminderScheduler.formatDate(c.getLong(3)));
                if (looksLikeImage(uri)) imagePreview(uri);
                secondaryButton("فتح المرفق", () -> openAttachment(uri));
                secondaryButton("حذف الصورة", () -> confirmDeleteCustomerAttachment(id, name, phone, place, location));
            }
        } finally {
            c.close();
        }
    }

    private void confirmDeleteCustomerAttachment(long id, String name, String phone, String place, String location) {
        new AlertDialog.Builder(this)
                .setTitle("حذف الصورة")
                .setMessage("تحب تحذف الصورة دي من العميل؟")
                .setPositiveButton("حذف", (dialog, which) -> {
                    db.deleteCustomerAttachment(id);
                    afterSave("تم حذف الصورة");
                    openCustomerDetails(name, phone, place, location);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void openAttachment(String uriText) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uriText));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            toast("تعذر فتح المرفق");
        }
    }

    private boolean looksLikeImage(String uri) {
        String value = emptyForDb(uri).toLowerCase(Locale.US);
        return value.startsWith("content:") || value.endsWith(".jpg") || value.endsWith(".jpeg") ||
                value.endsWith(".png") || value.endsWith(".webp") || value.contains("/uploads/");
    }

    private void exportMonthlyPdf() {
        try {
            File file = createMonthlyPdf();
            toast("تم حفظ PDF: " + file.getAbsolutePath());
        } catch (Exception e) {
            toast("تعذر تصدير تقرير الشهر PDF");
        }
    }

    private void exportMonthlyExcel() {
        try {
            File file = createMonthlyExcel();
            toast("تم حفظ Excel: " + file.getAbsolutePath());
        } catch (Exception e) {
            toast("تعذر تصدير ملف Excel");
        }
    }

    private File createMonthlyExcel() throws Exception {
        long[] range = monthRange();
        StringBuilder html = new StringBuilder();
        html.append("\ufeff<html><head><meta charset=\"UTF-8\"></head>");
        html.append("<body dir=\"rtl\" style=\"font-family:Arial;\">");
        html.append("<h2>تقرير الشهر الحالي</h2>");
        html.append("<p>تاريخ التصدير: ").append(htmlEscape(ReminderScheduler.formatDate(System.currentTimeMillis()))).append("</p>");
        appendExcelSummary(html, range);
        appendExcelExtinguishers(html, range);
        appendExcelSimpleTable(html, "شهادات السلامة",
                new String[]{"العميل", "الرقم", "اسم المكان", "الحالة", "المبلغ", "تاريخ الشهادة", "التذكير"},
                "SELECT customer_name, phone, place_name, customer_status, total_price, certificate_date, reminder_at FROM safety_certificates WHERE created_at BETWEEN ? AND ? ORDER BY created_at DESC",
                range, new int[]{5, 6}, new int[]{4});
        appendExcelSimpleTable(html, "التقارير الفنية",
                new String[]{"العميل", "الرقم", "اسم المكان", "الحالة", "المبلغ", "تاريخ التقرير", "التذكير"},
                "SELECT customer_name, phone, place_name, customer_status, total_price, report_date, reminder_at FROM technical_reports WHERE created_at BETWEEN ? AND ? ORDER BY created_at DESC",
                range, new int[]{5, 6}, new int[]{4});
        appendExcelSimpleTable(html, "عقود الصيانة",
                new String[]{"العميل", "الرقم", "اسم المكان", "الحالة", "تاريخ البداية", "الزيارة القادمة", "التذكير"},
                "SELECT customer_name, phone, place_name, customer_status, start_date, next_visit_at, reminder_at FROM maintenance_contracts WHERE created_at BETWEEN ? AND ? ORDER BY created_at DESC",
                range, new int[]{4, 5, 6}, new int[]{});
        appendExcelSimpleTable(html, "السلف",
                new String[]{"الشخص", "المبلغ", "ملاحظة"},
                "SELECT employee_name, amount, note FROM advances WHERE created_at BETWEEN ? AND ? ORDER BY created_at DESC",
                range, new int[]{}, new int[]{1});
        html.append("</body></html>");

        File dir = reportDir();
        String month = String.format(Locale.US, "%tY-%tm", new Date(), new Date());
        File file = new File(dir, "monthly-report-" + month + ".xls");
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
        try {
            writer.write(html.toString());
        } finally {
            writer.close();
        }
        return file;
    }

    private void appendExcelSummary(StringBuilder html, long[] range) {
        double extinguisherTotal = monthlyTableTotal("extinguishers", range);
        double certificateTotal = monthlyTableTotal("safety_certificates", range);
        double reportTotal = monthlyTableTotal("technical_reports", range);
        double extinguisherPct = db.settingDouble("extinguisher_percent", 25);
        double certificatePct = db.settingDouble("certificate_percent", 0);
        double reportPct = db.settingDouble("report_percent", 0);
        double extinguisherShare = extinguisherTotal * extinguisherPct / 100.0;
        double certificateShare = certificateTotal * certificatePct / 100.0;
        double reportShare = reportTotal * reportPct / 100.0;
        html.append("<h3>الملخص</h3><table border=\"1\" cellspacing=\"0\" cellpadding=\"6\">");
        html.append("<tr><th>البند</th><th>الإجمالي</th><th>النسبة</th><th>نسبتك</th></tr>");
        appendExcelRow(html, "الطفايات", money(extinguisherTotal), cleanNumber(extinguisherPct) + "%", money(extinguisherShare));
        appendExcelRow(html, "شهادات السلامة", money(certificateTotal), cleanNumber(certificatePct) + "%", money(certificateShare));
        appendExcelRow(html, "التقارير الفنية", money(reportTotal), cleanNumber(reportPct) + "%", money(reportShare));
        appendExcelRow(html, "إجمالي نسبتك", "", "", money(extinguisherShare + certificateShare + reportShare));
        html.append("</table>");
    }

    private void appendExcelExtinguishers(StringBuilder html, long[] range) {
        appendExcelSimpleTable(html, "الطفايات",
                new String[]{"العميل", "الرقم", "اسم المكان", "الحالة", "النوع", "الوزن", "العدد", "المبلغ", "المدفوع", "المتبقي", "تاريخ الاستيكر", "التذكير", "استلم تاني"},
                "SELECT customer_name, phone, place_name, customer_status, extinguisher_type, weight, count, total_price, IFNULL(paid_amount,0), MAX(total_price-IFNULL(paid_amount,0),0), sticker_date, reminder_at, delivered_again FROM extinguishers WHERE created_at BETWEEN ? AND ? ORDER BY created_at DESC",
                range, new int[]{10, 11}, new int[]{7, 8, 9});
    }

    private void appendExcelSimpleTable(StringBuilder html, String title, String[] headers, String sql,
                                        long[] range, int[] dateColumns, int[] moneyColumns) {
        html.append("<h3>").append(htmlEscape(title)).append("</h3>");
        html.append("<table border=\"1\" cellspacing=\"0\" cellpadding=\"6\"><tr>");
        for (String header : headers) html.append("<th>").append(htmlEscape(header)).append("</th>");
        html.append("</tr>");
        Cursor c = db.raw(sql, String.valueOf(range[0]), String.valueOf(range[1]));
        try {
            while (c.moveToNext()) {
                html.append("<tr>");
                for (int i = 0; i < headers.length; i++) {
                    String value;
                    if (containsIndex(dateColumns, i)) value = ReminderScheduler.formatDate(c.getLong(i));
                    else if (containsIndex(moneyColumns, i)) value = money(c.getDouble(i));
                    else if (title.equals("الطفايات") && i == headers.length - 1) value = yesNoLabel(c.getInt(i));
                    else value = safe(c.getString(i));
                    html.append("<td>").append(htmlEscape(value)).append("</td>");
                }
                html.append("</tr>");
            }
        } finally {
            c.close();
        }
        html.append("</table>");
    }

    private void appendExcelRow(StringBuilder html, String... values) {
        html.append("<tr>");
        for (String value : values) html.append("<td>").append(htmlEscape(value)).append("</td>");
        html.append("</tr>");
    }

    private boolean containsIndex(int[] values, int target) {
        for (int value : values) if (value == target) return true;
        return false;
    }

    private String htmlEscape(String value) {
        return safe(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private File reportDir() {
        File base = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (base == null) base = getFilesDir();
        File dir = new File(base, "FireManagerReports");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private File createMonthlyPdf() throws Exception {
        long[] range = monthRange();
        PdfDocument pdf = new PdfDocument();
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = pdf.startPage(info);
        Canvas canvas = page.getCanvas();
        Paint titlePaint = new Paint();
        titlePaint.setColor(BRAND_DARK);
        titlePaint.setTextSize(20);
        titlePaint.setTextAlign(Paint.Align.RIGHT);
        titlePaint.setFakeBoldText(true);
        Paint paint = new Paint();
        paint.setColor(TEXT);
        paint.setTextSize(13);
        paint.setTextAlign(Paint.Align.RIGHT);

        int x = 555;
        int y = 48;
        canvas.drawText("تقرير الشهر الحالي", x, y, titlePaint);
        y += 32;
        y = drawPdfLine(canvas, paint, "تاريخ التصدير: " + ReminderScheduler.formatDate(System.currentTimeMillis()), x, y);
        y = drawPdfLine(canvas, paint, "عدد الطفايات: " + cleanNumber(singleDouble("SELECT COALESCE(SUM(count),0) FROM extinguishers WHERE created_at BETWEEN " + range[0] + " AND " + range[1])), x, y);
        y = drawPdfLine(canvas, paint, "إجمالي مبلغ الطفايات: " + money(singleDouble("SELECT COALESCE(SUM(total_price),0) FROM extinguishers WHERE created_at BETWEEN " + range[0] + " AND " + range[1])), x, y);
        y = drawPdfLine(canvas, paint, "إجمالي شهادات السلامة: " + money(singleDouble("SELECT COALESCE(SUM(total_price),0) FROM safety_certificates WHERE created_at BETWEEN " + range[0] + " AND " + range[1])), x, y);
        y = drawPdfLine(canvas, paint, "إجمالي التقارير الفنية: " + money(singleDouble("SELECT COALESCE(SUM(total_price),0) FROM technical_reports WHERE created_at BETWEEN " + range[0] + " AND " + range[1])), x, y);
        y += 18;
        canvas.drawText("آخر السلف هذا الشهر", x, y, titlePaint);
        y += 24;
        Cursor c = db.raw("SELECT employee_name, amount FROM advances WHERE created_at BETWEEN ? AND ? ORDER BY created_at DESC LIMIT 12",
                String.valueOf(range[0]), String.valueOf(range[1]));
        try {
            while (c.moveToNext() && y < 790) {
                y = drawPdfLine(canvas, paint, safe(c.getString(0)) + " - " + money(c.getDouble(1)), x, y);
            }
        } finally {
            c.close();
        }

        pdf.finishPage(page);
        File dir = reportDir();
        String month = String.format(Locale.US, "%tY-%tm", new Date(), new Date());
        File file = new File(dir, "monthly-report-" + month + ".pdf");
        FileOutputStream output = new FileOutputStream(file);
        try {
            pdf.writeTo(output);
        } finally {
            output.close();
            pdf.close();
        }
        return file;
    }

    private int customerExtinguisherCount(String name, String phone, String place, String location) {
        Cursor c = db.raw("SELECT COALESCE(SUM(count),0) FROM extinguishers " +
                        "WHERE customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=?",
                customerArgs(name, phone, place, location));
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    private double customerTotalPrice(String name, String phone, String place, String location) {
        Cursor c = db.raw("SELECT COALESCE(SUM(total_price),0) FROM (" +
                        "SELECT total_price FROM extinguishers WHERE customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=? " +
                        "UNION ALL SELECT total_price FROM safety_certificates WHERE customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=? " +
                        "UNION ALL SELECT total_price FROM technical_reports WHERE customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=?)",
                name, emptyForDb(phone), emptyForDb(place), emptyForDb(location),
                name, emptyForDb(phone), emptyForDb(place), emptyForDb(location),
                name, emptyForDb(phone), emptyForDb(place), emptyForDb(location));
        try {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        } finally {
            c.close();
        }
    }

    private String customerStatus(String name, String phone, String place, String location) {
        Cursor c = db.raw("SELECT IFNULL(customer_status,'جديد') FROM (" +
                        "SELECT customer_status, created_at FROM extinguishers WHERE customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=? " +
                        "UNION ALL SELECT customer_status, created_at FROM safety_certificates WHERE customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=? " +
                        "UNION ALL SELECT customer_status, created_at FROM technical_reports WHERE customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=? " +
                        "UNION ALL SELECT customer_status, created_at FROM maintenance_contracts WHERE customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=?) " +
                        "ORDER BY created_at DESC LIMIT 1",
                name, emptyForDb(phone), emptyForDb(place), emptyForDb(location),
                name, emptyForDb(phone), emptyForDb(place), emptyForDb(location),
                name, emptyForDb(phone), emptyForDb(place), emptyForDb(location),
                name, emptyForDb(phone), emptyForDb(place), emptyForDb(location));
        try {
            if (c.moveToFirst()) return emptyForDb(c.getString(0));
        } finally {
            c.close();
        }
        return "جديد";
    }

    private void saveCustomerIfMissing(String name, String phone, String place, String location) {
        if (emptyForDb(name).trim().isEmpty()) return;
        Cursor c = db.raw("SELECT id FROM customers WHERE name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=? LIMIT 1",
                name, emptyForDb(phone), emptyForDb(place), emptyForDb(location));
        try {
            if (c.moveToFirst()) return;
        } finally {
            c.close();
        }
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("phone", emptyForDb(phone));
        cv.put("place_name", emptyForDb(place));
        cv.put("location", emptyForDb(location));
        cv.put("customer_status", "جديد");
        cv.put("created_at", System.currentTimeMillis());
        db.insert("customers", cv);
    }

    private int drawPdfLine(Canvas canvas, Paint paint, String text, int x, int y) {
        canvas.drawText(text, x, y, paint);
        return y + 22;
    }

    private void sendWhatsApp(String phone, String customerName, int extinguisherCount) {
        String[] templates = whatsappTemplates();
        int selected = selectedWhatsappTemplateIndex(templates.length);
        String template = templates[selected];
        String countText = extinguisherCount > 0 ? String.valueOf(extinguisherCount) : "";
        String message = template
                .replace("{name}", safe(customerName))
                .replace("{count}", countText);
        if (!template.contains("{count}") && extinguisherCount > 0) {
            message = message + "\nعدد الطفايات المسجلة عندكم: " + extinguisherCount + " طفاية.";
        }
        openWhatsAppMessage(phone, message);
    }

    private void shareCustomerReportWhatsApp(String phone, String name, String place, String location,
                                             String status, int extinguisherCount, double totalPrice) {
        String message = buildCustomerReport(phone, name, place, location, status, extinguisherCount, totalPrice);
        openWhatsAppMessage(phone, message);
    }

    private String buildCustomerReport(String phone, String name, String place, String location, String status,
                                       int extinguisherCount, double totalPrice) {
        StringBuilder out = new StringBuilder();
        out.append("تحياتنا لك ").append(safe(name)).append("\n");
        out.append("هذا ملخص حالة العميل/الموقع لدينا:\n");
        out.append("الحالة: ").append(safe(status.isEmpty() ? "جديد" : status)).append("\n");
        out.append("اسم المكان: ").append(safe(place)).append("\n");
        out.append("عدد الطفايات: ").append(extinguisherCount > 0 ? extinguisherCount + " طفاية" : "").append("\n");
        out.append("إجمالي المبلغ المسجل: ").append(money(totalPrice)).append("\n");
        if (!emptyForDb(location).isEmpty()) out.append("اللوكيشن: ").append(location).append("\n");

        Cursor c = db.raw("SELECT extinguisher_type, weight, count, total_price, delivered_again FROM extinguishers " +
                        "WHERE customer_name=? AND IFNULL(phone,'')=? AND IFNULL(place_name,'')=? AND IFNULL(location,'')=? ORDER BY created_at DESC LIMIT 3",
                name, emptyForDb(phone), emptyForDb(place), emptyForDb(location));
        try {
            int index = 1;
            while (c.moveToNext()) {
                out.append("\nطفاية ").append(index++).append(": ");
                out.append(safe(c.getString(0))).append(" - ");
                out.append(safe(c.getString(1))).append(" - ");
                out.append(c.getInt(2)).append(" عدد - ");
                out.append(money(c.getDouble(3))).append(" - ");
                out.append("استلم تاني: ").append(yesNoLabel(c.getInt(4)));
            }
        } finally {
            c.close();
        }
        out.append("\n\nشاكرين لكم، وبانتظار تأكيدكم.");
        return out.toString();
    }

    private void openWhatsAppMessage(String phone, String message) {
        String normalizedPhone = normalizePhone(phone);
        if (normalizedPhone.isEmpty()) {
            toast("رقم العميل غير مسجل");
            return;
        }
        Uri uri = Uri.parse("https://wa.me/" + normalizedPhone + "?text=" + Uri.encode(message));
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            toast("تعذر فتح واتساب");
        }
    }

    private void openWhatsAppChat(String phone) {
        String normalizedPhone = normalizePhone(phone);
        if (normalizedPhone.isEmpty()) {
            toast("رقم العميل غير مسجل");
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + normalizedPhone)));
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

    private void enableContactSaving() {
        if (!hasContactsPermission()) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS
            }, CONTACTS_REQUEST);
            return;
        }
        db.setSetting("auto_save_contacts", "1");
        afterSave("تم تشغيل حفظ جهات الاتصال تلقائيا");
        showSettings();
    }

    private boolean hasContactsPermission() {
        return Build.VERSION.SDK_INT < 23 ||
                (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
                        checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED);
    }

    private void saveContactIfEnabled(String name, String phone) {
        if (!"1".equals(db.setting("auto_save_contacts", "0"))) return;
        if (name == null || name.trim().isEmpty() || phone == null || phone.trim().isEmpty()) return;
        if (!hasContactsPermission()) return;
        try {
            if (contactExists(phone)) return;
            ArrayList<ContentProviderOperation> ops = new ArrayList<>();
            ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build());
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name.trim() + " - عميل طفايات")
                    .build());
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone.trim())
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build());
            getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        } catch (Exception ignored) {
        }
    }

    private boolean contactExists(String phone) {
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone));
        Cursor c = getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup._ID}, null, null, null);
        if (c == null) return false;
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
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
        return "تحياتنا وتقديرنا لك {name}\nحبيت أذكركم إن موعد انتهاء شهادة/استيكر الطفايات قرب، وعدد الطفايات المسجلة عندكم {count} طفاية. ودي فرصة نرتب زيارة صيانة في الوقت اللي يناسبكم عشان نتأكد إن كل شي جاهز وآمن.\nالله يعطيكم العافية.|||" +
                "تحياتنا لك {name}\nنذكركم بقرب موعد متابعة الطفايات، وعددها عندكم {count} طفاية. فضلا حددوا لنا وقت مناسب للزيارة والصيانة، وبإذن الله نخدمكم بالشكل اللي يرضيكم.\nشاكرين لكم تعاونكم.|||" +
                "تحياتنا وتقديرنا\nعندكم {count} طفاية مسجلة لدينا، وموعد شهادة/استيكر الطفايات قرب ينتهي. نحتاج ننسق معكم موعد زيارة صيانة مناسب، وربي يبارك لكم.";
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
        b.setAllCaps(false);
        b.setMinHeight(dp(46));
        b.setBackground(rounded(Color.WHITE, Color.rgb(248, 113, 113), dp(10)));
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(3), 0, dp(8));
        content.addView(b, lp);
    }

    private void actionButton(String text, int fillColor, Runnable action) {
        actionButton(text, fillColor, 0, action);
    }

    private void actionButton(String text, int fillColor, int iconRes, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setMinHeight(dp(52));
        b.setBackground(rounded(fillColor, darken(fillColor), dp(14)));
        if (iconRes != 0) {
            b.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
            b.setCompoundDrawablePadding(dp(8));
        }
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(5), 0, dp(8));
        content.addView(b, lp);
    }

    private int darken(int color) {
        return Color.rgb(Math.max(0, Color.red(color) - 40),
                Math.max(0, Color.green(color) - 40),
                Math.max(0, Color.blue(color) - 40));
    }

    private EditText input(String hint, int inputType) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        row.setBackground(rounded(Color.WHITE, Color.rgb(203, 213, 225), dp(12)));

        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextColor(TEXT);
        et.setHintTextColor(Color.rgb(100, 116, 139));
        et.setTextSize(16);
        et.setInputType(inputType);
        et.setGravity(Gravity.RIGHT);
        et.setSingleLine(true);
        et.setMinHeight(dp(58));
        et.setBackgroundColor(Color.TRANSPARENT);
        row.addView(et, new LinearLayout.LayoutParams(0, -2, 1));

        if (hint.contains("لوكيشن")) {
            Button current = new Button(this);
            current.setText("موقعي");
            current.setTextSize(13);
            current.setTextColor(BRAND);
            current.setAllCaps(false);
            current.setBackground(rounded(BRAND_LIGHT, Color.rgb(254, 202, 202), dp(14)));
            current.setOnClickListener(v -> fillCurrentLocation(et));
            LinearLayout.LayoutParams locLp = new LinearLayout.LayoutParams(dp(86), dp(48));
            locLp.setMargins(dp(6), 0, 0, 0);
            row.addView(current, locLp);
        }

        int inputClass = inputType & InputType.TYPE_MASK_CLASS;
        if (inputClass != InputType.TYPE_CLASS_PHONE) {
            Button mic = new Button(this);
            mic.setText("صوت");
            mic.setTextSize(13);
            mic.setTextColor(BRAND);
            mic.setAllCaps(false);
            mic.setBackground(rounded(BRAND_LIGHT, Color.rgb(254, 202, 202), dp(14)));
            mic.setOnClickListener(v -> toggleManualVoice(mic, et));
            LinearLayout.LayoutParams micLp = new LinearLayout.LayoutParams(dp(78), dp(48));
            micLp.setMargins(dp(6), 0, 0, 0);
            row.addView(mic, micLp);
        }

        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(6), 0, dp(9));
        content.addView(row, lp);
        return et;
    }

    private void button(String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setMinHeight(dp(50));
        b.setBackground(rounded(BRAND, ACCENT, dp(10)));
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(8), 0, dp(8));
        content.addView(b, lp);
    }

    private void voiceAllButton(String text, EditText... fields) {
        Button b = new Button(this);
        b.setText(text + "\nاضغط مرة ثانية عند الانتهاء");
        b.setTextColor(BRAND_DARK);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setMinHeight(dp(58));
        b.setBackground(rounded(Color.WHITE, Color.rgb(254, 202, 202), dp(10)));
        b.setOnClickListener(v -> toggleManualVoice(b, null, fields));
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(3), 0, dp(8));
        content.addView(b, lp);
    }

    private void voiceToFieldButton(String text, EditText target) {
        Button b = new Button(this);
        b.setText(text + "\nاضغط مرة ثانية عند الانتهاء");
        b.setTextColor(BRAND_DARK);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setMinHeight(dp(58));
        b.setBackground(rounded(Color.WHITE, Color.rgb(254, 202, 202), dp(10)));
        b.setOnClickListener(v -> toggleManualVoice(b, target));
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(3), 0, dp(8));
        content.addView(b, lp);
    }

    private EditText hiddenInput(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        return et;
    }

    private void statusInputBar(EditText statusTarget) {
        TextView label = new TextView(this);
        label.setText("حالة العميل: " + txt(statusTarget));
        label.setTextColor(Color.rgb(71, 85, 105));
        label.setGravity(Gravity.RIGHT);
        label.setTextSize(14);
        content.addView(label, matchWrap());
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (String status : customerStatuses()) {
            Button b = new Button(this);
            b.setText(status);
            b.setTextSize(13);
            b.setAllCaps(false);
            styleStatusChoice(b, status.equals(txt(statusTarget)));
            b.setOnClickListener(v -> {
                statusTarget.setText(status);
                label.setText("حالة العميل: " + status);
                for (int i = 0; i < row.getChildCount(); i++) {
                    View child = row.getChildAt(i);
                    if (child instanceof Button) {
                        Button item = (Button) child;
                        styleStatusChoice(item, status.equals(item.getText().toString()));
                    }
                }
                toast("الحالة: " + status);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44));
            lp.setMargins(dp(4), 0, dp(4), dp(6));
            row.addView(b, lp);
        }
        hsv.addView(row);
        content.addView(hsv, matchWrap());
    }

    private void quickChoiceBar(EditText target, String label, String[] choices) {
        small(label + " - اختار بسرعة أو اكتب/استخدم الصوت");
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (String choice : choices) {
            Button b = new Button(this);
            b.setText(choice);
            b.setTextColor(BRAND_DARK);
            b.setTextSize(13);
            b.setAllCaps(false);
            b.setBackground(rounded(Color.WHITE, Color.rgb(203, 213, 225), dp(16)));
            b.setOnClickListener(v -> {
                target.setText(choice);
                target.setSelection(target.getText().length());
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44));
            lp.setMargins(dp(4), 0, dp(4), dp(6));
            row.addView(b, lp);
        }
        hsv.addView(row);
        content.addView(hsv, matchWrap());
    }

    private String[] extinguisherTypes() {
        return new String[]{"بودرة", "CO2", "ماء", "رغوة", "كيميائي رطب", "بودرة بعجلات"};
    }

    private String[] extinguisherWeights() {
        return new String[]{"6kg", "10kg", "12kg", "25kg", "50kg"};
    }

    private void styleStatusChoice(Button button, boolean selected) {
        String status = emptyForDb(button.getText().toString()).replace(" ✓", "");
        int fill = statusColor(status);
        boolean colored = fill != Color.WHITE;
        button.setTextColor(statusNeedsDarkText(status) ? BRAND_DARK : (colored || selected ? Color.WHITE : BRAND_DARK));
        button.setBackground(rounded(colored ? fill : (selected ? ACCENT : Color.WHITE),
                selected ? BRAND_DARK : Color.rgb(203, 213, 225), dp(16)));
    }

    private int statusColor(String status) {
        if ("استلام الطفايات".equals(status)) return Color.rgb(37, 99, 235);
        if ("تسليم جزئي".equals(status)) return Color.rgb(148, 163, 184);
        if ("تسليم الطفايات".equals(status)) return Color.rgb(22, 163, 74);
        if ("جاري الصيانة".equals(status)) return Color.rgb(250, 204, 21);
        return Color.WHITE;
    }

    private boolean statusNeedsDarkText(String status) {
        return "جاري الصيانة".equals(status) || "تسليم جزئي".equals(status);
    }

    private void quickImageBar() {
        small("صور الطفايات اختيارية، ممكن تختار أكتر من صورة أو تصور بالكاميرا.");
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.addView(compactActionButton("المعرض", this::chooseExtinguisherImage), new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(dp(8), 1);
        TextView spacer = new TextView(this);
        row.addView(spacer, gap);
        row.addView(compactActionButton("الكاميرا", this::takeExtinguisherPhoto), new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(4), 0, dp(8));
        content.addView(row, lp);
    }

    private Button compactActionButton(String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(BRAND_DARK);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackground(rounded(Color.WHITE, Color.rgb(248, 113, 113), dp(14)));
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void toggleManualVoice(Button button, EditText target, EditText... group) {
        if (manualVoiceActive && manualVoiceButton == button) {
            finishManualVoice();
            return;
        }
        if (manualVoiceActive) finishManualVoice();
        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            voiceTarget = target;
            voiceGroup = group;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_REQUEST);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("خاصية الإدخال الصوتي غير متاحة على هذا الجهاز");
            return;
        }

        voiceTarget = target;
        voiceGroup = group;
        manualVoiceButton = button;
        manualVoiceIdleText = button.getText().toString();
        manualVoiceCommittedText = "";
        manualVoiceCurrentText = "";
        manualVoiceActive = true;
        manualVoiceStopRequested = false;
        manualVoiceApplied = false;
        button.setText("إيقاف وتوزيع");
        button.setBackground(rounded(ACCENT, BRAND_DARK, dp(14)));
        button.setTextColor(Color.WHITE);
        beginManualListening();
        toast("اتكلم براحتك، ولما تخلص اضغط إيقاف وتوزيع");
    }

    private void beginManualListening() {
        destroySpeechRecognizer();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int error) {
                if (manualVoiceActive && !manualVoiceStopRequested) restartManualListening();
                else applyManualVoiceResult();
            }
            @Override public void onResults(Bundle results) {
                captureFinalVoiceResult(results);
                if (manualVoiceStopRequested) applyManualVoiceResult();
                else restartManualListening();
            }
            @Override public void onPartialResults(Bundle partialResults) {
                capturePartialVoiceResult(partialResults);
            }
            @Override public void onEvent(int eventType, Bundle params) {}
        });
        speechRecognizer.startListening(manualVoiceIntent());
    }

    private Intent manualVoiceIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 300000);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 300000);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300000);
        return intent;
    }

    private void restartManualListening() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (manualVoiceActive && !manualVoiceStopRequested) beginManualListening();
        }, 350);
    }

    private void capturePartialVoiceResult(Bundle bundle) {
        if (bundle == null) return;
        ArrayList<String> matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) manualVoiceCurrentText = matches.get(0);
    }

    private void captureFinalVoiceResult(Bundle bundle) {
        if (bundle == null) return;
        ArrayList<String> matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) return;
        String chunk = matches.get(0).trim();
        if (chunk.isEmpty()) return;
        if (manualVoiceCommittedText.isEmpty()) {
            manualVoiceCommittedText = chunk;
        } else if (!manualVoiceCommittedText.endsWith(chunk)) {
            manualVoiceCommittedText = (manualVoiceCommittedText + " " + chunk).trim();
        }
        manualVoiceCurrentText = "";
    }

    private void finishManualVoice() {
        manualVoiceStopRequested = true;
        try {
            if (speechRecognizer != null) speechRecognizer.stopListening();
        } catch (Exception ignored) {
        }
        new Handler(Looper.getMainLooper()).postDelayed(this::applyManualVoiceResult, 700);
    }

    private void applyManualVoiceResult() {
        if (manualVoiceApplied) return;
        manualVoiceApplied = true;
        String spoken = emptyForDb((manualVoiceCommittedText + " " + manualVoiceCurrentText).trim());
        resetManualVoiceButton();
        destroySpeechRecognizer();
        manualVoiceActive = false;
        manualVoiceStopRequested = false;
        if (spoken.isEmpty()) {
            toast("لم يتم التقاط كلام واضح");
            return;
        }
        if (voiceGroup != null && voiceGroup.length > 0) {
            fillVoiceGroup(spoken, voiceGroup);
        } else if (voiceTarget != null) {
            voiceTarget.setText(cleanVoiceText(spoken, voiceTarget.getInputType()));
            voiceTarget.setSelection(voiceTarget.getText().length());
            toast("تم إدخال الصوت");
        }
    }

    private void resetManualVoiceButton() {
        if (manualVoiceButton == null) return;
        manualVoiceButton.setText(manualVoiceIdleText);
        manualVoiceButton.setTextColor(BRAND_DARK);
        if ("صوت".equals(manualVoiceIdleText)) {
            manualVoiceButton.setBackground(rounded(BRAND_LIGHT, Color.rgb(254, 202, 202), dp(14)));
        } else {
            manualVoiceButton.setBackground(rounded(Color.WHITE, Color.rgb(254, 202, 202), dp(10)));
        }
        manualVoiceButton = null;
        manualVoiceIdleText = "";
    }

    private void destroySpeechRecognizer() {
        try {
            if (speechRecognizer != null) speechRecognizer.destroy();
        } catch (Exception ignored) {
        }
        speechRecognizer = null;
    }

    private void cancelManualVoice() {
        manualVoiceApplied = true;
        manualVoiceActive = false;
        manualVoiceStopRequested = true;
        resetManualVoiceButton();
        destroySpeechRecognizer();
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

    private int parseInt(String value, int fallback) {
        try {
            String clean = normalizeDigits(emptyForDb(value)).replaceAll("[^0-9-]", "");
            if (clean.isEmpty()) return fallback;
            return Integer.parseInt(clean);
        } catch (Exception e) {
            return fallback;
        }
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
        EditText phone = fieldByHint(fields, "رقم العميل");
        if (customer == null || type == null || count == null || price == null) return false;

        String normalized = normalizeDigits(text);
        if (phone != null) {
            Matcher phoneMatcher = Pattern.compile("(?:\\+?966|00966|05|5)[0-9\\s\\-]{7,13}").matcher(normalized);
            if (phoneMatcher.find()) {
                String phoneText = phoneMatcher.group().replaceAll("[^0-9+]", "");
                phone.setText(phoneText);
                normalized = (normalized.substring(0, phoneMatcher.start()) + " " + normalized.substring(phoneMatcher.end())).trim();
            }
        }
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
        if (hint.contains("اسم المكان")) return new String[]{"اسم المكان", "المكان", "الفرع", "اسم الفرع"};
        if (hint.contains("رقم")) return new String[]{"رقم العميل", "رقم", "الموبايل", "التليفون", "الهاتف"};
        if (hint.contains("لوكيشن")) return new String[]{"اللوكيشن", "لوكيشن", "الموقع", "العنوان"};
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
                "اسم المكان", "المكان", "الفرع", "اسم الفرع",
                "رقم العميل", "رقم", "الموبايل", "التليفون", "الهاتف",
                "اللوكيشن", "لوكيشن", "الموقع", "العنوان",
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

    private String rawVal(Cursor c, String column) {
        return emptyForDb(c.getString(c.getColumnIndexOrThrow(column)));
    }

    private int intVal(Cursor c, String column) {
        int index = c.getColumnIndex(column);
        if (index < 0 || c.isNull(index)) return 0;
        return c.getInt(index);
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    private String emptyForDb(String value) {
        return value == null ? "" : value;
    }

    private String displayCount(int count) {
        return count > 0 ? count + " طفاية" : "";
    }

    private boolean yesNo(String value) {
        String normalized = normalizeDigits(value).toLowerCase(Locale.US).trim();
        return normalized.equals("نعم") || normalized.equals("ايوه") || normalized.equals("أيوه") ||
                normalized.equals("اي") || normalized.equals("yes") || normalized.equals("y") ||
                normalized.equals("true") || normalized.equals("1");
    }

    private String yesNoLabel(int value) {
        return value == 1 ? "نعم" : "لا";
    }

    private String money(double value) {
        return String.format(Locale.US, "%.2f", value) + " ريال سعودي";
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
