package com.example.expense_tracker;

import android.content.Intent; // Added for navigation
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class Main_Activity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide Action Bar for a clean UI
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.main_activity);

        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);

        // Set Default Fragment (Home)
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commit();
        }

        // Bottom Navigation logic
        bottomNavigation.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                selectedFragment = new HomeFragment();
            } else if (id == R.id.nav_analysis) {
                selectedFragment = new AnalysisFragment();
            } else if (id == R.id.nav_wallet) {
                selectedFragment = PlaceholderFragment.newInstance("Wallet Page");
            } else if (id == R.id.nav_profile) {
                selectedFragment = PlaceholderFragment.newInstance("Profile Page");
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
                return true;
            }
            return false;
        });

        // FIXED: Now opens the Add Transaction Page Activity
        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(Main_Activity.this, AddTransactionActivity.class);
            startActivity(intent);
        });
    }

    // Home Fragment Class
    public static class HomeFragment extends Fragment {
        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_home, container, false);
        }
    }

    // Analysis Fragment Class
    public static class AnalysisFragment extends Fragment {
        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_analysis, container, false);
        }
    }

    // Placeholder Fragment Class for other pages
    public static class PlaceholderFragment extends Fragment {
        private static final String ARG_TITLE = "title";

        public static PlaceholderFragment newInstance(String title) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putString(ARG_TITLE, title);
            fragment.setArguments(args);
            return fragment;
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_placeholder, container, false);
            TextView textView = view.findViewById(R.id.page_title);
            if (getArguments() != null) {
                textView.setText(getArguments().getString(ARG_TITLE));
            }
            return view;
        }
    }
}