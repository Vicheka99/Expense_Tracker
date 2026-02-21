package com.example.expense_tracker;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class Main_Activity extends AppCompatActivity {

    // Inside your Main_Activity.java onCreate method
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // This must match the name of your xml file (main_activity.xml)
        setContentView(R.layout.main_activity);
    }
}

