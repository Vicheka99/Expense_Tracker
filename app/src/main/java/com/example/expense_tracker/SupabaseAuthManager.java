package com.example.expense_tracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;

public class SupabaseAuthManager {

    private static final String PREF_NAME = "supabase_auth_prefs";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_EMAIL = "user_email";
    
    private static SupabaseAuthManager instance;
    private final SharedPreferences prefs;
    private final OkHttpClient httpClient;
    private final String supabaseUrl;
    private final String supabaseAnonKey;
    private final Handler mainHandler;

    private SupabaseAuthManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.httpClient = new OkHttpClient();
        
        String rawUrl = context.getString(R.string.supabase_url);
        if (rawUrl != null) {
            rawUrl = rawUrl.trim();
            if (rawUrl.endsWith("/rest/v1/")) {
                rawUrl = rawUrl.substring(0, rawUrl.length() - 9);
            } else if (rawUrl.endsWith("/rest/v1")) {
                rawUrl = rawUrl.substring(0, rawUrl.length() - 8);
            }
            if (rawUrl.endsWith("/")) {
                rawUrl = rawUrl.substring(0, rawUrl.length() - 1);
            }
        }
        this.supabaseUrl = rawUrl;
        
        this.supabaseAnonKey = context.getString(R.string.supabase_anon_key);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized SupabaseAuthManager getInstance(Context context) {
        if (instance == null) {
            instance = new SupabaseAuthManager(context);
        }
        return instance;
    }

    public boolean isLoggedIn() {
        return prefs.getString(KEY_ACCESS_TOKEN, null) != null;
    }

    public String getUserEmail() {
        return prefs.getString(KEY_USER_EMAIL, "No user email");
    }

    public void logout() {
        prefs.edit().clear().apply();
    }

    public interface AuthCallback {
        void onSuccess(String email);
        void onFailure(String errorMessage);
    }

    public void signInWithGoogleToken(String idToken, AuthCallback callback) {
        String url = supabaseUrl + "/auth/v1/token?grant_type=id_token";
        
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("provider", "google");
            jsonBody.put("id_token", idToken);
        } catch (JSONException e) {
            callback.onFailure("Failed to build request body: " + e.getMessage());
            return;
        }

        RequestBody body = RequestBody.create(
                jsonBody.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Content-Type", "application/json")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> callback.onFailure("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response bodyResponse = response) {
                    String responseBody = bodyResponse.body() != null ? bodyResponse.body().string() : "";
                    if (!response.isSuccessful()) {
                        mainHandler.post(() -> {
                            try {
                                JSONObject errObj = new JSONObject(responseBody);
                                String errorDescription = errObj.optString("error_description", errObj.optString("error", "Unknown error"));
                                callback.onFailure("Supabase error: " + errorDescription);
                            } catch (JSONException e) {
                                callback.onFailure("Supabase error status: " + response.code() + " " + responseBody);
                            }
                        });
                        return;
                    }

                    try {
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        String accessToken = jsonResponse.getString("access_token");
                        String refreshToken = jsonResponse.getString("refresh_token");
                        
                        JSONObject userObject = jsonResponse.getJSONObject("user");
                        String email = userObject.getString("email");
                        String userId = userObject.optString("id", "");

                        String fullName = "";
                        String avatarUrl = "";
                        JSONObject metadata = userObject.optJSONObject("user_metadata");
                        if (metadata != null) {
                            fullName = metadata.optString("full_name", "");
                            avatarUrl = metadata.optString("avatar_url", "");
                        }

                        prefs.edit()
                                .putString(KEY_ACCESS_TOKEN, accessToken)
                                .putString(KEY_REFRESH_TOKEN, refreshToken)
                                .putString(KEY_USER_EMAIL, email)
                                .putString("user_id", userId)
                                .putString("user_full_name", fullName)
                                .putString("user_avatar_url", avatarUrl)
                                .apply();

                        mainHandler.post(() -> callback.onSuccess(email));

                    } catch (JSONException e) {
                        mainHandler.post(() -> callback.onFailure("Failed to parse response: " + e.getMessage()));
                    }
                }
            }
        });
    }

    public String getUserFullName() {
        return prefs.getString("user_full_name", "");
    }

    public String getUserAvatarUrl() {
        return prefs.getString("user_avatar_url", "");
    }

    public String getUserId() {
        return prefs.getString("user_id", "");
    }

    public void syncTransaction(DatabaseHelper.Transaction tx, final Runnable onSuccess, final Runnable onFailure) {
        String token = prefs.getString(KEY_ACCESS_TOKEN, null);
        if (token == null) {
            if (onFailure != null) onFailure.run();
            return;
        }
        
        String url = supabaseUrl + "/rest/v1/transactions";
        JSONObject json = new JSONObject();
        try {
            json.put("id", tx.id);
            json.put("user_email", tx.userEmail);
            json.put("type", tx.type);
            json.put("amount", tx.amount);
            json.put("category", tx.category);
            json.put("date", tx.date);
            json.put("note", tx.note);
            // Crop base64 image if too long, or send full string. 
            // In public schemas, base64 text columns can store long values.
            json.put("receipt_image", tx.receiptImage);
        } catch (JSONException e) {
            e.printStackTrace();
            if (onFailure != null) onFailure.run();
            return;
        }

        RequestBody body = RequestBody.create(
                json.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates") // Merge if key matches
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                if (onFailure != null) onFailure.run();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (response.isSuccessful()) {
                        if (onSuccess != null) onSuccess.run();
                    } else {
                        // Log error status
                        System.out.println("Sync failed with status: " + response.code() + " " + response.message());
                        if (onFailure != null) onFailure.run();
                    }
                }
            }
        });
    }

    public interface FetchTransactionsCallback {
        void onSuccess(java.util.List<DatabaseHelper.Transaction> transactions);
        void onFailure(String errorMessage);
    }

    public void fetchTransactionsFromServer(final FetchTransactionsCallback callback) {
        String token = prefs.getString(KEY_ACCESS_TOKEN, null);
        String email = prefs.getString(KEY_USER_EMAIL, "guest");
        if (token == null) {
            callback.onFailure("User is not logged in");
            return;
        }

        String url = supabaseUrl + "/rest/v1/transactions?user_email=eq." + email;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> callback.onFailure("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    String responseBody = r.body() != null ? r.body().string() : "";
                    if (!response.isSuccessful()) {
                        mainHandler.post(() -> callback.onFailure("Failed to fetch: status " + response.code() + " " + responseBody));
                        return;
                    }

                    try {
                        org.json.JSONArray jsonArray = new org.json.JSONArray(responseBody);
                        final java.util.List<DatabaseHelper.Transaction> list = new java.util.ArrayList<>();
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject obj = jsonArray.getJSONObject(i);
                            String id = obj.getString("id");
                            String userEmail = obj.getString("user_email");
                            String type = obj.getString("type");
                            double amount = obj.getDouble("amount");
                            String category = obj.getString("category");
                            String date = obj.getString("date");
                            String note = obj.optString("note", "");
                            String receiptImage = obj.optString("receipt_image", null);
                            if ("null".equals(receiptImage)) {
                                receiptImage = null;
                            }
                            
                            DatabaseHelper.Transaction tx = new DatabaseHelper.Transaction(
                                    id, userEmail, type, amount, category, date, note, receiptImage, 1
                            );
                            list.add(tx);
                        }
                        mainHandler.post(() -> callback.onSuccess(list));
                    } catch (JSONException e) {
                        mainHandler.post(() -> callback.onFailure("Parsing error: " + e.getMessage()));
                    }
                }
            }
        });
    }
}
