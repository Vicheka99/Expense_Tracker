package com.example.expense_tracker;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class Main_Activity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // Put this first

        // Hide ActionBar to match the full-screen design
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.main_activity);
    }
}