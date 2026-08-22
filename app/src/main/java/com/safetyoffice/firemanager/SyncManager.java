package com.safetyoffice.firemanager;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SyncManager {
    public static final int RC_SIGN_IN = 4041;
    private static final String IMAGE_UPLOAD_URL = "https://smmnoon.com/fire/upload.php";
    private static final String IMAGE_UPLOAD_TOKEN = "FireManager_smmnoon_2026_7391";
    private static final String DEFAULT_SUPERVISOR_EMAIL = "mohamede669@gmail.com";
    private static final String UPDATE_APK_URL = "https://smmnoon.com/fire/fire-salary-manager.apk";
    private final Context context;
    private final DatabaseHelper db;
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;

    public SyncManager(Context context, DatabaseHelper db) {
        this.context = context;
        this.db = db;
        this.auth = FirebaseAuth.getInstance();
        this.firestore = FirebaseFirestore.getInstance();
    }

    public FirebaseUser user() {
        return auth.getCurrentUser();
    }

    public void signIn(Activity activity) {
        GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(com.safetyoffice.firemanager.R.string.default_web_client_id))
                .requestEmail()
                .build();
        GoogleSignInClient client = GoogleSignIn.getClient(activity, options);
        activity.startActivityForResult(client.getSignInIntent(), RC_SIGN_IN);
    }

    public void handleSignInResult(Intent data, Runnable onDone) {
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
            auth.signInWithCredential(credential)
                    .addOnSuccessListener(result -> {
                        Toast.makeText(context, "تم تسجيل الدخول بحساب جوجل", Toast.LENGTH_SHORT).show();
                        restoreThenUpload(onDone);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(context, "فشل تسجيل الدخول: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        if (onDone != null) onDone.run();
                    });
        } catch (ApiException e) {
            Toast.makeText(context, "لم يتم تسجيل الدخول. راجع إعدادات Google في Firebase.", Toast.LENGTH_LONG).show();
            if (onDone != null) onDone.run();
        } catch (Exception e) {
            Toast.makeText(context, "لم يتم تسجيل الدخول: " + e.getMessage(), Toast.LENGTH_LONG).show();
            if (onDone != null) onDone.run();
        }
    }

    public void signOut(Runnable onDone) {
        auth.signOut();
        Toast.makeText(context, "تم تسجيل الخروج", Toast.LENGTH_SHORT).show();
        if (onDone != null) onDone.run();
    }

    public void publishRequiredUpdate(int versionCode, String versionName) {
        FirebaseUser u = user();
        if (u == null || u.getEmail() == null || !DEFAULT_SUPERVISOR_EMAIL.equalsIgnoreCase(u.getEmail())) return;
        Map<String, Object> data = new HashMap<>();
        data.put("min_version_code", versionCode);
        data.put("version_name", versionName);
        data.put("apk_url", UPDATE_APK_URL);
        data.put("supervisor_email", u.getEmail());
        data.put("updated_at", System.currentTimeMillis());
        firestore.collection("fire_manager_app_config").document("current").set(data, SetOptions.merge());
    }

    public void checkRequiredUpdate(int currentVersionCode, UpdateListener listener) {
        firestore.collection("fire_manager_app_config").document("current").get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    long minVersion = doc.getLong("min_version_code") == null ? 0 : doc.getLong("min_version_code");
                    if (minVersion <= currentVersionCode) return;
                    String versionName = doc.getString("version_name");
                    String apkUrl = doc.getString("apk_url");
                    if (listener != null) listener.onUpdateRequired(versionName, apkUrl == null ? UPDATE_APK_URL : apkUrl);
                });
    }

    public interface UpdateListener {
        void onUpdateRequired(String versionName, String apkUrl);
    }

    public interface AssignmentListener {
        void onAssignmentsImported(int count, String latestCustomer);
        void onError(String message);
    }

    public interface CompletedAssignmentsListener {
        void onLoaded(List<CompletedAssignment> assignments);
        void onError(String message);
    }

    public void upload(Runnable onDone) {
        FirebaseUser u = user();
        if (u == null) {
            if (onDone != null) onDone.run();
            return;
        }
        uploadCloudMedia(u, () -> uploadSnapshot(u, onDone));
    }

    private void uploadSnapshot(FirebaseUser u, Runnable onDone) {
        try {
            Map<String, Object> data = snapshotData(u);
            firestore.collection("fire_manager_users").document(u.getUid())
                    .set(data, SetOptions.merge())
                    .addOnSuccessListener(v -> {
                        Toast.makeText(context, "تم حفظ البيانات على جوجل", Toast.LENGTH_SHORT).show();
                        if (onDone != null) onDone.run();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(context, "فشل الحفظ على جوجل: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        if (onDone != null) onDone.run();
                    });
        } catch (Exception e) {
            Toast.makeText(context, "فشل تجهيز البيانات: " + e.getMessage(), Toast.LENGTH_LONG).show();
            if (onDone != null) onDone.run();
        }
    }

    public void uploadTeam(String teamCode, Runnable onDone) {
        FirebaseUser u = user();
        String code = cleanTeamCode(teamCode);
        if (u == null) {
            Toast.makeText(context, "سجل دخول جوجل الأول", Toast.LENGTH_SHORT).show();
            if (onDone != null) onDone.run();
            return;
        }
        if (code.length() == 0) {
            Toast.makeText(context, "اكتب كود الفريق الأول", Toast.LENGTH_SHORT).show();
            if (onDone != null) onDone.run();
            return;
        }
        uploadCloudMedia(u, () -> uploadTeamSnapshot(u, code, onDone));
    }

    private void uploadTeamSnapshot(FirebaseUser u, String code, Runnable onDone) {
        try {
            Map<String, Object> data = snapshotData(u);
            data.put("team_code", code);
            firestore.collection("fire_manager_teams").document(code)
                    .collection("members").document(u.getUid())
                    .set(data, SetOptions.merge())
                    .addOnSuccessListener(v -> {
                        Toast.makeText(context, "تم رفع بياناتك للفريق", Toast.LENGTH_SHORT).show();
                        if (onDone != null) onDone.run();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(context, "فشل رفع بيانات الفريق: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        if (onDone != null) onDone.run();
                    });
        } catch (Exception e) {
            Toast.makeText(context, "فشل تجهيز بيانات الفريق: " + e.getMessage(), Toast.LENGTH_LONG).show();
            if (onDone != null) onDone.run();
        }
    }

    public void restore(Runnable onDone) {
        FirebaseUser u = user();
        if (u == null) {
            Toast.makeText(context, "سجل دخول جوجل الأول", Toast.LENGTH_SHORT).show();
            if (onDone != null) onDone.run();
            return;
        }
        firestore.collection("fire_manager_users").document(u.getUid()).get()
                .addOnSuccessListener(doc -> importDocument(doc, onDone))
                .addOnFailureListener(e -> {
                    Toast.makeText(context, "فشل الاسترجاع: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    if (onDone != null) onDone.run();
                });
    }

    public void restoreTeam(String teamCode, Runnable onDone) {
        FirebaseUser u = user();
        String code = cleanTeamCode(teamCode);
        if (u == null) {
            Toast.makeText(context, "سجل دخول جوجل الأول", Toast.LENGTH_SHORT).show();
            if (onDone != null) onDone.run();
            return;
        }
        if (code.length() == 0) {
            Toast.makeText(context, "اكتب كود الفريق الأول", Toast.LENGTH_SHORT).show();
            if (onDone != null) onDone.run();
            return;
        }
        firestore.collection("fire_manager_teams").document(code).collection("members").get()
                .addOnSuccessListener(query -> {
                    int imported = 0;
                    try {
                        for (DocumentSnapshot doc : query.getDocuments()) {
                            if (doc.getId().equals(u.getUid())) continue;
                            String snapshot = doc.getString("snapshot");
                            if (snapshot == null) continue;
                            db.importTeamJson(new JSONObject(snapshot));
                            imported++;
                        }
                        ReminderScheduler.scheduleAll(context, db);
                        Toast.makeText(context, imported == 0 ? "لا توجد بيانات جديدة من الفريق" : "تم استرجاع بيانات " + imported + " عضو من الفريق", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(context, "فشل قراءة بيانات الفريق: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                    if (onDone != null) onDone.run();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(context, "فشل استرجاع بيانات الفريق: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    if (onDone != null) onDone.run();
                });
    }

    public void assignCustomerToTeam(String teamCode, JSONObject snapshot, String customerName, String phone,
                                     String place, String location, Runnable onDone) {
        FirebaseUser u = user();
        String code = cleanTeamCode(teamCode);
        if (u == null) {
            Toast.makeText(context, "سجل دخول جوجل الأول", Toast.LENGTH_SHORT).show();
            if (onDone != null) onDone.run();
            return;
        }
        if (code.length() == 0) {
            Toast.makeText(context, "اكتب كود الفريق الأول", Toast.LENGTH_SHORT).show();
            if (onDone != null) onDone.run();
            return;
        }
        try {
            DocumentReference ref = firestore.collection("fire_manager_assignments").document(code)
                    .collection("items").document();
            Map<String, Object> data = new HashMap<>();
            data.put("assignment_id", ref.getId());
            data.put("type", "customer");
            data.put("status", "open");
            data.put("team_code", code);
            data.put("customer_name", customerName);
            data.put("phone", phone);
            data.put("place_name", place);
            data.put("location", location);
            data.put("snapshot", snapshot.toString());
            data.put("assigned_by_email", u.getEmail());
            data.put("supervisor_email", DEFAULT_SUPERVISOR_EMAIL);
            data.put("created_at", System.currentTimeMillis());
            ref.set(data)
                    .addOnSuccessListener(v -> {
                        Toast.makeText(context, "تم تحويل العميل للفريق", Toast.LENGTH_SHORT).show();
                        if (onDone != null) onDone.run();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(context, "فشل تحويل العميل: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        if (onDone != null) onDone.run();
                    });
        } catch (Exception e) {
            Toast.makeText(context, "فشل تجهيز تحويل العميل: " + e.getMessage(), Toast.LENGTH_LONG).show();
            if (onDone != null) onDone.run();
        }
    }

    public void restoreAssignments(String teamCode, Runnable onDone) {
        FirebaseUser u = user();
        String code = cleanTeamCode(teamCode);
        if (u == null) {
            Toast.makeText(context, "سجل دخول جوجل الأول", Toast.LENGTH_SHORT).show();
            if (onDone != null) onDone.run();
            return;
        }
        if (code.length() == 0) {
            Toast.makeText(context, "اكتب كود الفريق الأول", Toast.LENGTH_SHORT).show();
            if (onDone != null) onDone.run();
            return;
        }
        firestore.collection("fire_manager_assignments").document(code).collection("items")
                .whereEqualTo("status", "open").get()
                .addOnSuccessListener(query -> {
                    int imported = 0;
                    try {
                        for (DocumentSnapshot doc : query.getDocuments()) {
                            String snapshot = doc.getString("snapshot");
                            if (snapshot == null) continue;
                            JSONObject root = new JSONObject(snapshot);
                            db.importTeamJson(root);
                            db.saveTeamAssignment(doc.getId(), code,
                                    doc.getString("customer_name"),
                                    doc.getString("phone"),
                                    doc.getString("place_name"),
                                    doc.getString("location"));
                            imported++;
                        }
                        ReminderScheduler.scheduleAll(context, db);
                        Toast.makeText(context, imported == 0 ? "لا توجد تكليفات مفتوحة" : "تم استلام " + imported + " تكليف", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(context, "فشل استلام التكليفات: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                    if (onDone != null) onDone.run();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(context, "فشل استلام التكليفات: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    if (onDone != null) onDone.run();
                });
    }

    public ListenerRegistration listenOpenAssignments(String teamCode, AssignmentListener listener) {
        FirebaseUser u = user();
        String code = cleanTeamCode(teamCode);
        if (u == null || code.length() == 0) return null;
        return firestore.collection("fire_manager_assignments").document(code).collection("items")
                .whereEqualTo("status", "open")
                .addSnapshotListener((query, error) -> {
                    if (error != null) {
                        if (listener != null) listener.onError(error.getMessage());
                        return;
                    }
                    importOpenAssignments(query, code, listener);
                });
    }

    private void importOpenAssignments(QuerySnapshot query, String code, AssignmentListener listener) {
        if (query == null) return;
        int imported = 0;
        String latestCustomer = "";
        try {
            for (DocumentSnapshot doc : query.getDocuments()) {
                String localStatus = db.teamAssignmentStatus(doc.getId());
                if ("open".equals(localStatus)) continue;
                String snapshot = doc.getString("snapshot");
                if (snapshot == null) continue;
                if (localStatus.length() == 0) db.importTeamJson(new JSONObject(snapshot));
                db.saveTeamAssignment(doc.getId(), code,
                        doc.getString("customer_name"),
                        doc.getString("phone"),
                        doc.getString("place_name"),
                        doc.getString("location"));
                imported++;
                latestCustomer = doc.getString("customer_name");
            }
            if (imported > 0) ReminderScheduler.scheduleAll(context, db);
            if (listener != null && imported > 0) listener.onAssignmentsImported(imported, latestCustomer == null ? "" : latestCustomer);
        } catch (Exception e) {
            if (listener != null) listener.onError(e.getMessage());
        }
    }

    public void completeAssignment(String teamCode, String assignmentId, JSONObject completedSnapshot, Runnable onDone) {
        FirebaseUser u = user();
        String code = cleanTeamCode(teamCode);
        if (u == null || code.length() == 0 || assignmentId == null || assignmentId.length() == 0) {
            Toast.makeText(context, "بيانات التكليف غير مكتملة", Toast.LENGTH_SHORT).show();
            if (onDone != null) onDone.run();
            return;
        }
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("status", "completed");
            data.put("completed_snapshot", completedSnapshot.toString());
            data.put("completed_by_email", u.getEmail());
            data.put("completed_at", System.currentTimeMillis());
            firestore.collection("fire_manager_assignments").document(code)
                    .collection("items").document(assignmentId)
                    .set(data, SetOptions.merge())
                    .addOnSuccessListener(v -> {
                        db.closeTeamAssignment(assignmentId);
                        Toast.makeText(context, "تم إرسال التحديث للمشرف", Toast.LENGTH_SHORT).show();
                        if (onDone != null) onDone.run();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(context, "فشل إرسال التحديث: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        if (onDone != null) onDone.run();
                    });
        } catch (Exception e) {
            Toast.makeText(context, "فشل تجهيز التحديث: " + e.getMessage(), Toast.LENGTH_LONG).show();
            if (onDone != null) onDone.run();
        }
    }

    public void restoreCompletedAssignments(String teamCode, Runnable onDone) {
        FirebaseUser u = user();
        String code = cleanTeamCode(teamCode);
        if (u == null) {
            Toast.makeText(context, "سجل دخول جوجل الأول", Toast.LENGTH_SHORT).show();
            if (onDone != null) onDone.run();
            return;
        }
        if (code.length() == 0) {
            Toast.makeText(context, "اكتب كود الفريق الأول", Toast.LENGTH_SHORT).show();
            if (onDone != null) onDone.run();
            return;
        }
        firestore.collection("fire_manager_assignments").document(code).collection("items")
                .whereEqualTo("status", "completed").get()
                .addOnSuccessListener(query -> {
                    int imported = 0;
                    try {
                        for (DocumentSnapshot doc : query.getDocuments()) {
                            String snapshot = doc.getString("completed_snapshot");
                            if (snapshot == null) continue;
                            db.importTeamCompletedJson(new JSONObject(snapshot));
                            doc.getReference().set(reviewedMarker(), SetOptions.merge());
                            imported++;
                        }
                        ReminderScheduler.scheduleAll(context, db);
                        Toast.makeText(context, imported == 0 ? "لا توجد تحديثات منتهية" : "تم استلام " + imported + " تحديث من الفريق", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(context, "فشل استلام تحديثات الفريق: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                    if (onDone != null) onDone.run();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(context, "فشل استلام تحديثات الفريق: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    if (onDone != null) onDone.run();
                });
    }

    public void fetchCompletedAssignments(String teamCode, CompletedAssignmentsListener listener) {
        FirebaseUser u = user();
        String code = cleanTeamCode(teamCode);
        if (u == null || code.length() == 0) {
            if (listener != null) listener.onError("بيانات الفريق غير مكتملة");
            return;
        }
        firestore.collection("fire_manager_assignments").document(code).collection("items").get()
                .addOnSuccessListener(query -> {
                    List<CompletedAssignment> result = new ArrayList<>();
                    for (DocumentSnapshot doc : query.getDocuments()) {
                        try {
                            result.add(new CompletedAssignment(
                                    code,
                                    doc.getId(),
                                    doc.getString("customer_name"),
                                    doc.getString("phone"),
                                    doc.getString("place_name"),
                                    doc.getString("location"),
                                    doc.getString("status"),
                                    doc.getString("snapshot"),
                                    doc.getString("completed_snapshot"),
                                    doc.getString("completed_by_email"),
                                    asLong(doc.get("completed_at"))
                            ));
                        } catch (Exception ignored) {
                        }
                    }
                    if (listener != null) listener.onLoaded(result);
                })
                .addOnFailureListener(e -> {
                    if (listener != null) listener.onError(e.getMessage());
                });
    }

    public void approveCompletedAssignment(CompletedAssignment item, Runnable onDone) {
        if (item == null || item.completedSnapshot == null) {
            Toast.makeText(context, "بيانات التحديث غير مكتملة", Toast.LENGTH_SHORT).show();
            if (onDone != null) onDone.run();
            return;
        }
        try {
            db.importTeamCompletedJson(new JSONObject(item.completedSnapshot));
            firestore.collection("fire_manager_assignments").document(item.teamCode)
                    .collection("items").document(item.assignmentId)
                    .set(reviewedMarker(), SetOptions.merge())
                    .addOnSuccessListener(v -> {
                        ReminderScheduler.scheduleAll(context, db);
                        Toast.makeText(context, "تم اعتماد تحديث الفني", Toast.LENGTH_SHORT).show();
                        if (onDone != null) onDone.run();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(context, "فشل اعتماد التحديث: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        if (onDone != null) onDone.run();
                    });
        } catch (Exception e) {
            Toast.makeText(context, "فشل قراءة تحديث الفني: " + e.getMessage(), Toast.LENGTH_LONG).show();
            if (onDone != null) onDone.run();
        }
    }

    public void rejectCompletedAssignment(CompletedAssignment item, Runnable onDone) {
        if (item == null) {
            if (onDone != null) onDone.run();
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("status", "open");
        if (item.completedSnapshot != null && item.completedSnapshot.length() > 0) {
            data.put("snapshot", item.completedSnapshot);
        }
        data.put("last_rejected_snapshot", item.completedSnapshot);
        data.put("supervisor_rejected_at", System.currentTimeMillis());
        firestore.collection("fire_manager_assignments").document(item.teamCode)
                .collection("items").document(item.assignmentId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(v -> {
                    Toast.makeText(context, "تم رفض تحديث الفني", Toast.LENGTH_SHORT).show();
                    if (onDone != null) onDone.run();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(context, "فشل رفض التحديث: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    if (onDone != null) onDone.run();
                });
    }

    private long asLong(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return value == null ? 0 : Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private Map<String, Object> reviewedMarker() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "supervisor_received");
        data.put("supervisor_received_at", System.currentTimeMillis());
        return data;
    }

    public void autoUploadQuietly() {
        if (user() == null) return;
        FirebaseUser u = user();
        uploadCloudMedia(u, () -> {
            try {
            Map<String, Object> data = snapshotData(u);
            firestore.collection("fire_manager_users").document(u.getUid()).set(data, SetOptions.merge());
            String teamCode = cleanTeamCode(db.setting("team_code", ""));
            if (teamCode.length() > 0) {
                Map<String, Object> teamData = new HashMap<>(data);
                teamData.put("team_code", teamCode);
                firestore.collection("fire_manager_teams").document(teamCode)
                        .collection("members").document(u.getUid())
                        .set(teamData, SetOptions.merge());
            }
            } catch (Exception ignored) {
            }
        });
    }

    private void restoreThenUpload(Runnable onDone) {
        FirebaseUser u = user();
        if (u == null) return;
        firestore.collection("fire_manager_users").document(u.getUid()).get()
                .addOnSuccessListener(doc -> importDocument(doc, () -> upload(onDone)))
                .addOnFailureListener(e -> upload(onDone));
    }

    private void importDocument(DocumentSnapshot doc, Runnable onDone) {
        try {
            if (doc.exists() && doc.getString("snapshot") != null) {
                db.importJson(new JSONObject(doc.getString("snapshot")));
                ReminderScheduler.scheduleAll(context, db);
                Toast.makeText(context, "تم استرجاع بيانات جوجل", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "لا توجد نسخة محفوظة على جوجل حتى الآن", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, "فشل قراءة نسخة جوجل: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        if (onDone != null) onDone.run();
    }

    private Map<String, Object> snapshotData(FirebaseUser user) throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("snapshot", db.exportJson().toString());
        data.put("updated_at", System.currentTimeMillis());
        data.put("email", user.getEmail());
        return data;
    }

    private void uploadCloudMedia(FirebaseUser user, Runnable onDone) {
        new Thread(() -> {
            List<MediaRef> refs = localMediaRefs();
            for (MediaRef item : refs) {
                String uploadedUrl = uploadMediaToHosting(user, item);
                if (uploadedUrl == null || uploadedUrl.length() == 0) continue;
                ContentValues cv = new ContentValues();
                cv.put(item.column, uploadedUrl);
                db.update(item.table, cv, "id=?", String.valueOf(item.id));
            }
            runOnMain(onDone);
        }).start();
    }

    private String uploadMediaToHosting(FirebaseUser user, MediaRef item) {
        HttpURLConnection connection = null;
        String boundary = "----FireManager" + System.currentTimeMillis();
        try {
            Uri uri = Uri.parse(item.uri);
            String mime = context.getContentResolver().getType(uri);
            if (mime == null || mime.trim().isEmpty()) mime = "image/jpeg";
            String fileName = item.table + "_" + item.id + ".jpg";

            connection = (HttpURLConnection) new URL(IMAGE_UPLOAD_URL).openConnection();
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(30000);
            connection.setRequestMethod("POST");
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("X-Upload-Token", IMAGE_UPLOAD_TOKEN);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            DataOutputStream out = new DataOutputStream(connection.getOutputStream());
            writeFormField(out, boundary, "token", IMAGE_UPLOAD_TOKEN);
            writeFormField(out, boundary, "team_code", db.setting("team_code", "team"));
            writeFormField(out, boundary, "customer", item.table + "_" + item.id);
            writeFormField(out, boundary, "device", user.getUid());
            writeFileField(out, boundary, "file", fileName, mime, uri);
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();

            int code = connection.getResponseCode();
            InputStream response = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            JSONObject json = new JSONObject(readAll(response));
            if (code >= 200 && code < 300 && json.optBoolean("ok")) {
                return json.optString("url", "");
            }
        } catch (Exception ignored) {
        } finally {
            if (connection != null) connection.disconnect();
        }
        return null;
    }

    private void writeFormField(DataOutputStream out, String boundary, String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void writeFileField(DataOutputStream out, String boundary, String name, String fileName, String mime, Uri uri) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        InputStream input = context.getContentResolver().openInputStream(uri);
        if (input == null) throw new IllegalStateException("Cannot open image");
        byte[] buffer = new byte[8192];
        int read;
        try {
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            input.close();
        }
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private String readAll(InputStream input) throws Exception {
        if (input == null) return "{}";
        byte[] buffer = new byte[4096];
        StringBuilder builder = new StringBuilder();
        int read;
        try {
            while ((read = input.read(buffer)) != -1) {
                builder.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            }
        } finally {
            input.close();
        }
        return builder.toString();
    }

    private void runOnMain(Runnable action) {
        if (action == null) return;
        new Handler(Looper.getMainLooper()).post(action);
    }

    private List<MediaRef> localMediaRefs() {
        List<MediaRef> refs = new ArrayList<>();
        collectLocalMedia(refs, "extinguishers", "image_uri");
        collectLocalMedia(refs, "extinguisher_images", "uri");
        collectLocalMedia(refs, "customer_attachments", "uri");
        return refs;
    }

    private void collectLocalMedia(List<MediaRef> refs, String table, String column) {
        Cursor c = db.raw("SELECT id, " + column + " FROM " + table +
                " WHERE IFNULL(" + column + ",'')<>'' AND " + column + " NOT LIKE 'http%'");
        try {
            while (c.moveToNext()) {
                refs.add(new MediaRef(table, column, c.getLong(0), c.getString(1)));
            }
        } finally {
            c.close();
        }
    }

    private static class MediaRef {
        final String table;
        final String column;
        final long id;
        final String uri;

        MediaRef(String table, String column, long id, String uri) {
            this.table = table;
            this.column = column;
            this.id = id;
            this.uri = uri;
        }
    }

    public static class CompletedAssignment {
        public final String teamCode;
        public final String assignmentId;
        public final String customerName;
        public final String phone;
        public final String place;
        public final String location;
        public final String status;
        public final String originalSnapshot;
        public final String completedSnapshot;
        public final String completedByEmail;
        public final long completedAt;

        CompletedAssignment(String teamCode, String assignmentId, String customerName, String phone,
                            String place, String location, String status, String originalSnapshot, String completedSnapshot,
                            String completedByEmail, long completedAt) {
            this.teamCode = teamCode;
            this.assignmentId = assignmentId;
            this.customerName = customerName;
            this.phone = phone;
            this.place = place;
            this.location = location;
            this.status = status;
            this.originalSnapshot = originalSnapshot;
            this.completedSnapshot = completedSnapshot;
            this.completedByEmail = completedByEmail;
            this.completedAt = completedAt;
        }
    }

    private String cleanTeamCode(String raw) {
        if (raw == null) return "";
        return raw.trim().replace("/", "_");
    }
}
