package com.safetyoffice.firemanager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class SyncManager {
    public static final int RC_SIGN_IN = 4041;
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

    public void upload(Runnable onDone) {
        FirebaseUser u = user();
        if (u == null) {
            if (onDone != null) onDone.run();
            return;
        }
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("snapshot", db.exportJson().toString());
            data.put("updated_at", System.currentTimeMillis());
            data.put("email", u.getEmail());
            firestore.collection("users").document(u.getUid())
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

    public void restore(Runnable onDone) {
        FirebaseUser u = user();
        if (u == null) {
            Toast.makeText(context, "سجل دخول جوجل الأول", Toast.LENGTH_SHORT).show();
            if (onDone != null) onDone.run();
            return;
        }
        firestore.collection("users").document(u.getUid()).get()
                .addOnSuccessListener(doc -> importDocument(doc, onDone))
                .addOnFailureListener(e -> {
                    Toast.makeText(context, "فشل الاسترجاع: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    if (onDone != null) onDone.run();
                });
    }

    public void autoUploadQuietly() {
        if (user() == null) return;
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("snapshot", db.exportJson().toString());
            data.put("updated_at", System.currentTimeMillis());
            firestore.collection("users").document(user().getUid()).set(data, SetOptions.merge());
        } catch (Exception ignored) {
        }
    }

    private void restoreThenUpload(Runnable onDone) {
        FirebaseUser u = user();
        if (u == null) return;
        firestore.collection("users").document(u.getUid()).get()
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
}
