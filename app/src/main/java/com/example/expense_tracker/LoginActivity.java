package com.example.expense_tracker;

import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException;
import com.google.android.material.button.MaterialButton;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Initialize SupabaseAuthManager
        SupabaseAuthManager authManager = SupabaseAuthManager.getInstance(this);

        // Check if user is already logged in
        if (authManager.isLoggedIn()) {
            Intent intent = new Intent(LoginActivity.this, Main_Activity.class);
            startActivity(intent);
            finish();
            return;
        }
        
        setContentView(R.layout.activity_login);

        MaterialButton btnGoogle = findViewById(R.id.btnGoogle);
        if (btnGoogle != null) {
            btnGoogle.setOnClickListener(v -> {
                btnGoogle.setEnabled(false);
                btnGoogle.setText("Connecting...");

                CredentialManager credentialManager = CredentialManager.create(LoginActivity.this);

                GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(getString(R.string.google_web_client_id))
                        .setAutoSelectEnabled(false)
                        .build();

                GetCredentialRequest request = new GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build();

                credentialManager.getCredentialAsync(
                        LoginActivity.this,
                        request,
                        new CancellationSignal(),
                        Executors.newSingleThreadExecutor(),
                        new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                            @Override
                            public void onResult(GetCredentialResponse result) {
                                handleCredential(result.getCredential());
                            }

                            @Override
                            public void onError(GetCredentialException e) {
                                runOnUiThread(() -> {
                                    btnGoogle.setEnabled(true);
                                    btnGoogle.setText("Continue with Google");
                                    Toast.makeText(LoginActivity.this, "Sign-in cancelled or failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    Log.e("LoginActivity", "CredentialManager error", e);
                                });
                            }
                        }
                );
            });
        }

        android.widget.TextView tvAddDeviceAccount = findViewById(R.id.tvAddDeviceAccount);
        if (tvAddDeviceAccount != null) {
            tvAddDeviceAccount.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_ADD_ACCOUNT);
                    intent.putExtra(android.provider.Settings.EXTRA_ACCOUNT_TYPES, new String[]{"com.google"});
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(LoginActivity.this, "Failed to open settings: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void handleCredential(Credential credential) {
        MaterialButton btnGoogle = findViewById(R.id.btnGoogle);
        if (credential instanceof CustomCredential &&
                GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            try {
                CustomCredential customCredential = (CustomCredential) credential;
                GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential.createFrom(customCredential.getData());
                String idToken = googleIdTokenCredential.getIdToken();
                
                runOnUiThread(() -> {
                    if (btnGoogle != null) {
                        btnGoogle.setText("Authenticating...");
                    }
                });

                SupabaseAuthManager.getInstance(this).signInWithGoogleToken(idToken, new SupabaseAuthManager.AuthCallback() {
                    @Override
                    public void onSuccess(String email) {
                        runOnUiThread(() -> {
                            Toast.makeText(LoginActivity.this, "Logged in as: " + email, Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(LoginActivity.this, Main_Activity.class);
                            startActivity(intent);
                            finish();
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        runOnUiThread(() -> {
                            if (btnGoogle != null) {
                                btnGoogle.setEnabled(true);
                                btnGoogle.setText("Continue with Google");
                            }
                            Toast.makeText(LoginActivity.this, "Supabase login failed: " + errorMessage, Toast.LENGTH_LONG).show();
                        });
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (btnGoogle != null) {
                        btnGoogle.setEnabled(true);
                        btnGoogle.setText("Continue with Google");
                    }
                    Toast.makeText(LoginActivity.this, "Failed to parse Google credential: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        } else {
            runOnUiThread(() -> {
                if (btnGoogle != null) {
                    btnGoogle.setEnabled(true);
                    btnGoogle.setText("Continue with Google");
                }
                Toast.makeText(LoginActivity.this, "Unexpected credential type", Toast.LENGTH_SHORT).show();
            });
        }
    }
}

