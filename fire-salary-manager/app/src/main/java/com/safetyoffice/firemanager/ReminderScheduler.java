package com.safetyoffice.firemanager;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class ReminderScheduler {
    private static final SimpleDateFormat DATE = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    public static void scheduleAll(Context context, DatabaseHelper db) {
        schedule(context, "monthly_report", 1, monthlyReportTrigger(), "تقرير الطفايات الشهري",
                "آخر الشهر وصل. افتح التطبيق وشوف عدد الطفايات وإجمالي المبلغ ونسبتك 25%.");

        Cursor e = db.all("extinguishers");
        try {
            while (e.moveToNext()) {
                schedule(context, "sticker", e.getLong(e.getColumnIndexOrThrow("id")),
                        e.getLong(e.getColumnIndexOrThrow("reminder_at")),
                        "تذكير استيكر الطفاية",
                        buildStickerMessage(e));
            }
        } finally {
            e.close();
        }

        scheduleTable(context, db, "safety_certificates", "certificate_date", "reminder_at",
                "تذكير شهادة السلامة", "شهادة السلامة قربت تنتهي للعميل: ");
        scheduleTable(context, db, "technical_reports", "report_date", "reminder_at",
                "تذكير تقرير فني", "التقرير الفني السنوي قرب للعميل: ");

        Cursor m = db.all("maintenance_contracts");
        try {
            while (m.moveToNext()) {
                long id = m.getLong(m.getColumnIndexOrThrow("id"));
                long start = m.getLong(m.getColumnIndexOrThrow("start_date"));
                String customer = m.getString(m.getColumnIndexOrThrow("customer_name"));
                String phone = m.getString(m.getColumnIndexOrThrow("phone"));
                String loc = m.getString(m.getColumnIndexOrThrow("location"));
                for (int i = 1; i <= 20; i++) {
                    long visit = addMonthsAvoidWeekend(start, i * 3);
                    long reminder = maintenanceReminder(visit);
                    schedule(context, "maintenance", id * 100 + i, reminder, "زيارة صيانة قريبة",
                            "زيارة " + customer + " يوم " + DATE.format(new Date(visit)) +
                                    "\nرقم: " + safe(phone) + "\nلوكيشن: " + safe(loc) +
                                    "\nطفايات: " + intVal(m, "extinguisher_count") +
                                    " | كواشف: " + intVal(m, "detector_count") +
                                    " | أجراس: " + intVal(m, "bell_count") +
                                    " | كواسر: " + intVal(m, "breaker_count"));
                }
            }
        } finally {
            m.close();
        }
    }

    private static void scheduleTable(Context context, DatabaseHelper db, String table, String dateCol,
                                      String reminderCol, String title, String prefix) {
        Cursor c = db.all(table);
        try {
            while (c.moveToNext()) {
                long id = c.getLong(c.getColumnIndexOrThrow("id"));
                long date = c.getLong(c.getColumnIndexOrThrow(dateCol));
                long reminder = c.getLong(c.getColumnIndexOrThrow(reminderCol));
                String customer = c.getString(c.getColumnIndexOrThrow("customer_name"));
                String phone = c.getString(c.getColumnIndexOrThrow("phone"));
                String loc = c.getString(c.getColumnIndexOrThrow("location"));
                schedule(context, table, id, reminder, title,
                        prefix + customer + "\nالتاريخ: " + DATE.format(new Date(date)) +
                                "\nرقم: " + safe(phone) + "\nلوكيشن: " + safe(loc));
            }
        } finally {
            c.close();
        }
    }

    public static long addMonthsAvoidWeekend(long from, int months) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(from);
        cal.add(Calendar.MONTH, months);
        while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY ||
                cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        return startOfDay(cal.getTimeInMillis());
    }

    public static long stickerReminder(long from) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(from);
        cal.add(Calendar.MONTH, 5);
        cal.add(Calendar.DAY_OF_MONTH, 15);
        return atHour(cal.getTimeInMillis(), 10);
    }

    public static long annualReminder(long date) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(date);
        cal.add(Calendar.YEAR, 1);
        cal.add(Calendar.DAY_OF_MONTH, -14);
        return atHour(cal.getTimeInMillis(), 10);
    }

    public static long maintenanceReminder(long visitDate) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(visitDate);
        cal.add(Calendar.DAY_OF_MONTH, -5);
        return atHour(cal.getTimeInMillis(), 10);
    }

    public static long parseDate(String value) throws Exception {
        return startOfDay(DATE.parse(value.trim()).getTime());
    }

    public static String formatDate(long value) {
        return DATE.format(new Date(value));
    }

    private static long monthlyReportTrigger() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        cal.set(Calendar.HOUR_OF_DAY, 20);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() < System.currentTimeMillis()) {
            cal.add(Calendar.MONTH, 1);
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        }
        return cal.getTimeInMillis();
    }

    private static long startOfDay(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.set(Calendar.HOUR_OF_DAY, 9);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private static long atHour(long time, int hour) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private static void schedule(Context context, String type, long id, long triggerAt, String title, String message) {
        if (triggerAt < System.currentTimeMillis()) return;
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("title", title);
        intent.putExtra("message", message);
        int requestCode = (type + id).hashCode();
        PendingIntent pending = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (alarm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            } else {
                alarm.set(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            }
        }
    }

    private static String buildStickerMessage(Cursor c) {
        return "قرب انتهاء صلاحية استيكر الطفاية للعميل: " +
                c.getString(c.getColumnIndexOrThrow("customer_name")) +
                "\nعدد الطفايات: " + c.getInt(c.getColumnIndexOrThrow("count")) +
                "\nالنوع: " + safe(c.getString(c.getColumnIndexOrThrow("extinguisher_type"))) +
                "\nالوزن: " + safe(c.getString(c.getColumnIndexOrThrow("weight"))) +
                "\nرقم العميل: " + safe(c.getString(c.getColumnIndexOrThrow("phone"))) +
                "\nلوكيشن: " + safe(c.getString(c.getColumnIndexOrThrow("location"))) +
                "\nافتح التطبيق وحول رسالة التذكير للعميل.";
    }

    private static String safe(String s) {
        return s == null || s.trim().isEmpty() ? "-" : s;
    }

    private static int intVal(Cursor c, String column) {
        int index = c.getColumnIndex(column);
        if (index < 0 || c.isNull(index)) return 0;
        return c.getInt(index);
    }
}
