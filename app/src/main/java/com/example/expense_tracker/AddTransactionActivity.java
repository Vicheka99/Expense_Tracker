package com.example.expense_tracker;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;

public class AddTransactionActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide Action Bar for a clean UI
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // This links to the layout we designed
        setContentView(R.layout.activity_add_transaction);

        // --- BACK BUTTON LOGIC ---
        // Find the back arrow button by its ID from the XML
        ImageView btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // This closes the current activity and returns to the previous screen
                finish();
            }
        });
    }
}